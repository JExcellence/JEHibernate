# Lazy-loading guide

Hibernate lazy associations throw `LazyInitializationException` when accessed after the persistence
context (the `EntityManager`/session) has closed. JEHibernate gives you three ways to avoid that.
This guide says which to use when.

## Decision table

| Situation | Use | Why |
|---|---|---|
| You need to touch lazy associations while doing related work, possibly mutating | **Session scoping** — `withSession(...)` | The `EntityManager` stays open for the whole lambda; lazy access works; one transaction. The default and recommended path. |
| Read-only traversal of lazy data, no writes | **Read-only session** — `withReadOnly(...)` | Same open-session benefit, no transaction overhead, Hibernate flush disabled. |
| You know up front which associations you need on a specific read | **EntityGraph** — `findByIdWithGraph(...)`, `findAllWithGraph(...)`, `findByIdWithNamedGraph(...)` | Fetches the named associations in a single query; the returned detached entity already has them, so no session is needed afterwards. Best N+1 fix for API/DTO boundaries. |
| Server-side-rendered web app migrating fast, associations accessed in the view layer | **OSIV (last resort)** — see below | Keeps a session open for the whole request. Convenient but an anti-pattern: hides N+1, holds connections longer, couples view to persistence. Prefer EntityGraph or scoping. |

Rule of thumb: **scope for behaviour, EntityGraph for shape.** If your code *does things* with the
graph, scope a session. If your code just *needs a known shape* of data returned, use an EntityGraph
and let the entity detach.

## 1. Session scoping (primary)

```java
BigDecimal total = orderRepo.withSession(session -> {
    ShopOrder order = session.find(ShopOrder.class, 1L);
    return order.getItems().stream()          // lazy access works inside the lambda
        .map(ShopOrderItem::getPrice)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
});
```

Read-only variant (no transaction, flush disabled):

```java
List<String> products = orderRepo.withReadOnly(session -> {
    ShopOrder order = session.find(ShopOrder.class, 1L);
    return order.getItems().stream().map(ShopOrderItem::getProduct).toList();
});
```

> Return **values or copies**, not the managed entity, if you intend to touch lazy associations
> after the lambda — the session closes when it returns.

## 2. EntityGraph (no session needed afterwards)

Ad-hoc attribute paths (dot-separated for nested paths):

```java
Optional<ShopOrder> order = orderRepo.findByIdWithGraph(1L, "items");
order.ifPresent(o -> o.getItems().size());   // already loaded, no extra query, no exception

List<ShopOrder> recent = orderRepo.findAllWithGraph("items", "customer.address");
```

Named graph declared on the entity:

```java
@NamedEntityGraph(name = "ShopOrder.withItems",
    attributeNodes = @NamedAttributeNode("items"))
@Entity
class ShopOrder { /* ... */ }

Optional<ShopOrder> order = orderRepo.findByIdWithNamedGraph(1L, "ShopOrder.withItems");
```

The graph is applied as a JPA `loadgraph` hint: listed attributes are fetched eagerly in **one
query**, everything else keeps its mapped fetch type. This is verified in
`EntityGraphIntegrationTest` via Hibernate `Statistics` (exactly one prepared statement).

## 3. Open-Session-In-View (last resort — not shipped as a bean)

JEHibernate **intentionally does not ship an OSIV bean.** Its model is session-per-operation;
binding a session to the whole HTTP request re-introduces the very problems (hidden N+1, longer
connection hold times, view↔persistence coupling) that scoping and EntityGraph solve cleanly.

If you are migrating a legacy SSR app and need it temporarily, here is a minimal Spring filter you
can copy into your application (requires `spring-web` + `jakarta.servlet-api`). Treat it as
scaffolding to delete later, not a destination:

```java
// LAST RESORT — prefer withSession(...) or findByIdWithGraph(...).
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OpenSessionInViewFilter extends OncePerRequestFilter {

    private final EntityManagerFactory emf;

    public OpenSessionInViewFilter(JEHibernate jeHibernate) {
        this.emf = jeHibernate.getEntityManagerFactory();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        EntityManager em = emf.createEntityManager();
        try {
            // Bind `em` to the request thread via your own ThreadLocal/holder, then read it
            // from your repositories for the duration of the request.
            chain.doFilter(request, response);
        } finally {
            em.close();
        }
    }
}
```

Document any use of this in your codebase with a removal plan.

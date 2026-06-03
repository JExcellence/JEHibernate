# Multi-tenancy guide

Multi-tenancy is **off by default**. Enable it with `ConfigurationBuilder.multiTenancy(...)` and
pick a strategy. See [ADR-0004](adr/0004-multi-tenancy.md) for the rationale.

## Choosing a strategy

| Strategy | Isolation | Ops cost | Use when |
|---|---|---|---|
| `DISCRIMINATOR` | row-level (`tenant_id` column) | lowest | Many small tenants, one schema, simplest setup. |
| `SCHEMA` | one schema per tenant | medium | Stronger isolation on one server/database. |
| `DATABASE` | one database per tenant | highest | Regulatory/isolation requirements; tenants provisioned separately. |

All strategies share **one HikariCP pool** except DATABASE (one `DataSource` per tenant).

## Binding the current tenant

```java
try (var ignored = TenantContext.open("acme")) {
    noteRepo.findAll();                 // sees only acme's data
    try (var ignored2 = TenantContext.open("globex")) {
        noteRepo.findAll();             // sees only globex's data
    }
    // back to acme
}
// no tenant bound — a strict resolver will throw if a query runs here
```

By default the tenant is read from `TenantContext`. Override with your own resolver:

```java
TenantResolver fromSubdomain = () -> currentRequestSubdomain();   // e.g. "acme" from acme.app.com
config.multiTenancy(MultiTenancyConfig.discriminator().withResolver(fromSubdomain));
```

## DISCRIMINATOR

Add a `@TenantId` field to each tenant-scoped entity. Hibernate populates it on insert and filters
every query automatically — there is no per-query filter to forget.

```java
@Entity
class Note extends LongIdEntity {
    @org.hibernate.annotations.TenantId
    private String tenantId;
    private String text;
}

config.multiTenancy(MultiTenancyConfig.discriminator());   // strict leak guard ON by default
```

A query run with **no tenant bound** throws (`IllegalStateException` → wrapped) instead of leaking
across tenants. To allow a default tenant instead of throwing:

```java
config.multiTenancy(MultiTenancyConfig.discriminator().withStrict(false, "public"));
```

## SCHEMA

```java
config.multiTenancy(MultiTenancyConfig.schema("PUBLIC"));   // "PUBLIC" = schema reset on release
```

Connections come from the shared pool; the active schema is switched per tenant and reset to the
default schema before the connection returns to the pool. On MySQL/MariaDB "schema" means
"database" (catalog) — prefer `DATABASE` there.

## DATABASE

```java
Map<String, DataSource> perTenant = Map.of(
    "acme",   acmeDataSource,
    "globex", globexDataSource);
config.multiTenancy(MultiTenancyConfig.database(perTenant));
```

JEHibernate does not provision tenant databases — supply the `DataSource` map yourself.

## Migrations per tenant

JEHibernate's bootstrap migration (Flyway, see the migration guide) runs once against the
default/any data source. For SCHEMA/DATABASE, run Flyway **per tenant** yourself, e.g.:

```java
for (var tenant : tenants) {
    Flyway.configure()
        .dataSource(tenantDataSource(tenant))     // DATABASE
        .schemas(tenant)                           // or SCHEMA
        .locations("classpath:db/migration")
        .load()
        .migrate();
}
```

DISCRIMINATOR uses a single central migration (one schema for all tenants).

## DSGVO

Tenant isolation is an Art. 32 technical measure. DISCRIMINATOR is the weakest (a single row-level
bug exposes another tenant) — the strict leak guard mitigates but does not eliminate this. For
high-risk personal data prefer SCHEMA/DATABASE. Implement cross-tenant access/erasure
(Art. 15/17) per strategy.

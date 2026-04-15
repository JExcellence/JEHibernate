package de.jexcellence.jehibernate.repository.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Fluent, type-safe query builder for constructing JPA Criteria queries.
 * <p>
 * QueryBuilder provides a clean, readable API for building complex queries without
 * writing JPQL or SQL. It supports filtering, multi-field sorting, pagination,
 * fetch joins (N+1 prevention), streaming, and async execution.
 * <p>
 * <b>Features:</b>
 * <ul>
 *   <li>Type-safe query construction via Criteria API</li>
 *   <li>AND and OR conditions</li>
 *   <li>Multiple sort fields</li>
 *   <li>Fetch joins to prevent N+1 queries</li>
 *   <li>Consistent pagination (count + data in same session)</li>
 *   <li>Streaming for large datasets</li>
 *   <li>Async execution with CompletableFuture</li>
 * </ul>
 * <p>
 * <b>Basic Example:</b>
 * <pre>{@code
 * var users = userRepo.query()
 *     .and("active", true)
 *     .like("email", "%@gmail.com")
 *     .greaterThan("age", 18)
 *     .orderByDesc("createdAt")
 *     .orderBy("username")
 *     .limit(10)
 *     .list();
 * }</pre>
 * <p>
 * <b>OR Conditions:</b>
 * <pre>{@code
 * var users = userRepo.query()
 *     .and("active", true)
 *     .or("role", "ADMIN")
 *     .or("role", "MODERATOR")
 *     .list();
 * // SQL: WHERE active = true AND (role = 'ADMIN' OR role = 'MODERATOR')
 * }</pre>
 * <p>
 * <b>Fetch Joins (N+1 prevention):</b>
 * <pre>{@code
 * var users = userRepo.query()
 *     .fetch("orders")           // INNER JOIN FETCH
 *     .fetchLeft("profile")      // LEFT JOIN FETCH
 *     .and("active", true)
 *     .list();
 * }</pre>
 * <p>
 * <b>Streaming (large datasets):</b>
 * <pre>{@code
 * try (var stream = userRepo.query().and("active", true).stream()) {
 *     stream.filter(u -> u.getAge() > 18)
 *           .forEach(this::process);
 * }
 * }</pre>
 * <p>
 * <b>Thread Safety:</b> QueryBuilder instances are NOT thread-safe. Create a new
 * instance for each query.
 *
 * @param <T> the entity type
 * @since 1.0
 * @see Specification
 * @see PageResult
 */
public final class QueryBuilder<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryBuilder.class);

    private final Class<T> entityClass;
    private final Supplier<EntityManager> entityManagerSupplier;
    private final ExecutorService executorService;
    private final List<Specification<T>> andSpecifications = new ArrayList<>();
    private final List<Specification<T>> orSpecifications = new ArrayList<>();
    private final List<SortOrder> sortOrders = new ArrayList<>();
    private final List<FetchRequest> fetchRequests = new ArrayList<>();
    private int offset = 0;
    private int limit = Integer.MAX_VALUE;

    private record SortOrder(String field, boolean ascending) {}
    private record FetchRequest(String association, JoinType joinType) {}

    public QueryBuilder(
        Class<T> entityClass,
        Supplier<EntityManager> entityManagerSupplier,
        ExecutorService executorService
    ) {
        this.entityClass = entityClass;
        this.entityManagerSupplier = entityManagerSupplier;
        this.executorService = executorService;
    }

    // --- AND conditions ---

    public QueryBuilder<T> where(Specification<T> spec) {
        andSpecifications.add(spec);
        return this;
    }

    public QueryBuilder<T> and(String field, Object value) {
        return where(Specifications.equal(field, value));
    }

    public QueryBuilder<T> like(String field, String pattern) {
        return where(Specifications.like(field, pattern));
    }

    public QueryBuilder<T> in(String field, Collection<?> values) {
        return where(Specifications.in(field, values));
    }

    public QueryBuilder<T> between(String field, Comparable<?> start, Comparable<?> end) {
        return where(Specifications.between(field, start, end));
    }

    public QueryBuilder<T> isNull(String field) {
        return where(Specifications.isNull(field));
    }

    public QueryBuilder<T> isNotNull(String field) {
        return where(Specifications.isNotNull(field));
    }

    public QueryBuilder<T> greaterThan(String field, Comparable<?> value) {
        return where(Specifications.greaterThan(field, value));
    }

    public QueryBuilder<T> lessThan(String field, Comparable<?> value) {
        return where(Specifications.lessThan(field, value));
    }

    public QueryBuilder<T> greaterThanOrEqual(String field, Comparable<?> value) {
        return where(Specifications.greaterThanOrEqual(field, value));
    }

    public QueryBuilder<T> lessThanOrEqual(String field, Comparable<?> value) {
        return where(Specifications.lessThanOrEqual(field, value));
    }

    public QueryBuilder<T> notEqual(String field, Object value) {
        return where(Specifications.notEqual(field, value));
    }

    // --- OR conditions ---

    /**
     * Adds an OR condition. Multiple OR conditions are combined with OR logic.
     * The final predicate is: {@code AND(all_and_conditions) AND (OR(all_or_conditions))}.
     *
     * @param field the field name
     * @param value the value to compare
     * @return this builder for chaining
     */
    public QueryBuilder<T> or(String field, Object value) {
        return orWhere(Specifications.equal(field, value));
    }

    /**
     * Adds an OR specification. Multiple OR specs are combined with OR logic.
     *
     * @param spec the specification
     * @return this builder for chaining
     */
    public QueryBuilder<T> orWhere(Specification<T> spec) {
        orSpecifications.add(spec);
        return this;
    }

    public QueryBuilder<T> orLike(String field, String pattern) {
        return orWhere(Specifications.like(field, pattern));
    }

    // --- Sorting (multiple fields supported) ---

    /**
     * Adds an ascending sort order. Multiple calls accumulate sort orders.
     *
     * @param field the field to sort by
     * @return this builder for chaining
     */
    public QueryBuilder<T> orderBy(String field) {
        sortOrders.add(new SortOrder(field, true));
        return this;
    }

    /**
     * Adds a descending sort order. Multiple calls accumulate sort orders.
     *
     * @param field the field to sort by
     * @return this builder for chaining
     */
    public QueryBuilder<T> orderByDesc(String field) {
        sortOrders.add(new SortOrder(field, false));
        return this;
    }

    // --- Fetch joins (N+1 prevention) ---

    /**
     * Adds an INNER JOIN FETCH for an association. Prevents N+1 queries by
     * loading the association in the same SQL query.
     *
     * @param association the association name (e.g., "orders", "profile")
     * @return this builder for chaining
     */
    public QueryBuilder<T> fetch(String association) {
        fetchRequests.add(new FetchRequest(association, JoinType.INNER));
        return this;
    }

    /**
     * Adds a LEFT JOIN FETCH for an association. Use when the association may be null.
     *
     * @param association the association name
     * @return this builder for chaining
     */
    public QueryBuilder<T> fetchLeft(String association) {
        fetchRequests.add(new FetchRequest(association, JoinType.LEFT));
        return this;
    }

    // --- Pagination ---

    public QueryBuilder<T> offset(int offset) {
        this.offset = Math.max(0, offset);
        return this;
    }

    public QueryBuilder<T> limit(int limit) {
        this.limit = Math.max(1, limit);
        return this;
    }

    public QueryBuilder<T> page(int pageNumber, int pageSize) {
        this.offset = Math.max(0, pageNumber) * Math.max(1, pageSize);
        this.limit = Math.max(1, pageSize);
        return this;
    }

    // --- Execution ---

    public List<T> list() {
        long start = System.nanoTime();
        try (EntityManager em = entityManagerSupplier.get()) {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(entityClass);
            Root<T> root = cq.from(entityClass);

            applyFetches(root);
            applyPredicates(cb, cq, root);
            applySorting(cb, cq, root);

            var query = em.createQuery(cq);
            if (offset > 0) {
                query.setFirstResult(offset);
            }
            if (limit < Integer.MAX_VALUE) {
                query.setMaxResults(limit);
            }
            List<T> results = query.getResultList();
            logTiming("list", start);
            return results;
        }
    }

    /**
     * Execute query and return results as a PageResult with pagination metadata.
     * Count and data queries execute within the same EntityManager session for consistency.
     *
     * @param pageNumber the page number (zero-based)
     * @param pageSize   the page size
     * @return PageResult containing the data and pagination info
     */
    public PageResult<T> getPage(int pageNumber, int pageSize) {
        long start = System.nanoTime();
        int safePageNumber = Math.max(0, pageNumber);
        int safePageSize = Math.max(1, pageSize);

        try (EntityManager em = entityManagerSupplier.get()) {
            CriteriaBuilder cb = em.getCriteriaBuilder();

            // Count query
            CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
            Root<T> countRoot = countQuery.from(entityClass);
            countQuery.select(cb.count(countRoot));
            applyPredicates(cb, countQuery, countRoot);
            long totalElements = em.createQuery(countQuery).getSingleResult();

            // Data query
            CriteriaQuery<T> dataQuery = cb.createQuery(entityClass);
            Root<T> dataRoot = dataQuery.from(entityClass);
            dataQuery.select(dataRoot);
            applyFetches(dataRoot);
            applyPredicates(cb, dataQuery, dataRoot);
            applySorting(cb, dataQuery, dataRoot);

            List<T> content = em.createQuery(dataQuery)
                .setFirstResult(safePageNumber * safePageSize)
                .setMaxResults(safePageSize)
                .getResultList();

            logTiming("getPage", start);
            return new PageResult<>(content, totalElements, safePageNumber, safePageSize);
        }
    }

    public Optional<T> first() {
        this.limit = 1;
        List<T> results = list();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public long count() {
        long start = System.nanoTime();
        try (EntityManager em = entityManagerSupplier.get()) {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<T> root = cq.from(entityClass);
            cq.select(cb.count(root));

            applyPredicates(cb, cq, root);

            long result = em.createQuery(cq).getSingleResult();
            logTiming("count", start);
            return result;
        }
    }

    public boolean exists() {
        return count() > 0;
    }

    /**
     * Returns a stream of results. The underlying EntityManager is closed when the
     * stream is closed. <b>Must be used with try-with-resources.</b>
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * try (var stream = repo.query().and("active", true).stream()) {
     *     stream.forEach(this::process);
     * }
     * }</pre>
     *
     * @return a closeable stream of entities
     */
    public Stream<T> stream() {
        EntityManager em = entityManagerSupplier.get();
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(entityClass);
            Root<T> root = cq.from(entityClass);

            applyFetches(root);
            applyPredicates(cb, cq, root);
            applySorting(cb, cq, root);

            var query = em.createQuery(cq);
            if (offset > 0) {
                query.setFirstResult(offset);
            }
            if (limit < Integer.MAX_VALUE) {
                query.setMaxResults(limit);
            }
            return query.getResultStream().onClose(em::close);
        } catch (Exception e) {
            em.close();
            throw e;
        }
    }

    // --- Async ---

    public CompletableFuture<List<T>> listAsync() {
        return CompletableFuture.supplyAsync(this::list, executorService);
    }

    public CompletableFuture<Optional<T>> firstAsync() {
        return CompletableFuture.supplyAsync(this::first, executorService);
    }

    public CompletableFuture<Long> countAsync() {
        return CompletableFuture.supplyAsync(this::count, executorService);
    }

    public CompletableFuture<PageResult<T>> getPageAsync(int pageNumber, int pageSize) {
        return CompletableFuture.supplyAsync(() -> getPage(pageNumber, pageSize), executorService);
    }

    // --- Internal ---

    private void applyPredicates(CriteriaBuilder cb, CriteriaQuery<?> cq, Root<T> root) {
        List<Predicate> allPredicates = new ArrayList<>();

        // AND conditions
        for (Specification<T> spec : andSpecifications) {
            allPredicates.add(spec.toPredicate(root, cq, cb));
        }

        // OR conditions — combined as a single OR predicate added to the AND list
        if (!orSpecifications.isEmpty()) {
            Predicate[] orPredicates = orSpecifications.stream()
                .map(spec -> spec.toPredicate(root, cq, cb))
                .toArray(Predicate[]::new);
            allPredicates.add(cb.or(orPredicates));
        }

        if (!allPredicates.isEmpty()) {
            cq.where(allPredicates.toArray(new Predicate[0]));
        }
    }

    @SuppressWarnings("unchecked")
    private void applySorting(CriteriaBuilder cb, CriteriaQuery<T> cq, Root<T> root) {
        if (sortOrders.isEmpty()) return;

        List<Order> orders = new ArrayList<>(sortOrders.size());
        for (SortOrder so : sortOrders) {
            var path = Specifications.resolvePath(root, so.field());
            orders.add(so.ascending() ? cb.asc(path) : cb.desc(path));
        }
        cq.orderBy(orders);
    }

    private void applyFetches(Root<T> root) {
        for (FetchRequest fr : fetchRequests) {
            root.fetch(fr.association(), fr.joinType());
        }
    }

    private void logTiming(String operation, long startNanos) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (durationMs > 500) {
            LOGGER.warn("Slow query: {}.{} took {}ms", entityClass.getSimpleName(), operation, durationMs);
        } else {
            LOGGER.debug("Query: {}.{} completed in {}ms", entityClass.getSimpleName(), operation, durationMs);
        }
    }
}

package de.jexcellence.hibernate.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Full-featured JPA repository with synchronous and asynchronous CRUD operations.
 *
 * @param <T>  managed entity type
 * @param <ID> identifier type of the entity
 * @author JExcellence
 * @version 1.1
 * @since 1.0
 */
public class BaseRepository<T, ID> {

    private final Logger logger;
    private final Class<T> entityClass;
    private final EntityManagerFactory entityManagerFactory;
    private final ExecutorService executorService;

    public BaseRepository(
        @NotNull final ExecutorService executor,
        @NotNull final EntityManagerFactory entityManagerFactory,
        @NotNull final Class<T> entityClass
    ) {
        this.entityManagerFactory = entityManagerFactory;
        this.entityClass = entityClass;
        this.executorService = executor;
        this.logger = Logger.getLogger(entityClass.getName());
    }

    @NotNull
    public CompletableFuture<T> createAsync(@NotNull final T entity) {
        return CompletableFuture.supplyAsync(() -> this.create(entity), this.executorService);
    }

    @NotNull
    public CompletableFuture<T> updateAsync(@NotNull final T entity) {
        return CompletableFuture.supplyAsync(() -> this.update(entity), this.executorService);
    }

    @NotNull
    public CompletableFuture<Boolean> deleteAsync(@NotNull final ID id) {
        return CompletableFuture.supplyAsync(() -> this.delete(id), this.executorService);
    }

    @NotNull
    public CompletableFuture<Optional<T>> findByIdAsync(@NotNull final ID id) {
        return CompletableFuture.supplyAsync(() -> this.findById(id), this.executorService);
    }

    @NotNull
    public CompletableFuture<List<T>> findAllAsync(final int pageNumber, final int pageSize) {
        return CompletableFuture.supplyAsync(() -> this.findAll(pageNumber, pageSize), this.executorService);
    }

    @NotNull
    public T create(@NotNull final T entity) {
        return this.executeInTransaction(em -> {
            em.persist(entity);
            return entity;
        });
    }

    @NotNull
    public List<T> createAll(@NotNull final Collection<T> entities) {
        return this.executeInTransaction(em -> {
            var result = new ArrayList<T>(entities.size());
            var count = 0;
            for (var entity : entities) {
                em.persist(entity);
                result.add(entity);
                if (++count % 50 == 0) {
                    em.flush();
                    em.clear();
                }
            }
            return result;
        });
    }

    @NotNull
    public CompletableFuture<List<T>> createAllAsync(@NotNull final Collection<T> entities) {
        return CompletableFuture.supplyAsync(() -> this.createAll(entities), this.executorService);
    }

    @NotNull
    public T update(@NotNull final T entity) {
        return this.executeInTransaction(em -> em.merge(entity));
    }

    @NotNull
    public List<T> updateAll(@NotNull final Collection<T> entities) {
        return this.executeInTransaction(em -> {
            var result = new ArrayList<T>(entities.size());
            var count = 0;
            for (var entity : entities) {
                result.add(em.merge(entity));
                if (++count % 50 == 0) {
                    em.flush();
                    em.clear();
                }
            }
            return result;
        });
    }

    @NotNull
    public T save(@NotNull final T entity) {
        return this.executeInTransaction(em -> {
            if (em.contains(entity)) {
                return em.merge(entity);
            }
            em.persist(entity);
            return entity;
        });
    }

    public boolean delete(@NotNull final ID id) {
        return this.executeInTransaction(em -> {
            var entity = em.find(this.entityClass, id);
            if (entity != null) {
                em.remove(entity);
                return true;
            }
            return false;
        });
    }

    public void deleteEntity(@NotNull final T entity) {
        this.runInTransaction(em -> {
            var managed = em.contains(entity) ? entity : em.merge(entity);
            em.remove(managed);
        });
    }

    public int deleteAll(@NotNull final Collection<ID> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return this.executeInTransaction(em -> {
            var cb = em.getCriteriaBuilder();
            var delete = cb.createCriteriaDelete(this.entityClass);
            var root = delete.from(this.entityClass);
            delete.where(root.get("id").in(ids));
            return em.createQuery(delete).executeUpdate();
        });
    }

    public int deleteByAttribute(@NotNull final String attribute, @NotNull final Object value) {
        return this.executeInTransaction(em -> {
            var cb = em.getCriteriaBuilder();
            var delete = cb.createCriteriaDelete(this.entityClass);
            var root = delete.from(this.entityClass);
            delete.where(cb.equal(root.get(attribute), value));
            return em.createQuery(delete).executeUpdate();
        });
    }

    @NotNull
    public Optional<T> findById(@NotNull final ID id) {
        return this.executeInTransaction(em -> Optional.ofNullable(em.find(this.entityClass, id)));
    }

    public boolean existsById(@NotNull final ID id) {
        return this.executeInTransaction(em -> {
            var cb = em.getCriteriaBuilder();
            var cq = cb.createQuery(Long.class);
            var root = cq.from(this.entityClass);
            cq.select(cb.literal(1L)).where(cb.equal(root.get("id"), id));
            return !em.createQuery(cq).setMaxResults(1).getResultList().isEmpty();
        });
    }

    public long count() {
        return this.executeInTransaction(em -> {
            var cb = em.getCriteriaBuilder();
            var cq = cb.createQuery(Long.class);
            cq.select(cb.count(cq.from(this.entityClass)));
            return em.createQuery(cq).getSingleResult();
        });
    }

    public long countByAttribute(@NotNull final String attribute, @NotNull final Object value) {
        return this.executeInTransaction(em -> {
            var cb = em.getCriteriaBuilder();
            var cq = cb.createQuery(Long.class);
            var root = cq.from(this.entityClass);
            cq.select(cb.count(root)).where(cb.equal(root.get(attribute), value));
            return em.createQuery(cq).getSingleResult();
        });
    }

    @NotNull
    public List<T> findAll() {
        return this.executeInTransaction(em -> {
            var cb = em.getCriteriaBuilder();
            var cq = cb.createQuery(this.entityClass);
            cq.select(cq.from(this.entityClass));
            return em.createQuery(cq).getResultList();
        });
    }

    @NotNull
    public List<T> findAll(final int pageNumber, final int pageSize) {
        return this.findAll(pageNumber, pageSize, null, true);
    }

    @NotNull
    public List<T> findAll(final int pageNumber, final int pageSize, @Nullable final String sortBy, final boolean ascending) {
        return this.executeInTransaction(em -> {
            var cb = em.getCriteriaBuilder();
            var cq = cb.createQuery(this.entityClass);
            var root = cq.from(this.entityClass);
            cq.select(root);

            if (sortBy != null && !sortBy.isBlank()) {
                cq.orderBy(ascending ? cb.asc(root.get(sortBy)) : cb.desc(root.get(sortBy)));
            }

            return em.createQuery(cq)
                .setFirstResult(Math.max(pageNumber, 0) * Math.max(pageSize, 1))
                .setMaxResults(Math.max(pageSize, 1))
                .getResultList();
        });
    }

    @NotNull
    public List<T> findAllById(@NotNull final Collection<ID> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return this.executeInTransaction(em -> {
            var cb = em.getCriteriaBuilder();
            var cq = cb.createQuery(this.entityClass);
            var root = cq.from(this.entityClass);
            cq.select(root).where(root.get("id").in(ids));
            return em.createQuery(cq).getResultList();
        });
    }

    @NotNull
    public Optional<T> findByAttribute(@NotNull final String attribute, @NotNull final Object value) {
        return this.executeInTransaction(em -> {
            var cb = em.getCriteriaBuilder();
            var cq = cb.createQuery(this.entityClass);
            var root = cq.from(this.entityClass);
            cq.select(root).where(cb.equal(root.get(attribute), value));
            return em.createQuery(cq).getResultStream().findFirst();
        });
    }

    @NotNull
    public List<T> findAllByAttribute(@NotNull final String attribute, @NotNull final Object value) {
        return this.executeInTransaction(em -> {
            var cb = em.getCriteriaBuilder();
            var cq = cb.createQuery(this.entityClass);
            var root = cq.from(this.entityClass);
            cq.select(root).where(cb.equal(root.get(attribute), value));
            return em.createQuery(cq).getResultList();
        });
    }

    @NotNull
    public Optional<T> findByAttributes(@NotNull final Map<String, Object> attributes) {
        return this.executeInTransaction(em -> {
            var cb = em.getCriteriaBuilder();
            var cq = cb.createQuery(this.entityClass);
            var root = cq.from(this.entityClass);

            try {
                this.applyPredicates(attributes, cb, cq, root);
                return em.createQuery(cq).getResultStream().findFirst();
            } catch (final RuntimeException e) {
                this.logger.log(Level.WARNING, "Failed to build attribute predicates", e);
                return Optional.empty();
            }
        });
    }

    @NotNull
    public List<T> findAllByAttributes(@NotNull final Map<String, Object> attributes) {
        return this.executeInTransaction(em -> {
            var cb = em.getCriteriaBuilder();
            var cq = cb.createQuery(this.entityClass);
            var root = cq.from(this.entityClass);

            try {
                this.applyPredicates(attributes, cb, cq, root);
                return em.createQuery(cq).getResultList();
            } catch (final RuntimeException e) {
                this.logger.log(Level.WARNING, "Failed to build attribute predicates", e);
                return Collections.emptyList();
            }
        });
    }

    @NotNull
    public CompletableFuture<List<T>> findAllByAttributesAsync(@NotNull final Map<String, Object> attributes) {
        return CompletableFuture.supplyAsync(() -> this.findAllByAttributes(attributes), this.executorService);
    }

    @NotNull
    public QueryBuilder<T> query() {
        return new QueryBuilder<>(this);
    }

    public void refresh(@NotNull final T entity) {
        this.runInTransaction(em -> em.refresh(em.contains(entity) ? entity : em.merge(entity)));
    }

    public void detach(@NotNull final T entity) {
        this.runInTransaction(em -> {
            if (em.contains(entity)) {
                em.detach(entity);
            }
        });
    }

    @NotNull
    public <R> R executeInTransaction(@NotNull final Function<EntityManager, R> action) {
        EntityTransaction transaction = null;
        try (var em = this.entityManagerFactory.createEntityManager()) {
            transaction = em.getTransaction();
            transaction.begin();
            var result = action.apply(em);
            transaction.commit();
            return result;
        } catch (final RuntimeException e) {
            this.rollbackQuietly(transaction);
            throw e;
        } catch (final Exception e) {
            this.rollbackQuietly(transaction);
            throw new IllegalStateException("Repository operation failed", e);
        }
    }

    public void runInTransaction(@NotNull final Consumer<EntityManager> action) {
        this.executeInTransaction(em -> {
            action.accept(em);
            return null;
        });
    }

    @NotNull
    protected Class<T> getEntityClass() {
        return this.entityClass;
    }

    @NotNull
    protected ExecutorService getExecutorService() {
        return this.executorService;
    }

    private void applyPredicates(
        @NotNull final Map<String, Object> attributes,
        @NotNull final CriteriaBuilder cb,
        @NotNull final CriteriaQuery<T> cq,
        @NotNull final Root<T> root
    ) {
        var predicates = new ArrayList<Predicate>();

        for (var entry : attributes.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();

            if (key.contains(".")) {
                var parts = key.split("\\.");
                Path<?> path = root.get(parts[0]);
                for (var i = 1; i < parts.length; i++) {
                    path = path.get(parts[i]);
                }
                predicates.add(cb.equal(path, value));
            } else {
                predicates.add(cb.equal(root.get(key), value));
            }
        }

        cq.select(root).where(predicates.toArray(Predicate[]::new));
    }

    private void rollbackQuietly(@Nullable final EntityTransaction transaction) {
        if (transaction == null || !transaction.isActive()) {
            return;
        }
        try {
            transaction.rollback();
        } catch (final RuntimeException e) {
            this.logger.log(Level.WARNING, "Failed to rollback transaction", e);
        }
    }

    public static class QueryBuilder<E> {
        private final BaseRepository<E, ?> repository;
        private final List<PredicateSpec> predicates = new ArrayList<>();
        private String sortBy;
        private boolean ascending = true;
        private int offset = 0;
        private int limit = Integer.MAX_VALUE;

        QueryBuilder(BaseRepository<E, ?> repository) {
            this.repository = repository;
        }

        @NotNull
        public QueryBuilder<E> where(@NotNull final String attribute, @NotNull final Object value) {
            this.predicates.add(new PredicateSpec(attribute, value, PredicateType.EQUAL));
            return this;
        }

        @NotNull
        public QueryBuilder<E> whereLike(@NotNull final String attribute, @NotNull final String pattern) {
            this.predicates.add(new PredicateSpec(attribute, pattern, PredicateType.LIKE));
            return this;
        }

        @NotNull
        public QueryBuilder<E> whereIn(@NotNull final String attribute, @NotNull final Collection<?> values) {
            this.predicates.add(new PredicateSpec(attribute, values, PredicateType.IN));
            return this;
        }

        @NotNull
        public QueryBuilder<E> whereNotNull(@NotNull final String attribute) {
            this.predicates.add(new PredicateSpec(attribute, null, PredicateType.NOT_NULL));
            return this;
        }

        @NotNull
        public QueryBuilder<E> whereNull(@NotNull final String attribute) {
            this.predicates.add(new PredicateSpec(attribute, null, PredicateType.IS_NULL));
            return this;
        }

        @NotNull
        public QueryBuilder<E> whereGreaterThan(@NotNull final String attribute, @NotNull final Comparable<?> value) {
            this.predicates.add(new PredicateSpec(attribute, value, PredicateType.GREATER_THAN));
            return this;
        }

        @NotNull
        public QueryBuilder<E> whereLessThan(@NotNull final String attribute, @NotNull final Comparable<?> value) {
            this.predicates.add(new PredicateSpec(attribute, value, PredicateType.LESS_THAN));
            return this;
        }

        @NotNull
        public QueryBuilder<E> orderBy(@NotNull final String attribute) {
            this.sortBy = attribute;
            this.ascending = true;
            return this;
        }

        @NotNull
        public QueryBuilder<E> orderByDesc(@NotNull final String attribute) {
            this.sortBy = attribute;
            this.ascending = false;
            return this;
        }

        @NotNull
        public QueryBuilder<E> offset(final int offset) {
            this.offset = Math.max(0, offset);
            return this;
        }

        @NotNull
        public QueryBuilder<E> limit(final int limit) {
            this.limit = Math.max(1, limit);
            return this;
        }

        @NotNull
        public QueryBuilder<E> page(final int pageNumber, final int pageSize) {
            this.offset = Math.max(0, pageNumber) * Math.max(1, pageSize);
            this.limit = Math.max(1, pageSize);
            return this;
        }

        @NotNull
        public List<E> list() {
            return this.repository.executeInTransaction(this::executeQuery);
        }

        @NotNull
        public Optional<E> first() {
            this.limit = 1;
            return this.repository.executeInTransaction(em -> this.executeQuery(em).stream().findFirst());
        }

        public long count() {
            return this.repository.executeInTransaction(em -> {
                var cb = em.getCriteriaBuilder();
                var cq = cb.createQuery(Long.class);
                var root = cq.from(this.repository.getEntityClass());
                cq.select(cb.count(root));
                this.applyPredicates(cb, cq, root);
                return em.createQuery(cq).getSingleResult();
            });
        }

        public boolean exists() {
            return this.count() > 0;
        }

        @NotNull
        public CompletableFuture<List<E>> listAsync() {
            return CompletableFuture.supplyAsync(this::list, this.repository.getExecutorService());
        }

        private List<E> executeQuery(EntityManager em) {
            var cb = em.getCriteriaBuilder();
            var cq = cb.createQuery(this.repository.getEntityClass());
            var root = cq.from(this.repository.getEntityClass());
            cq.select(root);

            this.applyPredicates(cb, cq, root);

            if (this.sortBy != null) {
                cq.orderBy(this.ascending ? cb.asc(root.get(this.sortBy)) : cb.desc(root.get(this.sortBy)));
            }

            var query = em.createQuery(cq);
            if (this.offset > 0) {
                query.setFirstResult(this.offset);
            }
            if (this.limit < Integer.MAX_VALUE) {
                query.setMaxResults(this.limit);
            }
            return query.getResultList();
        }

        @SuppressWarnings("unchecked")
        private void applyPredicates(CriteriaBuilder cb, CriteriaQuery<?> cq, Root<E> root) {
            if (this.predicates.isEmpty()) {
                return;
            }

            var jpaPredicates = new ArrayList<Predicate>();
            for (var spec : this.predicates) {
                var path = this.resolvePath(root, spec.attribute);
                var predicate = switch (spec.type) {
                    case EQUAL -> cb.equal(path, spec.value);
                    case LIKE -> cb.like((Path<String>) path, (String) spec.value);
                    case IN -> path.in((Collection<?>) spec.value);
                    case NOT_NULL -> cb.isNotNull(path);
                    case IS_NULL -> cb.isNull(path);
                    case GREATER_THAN -> cb.greaterThan((Path<Comparable>) path, (Comparable) spec.value);
                    case LESS_THAN -> cb.lessThan((Path<Comparable>) path, (Comparable) spec.value);
                };
                jpaPredicates.add(predicate);
            }
            cq.where(jpaPredicates.toArray(Predicate[]::new));
        }

        private Path<?> resolvePath(Root<E> root, String attribute) {
            if (!attribute.contains(".")) {
                return root.get(attribute);
            }
            var parts = attribute.split("\\.");
            Path<?> path = root.get(parts[0]);
            for (var i = 1; i < parts.length; i++) {
                path = path.get(parts[i]);
            }
            return path;
        }

        private record PredicateSpec(String attribute, Object value, PredicateType type) {}
        private enum PredicateType { EQUAL, LIKE, IN, NOT_NULL, IS_NULL, GREATER_THAN, LESS_THAN }
    }
}

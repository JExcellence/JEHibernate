package de.jexcellence.hibernate.repository;

import de.jexcellence.hibernate.entity.AbstractEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.criteria.*;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractCRUDRepository<T extends AbstractEntity, ID> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractCRUDRepository.class);
    private final EntityManagerFactory entityManagerFactory;
    private final Class<T> entityClass;

    public AbstractCRUDRepository(EntityManagerFactory entityManagerFactory, Class<T> entityClass) {
        this.entityManagerFactory = entityManagerFactory;
        this.entityClass = entityClass;
    }

    public Mono<T> create(T entity) {
        return Mono.defer(() -> {
                    logger.debug("Creating new entity of type {}", entityClass.getSimpleName());
                    return Mono.fromCallable(() -> {
                        try (EntityManager em = entityManagerFactory.createEntityManager()) {
                            EntityTransaction tx = em.getTransaction();
                            tx.begin();
                            em.persist(entity);
                            tx.commit();
                            return entity;
                        }
                    });
                }).doOnError(e -> logger.error("Error creating entity", e))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100)));
    }

    public Mono<T> update(T entity) {
        return Mono.defer(() -> {
                    logger.debug("Updating entity of type {}", entityClass.getSimpleName());
                    return Mono.fromCallable(() -> {
                        try (EntityManager em = entityManagerFactory.createEntityManager()) {
                            EntityTransaction tx = em.getTransaction();
                            tx.begin();
                            T merged = em.merge(entity);
                            tx.commit();
                            return merged;
                        }
                    });
                }).doOnError(e -> logger.error("Error updating entity", e))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100)));
    }

    public Mono<Void> delete(ID id) {
        return Mono.defer(() -> {
                    logger.debug("Deleting entity of type {} with id {}", entityClass.getSimpleName(), id);
                    return Mono.fromCallable(() -> {
                        try (EntityManager em = entityManagerFactory.createEntityManager()) {
                            EntityTransaction tx = em.getTransaction();
                            tx.begin();
                            T entity = em.find(entityClass, id);
                            if (entity != null) {
                                em.remove(entity);
                            }
                            tx.commit();
                            return null;
                        }
                    });
                }).doOnError(e -> logger.error("Error deleting entity", e))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100)));
    }

    public Mono<Optional<T>> findById(ID id) {
        return Mono.defer(() -> {
                    logger.debug("Finding entity of type {} with id {}", entityClass.getSimpleName(), id);
                    return Mono.fromCallable(() -> {
                        try (EntityManager em = entityManagerFactory.createEntityManager()) {
                            return Optional.ofNullable(em.find(entityClass, id));
                        }
                    });
                }).doOnError(e -> logger.error("Error finding entity by id", e))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100)));
    }

    public Flux<T> findAll(int pageNumber, int pageSize, Map<String, String> sortBy, Map<String, Object> filters) {
        return Flux.defer(() -> {
                    logger.debug("Finding all entities of type {} with pagination and filters", entityClass.getSimpleName());
                    return Flux.fromCallable(() -> {
                        try (EntityManager em = entityManagerFactory.createEntityManager()) {
                            CriteriaBuilder cb = em.getCriteriaBuilder();
                            CriteriaQuery<T> cq = cb.createQuery(entityClass);
                            Root<T> root = cq.from(entityClass);

                            // Add sorting
                            if (sortBy != null && !sortBy.isEmpty()) {
                                List<Order> orders = new ArrayList<>();
                                sortBy.forEach((field, direction) -> {
                                    Path<?> path = root.get(field);
                                    if ("desc".equalsIgnoreCase(direction)) {
                                        orders.add(cb.desc(path));
                                    } else {
                                        orders.add(cb.asc(path));
                                    }
                                });
                                cq.orderBy(orders);
                            }

                            // Add filtering
                            if (filters != null && !filters.isEmpty()) {
                                List<Predicate> predicates = new ArrayList<>();
                                filters.forEach((key, value) -> {
                                    if (key.contains(".")) {
                                        String[] parts = key.split("\\.");
                                        Path<?> path = root.get(parts[0]);
                                        for (int i = 1; i < parts.length; i++) {
                                            path = path.get(parts[i]);
                                        }
                                        predicates.add(cb.equal(path, value));
                                    } else {
                                        predicates.add(cb.equal(root.get(key), value));
                                    }
                                });
                                cq.where(predicates.toArray(new Predicate[0]));
                            }

                            Query query = em.createQuery(cq);
                            query.setFirstResult(pageNumber * pageSize);
                            query.setMaxResults(pageSize);
                            return query.getResultList();
                        }
                    }).flatMapMany(resultList -> Flux.fromIterable(resultList));
                }).doOnError(e -> logger.error("Error finding all entities", e))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100)));
    }

    protected <R> Mono<R> executeQuery(Function<EntityManager, R> action) {
        return Mono.defer(() -> {
                    logger.debug("Executing custom query");
                    return Mono.fromCallable(() -> {
                        try (EntityManager em = entityManagerFactory.createEntityManager()) {
                            EntityTransaction tx = em.getTransaction();
                            tx.begin();
                            R result = action.apply(em);
                            tx.commit();
                            return result;
                        }
                    });
                }).doOnError(e -> logger.error("Error executing query", e))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100)));
    }
}
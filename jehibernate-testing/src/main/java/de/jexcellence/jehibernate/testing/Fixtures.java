package de.jexcellence.jehibernate.testing;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

import java.util.Arrays;
import java.util.List;

/**
 * Small persistence helpers for tests, plus a base for fluent fixture builders.
 * <p>
 * Build domain-specific fixtures by extending {@link Builder}:
 * <pre>{@code
 * final class TestEntities {
 *     static UserBuilder aUser() { return new UserBuilder(); }
 *
 *     static final class UserBuilder extends Fixtures.Builder<User> {
 *         private String role = "USER";
 *         UserBuilder withRole(String role) { this.role = role; return this; }
 *         @Override protected User build() { return new User(role); }
 *     }
 * }
 *
 * User admin = TestEntities.aUser().withRole("ADMIN").persist(emf);
 * }</pre>
 *
 * @since 4.0
 */
public final class Fixtures {

    private Fixtures() {
    }

    /**
     * Persists a single entity in its own transaction and returns it.
     *
     * @param emf    the entity manager factory
     * @param entity the entity to persist
     * @param <E>    the entity type
     * @return the persisted entity
     */
    public static <E> E persist(EntityManagerFactory emf, E entity) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try (em) {
            tx.begin();
            em.persist(entity);
            tx.commit();
            return entity;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }

    /**
     * Persists several entities in one transaction.
     *
     * @param emf      the entity manager factory
     * @param entities the entities to persist
     */
    @SafeVarargs
    public static <E> List<E> persistAll(EntityManagerFactory emf, E... entities) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try (em) {
            tx.begin();
            for (E entity : entities) {
                em.persist(entity);
            }
            tx.commit();
            return Arrays.asList(entities);
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }

    /**
     * Base class for fluent test-entity builders.
     *
     * @param <E> the entity type produced by the builder
     */
    public abstract static class Builder<E> {

        /**
         * @return a new, unpersisted entity instance from the builder's current state
         */
        protected abstract E build();

        /**
         * Builds the entity and persists it via {@link Fixtures#persist}.
         *
         * @param emf the entity manager factory
         * @return the persisted entity
         */
        public E persist(EntityManagerFactory emf) {
            return Fixtures.persist(emf, build());
        }
    }
}

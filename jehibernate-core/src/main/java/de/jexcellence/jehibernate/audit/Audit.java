package de.jexcellence.jehibernate.audit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only query helpers over Envers audit history. Requires {@code hibernate-envers} on the
 * classpath and the entity annotated {@code @org.hibernate.envers.Audited}.
 *
 * @since 4.0
 */
public final class Audit {

    private Audit() {
    }

    /**
     * Returns each historical state of an entity, oldest revision first.
     *
     * @param emf         the entity manager factory
     * @param entityClass the audited entity type
     * @param id          the entity id
     * @param <T>         the entity type
     * @return the entity's states across all revisions (empty if never audited)
     */
    public static <T> List<T> getRevisions(EntityManagerFactory emf, Class<T> entityClass, Object id) {
        EntityManager em = emf.createEntityManager();
        try (em) {
            AuditReader reader = AuditReaderFactory.get(em);
            List<Number> revisionNumbers = reader.getRevisions(entityClass, id);
            List<T> states = new ArrayList<>(revisionNumbers.size());
            for (Number revision : revisionNumbers) {
                states.add(reader.find(entityClass, id, revision));
            }
            return states;
        }
    }

    /**
     * Returns the revision numbers at which the entity changed, oldest first.
     *
     * @param emf         the entity manager factory
     * @param entityClass the audited entity type
     * @param id          the entity id
     * @return the revision numbers
     */
    public static List<Number> getRevisionNumbers(EntityManagerFactory emf, Class<?> entityClass, Object id) {
        EntityManager em = emf.createEntityManager();
        try (em) {
            return AuditReaderFactory.get(em).getRevisions(entityClass, id);
        }
    }

    /**
     * Returns the {@link JEHibernateRevisionEntity} metadata (user, timestamp, tenant) for a revision.
     *
     * @param emf      the entity manager factory
     * @param revision the revision number
     * @return the revision metadata
     */
    public static JEHibernateRevisionEntity getRevisionInfo(EntityManagerFactory emf, Number revision) {
        EntityManager em = emf.createEntityManager();
        try (em) {
            return AuditReaderFactory.get(em).findRevision(JEHibernateRevisionEntity.class, revision);
        }
    }
}

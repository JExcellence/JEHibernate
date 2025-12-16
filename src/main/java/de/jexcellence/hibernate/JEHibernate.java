package de.jexcellence.hibernate;

import de.jexcellence.hibernate.util.ConnectionFactory;
import jakarta.persistence.EntityManagerFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Entry point for bootstrapping JEHibernate.
 *
 * <p>Encapsulates creation and lifecycle management of the shared {@link EntityManagerFactory}.
 *
 * <p>Example usage:
 * <pre>{@code
 * try (JEHibernate jeHibernate = new JEHibernate("config/hibernate.properties")) {
 *     EntityManagerFactory emf = jeHibernate.getEntityManagerFactory();
 * }
 * }</pre>
 *
 * @author JExcellence
 * @version 1.0
 * @since 1.0
 */
public final class JEHibernate implements AutoCloseable {

    private final EntityManagerFactory entityManagerFactory;

    /**
     * Creates a new bootstrap instance using the supplied configuration file.
     *
     * @param filePath path to a Hibernate properties file
     * @throws IllegalArgumentException when {@code filePath} is blank
     * @throws IllegalStateException    when the {@link EntityManagerFactory} cannot be initialised
     */
    public JEHibernate(@NotNull final String filePath) {
        if (filePath.isBlank()) {
            throw new IllegalArgumentException("File path must not be blank.");
        }
        this.entityManagerFactory = new ConnectionFactory(filePath).getEntityManagerFactory();
    }

    /**
     * Returns the managed {@link EntityManagerFactory}.
     *
     * @return the active {@link EntityManagerFactory}
     */
    @NotNull
    public EntityManagerFactory getEntityManagerFactory() {
        return this.entityManagerFactory;
    }

    /**
     * Closes the managed {@link EntityManagerFactory} if it is still open.
     */
    @Override
    public void close() {
        if (this.entityManagerFactory.isOpen()) {
            this.entityManagerFactory.close();
        }
    }
}

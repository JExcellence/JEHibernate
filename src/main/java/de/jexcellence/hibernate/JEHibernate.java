package de.jexcellence.hibernate;

import de.jexcellence.hibernate.util.DatabaseConnectionManager;
import jakarta.persistence.EntityManagerFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Entry point for bootstrapping JEHibernate.
 *
 * <p>The class encapsulates creation and lifecycle management of the shared
 * {@link EntityManagerFactory}. A minimal usage example:</p>
 *
 * <pre>{@code
 * try (JEHibernate jeHibernate = new JEHibernate("config/hibernate.properties")) {
 *     EntityManagerFactory emf = jeHibernate.getEntityManagerFactory();
 *     // use repositories backed by emf
 * }
 * }</pre>
 */
public final class JEHibernate implements AutoCloseable {

    private final EntityManagerFactory entityManagerFactory;

    /**
     * Creates a new bootstrap instance using the supplied configuration file.
     *
     * @param filePath path to a Hibernate properties file (must not be blank)
     * @throws IllegalArgumentException when {@code filePath} is {@code null} or blank
     * @throws IllegalStateException    when the {@link EntityManagerFactory} cannot be initialised
     */
    public JEHibernate(@NotNull final String filePath) {
        if (filePath.isBlank()) {
            throw new IllegalArgumentException("File path must not be blank.");
        }
        this.entityManagerFactory = new DatabaseConnectionManager(filePath).retrieveEntityManagerFactory();
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
package de.jexcellence.hibernate;

import de.jexcellence.hibernate.util.DatabaseConnectionManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * JEHibernate is a utility class that manages the lifecycle of an EntityManagerFactory.
 * It initializes the factory using a configuration file and provides methods to access
 * and close the factory.
 */
public class JEHibernate {

    private final EntityManagerFactory entityManagerFactory;

    /**
     * Constructs a JEHibernate instance and initializes the EntityManagerFactory
     * using the specified configuration file path.
     *
     * @param filePath the path to the configuration file used to initialize the EntityManagerFactory
     * @throws IllegalStateException if the EntityManagerFactory cannot be initialized
     */
    public JEHibernate(
        final String filePath
    ) {
        if (filePath == null || filePath.isEmpty())
            throw new IllegalArgumentException("File path must not be null or empty.");

        this.entityManagerFactory = new DatabaseConnectionManager(filePath).retrieveEntityManagerFactory();
    }

    /**
     * Closes the EntityManagerFactory if it is open. This method should be called
     * to release resources when the factory is no longer needed.
     */
    public void close() {
        if (this.entityManagerFactory != null)
            this.entityManagerFactory.close();
    }

    /**
     * Returns the EntityManagerFactory managed by this instance.
     *
     * @return the EntityManagerFactory
     */
    public EntityManagerFactory getEntityManagerFactory() {
        return this.entityManagerFactory;
    }
}
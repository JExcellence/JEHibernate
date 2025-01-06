package de.jexcellence.hibernate.util;

import de.jexcellence.hibernate.persistence.InfamousPersistence;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.jpa.HibernatePersistenceProvider;

import java.io.IOException;
import java.util.Properties;

/**
 * Factory class for creating and managing an {@link EntityManagerFactory} instance.
 * This class initializes the factory using properties loaded from a specified file.
 */
public class DatabaseConnectionManager implements AutoCloseable {

    private EntityManagerFactory entityManagerFactory;

    /**
     * Constructs a new {@code DatabaseConnectionManager} and initializes the
     * {@link EntityManagerFactory} using the specified properties file.
     *
     * @param filePath the path to the properties file
     */
    public DatabaseConnectionManager(final String filePath) {
        this.setupEntityManagerFactory(filePath);
    }

    /**
     * Closes the {@link EntityManagerFactory} if it is initialized.
     */
    @Override
    public void close() {
        if (this.entityManagerFactory != null) {
            this.entityManagerFactory.close();
        }
    }

    /**
     * Returns the initialized {@link EntityManagerFactory}.
     *
     * @return the {@link EntityManagerFactory} instance
     * @throws IllegalStateException if the factory is not initialized
     */
    public EntityManagerFactory retrieveEntityManagerFactory() {
        if (this.entityManagerFactory == null) {
            throw new IllegalStateException("EntityManagerFactory is not initialized.");
        }
        return this.entityManagerFactory;
    }

    /**
     * Initializes the {@link EntityManagerFactory} using properties loaded from the specified file.
     *
     * @param filePath the path to the properties file
     * @throws RuntimeException if an error occurs while loading properties
     */
    private void setupEntityManagerFactory(final String filePath) {
        try {
            Properties properties = new HibernateConfigManager().loadAndValidateProperties(filePath);
            this.entityManagerFactory = new HibernatePersistenceProvider().createContainerEntityManagerFactory(InfamousPersistence.get(), properties);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to load properties", exception);
        }
    }
}
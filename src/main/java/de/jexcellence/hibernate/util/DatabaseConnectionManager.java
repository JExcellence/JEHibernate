package de.jexcellence.hibernate.util;

import de.jexcellence.hibernate.persistence.InfamousPersistence;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Creates an {@link EntityManagerFactory} from a Hibernate properties file.
 */
public final class DatabaseConnectionManager implements AutoCloseable {

    private final EntityManagerFactory entityManagerFactory;

    /**
     * Instantiates the manager and eagerly initialises the {@link EntityManagerFactory}.
     *
     * @param filePath location of the Hibernate properties file
     * @throws IllegalArgumentException when {@code filePath} is {@code null} or blank
     * @throws UncheckedIOException     when the properties file cannot be loaded
     */
    public DatabaseConnectionManager(@NotNull final String filePath) {
        if (filePath.isBlank()) {
            throw new IllegalArgumentException("File path must not be blank.");
        }
        this.entityManagerFactory = this.createEntityManagerFactory(filePath);
    }

    /**
     * Returns the managed {@link EntityManagerFactory} instance.
     *
     * @return the initialised {@link EntityManagerFactory}
     */
    @NotNull
    public EntityManagerFactory retrieveEntityManagerFactory() {
        return this.entityManagerFactory;
    }

    /**
     * Closes the underlying {@link EntityManagerFactory}.
     */
    @Override
    public void close() {
        if (this.entityManagerFactory.isOpen()) {
            this.entityManagerFactory.close();
        }
    }

    @NotNull
    private EntityManagerFactory createEntityManagerFactory(@NotNull final String filePath) {
        try {
            final Properties properties = new HibernateConfigManager().loadAndValidateProperties(filePath);
            return new HibernatePersistenceProvider()
                .createContainerEntityManagerFactory(InfamousPersistence.get(), properties);
        } catch (final IOException exception) {
            throw new UncheckedIOException("Failed to load Hibernate properties", exception);
        }
    }
}
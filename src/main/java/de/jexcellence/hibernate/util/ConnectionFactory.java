package de.jexcellence.hibernate.util;

import de.jexcellence.hibernate.persistence.PersistenceUnitProvider;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Creates an {@link EntityManagerFactory} from a Hibernate properties file.
 *
 * @author JExcellence
 * @version 1.0
 * @since 1.0
 */
public final class ConnectionFactory implements AutoCloseable {

    private final EntityManagerFactory entityManagerFactory;

    public ConnectionFactory(@NotNull final String filePath) {
        if (filePath.isBlank()) {
            throw new IllegalArgumentException("File path must not be blank.");
        }
        this.entityManagerFactory = this.createEntityManagerFactory(filePath);
    }

    @NotNull
    public EntityManagerFactory getEntityManagerFactory() {
        return this.entityManagerFactory;
    }

    @Override
    public void close() {
        if (this.entityManagerFactory.isOpen()) {
            this.entityManagerFactory.close();
        }
    }

    @NotNull
    private EntityManagerFactory createEntityManagerFactory(@NotNull final String filePath) {
        try {
            var properties = new ConfigLoader().loadAndValidateProperties(filePath);
            return new HibernatePersistenceProvider()
                .createContainerEntityManagerFactory(PersistenceUnitProvider.get(), properties);
        } catch (final IOException exception) {
            throw new UncheckedIOException("Failed to load Hibernate properties", exception);
        }
    }
}

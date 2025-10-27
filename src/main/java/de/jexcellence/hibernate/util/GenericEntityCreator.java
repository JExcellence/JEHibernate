package de.jexcellence.hibernate.util;

import de.jexcellence.hibernate.repository.AbstractCRUDRepository;
import jakarta.persistence.Entity;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility for seeding repositories with entities discovered via classpath scanning.
 */
public final class GenericEntityCreator<T, ID> {

    private final AbstractCRUDRepository<T, ID> crudRepository;
    private final Logger logger;

    public GenericEntityCreator(
        @NotNull final AbstractCRUDRepository<T, ID> crudRepository,
        @NotNull final Logger logger
    ) {
        this.crudRepository = Objects.requireNonNull(crudRepository, "crudRepository");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void createEntitiesFromPackage(@NotNull final String packagePath) {
        if (packagePath.isBlank()) {
            throw new IllegalArgumentException("packagePath must not be blank");
        }

        this.logger.log(Level.INFO, "Scanning package {0} for entities", packagePath);
        final Reflections reflections = new Reflections(packagePath);
        final Set<Class<?>> entityClasses = reflections.getTypesAnnotatedWith(Entity.class);

        for (Class<?> entityClass : entityClasses) {
            try {
                @SuppressWarnings("unchecked")
                final T instance = (T) entityClass.getDeclaredConstructor().newInstance();
                final ID identifier = this.extractId(entityClass, instance);

                if (identifier == null || this.crudRepository.findById(identifier) == null) {
                    final CompletableFuture<T> future = this.crudRepository.createAsync(instance);
                    future.exceptionally(throwable -> {
                        this.logger.log(Level.SEVERE, "Failed to persist entity {0}", entityClass.getSimpleName());
                        this.logger.log(Level.FINE, throwable, throwable::getMessage);
                        return null;
                    });
                }
            } catch (final InvocationTargetException | InstantiationException |
                       IllegalAccessException | NoSuchMethodException exception) {
                this.logger.log(Level.SEVERE,
                    "Failed to instantiate entity {0}",
                    new Object[]{entityClass.getName()});
                this.logger.log(Level.FINE, exception, exception::getMessage);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private ID extractId(@NotNull final Class<?> entityClass, @NotNull final T instance) {
        try {
            final Method getId = entityClass.getMethod("getId");
            return (ID) getId.invoke(instance);
        } catch (final ReflectiveOperationException ignored) {
            return null;
        }
    }
}

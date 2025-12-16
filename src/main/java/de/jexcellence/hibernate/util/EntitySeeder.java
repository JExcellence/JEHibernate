package de.jexcellence.hibernate.util;

import de.jexcellence.hibernate.repository.BaseRepository;
import jakarta.persistence.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Seeds repositories with entities discovered via classpath scanning.
 *
 * @param <T>  the entity type
 * @param <ID> the entity identifier type
 * @author JExcellence
 * @version 1.0
 * @since 1.0
 */
public final class EntitySeeder<T, ID> {

    private final BaseRepository<T, ID> repository;
    private final Logger logger;

    public EntitySeeder(
        @NotNull final BaseRepository<T, ID> repository,
        @NotNull final Logger logger
    ) {
        this.repository = repository;
        this.logger = logger;
    }

    public void seedFromPackage(@NotNull final String packagePath) {
        if (packagePath.isBlank()) {
            throw new IllegalArgumentException("packagePath must not be blank");
        }

        this.logger.log(Level.INFO, "Scanning package {0} for entities", packagePath);
        var reflections = new Reflections(packagePath);
        var entityClasses = reflections.getTypesAnnotatedWith(Entity.class);

        for (var entityClass : entityClasses) {
            try {
                @SuppressWarnings("unchecked")
                var instance = (T) entityClass.getDeclaredConstructor().newInstance();
                var identifier = this.extractId(entityClass, instance);

                if (identifier == null || this.repository.findById(identifier).isEmpty()) {
                    var future = this.repository.createAsync(instance);
                    future.exceptionally(throwable -> {
                        this.logger.log(Level.SEVERE, "Failed to persist entity {0}", entityClass.getSimpleName());
                        this.logger.log(Level.FINE, throwable, throwable::getMessage);
                        return null;
                    });
                }
            } catch (final InvocationTargetException | InstantiationException |
                       IllegalAccessException | NoSuchMethodException exception) {
                this.logger.log(Level.SEVERE, "Failed to instantiate entity {0}", entityClass.getName());
                this.logger.log(Level.FINE, exception, exception::getMessage);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private ID extractId(@NotNull final Class<?> entityClass, @NotNull final T instance) {
        try {
            Method getId = entityClass.getMethod("getId");
            return (ID) getId.invoke(instance);
        } catch (final ReflectiveOperationException ignored) {
            return null;
        }
    }
}

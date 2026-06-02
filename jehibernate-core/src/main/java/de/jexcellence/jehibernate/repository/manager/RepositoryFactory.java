package de.jexcellence.jehibernate.repository.manager;

import de.jexcellence.jehibernate.exception.RepositoryException;
import jakarta.persistence.EntityManagerFactory;

import java.lang.reflect.Constructor;
import java.util.concurrent.ExecutorService;

/**
 * Factory class for creating repository instances using reflection.
 * <p>
 * Supports multiple constructor signatures in priority order:
 * <ol>
 *   <li>{@code (ExecutorService, EntityManagerFactory, Class)} — full constructor</li>
 *   <li>{@code (EntityManagerFactory, Class)} — no executor (uses default)</li>
 *   <li>{@code (ExecutorService, EntityManagerFactory)} — no entity class parameter</li>
 * </ol>
 *
 * @since 1.0
 * @see RepositoryRegistry
 */
public final class RepositoryFactory {

    private final ExecutorService executorService;
    private final EntityManagerFactory entityManagerFactory;

    public RepositoryFactory(ExecutorService executorService, EntityManagerFactory entityManagerFactory) {
        this.executorService = executorService;
        this.entityManagerFactory = entityManagerFactory;
    }

    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> repositoryClass, Class<?> entityClass) {
        // Try constructor signatures in order of preference
        try {
            // 1. Full constructor: (ExecutorService, EntityManagerFactory, Class)
            Constructor<T> ctor = findConstructor(repositoryClass,
                ExecutorService.class, EntityManagerFactory.class, Class.class);
            if (ctor != null) {
                if (!ctor.trySetAccessible()) {
                    throw new RepositoryException("Cannot access constructor of: " + repositoryClass.getName());
                }
                return ctor.newInstance(executorService, entityManagerFactory, entityClass);
            }

            // 2. Without executor: (EntityManagerFactory, Class)
            ctor = findConstructor(repositoryClass, EntityManagerFactory.class, Class.class);
            if (ctor != null) {
                if (!ctor.trySetAccessible()) {
                    throw new RepositoryException("Cannot access constructor of: " + repositoryClass.getName());
                }
                return ctor.newInstance(entityManagerFactory, entityClass);
            }

            // 3. Without entity class: (ExecutorService, EntityManagerFactory)
            ctor = findConstructor(repositoryClass, ExecutorService.class, EntityManagerFactory.class);
            if (ctor != null) {
                if (!ctor.trySetAccessible()) {
                    throw new RepositoryException("Cannot access constructor of: " + repositoryClass.getName());
                }
                return ctor.newInstance(executorService, entityManagerFactory);
            }

            throw new RepositoryException(
                "Repository " + repositoryClass.getName() + " must have one of these constructors: " +
                "(ExecutorService, EntityManagerFactory, Class), " +
                "(EntityManagerFactory, Class), or " +
                "(ExecutorService, EntityManagerFactory)"
            );
        } catch (RepositoryException e) {
            throw e;
        } catch (Exception e) {
            throw new RepositoryException(
                "Failed to instantiate repository: " + repositoryClass.getName(), e
            );
        }
    }

    private <T> Constructor<T> findConstructor(Class<T> clazz, Class<?>... paramTypes) {
        try {
            return clazz.getDeclaredConstructor(paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}

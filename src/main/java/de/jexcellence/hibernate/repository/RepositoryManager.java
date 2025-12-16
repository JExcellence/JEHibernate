package de.jexcellence.hibernate.repository;

import jakarta.persistence.EntityManagerFactory;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * Central registry and factory for repository instances.
 *
 * <p>Provides repository registration, instantiation, and dependency injection.
 *
 * <p>Simple usage:
 * <pre>{@code
 * // Initialize once at startup
 * RepositoryManager.initialize(executor, entityManagerFactory);
 *
 * // Register repositories
 * RepositoryManager.getInstance().register(UserRepository.class, User.class);
 *
 * // Get repository anywhere
 * UserRepository repo = RepositoryManager.get(UserRepository.class);
 *
 * // Or inject into objects
 * RepositoryManager.getInstance().injectInto(myService);
 * }</pre>
 *
 * @author JExcellence
 * @version 1.1
 * @since 1.0
 */
public final class RepositoryManager {

    private static volatile RepositoryManager instance;

    private final ExecutorService executor;
    private final EntityManagerFactory entityManagerFactory;
    private final ConcurrentHashMap<Class<?>, RepositoryMetadata<?, ?, ?>> registrations;
    private final ConcurrentHashMap<Class<?>, Object> instances;

    private RepositoryManager(
        @NotNull final ExecutorService executor,
        @NotNull final EntityManagerFactory entityManagerFactory
    ) {
        this.executor = executor;
        this.entityManagerFactory = entityManagerFactory;
        this.registrations = new ConcurrentHashMap<>();
        this.instances = new ConcurrentHashMap<>();
    }

    /**
     * Initializes the RepositoryManager singleton.
     *
     * @param executor             executor service for async operations
     * @param entityManagerFactory entity manager factory for database operations
     */
    public static void initialize(
        @NotNull final ExecutorService executor,
        @NotNull final EntityManagerFactory entityManagerFactory
    ) {
        synchronized (RepositoryManager.class) {
            instance = new RepositoryManager(executor, entityManagerFactory);
        }
    }

    /**
     * Gets the singleton instance.
     *
     * @return the RepositoryManager instance
     * @throws IllegalStateException if not initialized
     */
    @NotNull
    public static RepositoryManager getInstance() {
        var current = instance;
        if (current == null) {
            throw new IllegalStateException("RepositoryManager not initialized. Call initialize() first.");
        }
        return current;
    }

    /**
     * Checks if the RepositoryManager has been initialized.
     *
     * @return {@code true} if initialized
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    /**
     * Shorthand to get a repository instance.
     *
     * @param <T>             repository type
     * @param repositoryClass the repository class
     * @return the repository instance
     */
    @NotNull
    public static <T> T get(@NotNull final Class<T> repositoryClass) {
        return getInstance().getRepository(repositoryClass);
    }

    /**
     * Registers a basic repository.
     *
     * @param <T>             entity type
     * @param <ID>            entity identifier type
     * @param repositoryClass repository class
     * @param entityClass     entity class
     */
    public <T, ID> void register(
        @NotNull final Class<? extends BaseRepository<T, ID>> repositoryClass,
        @NotNull final Class<T> entityClass
    ) {
        var metadata = new RepositoryMetadata<T, ID, Object>(repositoryClass, entityClass, null);
        this.registrations.put(repositoryClass, metadata);
    }

    /**
     * Registers a cached repository with key extractor.
     *
     * @param <T>             entity type
     * @param <ID>            entity identifier type
     * @param <K>             cache key type
     * @param repositoryClass repository class
     * @param entityClass     entity class
     * @param keyExtractor    function to extract cache keys
     */
    public <T, ID, K> void register(
        @NotNull final Class<? extends CachedRepository<T, ID, K>> repositoryClass,
        @NotNull final Class<T> entityClass,
        @NotNull final Function<T, K> keyExtractor
    ) {
        var metadata = new RepositoryMetadata<>(repositoryClass, entityClass, keyExtractor);
        this.registrations.put(repositoryClass, metadata);
    }

    /**
     * Gets or creates a repository instance.
     *
     * @param <T>             repository type
     * @param repositoryClass repository class
     * @return the repository instance
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public <T> T getRepository(@NotNull final Class<T> repositoryClass) {
        var existing = this.instances.get(repositoryClass);
        if (existing != null) {
            return (T) existing;
        }

        synchronized (this.instances) {
            existing = this.instances.get(repositoryClass);
            if (existing != null) {
                return (T) existing;
            }

            var metadata = this.registrations.get(repositoryClass);
            if (metadata == null) {
                throw new IllegalStateException(
                    "Repository not registered: " + repositoryClass.getName() +
                    ". Call register() first."
                );
            }

            var newInstance = this.instantiateRepository(repositoryClass, metadata);
            this.instances.put(repositoryClass, newInstance);
            return newInstance;
        }
    }

    /**
     * Injects repositories into fields annotated with {@link InjectRepository}.
     *
     * @param target object to inject into
     */
    public void injectInto(@NotNull final Object target) {
        var targetClass = target.getClass();

        for (var current = targetClass; current != null; current = current.getSuperclass()) {
            for (var field : current.getDeclaredFields()) {
                if (!field.isAnnotationPresent(InjectRepository.class)) {
                    continue;
                }

                var fieldType = field.getType();

                if (!this.registrations.containsKey(fieldType)) {
                    throw new IllegalStateException(
                        "Repository not registered: " + fieldType.getName() +
                        " (field: " + field.getName() + " in " + targetClass.getName() + ")"
                    );
                }

                try {
                    field.setAccessible(true);
                    field.set(target, this.getRepository(fieldType));
                } catch (final IllegalAccessException e) {
                    throw new IllegalStateException("Failed to inject " + field.getName(), e);
                }
            }
        }
    }

    /**
     * Creates an instance with automatic repository injection.
     *
     * @param <T>         class type
     * @param targetClass class to instantiate
     * @return new instance with repositories injected
     */
    @NotNull
    public <T> T createWithInjection(@NotNull final Class<T> targetClass) {
        return this.createWithInjection(targetClass, new Object[0]);
    }

    /**
     * Creates an instance with constructor args and automatic repository injection.
     *
     * @param <T>             class type
     * @param targetClass     class to instantiate
     * @param constructorArgs constructor arguments
     * @return new instance with repositories injected
     */
    @NotNull
    public <T> T createWithInjection(
        @NotNull final Class<T> targetClass,
        @NotNull final Object... constructorArgs
    ) {
        try {
            var constructor = this.findConstructor(targetClass, constructorArgs);
            constructor.setAccessible(true);
            var instance = constructor.newInstance(constructorArgs);
            this.injectInto(instance);
            return instance;
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create " + targetClass.getName(), e);
        }
    }

    /**
     * Clears all cached repository instances (useful for testing).
     */
    public void clearInstances() {
        this.instances.clear();
    }

    /**
     * Shuts down the manager and clears all state.
     */
    public static void shutdown() {
        synchronized (RepositoryManager.class) {
            if (instance != null) {
                instance.instances.clear();
                instance.registrations.clear();
                instance = null;
            }
        }
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private <T> T instantiateRepository(
        @NotNull final Class<T> repositoryClass,
        @NotNull final RepositoryMetadata<?, ?, ?> metadata
    ) {
        try {
            if (metadata.hasKeyExtractor() && CachedRepository.class.isAssignableFrom(repositoryClass)) {
                var constructor = repositoryClass.getDeclaredConstructor(
                    ExecutorService.class,
                    EntityManagerFactory.class,
                    Class.class,
                    Function.class
                );
                constructor.setAccessible(true);
                return (T) constructor.newInstance(
                    this.executor,
                    this.entityManagerFactory,
                    metadata.getEntityClass(),
                    metadata.getKeyExtractor()
                );
            } else {
                var constructor = repositoryClass.getDeclaredConstructor(
                    ExecutorService.class,
                    EntityManagerFactory.class,
                    Class.class
                );
                constructor.setAccessible(true);
                return (T) constructor.newInstance(
                    this.executor,
                    this.entityManagerFactory,
                    metadata.getEntityClass()
                );
            }
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to instantiate " + repositoryClass.getName(), e);
        }
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private <T> Constructor<T> findConstructor(
        @NotNull final Class<T> targetClass,
        @NotNull final Object[] args
    ) throws NoSuchMethodException {
        if (args.length == 0) {
            return targetClass.getDeclaredConstructor();
        }

        var argTypes = new Class<?>[args.length];
        for (var i = 0; i < args.length; i++) {
            argTypes[i] = args[i].getClass();
        }

        for (var constructor : targetClass.getDeclaredConstructors()) {
            if (this.isCompatible(constructor.getParameterTypes(), argTypes)) {
                return (Constructor<T>) constructor;
            }
        }

        throw new NoSuchMethodException("No matching constructor for " + targetClass.getName());
    }

    private boolean isCompatible(@NotNull final Class<?>[] paramTypes, @NotNull final Class<?>[] argTypes) {
        if (paramTypes.length != argTypes.length) {
            return false;
        }
        for (var i = 0; i < paramTypes.length; i++) {
            if (!paramTypes[i].isAssignableFrom(argTypes[i]) && !this.isPrimitiveMatch(paramTypes[i], argTypes[i])) {
                return false;
            }
        }
        return true;
    }

    private boolean isPrimitiveMatch(@NotNull final Class<?> param, @NotNull final Class<?> arg) {
        return (param == int.class && arg == Integer.class) ||
               (param == long.class && arg == Long.class) ||
               (param == boolean.class && arg == Boolean.class) ||
               (param == double.class && arg == Double.class) ||
               (param == float.class && arg == Float.class) ||
               (param == byte.class && arg == Byte.class) ||
               (param == short.class && arg == Short.class) ||
               (param == char.class && arg == Character.class);
    }
}

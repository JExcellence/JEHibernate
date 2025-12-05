package de.jexcellence.hibernate.repository;

import jakarta.persistence.EntityManagerFactory;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * Central registry and factory for repository instances.
 * <p>
 * This singleton class manages repository registration, instantiation, and dependency injection.
 * It provides a lightweight dependency injection system specifically designed for repository management.
 * </p>
 * 
 * <p>Usage example:</p>
 * <pre>
 * // 1. Initialize the manager
 * ExecutorService executor = Executors.newFixedThreadPool(4);
 * EntityManagerFactory emf = createEntityManagerFactory();
 * RepositoryManager.initialize(executor, emf);
 * 
 * // 2. Register repositories
 * RepositoryManager.getInstance().register(
 *     UserRepository.class,
 *     User.class,
 *     User::getId
 * );
 * 
 * // 3. Inject into service classes
 * UserService service = new UserService();
 * RepositoryManager.getInstance().injectInto(service);
 * </pre>
 */
public final class RepositoryManager {

    private static volatile RepositoryManager instance;
    
    private final ExecutorService executor;
    private final EntityManagerFactory entityManagerFactory;
    private final ConcurrentHashMap<Class<?>, RepositoryMetadata<?, ?, ?>> registrations;
    private final ConcurrentHashMap<Class<?>, Object> instances;

    /**
     * Private constructor to enforce singleton pattern.
     * 
     * @param executor the executor service for asynchronous repository operations
     * @param entityManagerFactory the entity manager factory for database operations
     */
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
     * Initializes the RepositoryManager singleton with required dependencies.
     * <p>
     * This method must be called before any other RepositoryManager operations.
     * Subsequent calls will replace the existing instance.
     * </p>
     * 
     * @param executor the executor service for asynchronous repository operations
     * @param entityManagerFactory the entity manager factory for database operations
     * @throws NullPointerException if executor or entityManagerFactory is null
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
     * Gets the singleton RepositoryManager instance.
     * 
     * @return the RepositoryManager instance
     * @throws IllegalStateException if the manager has not been initialized
     */
    @NotNull
    public static RepositoryManager getInstance() {
        final RepositoryManager current = instance;
        if (current == null) {
            throw new IllegalStateException(
                "RepositoryManager must be initialized before use. Call RepositoryManager.initialize() first."
            );
        }
        return current;
    }


    /**
     * Registers a repository class for dependency injection.
     * <p>
     * This method is for repositories that extend AbstractCRUDRepository directly
     * without caching functionality.
     * </p>
     * 
     * @param <T> the entity type
     * @param <ID> the entity identifier type
     * @param repositoryClass the repository class to register
     * @param entityClass the entity class managed by the repository
     * @throws NullPointerException if repositoryClass or entityClass is null
     * @throws IllegalArgumentException if repositoryClass does not extend AbstractCRUDRepository
     */
    public <T, ID> void register(
        @NotNull final Class<? extends AbstractCRUDRepository<T, ID>> repositoryClass,
        @NotNull final Class<T> entityClass
    ) {
        final RepositoryMetadata<T, ID, ?> metadata = new RepositoryMetadata<>(
            repositoryClass,
            entityClass,
            null
        );
        
        this.registrations.put(repositoryClass, metadata);
    }

    /**
     * Registers a GenericCachedRepository class with a custom key extractor function.
     * <p>
     * This method is for repositories that extend GenericCachedRepository and require
     * a key extractor function for cache management.
     * </p>
     * 
     * @param <T> the entity type
     * @param <ID> the entity identifier type
     * @param <K> the cache key type
     * @param repositoryClass the cached repository class to register
     * @param entityClass the entity class managed by the repository
     * @param keyExtractor the function to extract cache keys from entities
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if repositoryClass does not extend AbstractCRUDRepository
     */
    public <T, ID, K> void register(
        @NotNull final Class<? extends GenericCachedRepository<T, ID, K>> repositoryClass,
        @NotNull final Class<T> entityClass,
        @NotNull final Function<T, K> keyExtractor
    ) {
        final RepositoryMetadata<T, ID, K> metadata = new RepositoryMetadata<>(
            repositoryClass,
            entityClass,
            keyExtractor
        );
        
        this.registrations.put(repositoryClass, metadata);
    }


    /**
     * Creates or retrieves a repository instance with thread-safe lazy initialization.
     * <p>
     * Uses double-checked locking to ensure only one instance is created per repository type.
     * The instance is cached for subsequent requests.
     * </p>
     * 
     * @param <T> the repository type
     * @param repositoryClass the class of the repository to create
     * @return the repository instance
     * @throws IllegalStateException if the repository is not registered or instantiation fails
     */
    @NotNull
    @SuppressWarnings("unchecked")
    private <T> T createRepository(@NotNull final Class<T> repositoryClass) {
        // First check without locking (fast path)
        Object existing = this.instances.get(repositoryClass);
        if (existing != null) {
            return (T) existing;
        }
        
        // Double-checked locking for thread-safe lazy initialization
        synchronized (this.instances) {
            existing = this.instances.get(repositoryClass);
            if (existing != null) {
                return (T) existing;
            }
            
            // Get metadata for this repository
            final RepositoryMetadata<?, ?, ?> metadata = this.registrations.get(repositoryClass);
            if (metadata == null) {
                throw new IllegalStateException(
                    "No repository registered for type " + repositoryClass.getName() + 
                    ". Register the repository using RepositoryManager.register() before injection."
                );
            }
            
            // Instantiate the repository
            final T newInstance = this.instantiateRepository(repositoryClass, metadata);
            this.instances.put(repositoryClass, newInstance);
            return newInstance;
        }
    }

    /**
     * Instantiates a repository using reflection based on its metadata.
     * <p>
     * Handles both AbstractCRUDRepository (3 parameters) and GenericCachedRepository (4 parameters)
     * constructors. For cached repositories, applies the registered keyExtractor function.
     * </p>
     * 
     * @param <T> the repository type
     * @param repositoryClass the class of the repository to instantiate
     * @param metadata the repository metadata containing configuration
     * @return the newly created repository instance
     * @throws IllegalStateException if instantiation fails
     */
    @NotNull
    @SuppressWarnings("unchecked")
    private <T> T instantiateRepository(
        @NotNull final Class<T> repositoryClass,
        @NotNull final RepositoryMetadata<?, ?, ?> metadata
    ) {
        try {
            // Check if this is a GenericCachedRepository (has keyExtractor)
            if (metadata.hasKeyExtractor() && GenericCachedRepository.class.isAssignableFrom(repositoryClass)) {
                // GenericCachedRepository constructor: (ExecutorService, EntityManagerFactory, Class<T>, Function<T, K>)
                final Constructor<?> constructor = repositoryClass.getDeclaredConstructor(
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
                // AbstractCRUDRepository constructor: (ExecutorService, EntityManagerFactory, Class<T>)
                final Constructor<?> constructor = repositoryClass.getDeclaredConstructor(
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
            throw new IllegalStateException(
                "Failed to create repository instance for " + repositoryClass.getName() + 
                ". Ensure the repository has a valid constructor matching AbstractCRUDRepository or GenericCachedRepository.",
                e
            );
        }
    }


    /**
     * Injects repository instances into fields annotated with @InjectRepository.
     * <p>
     * Scans all fields in the target object's class hierarchy and injects repository instances
     * into fields marked with the @InjectRepository annotation. Supports private fields and
     * is idempotent (safe to call multiple times on the same instance).
     * </p>
     * 
     * @param target the object to inject repositories into
     * @throws NullPointerException if target is null
     * @throws IllegalStateException if a field's repository type is not registered or injection fails
     */
    public void injectInto(@NotNull final Object target) {
        final Class<?> targetClass = target.getClass();
        
        // Scan all fields in the class hierarchy
        for (Class<?> currentClass = targetClass; currentClass != null; currentClass = currentClass.getSuperclass()) {
            for (final Field field : currentClass.getDeclaredFields()) {
                // Check if field is annotated with @InjectRepository
                if (!field.isAnnotationPresent(InjectRepository.class)) {
                    continue;
                }

                final Class<?> fieldType = field.getType();

                // Validate that the field type is registered
                if (!this.registrations.containsKey(fieldType)) {
                    throw new IllegalStateException(
                        "No repository registered for type " + fieldType.getName() +
                        " (field: " + field.getName() + " in " + targetClass.getName() + "). " +
                        "Register the repository using RepositoryManager.register() before injection."
                    );
                }

                // Get or create the repository instance
                final Object repositoryInstance = this.createRepository(fieldType);
                
                // Inject the instance into the field
                try {
                    field.setAccessible(true);
                    field.set(target, repositoryInstance);
                } catch (final IllegalAccessException e) {
                    throw new IllegalStateException(
                        "Failed to inject repository into field " + field.getName() + 
                        " in " + targetClass.getName(),
                        e
                    );
                }
            }
        }
    }
}

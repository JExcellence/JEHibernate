package de.jexcellence.jehibernate.repository.manager;

import de.jexcellence.jehibernate.exception.RepositoryException;
import de.jexcellence.jehibernate.repository.base.Repository;
import de.jexcellence.jehibernate.repository.injection.InjectionProcessor;
import de.jexcellence.jehibernate.scanner.RepositoryScanner;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Central registry for managing repository instances.
 * <p>
 * RepositoryRegistry handles repository lifecycle, registration, instantiation,
 * and dependency injection. It provides a singleton-like access pattern for
 * repositories while managing their creation and caching.
 * <p>
 * <b>Features:</b>
 * <ul>
 *   <li>Automatic repository instantiation</li>
 *   <li>Singleton pattern per repository type</li>
 *   <li>Thread-safe concurrent access</li>
 *   <li>Automatic scanning and registration</li>
 *   <li>Dependency injection support</li>
 * </ul>
 * <p>
 * <b>Basic Usage:</b>
 * <pre>{@code
 * var jeHibernate = JEHibernate.builder()
 *     .scanPackages("com.example")
 *     .build();
 * 
 * // Get repository (automatically instantiated and cached)
 * var userRepo = jeHibernate.repositories().get(UserRepository.class);
 * }</pre>
 * <p>
 * <b>Manual Registration:</b>
 * <pre>{@code
 * var registry = jeHibernate.repositories();
 * registry.register(UserRepository.class, User.class);
 * var userRepo = registry.get(UserRepository.class);
 * }</pre>
 * <p>
 * <b>Dependency Injection:</b>
 * <pre>{@code
 * public class UserService {
 *     @Inject
 *     private UserRepository userRepo;
 * }
 * 
 * var service = new UserService();
 * jeHibernate.repositories().injectInto(service);
 * 
 * // Or create with injection
 * var service = jeHibernate.repositories().createWithInjection(UserService.class);
 * }</pre>
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe and uses ConcurrentHashMap
 * for repository caching. Multiple threads can safely access repositories.
 *
 * @author JEHibernate
 * @version 2.0
 * @since 1.0
 * @see Repository
 * @see RepositoryScanner
 * @see InjectionProcessor
 */
public final class RepositoryRegistry {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryRegistry.class);

    private final RepositoryFactory factory;
    private final Map<Class<?>, Object> repositories = new ConcurrentHashMap<>();
    private final Map<Class<?>, Class<?>> registrations = new ConcurrentHashMap<>();
    
    public RepositoryRegistry(ExecutorService executorService, EntityManagerFactory entityManagerFactory) {
        this.factory = new RepositoryFactory(executorService, entityManagerFactory);
    }
    
    public <T, ID, R extends Repository<T, ID>> void register(Class<R> repositoryClass, Class<T> entityClass) {
        registrations.put(repositoryClass, entityClass);
        LOGGER.debug("Registered repository: {} for entity: {}", 
            repositoryClass.getSimpleName(), entityClass.getSimpleName());
    }
    
    @SuppressWarnings("unchecked")
    public <R> R get(Class<R> repositoryClass) {
        return (R) repositories.computeIfAbsent(repositoryClass, clazz -> {
            Class<?> entityClass = registrations.get(clazz);
            if (entityClass == null) {
                throw new RepositoryException(
                    "Repository not registered: " + clazz.getName() +
                    ". Call register() or scanAndRegister() first."
                );
            }
            LOGGER.debug("Instantiating repository: {}", clazz.getSimpleName());
            return factory.create(clazz, entityClass);
        });
    }
    
    @SuppressWarnings("unchecked")
    public void scanAndRegister(String... basePackages) {
        Map<Class<?>, Class<?>> discovered = RepositoryScanner.scan(basePackages);
        discovered.forEach((repoClass, entityClass) -> 
            register((Class) repoClass, entityClass));
    }
    
    public void injectInto(Object target) {
        InjectionProcessor.process(target, this);
    }
    
    public <T> T createWithInjection(Class<T> targetClass) {
        try {
            T instance = targetClass.getDeclaredConstructor().newInstance();
            injectInto(instance);
            return instance;
        } catch (Exception e) {
            throw new RepositoryException(
                "Failed to create instance with injection: " + targetClass.getName(),
                e
            );
        }
    }
    
    public void clear() {
        repositories.clear();
        registrations.clear();
    }
}

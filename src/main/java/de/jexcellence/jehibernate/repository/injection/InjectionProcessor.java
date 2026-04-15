package de.jexcellence.jehibernate.repository.injection;

import de.jexcellence.jehibernate.exception.RepositoryException;
import de.jexcellence.jehibernate.repository.manager.RepositoryRegistry;

import java.lang.reflect.Field;

/**
 * Processor for automatic dependency injection of repository instances into target objects.
 * <p>
 * This class provides reflection-based field injection for fields annotated with {@link Inject}.
 * It traverses the class hierarchy to inject repositories into both the target class and its
 * superclasses.
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * public class OrderService {
 *     @Inject
 *     private OrderRepository orderRepository;
 *     
 *     @Inject
 *     private UserRepository userRepository;
 *     
 *     public void createOrder(Long userId, OrderData data) {
 *         User user = userRepository.findById(userId).orElseThrow();
 *         Order order = new Order(user, data);
 *         orderRepository.save(order);
 *     }
 * }
 * 
 * // Initialize and inject dependencies
 * OrderService service = new OrderService();
 * InjectionProcessor.process(service, repositoryRegistry);
 * service.createOrder(userId, orderData);
 * }</pre>
 * 
 * <h2>Features:</h2>
 * <ul>
 *   <li>Automatic field injection based on {@link Inject} annotation</li>
 *   <li>Type-safe injection using field types</li>
 *   <li>Supports inheritance (processes entire class hierarchy)</li>
 *   <li>Handles private fields via reflection</li>
 *   <li>Throws {@link RepositoryException} on injection failures</li>
 * </ul>
 * 
 * <h2>Injection Process:</h2>
 * <ol>
 *   <li>Traverses class hierarchy from target class to Object</li>
 *   <li>Identifies fields annotated with {@link Inject}</li>
 *   <li>Retrieves repository instance from registry by field type</li>
 *   <li>Sets field value using reflection (bypasses access modifiers)</li>
 * </ol>
 * 
 * @since 1.0
 * @see Inject
 * @see RepositoryRegistry
 */
public final class InjectionProcessor {
    
    private InjectionProcessor() {
    }
    
    /**
     * Processes the target object and injects repository instances into fields annotated with {@link Inject}.
     * <p>
     * This method traverses the entire class hierarchy of the target object, including superclasses,
     * and injects repository instances from the registry into all annotated fields.
     * 
     * @param target the object to process for dependency injection
     * @param registry the repository registry containing available repository instances
     * @throws RepositoryException if injection fails due to access issues or missing repositories
     */
    public static void process(Object target, RepositoryRegistry registry) {
        Class<?> targetClass = target.getClass();
        
        for (Class<?> current = targetClass; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(Inject.class)) {
                    injectField(target, field, registry);
                }
            }
        }
    }
    
    /**
     * Injects a repository instance into a specific field of the target object.
     * 
     * @param target the object containing the field
     * @param field the field to inject
     * @param registry the repository registry to retrieve the repository from
     * @throws RepositoryException if the field cannot be accessed or set
     */
    private static void injectField(Object target, Field field, RepositoryRegistry registry) {
        Class<?> fieldType = field.getType();
        
        try {
            Object repository = registry.get(fieldType);
            field.setAccessible(true);
            field.set(target, repository);
        } catch (IllegalAccessException e) {
            throw new RepositoryException(
                "Failed to inject field: " + field.getName() + " in " + target.getClass().getName(),
                e
            );
        }
    }
}

package de.jexcellence.jehibernate.repository.injection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking repository fields that should be automatically injected.
 * <p>
 * Fields annotated with {@code @Inject} will be automatically populated with
 * repository instances from the {@link de.jexcellence.jehibernate.repository.manager.RepositoryRegistry}
 * when processed by {@link InjectionProcessor}.
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * public class UserService {
 *     @Inject
 *     private UserRepository userRepository;
 *     
 *     @Inject
 *     private OrderRepository orderRepository;
 *     
 *     public void processUser(Long userId) {
 *         User user = userRepository.findById(userId).orElseThrow();
 *         List<Order> orders = orderRepository.findByUserId(userId);
 *         // Process user and orders
 *     }
 * }
 * 
 * // Injection processing
 * UserService service = new UserService();
 * InjectionProcessor.process(service, repositoryRegistry);
 * }</pre>
 * 
 * <h2>Features:</h2>
 * <ul>
 *   <li>Automatic repository dependency injection</li>
 *   <li>Type-safe field injection based on field type</li>
 *   <li>Works with inheritance (processes superclass fields)</li>
 *   <li>Runtime reflection-based injection</li>
 * </ul>
 * 
 * @since 1.0
 * @see InjectionProcessor
 * @see de.jexcellence.jehibernate.repository.manager.RepositoryRegistry
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
}

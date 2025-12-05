package de.jexcellence.hibernate.repository;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks fields that should receive repository instances through dependency injection.
 * <p>
 * This annotation is used by the {@link RepositoryManager} to automatically inject
 * repository instances into fields at runtime. The field type must match a registered
 * repository class.
 * </p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * public class UserService {
 *     {@literal @}InjectRepository
 *     private UserRepository userRepository;
 *     
 *     public User findUser(UUID id) {
 *         return userRepository.findById(id);
 *     }
 * }
 * </pre>
 * 
 * @see RepositoryManager#injectInto(Object)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InjectRepository {
}

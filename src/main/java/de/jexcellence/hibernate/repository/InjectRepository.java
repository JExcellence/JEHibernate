package de.jexcellence.hibernate.repository;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks classes or fields that should receive repository instances through dependency injection.
 *
 * <p>Can be applied at class level to inject all repository fields, or at field level for selective injection.
 *
 * <p>Class-level example:
 * <pre>{@code
 * @InjectRepository
 * public class UserService {
 *     private UserRepository userRepository;
 *     private PlayerRepository playerRepository;
 * }
 * }</pre>
 *
 * <p>Field-level example:
 * <pre>{@code
 * public class UserService {
 *     @InjectRepository
 *     private UserRepository userRepository;
 * }
 * }</pre>
 *
 * @author JExcellence
 * @version 1.0
 * @since 1.0
 * @see RepositoryManager#injectInto(Object)
 * @see RepositoryManager#register(Class, Class) (Class)
 * @see RepositoryManager#createWithInjection(Class) (Class, Object...)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface InjectRepository {
}

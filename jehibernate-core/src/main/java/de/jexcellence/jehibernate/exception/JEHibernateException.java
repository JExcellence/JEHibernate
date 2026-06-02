package de.jexcellence.jehibernate.exception;

import java.io.Serial;

/**
 * Base exception class for all JEHibernate-specific exceptions.
 * <p>
 * This is the root of the JEHibernate exception hierarchy. All custom exceptions
 * in the library extend this class, making it easy to catch all JEHibernate-related
 * errors with a single catch block.
 * <p>
 * <b>Exception Hierarchy:</b>
 * <pre>
 * JEHibernateException (base)
 *   ├── TransactionException
 *   ├── RepositoryException
 *   │   └── EntityNotFoundException
 *   ├── ConfigurationException
 *   └── ValidationException
 * </pre>
 * <p>
 * <b>Example Usage:</b>
 * <pre>{@code
 * try {
 *     userRepo.create(user);
 * } catch (JEHibernateException e) {
 *     // Catches all JEHibernate exceptions
 *     logger.error("JEHibernate error", e);
 * }
 * }</pre>
 *
 * @author JEHibernate
 * @version 2.0
 * @since 1.0
 * @see TransactionException
 * @see RepositoryException
 * @see ConfigurationException
 * @see ValidationException
 */
public class JEHibernateException extends RuntimeException {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    public JEHibernateException(String message) {
        super(message);
    }
    
    public JEHibernateException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public JEHibernateException(Throwable cause) {
        super(cause);
    }
}

package de.jexcellence.jehibernate.exception;

import java.io.Serial;

/**
 * Exception thrown when entity validation fails.
 * <p>
 * This exception is used to indicate that an entity does not meet validation
 * requirements before being persisted or updated in the database.
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * if (user.getEmail() == null || user.getEmail().isBlank()) {
 *     throw new ValidationException("Email is required");
 * }
 * }</pre>
 * 
 * @since 1.0
 * @see JEHibernateException
 */
public final class ValidationException extends JEHibernateException {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    /**
     * Constructs a new ValidationException with the specified detail message.
     * 
     * @param message the detail message describing the validation failure
     */
    public ValidationException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new ValidationException with the specified detail message and cause.
     * 
     * @param message the detail message describing the validation failure
     * @param cause the underlying cause
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

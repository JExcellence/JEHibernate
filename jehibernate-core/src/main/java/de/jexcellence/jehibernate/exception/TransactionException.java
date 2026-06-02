package de.jexcellence.jehibernate.exception;

import java.io.Serial;

/**
 * Exception thrown when a transaction operation fails.
 * <p>
 * This exception is thrown when database transaction operations fail, including:
 * <ul>
 *   <li>Transaction commit failures</li>
 *   <li>Persistence operation errors</li>
 *   <li>Database constraint violations</li>
 *   <li>Connection errors during transactions</li>
 * </ul>
 * <p>
 * The exception uses Java 24 pattern matching for automatic classification
 * of underlying JPA/Hibernate exceptions.
 * <p>
 * <b>Example:</b>
 * <pre>{@code
 * try {
 *     userRepo.create(user);
 * } catch (TransactionException e) {
 *     logger.error("Transaction failed", e);
 *     // Handle transaction failure
 * }
 * }</pre>
 *
 * @author JEHibernate
 * @version 2.0
 * @since 1.0
 * @see JEHibernateException
 */
public class TransactionException extends JEHibernateException {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    public TransactionException(String message) {
        super(message);
    }
    
    public TransactionException(String message, Throwable cause) {
        super(message, cause);
    }
}

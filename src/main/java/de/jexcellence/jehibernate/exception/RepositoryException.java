package de.jexcellence.jehibernate.exception;

import java.io.Serial;

/**
 * Exception thrown when repository operations fail.
 * <p>
 * This exception is thrown for repository-level errors such as:
 * <ul>
 *   <li>Repository not found or not registered</li>
 *   <li>Invalid repository configuration</li>
 *   <li>Repository instantiation failures</li>
 * </ul>
 * <p>
 * <b>Example:</b>
 * <pre>{@code
 * try {
 *     var repo = jeHibernate.repositories().get(UserRepository.class);
 * } catch (RepositoryException e) {
 *     logger.error("Repository not found", e);
 * }
 * }</pre>
 *
 * @author JEHibernate
 * @version 2.0
 * @since 1.0
 * @see JEHibernateException
 * @see EntityNotFoundException
 */
public class RepositoryException extends JEHibernateException {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    public RepositoryException(String message) {
        super(message);
    }
    
    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}

package de.jexcellence.jehibernate.exception;

import java.io.Serial;

/**
 * Exception thrown when an entity cannot be found in the database.
 * <p>
 * This exception is typically thrown by repository methods when attempting to retrieve
 * an entity by its identifier and the entity does not exist.
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * User user = userRepository.findById(userId)
 *     .orElseThrow(() -> new EntityNotFoundException(User.class, userId));
 * }</pre>
 * 
 * @since 1.0
 * @see RepositoryException
 */
@SuppressWarnings("java:S110")
public final class EntityNotFoundException extends RepositoryException {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    /**
     * Constructs a new EntityNotFoundException with the specified detail message.
     * 
     * @param message the detail message
     */
    public EntityNotFoundException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new EntityNotFoundException with a formatted message containing
     * the entity class name and identifier.
     * 
     * @param entityClass the class of the entity that was not found
     * @param id the identifier of the entity that was not found
     */
    public EntityNotFoundException(Class<?> entityClass, Object id) {
        super("Entity %s with id %s not found".formatted(entityClass.getSimpleName(), id));
    }
}

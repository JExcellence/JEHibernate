package de.jexcellence.jehibernate.entity.base;

/**
 * Interface for entities that have an identifier.
 * <p>
 * This interface provides a common contract for all entities with identifiers,
 * enabling generic repository operations and entity management.
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @Entity
 * public class User implements Identifiable<Long> {
 *     @Id
 *     @GeneratedValue
 *     private Long id;
 *     
 *     @Override
 *     public Long getId() {
 *         return id;
 *     }
 *     
 *     @Override
 *     public void setId(Long id) {
 *         this.id = id;
 *     }
 * }
 * 
 * // Check if entity is new
 * User user = new User();
 * if (user.isNew()) {
 *     // Entity has not been persisted yet
 * }
 * }</pre>
 * 
 * @param <I> the type of the entity identifier
 * @since 1.0
 * @see BaseEntity
 */
public interface Identifiable<I> {

    /**
     * Returns the identifier of this entity.
     *
     * @return the entity identifier, or null if the entity is new
     */
    I getId();

    /**
     * Sets the identifier of this entity.
     *
     * @param id the entity identifier
     */
    void setId(I id);
    
    /**
     * Checks if this entity is new (not yet persisted).
     * <p>
     * An entity is considered new if its identifier is null.
     * 
     * @return true if the entity is new, false otherwise
     */
    default boolean isNew() {
        return getId() == null;
    }
}

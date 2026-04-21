package de.jexcellence.jehibernate.entity.base;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.io.Serial;

/**
 * Base entity with custom String ID.
 * <p>
 * This entity allows you to use custom String values as primary keys,
 * useful for natural keys or business identifiers. The ID must be set
 * manually before persisting the entity.
 * <p>
 * <b>Features:</b>
 * <ul>
 *   <li>Custom String ID (you control the value)</li>
 *   <li>Useful for natural keys (email, username, etc.)</li>
 *   <li>Automatic timestamps and optimistic locking</li>
 *   <li>No auto-generation - you set the ID</li>
 * </ul>
 * <p>
 * <b>Example Usage:</b>
 * <pre>{@code
 * @Entity
 * public class Session extends StringIdEntity {
 *     private String token;
 *     private Instant expiresAt;
 *     
 *     protected Session() {}
 *     
 *     public Session(String sessionId, String token) {
 *         setId(sessionId);  // IMPORTANT: Set ID manually
 *         this.token = token;
 *         this.expiresAt = Instant.now().plusSeconds(3600);
 *     }
 *     
 *     // Getters and setters...
 * }
 * 
 * // Usage:
 * String sessionId = UUID.randomUUID().toString();
 * var session = new Session(sessionId, "token123");
 * sessionRepo.create(session);
 * System.out.println("Session ID: " + session.getId());
 * }</pre>
 * <p>
 * <b>When to Use:</b>
 * <ul>
 *   <li>Natural keys (email, username, code)</li>
 *   <li>External system IDs</li>
 *   <li>Business identifiers</li>
 *   <li>Human-readable IDs</li>
 * </ul>
 * <p>
 * <b>Important:</b> You MUST set the ID before persisting:
 * <pre>{@code
 * var entity = new MyEntity();
 * entity.setId("my-custom-id");  // Required!
 * repo.create(entity);
 * }</pre>
 *
 * @author JEHibernate
 * @version 2.0
 * @since 1.0
 * @see BaseEntity
 * @see LongIdEntity
 * @see UuidEntity
 */
@MappedSuperclass
public abstract class StringIdEntity extends BaseEntity<String> {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    @Id
    private String id;
    
    protected StringIdEntity() {
    }
    
    @Override
    public String getId() {
        return id;
    }
    
    @Override
    public void setId(String id) {
        if (!isNew()) {
            throw new IllegalStateException("ID cannot be changed after persistence");
        }
        this.id = id;
    }

    /**
     * Delegates to {@link de.jexcellence.jehibernate.entity.base.BaseEntity#equals(Object)},
     * which compares by database ID (or by identity token for transient instances).
     * Explicitly overridden here to satisfy static-analysis tools that require
     * subclasses adding fields to declare equals/hashCode.
     */
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}

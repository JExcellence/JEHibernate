package de.jexcellence.jehibernate.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Abstract base entity providing common fields and functionality for all entities.
 * <p>
 * This mapped superclass provides:
 * <ul>
 *   <li><b>Automatic timestamps</b> - createdAt and updatedAt managed automatically</li>
 *   <li><b>Optimistic locking</b> - version field for concurrent update detection</li>
 *   <li><b>Proper equals/hashCode</b> - based on entity ID</li>
 *   <li><b>Serialization support</b> - implements Serializable</li>
 * </ul>
 * <p>
 * <b>Concrete Implementations:</b>
 * <ul>
 *   <li>{@link LongIdEntity} - Auto-increment Long ID (most common)</li>
 *   <li>{@link UuidEntity} - Auto-generated UUID (distributed systems)</li>
 *   <li>{@link StringIdEntity} - Custom String ID (natural keys)</li>
 * </ul>
 * <p>
 * <b>Example Usage:</b>
 * <pre>{@code
 * @Entity
 * public class User extends LongIdEntity {
 *     private String username;
 *     private String email;
 *     
 *     // Getters and setters...
 * }
 * 
 * // Timestamps are automatic:
 * var user = new User("alice", "alice@example.com");
 * userRepo.create(user);
 * System.out.println(user.getCreatedAt());  // Automatically set
 * System.out.println(user.getUpdatedAt());  // Automatically set
 * }</pre>
 * <p>
 * <b>Optimistic Locking:</b>
 * The version field enables optimistic locking to detect concurrent modifications:
 * <pre>{@code
 * // Thread 1 and Thread 2 both load the same entity
 * User user1 = repo.findById(1L).get();
 * User user2 = repo.findById(1L).get();
 * 
 * // Thread 1 updates and saves
 * user1.setEmail("new1@example.com");
 * repo.update(user1);  // Success, version incremented
 * 
 * // Thread 2 tries to update
 * user2.setEmail("new2@example.com");
 * repo.update(user2);  // Throws OptimisticLockException
 * }</pre>
 *
 * @param <I> the ID type (Long, UUID, or String)
 * @author JEHibernate
 * @version 2.0
 * @since 1.0
 * @see LongIdEntity
 * @see UuidEntity
 * @see StringIdEntity
 * @see Identifiable
 */
@MappedSuperclass
public abstract class BaseEntity<I> implements Identifiable<I>, Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Transient identity token for correct equals/hashCode behavior before persistence.
     * Without this, all new entities (getId() == null) would be considered equal,
     * breaking HashSet/HashMap usage. Not serialized — only used for in-memory identity.
     */
    private final transient UUID identityToken = UUID.randomUUID();

    @Version
    private int version;
    
    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    private Instant updatedAt;
    
    protected BaseEntity() {
    }
    
    @PrePersist
    protected void onPrePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }
    
    @PreUpdate
    protected void onPreUpdate() {
        updatedAt = Instant.now();
    }
    
    public int getVersion() {
        return version;
    }
    
    protected void setVersion(int version) {
        this.version = version;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        if (!isNew()) {
            throw new IllegalStateException("createdAt cannot be changed after persistence");
        }
        this.createdAt = createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BaseEntity<?> that)) return false;
        if (getId() != null && that.getId() != null) {
            return Objects.equals(getId(), that.getId());
        }
        return Objects.equals(identityToken, that.identityToken);
    }

    @Override
    public int hashCode() {
        return getId() != null ? Objects.hash(getId()) : identityToken.hashCode();
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
            "id=" + getId() +
            ", version=" + version +
            ", createdAt=" + createdAt +
            ", updatedAt=" + updatedAt +
            '}';
    }
}

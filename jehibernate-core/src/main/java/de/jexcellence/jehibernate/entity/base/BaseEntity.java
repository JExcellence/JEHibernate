package de.jexcellence.jehibernate.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import org.hibernate.annotations.ColumnDefault;
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

    /**
     * Optimistic-locking version. The column is {@code NOT NULL DEFAULT 0}: Hibernate 7.2+ maps
     * {@code @Version} as {@code NOT NULL} unconditionally (it ignores {@code @Column(nullable = true)}),
     * and {@link ColumnDefault @ColumnDefault("0")} puts the default into the generated DDL.
     * <p>
     * The field is a boxed {@code Integer} (not primitive {@code int}) only for transition tolerance:
     * a database created by JEHibernate 4.0.1/4.0.2 has a <em>nullable</em> version column that may
     * hold {@code null} on rows predating it. Until such a column is migrated to {@code NOT NULL}
     * (see the migration guide), reading one of those rows must not blow up on unboxing, so the
     * getter is null-safe and returns {@code 0}. On a schema created by 4.1+ the column is never null.
     * <p>
     * Adding this column to an <em>already-populated</em> table is a migration step (Flyway/Liquibase),
     * not something {@code hbm2ddl.auto=update} can do: Hibernate 7.2+ emits
     * {@code add column version integer not null}, which databases reject for existing rows, and the
     * failure aborts the whole schema update. The migration guide ships the {@code ALTER TABLE} to run.
     * <p>
     * Defaulted to {@code 0} so a freshly constructed, detached entity — e.g.
     * {@code new X(); x.setId(existingId)} passed to {@code createOrUpdate}/{@code merge} — carries
     * version {@code 0} and matches an existing row's {@code where … and version=0}.
     */
    @Version
    @ColumnDefault("0")
    private Integer version = 0;

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
    
    /**
     * Returns the optimistic-locking version. Null-safe: a legacy row from a 4.0.1/4.0.2 nullable
     * column that still holds {@code null} (before the NOT NULL migration) reads as {@code 0}.
     *
     * @return the version, or {@code 0} if not yet set
     */
    public int getVersion() {
        return version == null ? 0 : version;
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
            ", version=" + getVersion() +
            ", createdAt=" + createdAt +
            ", updatedAt=" + updatedAt +
            '}';
    }
}

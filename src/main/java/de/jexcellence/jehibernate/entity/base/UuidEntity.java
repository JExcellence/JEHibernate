package de.jexcellence.jehibernate.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.io.Serial;
import java.util.UUID;

/**
 * Base entity with auto-generated UUID.
 * <p>
 * This entity uses UUID (Universally Unique Identifier) as the primary key,
 * which is automatically generated before persistence. UUIDs are ideal for
 * distributed systems where entities may be created on different nodes.
 * <p>
 * <b>Features:</b>
 * <ul>
 *   <li>Auto-generated UUID (version 4)</li>
 *   <li>Stored as BINARY(16) for efficiency</li>
 *   <li>Globally unique across all databases</li>
 *   <li>No database round-trip needed for ID generation</li>
 *   <li>Automatic timestamps and optimistic locking</li>
 * </ul>
 * <p>
 * <b>Example Usage:</b>
 * <pre>{@code
 * @Entity
 * public class Player extends UuidEntity {
 *     private String username;
 *     private String server;
 *     
 *     protected Player() {}
 *     
 *     public Player(String username) {
 *         this.username = username;
 *     }
 *     
 *     // Getters and setters...
 * }
 * 
 * // Usage:
 * var player = new Player("alice");
 * playerRepo.create(player);
 * System.out.println("Generated UUID: " + player.getId());
 * // e.g., 550e8400-e29b-41d4-a716-446655440000
 * }</pre>
 * <p>
 * <b>When to Use:</b>
 * <ul>
 *   <li>Distributed systems with multiple databases</li>
 *   <li>Microservices architecture</li>
 *   <li>Need to generate IDs before database insert</li>
 *   <li>Want globally unique identifiers</li>
 *   <li>Security (IDs are not sequential/predictable)</li>
 * </ul>
 * <p>
 * <b>Performance Note:</b> UUIDs are stored as BINARY(16) for optimal storage
 * and indexing performance (16 bytes vs 36 bytes for string representation).
 *
 * @author JEHibernate
 * @version 2.0
 * @since 1.0
 * @see BaseEntity
 * @see LongIdEntity
 * @see StringIdEntity
 */
@MappedSuperclass
public abstract class UuidEntity extends BaseEntity<UUID> {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;
    
    protected UuidEntity() {
    }
    
    @Override
    protected void onPrePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        super.onPrePersist();
    }
    
    @Override
    public UUID getId() {
        return id;
    }
    
    @Override
    public void setId(UUID id) {
        if (!isNew()) {
            throw new IllegalStateException("ID cannot be changed after persistence");
        }
        this.id = id;
    }
}

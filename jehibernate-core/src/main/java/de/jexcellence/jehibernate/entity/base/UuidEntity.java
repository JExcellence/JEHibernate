package de.jexcellence.jehibernate.entity.base;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 *   <li>Stored using each database's native UUID type (dialect-portable; see below)</li>
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
 * <b>Storage Note:</b> the id is mapped via {@link org.hibernate.type.SqlTypes#UUID}, so Hibernate
 * emits the dialect-native type: {@code uuid} on PostgreSQL and H2, {@code binary(16)} on MySQL and
 * MariaDB, {@code uniqueidentifier} on SQL Server. In every case this is a compact 16-byte key rather
 * than the 36-character string representation.
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
    
    /**
     * UUID primary key mapped via {@link SqlTypes#UUID} so Hibernate picks the portable, dialect-native
     * SQL type instead of a hard-coded {@code BINARY(16)} literal: {@code uuid} on PostgreSQL and H2,
     * {@code binary(16)} on MySQL/MariaDB, {@code uniqueidentifier} on SQL Server. A raw
     * {@code columnDefinition = "BINARY(16)"} was previously forced into the DDL, which PostgreSQL
     * rejects (it has no {@code BINARY} type), so table creation failed there entirely.
     */
    @Id
    @JdbcTypeCode(SqlTypes.UUID)
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

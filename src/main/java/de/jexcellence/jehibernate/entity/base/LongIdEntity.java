package de.jexcellence.jehibernate.entity.base;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.io.Serial;

/**
 * Base entity with auto-increment Long ID.
 * <p>
 * This is the most commonly used base entity, providing an auto-generated
 * Long ID using database identity columns (AUTO_INCREMENT in MySQL, SERIAL in PostgreSQL).
 * <p>
 * <b>Features:</b>
 * <ul>
 *   <li>Auto-generated Long ID</li>
 *   <li>Automatic timestamps (createdAt, updatedAt)</li>
 *   <li>Optimistic locking (version field)</li>
 *   <li>Proper equals/hashCode based on ID</li>
 * </ul>
 * <p>
 * <b>Example Usage:</b>
 * <pre>{@code
 * @Entity
 * public class User extends LongIdEntity {
 *     private String username;
 *     private String email;
 *     private boolean active;
 *     
 *     protected User() {}  // JPA requires no-arg constructor
 *     
 *     public User(String username, String email) {
 *         this.username = username;
 *         this.email = email;
 *         this.active = true;
 *     }
 *     
 *     // Getters and setters...
 * }
 * 
 * // Usage:
 * var user = new User("alice", "alice@example.com");
 * userRepo.create(user);
 * System.out.println("Generated ID: " + user.getId());  // e.g., 1
 * }</pre>
 * <p>
 * <b>When to Use:</b>
 * <ul>
 *   <li>Single database instance</li>
 *   <li>Sequential IDs are acceptable</li>
 *   <li>Most common use case</li>
 * </ul>
 * <p>
 * <b>Database Support:</b> Works with all supported databases (MySQL, PostgreSQL, H2, etc.)
 *
 * @author JEHibernate
 * @version 2.0
 * @since 1.0
 * @see BaseEntity
 * @see UuidEntity
 * @see StringIdEntity
 */
@MappedSuperclass
public abstract class LongIdEntity extends BaseEntity<Long> {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    protected LongIdEntity() {
    }
    
    @Override
    public Long getId() {
        return id;
    }
    
    @Override
    public void setId(Long id) {
        if (!isNew()) {
            throw new IllegalStateException("ID cannot be changed after persistence");
        }
        this.id = id;
    }
}

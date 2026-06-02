package de.jexcellence.jehibernate.naming;

import org.hibernate.boot.model.naming.PhysicalNamingStrategy;

/**
 * Marker interface for custom naming strategies in JEHibernate.
 * <p>
 * This interface extends Hibernate's {@link PhysicalNamingStrategy} to provide
 * a type-safe way to define custom naming conventions for database objects.
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Use the built-in snake_case strategy
 * DatabaseConfig config = DatabaseConfig.builder()
 *     .namingStrategy(new SnakeCaseStrategy())
 *     .build();
 * 
 * // Or implement a custom strategy
 * public class CustomNamingStrategy implements NamingStrategy {
 *     // Implement PhysicalNamingStrategy methods
 * }
 * }</pre>
 * 
 * @since 1.0
 * @see SnakeCaseStrategy
 * @see PhysicalNamingStrategy
 */
public interface NamingStrategy extends PhysicalNamingStrategy {
}

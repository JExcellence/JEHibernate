package de.jexcellence.jehibernate.repository.query;

import jakarta.persistence.criteria.Path;

import java.util.Collection;

/**
 * Utility class providing factory methods for creating common {@link Specification} instances.
 * <p>
 * This class offers a fluent API for building type-safe query specifications without
 * directly working with the JPA Criteria API. Specifications can be combined using
 * logical operators to create complex queries.
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Simple equality
 * Specification<User> activeSpec = Specifications.equalTo("active", true);
 * 
 * // Complex query with multiple conditions
 * Specification<User> spec = Specifications.equalTo("active", true)
 *     .and(Specifications.like("email", "%@example.com"))
 *     .and(Specifications.greaterThan("age", 18))
 *     .and(Specifications.isNotNull("lastLogin"));
 * 
 * List<User> users = userRepository.findAll(spec);
 * 
 * // Range query
 * Specification<Order> dateRange = Specifications.between("createdAt", startDate, endDate);
 * 
 * // IN clause
 * Specification<Product> categories = Specifications.in("category", List.of("Electronics", "Books"));
 * 
 * // Nested properties
 * Specification<Order> userCity = Specifications.equalTo("user.address.city", "New York");
 * }</pre>
 * 
 * <h2>Supported Operations:</h2>
 * <ul>
 *   <li>Equality: {@code equal}, {@code notEqual}</li>
 *   <li>Null checks: {@code isNull}, {@code isNotNull}</li>
 *   <li>String matching: {@code like}</li>
 *   <li>Comparisons: {@code greaterThan}, {@code lessThan}, {@code greaterThanOrEqual}, {@code lessThanOrEqual}</li>
 *   <li>Range: {@code between}</li>
 *   <li>Collection: {@code in}</li>
 *   <li>Nested properties: Use dot notation (e.g., "user.address.city")</li>
 * </ul>
 * 
 * @since 1.0
 * @see Specification
 */
public final class Specifications {
    
    private Specifications() {
    }
    
    /**
     * Creates a specification for equality comparison.
     *
     * <p>Named {@code equalTo} (not {@code equal}) to avoid confusion with
     * {@link Object#equals(Object)}, which this method does not override.
     *
     * @param <T>   the entity type
     * @param field the field name (supports nested properties with dot notation)
     * @param value the expected value
     * @return a specification for the equality condition
     */
    public static <T> Specification<T> equalTo(String field, Object value) {
        return (root, query, cb) -> cb.equal(resolvePath(root, field), value);
    }

    /**
     * Creates a specification for inequality comparison.
     *
     * <p>Named {@code notEqualTo} (not {@code notEqual}) for symmetry with
     * {@link #equalTo(String, Object)}.
     *
     * @param <T>   the entity type
     * @param field the field name
     * @param value the value to compare
     * @return a specification for the inequality condition
     */
    public static <T> Specification<T> notEqualTo(String field, Object value) {
        return (root, query, cb) -> cb.notEqual(resolvePath(root, field), value);
    }
    
    /**
     * Creates a specification for LIKE pattern matching.
     * 
     * @param <T> the entity type
     * @param field the field name
     * @param pattern the LIKE pattern (use % for wildcards)
     * @return a specification for the LIKE condition
     */
    public static <T> Specification<T> like(String field, String pattern) {
        return (root, query, cb) -> cb.like(resolvePath(root, field), pattern);
    }
    
    /**
     * Creates a specification for IN clause.
     * 
     * @param <T> the entity type
     * @param field the field name
     * @param values the collection of values
     * @return a specification for the IN condition
     */
    public static <T> Specification<T> in(String field, Collection<?> values) {
        return (root, query, cb) -> resolvePath(root, field).in(values);
    }
    
    /**
     * Creates a specification for IS NULL check.
     * 
     * @param <T> the entity type
     * @param field the field name
     * @return a specification for the IS NULL condition
     */
    public static <T> Specification<T> isNull(String field) {
        return (root, query, cb) -> cb.isNull(resolvePath(root, field));
    }
    
    /**
     * Creates a specification for IS NOT NULL check.
     * 
     * @param <T> the entity type
     * @param field the field name
     * @return a specification for the IS NOT NULL condition
     */
    public static <T> Specification<T> isNotNull(String field) {
        return (root, query, cb) -> cb.isNotNull(resolvePath(root, field));
    }
    
    /**
     * Creates a specification for greater than comparison.
     * 
     * @param <T> the entity type
     * @param field the field name
     * @param value the value to compare
     * @return a specification for the greater than condition
     */
    @SuppressWarnings("unchecked")
    public static <T> Specification<T> greaterThan(String field, Comparable<?> value) {
        return (root, query, cb) -> cb.greaterThan(resolvePath(root, field), (Comparable) value);
    }
    
    /**
     * Creates a specification for less than comparison.
     * 
     * @param <T> the entity type
     * @param field the field name
     * @param value the value to compare
     * @return a specification for the less than condition
     */
    @SuppressWarnings("unchecked")
    public static <T> Specification<T> lessThan(String field, Comparable<?> value) {
        return (root, query, cb) -> cb.lessThan(resolvePath(root, field), (Comparable) value);
    }
    
    /**
     * Creates a specification for greater than or equal comparison.
     * 
     * @param <T> the entity type
     * @param field the field name
     * @param value the value to compare
     * @return a specification for the greater than or equal condition
     */
    @SuppressWarnings("unchecked")
    public static <T> Specification<T> greaterThanOrEqual(String field, Comparable<?> value) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(resolvePath(root, field), (Comparable) value);
    }
    
    /**
     * Creates a specification for less than or equal comparison.
     * 
     * @param <T> the entity type
     * @param field the field name
     * @param value the value to compare
     * @return a specification for the less than or equal condition
     */
    @SuppressWarnings("unchecked")
    public static <T> Specification<T> lessThanOrEqual(String field, Comparable<?> value) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(resolvePath(root, field), (Comparable) value);
    }
    
    /**
     * Creates a specification for BETWEEN range comparison.
     * 
     * @param <T> the entity type
     * @param field the field name
     * @param start the start of the range (inclusive)
     * @param end the end of the range (inclusive)
     * @return a specification for the BETWEEN condition
     */
    @SuppressWarnings("unchecked")
    public static <T> Specification<T> between(String field, Comparable<?> start, Comparable<?> end) {
        return (root, query, cb) -> cb.between(resolvePath(root, field), (Comparable) start, (Comparable) end);
    }
    
    /**
     * Resolves a field path, supporting nested properties with dot notation.
     * <p>
     * For example, "user.address.city" will traverse from root to user, then to address, then to city.
     * 
     * @param <T> the path type
     * @param root the root path
     * @param field the field path (may contain dots for nested properties)
     * @return the resolved path
     */
    @SuppressWarnings("unchecked")
    static <T> Path<T> resolvePath(Path<?> root, String field) {
        if (!field.contains(".")) {
            return root.get(field);
        }
        String[] parts = field.split("\\.");
        Path<T> path = root.get(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            path = path.get(parts[i]);
        }
        return path;
    }
}

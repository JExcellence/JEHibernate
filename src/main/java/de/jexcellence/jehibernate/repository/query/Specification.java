package de.jexcellence.jehibernate.repository.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Functional interface for building JPA Criteria API predicates.
 * <p>
 * Specifications provide a type-safe way to build dynamic queries using the JPA Criteria API.
 * They can be combined using logical operators (and, or, not) to create complex query conditions.
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Simple specification
 * Specification<User> activeUsers = (root, query, cb) -> 
 *     cb.equal(root.get("active"), true);
 * 
 * // Combined specifications
 * Specification<User> spec = Specifications.equal("active", true)
 *     .and(Specifications.like("email", "%@example.com"))
 *     .and(Specifications.greaterThan("age", 18));
 * 
 * List<User> users = userRepository.findAll(spec);
 * 
 * // Negation
 * Specification<User> notDeleted = Specifications.equal("deleted", true).not();
 * }</pre>
 * 
 * @param <T> the entity type
 * @since 1.0
 * @see Specifications
 */
@FunctionalInterface
public interface Specification<T> {
    
    /**
     * Converts this specification to a JPA Criteria API predicate.
     * 
     * @param root the root entity
     * @param query the criteria query
     * @param criteriaBuilder the criteria builder
     * @return the predicate representing this specification
     */
    Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder);
    
    /**
     * Combines this specification with another using logical AND.
     * 
     * @param other the other specification
     * @return a new specification representing the AND combination
     */
    default Specification<T> and(Specification<T> other) {
        return (root, query, cb) -> cb.and(toPredicate(root, query, cb), other.toPredicate(root, query, cb));
    }
    
    /**
     * Combines this specification with another using logical OR.
     * 
     * @param other the other specification
     * @return a new specification representing the OR combination
     */
    default Specification<T> or(Specification<T> other) {
        return (root, query, cb) -> cb.or(toPredicate(root, query, cb), other.toPredicate(root, query, cb));
    }
    
    /**
     * Negates this specification using logical NOT.
     * 
     * @return a new specification representing the negation
     */
    default Specification<T> not() {
        return (root, query, cb) -> cb.not(toPredicate(root, query, cb));
    }
}

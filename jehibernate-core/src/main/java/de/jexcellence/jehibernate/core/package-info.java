/**
 * Main entry points for JEHibernate.
 * <p>
 * {@link JEHibernate} serves as the primary facade and lifecycle manager for the library,
 * coordinating entity scanning, repository registration via {@link de.jexcellence.jehibernate.repository.manager.RepositoryRegistry},
 * and session-scoped transactions. It manages virtual thread executors (or cached thread pool) and HikariCP connection pools,
 * exposing fluent execution methods with session-scoped transactions.
 *
 * @since 1.0
 */
package de.jexcellence.jehibernate.core;
package de.jexcellence.jehibernate.testing;

/**
 * Per-test-method database reset strategy applied by {@link JEHibernateExtension} after each test.
 *
 * @since 4.0
 */
public enum DatabaseReset {
    /** No reset between tests. */
    NONE,
    /** Truncate all mapped tables (FK-aware via Hibernate's SchemaManager). The default. */
    TRUNCATE_ALL,
    /** Drop and recreate all mapped objects between tests (slowest, strongest). */
    DROP_CREATE,
    /**
     * Intended single-transaction rollback. JEHibernate repositories commit per operation, so this
     * cannot wrap arbitrary repository calls; it falls back to {@link #TRUNCATE_ALL} with a warning.
     */
    ROLLBACK_TX
}

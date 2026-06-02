package de.jexcellence.jehibernate.migration;

/**
 * Schema-migration tool selection.
 *
 * @since 4.0
 */
public enum MigrationTool {
    /** Flyway — the JEHibernate default. SQL migrations named {@code V001__description.sql}. */
    FLYWAY,
    /** Liquibase — opt-in for XML/YAML/JSON changelog users. */
    LIQUIBASE,
    /** No migration tool — JEHibernate never runs migrations regardless of classpath. */
    NONE
}

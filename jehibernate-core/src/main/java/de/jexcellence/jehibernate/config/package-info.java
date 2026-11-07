/**
 * Configuration and bootstrapping API.
 * <p>
 * {@link ConfigurationBuilder} assembles database settings, connection pool, migrations, entities,
 * naming strategy, multi-tenacy and Hibernate into a JPA {@code EnityManagerFactory}.
 * Connection settings are held by the immutable
 * {@link DatabaseConfig}, with  the supported database engines in
 * {@link DatabaseType} and file-based loading (classPath, plugin or file-system) via
 * {@link PropertyLoader}.
 *
 * @since 1.0
 */

package de.jexcellence.jehibernate.config;
package de.jexcellence.jehibernate.exception;

import de.jexcellence.jehibernate.config.ConfigurationBuilder;

import java.io.Serial;

/**
 * Exception thrown when configuration is invalid or incomplete.
 * <p>
 * This exception is thrown during JEHibernate initialization when:
 * <ul>
 *   <li>Required configuration is missing</li>
 *   <li>Configuration values are invalid</li>
 *   <li>Database connection cannot be established</li>
 *   <li>Entity or repository scanning fails</li>
 * </ul>
 * <p>
 * <b>Example:</b>
 * <pre>{@code
 * try {
 *     var jeHibernate = JEHibernate.builder()
 *         .configuration(config -> config
 *             .database(DatabaseType.MYSQL)
 *             // Missing URL - will throw ConfigurationException
 *         )
 *         .build();
 * } catch (ConfigurationException e) {
 *     logger.error("Invalid configuration", e);
 * }
 * }</pre>
 *
 * @author JEHibernate
 * @version 2.0
 * @since 1.0
 * @see JEHibernateException
 * @see ConfigurationBuilder
 */
public final class ConfigurationException extends JEHibernateException {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    public ConfigurationException(String message) {
        super(message);
    }
    
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

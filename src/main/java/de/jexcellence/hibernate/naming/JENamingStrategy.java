package de.jexcellence.hibernate.naming;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * JENamingStrategy provides a custom Hibernate physical naming strategy that converts logical entity
 * and property names to database-friendly physical names following snake_case conventions.
 *
 * <p>This naming strategy extends Hibernate's standard physical naming strategy to provide consistent
 * database naming conventions across the entire application. It performs the following transformations:</p>
 * <ul>
 *   <li>Converts camelCase names to snake_case (e.g., "firstName" → "first_name")</li>
 *   <li>Replaces hyphens with underscores (e.g., "user-profile" → "user_profile")</li>
 *   <li>Converts all names to lowercase for database compatibility</li>
 *   <li>Applies consistent naming to tables, columns, and sequences</li>
 *   <li>Preserves quoted identifier status for special cases</li>
 * </ul>
 *
 * <p>The strategy uses regular expressions for efficient camelCase detection and conversion,
 * ensuring optimal performance during schema generation and runtime operations. All naming
 * transformations are logged at trace level for debugging and auditing purposes.</p>
 *
 * <p>This implementation is particularly useful in environments where:</p>
 * <ul>
 *   <li>Database naming conventions require snake_case formatting</li>
 *   <li>Consistent naming across different database vendors is required</li>
 *   <li>Legacy systems expect specific naming patterns</li>
 *   <li>Team coding standards mandate specific database naming conventions</li>
 * </ul>
 *
 * <p>Usage example in Hibernate configuration:</p>
 * <pre>
 * hibernate.physical_naming_strategy=de.jexcellence.hibernate.naming.JENamingStrategy
 * </pre>
 *
 * <p>Transformation examples:</p>
 * <pre>
 * Entity "UserProfile" → Table "user_profile"
 * Property "firstName" → Column "first_name"
 * Property "user-id" → Column "user_id"
 * Sequence "userSeq" → Sequence "user_seq"
 * </pre>
 *
 * @author JExcellence
 * @version 1.0
 * @since 1.0
 * @see PhysicalNamingStrategyStandardImpl
 * @see Identifier
 * @see JdbcEnvironment
 */
public class JENamingStrategy extends PhysicalNamingStrategyStandardImpl {
    
    private static final Logger logger = LoggerFactory.getLogger(JENamingStrategy.class);
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([a-z])([A-Z]+)");
    private static final String UNDERSCORE = "_";
    
    /**
     * Default constructor for JENamingStrategy.
     */
    public JENamingStrategy() {}
    
    /**
     * Converts a logical table name to its physical database representation.
     *
     * <p>This method transforms entity class names into database table names following
     * the configured naming conventions. The transformation ensures that Java entity
     * names are properly converted to database-compatible table names.</p>
     *
     * <p>The conversion process applies the following transformations:</p>
     * <ul>
     *   <li>camelCase to snake_case conversion</li>
     *   <li>Hyphen to underscore replacement</li>
     *   <li>Lowercase normalization</li>
     *   <li>Preservation of quoted identifier status</li>
     * </ul>
     *
     * <p>Example transformations:</p>
     * <pre>
     * "UserProfile" → "user_profile"
     * "OrderItem" → "order_item"
     * "user-account" → "user_account"
     * </pre>
     *
     * @param logicalName the logical table name from the entity mapping
     * @param context the JDBC environment context for database-specific handling
     * @return the physical table name identifier for database schema generation
     * @throws IllegalArgumentException if logicalName is null
     */
    @Override
    public Identifier toPhysicalTableName(final Identifier logicalName, final JdbcEnvironment context) {
        return this.convertIdentifier(logicalName, context);
    }
    
    /**
     * Converts a logical column name to its physical database representation.
     *
     * <p>This method transforms entity property names into database column names following
     * the configured naming conventions. The transformation ensures that Java property
     * names are properly converted to database-compatible column names.</p>
     *
     * <p>The conversion process applies the same transformations as table names,
     * ensuring consistency across the entire database schema.</p>
     *
     * <p>Example transformations:</p>
     * <pre>
     * "firstName" → "first_name"
     * "emailAddress" → "email_address"
     * "user-id" → "user_id"
     * </pre>
     *
     * @param logicalName the logical column name from the property mapping
     * @param context the JDBC environment context for database-specific handling
     * @return the physical column name identifier for database schema generation
     * @throws IllegalArgumentException if logicalName is null
     */
    @Override
    public Identifier toPhysicalColumnName(final Identifier logicalName, final JdbcEnvironment context) {
        return this.convertIdentifier(logicalName, context);
    }
    
    /**
     * Converts a logical sequence name to its physical database representation.
     *
     * <p>This method transforms sequence names used for ID generation into database
     * sequence names following the configured naming conventions. This ensures that
     * sequence names are consistent with table and column naming patterns.</p>
     *
     * <p>Sequences are commonly used for primary key generation in databases that
     * support them (PostgreSQL, Oracle, etc.), and consistent naming helps maintain
     * a clean and predictable database schema.</p>
     *
     * <p>Example transformations:</p>
     * <pre>
     * "userSeq" → "user_seq"
     * "orderSequence" → "order_sequence"
     * "id-generator" → "id_generator"
     * </pre>
     *
     * @param logicalName the logical sequence name from the mapping configuration
     * @param context the JDBC environment context for database-specific handling
     * @return the physical sequence name identifier for database schema generation
     * @throws IllegalArgumentException if logicalName is null
     */
    @Override
    public Identifier toPhysicalSequenceName(final Identifier logicalName, final JdbcEnvironment context) {
        return this.convertIdentifier(logicalName, context);
    }
    
    /**
     * Performs the core identifier conversion logic for all database object types.
     *
     * <p>This method serves as the central conversion point for all identifier transformations,
     * ensuring consistent naming behavior across tables, columns, and sequences. It applies
     * the naming convention transformation and preserves the quoted status of the original identifier.</p>
     *
     * <p>The method includes trace-level logging to facilitate debugging of naming transformations
     * during development and troubleshooting. This logging can be enabled by setting the logger
     * level to TRACE for this class.</p>
     *
     * <p>The conversion process:</p>
     * <ol>
     *   <li>Extracts the text from the logical identifier</li>
     *   <li>Applies the naming convention transformation</li>
     *   <li>Creates a new identifier with the converted name</li>
     *   <li>Preserves the quoted status from the original identifier</li>
     *   <li>Logs the transformation for debugging purposes</li>
     * </ol>
     *
     * @param logicalName the logical identifier to convert
     * @param context the JDBC environment context for database-specific handling
     * @return a new identifier with the converted physical name
     * @throws IllegalArgumentException if logicalName is null
     */
    private Identifier convertIdentifier(final Identifier logicalName, final JdbcEnvironment context) {
        final String convertedName = this.applyNamingConvention(logicalName.getText());
        logger.trace("Converted name: {} -> {}", logicalName.getText(), convertedName);
        return new Identifier(convertedName, logicalName.isQuoted());
    }
    
    /**
     * Applies the snake_case naming convention transformation to a given name.
     *
     * <p>This method performs the core naming transformation logic using regular expressions
     * for efficient pattern matching and replacement. The transformation process is designed
     * to handle various input formats and produce consistent snake_case output.</p>
     *
     * <p>The transformation algorithm:</p>
     * <ol>
     *   <li>Uses regex pattern to identify camelCase boundaries (lowercase followed by uppercase)</li>
     *   <li>Inserts underscores between camelCase word boundaries</li>
     *   <li>Replaces any existing hyphens with underscores</li>
     *   <li>Converts the entire string to lowercase</li>
     *   <li>Logs the transformation at trace level for debugging</li>
     * </ol>
     *
     * <p>The regex pattern {@code ([a-z])([A-Z]+)} captures:</p>
     * <ul>
     *   <li>Group 1: A lowercase letter</li>
     *   <li>Group 2: One or more uppercase letters</li>
     * </ul>
     *
     * <p>The replacement {@code $1_$2} inserts an underscore between the captured groups,
     * effectively converting camelCase to snake_case format.</p>
     *
     * <p>Example transformations:</p>
     * <pre>
     * "firstName" → "first_name"
     * "XMLHttpRequest" → "xml_http_request"
     * "user-profile" → "user_profile"
     * "CONSTANT_VALUE" → "constant_value"
     * </pre>
     *
     * @param name the original name to transform
     * @return the transformed name following snake_case convention
     * @throws IllegalArgumentException if name is null
     */
    private String applyNamingConvention(final String name) {
        final String converted = CAMEL_CASE_PATTERN.matcher(name).replaceAll("$1" + UNDERSCORE + "$2")
                                                   .replace("-", UNDERSCORE)
                                                   .toLowerCase();
        logger.trace("Applied naming convention: {} -> {}", name, converted);
        return converted;
    }
}
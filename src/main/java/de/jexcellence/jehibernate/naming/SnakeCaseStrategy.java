package de.jexcellence.jehibernate.naming;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

import java.io.Serial;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Naming strategy that converts Java camelCase names to database snake_case names.
 * <p>
 * This strategy automatically converts entity and field names from Java naming conventions
 * (camelCase) to database naming conventions (snake_case). For example, {@code userName}
 * becomes {@code user_name}.
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * DatabaseConfig config = DatabaseConfig.builder()
 *     .namingStrategy(new SnakeCaseStrategy())
 *     .build();
 * 
 * @Entity
 * public class UserAccount {  // Table: user_account
 *     @Id
 *     private Long userId;    // Column: user_id
 *     
 *     private String firstName;  // Column: first_name
 * }
 * }</pre>
 * 
 * <h2>Conversion Rules:</h2>
 * <ul>
 *   <li>Converts camelCase to snake_case: {@code userName} → {@code user_name}</li>
 *   <li>Converts hyphens to underscores: {@code user-name} → {@code user_name}</li>
 *   <li>Converts to lowercase</li>
 *   <li>Preserves quoted identifiers</li>
 * </ul>
 * 
 * @since 1.0
 * @see NamingStrategy
 */
public final class SnakeCaseStrategy extends PhysicalNamingStrategyStandardImpl implements NamingStrategy {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private static final Pattern CAMEL_CASE = Pattern.compile("([a-z])([A-Z])");
    
    /**
     * Converts a logical table name to a physical table name using snake_case.
     * 
     * @param logicalName the logical table name
     * @param context the JDBC environment context
     * @return the physical table name in snake_case
     */
    @Override
    public Identifier toPhysicalTableName(Identifier logicalName, JdbcEnvironment context) {
        return convert(logicalName);
    }
    
    /**
     * Converts a logical column name to a physical column name using snake_case.
     * 
     * @param logicalName the logical column name
     * @param context the JDBC environment context
     * @return the physical column name in snake_case
     */
    @Override
    public Identifier toPhysicalColumnName(Identifier logicalName, JdbcEnvironment context) {
        return convert(logicalName);
    }
    
    /**
     * Converts a logical sequence name to a physical sequence name using snake_case.
     * 
     * @param logicalName the logical sequence name
     * @param context the JDBC environment context
     * @return the physical sequence name in snake_case
     */
    @Override
    public Identifier toPhysicalSequenceName(Identifier logicalName, JdbcEnvironment context) {
        return convert(logicalName);
    }
    
    /**
     * Converts an identifier from camelCase to snake_case.
     * 
     * @param logicalName the logical identifier
     * @return the converted identifier in snake_case
     */
    private Identifier convert(Identifier logicalName) {
        String converted = CAMEL_CASE.matcher(logicalName.getText())
            .replaceAll("$1_$2")
            .replace('-', '_')
            .toLowerCase(Locale.ROOT);
        return Identifier.toIdentifier(converted, logicalName.isQuoted());
    }
}

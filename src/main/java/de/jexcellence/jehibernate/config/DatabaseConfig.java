package de.jexcellence.jehibernate.config;

/**
 * Immutable configuration record for database connection settings.
 * <p>
 * This Java 24 record provides a type-safe, immutable way to configure database
 * connections with automatic validation and a fluent builder API.
 * <p>
 * <b>Features:</b>
 * <ul>
 *   <li>Immutable by design (thread-safe)</li>
 *   <li>Built-in validation</li>
 *   <li>Fluent builder pattern</li>
 *   <li>Automatic driver and dialect selection</li>
 * </ul>
 * <p>
 * <b>Example Usage:</b>
 * <pre>{@code
 * var config = DatabaseConfig.builder()
 *     .type(DatabaseType.MYSQL)
 *     .url("jdbc:mysql://localhost:3306/mydb")
 *     .username("root")
 *     .password("secret")
 *     .build();
 * }</pre>
 * <p>
 * <b>Validation:</b> The compact constructor validates that:
 * <ul>
 *   <li>type is not null</li>
 *   <li>url is not null or blank</li>
 * </ul>
 *
 * @param type the database type (must not be null)
 * @param url the JDBC connection URL (must not be null or blank)
 * @param username the database username (may be null)
 * @param password the database password (may be null)
 * @param driver the JDBC driver class name
 * @param dialect the Hibernate dialect class name
 * @author JEHibernate
 * @version 2.0
 * @since 1.0
 * @see DatabaseType
 * @see ConfigurationBuilder
 */
public record DatabaseConfig(
    DatabaseType type,
    String url,
    String username,
    String password,
    String driver,
    String dialect
) {
    
    public DatabaseConfig {
        if (type == null) {
            throw new IllegalArgumentException("Database type cannot be null");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Database URL cannot be null or blank");
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private DatabaseType type;
        private String url;
        private String username;
        private String password;
        private String driver;
        private String dialect;
        
        public Builder type(DatabaseType type) {
            this.type = type;
            return this;
        }
        
        public Builder url(String url) {
            this.url = url;
            return this;
        }
        
        public Builder username(String username) {
            this.username = username;
            return this;
        }
        
        public Builder password(String password) {
            this.password = password;
            return this;
        }
        
        public Builder driver(String driver) {
            this.driver = driver;
            return this;
        }
        
        public Builder dialect(String dialect) {
            this.dialect = dialect;
            return this;
        }
        
        public DatabaseConfig build() {
            String finalDriver = driver != null ? driver : type.getDriverClass();
            String finalDialect = dialect != null ? dialect : type.getDialectClass();
            return new DatabaseConfig(type, url, username, password, finalDriver, finalDialect);
        }
    }
}

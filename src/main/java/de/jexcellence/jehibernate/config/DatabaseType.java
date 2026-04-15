package de.jexcellence.jehibernate.config;

/**
 * Enumeration of supported database types with their JDBC drivers and Hibernate dialects.
 * <p>
 * Each database type includes the fully qualified class names for:
 * <ul>
 *   <li>JDBC Driver - for database connectivity</li>
 *   <li>Hibernate Dialect - for SQL generation</li>
 * </ul>
 * <p>
 * <b>Supported Databases:</b>
 * <ul>
 *   <li>{@link #H2} - In-memory and file-based database</li>
 *   <li>{@link #MYSQL} - MySQL 8.0+</li>
 *   <li>{@link #MARIADB} - MariaDB 10.3+</li>
 *   <li>{@link #POSTGRESQL} - PostgreSQL 12+</li>
 *   <li>{@link #ORACLE} - Oracle 12c+</li>
 *   <li>{@link #MSSQL_SERVER} - Microsoft SQL Server 2017+</li>
 *   <li>{@link #SQLITE} - SQLite 3.x</li>
 *   <li>{@link #HSQLDB} - HyperSQL Database</li>
 * </ul>
 * <p>
 * <b>Example Usage:</b>
 * <pre>{@code
 * var jeHibernate = JEHibernate.builder()
 *     .configuration(config -> config
 *         .database(DatabaseType.POSTGRESQL)
 *         .url("jdbc:postgresql://localhost:5432/mydb")
 *         .credentials("user", "pass"))
 *     .build();
 * }</pre>
 *
 * @author JEHibernate
 * @version 2.0
 * @since 1.0
 * @see DatabaseConfig
 * @see ConfigurationBuilder
 */
public enum DatabaseType {
    H2("h2", "org.h2.Driver", "org.hibernate.dialect.H2Dialect"),
    MYSQL("mysql", "com.mysql.cj.jdbc.Driver", "org.hibernate.dialect.MySQLDialect"),
    MARIADB("mariadb", "org.mariadb.jdbc.Driver", "org.hibernate.dialect.MariaDBDialect"),
    POSTGRESQL("postgresql", "org.postgresql.Driver", "org.hibernate.dialect.PostgreSQLDialect"),
    ORACLE("oracle", "oracle.jdbc.OracleDriver", "org.hibernate.dialect.OracleDialect"),
    MSSQL_SERVER("mssql", "com.microsoft.sqlserver.jdbc.SQLServerDriver", "org.hibernate.dialect.SQLServerDialect"),
    SQLITE("sqlite", "org.sqlite.JDBC", "org.hibernate.community.dialect.SQLiteDialect"),
    HSQLDB("hsqldb", "org.hsqldb.jdbc.JDBCDriver", "org.hibernate.dialect.HSQLDialect");

    private final String prefix;
    private final String driverClass;
    private final String dialectClass;

    DatabaseType(String prefix, String driverClass, String dialectClass) {
        this.prefix = prefix;
        this.driverClass = driverClass;
        this.dialectClass = dialectClass;
    }

    public String getDriverClass() {
        return driverClass;
    }

    public String getDialectClass() {
        return dialectClass;
    }

    /**
     * Returns the property prefix for this database type (e.g., "h2", "mysql", "mssql").
     * Used to look up properties like {@code mysql.url}, {@code mysql.username}, etc.
     *
     * @return the property prefix
     */
    public String getPrefix() {
        return prefix;
    }
}

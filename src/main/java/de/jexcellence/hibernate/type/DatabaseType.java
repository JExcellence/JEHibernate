package de.jexcellence.hibernate.type;

/**
 * DatabaseType represents the supported database management systems for the Hibernate-based application.
 *
 * <p>This enumeration defines all database types that the application can connect to and work with.
 * Each constant represents a specific database vendor and is used throughout the application for:</p>
 * <ul>
 *   <li>Database connection configuration and management</li>
 *   <li>Dialect selection for Hibernate ORM mapping</li>
 *   <li>Database-specific query optimization and feature detection</li>
 *   <li>Migration script selection and execution</li>
 *   <li>Database vendor-specific behavior customization</li>
 * </ul>
 *
 * <p>The enum supports both enterprise-grade databases (Oracle, SQL Server, PostgreSQL)
 * and lightweight databases (SQLite, H2, HSQLDB) suitable for development, testing,
 * and embedded applications.</p>
 *
 * <p>Usage example:</p>
 * <pre>
 * DatabaseType dbType = DatabaseType.POSTGRES_SQL;
 * String dialect = getHibernateDialect(dbType);
 * </pre>
 *
 * @author JExcellence
 * @version 1.0
 * @since 1.0
 * @see org.hibernate.dialect.Dialect
 */
public enum DatabaseType {
    
    /**
     * Oracle Database - Enterprise-grade relational database management system.
     *
     * <p>Oracle Database is a multi-model database management system produced and marketed
     * by Oracle Corporation. It is one of the most widely used enterprise database systems,
     * known for its robustness, scalability, and comprehensive feature set.</p>
     *
     * <p>Key characteristics:</p>
     * <ul>
     *   <li>Enterprise-grade performance and reliability</li>
     *   <li>Advanced security features and compliance support</li>
     *   <li>Comprehensive SQL and PL/SQL support</li>
     *   <li>Excellent scalability for large datasets</li>
     *   <li>Extensive tooling and ecosystem support</li>
     * </ul>
     */
    ORACLE,
    
    /**
     * Microsoft SQL Server - Enterprise relational database management system.
     *
     * <p>Microsoft SQL Server is a relational database management system developed by Microsoft.
     * It is widely used in enterprise environments, particularly in Windows-based infrastructures,
     * and offers strong integration with Microsoft technologies.</p>
     *
     * <p>Key characteristics:</p>
     * <ul>
     *   <li>Seamless integration with Microsoft ecosystem</li>
     *   <li>Advanced analytics and business intelligence features</li>
     *   <li>Strong security and compliance capabilities</li>
     *   <li>Excellent performance for Windows-based applications</li>
     *   <li>Comprehensive development and management tools</li>
     * </ul>
     */
    MSSQL_SERVER,
    
    /**
     * PostgreSQL - Advanced open-source relational database system.
     *
     * <p>PostgreSQL is a powerful, open-source object-relational database system with a strong
     * reputation for reliability, feature robustness, and performance. It is known for its
     * standards compliance and extensibility.</p>
     *
     * <p>Key characteristics:</p>
     * <ul>
     *   <li>Open-source with no licensing costs</li>
     *   <li>ACID compliance and strong data integrity</li>
     *   <li>Advanced features like JSON support, full-text search</li>
     *   <li>Excellent standards compliance (SQL:2016)</li>
     *   <li>Highly extensible with custom functions and data types</li>
     * </ul>
     */
    POSTGRESQL,
    
    /**
     * MySQL - Popular open-source relational database management system.
     *
     * <p>MySQL is one of the world's most popular open-source relational database management
     * systems. It is widely used for web applications and is a central component of the
     * LAMP (Linux, Apache, MySQL, PHP/Python/Perl) open-source web application software stack.</p>
     *
     * <p>Key characteristics:</p>
     * <ul>
     *   <li>High performance and reliability</li>
     *   <li>Easy to use and deploy</li>
     *   <li>Strong community support and ecosystem</li>
     *   <li>Excellent for web applications and content management</li>
     *   <li>Multiple storage engines for different use cases</li>
     * </ul>
     */
    MYSQL,
    
    /**
     * MariaDB - Community-developed fork of MySQL with enhanced features.
     *
     * <p>MariaDB is a community-developed, commercially supported fork of the MySQL relational
     * database management system. It is intended to remain free and open-source software under
     * the GNU General Public License.</p>
     *
     * <p>Key characteristics:</p>
     * <ul>
     *   <li>Drop-in replacement for MySQL</li>
     *   <li>Enhanced performance and optimization</li>
     *   <li>Additional storage engines (Aria, ColumnStore)</li>
     *   <li>Active community development</li>
     *   <li>Better open-source licensing guarantees</li>
     * </ul>
     */
    MARIADB,
    
    /**
     * SQLite - Lightweight, serverless, self-contained SQL database engine.
     *
     * <p>SQLite is a C-language library that implements a small, fast, self-contained,
     * high-reliability, full-featured, SQL database engine. It is the most used database
     * engine in the world and is ideal for embedded applications and development.</p>
     *
     * <p>Key characteristics:</p>
     * <ul>
     *   <li>Zero-configuration and serverless</li>
     *   <li>Self-contained single file database</li>
     *   <li>Cross-platform compatibility</li>
     *   <li>Perfect for development, testing, and embedded applications</li>
     *   <li>ACID-compliant with excellent reliability</li>
     * </ul>
     */
    SQLITE,
    
    /**
     * HSQLDB (HyperSQL Database) - Lightweight, pure Java relational database engine.
     *
     * <p>HSQLDB is a relational database engine written in Java, with a JDBC driver,
     * conforming to a large subset of the SQL-92, SQL:1999, SQL:2003 and SQL:2008 standards.
     * It offers a small, fast multithreaded, and transactional database engine.</p>
     *
     * <p>Key characteristics:</p>
     * <ul>
     *   <li>Pure Java implementation</li>
     *   <li>In-memory and file-based storage options</li>
     *   <li>Excellent for testing and development</li>
     *   <li>Small footprint and fast startup</li>
     *   <li>Good SQL standards compliance</li>
     * </ul>
     */
    HSQLDB,
    
    /**
     * H2 Database - Fast, lightweight, Java-based relational database engine.
     *
     * <p>H2 is a relational database management system written in Java. It can be embedded
     * in Java applications or run in client-server mode. It is particularly popular for
     * development, testing, and as an embedded database for applications.</p>
     *
     * <p>Key characteristics:</p>
     * <ul>
     *   <li>Very fast performance and small footprint</li>
     *   <li>Multiple connection modes (embedded, server, in-memory)</li>
     *   <li>Browser-based console for database management</li>
     *   <li>Excellent compatibility with other databases</li>
     *   <li>Perfect for unit testing and development</li>
     * </ul>
     */
    H2
}
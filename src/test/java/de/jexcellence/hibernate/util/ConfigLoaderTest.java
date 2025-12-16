package de.jexcellence.hibernate.util;

import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ConfigLoader")
class ConfigLoaderTest {

    private ConfigLoader configLoader;

    @BeforeEach
    void setUp() {
        configLoader = new ConfigLoader();
    }

    @Nested
    @DisplayName("loadAndValidateProperties")
    class LoadAndValidateProperties {

        @Test
        @DisplayName("should load H2 configuration with defaults")
        void shouldLoadH2ConfigurationWithDefaults(@TempDir Path tempDir) throws IOException {
            var configFile = tempDir.resolve("hibernate.properties");
            Files.writeString(configFile, """
                database.type=H2
                h2.url=jdbc:h2:mem:testdb
                """);

            var properties = configLoader.loadAndValidateProperties(configFile.toString());

            assertThat(properties.getProperty(AvailableSettings.JAKARTA_JDBC_URL))
                .isEqualTo("jdbc:h2:mem:testdb");
            assertThat(properties.getProperty(AvailableSettings.JAKARTA_JDBC_DRIVER))
                .isEqualTo("org.h2.Driver");
            assertThat(properties.getProperty(AvailableSettings.JAKARTA_JDBC_USER))
                .isEqualTo("sa");
            assertThat(properties.getProperty(AvailableSettings.DIALECT))
                .isEqualTo("org.hibernate.dialect.H2Dialect");
        }

        @Test
        @DisplayName("should load MySQL configuration")
        void shouldLoadMySqlConfiguration(@TempDir Path tempDir) throws IOException {
            var configFile = tempDir.resolve("hibernate.properties");
            Files.writeString(configFile, """
                database.type=MYSQL
                mysql.url=jdbc:mysql://localhost:3306/testdb
                mysql.username=root
                mysql.password=secret
                """);

            var properties = configLoader.loadAndValidateProperties(configFile.toString());

            assertThat(properties.getProperty(AvailableSettings.JAKARTA_JDBC_URL))
                .isEqualTo("jdbc:mysql://localhost:3306/testdb");
            assertThat(properties.getProperty(AvailableSettings.JAKARTA_JDBC_DRIVER))
                .isEqualTo("com.mysql.cj.jdbc.Driver");
            assertThat(properties.getProperty(AvailableSettings.JAKARTA_JDBC_USER))
                .isEqualTo("root");
            assertThat(properties.getProperty(AvailableSettings.JAKARTA_JDBC_PASSWORD))
                .isEqualTo("secret");
        }

        @Test
        @DisplayName("should load MariaDB configuration")
        void shouldLoadMariaDbConfiguration(@TempDir Path tempDir) throws IOException {
            var configFile = tempDir.resolve("hibernate.properties");
            Files.writeString(configFile, """
                database.type=MARIADB
                mariadb.url=jdbc:mariadb://localhost:3306/testdb
                mariadb.username=admin
                mariadb.password=pass123
                """);

            var properties = configLoader.loadAndValidateProperties(configFile.toString());

            assertThat(properties.getProperty(AvailableSettings.JAKARTA_JDBC_DRIVER))
                .isEqualTo("org.mariadb.jdbc.Driver");
            assertThat(properties.getProperty(AvailableSettings.DIALECT))
                .isEqualTo("org.hibernate.dialect.MariaDBDialect");
        }

        @Test
        @DisplayName("should load PostgreSQL configuration")
        void shouldLoadPostgreSqlConfiguration(@TempDir Path tempDir) throws IOException {
            var configFile = tempDir.resolve("hibernate.properties");
            Files.writeString(configFile, """
                database.type=POSTGRESQL
                postgresql.url=jdbc:postgresql://localhost:5432/testdb
                postgresql.username=postgres
                postgresql.password=secret
                """);

            var properties = configLoader.loadAndValidateProperties(configFile.toString());

            assertThat(properties.getProperty(AvailableSettings.JAKARTA_JDBC_DRIVER))
                .isEqualTo("org.postgresql.Driver");
            assertThat(properties.getProperty(AvailableSettings.DIALECT))
                .isEqualTo("org.hibernate.dialect.PostgreSQLDialect");
        }

        @Test
        @DisplayName("should apply Hibernate settings")
        void shouldApplyHibernateSettings(@TempDir Path tempDir) throws IOException {
            var configFile = tempDir.resolve("hibernate.properties");
            Files.writeString(configFile, """
                database.type=H2
                hibernate.hbm2ddl.auto=update
                hibernate.show_sql=true
                hibernate.format_sql=true
                hibernate.jdbc.batch_size=50
                """);

            var properties = configLoader.loadAndValidateProperties(configFile.toString());

            assertThat(properties.getProperty(AvailableSettings.HBM2DDL_AUTO))
                .isEqualTo("update");
            assertThat(properties.getProperty(AvailableSettings.SHOW_SQL))
                .isEqualTo("true");
            assertThat(properties.getProperty(AvailableSettings.FORMAT_SQL))
                .isEqualTo("true");
            assertThat(properties.getProperty(AvailableSettings.STATEMENT_BATCH_SIZE))
                .isEqualTo("50");
        }

        @Test
        @DisplayName("should default to H2 when database.type is not specified")
        void shouldDefaultToH2WhenTypeNotSpecified(@TempDir Path tempDir) throws IOException {
            var configFile = tempDir.resolve("hibernate.properties");
            Files.writeString(configFile, "");

            var properties = configLoader.loadAndValidateProperties(configFile.toString());

            assertThat(properties.getProperty(AvailableSettings.JAKARTA_JDBC_DRIVER))
                .isEqualTo("org.h2.Driver");
        }

        @Test
        @DisplayName("should throw exception for unsupported database type")
        void shouldThrowExceptionForUnsupportedDatabaseType(@TempDir Path tempDir) throws IOException {
            var configFile = tempDir.resolve("hibernate.properties");
            Files.writeString(configFile, "database.type=UNKNOWN_DB");

            assertThatThrownBy(() -> configLoader.loadAndValidateProperties(configFile.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported database type");
        }

        @Test
        @DisplayName("should throw exception when non-H2 database missing credentials")
        void shouldThrowExceptionWhenMissingCredentials(@TempDir Path tempDir) throws IOException {
            var configFile = tempDir.resolve("hibernate.properties");
            Files.writeString(configFile, """
                database.type=MYSQL
                mysql.url=jdbc:mysql://localhost:3306/testdb
                """);

            assertThatThrownBy(() -> configLoader.loadAndValidateProperties(configFile.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username is required");
        }

        @Test
        @DisplayName("should fall back to bundled properties when file not found")
        void shouldFallBackToBundledProperties() throws IOException {
            var properties = configLoader.loadAndValidateProperties("nonexistent/path/hibernate.properties");

            assertThat(properties).isNotEmpty();
            assertThat(properties.getProperty(AvailableSettings.JAKARTA_JDBC_DRIVER)).isNotNull();
        }
    }
}

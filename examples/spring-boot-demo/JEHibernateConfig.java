// examples/spring-boot-demo — Spring Boot bootstrap in < 50 lines.
// Not part of the Gradle build; copy into your application.
package com.example.demo;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
class JEHibernateConfig {

    /**
     * Reuses Spring Boot's auto-configured DataSource (HikariCP). JEHibernate does not create or
     * close it. Flyway migrations under classpath:db/migration run before the SessionFactory builds;
     * ddl-auto defaults to validate.
     */
    @Bean(destroyMethod = "close")
    JEHibernate jeHibernate(DataSource dataSource) {
        return JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.POSTGRESQL)
                .url("jdbc:postgresql://localhost:5432/app")  // used only for dialect/driver metadata
                .dataSource(dataSource)
                .ddlAuto("validate"))
            .scanPackages("com.example.demo")
            .build();
    }

    @Bean
    UserRepository userRepository(JEHibernate jeHibernate) {
        return jeHibernate.repositories().get(UserRepository.class);
    }
}

// Your repository — the same abstraction as in the plugin world:
// class UserRepository extends AbstractCrudRepository<User, Long> { ... }

plugins {
    `java-library`
}

description = "JEHibernate Spring Boot starter — auto-configuration that reuses the application DataSource"

dependencies {
    // ── API (exposed to consumers) ──
    api(project(":jehibernate-core"))

    // ── Compile-only: Spring Boot (the host application provides these) ──
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.slf4j.api)
    annotationProcessor(libs.spring.boot.configuration.processor)

    // ── Compile-only: test-slice infrastructure for @JEHibernateRepositoryTest.
    // Consumers get these transitively from spring-boot-starter-test at test scope.
    compileOnly(libs.spring.boot.test)
    compileOnly(libs.spring.boot.test.autoconfigure)
    compileOnly(libs.spring.test)
    compileOnly(libs.junit.jupiter)

    // ── Test ──
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.jdbc)
    testImplementation(libs.spring.boot.autoconfigure)
    testImplementation(libs.h2)
}

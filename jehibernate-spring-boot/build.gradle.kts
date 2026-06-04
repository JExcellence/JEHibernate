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

    // ── Test ──
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.jdbc)
    testImplementation(libs.spring.boot.autoconfigure)
    testImplementation(libs.h2)
}

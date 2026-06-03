plugins {
    `java-library`
}

description = "JEHibernate Spring Boot starter — auto-configuration that reuses the application DataSource"

dependencies {
    // ── Implementation ──
    api(project(":jehibernate-core"))

    // ── Compile-only: Spring Boot (host application provides these) ──
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.spring.orm)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.jakarta.persistence)
    annotationProcessor(libs.spring.boot.configuration.processor)

    // ── Test ──
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.h2)
    testRuntimeOnly(libs.slf4j.simple)
}

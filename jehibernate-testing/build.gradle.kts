plugins {
    `java-library`
}

description = "JEHibernate testing — Testcontainers integration and repository test fixtures (testCompile only)"

dependencies {
    // ── Implementation ──
    api(project(":jehibernate-core"))
    api(libs.junit.jupiter)
    api(libs.testcontainers.junit)
    api(libs.flyway.core)
    // Container classes are referenced directly by JEHibernateExtension's strategy switch, so the
    // bytecode verifier needs them at load time. A test-fixtures module legitimately depends on all
    // supported Testcontainers DB modules; the matching JDBC driver stays the consumer's choice.
    api(libs.testcontainers.postgresql)
    api(libs.testcontainers.mysql)
    api(libs.testcontainers.mariadb)
    api(libs.testcontainers.mssql)

    compileOnly(libs.slf4j.api)

    // ── Test ──
    testImplementation(libs.assertj.core)
    testImplementation(libs.h2)
    // Driver for the Postgres example test (the container module comes via api).
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.slf4j.simple)
}

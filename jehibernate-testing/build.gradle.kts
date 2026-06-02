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

    // ── Compile-only: drivers/containers chosen by the consuming test module ──
    compileOnly(libs.testcontainers.postgresql)
    compileOnly(libs.testcontainers.mysql)
    compileOnly(libs.testcontainers.mariadb)
    compileOnly(libs.testcontainers.mssql)
    compileOnly(libs.slf4j.api)

    // ── Test ──
    testImplementation(libs.assertj.core)
    testImplementation(libs.h2)
    testRuntimeOnly(libs.slf4j.simple)
}

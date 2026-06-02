plugins {
    `java-library`
}

description = "JEHibernate plugin helpers — Spigot/Paper convenience (PropertyLoader from plugin data folder)"

dependencies {
    // ── Implementation ──
    api(project(":jehibernate-core"))

    // ── Compile-only: Paper API (the server runtime provides it + SLF4J) ──
    compileOnly(libs.paper.api)
    compileOnly(libs.slf4j.api)

    // ── Test ──
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.h2)
    testRuntimeOnly(libs.slf4j.simple)
}

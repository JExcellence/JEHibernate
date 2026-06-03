plugins {
    `java-library`
}

description = "JEHibernate plugin helpers — Spigot/Paper convenience (PropertyLoader from plugin data folder)"

// Paper 1.21 targets Java 21; this module follows the Paper runtime (core stays at release 17).
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

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

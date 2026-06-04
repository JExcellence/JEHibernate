import org.gradle.api.publish.maven.MavenPublication

plugins {
    `java-library`
    signing
    id("com.vanniktech.maven.publish")
}

description = "JEHibernate core — plugin-agnostic, Spring-agnostic Hibernate/JPA utility library"

dependencies {
    // ── API (exposed to consumers) ──
    api(platform(libs.hibernate.platform))
    api(libs.hibernate.core)
    api(libs.jakarta.persistence)
    api(libs.jakarta.transaction)

    // ── Implementation ──
    implementation(libs.reflections)
    implementation(libs.caffeine)
    // HikariCP is the default connection pool: JEHibernate owns the DataSource and hands it to
    // Hibernate via DatasourceConnectionProviderImpl (shipped in hibernate-core), so
    // hibernate-hikaricp is intentionally not needed.
    implementation(libs.hikari)
    // SLF4J API: bundled transitively so standalone/Spring consumers get the logging facade
    // without manual setup. Plugins already have it from Paper (benign duplicate); consumers
    // still choose their own binding.
    implementation(libs.slf4j.api)

    // ── Compile-only: optional integrations detected at runtime ──
    // Migration: present → auto-run; absent → silent no-op (see MigrationSupport).
    compileOnly(libs.flyway.core)
    compileOnly(libs.liquibase.core)
    compileOnly(libs.hibernate.jcache)
    // Envers audit: present → @Audited entities are versioned; absent → audit classes unused.
    compileOnly(libs.hibernate.envers)
    // API contracts / JDBC drivers provided by the host.
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.postgresql)
    compileOnly(libs.mysql)
    compileOnly(libs.h2)
    compileOnly(libs.jackson.databind)
    compileOnly(libs.jackson.jsr310)

    // ── Test ──
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.h2)
    testImplementation(libs.flyway.core)
    testImplementation(libs.hibernate.envers)
    testRuntimeOnly(libs.slf4j.simple)
}

mavenPublishing {
    coordinates(group.toString(), "jehibernate-core", version.toString())

    pom {
        name.set("JEHibernate Core")
        description.set(project.description)
        url.set("https://github.com/jexcellence/JEHibernate")
        inceptionYear.set("2024")

        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("13140db9-1cc4-41fc-9c83-cffcce069bfa")
                name.set("Justin Eiletz")
                email.set("justin.eiletz@jexcellence.de")
                organization.set("JExcellence")
                organizationUrl.set("https://jexcellence.de")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/jexcellence/JEHibernate.git")
            developerConnection.set("scm:git:ssh://github.com:jexcellence/JEHibernate.git")
            url.set("https://github.com/jexcellence/JEHibernate")
        }
        issueManagement {
            system.set("GitHub Issues")
            url.set("https://github.com/jexcellence/JEHibernate/issues")
        }
        ciManagement {
            system.set("GitHub Actions")
            url.set("https://github.com/jexcellence/JEHibernate/actions")
        }
    }
}

val signingKeyProp = (project.findProperty("signingInMemoryKey") ?: project.findProperty("signingKey")) as String?
val signingPasswordProp = (project.findProperty("signingInMemoryKeyPassword") ?: project.findProperty("signing.password")) as String?

signing {
    isRequired = !signingKeyProp.isNullOrBlank() && !signingPasswordProp.isNullOrBlank()
    if (!signingKeyProp.isNullOrBlank() && !signingPasswordProp.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKeyProp, signingPasswordProp)
    }
}

afterEvaluate {
    if (!signingKeyProp.isNullOrBlank() && !signingPasswordProp.isNullOrBlank()) {
        publishing {
            publications.withType<MavenPublication> {
                signing.sign(this)
            }
        }
    }
}

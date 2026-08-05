import org.gradle.api.publish.maven.MavenPublication

plugins {
    `java-library`
    signing
    id("com.vanniktech.maven.publish")
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

mavenPublishing {
    coordinates(group.toString(), project.name, version.toString())
    pom {
        name.set(project.name)
        description.set(project.description ?: "JEHibernate module")
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

val signingKeyProp = (findProperty("signingInMemoryKey") ?: findProperty("signingKey")) as String?
val signingPasswordProp = (findProperty("signingInMemoryKeyPassword") ?: findProperty("signing.password")) as String?

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

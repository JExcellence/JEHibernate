import org.gradle.api.publish.maven.MavenPublication

plugins {
    `java-library`
    signing
    id("com.vanniktech.maven.publish")
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

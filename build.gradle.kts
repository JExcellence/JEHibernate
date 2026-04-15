import org.gradle.api.publish.maven.MavenPublication
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    `java-library`
    signing
    id("com.vanniktech.maven.publish") version "0.34.0"
}

group = "de.jexcellence.hibernate"
version = "3.0.1"
description = "Modern Hibernate/JPA utility library for Java 17+"

val isSnapshot = version.toString().endsWith("SNAPSHOT")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
    withSourcesJar()
}

dependencies {
    implementation(platform("org.hibernate.orm:hibernate-platform:7.1.4.Final"))
    implementation("org.hibernate.orm:hibernate-core")
    implementation("jakarta.transaction:jakarta.transaction-api")
    implementation("org.reflections:reflections:0.10.2")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")
    implementation("org.slf4j:slf4j-api:2.0.16")

    compileOnly("org.jetbrains:annotations:26.0.1")
    compileOnly("org.postgresql:postgresql:42.7.7")
    compileOnly("com.mysql:mysql-connector-j:9.3.0")
    compileOnly("com.h2database:h2:2.4.240")
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    compileOnly("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    compileOnly("org.hibernate.orm:hibernate-agroal")
    compileOnly("io.agroal:agroal-pool:2.5")
    compileOnly("org.hibernate.orm:hibernate-jcache")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testImplementation("com.h2database:h2:2.4.240")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.named("build") {
    dependsOn(tasks.test)
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    options.encoding = "UTF-8"
}

mavenPublishing {
    coordinates(group.toString(), "JEHibernate", version.toString())

    pom {
        name.set("JEHibernate")
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

// Configure signing - read from gradle.properties
// Supports both naming conventions: signingKey/signing.password and signingInMemoryKey/signingInMemoryKeyPassword
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

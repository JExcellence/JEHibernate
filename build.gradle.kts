import org.gradle.api.publish.maven.MavenPublication
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

plugins {
    `java-library`
    signing
    id("com.vanniktech.maven.publish") version "0.34.0"
}

group = "de.jexcellence.hibernate"
version = "1.0.2"
description = "Hibernate integration utilities by JExcellence"

val isSnapshot = version.toString().endsWith("SNAPSHOT")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

dependencies {
    implementation(platform("org.hibernate.orm:hibernate-platform:7.1.4.Final"))
    implementation("org.hibernate.orm:hibernate-core")
    implementation("jakarta.transaction:jakarta.transaction-api")
    implementation("org.reflections:reflections:0.10.2")
    implementation("com.google.guava:guava:33.4.0-jre")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")

    compileOnly("org.jetbrains:annotations:24.1.0")
    testCompileOnly("org.jetbrains:annotations:24.1.0")

    implementation("org.postgresql:postgresql:42.7.3")
    implementation("com.mysql:mysql-connector-j:9.2.0")
    implementation("com.h2database:h2:2.3.232")
    implementation("org.slf4j:slf4j-api:2.0.13")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("com.h2database:h2:2.3.232")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
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
val signingKeyProp = project.findProperty("signingInMemoryKey") as String?
val signingPasswordProp = project.findProperty("signingInMemoryKeyPassword") as String?

signing {
    isRequired = signingKeyProp != null && signingPasswordProp != null
    
    if (signingKeyProp != null && signingPasswordProp != null) {
        useInMemoryPgpKeys(signingKeyProp, signingPasswordProp)
    }
}

afterEvaluate {
    if (signingKeyProp != null && signingPasswordProp != null) {
        publishing {
            publications.withType<MavenPublication> {
                signing.sign(this)
            }
        }
    }
}

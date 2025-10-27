import org.gradle.api.publish.maven.MavenPublication
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

plugins {
    `java-library`
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

group = "de.jexcellence.hibernate"
version = "1.0.1"
description = "Hibernate integration utilities by JExcellence"

val isSnapshot = version.toString().endsWith("SNAPSHOT")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
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

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.jar {
    from("LICENSE") {
        into("META-INF")
    }
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Built-By" to System.getProperty("user.name"),
            "Built-Date" to DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now())
        )
    }
}

val portalUsername = (project.findProperty("centralPortalUsername") as String?)
    ?: (project.findProperty("centralUsername") as String?)
    ?: System.getenv("CENTRAL_PORTAL_USERNAME")
    ?: System.getenv("CENTRAL_USERNAME")
val portalPassword = (project.findProperty("centralPortalPassword") as String?)
    ?: (project.findProperty("centralToken") as String?)
    ?: System.getenv("CENTRAL_PORTAL_PASSWORD")
    ?: System.getenv("CENTRAL_TOKEN")

val sonatypeUsername = (project.findProperty("sonatypeUsername") as String?) ?: portalUsername
val sonatypePassword = (project.findProperty("sonatypePassword") as String?) ?: portalPassword
val sonatypeNexusUrl = (project.findProperty("sonatypeNexusUrl") as String?)
    ?: "https://ossrh-staging-api.central.sonatype.com/service/local/"
val sonatypeSnapshotRepositoryUrl = (project.findProperty("sonatypeSnapshotRepositoryUrl") as String?)
    ?: "https://central.sonatype.com/repository/maven-snapshots/"

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "JEHibernate"

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
    }

    repositories {
        if (isSnapshot && !sonatypeUsername.isNullOrBlank() && !sonatypePassword.isNullOrBlank()) {
            maven {
                name = "CentralSnapshots"
                url = uri(sonatypeSnapshotRepositoryUrl)
                credentials {
                    username = sonatypeUsername
                    password = sonatypePassword
                }
            }
        }

        mavenLocal()
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri(sonatypeNexusUrl))
            snapshotRepositoryUrl.set(uri(sonatypeSnapshotRepositoryUrl))

            if (!sonatypeUsername.isNullOrBlank()) {
                username.set(sonatypeUsername)
            }

            if (!sonatypePassword.isNullOrBlank()) {
                password.set(sonatypePassword)
            }
        }
    }
}

signing {
    val signingKey = (project.findProperty("signingKey") as String?) ?: System.getenv("SIGNING_KEY")
    val signingPassword = (project.findProperty("signingPassword") as String?) ?: System.getenv("SIGNING_PASSWORD")

    val sanitizedSigningKey = signingKey?.replace("\\n", "\n")

    if (!sanitizedSigningKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        useInMemoryPgpKeys(sanitizedSigningKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    }
}

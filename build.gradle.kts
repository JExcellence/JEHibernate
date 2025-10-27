import org.gradle.api.publish.maven.MavenPublication
import org.gradle.authentication.http.BasicAuthentication
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

plugins {
    `java-library`
    `maven-publish`
    signing
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

val centralPortalUsername = (project.findProperty("centralPortalUsername") as String?)
    ?: System.getenv("CENTRAL_PORTAL_USERNAME")
val centralPortalPassword = (project.findProperty("centralPortalPassword") as String?)
    ?: System.getenv("CENTRAL_PORTAL_PASSWORD")
val centralPortalUrl = (project.findProperty("centralPortalUrl") as String?)
val ossrhUsername = (project.findProperty("ossrhUsername") as String?)
    ?: System.getenv("OSSRH_USERNAME")
val ossrhPassword = (project.findProperty("ossrhPassword") as String?)
    ?: System.getenv("OSSRH_PASSWORD")

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
        if (isSnapshot) {
            maven {
                name = "SonatypeSnapshots"
                url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                if (!ossrhUsername.isNullOrBlank() && !ossrhPassword.isNullOrBlank()) {
                    credentials {
                        username = ossrhUsername
                        password = ossrhPassword
                    }
                }
            }
        } else {
            maven {
                name = "CentralPortal"
                url = uri(centralPortalUrl ?: "https://central.sonatype.com/api/v1/publish")
                if (!centralPortalUsername.isNullOrBlank() && !centralPortalPassword.isNullOrBlank()) {
                    credentials {
                        username = centralPortalUsername
                        password = centralPortalPassword
                    }
                }
            }
        }

        mavenLocal()
    }
}

signing {
    val signingKey = (project.findProperty("signingKey") as String?) ?: System.getenv("SIGNING_KEY")
    val signingPassword = (project.findProperty("signingPassword") as String?) ?: System.getenv("SIGNING_PASSWORD")

    if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    }
}

import org.gradle.api.publish.maven.MavenPublication
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    `java-library`
    signing
    id("com.vanniktech.maven.publish") version "0.34.0"
}

group = "de.jexcellence.hibernate"
version = "3.0.3"
description = "Modern Hibernate/JPA utility library for Java 17+"

val isSnapshot = version.toString().endsWith("SNAPSHOT")

// ── Dependency versions ────────────────────────────────────────────────────
val hibernatePlatformVersion  = "7.1.4.Final"
val reflectionsVersion        = "0.10.2"
val caffeineVersion           = "3.2.0"
val slf4jVersion              = "2.0.16"
val jetbrainsAnnotationsVersion = "26.0.1"
val postgresqlVersion         = "42.7.7"
val mysqlVersion              = "9.3.0"
val h2Version                 = "2.4.240"
val jacksonVersion            = "2.18.2"
val junitVersion              = "5.11.4"
val assertjVersion            = "3.27.3"
val mockitoVersion            = "5.15.2"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
    withSourcesJar()
}

dependencies {
    implementation(platform("org.hibernate.orm:hibernate-platform:$hibernatePlatformVersion"))
    implementation("org.hibernate.orm:hibernate-core")
    implementation("jakarta.persistence:jakarta.persistence-api")
    implementation("jakarta.transaction:jakarta.transaction-api")
    implementation("org.reflections:reflections:$reflectionsVersion")
    implementation("com.github.ben-manes.caffeine:caffeine:$caffeineVersion")

    // SLF4J API — provided by the runtime environment (Paper/Spigot include it)
    compileOnly("org.slf4j:slf4j-api:$slf4jVersion")

    compileOnly("org.jetbrains:annotations:$jetbrainsAnnotationsVersion")
    compileOnly("org.postgresql:postgresql:$postgresqlVersion")
    compileOnly("com.mysql:mysql-connector-j:$mysqlVersion")
    compileOnly("com.h2database:h2:$h2Version")
    compileOnly("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    compileOnly("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    compileOnly("org.hibernate.orm:hibernate-agroal")
    compileOnly("io.agroal:agroal-pool:2.5")
    compileOnly("org.hibernate.orm:hibernate-jcache")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("com.h2database:h2:$h2Version")
    testRuntimeOnly("org.slf4j:slf4j-simple:$slf4jVersion")
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

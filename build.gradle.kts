plugins {
    id("java")
    id("maven-publish")
    id("signing")
}

group = "de.jexcellence.hibernate"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.hibernate.orm:hibernate-platform:6.6.4.Final"))
    implementation("org.hibernate.orm:hibernate-core")
    implementation("jakarta.transaction:jakarta.transaction-api")
    implementation("org.reflections:reflections:0.10.2")
    implementation("com.google.guava:guava:33.4.0-jre")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "JEHibernate"

            pom {
                name.set("JEHibernate")
                description.set("Hibernate integration by JExcellence")
                url.set("https://github.com/jexcellence/JEHibernate")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("JExcellence")
                        organization.set("JExcellence")
                        name.set("Justin Eiletz")
                        email.set("justin.eiletz@jexcellence.de")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/jexcellence/JEHibernate.git")
                    developerConnection.set("scm:git:ssh://github.com:jexcellence/JEHibernate.git")
                    url.set("https://github.com/jexcellence/JEHibernate")
                }
            }
        }
    }

    repositories {
        maven {
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl

            credentials {
                username = System.getenv("OSSRH_USERNAME")
                password = System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
    useInMemoryPgpKeys(
        System.getenv("SIGNING_KEY_ID"),
        System.getenv("SIGNING_KEY"),
        System.getenv("SIGNING_PASSWORD")
    )
}

tasks.register("cleanMavenDeployLocally") {
    dependsOn("clean", "publishToMavenLocal")
}
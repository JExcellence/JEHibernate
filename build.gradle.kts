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
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("13140db9-1cc4-41fc-9c83-cffcce069bfa")
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
            name = "OSSRH"
            url = uri(
                if (version.toString().endsWith("SNAPSHOT"))
                    "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                else
                    "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            )

            credentials {
                username = project.findProperty("ossrhUsername")?.toString() ?: ""
                password = project.findProperty("ossrhPassword")?.toString() ?: ""
            }
        }
    }
}

signing {
    val signingKey: String? = project.findProperty("signingKey") as String?
    val signingPassword: String? = project.findProperty("signingPassword") as String?

    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    }
}
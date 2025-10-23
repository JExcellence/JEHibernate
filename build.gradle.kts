plugins {
    id("java")
    id("maven-publish")
    id("signing")
}

group = "de.jexcellence.hibernate"
version = "1.0.1"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(platform("org.hibernate.orm:hibernate-platform:7.1.4.Final"))
    implementation("org.hibernate.orm:hibernate-core")
    implementation("jakarta.transaction:jakarta.transaction-api")
    implementation("org.reflections:reflections:0.10.2")
    implementation("com.google.guava:guava:33.4.0-jre")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")

    implementation("org.postgresql:postgresql:42.7.3")
    implementation("com.mysql:mysql-connector-j:9.2.0")
    implementation("com.h2database:h2:2.3.232")

    implementation("org.slf4j:slf4j-api:2.0.13")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("com.h2database:h2:2.3.232")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withJavadocJar()
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
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
        mavenLocal()
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
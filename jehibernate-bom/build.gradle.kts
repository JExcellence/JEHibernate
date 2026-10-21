import com.vanniktech.maven.publish.JavaPlatform
import org.gradle.api.publish.maven.MavenPublication

plugins {
    `java-platform`
    signing
    id("com.vanniktech.maven.publish")
}

description = "JEHibernate BOM — aligns the versions of all JEHibernate modules in one import"

dependencies {
    constraints {
        api(project(":jehibernate-core"))
        api(project(":jehibernate-spring-boot"))
        api(project(":jehibernate-plugin"))
        api(project(":jehibernate-testing"))
    }
}

mavenPublishing {
    configure(JavaPlatform())
    coordinates(group.toString(), project.name, version.toString())
    pom {
        name.set(project.name)
        description.set(project.description ?: "JEHibernate BOM")
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

// Signing: in-memory PGP keys from properties (signingInMemoryKey / …Password), no-op when absent
// (e.g. publishToMavenLocal). Central publishing requires them.
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

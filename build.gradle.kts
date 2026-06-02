plugins {
    `java-library`
    id("com.vanniktech.maven.publish") version "0.34.0" apply false
}

allprojects {
    group = "de.jexcellence.hibernate"
    version = "4.0.0"
}

// ── Shared configuration for every JEHibernate module ───────────────────────
subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenLocal()
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            // Build with a modern JDK …
            languageVersion.set(JavaLanguageVersion.of(24))
        }
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        // … but emit Java 17 bytecode so Spigot/Paper runtimes (Java 21) and
        // generic Java 17+ backends can all consume the library.
        options.release.set(17)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }

    tasks.withType<Javadoc>().configureEach {
        (options as org.gradle.external.javadoc.StandardJavadocDocletOptions)
            .addStringOption("Xdoclint:none", "-quiet")
        options.encoding = "UTF-8"
    }
}

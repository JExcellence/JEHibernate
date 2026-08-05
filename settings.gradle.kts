pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenLocal()
        mavenCentral()
        google()
    }
}

// Lets Gradle auto-provision the Java 24 toolchain (e.g. on CI where only the Gradle-running JDK
// is installed), so the build isn't tied to a locally present JDK 24.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        // Paper API (jehibernate-plugin, compileOnly)
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "JEHibernate"

include("jehibernate-core")
include("jehibernate-spring-boot")
include("jehibernate-plugin")
include("jehibernate-testing")

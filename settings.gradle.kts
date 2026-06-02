pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenLocal()
        mavenCentral()
        google()
    }
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

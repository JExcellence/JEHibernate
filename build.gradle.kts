plugins {
    id("java")
    id("maven-publish")
}

group = "de.jexcellence.hibernate"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    //hibernate platform
    implementation(platform("org.hibernate.orm:hibernate-platform:6.6.4.Final"))

    //use the versions from the platform of hibernate
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
}

tasks.register("cleanMavenDeployLocally") {
    dependsOn("clean", "publishToMavenLocal")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = group.toString()
            artifactId = "JEHibernate"
            version = version.toString()
            description = "Hibernate integration for JExcellence"
        }
    }
}
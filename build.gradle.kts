plugins {
    id("java")
    id("xyz.jpenilla.run-paper") version "2.2.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.papermc.paperweight.userdev") version "1.5.11"
}

group = "dev.benndorf"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.20.4-R0.1-SNAPSHOT")
    implementation("org.ode4j:core:0.5.2")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks {
    processResources {
        filesMatching(listOf("plugin.yml")) {
            expand("version" to project.version)
        }
    }
}

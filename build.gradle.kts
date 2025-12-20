plugins {
    `maven-publish`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.2.1"
    kotlin("jvm") version "2.0.0"
}

group = "com.inspecta.plugin"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
}

gradlePlugin {
    website.set("https://github.com/HossamSadekk/Inspecta")
    vcsUrl.set("https://github.com/HossamSadekk/Inspecta")

    plugins {
        create("inspecta") {
            id = "com.inspecta.plugin"
            implementationClass = "com.inspecta.plugin.InspectaPlugin"
            displayName = "Inspecta"
            description = "A powerful CLI tool to audit Android app size, assets, libraries, and unused resources."
            tags.set(listOf("android", "analytics", "size", "bloat", "assets"))
        }
    }
}
dependencies {
    implementation("com.android.tools.build:gradle:8.3.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
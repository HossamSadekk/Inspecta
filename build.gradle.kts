plugins {
    `maven-publish`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.2.1"
    kotlin("jvm") version "2.0.0"
}

group = "org.plugin.inspecta"
version = "1.1.2"

// Configure Java compatibility
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Configure Kotlin to target JVM 17
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}

repositories {
    mavenCentral()
    google()
}

gradlePlugin {
    website.set("https://github.com/HossamSadekk/Inspecta")
    vcsUrl.set("https://github.com/HossamSadekk/Inspecta")
    plugins {
        create("inspecta") {
            id = "org.plugin.inspecta"
            implementationClass = "org.plugin.inspecta.InspectaPlugin"
            displayName = "Inspecta"
            description = "A powerful CLI tool to audit Android app size, assets, libraries, and unused resources."
            tags.set(listOf("android", "analytics", "size", "bloat", "assets"))
            version = "1.1.2"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.github.HossamSadekk"
            artifactId = "Inspecta"
            version = "1.1.2"
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
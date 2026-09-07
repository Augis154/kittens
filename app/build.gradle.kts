plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // This dependency is used by the application.
    implementation(libs.guava)

    // JSON (de)serialization for the network protocol.
    implementation(libs.gson)
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    // Define the main class for the application.
    mainClass = "kittens.client.Client"
}

// Custom task to run the Client
tasks.register<JavaExec>("runClient") {
    group = "application"
    mainClass = "kittens.client.Client"
    classpath = sourceSets["main"].runtimeClasspath
}

// Custom task to run the Server
tasks.register<JavaExec>("runServer") {
    group = "application"
    mainClass = "kittens.server.Server"
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in` // Allows you to type commands into the server console
}

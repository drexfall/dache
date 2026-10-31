plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(projects.pluginRuntime)
    // Serves recorded responses for `test --fixtures`.
    implementation(libs.ktor.client.mock)
    testImplementation(kotlin("test"))
}

// The registry's CI downloads this distribution (distZip) from a release, so it carries a version.
version = "0.1.0"

application {
    mainClass = "dev.dache.plugin.cli.MainKt"
    applicationName = "dache-plugin"
}

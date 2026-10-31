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

application {
    mainClass = "dev.dache.plugin.cli.MainKt"
    applicationName = "dache-plugin"
}

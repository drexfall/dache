plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// Turns manifests/*.json into Kotlin string constants, so bundled plugins need no resource loading.
val generateBundledManifests by tasks.registering {
    val manifestsDir = layout.projectDirectory.dir("manifests").asFile
    val outputDir = layout.buildDirectory.dir("generated/bundled")
    inputs.dir(manifestsDir).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(outputDir)
    doLast {
        val entries =
            manifestsDir
                .listFiles { file -> file.extension == "json" }
                .orEmpty()
                .sortedBy { it.name }
                .joinToString("\n") { file ->
                    val json = file.readText()
                    require("\"\"\"" !in json) { "${file.name} contains a triple quote" }
                    val literal = json.replace("$", "\${'$'}")
                    "        \"${file.nameWithoutExtension}\" to\n            \"\"\"$literal\"\"\","
                }
        val dir = outputDir.get().asFile.resolve("dev/dache/plugin/registry")
        dir.deleteRecursively()
        dir.mkdirs()
        dir.resolve("BundledManifests.kt").writeText(
            """
            |// Generated from plugin-registry/manifests by generateBundledManifests. Do not edit.
            |package dev.dache.plugin.registry
            |
            |internal val bundledManifestJson: Map<String, String> =
            |    mapOf(
            |$entries
            |    )
            |
            """.trimMargin(),
        )
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    android {
        namespace = "dev.dache.plugin.registry"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        withHostTest {}
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    jvm {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBundledManifests)
            dependencies {
                api(projects.pluginRuntime)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.androidx.datastore.preferences.core)
            }
        }
        androidMain.dependencies {
            implementation(libs.tink.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

// KMP has no `test` task; alias it so `./gradlew test` runs the shared tests on both targets.
tasks.register("test") {
    group = "verification"
    dependsOn("jvmTest", "testAndroidHostTest")
}

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }

    android {
        namespace = "dev.dache.app"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        withHostTest {}
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        androidResources { enable = true }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core)
            api(projects.pluginRegistry)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.components.resources)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        jvmTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

compose.resources {
    packageOfResClass = "dev.dache.app.resources"
}

compose.desktop {
    application {
        mainClass = "dev.dache.app.MainKt"
        // jpackage isn't in every JDK (Android Studio's JBR lacks it); PACKAGING_JDK names one that has it.
        providers.environmentVariable("PACKAGING_JDK").orNull?.let { javaHome = it }
        nativeDistributions {
            // MSI builds only on Windows (jpackage needs WiX there); the release workflow builds it on a Windows runner.
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Msi)
            packageName = "dache"
            packageVersion = providers.gradleProperty("dache.version").get()
            description = "One dashboard for your hosting, DNS, CI, servers and uptime"
            vendor = "Dache"
            // TLS needs jdk.crypto.ec on JDK 17-21; the rest come from Ktor, OkHttp and DataStore.
            modules("java.naming", "java.net.http", "jdk.crypto.ec", "jdk.unsupported", "java.management")
            providers.gradleProperty("dache.distDir").orNull?.let { outputBaseDir.set(file(it)) }
            linux {
                iconFile.set(rootProject.file("packaging/linux/dache.png"))
            }
            windows {
                iconFile.set(rootProject.file("packaging/windows/dache.ico"))
                menuGroup = "Dache"
                perUserInstall = true
                shortcut = true
                // Fixed for good: Windows uses it to recognise newer installers as upgrades of this app.
                upgradeUuid = "a476e80c-6deb-4294-980e-4300303417f6"
            }
        }
    }
}

// The AppImage for Linux releases: the jpackage app image wrapped by packaging/linux/build-appimage.sh.
val packageAppImage by tasks.registering(Exec::class) {
    group = "compose desktop"
    description = "Builds Dache-<version>-x86_64.AppImage from the desktop distributable."
    dependsOn("createDistributable")
    val version = compose.desktop.application.nativeDistributions.packageVersion
    val base = compose.desktop.application.nativeDistributions.outputBaseDir
    val appImageDir = base.map { it.dir("main/app/dache") }
    val output = base.map { it.file("main/appimage/Dache-$version-x86_64.AppImage") }
    outputs.file(output)
    executable = rootProject.file("packaging/linux/build-appimage.sh").absolutePath
    argumentProviders +=
        CommandLineArgumentProvider {
            listOf(appImageDir.get().asFile.absolutePath, output.get().asFile.absolutePath)
        }
}

// KMP has no `test` task; alias it so `./gradlew test` runs the shared tests on both targets.
tasks.register("test") {
    group = "verification"
    dependsOn("jvmTest", "testAndroidHostTest")
}

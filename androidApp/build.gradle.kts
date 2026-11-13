plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

/** major * 10000 + minor * 100 + patch, so every release's code is above the one before it. */
fun versionCodeOf(version: String): Int {
    val (major, minor, patch) = version.substringBefore('-').split('.').map(String::toInt)
    require(minor < 100 && patch < 100) { "dache.version $version doesn't fit the versionCode scheme" }
    return major * 10_000 + minor * 100 + patch
}

android {
    namespace = "dev.dache.android"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "dev.dache"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        // One version source: dache.version in gradle.properties. 0.1.0 -> code 100, 1.2.3 -> 10203.
        versionName = providers.gradleProperty("dache.version").get()
        versionCode = versionCodeOf(versionName!!)
    }

    signingConfigs {
        // The release key comes from the environment: CI decodes it from a secret on tags. Without it the release
        // build is signed with the debug key so CI can still install it on an emulator; that APK is not for users.
        create("release") {
            val keystore = providers.environmentVariable("DACHE_KEYSTORE_FILE").orNull
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = providers.environmentVariable("DACHE_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("DACHE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("DACHE_KEY_PASSWORD").orNull
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(projects.app)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime)
}

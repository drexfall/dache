rootProject.name = "dache"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":core")
include(":app")
include(":androidApp")
include(":plugin-api")
include(":plugin-runtime")
include(":plugin-registry")
include(":tools:plugin-cli")

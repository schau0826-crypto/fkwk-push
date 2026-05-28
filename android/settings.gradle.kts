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
        // Shizuku API 托管在 jitpack
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "fkwk-push"
include(":app")

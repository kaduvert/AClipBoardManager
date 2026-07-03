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
        // Classic Xposed API artifacts (de.robv.android.xposed:api)
        // maven { url = uri("https://api.xposed.info/") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ClipVault"
include(":app")

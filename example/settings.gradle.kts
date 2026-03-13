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
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "RiviumSyncExample"
include(":app")

// RiviumSync SDK (local module)
include(":rivium_sync")
project(":rivium_sync").projectDir = file("../rivium_sync")

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
        maven { url = uri("https://pub-69e86fbad8904e4a8bd3a1b2d051df1f.r2.dev/maven") }
        // Local libs folder for fallback
        flatDir {
            dirs("libs")
        }
    }
}

rootProject.name = "RiviumSyncSDK"

// PN Protocol module (local development - shared with RiviumPush)
// Only include if the local source exists, otherwise use Maven Central dependency
val pnProtocolDir = file("../../RiviumPush/protocol/android/pn-protocol")
if (pnProtocolDir.exists()) {
    include(":pn-protocol")
    project(":pn-protocol").projectDir = pnProtocolDir
}

include(":rivium_sync")

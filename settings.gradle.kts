rootProject.name = "factionscore"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        maven("https://repo.powernukkitx.org/releases")
        maven("https://jitpack.io")
        maven("https://repo.opencollab.dev/maven-releases/")
        maven("https://repo.opencollab.dev/maven-snapshots/") {
            mavenContent {
                snapshotsOnly()
            }
        }
    }
}

// The engine is kept as its own composite build (it ships its own
// settings.gradle.kts / pluginManagement / repositories) so that pulling
// upstream PowerNukkitX changes into engine/powernukkitx stays a clean
// source merge instead of fighting our root build file.
includeBuild("engine/powernukkitx")

include("plugins:FactionsCore")

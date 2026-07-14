pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        maven("https://jitpack.io")
        maven("https://repo.opencollab.dev/maven-releases/")
        maven("https://repo.opencollab.dev/maven-snapshots/") {
            mavenContent {
                snapshotsOnly()
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        // Vendored copies of every -SNAPSHOT dependency (protocol, raknet, math, libdeflate):
        // upstream snapshot repos drift and purge old builds, which broke fresh clones with
        // "cannot find symbol DataStorePropertyValueType". Checked first so these exact,
        // known-good artifacts always win; everything else still resolves remotely.
        maven {
            name = "vendored"
            url = uri(rootDir.resolve("local-repo"))
        }
        mavenLocal()
        mavenCentral()
        maven("https://repo.maven.apache.org/maven2/")
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

rootProject.name = "powernukkitx"

// Enable Gradle enterprise features for better build insights
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
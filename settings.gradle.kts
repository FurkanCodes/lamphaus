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
        // Lamphaus's patched Media3 ExoPlayer (scripts/build-media3-exoplayer.sh).
        maven {
            url = uri("third_party/maven")
            content { includeGroup("com.lamphaus.media3") }
        }
    }
}

rootProject.name = "Lamphaus"

include(":app")
include(":core:model")
include(":core:provider")
include(":core:data")
include(":core:player")
include(":core:ffmpeg")
include(":benchmark")

plugins {
    alias(libs.plugins.android.library)
}

// Media3's FFmpeg audio extension (Apache-2.0, copied from androidx/media
// 1.11.0 libraries/decoder_ffmpeg) over LGPL FFmpeg built by
// scripts/build-ffmpeg-audio.sh. Keep the Media3 version in step with
// gradle/libs.versions.toml.
android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    api(libs.androidx.media3.decoder)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.annotation)
    compileOnly(libs.checker.qual)
}

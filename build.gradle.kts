plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.android.baselineprofile) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

/*
 * Media3 ExoPlayer comes from Lamphaus's patched build (off-heap sample
 * buffers for "Native memory buffer", PLY-NET-01), substituted everywhere so
 * session, HLS, DASH, and libass resolve against the same classes. It is
 * built from the Media3 version in the catalog: bumping Media3 needs
 * scripts/build-media3-exoplayer.sh re-run with the new tag first.
 */
val media3ForkBase = "1.11.0"
val media3ForkVersion = "$media3ForkBase-lamphaus.1"
check(libs.versions.media3.get() == media3ForkBase) {
    "Media3 ${libs.versions.media3.get()} has no Lamphaus ExoPlayer build; run scripts/build-media3-exoplayer.sh for it."
}
subprojects {
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("androidx.media3:media3-exoplayer"))
                .using(module("com.lamphaus.media3:media3-exoplayer:$media3ForkVersion"))
                .because("off-heap sample buffers (third_party/media3/patches)")
        }
    }
}

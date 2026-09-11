import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.baselineprofile)
}

// Single tracked version identity consumed by Gradle and release tooling
// (plan §2). Never reuse a published versionCode for different bytes.
val versionProps = Properties()
rootProject.file("gradle/version.properties").inputStream().use(versionProps::load)
val releaseVersionName: String = versionProps.getProperty("versionName")
val releaseVersionCode: Int = versionProps.getProperty("versionCode").toInt()
val updateFeedUrl: String =
    providers.gradleProperty("lamphaus.updateFeedUrl").orNull
        ?: "https://raw.githubusercontent.com/furkancodes/lamphaus/release-metadata/updates/v1/index.json"

val supabaseUrl = providers.gradleProperty("lamphaus.supabaseUrl").orNull.orEmpty()
val supabasePublishableKey = providers.gradleProperty("lamphaus.supabasePublishableKey").orNull.orEmpty()
val cloudConfigured = supabaseUrl.isNotBlank() && supabasePublishableKey.isNotBlank()
val releaseStorePath = providers.environmentVariable("LAMPHAUS_RELEASE_STORE_FILE").orNull
val releaseKeyAlias = providers.environmentVariable("LAMPHAUS_RELEASE_KEY_ALIAS").orNull
val releaseStorePassword = providers.environmentVariable("LAMPHAUS_RELEASE_STORE_PASSWORD").orNull
val releaseKeyPassword = providers.environmentVariable("LAMPHAUS_RELEASE_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    releaseStorePath,
    releaseKeyAlias,
    releaseStorePassword,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.lamphaus.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lamphaus.app"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("boolean", "CLOUD_CONFIGURED", cloudConfigured.toString())
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabasePublishableKey\"")
        buildConfigField("String", "CAST_APPLICATION_ID", "\"${providers.gradleProperty("lamphaus.castAppId").orNull.orEmpty()}\"")
        buildConfigField("String", "EMAIL_LINK_DOMAIN", "\"${providers.gradleProperty("lamphaus.emailLinkDomain").orNull ?: "links.lamphaus.app"}\"")
        buildConfigField("String", "WEB_CLIENT_ID", "\"${providers.gradleProperty("lamphaus.webClientId").orNull.orEmpty()}\"")
        buildConfigField("String", "UPDATE_FEED_URL", "\"$updateFeedUrl\"")
        // Production update discovery only; debug/staging builds never poll it.
        buildConfigField("boolean", "UPDATES_ENABLED", "false")
        buildConfigField("boolean", "BENCHMARK_FIXTURES", "false")
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("production") {
                storeFile = file(checkNotNull(releaseStorePath))
                keyAlias = releaseKeyAlias
                storePassword = releaseStorePassword
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            buildConfigField("boolean", "DIAGNOSTICS_DEFAULT", "false")
        }
        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            matchingFallbacks += listOf("debug")
        }
        release {
            signingConfig = signingConfigs.findByName("production")
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "DIAGNOSTICS_DEFAULT", "false")
            buildConfigField("boolean", "UPDATES_ENABLED", "true")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // Isolated updater QA: separate package/certificate/feed, release
        // optimization mirrored so A→B rehearsal matches production.
        create("updaterQa") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".updaterqa"
            versionNameSuffix = "-updaterqa"
            buildConfigField("String", "UPDATE_FEED_URL", "\"https://raw.githubusercontent.com/furkancodes/lamphaus/release-metadata-qa/updates/v1/index.json\"")
            buildConfigField("boolean", "UPDATES_ENABLED", "true")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = true
        lintConfig = file("lint.xml")
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "OldTargetApi")
    }

    // Generated profiles ship in the universal APK without regenerating
    // during normal assembly (plan §7).
    baselineProfile {
        saveInSrc = true
        automaticGenerationDuringBuild = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:provider"))
    implementation(project(":core:data"))
    implementation(project(":core:player"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material3.window.size)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.tv.material)
    implementation(libs.coil.compose)
    implementation(libs.google.material)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.cast.framework)
    implementation(libs.play.services.cast.tv)
    implementation(libs.zxing.core)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":benchmark"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

androidComponents {
    finalizeDsl { extension ->
        extension.buildTypes.filter { it.name == "benchmarkRelease" || it.name == "nonMinifiedRelease" }.forEach { target ->
            target.apply {
                applicationIdSuffix = ".benchmark"
                versionNameSuffix = "-benchmark"
                // Baseline Profile output must retain source class names so it
                // can be rewritten for each independently obfuscated release.
                isMinifyEnabled = false
                isShrinkResources = isMinifyEnabled
                buildConfigField("boolean", "BENCHMARK_FIXTURES", "true")
                buildConfigField("boolean", "CLOUD_CONFIGURED", "false")
                buildConfigField("boolean", "UPDATES_ENABLED", "false")
                buildConfigField("String", "SUPABASE_URL", "\"\"")
                buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"\"")
            }
            extension.sourceSets.getByName(target.name).assets.srcDir("src/benchmark/assets")
        }
    }
}

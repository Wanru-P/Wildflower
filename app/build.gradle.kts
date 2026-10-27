import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.FileInputStream

// Reads API keys from local.properties (gitignored) so they never touch
// source control. Empty string if unset — the services treat that as
// "unavailable" (PlantNetIdentifier / GeminiPlantInfoService both check).
val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        load(FileInputStream(localFile))
    }
}

// Public service URL only. CloudBase passed live session, context-recall and nature checks.
// The existing Worker remains deployed as a rollback option, not the production default.
val natureProxyOrigin = providers.gradleProperty("natureProxyOrigin")
    .orElse("https://wildflower-ai-test-d5cge7e1432c7-1410768603.ap-shanghai.app.tcloudbase.com/wildflower-ai").get().trim().trimEnd('/')
require(Regex("https://[A-Za-z0-9.-]+(?::[0-9]+)?(?:/[A-Za-z0-9_~-]+)*").matches(natureProxyOrigin)) {
    "natureProxyOrigin must be an HTTPS service base without credentials, query, or /v1 endpoint."
}

private val releaseSigningVariableNames = listOf(
    "WILDFLOWER_RELEASE_STORE_FILE",
    "WILDFLOWER_RELEASE_STORE_PASSWORD",
    "WILDFLOWER_RELEASE_KEY_ALIAS",
    "WILDFLOWER_RELEASE_KEY_PASSWORD",
)
private val releaseSigningEnvironment = releaseSigningVariableNames.associateWith(System::getenv)
private val releaseSigningReady = releaseSigningEnvironment.values.all { !it.isNullOrBlank() }
private val releasePackagingRequested = gradle.startParameter.taskNames.any { requested ->
    requested.substringAfterLast(':') in setOf(
        "assembleRelease",
        "bundleRelease",
        "packageRelease",
        "installRelease",
    )
}

if (releasePackagingRequested && !releaseSigningReady) {
    throw GradleException(
        "Release signing unavailable. Set the four WILDFLOWER_RELEASE_* environment variables; " +
            "the release build will not fall back to the Debug certificate.",
    )
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.floralens"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.floralens"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Explicit opt-in only: never populate an ordinary Debug garden with fictitious records.
        buildConfigField("boolean", "SEED_DEMO", providers.gradleProperty("seedDemo").orElse("false").get())
        // Internal editorial tools are opt-in; ordinary Debug and release are presentation-safe.
        buildConfigField("boolean", "INTERNAL_TOOLS", providers.gradleProperty("internalTools").orElse("false").get())

        buildConfigField(
            "String",
            "PLANTNET_API_KEY",
            "\"${localProperties.getProperty("PLANTNET_API_KEY", "")}\"",
        )
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\"",
        )
        buildConfigField(
            "String",
            "YOUTUBE_API_KEY",
            "\"${localProperties.getProperty("YOUTUBE_API_KEY", "")}\"",
        )
        buildConfigField(
            "String",
            "OPENAI_API_KEY",
            "\"${localProperties.getProperty("OPENAI_API_KEY", "")}\"",
        )
        // This public origin is safe to embed. Provider secrets and short-lived
        // session credentials are never BuildConfig values.
        buildConfigField(
            "String",
            "NATURE_PROXY_ORIGIN",
            "\"$natureProxyOrigin\"",
        )

        val mapsKey = localProperties.getProperty("BAIDU_MAP_ANDROID_AK", "")
        manifestPlaceholders["BAIDU_MAP_ANDROID_AK"] = mapsKey
        buildConfigField("boolean", "BAIDU_MAP_CONFIGURED", mapsKey.isNotBlank().toString())
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = rootProject.file(releaseSigningEnvironment.getValue("WILDFLOWER_RELEASE_STORE_FILE")!!)
                storePassword = releaseSigningEnvironment.getValue("WILDFLOWER_RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigningEnvironment.getValue("WILDFLOWER_RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningEnvironment.getValue("WILDFLOWER_RELEASE_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    // Don't compress the bundled .tflite model
    androidResources {
        noCompress += "tflite"
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Persistence
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    // Camera
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // ML (on-device)
    implementation(libs.tflite)
    implementation(libs.tflite.support)

    // Reminders
    implementation(libs.work.runtime.ktx)

    implementation("com.baidu.lbsyun:BaiduMapSDK_Map:8.2.0")
    implementation("com.baidu.lbsyun:BaiduMapSDK_Util:8.2.0")

    // Network — Pl@ntNet identification + Gemini insights (online mode only)
    implementation(libs.okhttp)

    // Images
    implementation(libs.coil.compose)
    // Already present through Coil; explicit dependency for the card decoder (API 26+).
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.core.splashscreen)
}

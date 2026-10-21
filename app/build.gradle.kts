import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ── Release version ────────────────────────────────────────────────
//
// versionCode is the monotonically increasing integer the Play Store
// orders releases by, so it must never repeat and must never go
// backwards. versionName is the string a human reads; the release tag
// carries it.
//
// Both defaults are declared here, in one place, in a shape that
// scripts/check-release-version.sh can read:
//
//   val defaultVersionCode = <positive integer>
//   val defaultVersionName = "<non-empty string>"
//
// A release build may override either with -Pcyclecare.versionCode= /
// -Pcyclecare.versionName=, or with the CYCLECARE_VERSION_CODE /
// CYCLECARE_VERSION_NAME environment variables. The release workflow
// passes back exactly the values the version check validated, so the
// artifact that is built is the artifact that was checked.
val defaultVersionCode = 7
val defaultVersionName = "1.0"

val releaseVersionCode: Int = run {
    val raw = (providers.gradleProperty("cyclecare.versionCode").orNull
        ?: providers.environmentVariable("CYCLECARE_VERSION_CODE").orNull
        ?: defaultVersionCode.toString()).trim()
    raw.toIntOrNull()?.takeIf { it > 0 }
        ?: throw GradleException(
            "cyclecare.versionCode must be a positive integer, but was '$raw'.",
        )
}

val releaseVersionName: String = run {
    val raw = (providers.gradleProperty("cyclecare.versionName").orNull
        ?: providers.environmentVariable("CYCLECARE_VERSION_NAME").orNull
        ?: defaultVersionName).trim()
    if (raw.isEmpty()) {
        throw GradleException("cyclecare.versionName must not be empty.")
    }
    raw
}

// ── Release signing ────────────────────────────────────────────────
//
// Signing material is resolved from environment variables first, then
// from a keystore.properties file. Nothing here reads a password from a
// committed file, and a build with no credentials configured simply
// produces an unsigned release artifact, which is what the CI release
// build in ci.yml needs.
//
// Environment variables:
//   CYCLECARE_STORE_FILE, CYCLECARE_STORE_PASSWORD,
//   CYCLECARE_KEY_ALIAS, CYCLECARE_KEY_PASSWORD
//   CYCLECARE_KEYSTORE_PROPERTIES — optional. The path to the
//     keystore.properties file when it is not the repository root.
//     The release workflow reconstructs that file in the runner's
//     temporary directory and points this at it, so no file holding a
//     password ever exists inside the repository.
val keystorePropertiesFile: File? = (
    providers.environmentVariable("CYCLECARE_KEYSTORE_PROPERTIES").orNull?.let(::File)
        ?: rootProject.file("keystore.properties")
    ).takeIf { it.exists() }

val keystoreProperties = Properties().apply {
    keystorePropertiesFile?.inputStream()?.use(::load)
}

val releaseStoreFile: String? =
    providers.environmentVariable("CYCLECARE_STORE_FILE").orNull
        ?: keystoreProperties.getProperty("storeFile")
val releaseStorePassword: String? =
    providers.environmentVariable("CYCLECARE_STORE_PASSWORD").orNull
        ?: keystoreProperties.getProperty("storePassword")
val releaseKeyAlias: String? =
    providers.environmentVariable("CYCLECARE_KEY_ALIAS").orNull
        ?: keystoreProperties.getProperty("keyAlias")
val releaseKeyPassword: String? =
    providers.environmentVariable("CYCLECARE_KEY_PASSWORD").orNull
        ?: keystoreProperties.getProperty("keyPassword")

val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.cyclecare.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cyclecare.tracker"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The exported Room schemas are needed by the instrumented tests
    // that build a database at an old schema version and migrate it
    // with the production migration list.
    sourceSets["androidTest"].assets.srcDir("$rootDir/core/database/schemas")

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            manifestPlaceholders["adMobApplicationId"] = "ca-app-pub-3940256099942544~3347511713"
        }

        getByName("release") {
            manifestPlaceholders["adMobApplicationId"] = "ca-app-pub-2324135355814969~9539632647"
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }

            // R8 is enabled. See app/proguard-rules.pro.
            //
            // The model-enum keep rule is load-bearing: enums are persisted
            // as constant names and read back with enumValueOf(), so
            // shrinking without it would silently rewrite persisted user
            // values on the first run of a release build.
            isMinifyEnabled = true

            // Resource shrinking is deliberately deferred. AdMob and UMP
            // resolve some resources dynamically, and gradle.properties
            // already disables R8 optimized resource shrinking. Enable this
            // only after the shrunk build has been verified on a device.
            isShrinkResources = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:home"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:logging"))
    implementation(project(":feature:insights"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:privacy"))
    implementation(project(":feature:export"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)

    // AdMob
    implementation("com.google.android.gms:play-services-ads:25.2.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("com.google.android.gms:play-services-base:18.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0")
    implementation("com.google.guava:guava:32.1.3-android")

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.truth)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

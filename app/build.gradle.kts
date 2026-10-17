import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing material is resolved from environment variables first,
// then from the gitignored keystore.properties file for local
// convenience. Nothing here reads a password from a committed file, and
// a build with no credentials configured simply produces an unsigned
// release artifact, which is what CI needs.
//
// Environment variables:
//   CYCLECARE_STORE_FILE, CYCLECARE_STORE_PASSWORD,
//   CYCLECARE_KEY_ALIAS, CYCLECARE_KEY_PASSWORD
val keystoreProperties = Properties().apply {
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    }
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
        versionCode = 7
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

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
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.truth)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

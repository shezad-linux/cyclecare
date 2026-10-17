plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cyclecare.benchmark"
    compileSdk = 35
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = false
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

androidComponents {
    beforeVariants(selector().all()) { variantBuilder ->
        variantBuilder.enable = variantBuilder.buildType == "benchmark"
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.test.uiautomator)
}

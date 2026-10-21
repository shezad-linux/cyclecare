plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)

    // Adopted: the fake clock lives in :core:testing, which the domain
    // engines require in order to be deterministic under test.
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    // Three tests in this module guard the repository rather than a
    // class: `StoredCycleLengthGuardTest`, `CycleLengthRangeTest` and
    // `PredictionShapeGuardTest` read every module's `src/main` tree at
    // runtime and fail when a rule is broken somewhere else.
    //
    // Gradle cannot see a file read from inside a test, so without this
    // declaration the test task stays UP-TO-DATE after a change to a
    // feature module and the guard never runs again. A guard that can be
    // skipped silently is not a guard, so the sources it reads are
    // declared as inputs.
    inputs.files(
        rootProject.subprojects.map { it.layout.projectDirectory.dir("src/main") },
    )
}

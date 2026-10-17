pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CycleCare"

include(
    ":app",
    ":benchmark",
    ":core:model",
    ":core:domain",
    ":core:data",
    ":core:database",
    ":core:designsystem",
    ":core:security",
    ":core:testing",
    ":feature:onboarding",
    ":feature:home",
    ":feature:calendar",
    ":feature:logging",
    ":feature:insights",
    ":feature:settings",
    ":feature:privacy",
    ":feature:export",
)

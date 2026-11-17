pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "journeys"

include(
    "journeys-api",
    "journeys-core",
    "journeys-integration-fancynpcs",
    "journeys-integration-placeholderapi",
)

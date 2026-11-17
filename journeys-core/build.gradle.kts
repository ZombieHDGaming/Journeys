plugins {
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

description = "The Journeys Paper plugin."

dependencies {
    implementation(project(":journeys-api"))
    implementation(project(":journeys-integration-fancynpcs"))
    implementation(project(":journeys-integration-placeholderapi"))

    compileOnly(libs.paper.api)
    // Downloaded at runtime by JourneysLoader through Paper's library loader, so they are not shaded.
    compileOnly(libs.hikari)

    testImplementation(libs.paper.api)
    testImplementation(libs.hikari)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

// Library coordinates handed to the runtime loader. Kept in sync with the version catalog.
val runtimeLibraries = listOf(libs.hikari, libs.sqlite.jdbc, libs.mariadb.jdbc).map { provider ->
    provider.map { "${it.module.group}:${it.module.name}:${it.versionConstraint.requiredVersion}" }
}

tasks {
    processResources {
        val props = mapOf(
            "version" to project.version.toString(),
            "libraries" to runtimeLibraries.joinToString(",") { it.get() },
        )
        inputs.properties(props)
        filesMatching(listOf("paper-plugin.yml", "journeys-libraries.txt")) {
            expand(props)
        }
    }

    shadowJar {
        archiveBaseName = "Journeys"
        archiveClassifier = ""
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("26.3")
        jvmArgs("-Dcom.mojang.eula.agree=true")
        downloadPlugins {
            // Uncomment to test the FancyNPCs hook locally.
            // modrinth("fancynpcs", libs.versions.fancynpcs.get())
        }
    }
}

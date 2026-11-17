description = "PlaceholderAPI hook for Journeys. Shaded into the plugin jar and only loaded when PlaceholderAPI is installed."

dependencies {
    compileOnly(project(":journeys-api"))
    compileOnly(libs.paper.api)
    compileOnly(libs.placeholderapi)

    testImplementation(project(":journeys-api"))
    testImplementation(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

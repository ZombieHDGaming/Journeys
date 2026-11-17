description = "FancyNPCs hook for Journeys. Shaded into the plugin jar and only loaded when FancyNpcs is installed."

dependencies {
    compileOnly(project(":journeys-api"))
    compileOnly(libs.paper.api)
    compileOnly(libs.fancynpcs)
}

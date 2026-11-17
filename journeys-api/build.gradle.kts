plugins {
    `maven-publish`
}

description = "Public API for Journeys. Compile against this to add quest objective, condition and reward types."

dependencies {
    compileOnlyApi(libs.paper.api)

    testImplementation(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

java {
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "journeys-api"
            from(components["java"])
        }
    }
}

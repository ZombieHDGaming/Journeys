plugins {
    `java-library`
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.run.paper) apply false
}

allprojects {
    group = "dev.voidscape.journeys"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.fancyinnovations.com/releases")
        maven("https://repo.extendedclip.com/releases/")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release = 25
        options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial", "-parameters"))
    }

    tasks.withType<Javadoc>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

plugins {
    // Lets Gradle auto-download a JDK it needs (e.g. the runServer task's own launcher) when no
    // locally installed toolchain matches, instead of failing outright.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "clicksorted"

includeBuild("KcMcLib")

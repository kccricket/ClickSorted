import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    java
    jacoco
    id("com.gradleup.shadow") version "9.4.3"
    id("org.bxteam.runserver") version "1.2.2"
    id("com.modrinth.minotaur") version "2.9.0"
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
}

group = project.property("group") as String
version = project.property("version") as String

// Game versions supported by this release, kept in gradle.properties (comma-separated).
// Append new versions there when compatibility is verified — no other changes needed.
val gameVersionsList: List<String> = (project.findProperty("gameVersions") as? String)
    ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
    ?: listOf("1.21.5")

repositories {
    mavenCentral()
    // Spigot API snapshots
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    // Paper API
    maven("https://repo.papermc.io/repository/maven-public/")
    // EngineHub (WorldGuard etc.)
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    // paper-api is provided by the server at runtime — compile against it but don't bundle it
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")

    // bStats is bundled and relocated by Shadow
    implementation("org.bstats:bstats-bukkit:3.0.1")

    // Gson is provided by the server at runtime (Paper bundles it); compile against it but don't bundle it
    compileOnly("com.google.code.gson:gson:2.11.0")

    // paper-api must be available at both test compile time and test runtime (integration tests
    // bootstrap the full plugin via MockBukkit). compileOnly is NOT inherited by test scopes in Gradle.
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.+")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.1.2:4.113.1")
    // Gradle 9 no longer auto-includes the JUnit Platform launcher; add it explicitly.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Emit Java 21 bytecode regardless of the JDK used to compile.
tasks.withType<JavaCompile> {
    options.release.set(21)
    options.encoding = "UTF-8"
}

// paper-api (and the MockBukkit test harness) are compiled for Java 25 but are
// NEVER bundled — the server provides the API at runtime.  Gradle 9's JVM ecosystem
// compatibility checks would otherwise refuse to resolve JVM-25 JARs for a JVM-17
// compile target.  We override the TargetJvmVersion attribute on the two configurations
// that hold those provided/test JARs so resolution uses the running JDK version.
val runningJvm = Runtime.version().feature()
listOf(
    configurations.compileClasspath,
    configurations.testCompileClasspath,
    configurations.testRuntimeClasspath
).forEach { cfg ->
    cfg.configure {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, runningJvm)
        }
    }
}

// Filter only paper-plugin.yml — it's the only resource that contains a ${project.version} token.
// The other YAMLs (lang.yml, items.yml, groups.yml, config.yml) contain literal ${} and $
// characters that Gradle's expand() would corrupt. We bind "project.version" to match the
// Maven ${project.version} placeholder without touching the source file.
tasks.processResources {
    val props = mapOf("project" to mapOf("version" to version))
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

// Tests: JUnit Platform + the --add-opens flags required by MockBukkit v4 + ByteBuddy
// on Java 16+ where ClassLoader.defineClass is restricted. These replace the Maven
// Surefire <argLine> configuration.
tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "-XX:+EnableDynamicAgentLoading",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
    )
    finalizedBy(tasks.jacocoTestReport)
}

// Limit JaCoCo instrumentation to our source packages so that JaCoCo does not try to
// instrument Paper API classes (compiled for Java 25), which JaCoCo cannot handle.
jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/java/main")) {
            include("net/kccricket/clicksorted/**")
        }
    )
}

// Shadow JAR configuration — replaces maven-shade-plugin
tasks.shadowJar {
    // Produce build/libs/ClickSorted-${version}.jar — the distributable artifact.
    archiveFileName.set("ClickSorted-${version}.jar")
    // Relocate bStats so it doesn't conflict with other plugins bundling the same library
    relocate("org.bstats", "net.kccricket.clicksorted.thirdparty")
    // Retain manifest Main-Class for parity with the Maven build
    manifest {
        attributes["Main-Class"] = "net.kccricket.clicksorted.ClickSortedPlugin"
    }
}

// The shadow JAR is the distributable artifact; disable the plain jar task to prevent a
// naming collision between the two outputs on case-insensitive filesystems (macOS/Windows).
tasks.jar {
    enabled = false
}

// Make the standard 'build' task produce the shadow JAR
tasks.build {
    dependsOn(tasks.shadowJar)
}

// Run a local Paper dev server with the plugin already loaded.
// Usage: ./gradlew runServer [-PmcVersion=1.21.6]
tasks.runServer {
    serverType(org.bxteam.runserver.ServerType.PAPER)
    serverVersion((project.findProperty("mcVersion") as String?) ?: "26.2")
    acceptMojangEula()
    // Use the Shadow JAR (bStats relocated) instead of the plain jar task output.
    inputTask(tasks.named("shadowJar"))
}

// ---------------------------------------------------------------------------
// Release publishing
// ---------------------------------------------------------------------------

/**
 * Extracts the body of the latest CHANGELOG.md entry, stripping the version heading
 * and "Release date:" line. Entries are delimited by horizontal-rule ("---") separators.
 */
fun latestChangelog(): String {
    val lines = file("CHANGELOG.md").readLines()
    val startIdx = lines.indexOfFirst { it.matches(Regex("^# ClickSorted \\S+.*")) }
    require(startIdx >= 0) { "No '# ClickSorted <version>' heading found in CHANGELOG.md" }
    val body = lines.drop(startIdx + 1) // skip the heading line itself
    val endIdx = body.indexOfFirst { it.matches(Regex("^---\\s*$")) }
    val entryLines = if (endIdx >= 0) body.take(endIdx) else body
    return entryLines
        .filter { !it.startsWith("Release date:") }
        .joinToString("\n")
        .trim()
}

/** Writes the latest changelog entry to build/release-notes.md for the GitHub release step. */
tasks.register("writeReleaseNotes") {
    description = "Writes the latest CHANGELOG.md entry to build/release-notes.md"
    val outputFile = layout.buildDirectory.file("release-notes.md")
    outputs.file(outputFile)
    doLast {
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(latestChangelog())
        }
    }
}

modrinth {
    token.set(providers.environmentVariable("MODRINTH_TOKEN"))
    projectId.set("clicksorted")
    versionNumber.set(version.toString())
    versionName.set("ClickSorted ${version}")
    versionType.set("release")
    uploadFile.set(tasks.shadowJar.flatMap { it.archiveFile })
    gameVersions.set(gameVersionsList)
    loaders.set(listOf("paper", "folia"))
    changelog.set(providers.provider { latestChangelog() })
    // Keep the Modrinth resource page body in sync with README.md on each publish.
    syncBodyFrom.set(providers.fileContents(layout.projectDirectory.file("README.md")).asText)
}

hangarPublish {
    publications.register("plugin") {
        version.set(project.version as String)
        id.set("ClickSorted")
        channel.set("Release")
        changelog.set(latestChangelog())
        apiKey.set(providers.environmentVariable("HANGAR_API_TOKEN"))
        // Keep the Hangar resource page body in sync with README.md on each publish.
        pages {
            resourcePage(project.file("README.md").readText())
        }
        platforms {
            paper {
                jar.set(tasks.shadowJar.flatMap { it.archiveFile })
                platformVersions.set(gameVersionsList)
            }
        }
    }
}

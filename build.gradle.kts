import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    java
    jacoco
    id("com.gradleup.shadow") version "9.4.2"
}

group = project.property("group") as String
version = project.property("version") as String

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

// Emit Java 17 bytecode regardless of the JDK used to compile.
// We intentionally do NOT pin a Java 17 toolchain because paper-api 26.1.2 is compiled
// for Java 25; a JDK 17 compiler would reject those classpath classes.
// --release 17 tells the JDK 25 compiler to produce v61 bytecode and restrict the
// platform API to the Java 17 standard library.
tasks.withType<JavaCompile> {
    options.release.set(17)
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

// Filter only plugin.yml — it's the only resource that contains a ${project.version} token.
// The other YAMLs (lang.yml, items.yml, groups.yml, config.yml) contain literal ${} and $
// characters that Gradle's expand() would corrupt. We bind "project.version" to match the
// Maven ${project.version} placeholder without touching the source file.
tasks.processResources {
    val props = mapOf("project" to mapOf("version" to version))
    inputs.properties(props)
    filesMatching("plugin.yml") {
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
            include("me/desht/**", "xyz/chengzi/clicksort/**")
        }
    )
}

// Shadow JAR configuration — replaces maven-shade-plugin
tasks.shadowJar {
    // Produce build/libs/clicksort.jar (no classifier, fixed name)
    archiveFileName.set("clicksort-${version}.jar")
    // Relocate bStats so it doesn't conflict with other plugins bundling the same library
    relocate("org.bstats", "me.desht.clicksort.thirdparty")
    // Retain manifest Main-Class for parity with the Maven build
    manifest {
        attributes["Main-Class"] = "me.desht.clicksort.ClickSortPlugin"
    }
}

// Make the standard 'build' task produce the shadow JAR
tasks.build {
    dependsOn(tasks.shadowJar)
}

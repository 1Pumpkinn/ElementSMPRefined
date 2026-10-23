import proguard.gradle.ProGuardTask

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("com.guardsquare:proguard-gradle:7.8.0") }
}

plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.0.0"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    test {
        useJUnitPlatform()
    }

    shadowJar {
        archiveClassifier.set("shaded")
        minimize()
        mergeServiceFiles()
    }

    jar {
        enabled = true
    }

    runServer {
        enabled = true
        minecraftVersion("26.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        // Fills ${version} in plugin.yml from gradle.properties.
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)

        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

// ProGuard needs a full JDK (jmods), so it is opt-in instead of part of every build:
//   ./gradlew obfuscate   -> build/libs/<name>-<version>-obf.jar  (use this one for releases)
val obfuscate = tasks.register<ProGuardTask>("obfuscate") {
    group = "build"
    description = "Obfuscates the shaded jar with ProGuard."
    dependsOn(tasks.shadowJar)
    injars(tasks.shadowJar.flatMap { it.archiveFile })
    outjars(layout.buildDirectory.file("libs/${project.name}-${project.version}-obf.jar"))

    libraryjars("${System.getProperty("java.home")}/jmods")
    libraryjars(configurations.compileClasspath.get())

    configuration("proguard-rules.pro")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

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
    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
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
        val props = mapOf("version" to version)

        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
}

val obfuscate = tasks.register<ProGuardTask>("obfuscate") {
    dependsOn(tasks.shadowJar)
    injars(tasks.shadowJar.flatMap { it.archiveFile })
    outjars(layout.buildDirectory.file("libs/${project.name}-${project.version}-obf.jar"))

    libraryjars("${System.getProperty("java.home")}/jmods")
    libraryjars(configurations.compileClasspath.get())

    configuration("proguard-rules.pro")
}

tasks.build {
    dependsOn(obfuscate)
}
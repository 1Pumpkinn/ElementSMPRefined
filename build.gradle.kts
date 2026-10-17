import proguard.gradle.ProGuardTask
buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.8.0")
    }
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
        archiveClassifier.set("")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        minimize()
        mergeServiceFiles()
    }

    jar {
        enabled = true
    }

    runServer {
        enabled = true
        minecraftVersion("26.2")
        jvmArgs("-Xms1G", "-Xmx1G")
    }

    processResources {
        val props = mapOf("version" to version)

        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
}

val obfuscate by tasks.registering(ProGuardTask::class) {
    dependsOn(tasks.shadowJar)

    // JDK 25 from your toolchain, not whatever JVM Gradle itself runs on
    val jdkHome = javaToolchains.launcherFor(java.toolchain).get()
        .metadata.installationPath.asFile
    val jmodFilter = mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class")

    injars(tasks.shadowJar.flatMap { it.archiveFile })
    outjars(layout.buildDirectory.file("libs/${project.name}-obf.jar"))

    libraryjars(jmodFilter, "$jdkHome/jmods/java.base.jmod")
    libraryjars(jmodFilter, "$jdkHome/jmods/java.logging.jmod")
    libraryjars(configurations.compileClasspath.get())

    configuration("proguard-rules.pro")
}
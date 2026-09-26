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
        enabled = false
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
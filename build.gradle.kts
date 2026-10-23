import proguard.gradle.ProGuardTask

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("com.guardsquare:proguard-gradle:7.8.1") }
}

plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
}

group = "net.rose"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.134-stable")

    // Add shaded dependencies here, e.g.:
    // implementation("com.zaxxer:HikariCP:5.1.0")
}

// Full runtime view of compileOnly deps (Paper + all its transitive libs) for ProGuard
val proguardLibs: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    extendsFrom(configurations.compileOnly.get())
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, LibraryElements.JAR))
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
            expand(props)
        }
    }

    jar {
        archiveClassifier.set("plain")
    }

    shadowJar {
        archiveClassifier.set("")
        // relocate("com.zaxxer", "net.rose.elementSMPRefined.libs.hikari")
    }

    build {
        dependsOn(shadowJar)
    }

    register<ProGuardTask>("obfuscate") {
        group = "build"
        dependsOn(shadowJar)

        verbose()

        injars(shadowJar.flatMap { it.archiveFile })
        outjars(layout.buildDirectory.file("libs/${project.name}-${project.version}-obf.jar"))

        // JDK classes (Gradle must run on a full JDK 25, not a JRE)
        val jmods = "${System.getProperty("java.home")}/jmods"
        listOf(
            "java.base",
            "java.logging",
            "java.sql",
            "java.desktop",
            "java.xml",
            "java.net.http",
            "java.management",
            "java.naming",
            "java.prefs",
            "java.compiler"
        ).forEach {
            libraryjars(
                mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
                "$jmods/$it.jmod"
            )
        }

        // Paper API, Bukkit, Adventure and every transitive compileOnly dependency
        libraryjars(proguardLibs)

        configuration("proguard-rules.pro")
    }
}
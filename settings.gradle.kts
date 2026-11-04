pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net") {
            content {
                includeGroupByRegex("net\\.fabricmc.*")
            }
        }
    }

    val loomVersion = providers.gradleProperty("loom_version").get()
    val kotlinVersion = providers.gradleProperty("kotlin_version").get()

    plugins {
        id("net.fabricmc.fabric-loom") version loomVersion
        kotlin("jvm") version kotlinVersion
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
}

stonecutter {
    create(rootProject) {
        version("26.1.x", "26.1")
        versions("26.2", "26.3", "26.4")
        vcsVersion = "26.1.x"
    }
}

rootProject.name = "quoi"

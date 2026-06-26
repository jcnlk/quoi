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

    val loom_version: String by settings
    val kotlin_version: String by settings

    plugins {
        id("net.fabricmc.fabric-loom") version loom_version
        kotlin("jvm") version kotlin_version
    }
}

rootProject.name = "quoi"

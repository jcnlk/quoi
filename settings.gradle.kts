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

rootProject.name = "quoi"
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net")
    }

    val loomVersion = providers.gradleProperty("loom_version").get()
    val kotlinVersion = providers.gradleProperty("kotlin_version").get()

    plugins {
        id("fabric-loom") version loomVersion
        kotlin("jvm") version kotlinVersion
    }
}

rootProject.name = "quoi"

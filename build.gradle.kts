import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("fabric-loom")
    kotlin("jvm")
    `maven-publish`
}

version = property("mod_version") as String

fun optionalBoolProperty(name: String): Boolean =
    providers.gradleProperty(name).orNull?.toBooleanStrictOrNull() == true

repositories {
    mavenCentral()
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
//    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    if (optionalBoolProperty("quoi.enableDevAuth")) {
        modRuntimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.1")
    }

    modImplementation("io.github.classgraph:classgraph:4.8.184")
    include("io.github.classgraph:classgraph:4.8.184")

    property("minecraft_lwjgl_version").let {

        modImplementation("org.lwjgl:lwjgl-nanovg:$it")
        include("org.lwjgl:lwjgl-nanovg:$it")

        listOf("windows", "linux", "macos", "macos-arm64").forEach { v ->
            modImplementation("org.lwjgl:lwjgl-nanovg:$it:natives-$v")
            include("org.lwjgl:lwjgl-nanovg:$it:natives-$v")
        }
    }
}

loom {
    runConfigs.named("client") {
        isIdeConfigGenerated = true
        vmArgs.add("-Dmixin.debug.export=true")

        if (optionalBoolProperty("quoi.enableDevAuth")) {
            vmArgs.addAll(
                arrayOf(
                    "-Ddevauth.enabled=true",
                    "-Ddevauth.account=main"
                )
            )
        }

        if (optionalBoolProperty("quoi.enableEnhancedClassRedefinition")) {
            vmArgs.add("-XX:+AllowEnhancedClassRedefinition")
        }
    }

    runConfigs.named("server") {
        isIdeConfigGenerated = false
    }
}

afterEvaluate {
    loom.runs.named("client") {
        vmArg("-javaagent:${configurations.compileClasspath.get().find { it.name.contains("sponge-mixin") }}")
    }
}

tasks {
    processResources {
        filesMatching("fabric.mod.json") {
            expand(getProperties())
        }
    }

    compileKotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
            freeCompilerArgs.add("-Xlambdas=class")
        }
    }

    compileJava {
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }
}

java {
    withSourcesJar()
}

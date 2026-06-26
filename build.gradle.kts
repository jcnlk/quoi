import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom")
    kotlin("jvm")
    `maven-publish`
}

version = property("mod_version") as String

repositories {
    mavenCentral()
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1") {
        content {
            includeGroup("me.djtheredstoner")
        }
    }
    maven("https://maven.terraformersmc.com/") {
        content {
            includeGroup("com.terraformersmc")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")

    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    runtimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.2")
    runtimeOnly("org.apache.httpcomponents:httpclient:4.5.14")
    compileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")

    implementation("io.github.classgraph:classgraph:4.8.184")
    include("io.github.classgraph:classgraph:4.8.184")

    property("minecraft_lwjgl_version").let {

        implementation("org.lwjgl:lwjgl-nanovg:$it")
        include("org.lwjgl:lwjgl-nanovg:$it")

        listOf("windows", "linux", "macos", "macos-arm64").forEach { v ->
            implementation("org.lwjgl:lwjgl-nanovg:$it:natives-$v")
            include("org.lwjgl:lwjgl-nanovg:$it:natives-$v")
        }
    }
}

loom {
    runs {
        named("client") {
            generateRunConfig.set(true)
            runDirectory.set(layout.projectDirectory.dir("runs/${project.property("minecraft_version")}"))
            jvmArguments.addAll(
                "-Dmixin.debug.export=true",
                "-Ddevauth.enabled=true",
                "-Ddevauth.account=${providers.gradleProperty("devauth_account").orElse("main").get()}",
                "-XX:+AllowEnhancedClassRedefinition",
                "-XX:+IgnoreUnrecognizedVMOptions", // JetBrains Runtime only; Temurin/OpenJDK reject this flag. IntelliJ run configs use JBR.
            )
            jvmArguments.add(
                providers.provider {
                    "-javaagent:${configurations.compileClasspath.get().find { it.name.contains("sponge-mixin") }}"
                }
            )
        }

        named("server") {
            generateRunConfig.set(false)
        }
    }

    accessWidenerPath.set(file("src/main/resources/quoi.accesswidener"))
}

tasks {
    processResources {
        val properties = listOf(
            "mod_id",
            "mod_version",
            "mod_name",
            "loader_version",
            "fabric_api_version",
            "minecraft_version",
            "fabric_kotlin_version",
        ).associateWith { providers.gradleProperty(it).get() }

        inputs.properties(properties)

        filesMatching("fabric.mod.json") {
            expand(properties)
        }
    }

    compileKotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_25
            freeCompilerArgs.add("-Xlambdas=class")
        }
    }

    compileJava {
        sourceCompatibility = "25"
        targetCompatibility = "25"
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }
}

java {
    withSourcesJar()
}

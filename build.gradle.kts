import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("fabric-loom")
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

base {
    archivesName.set(property("archives_base_name") as String)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modRuntimeOnly("me.djtheredstoner:DevAuth-fabric:${property("devauth_version")}")
    runtimeOnly("org.apache.httpcomponents:httpclient:${property("httpclient_version")}")
    modCompileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")

    property("classgraph_version").let {
        modImplementation("io.github.classgraph:classgraph:$it")
        include("io.github.classgraph:classgraph:$it")
    }

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
        val modProperties = listOf(
            "mod_id",
            "mod_name",
            "mod_version",
            "loader_version",
            "fabric_api_version",
            "fabric_kotlin_version",
            "minecraft_version",
        ).associateWith { providers.gradleProperty(it).get() }

        inputs.properties(modProperties)

        filesMatching("fabric.mod.json") {
            expand(modProperties)
        }
    }

    compileKotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
            freeCompilerArgs.add("-Xlambdas=class")
        }
    }

    compileJava {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }

    withSourcesJar()
}

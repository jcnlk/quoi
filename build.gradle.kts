import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom")
    kotlin("jvm")
    `maven-publish`
}

version = property("mod_version") as String

repositories {
    mavenCentral()
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")

    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    implementation("net.fabricmc.fabric-api:fabric-command-api-v2:3.0.5+e2bdee7847")
    implementation("net.fabricmc.fabric-api:fabric-lifecycle-events-v1:4.0.6+a9b9c17647")
    implementation("net.fabricmc.fabric-api:fabric-networking-api-v1:6.3.0+50a808ce47")
    implementation("net.fabricmc.fabric-api:fabric-rendering-v1:23.0.4+c7e428fd47")
    implementation("net.fabricmc.fabric-api:fabric-screen-api-v1:5.0.1+d871b99e47")
    runtimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.1")
    runtimeOnly("org.apache.httpcomponents:httpclient:4.5.14")

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
    runConfigs.named("client") {
        isIdeConfigGenerated = true
        runDir = "runs/${project.property("minecraft_version")}"
        vmArgs.addAll(
            arrayOf(
                "-Dmixin.debug.export=true",
                "-Ddevauth.enabled=true",
                "-Ddevauth.account=${providers.gradleProperty("devauth_account").orElse("main").get()}",
                // JetBrains Runtime only; Temurin/OpenJDK reject this flag. IntelliJ run configs use JBR.
                "-XX:+AllowEnhancedClassRedefinition",
            )
        )
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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import net.fabricmc.loom.task.RunGameTask

plugins {
    id("net.fabricmc.fabric-loom")
    kotlin("jvm")
}

val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val modVersion = providers.gradleProperty("mod_version").get()
val archivesBaseName = providers.gradleProperty("archives_base_name").get()

version = "$modVersion+$minecraftVersion"

base {
    archivesName.set(archivesBaseName)
}

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
    runtimeOnly("me.djtheredstoner:DevAuth-fabric:${property("devauth_version")}")
    runtimeOnly("org.apache.httpcomponents:httpclient:${property("httpclient_version")}")
    compileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")

    property("classgraph_version").let {
        implementation("io.github.classgraph:classgraph:$it")
        include("io.github.classgraph:classgraph:$it")
    }
}

loom {
    runConfigs.named("client") {
        isIdeConfigGenerated = true
        runDir = "runs/${project.property("minecraft_version")}"
        vmArgs.addAll(
            arrayOf(
                "-Dmixin.debug.export=true",
                "-Dmixin.hotSwap=true",
                "-Ddevauth.enabled=true",
                "-Ddevauth.account=${providers.gradleProperty("devauth_account").orElse("main").get()}",
                "-XX:+AllowEnhancedClassRedefinition",
                "-XX:+IgnoreUnrecognizedVMOptions", // JetBrains Runtime only; Temurin/OpenJDK reject this flag. IntelliJ run configs use JBR.
            )
        )
    }

    runConfigs.named("server") {
        isIdeConfigGenerated = false
    }

    accessWidenerPath = file("src/main/resources/quoi.accesswidener")
}

afterEvaluate {
    val mixinAgent = configurations.runtimeClasspath.get().incoming.artifactView {
        componentFilter {
            it is ModuleComponentIdentifier &&
                it.group == "net.fabricmc" &&
                it.module == "sponge-mixin"
        }
    }.files.singleFile

    loom.runs.named("client") {
        vmArg("-javaagent:${mixinAgent.absolutePath}")
    }
}

tasks {
    withType<RunGameTask>().configureEach {
        notCompatibleWithConfigurationCache("Loom run classpaths can become stale after switching Minecraft version branches")
    }

    processResources {
        val properties = listOf(
            "mod_id",
            "mod_version",
            "mod_name",
            "loader_version",
            "fabric_api_version",
            "minecraft_dependency",
            "fabric_kotlin_version",
        ).associateWith { providers.gradleProperty(it).get() }

        inputs.properties(properties)

        filesMatching("fabric.mod.json") {
            expand(properties)
        }
    }

    withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
            freeCompilerArgs.add("-Xlambdas=class")
        }
    }

    withType<JavaCompile>().configureEach {
        options.release.set(25)
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    named<Jar>("jar") {
        from("LICENSE") {
            rename("LICENSE", "LICENSE_$archivesBaseName")
        }
    }
}

kotlin {
    jvmToolchain(25)
}

java {
    withSourcesJar()
}

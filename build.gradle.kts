import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("net.fabricmc.fabric-loom")
    kotlin("jvm")
}

val mcVersion = findProperty("minecraft_version")?.toString() ?: sc.current.version
val modVersion = providers.gradleProperty("mod_version").get()
val archivesBaseName = providers.gradleProperty("archives_base_name").get()
val usesSharedSources = sc.current.isActive

sourceSets.named("main") {
    java.exclude(
        when {
            sc.current.parsed < "26.3" -> listOf(
                "quoi/mixins/FirstPersonHandsAndItemsMixin.java",
                "quoi/mixins/ItemInHandRendererMixin.java",
                "quoi/mixins/OverlayLevelRendererMixin.java",
                "quoi/mixins/OverlayQuadSortingMixin.java",
                "quoi/mixins/LevelExtractorMixin.java",
            )
            else -> listOf("quoi/mixins/LegacyItemInHandRendererMixin.java")
        }
    )
}

version = "$modVersion+$mcVersion"

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
    minecraft("com.mojang:minecraft:$mcVersion")

    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    runtimeOnly("me.djtheredstoner:DevAuth-fabric:${property("devauth_version")}")
    runtimeOnly("org.apache.httpcomponents:httpclient:${property("httpclient_version")}")
    findProperty("mixinextras_version")?.let {
        runtimeOnly("io.github.llamalad7:mixinextras-fabric:$it")
    }
    compileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")

    testImplementation(platform("org.junit:junit-bom:${property("junit_version")}"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    property("classgraph_version").let {
        implementation("io.github.classgraph:classgraph:$it")
        listOf("classgraph", "classgraph-classpath", "classgraph-vfs", "classgraph-base").forEach { artifact ->
            include("io.github.classgraph:$artifact:$it")
        }
    }
}

loom {
    runs {
        named("client") {
            generateRunConfig.set(true)
            runDirectory.set(layout.projectDirectory.dir(providers.gradleProperty("run_directory").orElse("run").get()))
            jvmArguments.addAll(
                "-Dmixin.debug.export=true",
                "-Ddevauth.configDir=${rootProject.file(".devauth").absolutePath}",
                "-XX:+AllowEnhancedClassRedefinition",
                "-XX:+IgnoreUnrecognizedVMOptions",
            )
        }

        named("server") {
            generateRunConfig.set(false)
        }
    }

    accessWidenerPath.set(rootProject.file("src/main/resources/quoi.accesswidener"))
}

tasks {
    test {
        useJUnitPlatform()
    }

    register<Sync>("collectJar") {
        group = "build"
        description = "Collects this Minecraft version's JAR in the root build/libs directory."
        outputs.upToDateWhen { false }
        from(named<Jar>("jar").flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${sc.current.project}"))
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
        ).associateWith { project.property(it).toString() }

        inputs.properties(properties)

        filesMatching("fabric.mod.json") {
            expand(properties)
        }

    }

    withType<KotlinCompile>().configureEach {
        // Kotlin's incremental cache stores source paths. Switching Stonecutter's
        // source directory must invalidate it even when relative paths are equal.
        inputs.property("stonecutterUsesSharedSources", usesSharedSources)
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
        from(rootProject.file("LICENSE")) {
            rename("LICENSE", "LICENSE_$archivesBaseName")
        }
    }
}

kotlin {
    jvmToolchain(25)
}

import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("net.fabricmc.fabric-loom")
    kotlin("jvm")
}

val mcVersion = sc.current.version
val modVersion = providers.gradleProperty("mod_version").get()
val archivesBaseName = providers.gradleProperty("archives_base_name").get()
val usesSharedSources = sc.current.isActive

// Keep the complete NanoVG and vanilla GUI implementations in shared sources.
// Select their files here so source condition blocks aren't needed.
val rendererSources = when {
    sc.current.parsed < "26.2" -> listOf(
        "quoi/utils/ui/rendering/McBackend.kt",
        "quoi/utils/ui/rendering/McFonts.kt",
        "quoi/utils/ui/rendering/McImages.kt",
        "quoi/utils/ui/rendering/RendererBackend.kt",
        "quoi/utils/ui/rendering/ShelfPacker.kt",
        "quoi/utils/ui/rendering/TextEngine.kt",
        "quoi/utils/ui/rendering/UIGeometry.kt",
        "quoi/utils/ui/rendering/UIRenderer.kt",
        "quoi/utils/ui/rendering/Image.kt",
        "quoi/utils/render/WorldRenderContextUtils.kt",
        "quoi/module/impl/render/RenderOptimiser.kt",
    )
    else -> listOf(
        "quoi/utils/ui/rendering/NVGRenderer.kt",
        "quoi/utils/ui/rendering/NVGSpecialRenderer.kt",
        "quoi/utils/ui/rendering/LegacyImage.kt",
        "quoi/utils/render/LegacyWorldRenderContextUtils.kt",
        "quoi/module/impl/render/LegacyRenderOptimiser.kt",
    )
}
kotlin.sourceSets.named("main") {
    kotlin.exclude(rendererSources)
}
sourceSets.named("main") {
    java.exclude(when {
        sc.current.parsed < "26.2" -> listOf(
            "quoi/mixins/GuiMixin.java",
            "quoi/mixins/ItemInHandRendererMixin.java",
            "quoi/mixins/GuiGraphicsMixin.java",
            "quoi/mixins/HudMixin.java",
            "quoi/mixins/accessors/GuiGraphicsExtractorAccessor.java",
            "quoi/mixins/FirstPersonHandsAndItemsMixin.java",
        )
        sc.current.parsed < "26.3" -> listOf(
            "quoi/mixins/LegacyGuiMixin.java",
            "quoi/mixins/FirstPersonHandsAndItemsMixin.java",
            "quoi/mixins/ItemInHandRendererMixin.java",
        )
        else -> listOf("quoi/mixins/LegacyGuiMixin.java", "quoi/mixins/LegacyItemInHandRendererMixin.java")
    })
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

    property("classgraph_version").let {
        implementation("io.github.classgraph:classgraph:$it")
        include("io.github.classgraph:classgraph:$it")
    }

    findProperty("minecraft_lwjgl_version")?.let {
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
            runDirectory.set(layout.projectDirectory.dir("run"))
            jvmArguments.addAll(
                "-Dmixin.debug.export=true",
                "-Ddevauth.enabled=true",
                "-Ddevauth.account=${providers.gradleProperty("devauth_account").orElse("main").get()}",
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

package quoi.init

import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.FabricLoader
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.extensibility.IMixinInfo
import java.io.File
import java.net.JarURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.TreeSet

// based on: https://github.com/Noamm9/NoammAddons/blob/26.1.2/src/main/kotlin/com/github/noamm9/init/AutoMixinDiscovery.kt
class AutoMixinDiscovery : IMixinConfigPlugin {
    private var mixins = emptyList<String>()

    override fun onLoad(mixinPackage: String) {
        if (FabricLoader.getInstance().environmentType != EnvType.CLIENT) return
        val basePath = mixinPackage.replace('.', '/')
        val result = TreeSet<String>()

        val resources = javaClass.classLoader.getResources(basePath)
        while (resources.hasMoreElements()) {
            val url = resources.nextElement()
            when (url.protocol) {
                "jar" -> collectFromJar(url, basePath, result)
                "file" -> collectFromDir(url, basePath, result)
            }
        }

        mixins = result.toList()
    }

    private fun collectFromJar(url: URL, basePath: String, result: MutableSet<String>) {
        val connection = url.openConnection() as JarURLConnection
        connection.useCaches = false
        connection.jarFile.use { jar ->
            jar.entries().asSequence()
                .map { it.name }
                .filter { isMixinClass(it, basePath) }
                .map { relativeClassName(it, basePath) }
                .forEach(result::add)
        }
    }

    private fun collectFromDir(url: URL, basePath: String, result: MutableSet<String>) {
        val root = File(URLDecoder.decode(url.path, Charsets.UTF_8)).takeIf(File::isDirectory) ?: return
        root.walkTopDown()
            .filter(File::isFile)
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .filter(::isRelativeMixinClass)
            .map { relativeClassName("$basePath/$it", basePath) }
            .forEach(result::add)
    }

    private fun relativeClassName(name: String, basePath: String) =
        name.removePrefix("$basePath/").removeSuffix(".class").replace('/', '.')

    private fun isMixinClass(name: String, basePath: String) =
        name.startsWith("$basePath/") && name.endsWith(".class") &&
            '$' !in name && name.substringAfterLast('/') !in setOf("module-info.class", "package-info.class")

    private fun isRelativeMixinClass(name: String) =
        name.endsWith(".class") && '$' !in name &&
            name.substringAfterLast('/') !in setOf("module-info.class", "package-info.class")

    override fun getMixins() = mixins.toMutableList()
    override fun getRefMapperConfig() = null
    override fun shouldApplyMixin(targetClassName: String?, mixinClassName: String?) = true
    override fun acceptTargets(myTargets: MutableSet<String>?, otherTargets: MutableSet<String>?) {}
    override fun preApply(targetClassName: String?, targetClass: ClassNode?, mixinClassName: String?, info: IMixinInfo?) {}
    override fun postApply(targetClassName: String?, targetClass: ClassNode?, mixinClassName: String?, info: IMixinInfo?) {}
}

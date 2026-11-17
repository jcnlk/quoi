package quoi.utils

import quoi.QuoiMod.mc

inline val sf get() = mc.window.guiScale
inline val width get() = mc.window.width
inline val height get() = mc.window.height

inline val scaledWidth get() = width / sf
inline val scaledHeight get() = height / sf

inline val level get() = requireNotNull(mc.level) { "tried to access level before world was loaded" }
inline val player get() = requireNotNull(mc.player) { "tried to access player before it was loaded" }
inline val connection get() = requireNotNull(mc.connection) { "mc.connection is null" }
inline val gameMode get() = requireNotNull(mc.gameMode) { "mc.gameMode is null" }
inline val inGame get() = mc.level != null && mc.player != null

fun Any?.equalsOneOf(vararg options: Any?): Boolean {
    return options.any { this == it }
}

inline fun <K, V> MutableMap<K, V>.removeIf(predicate: (K, V) -> Boolean) {
    val iterator = this.entries.iterator()
    while (iterator.hasNext()) {
        val entry = iterator.next()
        if (predicate(entry.key, entry.value)) iterator.remove()
    }
}

fun Long.millisSince(): Long = System.currentTimeMillis() - this

fun Long.hasPassed(millis: Long): Boolean = millisSince() >= millis

inline fun <T> measureMillis(block: () -> T): Pair<T, Long> {
    val start = System.nanoTime()
    val result = block()
    return result to (System.nanoTime() - start) / 1_000_000
}

/**
 * `-1` returns the last element.
 */
fun <T> List<T>.getWrapped(index: Int): T {
    require(isNotEmpty()) { "List is empty" }
    return this[index.mod(size)]
}

/**
 * Returns the first element if [element] is missing.
 */
fun <T> List<T>.nextAfter(element: T): T = getWrapped(indexOf(element) + 1)

/**
 * Returns the last element if [element] is missing.
 */
fun <T> List<T>.previousBefore(element: T): T {
    val index = indexOf(element)
    return getWrapped(if (index == -1) -1 else index - 1)
}

inline fun <reified E : Enum<E>> E.next(): E = enumValues<E>().let { it[(ordinal + 1) % it.size] }

inline fun <reified E : Enum<E>> E.previous(): E = enumValues<E>().let { it[(ordinal - 1).mod(it.size)] }

inline fun <reified E : Enum<E>> enumValueOfOrNull(name: String): E? =
    enumValues<E>().firstOrNull { it.name.equals(name, ignoreCase = true) }

fun <K> MutableMap<K, Int>.addCount(key: K, amount: Int = 1) {
    this[key] = (this[key] ?: 0) + amount
}

fun <K> MutableMap<K, Long>.addCount(key: K, amount: Long) {
    this[key] = (this[key] ?: 0L) + amount
}

fun <K> MutableMap<K, Double>.addCount(key: K, amount: Double) {
    this[key] = (this[key] ?: 0.0) + amount
}

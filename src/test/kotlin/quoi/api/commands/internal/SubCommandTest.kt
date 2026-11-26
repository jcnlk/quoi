package quoi.api.commands.internal

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SubCommandTest {
    private val source = FabricClientCommandSource::class.java.let { type ->
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            error("Unexpected call to command source: ${method.name}")
        })
    }

    @Test
    fun `optional arguments are passed as null when omitted and as typed values when supplied`() {
        val calls = mutableListOf<Pair<String, Int?>>()
        val dispatcher = register { item: String, count: Int? -> calls.add(item to count) }

        assertEquals(1, dispatcher.execute("test arrows", source))
        assertEquals(1, dispatcher.execute("test arrows 64", source))
        assertFailsWith<CommandSyntaxException> { dispatcher.execute("test arrows many", source) }

        assertEquals(listOf("arrows" to null, "arrows" to 64), calls)
    }

    @Test
    fun `greedy arguments pass the rest of the command to the handler`() {
        val calls = mutableListOf<String>()
        val dispatcher = register { query: GreedyString -> calls.add(query.value) }

        assertEquals(1, dispatcher.execute("test Wither Impact", source))

        assertEquals(listOf("Wither Impact"), calls)
    }

    @Test
    fun `required arguments cannot follow optional arguments`() {
        assertFailsWith<IllegalArgumentException> {
            register { count: Int?, item: String -> "$count $item" }
        }
    }

    private fun register(handler: Function<*>): CommandDispatcher<FabricClientCommandSource> {
        val node = literal<FabricClientCommandSource>("test")
        SubCommand("test", "quoi", handler).attachTo(node)
        return CommandDispatcher<FabricClientCommandSource>().apply { register(node) }
    }
}

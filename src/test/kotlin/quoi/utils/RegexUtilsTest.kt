package quoi.utils

import org.junit.jupiter.api.Test
import quoi.utils.RegexUtils.anyMatch
import quoi.utils.RegexUtils.anyMatches
import quoi.utils.RegexUtils.findGroup
import quoi.utils.RegexUtils.firstGroup
import quoi.utils.RegexUtils.firstMatch
import quoi.utils.RegexUtils.forEachMatch
import quoi.utils.RegexUtils.groupOrEmpty
import quoi.utils.RegexUtils.groupOrNull
import quoi.utils.RegexUtils.ifFound
import quoi.utils.RegexUtils.ifMatches
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegexUtilsTest {
    private val lore = listOf("Rarity: Legendary", "Cost", "1,250 Coins", "Charges: 3/5")
    private val charges = Regex("""Charges: (?<current>\d+)/(?<max>\d+)""")

    @Test
    fun `reads groups from single lines`() {
        assertEquals("3", charges.findGroup(lore[3]))
        assertEquals("5", charges.findGroup(lore[3], "max"))
        assertNull(charges.findGroup(lore[0]))
        assertNull(charges.findGroup(lore[3], 7))
        assertNull(charges.findGroup(lore[3], "missing"))
    }

    @Test
    fun `reads groups from the first matching line`() {
        assertEquals("1,250", Regex("""([\d,]+) Coins""").firstGroup(lore))
        assertEquals("3", charges.firstGroup(lore, "current"))
        assertNull(Regex("Mythic").firstGroup(lore))
    }

    @Test
    fun `runs blocks only on matches`() {
        assertEquals(8, charges.ifFound(lore[3]) { groupValues[1].toInt() + groupValues[2].toInt() })
        assertNull(charges.ifFound(lore[0]) { error("should not run") })
        assertEquals("Legendary", Regex("""Rarity: (\w+)""").ifMatches(lore[0]) { groupValues[1] })
        assertNull(Regex("Rarity").ifMatches(lore[0]) { error("partial match must not run") })
        assertEquals(3, charges.firstMatch(lore) { groupOrNull("current")?.toInt() })

        val found = mutableListOf<String>()
        Regex("""^(\w+)""").forEachMatch(lore) { found += groupValues[1] }
        assertEquals(listOf("Rarity", "Cost", "1", "Charges"), found)
    }

    @Test
    fun `match checks and group fallbacks`() {
        assertTrue(Regex("Coins").anyMatch(lore))
        assertFalse(Regex("Mythic").anyMatch(lore))
        assertTrue(listOf(Regex("Cost"), Regex("Price")).anyMatches("Cost"))
        assertFalse(listOf(Regex("Cost")).anyMatches("Cost: 5"))

        val match = Regex("""(?<a>x)?y""").find("y")!!
        assertNull(match.groupOrNull("a"))
        assertNull(match.groupOrNull("missing"))
        assertEquals("", match.groupOrEmpty("a"))
    }
}

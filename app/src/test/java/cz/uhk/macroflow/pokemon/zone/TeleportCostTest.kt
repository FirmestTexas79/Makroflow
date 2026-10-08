package cz.uhk.macroflow.pokemon.zone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Teleport: 10 za den zdarma, pak cena cesty pěšky (docs/adr/0065). */
class TeleportCostTest {
    @Test fun `spoje odpovidaji mape zony`() {
        val json = listOf(File("src/main/assets/zone/zone1.json"), File("app/src/main/assets/zone/zone1.json")).first { it.exists() }.readText()
        val fromJson = Regex("\"a\":\\s*\"(\\w+)\"[^}]*?\"b\":\\s*\"(\\w+)\"").findAll(json).map { it.groupValues[1] to it.groupValues[2] }.toSet()
        assertEquals(fromJson, ZoneOne.LINKS.toSet())
    }

    @Test fun `nejkratsi cesta`() {
        assertEquals(0, ZoneOne.hops("TOWN", "TOWN"))
        assertEquals(1, ZoneOne.hops("TOWN", "MEADOW"))
        assertEquals(3, ZoneOne.hops("TOWN", "SKY_PASS"))
        assertEquals(5, ZoneOne.hops("HIDDEN_GROVE", "MINES"))
    }

    @Test fun `cena a blokace`() {
        assertEquals(0, ZoneOne.teleportCost("TOWN", "SKY_PASS", 9))
        assertEquals(9, ZoneOne.teleportCost("TOWN", "SKY_PASS", 10))
        val seen = ZoneOne.LOCATIONS.toSet()
        assertNull(ZoneOne.teleportBlock("MEADOW", "TOWN", seen, 0, 9, 9, usedToday = 10, energy = 3))
        assertEquals(ZoneOne.Block.Energy(9, 5), ZoneOne.teleportBlock("SKY_PASS", "TOWN", seen, 0, 9, 9, usedToday = 12, energy = 5))
    }
}

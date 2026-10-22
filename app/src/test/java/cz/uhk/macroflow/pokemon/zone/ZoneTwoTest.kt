package cz.uhk.macroflow.pokemon.zone

import cz.uhk.macroflow.pokemon.cave.AshPlain
import cz.uhk.macroflow.pokemon.cave.CaveMap
import cz.uhk.macroflow.pokemon.cave.EchoCanyon
import cz.uhk.macroflow.pokemon.cave.OzvenaCity
import cz.uhk.macroflow.pokemon.cave.QuietOrchard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Mapa Zóna 2 (Akt II) v deníku: rozvržení z gen_zone2.py sedí s mapami lokací, teleport a objevování. */
class ZoneTwoTest {
    private fun file(rel: String): File = listOf(File(rel), File("app/$rel")).first { it.exists() }
    private val maps: Map<String, CaveMap> = mapOf("ASH_PLAIN" to AshPlain.MAP, "QUIET_ORCHARD" to QuietOrchard.MAP, "ECHO_CANYON" to EchoCanyon.MAP, "OZVENA" to OzvenaCity.MAP)

    @Test fun zone2LocationsHaveArrivalNamesAndZone() {
        ZoneOne.ZONE2.forEach {
            assertEquals(2, ZoneOne.zoneOf(it))
            assertEquals(maps.getValue(it).exitNode, ZoneOne.ARRIVAL[it])
            assertTrue(ZoneOne.NAMES.containsKey(it))
        }
        assertEquals(1, ZoneOne.zoneOf("TOWN"))
        assertEquals(1, ZoneOne.hops("SKY_PASS", "ASH_PLAIN"))
        assertEquals(2, ZoneOne.hops("QUIET_ORCHARD", "ECHO_CANYON"))
    }

    @Test fun generatedLayoutMatchesGameNodes() {
        val json = file("src/main/assets/zone2/zone2.json").readText()
        val ends = Regex("\"(a|b)\": \"([A-Z_]+)\",\\s*\"\\1Node\": \"([a-z_]+)\",\\s*\"\\1Dir\": \"[nsew]\",\\s*\"\\1Pos\": \\[[^\\]]*\\],\\s*\"\\1Frac\": \\[\\s*([0-9.]+),\\s*([0-9.]+)\\s*\\]")
            .findAll(json).toList()
        assertEquals(6, ends.size)
        for (e in ends) {
            val g = e.groupValues
            val biome = g[2]; val node = g[3]; val fx = g[4]; val fy = g[5]
            val m = maps.getValue(biome)
            val n = m.node(node) ?: error("$biome/$node")
            assertEquals("$biome/$node x", n.x.toFloat() / m.artW, fx.toFloat(), 0.002f)
            assertEquals("$biome/$node y", n.y.toFloat() / m.artH, fy.toFloat(), 0.002f)
        }
    }

    @Test fun visitFlagsRevealZone2() {
        val s = ZoneOne.inferSeen(setOf("quiet_orchard_visited"), emptySet())
        assertTrue("ASH_PLAIN" in s && "QUIET_ORCHARD" in s && "SKY_PASS" in s)
        assertNull(ZoneOne.teleportBlock("ASH_PLAIN", "TOWN", setOf("TOWN", "ASH_PLAIN"), 0, 9, 5))
        assertEquals(ZoneOne.Block.Unknown, ZoneOne.teleportBlock("ECHO_CANYON", "TOWN", setOf("TOWN"), 0, 9, 5))
    }
}

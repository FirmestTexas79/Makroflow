package cz.uhk.macroflow.pokemon.zone

import cz.uhk.macroflow.pokemon.cave.CaveMap
import cz.uhk.macroflow.pokemon.cave.RootBridge
import cz.uhk.macroflow.pokemon.cave.RootCamp
import cz.uhk.macroflow.pokemon.cave.RootNameless
import cz.uhk.macroflow.pokemon.cave.RootSignal
import cz.uhk.macroflow.pokemon.cave.RootSlopeCamp
import cz.uhk.macroflow.pokemon.cave.RootWatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Mapa Zóna 3 (Hlubina) v deníku: rozvržení z gen_zone3.py sedí s mapami lokací, příchod a objevování. */
class ZoneThreeTest {
    private fun file(rel: String): File = listOf(File(rel), File("app/$rel")).first { it.exists() }
    private val maps: Map<String, CaveMap> = mapOf("ROOT_CAMP" to RootCamp.MAP, "ROOT_WATCH" to RootWatch.MAP, "ROOT_SIGNAL" to RootSignal.MAP,
        "ROOT_BRIDGE" to RootBridge.MAP, "ROOT_SLOPECAMP" to RootSlopeCamp.MAP, "ROOT_NAMELESS" to RootNameless.MAP)

    @Test fun zone3LocationsHaveArrivalNamesAndZone() {
        assertEquals(maps.keys, ZoneOne.ZONE3)
        ZoneOne.ZONE3.forEach {
            assertEquals(3, ZoneOne.zoneOf(it))
            assertEquals(maps.getValue(it).exitNode, ZoneOne.ARRIVAL[it])
            assertTrue(ZoneOne.NAMES.containsKey(it))
            assertTrue(it in ZoneOne.LOCATIONS)
        }
        assertEquals(2, ZoneOne.zoneOf("TOWER_ABYSS"))
        assertEquals(3, ZoneOne.hops("ROOT_CAMP", "ROOT_BRIDGE"))
    }

    @Test fun generatedLayoutMatchesGameNodes() {
        val json = file("src/main/assets/zone3/zone3.json").readText()
        val ends = Regex("\"(a|b)\": \"([A-Z_0-9]+)\",\\s*\"\\1Node\": \"([a-z_0-9]+)\",\\s*\"\\1Dir\": \"[nsew]\",\\s*\"\\1Pos\": \\[[^\\]]*\\],\\s*\"\\1Frac\": \\[\\s*([0-9.]+),\\s*([0-9.]+)\\s*\\]")
            .findAll(json).toList()
        assertEquals(10, ends.size)
        for (e in ends) {
            val g = e.groupValues
            val m = maps.getValue(g[2])
            val n = m.node(g[3]) ?: error("${g[2]}/${g[3]}")
            assertEquals("${g[2]}/${g[3]} x", n.x.toFloat() / m.artW, g[4].toFloat(), 0.002f)
            assertEquals("${g[2]}/${g[3]} y", n.y.toFloat() / m.artH, g[5].toFloat(), 0.002f)
        }
    }

    @Test fun visitFlagsRevealZone3() {
        val s = ZoneOne.inferSeen(setOf(RootSignal.VISITED_KEY, RootCamp.VISITED_KEY), emptySet())
        assertTrue("ROOT_SIGNAL" in s && "ROOT_CAMP" in s)
    }
}

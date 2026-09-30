package cz.uhk.macroflow.pokemon.zone

import cz.uhk.macroflow.pokemon.cave.CaveMap
import cz.uhk.macroflow.pokemon.cave.CaveMaps
import cz.uhk.macroflow.pokemon.cave.ForestMap
import cz.uhk.macroflow.pokemon.cave.GroveMap
import cz.uhk.macroflow.pokemon.cave.MinesMap
import cz.uhk.macroflow.pokemon.cave.SkyPass
import cz.uhk.macroflow.pokemon.story.StoryProgress
import cz.uhk.macroflow.pokemon.walk.HeroAnims
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Mapa Zóna 1, teleport a smrt postavy (docs/adr/0052). */
class ZoneOneTest {

    private fun file(rel: String): File = listOf(File(rel), File("app/$rel")).first { it.exists() }

    @Test fun secretLocationOnlyWhileStandingInIt() {
        val seen = setOf("TOWN", "MEADOW", "FOREST", "HIDDEN_GROVE")
        assertEquals(ZoneOne.Shown.HIDDEN, ZoneOne.shown("HIDDEN_GROVE", "FOREST", seen))
        assertEquals(ZoneOne.Shown.HERE, ZoneOne.shown("HIDDEN_GROVE", "HIDDEN_GROVE", seen))
        assertEquals(ZoneOne.Shown.KNOWN, ZoneOne.shown("MEADOW", "FOREST", seen))
        assertEquals(ZoneOne.Shown.FOG, ZoneOne.shown("MINES", "FOREST", seen))
        assertTrue(!ZoneOne.linkShown("FOREST", "HIDDEN_GROVE", "FOREST", seen))
        assertTrue(ZoneOne.linkShown("FOREST", "HIDDEN_GROVE", "HIDDEN_GROVE", seen))
        assertTrue(ZoneOne.linkShown("MOUNTAINS", "MINES", "TOWN", seen))       // mlha: spoj naznačí cestu
    }

    @Test fun teleportRules() {
        val seen = setOf("TOWN", "MEADOW", "MOUNTAINS", "FOREST", "HIDDEN_GROVE")
        fun block(t: String, cur: String = "TOWN", steps: Int = 0, done: Int = 9) =
            ZoneOne.teleportBlock(t, cur, seen, steps, done, 5)
        assertNull(block("MEADOW"))
        assertNull(block("MOUNTAINS"))
        assertEquals(ZoneOne.Block.Here, block("TOWN"))
        assertEquals(ZoneOne.Block.Unknown, block("MINES"))
        assertEquals(ZoneOne.Block.Secret, block("HIDDEN_GROVE"))
        assertEquals(ZoneOne.Block.Steps(1200), block("MOUNTAINS", steps = 1200))
        assertEquals(ZoneOne.Block.Forest(3, 5), block("FOREST", done = 3))
        assertNull(block("MEADOW", steps = 1200))                                 // louka zámek hor nemá
    }

    @Test fun oldSavesInferDiscoveredLocations() {
        val s = ZoneOne.inferSeen(setOf("mines_visited", "zone_seen_SKY_PASS"), setOf("TOWN"))
        assertEquals(setOf("TOWN", "MEADOW", "MOUNTAINS", "CAVE_MAZE", "MINES", "SKY_PASS"), s)
        assertEquals(setOf("TOWN"), ZoneOne.inferSeen(emptySet(), emptySet()))
        assertTrue("MEADOW" in ZoneOne.inferSeen(emptySet(), setOf("MEADOW")))
        assertTrue(StoryProgress.isStoryKey(ZoneOne.seenKey("MINES")))           // synchronizuje se
    }

    @Test fun arrivalNodesExistInCaveMaps() {
        val caves: Map<String, CaveMap> = mapOf("FOREST" to ForestMap.MAP, "CAVE_MAZE" to CaveMaps.MAZE, "CAVE_OPEN" to CaveMaps.OPEN,
            "MINES" to MinesMap.MAP, "SKY_PASS" to SkyPass.MAP, "HIDDEN_GROVE" to GroveMap.MAP)
        caves.forEach { (b, m) -> assertTrue(b, m.exitNode == ZoneOne.ARRIVAL[b]) }
        assertEquals(ZoneOne.LOCATIONS.toSet(), ZoneOne.ARRIVAL.keys)
        assertEquals(ZoneOne.LOCATIONS.toSet(), ZoneOne.NAMES.keys)
    }

    /**
     * zone1.json (gen_zone.py) opisuje souřadnice uzlů – musí sedět s CaveMap a s BiomeRegistry,
     * jinak by vchody a východy na mapě ležely jinde než ve hře.
     */
    @Test fun generatedLayoutMatchesGameNodes() {
        val json = file("src/main/assets/zone/zone1.json").readText()
        val caves: Map<String, CaveMap> = mapOf("FOREST" to ForestMap.MAP, "CAVE_MAZE" to CaveMaps.MAZE, "CAVE_OPEN" to CaveMaps.OPEN,
            "MINES" to MinesMap.MAP, "SKY_PASS" to SkyPass.MAP, "HIDDEN_GROVE" to GroveMap.MAP)
        val registry = file("src/main/java/cz/uhk/macroflow/pokemon/BiomeRegistry.kt").readText()
        fun outdoor(node: String): Pair<Float, Float> {
            val m = Regex("Waypoint\\(\"$node\",\\s*PointF\\(([0-9.]+)f,\\s*([0-9.]+)f\\)").find(registry)
                ?: error("uzel $node v BiomeRegistry")
            return m.groupValues[1].toFloat() to m.groupValues[2].toFloat()
        }
        val ends = Regex("\"(a|b)\": \"([A-Z_]+)\",\\s*\"\\1Node\": \"([a-z_]+)\",\\s*\"\\1Dir\": \"[nsew]\",\\s*\"\\1Pos\": \\[[^\\]]*\\],\\s*\"\\1Frac\": \\[\\s*([0-9.]+),\\s*([0-9.]+)\\s*\\]")
            .findAll(json).toList()
        assertEquals(16, ends.size)                                                  // 8 spojů × 2 konce
        for (e in ends) {
            val (_, biome, node, fx, fy) = e.destructured
            val expect = caves[biome]?.let { m ->
                val n = m.nodes.first { it.id == node }
                n.x.toFloat() / m.artW to n.y.toFloat() / m.artH
            } ?: outdoor(node)
            assertTrue("$biome/$node", kotlin.math.abs(expect.first - fx.toFloat()) < 0.001f &&
                kotlin.math.abs(expect.second - fy.toFloat()) < 0.001f)
        }
        ZoneOne.LOCATIONS.forEach { b ->
            assertTrue(b, json.contains("\"$b\": {") && file("src/main/assets/zone/$b.png").exists())
        }
    }

    @Test fun deathAndJumpAreFrontFacingOneShots() {
        assertEquals("death_s", HeroAnims.key("death", "ne"))
        assertEquals("jump_s", HeroAnims.key("jump", "w"))
        val d = listOf(100, 100, 200)
        assertEquals(0, HeroAnims.frameOnce(d, 50))
        assertEquals(2, HeroAnims.frameOnce(d, 250))
        assertEquals(2, HeroAnims.frameOnce(d, 5000))                               // zůstane ležet
    }
}

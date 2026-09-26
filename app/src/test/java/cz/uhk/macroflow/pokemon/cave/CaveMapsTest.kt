package cz.uhk.macroflow.pokemon.cave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaveMapsTest {

    @Test
    fun everyNodeIsReachableFromTheExit() {
        CaveMaps.ALL.forEach { cave ->
            assertEquals(cave.nodes.map { it.id }.toSet(), cave.reachableFrom(cave.exitNode))
        }
    }

    @Test
    fun edgesReferToExistingNodesAndIdsAreUnique() {
        CaveMaps.ALL.forEach { cave ->
            val ids = cave.nodes.map { it.id }
            assertEquals(ids.size, ids.toSet().size)
            cave.edges.forEach { (a, b) -> assertNotNull(cave.node(a)); assertNotNull(cave.node(b)) }
            assertTrue(cave.encounterNodes.all { it in ids })
            assertTrue(cave.crystalNode!! in ids && cave.exitNode in ids)
        }
    }

    @Test
    fun nodesLieInsideTheMapAndAltarAboveCrystalNode() {
        CaveMaps.ALL.forEach { cave ->
            cave.nodes.forEach { assertTrue(it.id, it.x in 0 until cave.artW && it.y in 0 until cave.artH) }
            val (_, baseY) = cave.crystalBase
            assertTrue(baseY - Crystals.H > 0)
        }
    }

    @Test
    fun crystalIsAtTheFarEndOfTheCave() {
        // Krystal je „na konci“: nejdelší cesta (počet kroků) od východu
        CaveMaps.ALL.forEach { cave ->
            val depth = HashMap<String, Int>().apply { put(cave.exitNode, 0) }
            val queue = ArrayDeque(listOf(cave.exitNode))
            while (queue.isNotEmpty()) {
                val n = queue.removeFirst()
                for (m in cave.neighbors(n)) if (m !in depth) { depth[m] = depth[n]!! + 1; queue.addLast(m) }
            }
            assertEquals(depth.values.max(), depth[cave.crystalNode!!])
        }
    }

    @Test
    fun oneBlueOneRedAndDistinctMountainEntrances() {
        assertEquals(setOf(CrystalColor.BLUE, CrystalColor.RED), CaveMaps.ALL.map { it.crystal }.toSet())
        assertEquals(setOf("cave", "mine"), CaveMaps.ALL.map { it.mountainNode }.toSet())
    }

    @Test
    fun everyNodeIsHorizontallyOnScreenWhereverTheCameraIs() {
        // Dřív byla mapa 2× širší než displej a body na stranách nešlo naklikat
        for ((vw, vh) in listOf(1080 to 2340, 720 to 1600, 1440 to 3120, 1080 to 1920)) {
            // Hvozd je od docs/adr/0036 schválně širší než displej (volná chůze, kamera do stran)
            CaveMaps.ALL.forEach { cave ->
                val s = MapCamera.pixelScale(cave.artW, cave.artH, vw, vh, cave.artPixelsAcross)
                val worldW = cave.artW * s
                cave.nodes.forEach { player ->
                    val off = MapCamera.offset(player.x * s.toFloat(), vw, worldW)
                    cave.nodes.forEach { n ->
                        val sx = n.x * s + off
                        assertTrue("${n.id} @ $vw: $sx", sx >= vw * 0.05f && sx <= vw * 0.95f)
                    }
                }
            }
        }
    }
}

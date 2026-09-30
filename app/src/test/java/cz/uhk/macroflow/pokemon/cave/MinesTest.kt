package cz.uhk.macroflow.pokemon.cave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinesTest {
    private val M = MinesMap

    @Test fun graphIsConnectedAndLeadsBackToTheOldMine() {
        val map = M.MAP
        assertEquals(map.nodes.map { it.id }.toSet(), map.reachableFrom(M.EXIT_NODE))
        assertEquals("CAVE_MAZE", map.parentBiome)
        // uzel ve Starém dole existuje a sedí se souřadnicemi v MinesMap
        val door = CaveMaps.MAZE.node(M.MAZE_NODE)!!
        assertEquals(M.MAZE_NODE_X, door.x); assertEquals(M.MAZE_NODE_Y, door.y)
        assertTrue("chodba_sever" in CaveMaps.MAZE.neighbors(M.MAZE_NODE))
        assertEquals(M.MAZE_DOOR, CaveMaps.MAZE.tapAreas[M.MAZE_NODE])
        for (n in M.ACTION_NODES) assertTrue(n, map.node(n) != null)
    }

    @Test fun lavaShapesMatchTheGenerator() {
        // vodopád ústí do jezírka, stoka navazuje na řeku, most řeku přerušuje
        assertTrue(M.isFloorLava(M.POOL_CX.toInt(), M.POOL_CY.toInt()))
        assertTrue(M.isFall((M.FALL_X0 + M.FALL_X1) / 2, M.FALL_Y1))
        assertTrue(M.isFloorLava(M.CHANNEL_X0, M.CHANNEL_Y1) && M.isFloorLava(M.CHANNEL_X0, M.RIVER_Y0))
        assertTrue(!M.isFloorLava((M.BRIDGE_X0 + M.BRIDGE_X1) / 2, M.RIVER_Y0))
        // uzly nestojí v lávě
        for (n in M.MAP.nodes) assertTrue(n.id, !M.isFloorLava(n.x, n.y) && !M.isFall(n.x, n.y))
    }

    @Test fun lavaFrameAnimatesOnlyOverLava() {
        val w = MinesArt.lavaW; val h = MinesArt.lavaH
        val a = IntArray(w * h).also { MinesArt.lavaFrame(0, it) }
        val b = IntArray(w * h).also { MinesArt.lavaFrame(500, it) }
        val x0 = M.LAVA_BOUNDS[0]; val y0 = M.LAVA_BOUNDS[1]
        var lavaPx = 0
        for (y in 0 until h) for (x in 0 until w) {
            val lava = M.isFall(x0 + x, y0 + y) || M.isFloorLava(x0 + x, y0 + y)
            if (lava) { lavaPx++; assertEquals(0xFF, a[y * w + x] ushr 24) }
        }
        assertTrue(lavaPx > 900)
        assertTrue("láva teče", (0 until w * h).count { a[it] != b[it] } > lavaPx / 4)
    }

    @Test fun threeFliesKeepMovingNearTheirSwarm() {
        for (bug in MinesArt.Bug.entries) {
            for (i in 0 until MinesArt.FLIES) {
                val p0 = MinesArt.flyOffset(bug, i, 0); val p1 = MinesArt.flyOffset(bug, i, 300)
                assertTrue(p0 != p1)
                for (t in 0L..20_000L step 250) {
                    val (x, y) = MinesArt.flyOffset(bug, i, t)
                    assertTrue("$bug/$i", kotlin.math.abs(x) < 14f && kotlin.math.abs(y) < 10f)
                }
            }
            val up = MinesArt.sprite(bug, true, true); val down = MinesArt.sprite(bug, false, true)
            assertEquals(MinesArt.SPRITE_W * MinesArt.SPRITE_H, up.size)
            assertTrue(!up.contentEquals(down))
        }
    }
}

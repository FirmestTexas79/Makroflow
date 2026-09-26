package cz.uhk.macroflow.pokemon.arena

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArenaTest {

    @Test fun biomesMapToThemes() {
        assertEquals(ArenaTheme.WATER, ArenaTheme.fromBiome("LAKE"))
        assertEquals(ArenaTheme.WATER, ArenaTheme.fromBiome("WATER"))
        assertEquals(ArenaTheme.CAVE_MAZE, ArenaTheme.fromBiome("CAVE_MAZE"))
        assertEquals(ArenaTheme.MEADOW, ArenaTheme.fromBiome(null))
    }

    @Test fun cameraPutsEnemyBehindPlayer() {
        val e = Arenas.enemyFoot; val p = Arenas.playerFoot
        assertTrue("soupeř musí stát dál než hráč", e.z - p.z > 4f)
        val proj = Arenas.camera.project(e, Arenas.W, Arenas.H)!!
        assertEquals(Arenas.ENEMY_X, proj.first, 0.5f)
        assertEquals(Arenas.ENEMY_Y, proj.second, 0.5f)
    }

    /** Mezi kamerou a Makromony nesmí nic stát (kromě podstavce a země). */
    @Test fun nothingBlocksTheView() {
        val cam = Arenas.camera.pos
        val targets = listOf(Arenas.enemyFoot + V3(0f, 0.9f, 0f), Arenas.playerFoot + V3(0f, 0.9f, 0f))
        for (t in ArenaTheme.entries) {
            val scene = Arenas.scene(t, 3)
            for (target in targets) for (i in 1 until 60) {
                val q = cam + (target - cam) * (i / 60f)
                val hit = scene.boxes.firstOrNull { b ->
                    q.x > b.x0 && q.x < b.x1 && q.y > b.y0 && q.y < b.y1 && q.z > b.z0 && q.z < b.z1
                }
                assertTrue("$t: výhled na $target zakrývá kvádr ${hit?.let { "${it.x0},${it.y0},${it.z0}–${it.x1},${it.y1},${it.z1}" }}", hit == null)
            }
        }
    }

    @Test fun everyThemeRendersAFullPicture() {
        for (t in ArenaTheme.entries) {
            val px = Arenas.render(t, 1)
            assertEquals(Arenas.W * Arenas.H, px.size)
            assertTrue(t.name, px.all { (it ushr 24) == 0xFF })
            // pod nohama soupeře je podstavec, ne obloha
            val foot = px[(Arenas.ENEMY_Y.toInt() + 2) * Arenas.W + Arenas.ENEMY_X.toInt()]
            assertTrue(t.name, px.toSet().size > 200)
            assertTrue(t.name, foot != px[0] || t == ArenaTheme.CAVE_MAZE)
        }
    }
}

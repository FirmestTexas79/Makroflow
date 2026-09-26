package cz.uhk.macroflow.pokemon.walk

import cz.uhk.macroflow.pokemon.cave.CaveMap
import cz.uhk.macroflow.pokemon.cave.CaveMaps
import cz.uhk.macroflow.pokemon.cave.ForestMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WalkGridTest {

    /** 10×10 px buňky, zeď uprostřed s mezerou dole. */
    private val wall = WalkGrid.parse(
        """
        # testovací mapa
        60 50 10
        ......
        ..#...
        ..#...
        ..#...
        ......
        """.trimIndent()
    )

    @Test fun parsesHeaderAndCells() {
        assertEquals(6, wall.cols)
        assertEquals(5, wall.rows)
        assertTrue(wall.isWalkableCell(0, 0))
        assertTrue(!wall.isWalkableCell(2, 1))
        assertTrue(!wall.isWalkable(Pt(25f, 15f)))
        assertTrue(!wall.isWalkable(Pt(-1f, 5f)))
        assertTrue(!wall.isWalkable(Pt(5f, 55f)))
    }

    @Test fun straightLineWhenNothingInTheWay() {
        val path = wall.findPath(Pt(5f, 5f), Pt(55f, 5f))!!
        assertEquals(listOf(Pt(55f, 5f)), path)
    }

    @Test fun goesAroundTheWall() {
        val from = Pt(5f, 15f); val to = Pt(55f, 15f)
        val path = wall.findPath(from, to)!!
        assertEquals(to, path.last())
        // Každý úsek vede jen po průchozí ploše
        var prev = from
        for (p in path) { assertTrue("úsek $prev → $p", wall.lineWalkable(prev, p)); prev = p }
        // Obchází zeď – buď horem (y < 10), nebo spodem (y > 40)
        assertTrue(path.any { it.y < 10f || it.y > 40f })
        assertTrue(path.size in 2..4)
    }

    @Test fun targetInsideWallSnapsToNearestFreeCell() {
        val snapped = wall.snap(Pt(25f, 25f))!!
        assertTrue(wall.isWalkable(snapped))
        assertTrue(snapped.dist(Pt(25f, 25f)) <= 10.01f)
        val path = wall.findPath(Pt(5f, 25f), Pt(25f, 25f))!!
        assertTrue(wall.isWalkable(path.last()))
    }

    @Test fun alreadyThereGivesEmptyPath() {
        assertEquals(emptyList<Pt>(), wall.findPath(Pt(5f, 5f), Pt(5f, 5f)))
    }

    @Test fun unreachableIslandGivesNull() {
        val g = WalkGrid.parse("30 10 10\n.#.")
        assertNull(g.findPath(Pt(5f, 5f), Pt(25f, 5f)))
    }

    @Test fun noCornerCutting() {
        // Úhlopříčka mezi dvěma zdmi by prošla rohem – nesmí
        val g = WalkGrid.parse("20 20 10\n.#\n#.")
        assertNull(g.findPath(Pt(5f, 5f), Pt(15f, 15f)))
    }

    @Test fun geometryCenterCropRoundTrip() {
        // Obrázek 384×624 na obrazovce 1280×2856: výška rozhoduje, strany se oříznou
        val geo = MapGeometry(384, 624, 1280, 2856)
        assertEquals(2856f / 624f, geo.scale, 0.001f)
        assertTrue(geo.offsetX < 0f)
        assertEquals(0f, geo.offsetY, 0.001f)
        val p = Pt(100f, 200f)
        val back = geo.toImage(geo.toWorld(p))
        assertEquals(p.x, back.x, 0.01f); assertEquals(p.y, back.y, 0.01f)
        // Jeskyně: svět = obrázek × celé číslo → žádný posun
        val cave = MapGeometry(150, 440, 150 * 8, 440 * 8)
        assertEquals(8f, cave.scale, 0.0001f)
        assertEquals(0f, cave.offsetX, 0.0001f); assertEquals(0f, cave.offsetY, 0.0001f)
    }

    @Test fun directionFollowsDominantAxis() {
        assertEquals(WalkDirection.RIGHT, WalkDirection.of(10f, 2f))
        assertEquals(WalkDirection.LEFT, WalkDirection.of(-10f, 2f))
        assertEquals(WalkDirection.DOWN, WalkDirection.of(1f, 10f))
        assertEquals(WalkDirection.UP, WalkDirection.of(1f, -10f))
        assertEquals(WalkDirection.LEFT, WalkDirection.of(0f, 0f, WalkDirection.LEFT))
    }

    // ── Skutečné mapy z assets ──

    private fun asset(name: String): WalkGrid {
        val rel = "src/main/assets/walk/$name.txt"
        val f = listOf(File(rel), File("app/$rel")).first { it.exists() }
        return WalkGrid.parse(f.readText())
    }

    @Test fun allMasksParseAndHaveWalkableArea() {
        for (n in listOf("town", "meadow", "mountains", "cave_open", "cave_maze", "forest")) {
            val g = asset(n)
            val share = g.walkableCount.toFloat() / (g.cols * g.rows)
            assertTrue("$n: $share", share in 0.1f..0.8f)
        }
    }

    /** Každý uzel jeskyní a lesa leží na průchozí ploše (nebo těsně u ní) a ze vchodu se k němu dá dojít. */
    @Test fun caveAndForestNodesAreReachable() {
        val maps = mapOf<String, CaveMap>("cave_open" to CaveMaps.OPEN, "cave_maze" to CaveMaps.MAZE, "forest" to ForestMap.MAP)
        for ((name, map) in maps) {
            val g = asset(name)
            assertEquals(map.artW, g.imgW); assertEquals(map.artH, g.imgH)
            val exit = map.nodes.first { it.id == map.exitNode }.let { Pt(it.x.toFloat(), it.y.toFloat()) }
            for (n in map.nodes) {
                val p = Pt(n.x.toFloat(), n.y.toFloat())
                val snapped = g.snap(p)
                assertTrue("$name/${n.id}", snapped != null)
                assertTrue("$name/${n.id} daleko od průchozí plochy", snapped!!.dist(p) <= g.cell * 2f)
                assertTrue("$name/${n.id} nedosažitelný", g.findPath(exit, p) != null)
            }
        }
    }

    @Test fun smoothedPathsStayOnWalkableGround() {
        val g = asset("cave_maze")
        val map = CaveMaps.MAZE
        val a = map.nodes.first().let { Pt(it.x.toFloat(), it.y.toFloat()) }
        for (n in map.nodes) {
            val b = Pt(n.x.toFloat(), n.y.toFloat())
            val path = g.findPath(a, b) ?: continue
            var prev = g.snap(a)!!
            for (p in path) { assertTrue("${n.id}: $prev → $p", g.lineWalkable(prev, p) || prev.dist(p) <= g.cell); prev = p }
        }
    }
}

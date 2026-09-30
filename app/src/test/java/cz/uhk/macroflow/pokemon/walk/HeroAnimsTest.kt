package cz.uhk.macroflow.pokemon.walk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Postava ze Sunnyside World (docs/adr/0051). */
class HeroAnimsTest {

    private fun asset(name: String): File {
        val rel = "src/main/assets/hero/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }
    }

    @Test fun eightDirectionsFromMovement() {
        assertEquals("e", HeroAnims.dir8(10f, 0f))
        assertEquals("s", HeroAnims.dir8(0f, 10f))
        assertEquals("n", HeroAnims.dir8(0f, -10f))
        assertEquals("w", HeroAnims.dir8(-10f, 0f))
        assertEquals("se", HeroAnims.dir8(7f, 7f))
        assertEquals("nw", HeroAnims.dir8(-7f, -7f))
        assertEquals("ne", HeroAnims.dir8(7f, -7f))
        assertEquals("sw", HeroAnims.dir8(-7f, 7f))
        assertEquals("nw", HeroAnims.dir8(0.1f, 0.2f, "nw"))       // skoro stojí → beze změny
        assertEquals(listOf("s", "n", "w", "e"), (0..3).map { HeroAnims.fromFacing(it) })
    }

    @Test fun workHasTwoSidesAndSkillsMapToActions() {
        assertEquals("axe_e", HeroAnims.key("axe", "ne"))
        assertEquals("mining_w", HeroAnims.key("mining", "sw"))
        assertEquals("walk_ne", HeroAnims.key("walk", "ne"))
        assertEquals("axe", HeroAnims.actionFor("logging"))
        assertEquals("mining", HeroAnims.actionFor("mining"))
        assertEquals("casting", HeroAnims.actionFor("bugcatching"))
    }

    @Test fun frameTimingLoops() {
        val d = listOf(100, 100, 200)
        assertEquals(0, HeroAnims.frameAt(d, 0))
        assertEquals(1, HeroAnims.frameAt(d, 150))
        assertEquals(2, HeroAnims.frameAt(d, 399))
        assertEquals(0, HeroAnims.frameAt(d, 400))
        assertEquals(0, HeroAnims.frameAt(emptyList(), 50))
    }

    /** Každý pás, který hra potřebuje, existuje a má tolik snímků, kolik říká hero.json. */
    @Test fun assetsMatchTheSpec() {
        val spec = HeroAnims.parseSpec(asset("hero.json").readText())
        assertEquals(64, spec.frameW); assertEquals(40, spec.frameH)
        val needed = HeroAnims.MOVE.flatMap { a -> HeroAnims.DIRS8.map { "${a}_$it" } } +
            listOf("axe", "mining", "casting").flatMap { listOf("${it}_e", "${it}_w") } +
            HeroAnims.ONCE.map { "${it}_s" }
        for (k in needed) {
            val durs = spec.anims[k]
            assertTrue(k, durs != null && durs.isNotEmpty() && durs.all { it > 0 })
            val img = javax.imageio.ImageIO.read(asset("$k.png"))
            assertTrue(k, spec.frameW * durs!!.size == img.width && spec.frameH == img.height)
        }
    }
}

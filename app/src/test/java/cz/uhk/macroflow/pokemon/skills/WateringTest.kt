package cz.uhk.macroflow.pokemon.skills

import cz.uhk.macroflow.pokemon.walk.HeroAnims
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Zalévání záhonů a nové animace postavy (docs/adr/0054). */
class WateringTest {

    private val berry = Berry.BLACK                                    // roste 4 h
    private val t0 = Garden.EPOCH + 10_000

    @Test fun waterOncePerQuarterOfGrowth() {
        val plot = Garden.Plot(berry, t0)
        val grow = Garden.growSeconds(berry, 0.0)
        val every = Garden.waterInterval(berry, 0.0)
        assertEquals(grow / 4, every)
        assertEquals(every, Garden.waterIn(plot, t0, t0, 0.0))           // čerstvě zasazeno
        assertTrue(!Garden.canWater(plot, t0, t0 + every - 1, 0.0))
        assertTrue(Garden.canWater(plot, t0, t0 + every, 0.0))
        // starý záhon bez záznamu zalití se počítá od zasazení
        assertTrue(Garden.canWater(plot, 0L, t0 + every, 0.0))
        assertNull(Garden.waterIn(Garden.Plot(null, 0), 0L, t0, 0.0))
        assertNull(Garden.waterIn(plot, 0L, t0 + grow, 0.0))              // hotové se nezalévá
    }

    @Test fun wateringMovesGrowthForward() {
        val plot = Garden.Plot(berry, t0)
        val now = t0 + Garden.waterInterval(berry, 0.0)
        val before = Garden.remaining(plot, now, 0.0)
        val after = Garden.remaining(Garden.watered(plot, 0.0), now, 0.0)
        assertEquals(Garden.waterBoost(berry, 0.0), before - after)
        assertEquals((Garden.growSeconds(berry, 0.0) * 0.15).toLong(), before - after)
        // po zalití se odpočítává znovu od zalití
        assertTrue(!Garden.canWater(Garden.watered(plot, 0.0), now, now + 60, 0.0))
    }

    @Test fun wateredPlotStillEncodes() {
        val w = Garden.watered(Garden.Plot(berry, t0), 0.0)
        val back = Garden.decode(Garden.encode(berry, w.plantedAt))
        assertEquals(w.plantedAt, back.plantedAt)
        assertEquals(t0, Garden.decodeTime(Garden.encodeTime(t0)))
        assertEquals(0L, Garden.decodeTime(0))
    }

    private fun asset(name: String): File {
        val rel = "src/main/assets/hero/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }
    }

    @Test fun fishingFramesAreTallerAndHammerIsShort() {
        val spec = HeroAnims.parseSpec(asset("hero.json").readText())
        for (k in listOf("reeling_e", "reeling_w", "caught_e", "caught_w")) {
            assertTrue(k, spec.height(k) == 64)
            val img = javax.imageio.ImageIO.read(asset("$k.png"))
            assertTrue(k, img.height == 64 && img.width == spec.frameW * spec.anims.getValue(k).size)
        }
        assertEquals(40, spec.height("hammer_e"))
        assertEquals(7, spec.anims.getValue("hammer_e").size)
        assertTrue(spec.anims.containsKey("watering_w"))
        assertEquals("caught_w", HeroAnims.key("caught", "w"))
    }
}

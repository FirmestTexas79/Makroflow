package cz.uhk.macroflow.pokemon.skills

import cz.uhk.macroflow.pokemon.balls.Makroball
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemInfoTest {
    @Test fun everyResourceAndBallHasASource() {
        Resource.entries.forEach { assertTrue(it.itemId, ItemInfo.sources(it.itemId).isNotEmpty()) }
        Makroball.entries.forEach { assertTrue(it.id, ItemInfo.sources(it.id).isNotEmpty()) }
        assertTrue(ItemInfo.sources("nic").isEmpty())
    }

    @Test fun seedRatesMatchDropTable() {
        assertEquals("1,25 %", ItemInfo.sources("seed_black")[0].rate)
        assertEquals("5 %", ItemInfo.sources("seed_blue")[0].rate)
        assertEquals("18,75 %", ItemInfo.sources("seed_green")[0].rate)
        assertTrue(ItemInfo.sources("ore_gold")[0].where.startsWith("Mechová"))
        assertTrue(ItemInfo.sources("log_oak")[0].rate.contains("3 min"))
    }
}

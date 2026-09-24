package cz.uhk.macroflow.pokemon.legend

import cz.uhk.macroflow.pokemon.cave.CrystalColor
import cz.uhk.macroflow.pokemon.cave.CrystalColor.BLUE
import cz.uhk.macroflow.pokemon.cave.CrystalColor.RED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegendProgressTest {

    @Test
    fun crystalIsGuardedUntilTheBossFalls() {
        val start = LegendProgress()
        assertEquals(LegendProgress.Altar.GUARDED, start.altar(BLUE))
        val won = start.copy(bossesDefeated = setOf(BLUE))
        assertEquals(LegendProgress.Altar.CRYSTAL_READY, won.altar(BLUE))
        assertEquals(LegendProgress.Altar.GUARDED, won.altar(RED))
        assertEquals(LegendProgress.Altar.EMPTY, won.copy(crystalsTaken = setOf(BLUE)).altar(BLUE))
    }

    @Test
    fun shrineWalksThroughTheWholeStory() {
        var p = LegendProgress()
        assertEquals(LegendProgress.Shrine.NeedCrystals(setOf(BLUE, RED)), p.shrine)
        p = p.copy(crystalsInBag = setOf(RED))
        assertEquals(LegendProgress.Shrine.NeedCrystals(setOf(BLUE)), p.shrine)
        p = p.copy(crystalsInBag = setOf(RED, BLUE))
        assertEquals(LegendProgress.Shrine.ReadyToPlace, p.shrine)
        // po vložení krystaly z inventáře zmizí, ale svatyně si je pamatuje
        p = p.copy(crystalsInBag = emptySet(), crystalsPlaced = true)
        assertEquals(LegendProgress.Shrine.LegendAwaits, p.shrine)
        assertEquals(setOf(BLUE, RED), p.socketsFilled)
        p = p.copy(legendFaced = true)
        assertEquals(LegendProgress.Shrine.GateOpen, p.shrine)
    }

    @Test
    fun loadReadsFlagsAndInventory() {
        val flags = setOf(LegendProgress.bossKey(BLUE), LegendProgress.takenKey(BLUE), LegendProgress.PLACED_KEY)
        val p = LegendProgress.load({ it in flags }, { if (it == BLUE.itemId) 1 else 0 })
        assertEquals(setOf(BLUE), p.bossesDefeated)
        assertEquals(setOf(BLUE), p.crystalsTaken)
        assertEquals(setOf(BLUE), p.crystalsInBag)
        assertTrue(p.crystalsPlaced)
        assertEquals(false, p.legendFaced)
    }

    @Test
    fun keysAndItemsAreDistinct() {
        val keys = CrystalColor.entries.flatMap { listOf(LegendProgress.bossKey(it), LegendProgress.takenKey(it), it.itemId) } +
            listOf(LegendProgress.PLACED_KEY, LegendProgress.LEGEND_KEY)
        assertEquals(keys.size, keys.toSet().size)
        CrystalColor.entries.forEach { assertEquals(it, CrystalColor.fromItem(it.itemId)) }
    }

    @Test
    fun openGateFitsTheArchAndSocketsSitOnTheShrine() {
        val w = PeakShrine.GATE_RIGHT - PeakShrine.GATE_LEFT + 1
        val h = PeakShrine.GATE_BOTTOM - PeakShrine.GATE_TOP + 1
        val px = PeakShrine.openGatePixels()
        assertEquals(w * h, px.size)
        assertEquals(0, px[0]); assertEquals(0, px[w - 1])             // oblé rohy nahoře
        assertTrue((0 until w).all { px[(h - 1) * w + it] != 0 })        // dole plná šířka
        for (y in 0 until h) for (x in 0 until w)                         // tvar souměrný
            assertEquals(px[y * w + x] != 0, px[y * w + (w - 1 - x)] != 0)
        PeakShrine.SOCKETS.values.forEach { assertTrue(it in PeakShrine.GATE_LEFT..PeakShrine.GATE_RIGHT) }
        assertTrue(PeakShrine.SOCKET_BOTTOM > PeakShrine.GATE_BOTTOM)
    }

    @Test
    fun crystalPickedUpByOldVersionReturnsToTheAltar() {
        // Starší verze zapsala jen „sebráno“ – bez strážce a bez předmětu v inventáři
        val p = LegendProgress.load({ it == LegendProgress.takenKey(RED) }, { 0 })
        assertEquals(LegendProgress.Altar.GUARDED, p.altar(RED))
        assertEquals(emptySet<CrystalColor>(), p.crystalsTaken)
    }
}

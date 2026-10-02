package cz.uhk.macroflow.pokemon.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spisy, sny, Mydrus zapomíná a probuzení (docs/adr/0057). */
class KustodiatTest {

    @Test fun dossierRevealsWithInsightButNeverTheTruthInActOne() {
        val raw = Dossiers.text("012", "Spirra", 3)
        val low = Insight.render(raw, 2)
        val high = Insight.render(raw, 12)
        assertTrue(low.contains("Odchyceno subjektem: 3×"))
        assertTrue(!low.contains("Pokus č. 1 ztracen"))
        assertTrue(high.contains("Pokus č. 1 ztracen"))
        assertTrue(!high.contains("lumenský"))                       // {99|…} zůstává černé celý akt I
        assertTrue(!Dossiers.visible(1) && Dossiers.visible(2))
        // každý druh z Makrodexu má vlastní spis
        (1..31).map { it.toString().padStart(3, '0') }.forEach { assertTrue(it, !Dossiers.text(it, "x", 1).contains("Bez záznamu")) }
    }

    @Test fun dreamsComeInTheMorningAfterSleepInOrder() {
        val h8 = 8L * 3600
        assertEquals(1, Dreams.next(emptySet(), 3, 7, h8, false, 0)?.id)
        assertNull(Dreams.next(emptySet(), 2, 7, h8, false, 0))                    // málo Vhledu
        assertNull(Dreams.next(emptySet(), 3, 15, h8, false, 0))                   // odpoledne
        assertNull(Dreams.next(emptySet(), 3, 7, 3600, false, 0))                  // nespal
        assertNull(Dreams.next(emptySet(), 3, 7, h8, true, 0))                     // dnes už
        assertNull(Dreams.next(emptySet(), 3, 7, h8, false, Dreams.CHANCE))        // nepadla náhoda
        assertEquals(2, Dreams.next(setOf("dream_1"), 3, 7, h8, false, 0)?.id)
        assertNull(Dreams.next(Dreams.ALL.map { it.key }.toSet(), 9, 7, h8, false, 0))
        assertTrue(StoryProgress.isStoryKey("dream_3"))
    }

    @Test fun mydrusForgetsFasterAndFaster() {
        assertEquals(listOf(2, 4, 3, 2, 2), (0..4).map { MydrusMemory.interval(it) })
        assertTrue(!MydrusMemory.forgets(false, 100, 90, 0))                       // hniloba ještě žije
        assertTrue(!MydrusMemory.forgets(true, 101, 100, 0))
        assertTrue(MydrusMemory.forgets(true, 102, 100, 0))
        assertTrue(!MydrusMemory.forgets(true, 103, 100, 1))
        assertTrue(MydrusMemory.forgets(true, 104, 100, 1))
        assertTrue(MydrusMemory.stranger(0).startsWith("Pst… nelekej se. Jsem Mydrus"))
        MydrusMemory.PLACES.forEach { assertTrue(MydrusMemory.fragment(it).isNotBlank()) }
    }

    @Test fun gudwinCounts() {
        assertTrue(Wakeups.gudwin(1, 0, 0).contains("Našel jsem tě"))
        assertTrue(Wakeups.gudwin(4, 0, 99).contains("4."))
        assertTrue(Wakeups.gudwin(4, 6, 0).contains("Dvě stě dvanáct"))
        assertTrue(Wakeups.gudwin(4, 3, 99).contains("Počítám je všechny"))
    }
}

package cz.uhk.macroflow.pokemon.story

import cz.uhk.macroflow.pokemon.cave.ForestMap
import cz.uhk.macroflow.pokemon.cave.GroveMap
import cz.uhk.macroflow.pokemon.cave.SkyPass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightTest {

    @Test fun insightGrowsWithSecretsAndPages() {
        assertEquals(0, Insight.level(emptySet()))
        assertEquals(1, Insight.level(setOf("legend_faced", "LAST_BIOME")))
        val all = Insight.FLAG_WEIGHTS.keys + Insight.PAGES.map { it.key }
        assertEquals(Insight.FLAG_WEIGHTS.values.sum() + Insight.PAGES.size, Insight.level(all))
    }

    @Test fun pagesSyncAsStoryProgress() {
        Insight.PAGES.forEach { assertTrue(it.key, StoryProgress.isStoryKey(it.key)) }
        assertEquals(Insight.PAGES.size, Insight.PAGES.map { it.id }.toSet().size)
    }

    @Test fun redactionRevealsWithInsight() {
        val t = "A {2|tajné} B {4|velmi tajné}"
        assertEquals("A █████ B █████ █████", Insight.render(t, 0))
        assertEquals("A tajné B █████ █████", Insight.render(t, 3))
        assertEquals("A tajné B velmi tajné", Insight.render(t, 4))
        assertEquals(2, Insight.hiddenCount(t, 1)); assertEquals(0, Insight.hiddenCount(t, 9))
        // žádný list není celý odkrytý hned na začátku a všechny se dají odkrýt úplně
        Insight.PAGES.forEach {
            assertTrue(it.text, Insight.hiddenCount(it.text, 1) > 0)
            assertEquals(0, Insight.hiddenCount(it.text, 5))
            assertTrue(!Insight.render(it.text, 99).contains("{"))
        }
    }

    @Test fun pagesLieOnRealNodesAndNeedTheirMoment() {
        val maps = mapOf("FOREST" to ForestMap.MAP, "SKY_PASS" to SkyPass.MAP, "HIDDEN_GROVE" to GroveMap.MAP)
        Insight.PAGES.forEach { p -> maps[p.biome]?.let { assertTrue("${p.id}", it.node(p.node) != null) } }
        // list u dubu je tam až po vyhnání Soulorda, a jen jednou
        assertNull(Insight.pageAt("FOREST", "stary_dub", emptySet()))
        val oak = Insight.pageAt("FOREST", "stary_dub", setOf(ForestHeart.ROT_DEFEATED_KEY))!!
        assertNull(Insight.pageAt("FOREST", "stary_dub", setOf(ForestHeart.ROT_DEFEATED_KEY, oak.key)))
        assertEquals(1, Insight.pageAt("MOUNTAINS", "camp", emptySet())!!.id)
        assertEquals(listOf(oak), Insight.foundPages(setOf(oak.key)))
    }

    @Test fun cracksAreRareAndOnlyForTheCursed() {
        assertNull(Insight.crack("town_intro_oliver", 1, 0))          // málo Vhledu
        assertTrue(Insight.crack("town_intro_oliver", 2, 0) != null)
        assertNull(Insight.crack("town_intro_oliver", 9, 50))         // většinou nic
        assertNull(Insight.crack("forest_heart", 9, 0))               // Mydrus není prokletý
        val hits = (0 until 100).count { Insight.crack("mountains_macro_king", 5, it) != null }
        assertTrue("$hits", hits in 5..15)
        assertTrue(!Insight.stationFlicker(3, 0))
        assertEquals(100, (0 until 600).count { Insight.stationFlicker(4, it) })
    }

    @Test fun whispersAndReceiptsStaySmall() {
        val whispered = (1..31).map { it.toString().padStart(3, '0') }.count { Insight.dexWhisper(it) != null }
        assertTrue("$whispered", whispered in 1..4)                   // nanejvýš zhruba každý desátý
        assertEquals("Účtenka S-7/4128", Insight.receipt(4128))
        assertEquals(Insight.ICON * Insight.ICON, Insight.pageIcon().size)
    }
}

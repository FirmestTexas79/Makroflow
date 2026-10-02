package cz.uhk.macroflow.pokemon.bag

import cz.uhk.macroflow.pokemon.balls.Makroball
import cz.uhk.macroflow.pokemon.cave.CrystalColor
import cz.uhk.macroflow.pokemon.skills.Gear
import cz.uhk.macroflow.pokemon.skills.Resource
import cz.uhk.macroflow.pokemon.status.MedItem
import cz.uhk.macroflow.pokemon.story.Insight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Batoh v deníku (docs/adr/0060). */
class BagItemsTest {

    @Test fun resourcesAndInternalStateAreNotInTheBag() {
        assertNull(BagItems.of(Resource.ORE_COPPER.itemId))
        assertNull(BagItems.of("skill_xp_mining"))
        assertNull(BagItems.of("skill_node_team_2"))
        assertNull(BagItems.of("garden_0"))
    }

    @Test fun itemsLandInTheirPockets() {
        val ball = Makroball.entries.first()
        assertEquals(BagItems.Pocket.BALLS, BagItems.of(ball.id)!!.pocket)
        assertEquals(BagItems.Pocket.MEDS, BagItems.of(MedItem.entries.first().id)!!.pocket)
        assertEquals(BagItems.Pocket.GEAR, BagItems.of(Gear.OLD_PICKAXE.id)!!.pocket)
        assertEquals(BagItems.Pocket.KEY, BagItems.of(CrystalColor.BLUE.itemId)!!.pocket)
        assertEquals(BagItems.Action.READ, BagItems.of(Insight.PAGES_ITEM)!!.action)
        assertEquals(BagItems.Action.USE, BagItems.of(BagItems.LURE_LAMP)!!.action)
        // každá ikona je čtverec size × size
        listOf(ball.id, Gear.OLD_PICKAXE.id, CrystalColor.RED.itemId, Insight.PAGES_ITEM, BagItems.LURE_LAMP, "neco_neznameho").forEach {
            val i = BagItems.of(it)!!
            assertEquals(it, i.size * i.size, i.pixels.size)
        }
    }

    @Test fun pocketsKeepOrderAndSkipEmpty() {
        val p = BagItems.pockets(mapOf(
            CrystalColor.BLUE.itemId to 1, Makroball.entries.first().id to 5, Resource.LOG_OAK.itemId to 9,
            MedItem.entries.first().id to 0, "skill_xp_logging" to 300))
        assertEquals(listOf(BagItems.Pocket.BALLS, BagItems.Pocket.KEY), p.keys.toList())
        assertEquals(5, p.getValue(BagItems.Pocket.BALLS).single().second)
        assertTrue(p.values.flatten().none { it.first.id == Resource.LOG_OAK.itemId })
    }
}

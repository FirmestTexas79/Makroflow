package cz.uhk.macroflow.nutrition

import cz.uhk.macroflow.data.SnackEntity
import cz.uhk.macroflow.nutrition.SnackCatalog.Group
import cz.uhk.macroflow.nutrition.SnackCatalog.Row
import cz.uhk.macroflow.nutrition.SnackCatalog.Timing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnackCatalogTest {

    private var nextId = 1
    private fun snack(name: String, weight: String, p: Float, s: Float, t: Float, pre: Boolean = false, kj: Float = 0f, fiber: Float = 0f) =
        SnackEntity(id = nextId++, name = name, weight = weight, p = p, s = s, t = t, isPre = pre, energyKj = kj, fiber = fiber)

    @Test
    fun portionParsing() {
        assertEquals(150f, SnackCatalog.portionGrams("150g"), 1e-4f)
        assertEquals(250f, SnackCatalog.portionGrams("250ml"), 1e-4f)
        assertEquals(1500f, SnackCatalog.portionGrams("1,5 kg"), 1e-4f)
        assertEquals(12.5f, SnackCatalog.portionGrams("12.5 g"), 1e-4f)
        assertEquals(100f, SnackCatalog.portionGrams("porce"), 1e-4f)
        assertEquals("ml", SnackCatalog.unit("250ml"))
        assertEquals("g", SnackCatalog.unit("60g"))
    }

    @Test
    fun groupsFollowEnergyNotGrams() {
        // Ořechy: víc sacharidů než bílkovin v gramech, ale energie hlavně z tuku
        assertEquals(Group.FAT, SnackCatalog.groupOf(snack("Vlašské ořechy", "100g", 18.4f, 14.6f, 60f)))
        assertEquals(Group.FAT, SnackCatalog.groupOf(snack("Avokádo", "100g", 1.9f, 0.4f, 23.5f)))
        assertEquals(Group.PROTEIN, SnackCatalog.groupOf(snack("Kuřecí prsa", "100g", 23.3f, 0.4f, 0.9f)))
        assertEquals(Group.CARBS, SnackCatalog.groupOf(snack("Banán", "100g", 0.3f, 23f, 0.3f)))
        // Lehké: brokolice má víc energie z bílkovin, ale jen ~33 kcal/100 g
        assertEquals(Group.LIGHT, SnackCatalog.groupOf(snack("Brokolice", "100g", 4.4f, 2.9f, 0.9f, kj = 140f)))
        // Tvaroh s ořechy: bílkoviny a tuk téměř vyrovnané → bílkoviny
        assertEquals(Group.PROTEIN, SnackCatalog.groupOf(snack("Tvaroh s ořechy", "200g", 24f, 8f, 10f)))
    }

    @Test
    fun energySplitSumsToOne() {
        val (p, s, t) = SnackCatalog.energySplit(10f, 40f, 8f)
        assertEquals(1f, p + s + t, 1e-5f)
        assertTrue(s > p && s > t)
    }

    @Test
    fun searchIgnoresDiacriticsAndWordOrder() {
        val chicken = snack("Kuřecí prsa (raw)", "100g", 23f, 0f, 1f)
        assertTrue(SnackCatalog.matches(chicken, "kure"))
        assertTrue(SnackCatalog.matches(chicken, "PRSA kuř"))
        assertFalse(SnackCatalog.matches(chicken, "krůtí"))
        assertTrue(SnackCatalog.matches(chicken, "  "))
    }

    @Test
    fun rowsHaveFavouritesThenGroups() {
        val a = snack("Skyr", "140g", 16f, 5f, 0f, pre = true)
        val b = snack("Banán", "100g", 0.3f, 23f, 0.3f, pre = true)
        val c = snack("Mandle", "30g", 5f, 6f, 16f)
        val d = snack("Tuňák", "130g", 28f, 0f, 1f)
        val rows = SnackCatalog.rows(listOf(a, b, c, d), "", Timing.ALL, mapOf("Banán" to 3, "Tuňák" to 7))
        val headers = rows.filterIsInstance<Row.Header>().map { it.group }
        assertEquals(listOf(Group.FAVOURITES, Group.PROTEIN, Group.CARBS, Group.FAT).filter { g -> g != Group.CARBS }, headers)
        // Oblíbené podle počtu použití, v dalších skupinách se neopakují
        val items = rows.filterIsInstance<Row.Item>()
        assertEquals(listOf("Tuňák", "Banán"), items.take(2).map { it.snack.name })
        assertEquals(4, items.size)
    }

    @Test
    fun searchHidesFavouritesAndTimingFilters() {
        val a = snack("Skyr", "140g", 16f, 5f, 0f, pre = true)
        val d = snack("Tuňák", "130g", 28f, 0f, 1f)
        val rows = SnackCatalog.rows(listOf(a, d), "tun", Timing.ALL, mapOf("Tuňák" to 7))
        assertEquals(Group.PROTEIN, (rows.first() as Row.Header).group)
        val pre = SnackCatalog.rows(listOf(a, d), "", Timing.PRE, emptyMap())
        assertEquals(listOf("Skyr"), pre.filterIsInstance<Row.Item>().map { it.snack.name })
    }

    @Test
    fun duplicatesShownOnce() {
        val rows = SnackCatalog.rows(listOf(snack("Avokádo", "100g", 1.9f, 0.4f, 23.5f), snack("Avokádo", "100g", 1.9f, 0.4f, 23.5f)), "", Timing.ALL, emptyMap())
        assertEquals(1, rows.filterIsInstance<Row.Item>().size)
    }

    @Test
    fun portionPresetsAreDistinctAndSorted() {
        val p = SnackCatalog.portionPresets(snack("Rýže", "100g", 3f, 40f, 0.5f)).map { it.grams }
        assertEquals(listOf(50f, 100f, 200f), p)
        val q = SnackCatalog.portionPresets(snack("Mléko", "250ml", 8f, 12f, 4f))
        assertEquals("100 ml", q.first().label)
    }

    @Test
    fun scalingUsesLabelEnergy() {
        // Palačinky: etiketa 1150 kJ ≈ 275 kcal / 180 g; z maker by vyšlo 272
        val pancakes = snack("Palačinky", "180g", 10f, 40f, 8f, kj = 1150f, fiber = 3f)
        val full = SnackCatalog.scale(pancakes, 180f)
        assertEquals(275, full.kcal)
        val half = SnackCatalog.scale(pancakes, 90f)
        assertEquals(5f, half.p, 1e-4f)
        assertTrue(half.kcal in 137..138)
        // Bez etikety: z maker
        val rice = SnackCatalog.scale(snack("Rýže", "100g", 7f, 77f, 1f), 100f)
        assertEquals(345, rice.kcal)
    }
}

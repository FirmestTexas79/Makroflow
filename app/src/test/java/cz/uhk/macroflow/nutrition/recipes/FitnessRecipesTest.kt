package cz.uhk.macroflow.nutrition.recipes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FitnessRecipesTest {
    private val eu14 = setOf("lepek", "korýši", "vejce", "ryby", "arašídy", "sója", "mléko", "skořápkové plody",
        "celer", "hořčice", "sezam", "oxid siřičitý", "vlčí bob", "měkkýši")

    @Test fun `patnact receptu s rozumnymi daty`() {
        val all = FitnessRecipes.ALL
        assertEquals(15, all.size)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        all.forEach { r ->
            assertTrue(r.id, r.ingredients.isNotEmpty() && r.steps.size >= 3 && r.servings >= 1)
            assertTrue(r.id, r.allergens.all { it in eu14 })
            val m = r.macros(1f)
            assertTrue("${r.id} ${m.kcal}", m.kcal in 100..1000)
            val fromMacros = m.p * 4 + m.s * 4 + m.t * 9
            assertTrue("${r.id} kcal ${m.kcal} vs makra $fromMacros", kotlin.math.abs(fromMacros - m.kcal) < m.kcal * 0.25f)
        }
    }

    @Test fun `porce prepocitaji gramy`() {
        val r = FitnessRecipes.ALL.first { it.servings > 1 }
        val one = r.scaled(1f).sumOf { it.grams.toDouble() }
        val all = r.ingredients.sumOf { it.grams.toDouble() }
        assertEquals(all / r.servings, one, 0.01)
        assertTrue(kotlin.math.abs(r.macros(r.servings.toFloat()).kcal - r.macros(1f).kcal * r.servings) <= r.servings)
    }

    @Test fun `kazdy recept ma fotku`() {
        FitnessRecipes.ALL.forEach { r ->
            assertTrue(r.id, listOf(File("src/main/res/drawable-nodpi/${r.photo}.jpg"), File("app/src/main/res/drawable-nodpi/${r.photo}.jpg")).any { it.exists() })
        }
    }
}

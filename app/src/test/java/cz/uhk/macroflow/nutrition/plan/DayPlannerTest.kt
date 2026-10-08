package cz.uhk.macroflow.nutrition.plan

import cz.uhk.macroflow.nutrition.plan.DayPlanner.Kind
import cz.uhk.macroflow.nutrition.plan.DayPlanner.Nutr
import cz.uhk.macroflow.nutrition.plan.DayPlanner.Option
import cz.uhk.macroflow.nutrition.plan.DayPlanner.Slot
import cz.uhk.macroflow.nutrition.recipes.FitnessRecipes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DayPlannerTest {

    private val options = FitnessRecipes.ALL.map { r ->
        val m = r.macros(1f)
        Option(r.id, r.name, when (r.category) {
            FitnessRecipes.Category.BREAKFAST -> Kind.BREAKFAST
            FitnessRecipes.Category.MAIN -> Kind.MAIN
            else -> Kind.SNACK
        }, Nutr(m.kcal.toDouble(), m.p.toDouble(), m.s.toDouble(), m.t.toDouble()))
    }
    private val target = Nutr(2600.0, 170.0, 300.0, 75.0)

    @Test fun openSlotsSkipPastAndEaten() {
        assertEquals(Slot.entries, DayPlanner.openSlots(6 * 60, emptyList()))
        // 12:00, snídaně snědená v 8:00 → oběd, svačina, večeře
        assertEquals(listOf(Slot.LUNCH, Slot.SNACK, Slot.DINNER), DayPlanner.openSlots(12 * 60, listOf(8 * 60)))
        // oběd už snědený ve 12:30
        assertEquals(listOf(Slot.SNACK, Slot.DINNER), DayPlanner.openSlots(13 * 60, listOf(8 * 60, 12 * 60 + 30)))
        assertTrue(DayPlanner.openSlots(23 * 60, emptyList()).isEmpty())
    }

    @Test fun wholeDayHitsTargetClosely() {
        val best = DayPlanner.plans(target, Nutr.ZERO, Slot.entries, options).first()
        assertTrue("kcal ${best.total.kcal}", abs(best.total.kcal - target.kcal) / target.kcal < 0.08)
        assertTrue("protein ${best.total.p}", abs(best.total.p - target.p) / target.p < 0.12)
        best.meals.forEach { m ->
            m.portions.forEach { assertTrue(it in 0.5..2.5); assertEquals(0.0, it % DayPlanner.STEP, 1e-9) }
            assertTrue(m.slot.kind in m.option.kinds)
        }
        assertEquals("žádný recept dvakrát", best.meals.size, best.meals.map { it.option.id }.toSet().size)
    }

    @Test fun staplesAndEatenAreSubtracted() {
        val shake = Nutr(320.0, 45.0, 20.0, 6.0)
        val eaten = Nutr(700.0, 40.0, 90.0, 20.0)
        val plan = DayPlanner.plans(target, eaten + shake, listOf(Slot.LUNCH, Slot.SNACK, Slot.DINNER), options).first()
        val meals = plan.meals.fold(Nutr.ZERO) { a, m -> a + m.nutr }
        assertEquals(plan.total.kcal, eaten.kcal + shake.kcal + meals.kcal, 1e-6)
        assertTrue(abs(plan.total.kcal - target.kcal) / target.kcal < 0.08)
    }

    @Test fun nothingWhenDayIsDone() {
        assertTrue(DayPlanner.plans(target, target - Nutr(100.0, 0.0, 0.0, 0.0), Slot.entries, options).isEmpty())
        assertTrue(DayPlanner.plans(target, Nutr.ZERO, emptyList(), options).isEmpty())
    }

    @Test fun alternativesDiffer() {
        val plans = DayPlanner.plans(target, Nutr.ZERO, Slot.entries, options)
        assertTrue(plans.size > 1)
        assertEquals(plans.size, plans.map { p -> p.meals.map { it.option.id }.toSet() }.toSet().size)
        assertTrue(plans.zipWithNext().all { (a, b) -> a.score <= b.score })
    }

    @Test fun pantryPlatesFillTheDay() {
        fun snack(id: Int, name: String, p: Float, s: Float, t: Float) =
            cz.uhk.macroflow.data.SnackEntity(id, name, "100 g", p, s, t, false, 0f, 0f)
        val pantry = listOf(
            snack(1, "Kuřecí prsa", 31f, 0f, 3.6f), snack(2, "Rýže vařená", 2.7f, 28f, 0.3f),
            snack(3, "Brokolice", 2.8f, 4f, 0.4f), snack(4, "Řecký jogurt", 10f, 3.6f, 0.4f),
            snack(5, "Ovesné vločky", 13f, 60f, 7f), snack(6, "Arašídové máslo", 25f, 12f, 50f)
        )
        val opts = PantryPlates.options(pantry)
        val plate = opts.first { Kind.MAIN in it.kinds }
        assertEquals(3, plate.parts.size)                                   // maso + příloha + zelenina
        assertEquals("Kuřecí prsa", PantryPlates.parts(plate.id, pantry).first().snack.name)   // nejoblíbenější bílkovina
        assertEquals("Brokolice", PantryPlates.parts(plate.id, pantry).last().snack.name)
        val best = DayPlanner.plans(target, Nutr.ZERO, Slot.entries, opts).first()
        assertTrue("kcal ${best.total.kcal}", abs(best.total.kcal - target.kcal) / target.kcal < 0.1)
        best.meals.forEach { m -> m.option.parts.zip(m.portions).forEach { (pt, k) -> assertTrue(k in pt.minPortion..pt.maxPortion) } }
    }
}

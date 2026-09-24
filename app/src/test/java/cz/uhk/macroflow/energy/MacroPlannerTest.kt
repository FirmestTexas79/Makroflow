package cz.uhk.macroflow.energy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroPlannerTest {

    private val samuelLike = Person(83.0, 175.0, 22, Sex.MALE)

    private fun plan(p: Person = samuelLike, goal: Goal = Goal.MAINTAIN, diet: Diet = Diet.BALANCED,
                     steps: Int = 6000, ex: List<Exercise> = emptyList()) =
        MacroPlanner.plan(p, goal, diet, Lifestyle.SEDENTARY, steps, ex)

    @Test fun `makra dávají dohromady cílové kcal – pro všechny diety a cíle`() {
        for (goal in Goal.entries) for (diet in Diet.entries) {
            val t = plan(goal = goal, diet = diet)
            val sum = FoodEnergy.kcal(t.proteinG, t.carbsG, t.fatG, t.fiberG)
            assertEquals("$goal/$diet", t.kcal, sum, 0.5)
        }
    }

    @Test fun `tuk nikdy pod 20 procent energie a sacharidy nikdy záporné`() {
        for (goal in Goal.entries) for (diet in Diet.entries) {
            val t = plan(goal = goal, diet = diet)
            assertTrue("$goal/$diet fat", t.fatG * 9 >= t.kcal * 0.20 - 0.5)
            assertTrue("$goal/$diet carbs", t.carbsG >= 0.0)
        }
    }

    @Test fun `keto drží 30 g sacharidů`() = assertEquals(30.0, plan(diet = Diet.KETO).carbsG, 1e-9)

    @Test fun `dieta = 0,5 procenta váhy týdně`() {
        val t = plan(goal = Goal.CUT)
        val deficit = t.expenditure.total - t.kcal
        assertEquals(83.0 * 0.005 * 7700.0 / 7.0, deficit, 0.01)   // 456,5 kcal/den
    }

    @Test fun `deficit je omezen na 25 procent výdeje`() {
        val heavy = Person(150.0, 180.0, 35, Sex.MALE)
        val t = plan(p = heavy, goal = Goal.CUT)
        assertTrue(t.kcal >= t.expenditure.total * 0.75 - 0.01)
    }

    @Test fun `příjem neklesne pod bezpečné minimum`() {
        // 45 kg, 155 cm, 60 let: výdej ≈ 1320 kcal, deficit by vedl na ≈ 1072 → minimum 1200
        val small = Person(45.0, 155.0, 60, Sex.FEMALE)
        assertEquals(1200.0, plan(p = small, goal = Goal.CUT).kcal, 0.01)
    }

    @Test fun `bílkoviny z netukové hmoty – obézní člověk nedostane 250 g`() {
        val obese = Person(120.0, 180.0, 40, Sex.MALE)
        val t = plan(p = obese)
        assertEquals(EnergyModel.fatFreeMassKg(obese) * 2.3, t.proteinG, 1e-9)
        assertTrue(t.proteinG / obese.weightKg < 2.0)
    }

    @Test fun `high protein při dietě = 3,1 g na kg netukové hmoty (horní mez Helms 2014)`() {
        val lean = samuelLike.copy(measuredBodyFatPct = 12.0)
        val t = plan(p = lean, goal = Goal.CUT, diet = Diet.HIGH_PROTEIN)
        assertEquals(83.0 * 0.88 * 3.1, t.proteinG, 1e-9)
    }

    @Test fun `vláknina 14 g na 1000 kcal, minimum 25 g`() {
        assertEquals(25.0, MacroPlanner.fiberG(1500.0), 1e-9)
        assertEquals(42.0, MacroPlanner.fiberG(3000.0), 1e-9)
    }

    @Test fun `voda podle EFSA plus pot z tréninku`() {
        assertEquals(2.75, MacroPlanner.waterL(Sex.MALE, 90.0), 1e-9)
        assertEquals(1.6, MacroPlanner.waterL(Sex.FEMALE, 0.0), 1e-9)
    }
}

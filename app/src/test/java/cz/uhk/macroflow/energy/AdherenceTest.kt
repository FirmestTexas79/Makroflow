package cz.uhk.macroflow.energy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdherenceTest {

    private val t = Adherence.Targets(kcal = 2500.0, protein = 160.0, carbs = 300.0, fat = 70.0)

    @Test fun `kalorie – hladovění ani přejedení není splnění`() {
        assertFalse(Adherence.isHit(Adherence.Nutrient.KCAL, Adherence.Eaten(1800.0, 160.0, 300.0, 70.0), t))
        assertTrue(Adherence.isHit(Adherence.Nutrient.KCAL, Adherence.Eaten(2400.0, 160.0, 300.0, 70.0), t))
        assertFalse(Adherence.isHit(Adherence.Nutrient.KCAL, Adherence.Eaten(3200.0, 160.0, 300.0, 70.0), t))
    }

    @Test fun `bílkoviny – minimum 90 procent, rozumný strop`() {
        assertFalse(Adherence.isHitPercent(Adherence.Nutrient.PROTEIN, 85))
        assertTrue(Adherence.isHitPercent(Adherence.Nutrient.PROTEIN, 140))
        assertFalse(Adherence.isHitPercent(Adherence.Nutrient.PROTEIN, 200))
    }

    @Test fun `perfektní den vyžaduje vše v pásmu`() {
        assertTrue(Adherence.isPerfectDay(Adherence.Eaten(2500.0, 170.0, 290.0, 72.0), t))
        assertFalse(Adherence.isPerfectDay(Adherence.Eaten(2500.0, 170.0, 200.0, 110.0), t))
    }

    @Test fun `nulový cíl nedává splnění`() =
        assertEquals(0, Adherence.percent(Adherence.Nutrient.FAT, Adherence.Eaten(0.0, 0.0, 0.0, 50.0), t.copy(fat = 0.0)))

    @Test fun `pokrok váhy jen ve směru cíle`() {
        assertEquals(2.0, Adherence.goalProgressKg(Goal.CUT, 90.0, 88.0, 42), 1e-9)   // 2 kg za 6 týdnů
        assertEquals(0.0, Adherence.goalProgressKg(Goal.CUT, 90.0, 92.0, 42), 1e-9)   // přibral při dietě
        assertEquals(2.0, Adherence.goalProgressKg(Goal.BULK, 75.0, 77.0, 120), 1e-9)
    }

    @Test fun `příliš rychlé hubnutí hra neodměňuje`() {
        // 5 kg za 3 týdny u 90 kg = 1,85 % týdně > 1 % → 0
        assertEquals(0.0, Adherence.goalProgressKg(Goal.CUT, 90.0, 85.0, 21), 1e-9)
    }

    @Test fun `udržování – stabilita aspoň 4 týdny`() {
        assertTrue(Adherence.isStableMaintenance(80.0, 80.6, 30))
        assertFalse(Adherence.isStableMaintenance(80.0, 81.5, 30))
        assertFalse(Adherence.isStableMaintenance(80.0, 80.2, 10))
    }
}

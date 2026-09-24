package cz.uhk.macroflow.energy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Referenční hodnoty jsou spočítané ručně z publikovaných rovnic (viz docs/adr/0002),
 * ne z implementace – test tak ověřuje, že kód odpovídá literatuře.
 */
class EnergyModelTest {

    private val samuelLike = Person(weightKg = 83.0, heightCm = 175.0, ageYears = 22, sex = Sex.MALE)
    private val eps = 0.01

    // ── Potraviny (EU 1169/2011) ────────────────────────────────────────────

    @Test fun `kcal z maker včetně vlákniny`() {
        assertEquals(171.0, FoodEnergy.kcal(10.0, 20.0, 5.0, 3.0), eps)  // 40 + 80 + 45 + 6
    }

    @Test fun `kJ používá pro tuk 37 kJ, ne 38`() {
        assertEquals(719.0, FoodEnergy.kj(10.0, 20.0, 5.0, 3.0), eps)    // 170 + 340 + 185 + 24
    }

    @Test fun `energie z etikety má přednost`() {
        assertEquals(100.0, FoodEnergy.kcalPreferLabel(418.4f, 50f, 50f, 50f), 0.01)
    }

    // ── BMR a složení těla ──────────────────────────────────────────────────

    @Test fun `Mifflin-St Jeor muž`() = assertEquals(1818.75, EnergyModel.bmrMifflin(samuelLike), eps)

    @Test fun `Mifflin-St Jeor žena`() =
        assertEquals(1320.25, EnergyModel.bmrMifflin(Person(60.0, 165.0, 30, Sex.FEMALE)), eps)

    @Test fun `Katch-McArdle z netukové hmoty`() = assertEquals(1882.0, EnergyModel.bmrKatchMcArdle(70.0), eps)

    @Test fun `změřené procento tuku přepne na Katch-McArdle`() {
        val lean = samuelLike.copy(measuredBodyFatPct = 12.0)
        assertEquals(370.0 + 21.6 * 83.0 * 0.88, EnergyModel.bmr(lean), eps)
    }

    @Test fun `odhad tuku Deurenberg`() =
        assertEquals(21.38, EnergyModel.estimatedBodyFatPct(samuelLike), 0.01)

    // ── Chůze (ACSM) ────────────────────────────────────────────────────────

    @Test fun `délka kroku z výšky`() = assertEquals(0.72625, EnergyModel.stepLengthM(samuelLike), 1e-6)

    @Test fun `chůze 0,5 kcal na kg a km čistého výdeje`() {
        val p = Person(80.0, 180.0, 30, Sex.MALE)          // krok 0,747 m → 10 000 kroků = 7,47 km
        assertEquals(298.8, EnergyModel.walkingNetKcal(10_000, p), eps)
    }

    @Test fun `kroky pro den`() {
        assertEquals(6000, EnergyModel.stepsForDay(null, isToday = false))
        assertEquals(6000, EnergyModel.stepsForDay(0, isToday = true))
        assertEquals(6000, EnergyModel.stepsForDay(3000, isToday = true))   // den ještě neskončil
        assertEquals(9000, EnergyModel.stepsForDay(9000, isToday = true))
        assertEquals(3000, EnergyModel.stepsForDay(3000, isToday = false))  // uzavřený den = realita
    }

    // ── Trénink (Compendium, ACSM) ──────────────────────────────────────────

    @Test fun `běh 1 kcal na kg a km`() =
        assertEquals(700.0, EnergyModel.exerciseNetKcal(Exercise.Run(10.0, 60.0), 70.0), eps)

    @Test fun `silový trénink čistý MET`() {
        assertEquals(320.0, EnergyModel.exerciseNetKcal(Exercise.Strength(StrengthKind.LEGS, 60.0), 80.0), eps)
        assertEquals(300.0, EnergyModel.exerciseNetKcal(Exercise.Strength(StrengthKind.PUSH, 90.0), 80.0), eps)
    }

    @Test fun `švihadlo podle kadence`() {
        // 1100 skoků / 10 min = 110/min → MET 11,8 → (11,8 − 1) × 70 × 1/6
        assertEquals(126.0, EnergyModel.exerciseNetKcal(Exercise.JumpRope(1100, 10.0), 70.0), eps)
    }

    @Test fun `schody`() = assertEquals(300.0, EnergyModel.exerciseNetKcal(Exercise.Stairs(30.0), 75.0), eps)

    // ── Celkový výdej ───────────────────────────────────────────────────────

    @Test fun `TEF je přesně 10 procent celku`() {
        val e = EnergyModel.expenditure(samuelLike, Lifestyle.SEDENTARY, 8000, emptyList())
        assertEquals(0.10, e.tef / e.total, 1e-9)
    }

    @Test fun `sedavý člověk s 6000 kroky má PAL v pásmu FAO 1,4`() {
        val e = EnergyModel.expenditure(samuelLike, Lifestyle.SEDENTARY, 6000, emptyList())
        val pal = e.total / e.bmr
        assertTrue("PAL $pal", pal in 1.35..1.45)
    }

    @Test fun `výdej roste s tréninkem právě o čistý výdej plus TEF`() {
        val rest = EnergyModel.expenditure(samuelLike, Lifestyle.SEDENTARY, 6000, emptyList())
        val legs = EnergyModel.expenditure(samuelLike, Lifestyle.SEDENTARY, 6000,
            listOf(Exercise.Strength(StrengthKind.LEGS, 60.0)))
        assertEquals(332.0 / 0.9, legs.total - rest.total, eps)
    }

    // ── Mapování z profilu ──────────────────────────────────────────────────

    @Test fun `dieta z českých popisků i enumů`() {
        assertEquals(Diet.BALANCED, Diet.from("Vyvážená"))
        assertEquals(Diet.LOW_CARB, Diet.from("Low Carb"))
        assertEquals(Diet.HIGH_PROTEIN, Diet.from("High Protein"))
        assertEquals(Diet.KETO, Diet.from("KETO"))
        assertEquals(Diet.VEGAN, Diet.from("Vegan"))
        assertEquals(Diet.BALANCED, Diet.from(null))
    }

    @Test fun `uložený násobitel se mapuje na životní styl`() {
        assertEquals(Lifestyle.SEDENTARY, Lifestyle.fromStored(1.2f))
        assertEquals(Lifestyle.ACTIVE_JOB, Lifestyle.fromStored(1.4f))
        assertEquals(Lifestyle.PHYSICAL_JOB, Lifestyle.fromStored(1.6f))
    }
}

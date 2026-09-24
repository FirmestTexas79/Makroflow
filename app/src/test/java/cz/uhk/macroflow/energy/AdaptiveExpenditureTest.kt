package cz.uhk.macroflow.energy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs

/**
 * Validace na syntetických datech: simulujeme člověka se ZNÁMÝM skutečným výdejem,
 * zapisovaným příjmem se šumem a vážením s denními výkyvy vody. Estimátor výdej nezná –
 * test ověřuje, že se k němu z dat přiblíží.
 */
class AdaptiveExpenditureTest {

    private val kcalPerKg = MacroPlanner.KCAL_PER_KG_TISSUE

    /** Simulace [days] dní. [weighEvery] = vážení každý n-tý den. */
    private fun simulate(
        seed: Long,
        trueTdee: Double,
        modelTdee: Double,
        meanIntake: Double,
        days: Int = 28,
        startWeight: Double = 83.0,
        intakeSd: Double = 250.0,
        scaleSd: Double = 0.7,
        weighEvery: Int = 1,
        loggedEvery: Int = 1
    ): List<AdaptiveExpenditure.Day> {
        val rnd = Random(seed)
        var w = startWeight
        return (0 until days).map { d ->
            val intake = meanIntake + rnd.nextGaussian() * intakeSd
            val measured = w + rnd.nextGaussian() * scaleSd
            val day = AdaptiveExpenditure.Day(
                day = 20_000 + d,
                weightKg = if (d % weighEvery == 0) measured else null,
                intakeKcal = if (d % loggedEvery == 0) intake else null,
                modelTdeeKcal = modelTdee
            )
            w += (intake - trueTdee) / kcalPerKg
            day
        }
    }

    // ── Trend váhy ──────────────────────────────────────────────────────────

    @Test fun `trend najde tempo hubnutí přes šum vody`() {
        // skutečně −0,5 kg/týden, váha šumí ±0,7 kg
        val slope = -0.5 / 7
        val rnd = Random(1)
        val weights = (0 until 28).associate { d -> d to 83.0 + slope * d + rnd.nextGaussian() * 0.7 }
        val avgSlope = WeightTrend().smooth(weights).map { it.slope }.average()
        assertEquals(slope, avgSlope, 0.025)
    }

    @Test fun `vyhlazení je v průměru přesnější než samotný filtr`() {
        val rnd = Random(9)
        var errFilter = 0.0
        var errSmooth = 0.0
        repeat(100) {
            val slope = (rnd.nextDouble() - 0.5) * 0.2
            val w = (0 until 28).associate { d -> d to 80.0 + slope * d + rnd.nextGaussian() * 0.7 }
            val t = WeightTrend()
            val f = t.filter(w); val sm = t.smooth(w)
            errFilter += abs((f.last().level - f.first().level) / 27 - slope)
            errSmooth += abs(sm.map { it.slope }.average() - slope)
        }
        assertTrue("filter $errFilter smooth $errSmooth", errSmooth < errFilter)
    }

    @Test fun `vyhlazení se v posledním dni shoduje s filtrem`() {
        val w = mapOf(0 to 80.0, 3 to 79.6, 5 to 79.9, 9 to 79.1)
        val t = WeightTrend()
        assertEquals(t.filter(w).last().level, t.smooth(w).last().level, 1e-12)
    }

    @Test fun `mezery ve vážení se zpracují po dnech, ne po záznamech`() {
        val weights = mapOf(0 to 80.0, 10 to 79.0, 20 to 78.0)   // 0,1 kg/den, jen 3 vážení
        val pts = WeightTrend(obsSd = 0.05).filter(weights)
        assertEquals(21, pts.size)                                // odhad pro každý den
        assertEquals(-0.1, pts.last().slope, 0.02)
    }

    @Test fun `nejistota predikce roste s horizontem`() {
        val t = WeightTrend()
        val last = t.filter((0 until 14).associateWith { 80.0 }).last()
        val (_, sd1) = t.forecast(last, 1)
        val (_, sd7) = t.forecast(last, 7)
        assertTrue(sd7 > sd1)
    }

    // ── Adaptivní výdej ─────────────────────────────────────────────────────

    @Test fun `bez dostatku dat se model nemění`() {
        val days = simulate(1, trueTdee = 2900.0, modelTdee = 2500.0, meanIntake = 2500.0, days = 10)
        val e = AdaptiveExpenditure.estimate(days)
        assertEquals(AdaptiveExpenditure.Status.NOT_ENOUGH_DATA, e.status)
        assertEquals(1.0, e.factor, 0.0)
    }

    @Test fun `model podhodnocuje – odhad se posune ke skutečnosti`() {
        val e = AdaptiveExpenditure.estimate(simulate(7, trueTdee = 2900.0, modelTdee = 2500.0, meanIntake = 2500.0))
        assertEquals(AdaptiveExpenditure.Status.OK, e.status)
        assertTrue("k=${e.factor}", e.factor > 1.0)
        assertTrue("odhad ${e.adaptiveTdee}", abs(e.adaptiveTdee - 2900) < abs(2500 - 2900.0) * 0.5)
    }

    @Test fun `model nadhodnocuje – odhad jde dolů`() {
        val e = AdaptiveExpenditure.estimate(simulate(3, trueTdee = 2200.0, modelTdee = 2600.0, meanIntake = 2400.0))
        assertTrue("k=${e.factor}", e.factor < 1.0)
    }

    @Test fun `přesný model zůstane přibližně beze změny`() {
        val e = AdaptiveExpenditure.estimate(simulate(11, trueTdee = 2500.0, modelTdee = 2500.0, meanIntake = 2300.0))
        assertEquals(1.0, e.factor, 0.06)
    }

    @Test fun `nedopsané dny se nepočítají`() {
        // Každý druhý den „zapomene“ zapsat večeři (jen 30 % příjmu) → takové dny se vyřadí
        val base = simulate(5, trueTdee = 2500.0, modelTdee = 2500.0, meanIntake = 2500.0)
        val partial = base.mapIndexed { i, d -> if (i % 2 == 1) d.copy(intakeKcal = d.intakeKcal!! * 0.3) else d }
        val e = AdaptiveExpenditure.estimate(partial)
        assertEquals(14, e.loggedDays)
        assertEquals(1.0, e.factor, 0.08)
    }

    @Test fun `korekce je omezena na plus minus 20 procent`() {
        val e = AdaptiveExpenditure.estimate(simulate(2, trueTdee = 4200.0, modelTdee = 2500.0, meanIntake = 2500.0,
            days = 28, scaleSd = 0.2))
        assertTrue(e.factor <= AdaptiveExpenditure.MAX_FACTOR + 1e-9)
    }

    @Test fun `Monte Carlo - adaptivní odhad je v průměru přesnější než samotný model`() {
        val rnd = Random(2026)
        var modelErr = 0.0
        var adaptiveErr = 0.0
        val runs = 200
        repeat(runs) { i ->
            val trueTdee = 2200.0 + rnd.nextDouble() * 1200.0
            val model = trueTdee * (1.0 + rnd.nextGaussian() * 0.10)       // rovnice ±10 %
            val intake = trueTdee + (rnd.nextDouble() - 0.5) * 1000.0         // dieta i bulk
            val e = AdaptiveExpenditure.estimate(
                simulate(1000L + i, trueTdee, model, intake, weighEvery = 1 + i % 3)
            )
            modelErr += abs(model - trueTdee)
            adaptiveErr += abs(e.adaptiveTdee - trueTdee)
        }
        // průměrná chyba klesne aspoň o čtvrtinu
        assertTrue("model ${modelErr / runs}, adaptivní ${adaptiveErr / runs}", adaptiveErr < modelErr * 0.75)
    }

    // ── Napojení na plánovač ────────────────────────────────────────────────

    @Test fun `korekce se promítne do výdeje i cíle`() {
        val p = Person(83.0, 175.0, 22, Sex.MALE)
        val base = MacroPlanner.plan(p, Goal.MAINTAIN, Diet.BALANCED, Lifestyle.SEDENTARY, 8000, emptyList())
        val adj = MacroPlanner.plan(p, Goal.MAINTAIN, Diet.BALANCED, Lifestyle.SEDENTARY, 8000, emptyList(), adaptiveFactor = 1.1)
        assertEquals(base.expenditure.total * 1.1, adj.expenditure.total, 1e-6)
        assertEquals(adj.expenditure.total, adj.kcal, 1e-6)
    }
}

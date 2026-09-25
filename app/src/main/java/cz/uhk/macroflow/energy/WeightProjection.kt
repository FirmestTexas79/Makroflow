package cz.uhk.macroflow.energy

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Biologická projekce hmotnosti na týden dopředu (čistý Kotlin, pokryto testy). Podklady v docs/adr/0020.
 *
 * Dva nezávislé zdroje informace o tempu změny hmotnosti:
 *  1. **Trend vážení** – stav Kalmanova filtru [WeightTrend] k vybranému dni (jen data známá k tomu dni).
 *  2. **Energetická bilance** – (příjem − výdej) / 7700 kcal/kg. Příjem = průměr zapsaných dní,
 *     nebo cíl kalorií, když se nezapisuje. Výdej = model z rovnic (fáze A) **bez adaptivní korekce**:
 *     adaptivní výdej je sám spočítaný z trendu vážení, takže by se stejná informace započetla dvakrát.
 *
 * Bilance vstupuje do filtru jako pozorování tempa (Kalmanova korekce s H = [0, 1]). Na začátku, kdy
 * vážení je málo a tempo z nich je nejisté, projekce stojí hlavně na bilanci; s přibývajícími váženími
 * převezme vedení trend. Interval je 95% a vychází z nejistot obou zdrojů.
 */
object WeightProjection {

    const val HORIZON_DAYS = 7
    /** Když se nezapisuje jídlo, bereme cíl – s nejistotou dodržení ±15 %. */
    const val TARGET_ADHERENCE_SD = 0.15
    /** Minimum zapsaných dní (z posledních 7), aby se příjem bral ze zápisu. */
    const val MIN_LOGGED_DAYS = 4

    enum class IntakeSource { LOGGED, TARGET }

    /** Vstup energetické bilance – průměry na den (kcal) a jejich směrodatné odchylky. */
    data class Energy(
        val intakeKcal: Double,
        val intakeSd: Double,
        val expenditureKcal: Double,
        val expenditureSd: Double,
        val source: IntakeSource
    ) {
        val balanceKcal: Double get() = intakeKcal - expenditureKcal
        /** Tempo z bilance (kg/den) a jeho nejistota. */
        val slopeKgPerDay: Double get() = balanceKcal / MacroPlanner.KCAL_PER_KG_TISSUE
        val slopeSd: Double get() = sqrt(intakeSd * intakeSd + expenditureSd * expenditureSd) / MacroPlanner.KCAL_PER_KG_TISSUE
    }

    data class Forecast(val day: Int, val mean: Double, val lower: Double, val upper: Double)

    data class Projection(
        val asOfDay: Int,
        /** Vyhlazený trend (RTS) ze všech vážení do vybraného dne – „skutečná“ hmotnost bez vody. */
        val trend: List<WeightTrend.Point>,
        /** Stav k vybranému dni po započtení bilance – z něj vychází predikce. */
        val start: WeightTrend.Point,
        val forecast: List<Forecast>,
        /** Tempo jen z vážení (kg/den), před započtením bilance. */
        val trendSlope: Double,
        val trendSlopeSd: Double,
        val energy: Energy?,
        /** Jakou vahou se bilance uplatnila v tempu (0 = jen vážení, 1 = jen bilance). */
        val energyWeight: Double,
        val weighIns: Int
    ) {
        val slopeKgPerWeek: Double get() = start.slope * 7
        val slopeSdKgPerWeek: Double get() = start.sdSlope * 7
        val end: Forecast get() = forecast.last()
    }

    /**
     * Energetický vstup z posledních dní.
     * @param loggedIntakes zapsané příjmy za poslední dokončené dny (už odfiltrované nedopsané dny)
     * @param targetKcal průměrný cíl kalorií na následující dny
     * @param modelExpenditure průměrný výdej z rovnic na následující dny (bez adaptivní korekce)
     */
    fun energyInput(loggedIntakes: List<Double>, targetKcal: Double, modelExpenditure: Double): Energy {
        val expenditureSd = AdaptiveExpenditure.MODEL_RELATIVE_SD * modelExpenditure
        return if (loggedIntakes.size >= MIN_LOGGED_DAYS) {
            val mean = loggedIntakes.average()
            val variance = loggedIntakes.sumOf { (it - mean) * (it - mean) } / (loggedIntakes.size - 1)
            // Nejistota budoucího průměru: rozptyl dní / n + systematická chyba zápisu
            val sd = sqrt(variance / loggedIntakes.size + (AdaptiveExpenditure.INTAKE_RELATIVE_SD * mean).let { it * it })
            Energy(mean, sd, modelExpenditure, expenditureSd, IntakeSource.LOGGED)
        } else {
            Energy(targetKcal, TARGET_ADHERENCE_SD * targetKcal, modelExpenditure, expenditureSd, IntakeSource.TARGET)
        }
    }

    /**
     * @param weights den (epochDay) → vážení; použijí se jen dny ≤ [asOfDay]
     * @return null, když do vybraného dne není žádné vážení
     */
    fun project(
        weights: Map<Int, Double>,
        asOfDay: Int,
        energy: Energy?,
        horizon: Int = HORIZON_DAYS,
        trend: WeightTrend = WeightTrend()
    ): Projection? {
        val known = weights.filterKeys { it <= asOfDay }
        if (known.isEmpty()) return null
        val filtered = trend.filter(known)
        val atDay = trend.predict(filtered.last(), asOfDay - filtered.last().day)

        val (start, weight) = if (energy != null) trend.observeSlope(atDay, energy.slopeKgPerDay, energy.slopeSd)
            else atDay to 0.0

        val forecast = (0..horizon).map { i ->
            val (mean, sd) = trend.forecast(start, i)
            Forecast(start.day + i, mean, mean - 1.96 * sd, mean + 1.96 * sd)
        }
        return Projection(
            asOfDay = asOfDay,
            trend = trend.smooth(known),
            start = start,
            forecast = forecast,
            trendSlope = atDay.slope,
            trendSlopeSd = atDay.sdSlope,
            energy = energy,
            energyWeight = weight,
            weighIns = known.size
        )
    }

    // ── Hodnocení tempa vzhledem k cíli ─────────────────────────────────────

    enum class Direction { LOSING, STABLE, GAINING }

    enum class Verdict { ON_TRACK, TOO_FAST, TOO_SLOW, WRONG_WAY, UNCERTAIN }

    data class Pace(
        val direction: Direction,
        val verdict: Verdict,
        /** Tempo v % tělesné hmotnosti za týden (záporné = hubnutí). */
        val percentPerWeek: Double,
        val message: String
    )

    /** Pod touto změnou (kg/týden) považujeme hmotnost za stabilní. */
    const val STABLE_KG_PER_WEEK = 0.1

    /**
     * Doporučená tempa (% hmotnosti za týden):
     *  - redukce 0,5–1,0 % (Helms, Aragon & Fitschen 2014 – zachování svalů u natural sportovců),
     *  - objem 0,25–0,5 % (Iraki et al. 2019 – omezení přírůstku tuku),
     *  - udržování ±0,25 %.
     */
    fun pace(goal: Goal, p: Projection): Pace {
        val kgWeek = p.slopeKgPerWeek
        val pct = kgWeek / p.start.level * 100
        val direction = when {
            kgWeek <= -STABLE_KG_PER_WEEK -> Direction.LOSING
            kgWeek >= STABLE_KG_PER_WEEK -> Direction.GAINING
            else -> Direction.STABLE
        }
        // Tempo nerozlišitelné od nuly a málo dat → nehodnotit
        if (p.weighIns < 5 && p.energyWeight < 0.5 && abs(kgWeek) < 1.96 * p.slopeSdKgPerWeek) {
            return Pace(direction, Verdict.UNCERTAIN, pct, "Zatím málo vážení – projekce se zpřesní s každým ranním check-inem.")
        }
        // Hranice mají toleranci ~0,1 % kolem cílových temp aplikace (−0,5 % / +0,25 %),
        // aby šum vážení nepřepínal hodnocení den co den.
        val (verdict, msg) = when (goal) {
            Goal.CUT -> when {
                pct > -0.1 -> Verdict.WRONG_WAY to "Na redukci váha neklesá. Zkontroluj zápis jídla nebo sniž příjem."
                pct < -1.0 -> Verdict.TOO_FAST to "Hubneš rychleji než 1 % týdně – hrozí ztráta svalů. Zvaž mírnější deficit."
                pct > -0.4 -> Verdict.TOO_SLOW to "Hubneš pomalu (pod 0,5 % týdně). Bezpečné, jen to potrvá déle."
                else -> Verdict.ON_TRACK to "Ideální tempo redukce: 0,5–1 % hmotnosti týdně."
            }
            Goal.BULK -> when {
                pct < 0.05 -> Verdict.WRONG_WAY to "Na objemu váha neroste. Přidej zhruba 150–250 kcal denně."
                pct > 0.5 -> Verdict.TOO_FAST to "Přibíráš rychleji než 0,5 % týdně – přibude zbytečně tuku."
                pct < 0.15 -> Verdict.TOO_SLOW to "Přibíráš pomalu (pod 0,25 % týdně) – čistý, ale pomalý objem."
                else -> Verdict.ON_TRACK to "Ideální tempo objemu: 0,25–0,5 % hmotnosti týdně."
            }
            Goal.MAINTAIN -> when {
                abs(pct) <= 0.25 -> Verdict.ON_TRACK to "Váha drží – přesně podle cíle udržování."
                pct < 0 -> Verdict.WRONG_WAY to "Na udržování váha klesá. Přidej trochu jídla."
                else -> Verdict.WRONG_WAY to "Na udržování váha roste. Uber trochu jídla nebo přidej pohyb."
            }
        }
        return Pace(direction, verdict, pct, msg)
    }
}

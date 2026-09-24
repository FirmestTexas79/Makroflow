package cz.uhk.macroflow.energy

/**
 * Kdy je denní cíl „splněný“ – jediné pravidlo pro questy, achievementy i XP.
 *
 * Princip (fáze C): hra odměňuje TREFENÍ osobního cíle, ne „čím víc/míň, tím líp“.
 *  - kalorie: ±10 % cíle – pod tím nehrozí odměna za hladovění, nad tím za přejídání,
 *  - bílkoviny: 90–150 % – dosažení minima; horní mez jen jako rozumný strop,
 *  - sacharidy a tuky: ±15 % – přirozená denní variabilita jídelníčku.
 * Cíle samotné už obsahují dietu/bulk (fáze A) a adaptivní výdej (fáze B).
 */
object Adherence {

    enum class Nutrient(val key: String, val label: String, val minPct: Int, val maxPct: Int) {
        KCAL("kcal", "kalorie", 90, 110),
        PROTEIN("protein", "bílkoviny", 90, 150),
        CARBS("carbs", "sacharidy", 85, 115),
        FAT("fat", "tuky", 85, 115);

        companion object {
            fun from(key: String?): Nutrient? = entries.firstOrNull { it.key == key }
        }
    }

    data class Targets(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)
    data class Eaten(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

    /** Snědeno v % cíle (zaokrouhleno dolů). Cíl ≤ 0 → 0 %. */
    fun percent(n: Nutrient, eaten: Eaten, targets: Targets): Int {
        val (e, t) = when (n) {
            Nutrient.KCAL -> eaten.kcal to targets.kcal
            Nutrient.PROTEIN -> eaten.protein to targets.protein
            Nutrient.CARBS -> eaten.carbs to targets.carbs
            Nutrient.FAT -> eaten.fat to targets.fat
        }
        return if (t <= 0.0) 0 else (e / t * 100.0).toInt()
    }

    fun isHitPercent(n: Nutrient, pct: Int): Boolean = pct in n.minPct..n.maxPct

    fun isHit(n: Nutrient, eaten: Eaten, targets: Targets): Boolean = isHitPercent(n, percent(n, eaten, targets))

    /** Celý den v pořádku: kalorie i všechna makra v pásmu. */
    fun isPerfectDay(eaten: Eaten, targets: Targets): Boolean = Nutrient.entries.all { isHit(it, eaten, targets) }

    // ── Váha: pokrok k cíli zdravým tempem ─────────────────────────────────

    /** Bezpečné maximum tempa hubnutí/přibírání v % hmotnosti za týden (Helms 2014: 0,5–1 %). */
    const val MAX_SAFE_RATE_PCT_PER_WEEK = 1.0

    /**
     * Posun trendové hmotnosti ve směru cíle [kg]. Pokud bylo průměrné tempo rychlejší
     * než bezpečné maximum, vrací 0 – rychlé shazování hra neodměňuje.
     * U udržování vrací 0 (tam se odměňuje stabilita, viz [isStableMaintenance]).
     */
    fun goalProgressKg(goal: Goal, startKg: Double, nowKg: Double, days: Int): Double {
        if (days <= 0 || goal == Goal.MAINTAIN) return 0.0
        val change = nowKg - startKg
        val towardGoal = if (goal == Goal.CUT) -change else change
        if (towardGoal <= 0.0) return 0.0
        val ratePctPerWeek = towardGoal / startKg * 100.0 / (days / 7.0)
        return if (ratePctPerWeek <= MAX_SAFE_RATE_PCT_PER_WEEK) towardGoal else 0.0
    }

    /** Udržování: trend se za [days] ≥ 28 dní nevzdálil od začátku o víc než 1 kg. */
    fun isStableMaintenance(startKg: Double, nowKg: Double, days: Int): Boolean =
        days >= 28 && kotlin.math.abs(nowKg - startKg) <= 1.0
}

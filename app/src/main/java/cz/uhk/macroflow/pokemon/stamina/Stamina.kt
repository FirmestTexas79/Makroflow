package cz.uhk.macroflow.pokemon.stamina

import kotlin.math.min

/**
 * Energie hráče v Makrosvětu (docs/adr/0065). Čistá pravidla bez Androidu, pokrytá testy.
 *
 * Základ 0–[BASE_MAX] + overstim 0–[OVER_MAX]. Utrácí se nejdřív overstim. Doplňuje se jen
 * přes noc ([refill]) a za zápisy ve funkční části ([grant]), nikdy pasivně během hraní.
 * (Balíček `stamina`, protože `energy` už patří k modelu kalorií.)
 */
object Stamina {
    const val BASE_MAX = 100
    const val OVER_MAX = 50
    /** Jak dlouho musí být hráč z Makrosvěta pryč, aby nový den doplnil energii. */
    const val AWAY_FOR_REFILL_MS = 4L * 60 * 60 * 1000
    /** Návrat do lokace, odkud hráč přišel, do této doby je zdarma (překliknutí). */
    const val RETURN_FREE_MS = 5L * 60 * 1000

    enum class Action(val cost: Int, val label: String) {
        TRANSITION(3, "Přechod"),
        WILD_BATTLE(4, "Souboj"),
        GUARDIAN(15, "Strážce"),
        GATHER_START(2, "Sběr"),
        CRAFT(2, "Výroba")
    }

    data class State(
        val base: Int = BASE_MAX,
        val over: Int = 0,
        /** Den (yyyy-MM-dd) posledního doplnění; null = ještě nikdy. */
        val refillDay: String? = null,
        /** Kdy hráč naposledy opustil Makrosvět (epoch ms), 0 = neznámo. */
        val lastExitAt: Long = 0,
        /** Odkud hráč přišel posledním placeným přechodem a kdy (pro bezplatný návrat). */
        val lastFrom: String? = null,
        val lastMoveAt: Long = 0,
        /** Kolik energie za zápisy dne [grantedDay] už bylo připsáno (odměny se počítají znovu a dorovnávají). */
        val granted: Int = 0,
        val grantedDay: String? = null
    ) {
        val total get() = base + over
    }

    /**
     * Doplnění přes noc: nový den a aspoň [AWAY_FOR_REFILL_MS] mimo Makrosvět. Overstim se maže.
     * Kdo hraje přes půlnoc, doplnění dostane až při prvním příchodu po čtyřhodinové pauze.
     * Hodiny posunuté dozadu (now < lastExitAt) doplnění nedají.
     */
    fun refill(s: State, today: String, now: Long): State {
        if (s.refillDay == today) return s
        val awayLongEnough = s.refillDay == null || s.lastExitAt == 0L || now - s.lastExitAt >= AWAY_FOR_REFILL_MS
        return if (awayLongEnough) s.copy(base = BASE_MAX, over = 0, refillDay = today, granted = 0, grantedDay = today) else s
    }

    /** Zaplatí [cost]; nejdřív z overstimu. null = energie nestačí. */
    fun spend(s: State, cost: Int): State? {
        if (cost <= 0) return s
        if (s.total < cost) return null
        val fromOver = min(s.over, cost)
        return s.copy(over = s.over - fromOver, base = s.base - (cost - fromOver))
    }

    /** Připíše energii: nejdřív do základu do [BASE_MAX], zbytek do overstimu do [OVER_MAX]. */
    fun grant(s: State, amount: Int): State {
        if (amount <= 0) return s
        val toBase = min(amount, BASE_MAX - s.base).coerceAtLeast(0)
        return s.copy(base = s.base + toBase, over = min(OVER_MAX, s.over + amount - toBase))
    }

    /** Co uživatel dnes zapsal ve funkční části (tabulka A v docs/adr/0065). */
    data class Day(
        val checkIn: Boolean = false,
        val meals: Int = 0,
        val waterMl: Int = 0,
        val waterGoalMl: Int = 0,
        val macrosHit: Boolean = false,
        val steps: Int = 0,
        val sets: Int = 0
    )

    /** Energie za celý den podle tabulky A, včetně denních stropů. Odměňuje zápis a cíl, ne množství. */
    fun dayReward(d: Day): Int {
        var r = 0
        if (d.checkIn) r += 15
        r += 5 * min(d.meals, 4)
        if (d.waterGoalMl > 0 && d.waterMl * 2 >= d.waterGoalMl) r += 5
        if (d.waterGoalMl > 0 && d.waterMl >= d.waterGoalMl) r += 10
        if (d.macrosHit) r += 10
        r += 5 * min(d.steps / 2000, 4)
        r += 2 * min(d.sets, 15)
        if (d.sets >= 6) r += 15          // dokončený trénink
        return r
    }

    /** Bílkoviny i kalorie v pásmu ±10 % cíle (přebytek se neodměňuje). */
    fun macrosHit(protein: Double, proteinGoal: Double, kcal: Double, kcalGoal: Double): Boolean =
        proteinGoal > 0 && kcalGoal > 0 &&
            kotlin.math.abs(protein - proteinGoal) <= proteinGoal * 0.1 &&
            kotlin.math.abs(kcal - kcalGoal) <= kcalGoal * 0.1

    /**
     * Dorovná odměny dne: [earned] je celkový nárok za [today], připíše se jen rozdíl proti už
     * připsanému. Nárok nikdy neklesá (smazané jídlo energii nevezme).
     */
    fun reward(s: State, today: String, earned: Int): State {
        val prev = if (s.grantedDay == today) s.granted else 0
        if (earned <= prev) return s
        return grant(s, earned - prev).copy(granted = earned, grantedDay = today)
    }

    /** Cena přechodu z [from] do [to]: návrat tam, odkud hráč právě přišel, je do 5 minut zdarma. */
    fun transitionCost(s: State, to: String, now: Long): Int =
        if (to == s.lastFrom && now >= s.lastMoveAt && now - s.lastMoveAt < RETURN_FREE_MS) 0
        else Action.TRANSITION.cost

    /**
     * Zapíše přechod. Placený si pamatuje výchozí lokaci; bezplatný návrat ji smaže,
     * aby se nedalo zdarma přecházet tam a zpátky donekonečna.
     */
    fun moved(s: State, from: String, cost: Int, now: Long): State =
        if (cost == 0) s.copy(lastFrom = null, lastMoveAt = 0) else s.copy(lastFrom = from, lastMoveAt = now)
}

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
        val lastMoveAt: Long = 0
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
        return if (awayLongEnough) s.copy(base = BASE_MAX, over = 0, refillDay = today) else s
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

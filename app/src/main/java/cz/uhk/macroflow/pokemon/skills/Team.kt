package cz.uhk.macroflow.pokemon.skills

/**
 * Aktivní tým Makromonů (docs/adr/0034): seznam ID chycených Makromonů, první je aktivní
 * parťák na liště a do souboje nastupuje první. Míst je 1–6 podle stromu Chytání.
 * Čistý Kotlin, pokryto testy.
 */
object Team {
    const val MAX = 6

    fun parse(csv: String?): List<Int> =
        csv.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.distinct()

    fun format(team: List<Int>): String = team.joinToString(",")

    /**
     * Srovná uložený tým se stavem: aktivní parťák první, bez duplicit a smazaných, nejvýš
     * [slots] členů (přebyteční se odeberou od konce – třeba po odebrání uzlu nebo z jiného zařízení).
     */
    fun normalize(team: List<Int>, active: Int?, slots: Int, existing: Set<Int>? = null): List<Int> {
        var t = team.distinct().filter { existing == null || it in existing }
        if (active != null && active > 0 && (existing == null || active in existing)) t = listOf(active) + (t - active)
        return t.take(slots.coerceIn(1, MAX))
    }

    sealed class Result {
        data class Ok(val team: List<Int>) : Result()
        object Full : Result()
    }

    /** Přidá na konec; plný tým = [Result.Full]. */
    fun add(team: List<Int>, id: Int, slots: Int): Result = when {
        id in team -> Result.Ok(team)
        team.size >= slots.coerceIn(1, MAX) -> Result.Full
        else -> Result.Ok(team + id)
    }

    fun remove(team: List<Int>, id: Int): List<Int> = team - id

    /** Udělá z Makromona aktivního (první). Plný tým vyřadí posledního. */
    fun makeActive(team: List<Int>, id: Int, slots: Int): List<Int> =
        (listOf(id) + (team - id)).take(slots.coerceIn(1, MAX))
}

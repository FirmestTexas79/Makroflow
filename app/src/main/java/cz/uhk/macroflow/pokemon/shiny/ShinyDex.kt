package cz.uhk.macroflow.pokemon.shiny

/**
 * Shiny část Makrodexu (čistý Kotlin, pokryto testy). Podklady v docs/adr/0016.
 *
 * * **Viděno** = shiny, na které hráč narazil v souboji (ukládá se v GamePrefs při setkání).
 *   Chycený shiny se počítá i jako viděný – i ti chycení před zavedením evidence.
 * * **Chyceno** = chycení Makromoni s `isShiny` (tabulka captured_makromon, synchronizuje se).
 */
object ShinyDex {
    /** GamePrefs: množina čísel Makrodexu, jejichž shiny verzi hráč viděl. */
    const val SEEN_KEY = "shinySeenIds"

    enum class Status { UNKNOWN, SEEN, CAUGHT }
    enum class Filter { SEEN, CAUGHT }

    fun status(id: String, seen: Set<String>, caught: Set<String>): Status = when (id) {
        in caught -> Status.CAUGHT
        in seen -> Status.SEEN
        else -> Status.UNKNOWN
    }

    /** Záznamy pro danou záložku, v pořadí Makrodexu. */
    fun visible(allIds: List<String>, filter: Filter, seen: Set<String>, caught: Set<String>): List<String> =
        allIds.filter {
            val s = status(it, seen, caught)
            if (filter == Filter.CAUGHT) s == Status.CAUGHT else s != Status.UNKNOWN
        }

    /** (viděno, chyceno) – chycení se počítají i do viděných. */
    fun counts(allIds: List<String>, seen: Set<String>, caught: Set<String>): Pair<Int, Int> =
        visible(allIds, Filter.SEEN, seen, caught).size to visible(allIds, Filter.CAUGHT, seen, caught).size
}

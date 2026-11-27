package cz.uhk.macroflow.energy

/**
 * Skutečný trénink dne přebíjí plán (docs/adr/0117). Bez Androidu – pokryto testy.
 *
 * Zdroje skutečnosti: aktivity z hodinek, deník sérií, rychlý zápis „Jdu na trénink“.
 *  - Silový trénink: hodinky > deník > rychlý zápis; druh (push/pull/legs) z plánu nebo zápisu.
 *  - Kardio a ostatní sporty: hodinky (jejich kcal) a rychlé kardio; chůze z hodinek se nepočítá – je v krocích.
 *  - Když skutečnost pro sílu / kardio existuje, nahradí plán téže skupiny.
 *  - Uzavřený den bez skutečnosti: plán vypadne jen tam, kde bychom trénink viděli (hodinky ten den synchronizovaly,
 *    u síly i pravidelné zapisování sérií). Kdo nic nezapisuje, tomu plán zůstane – nevíme, že necvičil.
 */
object ActualTraining {
    enum class Group { STRENGTH, CARDIO, WALK, OTHER }

    data class WatchActivity(val group: Group, val minutes: Double, val kcal: Int?)

    data class Evidence(
        /** Hodinky mají pro den data (trénink by tedy zachytily). */
        val watchSynced: Boolean = false,
        val watch: List<WatchActivity> = emptyList(),
        /** Délka zapsaného silového tréninku z deníku sérií (null = ten den nic). */
        val loggedStrengthMinutes: Double? = null,
        /** Uživatel série pravidelně zapisuje (posledních 14 dní) – chybějící zápis je důkaz vynechání. */
        val logsSets: Boolean = false,
        val quickStrength: Exercise.Strength? = null,
        val quickCardio: Exercise? = null
    )

    /** Kardio a ostatní sporty z hodinek bez kcal (Compendium: obecné kardio 7, ostatní 4,5 MET). */
    const val CARDIO_MET = 7.0
    const val OTHER_MET = 4.5

    fun isStrength(e: Exercise) = e is Exercise.Strength

    fun resolve(planned: List<Exercise>, ev: Evidence, dayOver: Boolean): List<Exercise> {
        val plannedStrength = planned.filterIsInstance<Exercise.Strength>()
        val plannedCardio = planned.filterNot(::isStrength)

        val watchStrength = ev.watch.filter { it.group == Group.STRENGTH }.sumOf { it.minutes }.takeIf { it > 0 }
        val kind = plannedStrength.firstOrNull()?.kind ?: ev.quickStrength?.kind ?: StrengthKind.PUSH
        val strength: List<Exercise> = when {
            watchStrength != null -> listOf(Exercise.Strength(kind, watchStrength))
            ev.loggedStrengthMinutes != null -> listOf(Exercise.Strength(kind, ev.loggedStrengthMinutes))
            ev.quickStrength != null -> listOf(ev.quickStrength.copy(kind = kind))
            dayOver && (ev.watchSynced || ev.logsSets) -> emptyList()
            else -> plannedStrength
        }

        val watchCardio = ev.watch.filter { it.group == Group.CARDIO || it.group == Group.OTHER }.map {
            if (it.kcal != null && it.kcal > 0) Exercise.Measured(it.kcal.toDouble(), it.minutes)
            else Exercise.Met(if (it.group == Group.CARDIO) CARDIO_MET else OTHER_MET, it.minutes)
        }
        val actualCardio = watchCardio + listOfNotNull(ev.quickCardio.takeIf { watchCardio.isEmpty() })
        val cardio: List<Exercise> = when {
            actualCardio.isNotEmpty() -> actualCardio
            dayOver && ev.watchSynced -> emptyList()
            else -> plannedCardio
        }
        return strength + cardio
    }

    /** Délka silového tréninku ze zapsaných sérií: od první do poslední + jedna pauza, 15–180 min. */
    fun minutesFromSets(createdAtMs: List<Long>): Double? {
        if (createdAtMs.size < 3) return null
        val span = (createdAtMs.max() - createdAtMs.min()) / 60_000.0
        return (span + 3.0).coerceIn(15.0, 180.0)
    }
}

package cz.uhk.macroflow.common

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Upozornění aplikace a jejich plánování (čistý Kotlin, pokryto testy) – docs/adr/0022.
 * Výchozí stav odpovídá chování před zavedením nastavení (vše zapnuto, stejné časy).
 */
enum class Reminder(
    val key: String,
    val title: String,
    val description: String,
    /** Čas v minutách od půlnoci; null = čas se nenastavuje (trénink podle plánu, voda ve slotech). */
    val defaultMinutes: Int?
) {
    MORNING("n_morning", "Ranní rituál", "Připomene check-in, pokud ho ještě nemáš", 8 * 60),
    WORKOUT("n_workout", "Trénink", "30 minut před tréninkem a po něm – podle času v Plánu", null),
    WATER("n_water", "Pitný režim", "Každé 2 hodiny mezi 9 a 21, jen když dlouho nepiješ", null),
    EVENING("n_evening", "Večerní přehled", "Shrnutí dne, nebo připomínka zapsat jídlo", 20 * 60),
    STREAK("n_streak", "Série v ohrožení", "Když ti chybí dnešní check-in a máš rozjetou sérii", 21 * 60)
}

object ReminderSchedule {

    const val WATER_FIRST_HOUR = 9
    const val WATER_LAST_HOUR = 21
    const val WATER_EVERY_HOURS = 2

    /** Nejbližší výskyt času [minutesOfDay] po [now] (dnes, jinak zítra). */
    fun nextDaily(now: LocalDateTime, minutesOfDay: Int): LocalDateTime {
        val t = now.toLocalDate().atTime(LocalTime.of(minutesOfDay / 60, minutesOfDay % 60))
        return if (t.isAfter(now)) t else t.plusDays(1)
    }

    /**
     * Nejbližší slot připomínky vody (9, 11, …, 21 h). Dřív se při spuštění po 9:00 bral
     * „9:00 + 2 h“, což mohl být čas v minulosti → upozornění vyskočilo hned.
     */
    fun nextWaterSlot(now: LocalDateTime): LocalDateTime {
        var h = WATER_FIRST_HOUR
        while (h <= WATER_LAST_HOUR) {
            val t = now.toLocalDate().atTime(h, 0)
            if (t.isAfter(now)) return t
            h += WATER_EVERY_HOURS
        }
        return now.toLocalDate().plusDays(1).atTime(WATER_FIRST_HOUR, 0)
    }

    /**
     * Délka série check-inů, o kterou uživatel dnes přijde: počet po sobě jdoucích dnů končících
     * včerejškem. Dřív se bral počet všech check-inů celkem (i s mezerami).
     */
    fun streakAtRisk(checkInDays: Set<LocalDate>, today: LocalDate): Int {
        var d = today.minusDays(1)
        var n = 0
        while (d in checkInDays) { n++; d = d.minusDays(1) }
        return n
    }

    /** „8:05“ */
    fun format(minutesOfDay: Int): String = "%d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)
}

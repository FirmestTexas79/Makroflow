package cz.uhk.macroflow.settings

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Připomínky aplikace a jejich plánování (čistý Kotlin, pokryto testy). Podklady v docs/adr/0022.
 *
 * [defaultMinutes] = výchozí čas v minutách od půlnoci; null = čas se nenastavuje
 * (trénink se řídí časem tréninku z plánu, voda běží po dvou hodinách přes den).
 */
enum class Reminder(val key: String, val title: String, val description: String, val defaultMinutes: Int?) {
    MORNING("morning", "Ranní rituál", "Připomene vážení a check-in, jen pokud ještě nejsou hotové.", 8 * 60),
    WORKOUT("workout", "Trénink", "30 min před tréninkem jídlo před výkonem, po tréninku bílkoviny.", null),
    WATER("water", "Pitný režim", "Mezi 9. a 21. hodinou, jen když jsi dlouho nepil a cíl ještě chybí.", null),
    EVENING("evening", "Večerní přehled", "Shrnutí dne, nebo připomínka zapsat jídlo.", 20 * 60),
    STREAK("streak", "Série v ohrožení", "Když by dnešek bez check-inu přerušil sérii dní po sobě.", 21 * 60);

    val hasTime: Boolean get() = defaultMinutes != null
}

object ReminderTime {

    /** Nejbližší okamžik s časem [minutesOfDay] – dnes, pokud ještě nenastal, jinak zítra. */
    fun nextTrigger(now: LocalDateTime, minutesOfDay: Int): LocalDateTime {
        val m = minutesOfDay.coerceIn(0, 24 * 60 - 1)
        val today = now.toLocalDate().atTime(LocalTime.of(m / 60, m % 60))
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /** „8:00“, „20:30“. */
    fun format(minutesOfDay: Int): String = "%d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)

    /**
     * Večerní upozornění musí přijít až po ranním (jinak by „dnes nic nezapsáno“ chodilo ráno).
     * Vrací chybovou hlášku, nebo null když je čas v pořádku.
     */
    fun validate(reminder: Reminder, minutesOfDay: Int): String? = when (reminder) {
        Reminder.MORNING -> if (minutesOfDay !in 4 * 60..12 * 60) "Ranní rituál nastav mezi 4:00 a 12:00." else null
        Reminder.EVENING, Reminder.STREAK -> if (minutesOfDay !in 16 * 60..23 * 60 + 30) "Večerní upozornění nastav mezi 16:00 a 23:30." else null
        else -> null
    }
}

/** Denní cíl vody pro připomínku (ml) – z modelu, se spodní rozumnou hranicí. */
object WaterGoal {
    const val FALLBACK_ML = 2500
    fun ml(targetLiters: Double?): Int =
        targetLiters?.takeIf { it > 0.5 }?.let { (it * 1000).toInt() } ?: FALLBACK_ML
}

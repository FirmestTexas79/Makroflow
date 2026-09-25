package cz.uhk.macroflow.history

import java.time.LocalDate

/** Týdenní pás kalendáře v Historii (čistý Kotlin, pokryto testy) – docs/adr/0022. */
object CalendarWeek {

    private val MONTHS_GENITIVE = listOf(
        "ledna", "února", "března", "dubna", "května", "června",
        "července", "srpna", "září", "října", "listopadu", "prosince"
    )

    fun monday(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

    /** Po–Ne týdne, do kterého patří [d]. */
    fun days(d: LocalDate): List<LocalDate> = monday(d).let { m -> (0L..6L).map { m.plusDays(it) } }

    /** „22.–28. září“, přes hranici měsíce „29. září – 5. října“, přes rok i s rokem. */
    fun label(d: LocalDate, today: LocalDate = LocalDate.now()): String {
        val a = monday(d); val b = a.plusDays(6)
        val year = if (b.year != today.year || a.year != b.year) " ${b.year}" else ""
        return if (a.month == b.month) "${a.dayOfMonth}.–${b.dayOfMonth}. ${MONTHS_GENITIVE[a.monthValue - 1]}$year"
        else "${a.dayOfMonth}. ${MONTHS_GENITIVE[a.monthValue - 1]} – ${b.dayOfMonth}. ${MONTHS_GENITIVE[b.monthValue - 1]}$year"
    }

    /** Dopředu jen do týdne, ve kterém je dnešek (budoucí dny nemají data). */
    fun canGoForward(anchor: LocalDate, today: LocalDate): Boolean = monday(anchor) < monday(today)
}

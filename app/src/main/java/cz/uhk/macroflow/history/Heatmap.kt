package cz.uhk.macroflow.history

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Heatmapa aktivity ve stylu GitHubu (čistý Kotlin, pokryto testy). Podklady v docs/adr/0007.
 *
 * Úroveň dne: -1 = bez dat, 0 = data, ale nic, 1–4 = stupně intenzity.
 */
enum class HeatMetric(val label: String) {
    /** Pevné hranice se zdravotním významem (Paluch et al. 2022). */
    STEPS("Kroky"),
    /** Chůze z naměřených kroků + trénink; kvartily vlastních dní (jako GitHub). */
    ACTIVE_KCAL("Aktivní výdej"),
    /** Počet trefených pásem (kalorie, B, S, T) – odměňuje trefu cíle, ne extrém. */
    GOALS("Cíle jídla");
}

object Heatmap {

    const val NO_DATA = -1
    val STEP_BOUNDS = intArrayOf(4000, 7000, 10000)
    const val STEP_GOAL = 7000
    const val GOALS_MAX = 4

    /** Úroveň pro každý den z [values] (null = bez dat). */
    fun levels(metric: HeatMetric, values: Map<LocalDate, Double?>): Map<LocalDate, Int> {
        val thresholds = if (metric == HeatMetric.ACTIVE_KCAL) quartiles(values.values.filterNotNull().filter { it > 0 }) else null
        return values.mapValues { (_, v) -> level(metric, v, thresholds) }
    }

    fun level(metric: HeatMetric, v: Double?, thresholds: DoubleArray? = null): Int {
        if (v == null) return NO_DATA
        if (v <= 0) return 0
        return when (metric) {
            HeatMetric.STEPS -> 1 + STEP_BOUNDS.count { v >= it }
            HeatMetric.GOALS -> v.roundToInt().coerceIn(0, GOALS_MAX)
            HeatMetric.ACTIVE_KCAL -> {
                val t = thresholds ?: return 4
                1 + t.count { v > it }
            }
        }
    }

    /** Hranice kvartilů (25., 50., 75. percentil) kladných hodnot. */
    fun quartiles(values: List<Double>): DoubleArray? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        fun pct(p: Double): Double {
            val pos = p * (s.size - 1)
            val lo = pos.toInt(); val hi = minOf(lo + 1, s.size - 1)
            return s[lo] + (s[hi] - s[lo]) * (pos - lo)
        }
        return doubleArrayOf(pct(0.25), pct(0.50), pct(0.75))
    }

    /**
     * Týdny (sloupce) od pondělí do neděle končící týdnem s [end]; [weeks] sloupců.
     * Dny po [end] jsou null (budoucnost se nekreslí).
     */
    fun weekColumns(end: LocalDate, weeks: Int): List<List<LocalDate?>> {
        val lastMonday = end.with(DayOfWeek.MONDAY)
        val firstMonday = lastMonday.minusWeeks((weeks - 1).toLong())
        return (0 until weeks).map { w ->
            val monday = firstMonday.plusWeeks(w.toLong())
            (0 until 7).map { d -> monday.plusDays(d.toLong()).takeIf { !it.isAfter(end) } }
        }
    }

    /**
     * Aktuální série splněných dní končící [today]. Nedokončený dnešek sérii nepřeruší
     * (počítá se od včerejška), splněný dnešek ji prodlouží.
     */
    fun streak(values: Map<LocalDate, Double?>, today: LocalDate, qualifies: (Double) -> Boolean): Int {
        var day = if (values[today]?.let(qualifies) == true) today else today.minusDays(1)
        var n = 0
        while (values[day]?.let(qualifies) == true) { n++; day = day.minusDays(1) }
        return n
    }

    fun qualifies(metric: HeatMetric, v: Double): Boolean = when (metric) {
        HeatMetric.STEPS -> v >= STEP_GOAL
        HeatMetric.GOALS -> v >= GOALS_MAX
        HeatMetric.ACTIVE_KCAL -> false
    }

    /** Jednořádkové shrnutí pod heatmapou. */
    fun summary(metric: HeatMetric, values: Map<LocalDate, Double?>, today: LocalDate): String {
        val data = values.filterValues { it != null }.mapValues { it.value!! }
        if (data.isEmpty()) return "Zatím žádná data."
        val cz = Locale("cs", "CZ")
        fun int(v: Double) = String.format(cz, "%,d", v.roundToInt()).replace(' ', ' ')
        return when (metric) {
            HeatMetric.STEPS -> {
                val s = streak(values, today) { qualifies(metric, it) }
                "Ø ${int(data.values.average())} kroků/den · ${days(data.values.count { it >= STEP_GOAL })} ≥ ${int(STEP_GOAL.toDouble())}" +
                    (if (s > 0) " · série ${days(s)}" else "")
            }
            HeatMetric.ACTIVE_KCAL -> {
                val best = data.maxBy { it.value }
                "Ø ${int(data.values.average())} kcal/den z pohybu · nejvíc ${int(best.value)} kcal (${best.key.dayOfMonth}. ${best.key.monthValue}.)"
            }
            HeatMetric.GOALS -> {
                val s = streak(values, today) { qualifies(metric, it) }
                "${days(data.values.count { it >= GOALS_MAX })} se všemi 4 cíli · Ø " +
                    String.format(cz, "%.1f", data.values.average()) + " ze 4" + (if (s > 0) " · série ${days(s)}" else "")
            }
        }
    }

    /** Česky: 1 den, 2–4 dny, 0 a 5+ dní. */
    fun days(n: Int): String = "$n " + when {
        n == 1 -> "den"
        n in 2..4 -> "dny"
        else -> "dní"
    }

    fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)
}

package cz.uhk.macroflow.pokemon.evolution

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Větvený vývoj Spirry (docs/adr/0031), bez Androidu – pokryto testy.
 *
 * Spirra se vyvine podle toho, co s ní jako aktivním parťákem děláš. Počítají se jen dny,
 * kdy byla Spirra aktivní (na liště); cíl, který splníš první, rozhodne o vývoji.
 * Drakirra je tajná – jen k ulovení.
 */
object SpirraEvolution {

    const val SPIRRA_ID = "012"
    const val DRAKIRRA_ID = "019"

    /** Vláknina „v rozmezí“: 90–140 % osobního cíle. */
    const val FIBER_MIN = 0.9
    const val FIBER_MAX = 1.4

    enum class Branch(val id: String, val displayName: String, val goal: Int, val task: String) {
        FLAMIRRA("013", "Flamirra", 5000, "Spal 5 000 kcal pohybem"),
        AQUIRRA("014", "Aquirra", 25_000, "Vypij celkem 25 l vody"),
        VERDIRRA("015", "Verdirra", 3, "3 dny za sebou vlákninu v rozmezí 90–140 % cíle"),
        SHADIRRA("016", "Shadirra", 10, "Zapiš 10 jídel v noci (21:00–4:59)"),
        CHARMIRRA("017", "Charmirra", 100_000, "Nachoď 100 000 kroků"),
        GLACIRRA("018", "Glacirra", 200, "Zapiš 200 sérií do tréninkového deníku")
    }

    /** Jeden den, kdy byla Spirra aktivním parťákem. */
    data class Day(
        val date: LocalDate,
        val burnedKcal: Int = 0,
        val waterMl: Int = 0,
        val fiberG: Double = 0.0,
        val fiberTargetG: Double = 0.0,
        val nightMeals: Int = 0,
        val steps: Int = 0,
        val workoutSets: Int = 0
    )

    fun isNight(time: String): Boolean {
        val h = time.substringBefore(':').trim().toIntOrNull() ?: return false
        return h >= 21 || h < 5
    }

    fun fiberInRange(day: Day): Boolean =
        day.fiberTargetG > 0 && day.fiberG >= day.fiberTargetG * FIBER_MIN && day.fiberG <= day.fiberTargetG * FIBER_MAX

    /** Nejdelší řada po sobě jdoucích kalendářních dnů s vlákninou v rozmezí. */
    fun bestFiberStreak(days: List<Day>): Int {
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (d in days.sortedBy { it.date }) {
            run = if (fiberInRange(d)) { if (prev != null && d.date == prev.plusDays(1) && run > 0) run + 1 else 1 } else 0
            prev = d.date
            best = maxOf(best, run)
        }
        return best
    }

    /** Postup ke každé větvi (hodnota ve stejných jednotkách jako [Branch.goal]). */
    fun progress(days: List<Day>): Map<Branch, Int> {
        val unique = days.distinctBy { it.date }
        return mapOf(
            Branch.FLAMIRRA to unique.sumOf { it.burnedKcal },
            Branch.AQUIRRA to unique.sumOf { it.waterMl },
            Branch.VERDIRRA to bestFiberStreak(unique),
            Branch.SHADIRRA to unique.sumOf { it.nightMeals },
            Branch.CHARMIRRA to unique.sumOf { it.steps },
            Branch.GLACIRRA to unique.sumOf { it.workoutSets }
        )
    }

    fun fraction(b: Branch, value: Int): Float = (value.toFloat() / b.goal).coerceIn(0f, 1f)

    /** Větev, do které se Spirra vyvine (splněná; při více splněných ta s největší rezervou). */
    fun ready(progress: Map<Branch, Int>): Branch? =
        progress.filter { (b, v) -> v >= b.goal }.maxByOrNull { (b, v) -> v.toDouble() / b.goal }?.key

    /** „1 250 / 5 000 kcal“, „12,5 / 25 l“. */
    fun progressText(b: Branch, value: Int): String {
        val v = value.coerceAtMost(b.goal)
        return when (b) {
            Branch.FLAMIRRA -> "${thousands(v)} / ${thousands(b.goal)} kcal"
            Branch.AQUIRRA -> "${liters(v)} / ${liters(b.goal)} l"
            Branch.VERDIRRA -> "$v / ${b.goal} dny"
            Branch.SHADIRRA -> "$v / ${b.goal} jídel"
            Branch.CHARMIRRA -> "${thousands(v)} / ${thousands(b.goal)} kroků"
            Branch.GLACIRRA -> "$v / ${b.goal} sérií"
        }
    }

    // ── Uložené dny (pro SharedPreferences) ──

    fun encode(d: Day): String = listOf(
        d.date.toString(), d.burnedKcal, d.waterMl, (d.fiberG * 10).roundToInt(), (d.fiberTargetG * 10).roundToInt(),
        d.nightMeals, d.steps, d.workoutSets
    ).joinToString("|")

    fun decode(s: String): Day? {
        val f = s.split('|')
        if (f.size < 8) return null
        return runCatching {
            Day(LocalDate.parse(f[0]), f[1].toInt(), f[2].toInt(), f[3].toInt() / 10.0, f[4].toInt() / 10.0,
                f[5].toInt(), f[6].toInt(), f[7].toInt())
        }.getOrNull()
    }

    private fun thousands(n: Int) = n.toString().reversed().chunked(3).joinToString(" ").reversed()
    private fun liters(ml: Int): String {
        val s = String.format(java.util.Locale.US, "%.1f", ml / 1000.0).removeSuffix(".0")
        return s.replace('.', ',')
    }
}

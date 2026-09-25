package cz.uhk.macroflow.nutrition

import kotlin.math.roundToInt

/**
 * Opakovaná jídla (docs/adr/0027), bez Androidu:
 *  - rozdělení dne na jídla podle času zápisu (položky do [MEAL_GAP_MIN] minut od sebe = jedno jídlo),
 *  - pojmenování podle denní doby (snídaně, oběd …),
 *  - šablony – uložení jídla nebo celého dne a jeho převod zpět na položky,
 *  - textový formát položek šablony pro Room (bez org.json, aby šel testovat na JVM).
 */
object MealRepeat {

    const val MEAL_GAP_MIN = 60

    /** Jedna snědená položka (hodnoty za snědenou porci, jako v consumed_snacks). */
    data class Item(
        val name: String,
        val p: Float, val s: Float, val t: Float,
        val calories: Int, val energyKj: Float, val fiber: Float,
        val time: String          // HH:mm
    )

    data class Meal(val label: String, val time: String, val items: List<Item>) {
        val kcal: Int get() = items.sumOf { it.calories }
        val protein: Float get() = items.sumOf { it.p.toDouble() }.toFloat()
        val summary: String get() = items.joinToString(", ") { it.name }
    }

    enum class Slot(val label: String, val fromMin: Int) {
        LATE("Pozdní jídlo", 0),
        BREAKFAST("Snídaně", 4 * 60),
        SNACK_AM("Dopolední svačina", 10 * 60 + 30),
        LUNCH("Oběd", 11 * 60 + 30),
        SNACK_PM("Odpolední svačina", 14 * 60 + 30),
        DINNER("Večeře", 17 * 60 + 30),
        LATE_EVENING("Pozdní jídlo", 21 * 60 + 30);
    }

    fun minutes(time: String): Int? {
        val parts = time.split(':')
        val h = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun slotOf(minutes: Int): Slot = Slot.entries.last { minutes >= it.fromMin }

    /**
     * Jídla dne v časovém pořadí. Položky bez platného času tvoří samostatné jídlo „Bez času“.
     * Dvě jídla ve stejné denní době se rozliší číslem („Oběd 2“).
     */
    fun meals(items: List<Item>): List<Meal> {
        val timed = items.mapNotNull { i -> minutes(i.time)?.let { it to i } }.sortedBy { it.first }
        val groups = mutableListOf<MutableList<Pair<Int, Item>>>()
        for (entry in timed) {
            val last = groups.lastOrNull()
            if (last != null && entry.first - last.last().first <= MEAL_GAP_MIN) last += entry else groups += mutableListOf(entry)
        }
        val used = mutableMapOf<String, Int>()
        val out = groups.map { g ->
            val base = slotOf(g.first().first).label
            val n = (used[base] ?: 0) + 1
            used[base] = n
            Meal(if (n == 1) base else "$base $n", g.first().second.time, g.map { it.second })
        }.toMutableList()
        val untimed = items.filter { minutes(it.time) == null }
        if (untimed.isNotEmpty()) out += Meal("Bez času", "", untimed)
        return out
    }

    /**
     * Položky k novému zápisu. Jedno jídlo se zapíše na aktuální čas [nowTime]; celý den si
     * ponechá původní časy (aby se dal zase rozdělit na snídani, oběd …).
     */
    fun relog(items: List<Item>, nowTime: String, keepTimes: Boolean): List<Item> =
        items.map { if (keepTimes && minutes(it.time) != null) it else it.copy(time = nowTime) }

    fun totalKcal(items: List<Item>) = items.sumOf { it.calories }

    // ── Šablony ─────────────────────────────────────────────────────────────

    enum class Kind { MEAL, DAY }

    /** Výchozí název šablony („Snídaně“, „Den 24. 9.“). */
    fun defaultName(kind: Kind, mealLabel: String?, day: Int, month: Int): String =
        if (kind == Kind.MEAL && !mealLabel.isNullOrBlank()) mealLabel.replace(Regex(""" \d+$"""), "") else "Den $day. $month."

    private const val FIELD = '\u001F'   // oddělovač polí (unit separator)
    private const val RECORD = '\u001E'  // oddělovač položek (record separator)

    /** Položky → text pro sloupec šablony. Řídicí znaky se z názvu odstraní. */
    fun encode(items: List<Item>): String = items.joinToString(RECORD.toString()) { i ->
        listOf(
            i.name.filterNot { it == FIELD || it == RECORD },
            i.p.toString(), i.s.toString(), i.t.toString(), i.calories.toString(),
            i.energyKj.toString(), i.fiber.toString(), i.time
        ).joinToString(FIELD.toString())
    }

    /** Text → položky; poškozené položky se přeskočí. */
    fun decode(text: String): List<Item> {
        if (text.isEmpty()) return emptyList()
        return text.split(RECORD).mapNotNull { rec ->
            val f = rec.split(FIELD)
            if (f.size < 8) return@mapNotNull null
            Item(
                name = f[0],
                p = f[1].toFloatOrNull() ?: return@mapNotNull null,
                s = f[2].toFloatOrNull() ?: return@mapNotNull null,
                t = f[3].toFloatOrNull() ?: return@mapNotNull null,
                calories = f[4].toIntOrNull() ?: return@mapNotNull null,
                energyKj = f[5].toFloatOrNull() ?: 0f,
                fiber = f[6].toFloatOrNull() ?: 0f,
                time = f[7]
            )
        }
    }

    /** Krátký popis pro seznam: „3 položky · 520 kcal · 38 g B“. */
    fun describe(items: List<Item>): String {
        val n = items.size
        val word = when { n == 1 -> "položka"; n in 2..4 -> "položky"; else -> "položek" }
        val protein = items.sumOf { it.p.toDouble() }.roundToInt()
        return "$n $word · ${totalKcal(items)} kcal · $protein g B"
    }
}

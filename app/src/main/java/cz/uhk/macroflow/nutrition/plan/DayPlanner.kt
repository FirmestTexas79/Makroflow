package cz.uhk.macroflow.nutrition.plan

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Ideální zbytek dne (docs/adr/0069), čistý Kotlin.
 *
 * Cíl dne (kcal, B, S, T) dává [cz.uhk.macroflow.dashboard.MacroCalculator] – predikce výdeje
 * (BMR + NEAT + kroky do konce dne + trénink + TEF, adaptivně korigovaná vážením).
 * Od něj se odečte snědené a pevné položky dne (shake …), které ještě nejsou zapsané.
 * Zbytek se rozdělí do volných jídel dne: pro každou kombinaci receptů se hledají porce x_i
 * minimalizující vážené relativní odchylky od cíle
 *
 *     Σ_j w_j · ((base_j + Σ_i a_ij·x_i − T_j) / T_j)²  +  λ · Σ_i (x_i − 1)²  +  c · počet jídel
 *
 * (j = kcal, B, S, T; a_ij = makra jedné porce; λ drží porce blízko běžné porci).
 * Je to omezená nejmenší čtverce, řešená souřadnicovým sestupem s ořezem do [min, max] porcí,
 * pak zaokrouhleno na čtvrt porce. Kombinací je pár set, na telefonu to trvá milisekundy.
 */
object DayPlanner {

    data class Nutr(val kcal: Double, val p: Double, val s: Double, val t: Double) {
        operator fun plus(o: Nutr) = Nutr(kcal + o.kcal, p + o.p, s + o.s, t + o.t)
        operator fun minus(o: Nutr) = Nutr(kcal - o.kcal, p - o.p, s - o.s, t - o.t)
        operator fun times(k: Double) = Nutr(kcal * k, p * k, s * k, t * k)
        operator fun get(j: Int) = when (j) { 0 -> kcal; 1 -> p; 2 -> s; else -> t }

        companion object { val ZERO = Nutr(0.0, 0.0, 0.0, 0.0) }
    }

    enum class Kind { BREAKFAST, MAIN, SNACK }

    enum class Slot(val label: String, val fromMin: Int, val toMin: Int, val kind: Kind) {
        BREAKFAST("Snídaně", 4 * 60, 10 * 60 + 30, Kind.BREAKFAST),
        LUNCH("Oběd", 10 * 60 + 30, 14 * 60 + 30, Kind.MAIN),
        SNACK("Svačina", 14 * 60 + 30, 17 * 60 + 30, Kind.SNACK),
        DINNER("Večeře", 17 * 60 + 30, 22 * 60, Kind.MAIN)
    }

    /** Jídlo, které jde navrhnout: makra jedné porce a rozsah porcí. */
    data class Option(
        val id: String, val name: String, val kind: Kind, val perPortion: Nutr,
        val minPortion: Double = 0.5, val maxPortion: Double = 2.5
    )

    data class Meal(val slot: Slot, val option: Option, val portions: Double) {
        val nutr: Nutr get() = option.perPortion * portions
    }

    /** [total] = snědené + pevné položky + navržená jídla. */
    data class Plan(val meals: List<Meal>, val total: Nutr, val score: Double)

    /** Váhy odchylek kcal, B, S, T – bílkoviny a energie jsou nejdůležitější. */
    private val W = doubleArrayOf(1.0, 1.5, 0.6, 0.6)
    private const val LAMBDA = 0.02
    private const val MEAL_COST = 0.002
    const val STEP = 0.25
    /** Pod tolik zbývajících kcal už nic nenavrhujeme – den je splněný. */
    const val DONE_KCAL = 150.0

    /** Jídla dne, která jsou ještě před námi a v jejichž čase se nic nejedlo. */
    fun openSlots(nowMin: Int, eatenMinutes: List<Int>): List<Slot> =
        Slot.entries.filter { s -> s.toMin > nowMin && eatenMinutes.none { it in s.fromMin until s.toMin } }

    /**
     * Nejlepší plány seřazené od nejlepšího, každý s jinou sadou jídel (pro „Jiný návrh“).
     * @param base snědené + pevné položky, které se ještě sní
     */
    fun plans(target: Nutr, base: Nutr, slots: List<Slot>, options: List<Option>, max: Int = 5): List<Plan> {
        if (target.kcal - base.kcal < DONE_KCAL || slots.isEmpty()) return emptyList()
        val out = mutableListOf<Plan>()
        for (mask in 1 until (1 shl slots.size)) {
            val chosen = slots.filterIndexed { i, _ -> mask and (1 shl i) != 0 }
            pick(chosen, options, 0, ArrayList()) { picks -> out += fit(target, base, chosen, picks) }
        }
        return out.sortedBy { it.score }.distinctBy { p -> p.meals.map { it.option.id }.toSet() }.take(max)
    }

    private fun pick(slots: List<Slot>, options: List<Option>, i: Int, acc: ArrayList<Option>, emit: (List<Option>) -> Unit) {
        if (i == slots.size) { emit(acc.toList()); return }
        for (o in options) {
            if (o.kind != slots[i].kind || acc.any { it.id == o.id }) continue
            acc += o; pick(slots, options, i + 1, acc, emit); acc.removeAt(acc.lastIndex)
        }
    }

    private fun fit(target: Nutr, base: Nutr, slots: List<Slot>, picks: List<Option>): Plan {
        val n = picks.size
        val rem = target - base
        val x = DoubleArray(n) { 1.0 }
        repeat(40) {
            for (i in 0 until n) {
                var num = LAMBDA; var den = LAMBDA
                for (j in 0..3) {
                    val w = W[j] / sq(max(target[j], 1.0))
                    var other = 0.0
                    for (k in 0 until n) if (k != i) other += picks[k].perPortion[j] * x[k]
                    val a = picks[i].perPortion[j]
                    num += w * a * (rem[j] - other); den += w * a * a
                }
                x[i] = (num / den).coerceIn(picks[i].minPortion, picks[i].maxPortion)
            }
        }
        val meals = picks.indices.map { i ->
            val q = ((x[i] / STEP).roundToInt() * STEP).coerceIn(picks[i].minPortion, picks[i].maxPortion)
            Meal(slots[i], picks[i], q)
        }
        val total = meals.fold(base) { acc, m -> acc + m.nutr }
        return Plan(meals, total, score(total, target, meals))
    }

    fun score(total: Nutr, target: Nutr, meals: List<Meal>): Double =
        (0..3).sumOf { j -> W[j] * sq((total[j] - target[j]) / max(target[j], 1.0)) } +
            meals.sumOf { LAMBDA * sq(it.portions - 1.0) } + MEAL_COST * meals.size

    private fun sq(v: Double) = v * v
}

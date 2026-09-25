package cz.uhk.macroflow.nutrition

import cz.uhk.macroflow.data.SnackEntity
import cz.uhk.macroflow.energy.FoodEnergy
import java.text.Normalizer
import kotlin.math.roundToInt

/**
 * Logika obrazovky Snacky bez Androidu (pokryto testy). Podklady v docs/adr/0021.
 *
 *  - rozřazení potravin podle toho, odkud pochází většina energie (ne „bílkovin víc než sacharidů“,
 *    kvůli čemuž ořechy nebo avokádo padaly mezi sacharidy),
 *  - hledání bez diakritiky („kure“ najde „Kuřecí“),
 *  - oddíl Oblíbené z historie používání,
 *  - přepočet porce včetně energie z etikety (dřív seznam ukazoval kcal z etikety, ale dialog
 *    a uložený záznam kcal z maker – u stejné potraviny se čísla lišila).
 */
object SnackCatalog {

    enum class Timing { ALL, PRE, POST }

    enum class Group(val title: String) {
        FAVOURITES("OBLÍBENÉ"),
        PROTEIN("BÍLKOVINY"),
        CARBS("SACHARIDY"),
        FAT("TUKY"),
        LIGHT("ZELENINA A LEHKÉ")
    }

    sealed interface Row {
        data class Header(val group: Group, val count: Int) : Row
        data class Item(val snack: SnackEntity, val group: Group) : Row
    }

    const val MAX_FAVOURITES = 5

    /** Výchozí porce v gramech (nebo ml) z textu „150g“, „250ml“, „1,5 kg“; neznámé → 100. */
    fun portionGrams(weight: String): Float {
        val m = Regex("""(\d+(?:[.,]\d+)?)\s*(kg|g|ml|l)?""", RegexOption.IGNORE_CASE).find(weight) ?: return 100f
        val value = m.groupValues[1].replace(',', '.').toFloatOrNull() ?: return 100f
        val grams = when (m.groupValues[2].lowercase()) { "kg", "l" -> value * 1000f; else -> value }
        return grams.takeIf { it > 0f } ?: 100f
    }

    /** Jednotka porce pro zobrazení („g“ nebo „ml“). */
    fun unit(weight: String): String = if (weight.contains("ml", true) || Regex("""\d\s*l\b""", RegexOption.IGNORE_CASE).containsMatchIn(weight)) "ml" else "g"

    /** Podíl energie z bílkovin, sacharidů a tuků (součet 1; bez energie rovnoměrně). */
    fun energySplit(p: Float, s: Float, t: Float): Triple<Float, Float, Float> {
        val ep = p * FoodEnergy.KCAL_PER_G_PROTEIN.toFloat()
        val es = s * FoodEnergy.KCAL_PER_G_CARBS.toFloat()
        val et = t * FoodEnergy.KCAL_PER_G_FAT.toFloat()
        val sum = ep + es + et
        if (sum <= 0f) return Triple(1f / 3, 1f / 3, 1f / 3)
        return Triple(ep / sum, es / sum, et / sum)
    }

    /** Pod touto energetickou hustotou (kcal/100 g) je potravina „lehká“ – zelenina, saláty, houby. */
    const val LIGHT_KCAL_PER_100G = 60.0

    fun kcalPer100g(snack: SnackEntity): Double =
        FoodEnergy.kcalPreferLabel(snack.energyKj, snack.p, snack.s, snack.t, snack.fiber) / portionGrams(snack.weight) * 100

    /**
     * Lehké potraviny (< 60 kcal/100 g) zvlášť – jinak by brokolice kvůli poměru padala mezi bílkoviny.
     * Ostatní podle největšího zdroje energie; při těsné shodě rozhodnou bílkoviny.
     */
    fun groupOf(snack: SnackEntity): Group {
        if (kcalPer100g(snack) < LIGHT_KCAL_PER_100G) return Group.LIGHT
        val (p, s, t) = energySplit(snack.p, snack.s, snack.t)
        return when {
            p >= s - 0.05f && p >= t - 0.05f -> Group.PROTEIN
            t > s -> Group.FAT
            else -> Group.CARBS
        }
    }

    /** Malá písmena bez diakritiky. */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** Shoda, když název obsahuje všechna slova dotazu (v libovolném pořadí). */
    fun matches(snack: SnackEntity, query: String): Boolean {
        val words = normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val name = normalize(snack.name)
        return words.all { name.contains(it) }
    }

    fun matchesTiming(snack: SnackEntity, timing: Timing) = when (timing) {
        Timing.ALL -> true
        Timing.PRE -> snack.isPre
        Timing.POST -> !snack.isPre
    }

    /**
     * Řádky seznamu. [snacks] už jsou seřazené podle oblíbenosti (DAO „smart“ dotaz) – pořadí se zachová.
     * Oblíbené (nejvýš [MAX_FAVOURITES] s [usage] > 0) jen bez hledání; v ostatních skupinách se neopakují.
     * Duplicitní názvy (stejná potravina uložená dvakrát) se ukážou jednou.
     */
    fun rows(snacks: List<SnackEntity>, query: String, timing: Timing, usage: Map<String, Int>): List<Row> {
        val visible = snacks.filter { matchesTiming(it, timing) && matches(it, query) }
            .distinctBy { normalize(it.name) to it.weight }
        val favourites = if (query.isBlank())
            visible.filter { (usage[it.name] ?: 0) > 0 }.sortedByDescending { usage[it.name] ?: 0 }.take(MAX_FAVOURITES)
        else emptyList()
        val favIds = favourites.map { it.id }.toSet()

        val out = mutableListOf<Row>()
        if (favourites.isNotEmpty()) {
            out += Row.Header(Group.FAVOURITES, favourites.size)
            favourites.forEach { out += Row.Item(it, Group.FAVOURITES) }
        }
        val rest = visible.filter { it.id !in favIds }.groupBy { groupOf(it) }
        listOf(Group.PROTEIN, Group.CARBS, Group.FAT, Group.LIGHT).forEach { g ->
            val items = rest[g].orEmpty()
            if (items.isNotEmpty()) {
                out += Row.Header(g, items.size)
                items.forEach { out += Row.Item(it, g) }
            }
        }
        return out
    }

    // ── Porce ───────────────────────────────────────────────────────────────

    data class Portion(val label: String, val grams: Float)

    /** Rychlé volby: ½ porce, porce, 2 porce a 100 g (bez duplicit, vzestupně). */
    fun portionPresets(snack: SnackEntity): List<Portion> {
        val base = portionGrams(snack.weight)
        val u = unit(snack.weight)
        val list = listOf(
            Portion("½ porce", base / 2),
            Portion("porce", base),
            Portion("2 porce", base * 2),
            Portion("100 $u", 100f)
        )
        return list.distinctBy { it.grams.roundToInt() }.sortedBy { it.grams }
    }

    data class Scaled(val grams: Float, val p: Float, val s: Float, val t: Float, val fiber: Float, val kcal: Int, val kj: Float)

    /**
     * Hodnoty pro [grams]. Energie: když má potravina energii z etikety, škáluje se ta (je přesnější než
     * výpočet z maker – obsahuje alkoholy, polyoly apod.); jinak z maker (EU 1169/2011).
     */
    fun scale(snack: SnackEntity, grams: Float): Scaled {
        val base = portionGrams(snack.weight)
        val f = if (base > 0f) grams / base else 0f
        val p = snack.p * f; val s = snack.s * f; val t = snack.t * f; val fiber = snack.fiber * f
        val kcal = FoodEnergy.kcalPreferLabel(snack.energyKj, snack.p, snack.s, snack.t, snack.fiber) * f
        val kj = kcal * FoodEnergy.KJ_PER_KCAL
        return Scaled(grams, p, s, t, fiber, kcal.roundToInt(), kj.toFloat())
    }
}

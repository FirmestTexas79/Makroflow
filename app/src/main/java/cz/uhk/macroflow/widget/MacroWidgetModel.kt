package cz.uhk.macroflow.widget

import cz.uhk.macroflow.energy.Adherence
import kotlin.math.roundToInt

/**
 * Co ukazuje widget Makra 2×2 (docs/adr/0026), bez Androidu (pokryto testy).
 *
 * Uprostřed logo, kolem něj prstenec rozdělený na tři oblouky: sacharidy vlevo dole, bílkoviny
 * nahoře, tuky vpravo dole. Mezi dvěma spodními oblouky je mezera s kaloriemi (snědeno nebo
 * zbývá, přepíná se klepnutím). Oblouk se plní podle snědeného podílu denního cíle.
 *
 * Úhly jsou jako v Canvas.drawArc: 0° = vpravo, kladně po směru hodinových ručiček.
 */
object MacroWidgetModel {

    enum class Mode { EATEN, REMAINING }

    enum class Macro(val letter: String, val label: String) {
        CARBS("S", "sacharidy"),
        PROTEIN("B", "bílkoviny"),
        FAT("T", "tuky")
    }

    /** Mezera dole pro kalorie a mezery mezi oblouky (stupně, včetně zaoblených konců). */
    const val BOTTOM_GAP_DEG = 84f
    const val SEPARATOR_DEG = 12f
    /** Oblouky v pořadí po směru hodinových ručiček od levého kraje spodní mezery. */
    val ORDER = listOf(Macro.CARBS, Macro.PROTEIN, Macro.FAT)

    val SWEEP_DEG = (360f - BOTTOM_GAP_DEG - SEPARATOR_DEG * (ORDER.size - 1)) / ORDER.size

    /**
     * Jeden oblouk. [startDeg]/[sweepDeg] = celá dráha (podklad), [fillStartDeg]/[fillSweepDeg] =
     * vyplněná část (záporný sweep = proti směru ručiček – pravý oblouk se plní zdola nahoru,
     * zrcadlově k levému). [value] = gramy k zobrazení podle režimu, [over] = cíl překročen.
     */
    data class Segment(
        val macro: Macro,
        val startDeg: Float,
        val sweepDeg: Float,
        val fraction: Float,
        val fillStartDeg: Float,
        val fillSweepDeg: Float,
        val value: Int,
        val over: Boolean
    ) {
        val midDeg: Float get() = startDeg + sweepDeg / 2
    }

    data class State(
        val segments: List<Segment>,
        val kcalText: String,
        val kcalCaption: String,
        val description: String,
        val hasTargets: Boolean
    )

    /** Začátek oblouku [index] (0 = levý). */
    fun startDeg(index: Int): Float = 90f + BOTTOM_GAP_DEG / 2 + index * (SWEEP_DEG + SEPARATOR_DEG)

    private fun eatenOf(m: Macro, e: Adherence.Eaten) = when (m) {
        Macro.PROTEIN -> e.protein; Macro.CARBS -> e.carbs; Macro.FAT -> e.fat
    }
    private fun targetOf(m: Macro, t: Adherence.Targets) = when (m) {
        Macro.PROTEIN -> t.protein; Macro.CARBS -> t.carbs; Macro.FAT -> t.fat
    }

    /** Podíl snědeného z cíle 0–1 (cíl ≤ 0 nebo neznámý → 0). */
    fun fraction(eaten: Double, target: Double?): Float =
        if (target == null || target <= 0.0) 0f else (eaten / target).toFloat().coerceIn(0f, 1f)

    /** Celé číslo s úzkou mezerou po tisících („2 150“). */
    fun thousands(n: Int): String {
        val s = kotlin.math.abs(n).toString().reversed().chunked(3).joinToString("\u202F").reversed()
        return if (n < 0) "-$s" else s
    }

    fun build(eaten: Adherence.Eaten, targets: Adherence.Targets?, mode: Mode): State {
        val usable = targets?.takeIf { it.kcal > 0.0 }
        val effectiveMode = if (usable == null) Mode.EATEN else mode

        val segments = ORDER.mapIndexed { i, m ->
            val start = startDeg(i)
            val e = eatenOf(m, eaten)
            val t = usable?.let { targetOf(m, it) }
            val f = fraction(e, t)
            val over = t != null && t > 0.0 && e > t
            val value = when (effectiveMode) {
                Mode.EATEN -> e.roundToInt()
                Mode.REMAINING -> (t!! - e).roundToInt().let { if (over) -it else it }   // navíc = kladné číslo
            }
            val rightSide = i == ORDER.lastIndex
            Segment(
                macro = m, startDeg = start, sweepDeg = SWEEP_DEG, fraction = f,
                fillStartDeg = if (rightSide) start + SWEEP_DEG else start,
                fillSweepDeg = if (rightSide) -SWEEP_DEG * f else SWEEP_DEG * f,
                value = value, over = over
            )
        }

        val kcalEaten = eaten.kcal.roundToInt()
        val (text, caption) = when {
            usable == null -> thousands(kcalEaten) to "kcal snědeno"
            effectiveMode == Mode.EATEN -> thousands(kcalEaten) to "z ${thousands(usable.kcal.roundToInt())} kcal"
            else -> {
                val left = (usable.kcal - eaten.kcal).roundToInt()
                if (left >= 0) thousands(left) to "kcal zbývá" else "+${thousands(-left)}" to "kcal navíc"
            }
        }

        val parts = segments.joinToString(", ") { s ->
            val t = usable?.let { targetOf(s.macro, it).roundToInt() }
            "${s.macro.label} ${eatenOf(s.macro, eaten).roundToInt()}" + (t?.let { " z $it" } ?: "") + " g"
        }
        val description = "Makroflow: ${kcalEaten} kcal snědeno" +
            (usable?.let { " z ${it.kcal.roundToInt()}" } ?: "") + "; $parts"

        return State(segments, text, caption, description, usable != null)
    }

    /** Popisek oblouku: v režimu „zbývá“ se překročení píše s plusem. */
    fun segmentLabel(s: Segment, mode: Mode, hasTargets: Boolean): String =
        if (mode == Mode.REMAINING && hasTargets && s.over) "+${s.value}" else s.value.toString()
}

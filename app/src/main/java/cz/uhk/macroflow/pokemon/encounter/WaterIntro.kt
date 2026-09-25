package cz.uhk.macroflow.pokemon.encounter

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Časová osa intra setkání u vody (čistý Kotlin, pokryto testy). Kreslí [WaterScene],
 * přehrává PixelEncounterView; podklady v docs/adr/0030.
 *
 * Soumrak nad hladinou, v rákosí splávek. Po hladině se rozbíhají kruhy, pod vodou krouží
 * stín, splávek dvakrát cukne, pak zmizí pod hladinou a vytryskne vodní sloup – záblesk a souboj.
 */
object WaterIntro {

    const val FADE_IN_END = 350L          // tma → soumrak
    const val RIPPLE_START = 380L         // kruhy od splávku
    const val SHADOW_START = 650L         // stín pod hladinou se blíží
    const val SHADOW_END = 1600L
    const val TUG_1 = 1120L               // první cuknutí
    const val TUG_2 = 1400L               // druhé cuknutí
    const val TUG_LEN = 150L
    const val PULL_UNDER = 1620L          // splávek zmizí pod hladinou
    const val BURST_START = 1680L         // vodní sloup
    const val BURST_PEAK = 1950L
    const val REVEAL_AT = 2000L           // záblesk a souboj (kapky ještě padají)
    const val END = 2600L

    const val RIPPLE_EVERY = 420L
    const val RIPPLE_LIFE = 1100L

    fun progress(t: Long, start: Long, end: Long): Float = MountainIntro.progress(t, start, end)

    /** Svislý posun splávku v pixelech: klidné pohupování, dvě cuknutí dolů, pak pod hladinu. */
    fun bobberOffset(t: Long): Float {
        val idle = sin(t * 0.0065).toFloat() * 0.8f
        fun tug(start: Long): Float {
            if (t !in start until start + TUG_LEN) return 0f
            return sin(PI * progress(t, start, start + TUG_LEN)).toFloat() * 3f
        }
        val under = if (t >= PULL_UNDER) progress(t, PULL_UNDER, PULL_UNDER + 90) * 6f else 0f
        return idle + tug(TUG_1) + tug(TUG_2) + under
    }

    fun bobberVisible(t: Long): Boolean = t < PULL_UNDER + 90

    /**
     * Kruhy na hladině: (poloměr 0..1 vůči maximu, průhlednost 0..1) pro všechny právě živé.
     * Pravidelné kruhy od [RIPPLE_START] + silnější při cuknutích.
     */
    fun ripples(t: Long): List<Pair<Float, Float>> {
        val starts = mutableListOf<Long>()
        var s = RIPPLE_START
        while (s <= minOf(t, PULL_UNDER)) { starts += s; s += RIPPLE_EVERY }
        starts += listOf(TUG_1, TUG_2, PULL_UNDER).filter { it <= t }
        return starts.mapNotNull { s0 ->
            val p = (t - s0).toFloat() / RIPPLE_LIFE
            if (p < 0f || p > 1f) null else p to (1f - p) * (1f - p)
        }
    }

    /**
     * Stín pod hladinou: (dx, dy) vůči splávku v násobcích poloměru a průhlednost. Krouží po
     * spirále blíž a blíž, při vytrysknutí zmizí.
     */
    fun shadow(t: Long): Triple<Float, Float, Float> {
        if (t < SHADOW_START || t >= BURST_START) return Triple(0f, 0f, 0f)
        val p = progress(t, SHADOW_START, SHADOW_END)
        val r = 1f - p * 0.95f
        val a = (p * 2.4f * PI).toFloat() + 0.8f
        val alpha = minOf(1f, p * 3f) * 0.55f
        return Triple(cos(a) * r, sin(a) * r * 0.45f, alpha)
    }

    /** Výška vodního sloupu 0..1 (rychle nahoru, pak se mírně rozpadá). */
    fun column(t: Long): Float = when {
        t < BURST_START -> 0f
        t < BURST_PEAK -> MountainIntro.easeOutCubic(progress(t, BURST_START, BURST_PEAK))
        else -> 1f - 0.35f * progress(t, BURST_PEAK, END)
    }

    /** Otřes obrazu v pixelech – jen při vytrysknutí. */
    fun shakeAmplitude(t: Long): Float = when {
        t < BURST_START -> 0f
        t < BURST_START + 400 -> 2f * (1f - progress(t, BURST_START, BURST_START + 400))
        else -> 0f
    }

    /** Kývání rákosí (px) – stéblo [i] má vlastní fázi, u vytrysknutí se rozkývá víc. */
    fun reedSway(t: Long, i: Int): Float {
        val gust = if (t >= BURST_START) 1f + 1.5f * (1f - progress(t, BURST_START, END)) else 1f
        return sin(t * 0.004 + i * 1.3).toFloat() * gust
    }
}

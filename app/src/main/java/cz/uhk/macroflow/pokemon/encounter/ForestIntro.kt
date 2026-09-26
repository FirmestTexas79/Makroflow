package cz.uhk.macroflow.pokemon.encounter

import kotlin.math.PI
import kotlin.math.sin

/**
 * Časová osa intra setkání v Hvozdu (čistý Kotlin, pokryto testy). Kreslí [ForestScene],
 * přehrává PixelEncounterView – stejná stylizace jako voda (docs/adr/0030, 0036).
 *
 * Soumrak v lese: světlo padá mezi kmeny, poletují světlušky a listí. Keř uprostřed palouku
 * se dvakrát zachvěje, v jeho stínu se rozsvítí oči, pak se keř rozlétne do listí,
 * z korun vyletí ptáci – záblesk a souboj.
 */
object ForestIntro {
    const val FADE_IN_END = 350L
    const val RUSTLE_1 = 650L
    const val RUSTLE_2 = 1200L
    const val RUSTLE_LEN = 260L
    const val EYES_1 = 900L          // oči se poprvé rozsvítí
    const val EYES_1_END = 1150L
    const val EYES_2 = 1480L         // podruhé – blíž a větší
    const val SHAKE_START = 1620L    // keř se rozkmitá
    const val BURST_START = 1760L    // keř se rozlétne
    const val BURST_PEAK = 1960L
    const val REVEAL_AT = 2060L
    const val END = 2660L

    fun progress(t: Long, start: Long, end: Long): Float = MountainIntro.progress(t, start, end)

    /** Vodorovný kmit keře v pixelech (dvě zachvění, pak čím dál silnější třes). */
    fun rustle(t: Long): Float {
        fun burst(start: Long): Float {
            if (t !in start until start + RUSTLE_LEN) return 0f
            val p = progress(t, start, start + RUSTLE_LEN)
            return sin(p * PI * 6).toFloat() * 1.6f * (1f - p)
        }
        val shake = if (t in SHAKE_START until BURST_START) sin(t * 0.09).toFloat() * (1f + 2f * progress(t, SHAKE_START, BURST_START)) else 0f
        return burst(RUSTLE_1) + burst(RUSTLE_2) + shake
    }

    /** Oči ve stínu keře: (průhlednost 0–1, velikost 1–2). Mrknou uprostřed prvního rozsvícení. */
    fun eyes(t: Long): Pair<Float, Float> = when {
        t in EYES_1 until EYES_1_END -> {
            val p = progress(t, EYES_1, EYES_1_END)
            val blink = if (p in 0.45f..0.55f) 0f else 1f
            (minOf(1f, p * 5f) * minOf(1f, (1f - p) * 5f) * blink) to 1f
        }
        t in EYES_2 until BURST_START -> (minOf(1f, progress(t, EYES_2, EYES_2 + 120) * 1f)) to 1.6f
        else -> 0f to 1f
    }

    /** Jak daleko jsou kusy keře rozlétnuté 0–1. */
    fun burst(t: Long): Float = when {
        t < BURST_START -> 0f
        t < BURST_PEAK -> MountainIntro.easeOutCubic(progress(t, BURST_START, BURST_PEAK))
        else -> 1f
    }

    /** Otřes obrazu v pixelech – při rozletu keře. */
    fun shakeAmplitude(t: Long): Float = when {
        t < BURST_START -> 0f
        t < BURST_START + 380 -> 2f * (1f - progress(t, BURST_START, BURST_START + 380))
        else -> 0f
    }

    /** Světelné paprsky mezi kmeny pomalu dýchají (0,5–1). */
    fun rays(t: Long): Float = 0.75f + 0.25f * sin(t * 0.0025).toFloat()

    /** Ptáci vylétající z korun po rozletu: 0 (ještě ne) … 1 (pryč ze záběru). */
    fun birds(t: Long): Float = progress(t, BURST_START + 60, END)
}

package cz.uhk.macroflow.pokemon.encounter

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Časová osa a generování scény pro intro setkání v Horách (čistý Kotlin, pokryto testy).
 * Kreslí MountainEncounterView; podklady v docs/adr/0010.
 */
object MountainIntro {

    // ── Časová osa (ms od startu) ──
    const val SKY_IN_END = 350L          // tma → soumrak
    const val BACK_RISE_START = 200L     // zadní hřeben vyjíždí zdola
    const val BACK_RISE_END = 850L
    const val FRONT_RISE_START = 320L    // přední hřeben, zem a balvan
    const val FRONT_RISE_END = 950L
    const val RUMBLE_START = 1000L       // otřesy, padající kamínky, prach
    const val CRACK_START = 1450L        // prasklina roste shora dolů
    const val SPLIT_START = 1750L        // balvan se rozpůlí, úlomky
    const val SPLIT_END = 2250L
    const val REVEAL_AT = 2000L          // záblesk a odhalení souboje (úlomky ještě letí)
    const val END = 2600L

    /** Průběh 0..1 v intervalu [start, end]. */
    fun progress(t: Long, start: Long, end: Long): Float =
        ((t - start).toFloat() / (end - start)).coerceIn(0f, 1f)

    fun easeOutCubic(p: Float): Float { val q = 1f - p; return 1f - q * q * q }
    fun easeInQuad(p: Float): Float = p * p

    /**
     * Síla otřesu (v pixelech nízkého rozlišení): mírné dunění, pak silný náraz při rozpůlení,
     * který rychle odezní.
     */
    fun shakeAmplitude(t: Long): Float = when {
        t < RUMBLE_START -> 0f
        t < SPLIT_START -> 1f
        t < SPLIT_START + 350 -> 3f * (1f - progress(t, SPLIT_START, SPLIT_START + 350))
        else -> 0f
    }

    /**
     * Hřeben hor metodou posouvání středního bodu (midpoint displacement): výšky 0..[amplitude]
     * pro každý sloupec 0..width-1. Deterministické podle [seed] – hory vypadají pokaždé stejně.
     * [roughness] < 1 tlumí náhodu s každým půlením (menší = hladší kopce).
     */
    fun ridge(seed: Int, width: Int, amplitude: Int, roughness: Float = 0.55f): IntArray {
        require(width >= 2 && amplitude > 0)
        val rnd = Random(seed)
        var n = 1
        while (n < width - 1) n *= 2
        val h = FloatArray(n + 1)
        h[0] = rnd.nextFloat() * 0.5f; h[n] = rnd.nextFloat() * 0.5f
        var step = n; var spread = 1f
        while (step > 1) {
            val half = step / 2
            var i = half
            while (i < n) {
                h[i] = (h[i - half] + h[i + half]) / 2f + (rnd.nextFloat() - 0.5f) * spread
                i += step
            }
            spread *= roughness
            step = half
        }
        val lo = h.min(); val hi = h.max()
        val range = (hi - lo).takeIf { it > 1e-6f } ?: 1f
        return IntArray(width) { x -> (((h[x * n / (width - 1)] - lo) / range) * amplitude).toInt() }
    }

    /** Svislá čára praskliny: x-posun vůči středu balvanu pro řádek y (klikatá, ±[amp] px). */
    fun crackOffset(y: Int, amp: Int = 3, seed: Int = 5): Int {
        val phase = sin(y * 0.55 + seed) + 0.6 * sin(y * 1.7 + seed * 2)
        return (phase / 1.6 * amp).toInt()
    }

    /** Parabolický odlet půlky balvanu: nahoru a pak dolů (v pixelech nízkého rozlišení). */
    fun halfLift(p: Float, height: Float): Float = -height * sin(PI * p * 0.85).toFloat() + height * 0.6f * p * p
}

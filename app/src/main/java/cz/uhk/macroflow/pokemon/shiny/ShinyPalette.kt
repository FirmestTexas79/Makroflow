package cz.uhk.macroflow.pokemon.shiny

import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

/**
 * Shiny Makromoni (čistý Kotlin, pokryto testy). Podklady v docs/adr/0009.
 *
 *  - Šance 1 : [ODDS] na každé divoké setkání (ne u vynuceného tutoriálového).
 *  - Barevný filtr: rotace odstínu v prostoru HSV o úhel daný druhem Makromona.
 *    Úhel je pro druh stálý (hash ID), takže shiny Ignar vypadá pokaždé stejně – jako u Pokémonů.
 *    Pixely bez barvy (obrys, bílá, šedá) rotace nemění, sprite si tak drží kresbu.
 */
object ShinyPalette {

    const val ODDS = 100

    /** Nejmenší a největší posun odstínu – pod 90° by shiny vypadal jen „trochu jinak“. */
    const val MIN_SHIFT = 90
    const val MAX_SHIFT = 270

    /** Mírné zvýraznění sytosti, ať nová barva „svítí“. */
    const val SATURATION_BOOST = 1.12f

    fun roll(random: Random = Random.Default, odds: Int = ODDS): Boolean = random.nextInt(odds) == 0

    /** Posun odstínu pro druh (ve stupních, [MIN_SHIFT]..[MAX_SHIFT]) – deterministický. */
    fun hueShiftFor(makrodexId: String): Int {
        // FNV-1a + fmix32: stabilní napříč verzemi JVM/Androidu
        var h = 0x811C9DC5.toInt()
        makrodexId.forEach { ch -> h = (h xor ch.code) * 0x01000193 }
        // Promíchání (fmix32 z MurmurHash3) – podobná ID „001“, „004“ by jinak dala podobné odstíny
        h = h xor (h ushr 16); h *= 0x85EBCA6B.toInt()
        h = h xor (h ushr 13); h *= 0xC2B2AE35.toInt()
        h = h xor (h ushr 16)
        val span = MAX_SHIFT - MIN_SHIFT + 1
        return MIN_SHIFT + ((h.toLong() and 0xFFFFFFFFL) % span).toInt()
    }

    /** Přebarví jeden pixel ARGB; průhlednost zůstává. */
    fun transform(argb: Int, shiftDeg: Int, satBoost: Float = SATURATION_BOOST): Int {
        val a = (argb ushr 24) and 0xFF
        if (a == 0) return argb
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f

        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        val delta = max - min
        if (delta < 1e-4f) return argb                       // šedá/černá/bílá – beze změny

        var h = when (max) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        if (h < 0) h += 360f
        val s = (delta / max * satBoost).coerceAtMost(1f)
        val v = max

        h = (h + shiftDeg) % 360f
        return (a shl 24) or hsvToRgb(h, s, v)
    }

    /** Přebarví celé pole pixelů (výstup z Bitmap.getPixels) na místě. */
    fun transformAll(pixels: IntArray, shiftDeg: Int) {
        for (i in pixels.indices) pixels[i] = transform(pixels[i], shiftDeg)
    }

    internal fun hsvToRgb(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val hp = h / 60f
        val x = c * (1 - abs(hp % 2f - 1))
        val (r1, g1, b1) = when (floor(hp).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
    }
}

package cz.uhk.macroflow.pokemon.cave

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * Sestup do Dolů (docs/adr/0049): letíme šikmou štolou hlouběji pod Starý důl. Kolem ubíhají
 * dřevěné výztuže, po zemi koleje, ve stěnách se blýskají fialové a rudé krystaly a praskliny
 * žhnou. Z hloubky roste rudý žár lávy a proti nám letí jiskry. Čistý Kotlin, pokryto testy.
 *
 * Obrazovka je zakrytá od prvního snímku; v [COVERED_AT] se pod ní vymění mapa a na konci
 * scéna ztmavne do žáru (překryv pak rozplyne CaveTransitionView).
 */
object MineDescent {
    const val COVERED_AT = 900L
    const val END = 1900L

    internal val ROCK = intArrayOf(0xFF0E0C16.toInt(), 0xFF1A1626.toInt(), 0xFF26203A.toInt(), 0xFF342C4C.toInt())
    internal val TIMBER = intArrayOf(0xFF3A2414.toInt(), 0xFF6C4420.toInt(), 0xFF93602C.toInt(), 0xFFC48A4A.toInt())
    internal const val RAIL = 0xFF9AA0B4.toInt()
    internal const val SLEEPER = 0xFF4A2E18.toInt()
    internal val CRYSTAL_V = intArrayOf(0xFF8250D2.toInt(), 0xFFBE96FF.toInt())
    internal val CRYSTAL_R = intArrayOf(0xFFC83240.toInt(), 0xFFFF8278.toInt())
    internal const val EMBER = 0xFFFFA83A.toInt()
    internal const val LAVA_GLOW = 0xFFFF5A1A.toInt()
    internal const val HEAT = 0xFFFFC060.toInt()
    internal const val VOID = 0xFF06040A.toInt()
}

class MineDescentScene(val w: Int, val h: Int) {
    private val cx = w / 2f
    private val cy = h * 0.46f
    private val unit = w * 0.5f
    /** Poměr výšky a šířky štoly. */
    private val aspect = 0.82f
    private val spacing = 1.6f

    /** Kolik štoly jsme už prolétli (zrychluje se, na konci zpomalí do sálu). */
    fun travelled(t: Long): Float {
        val s = t / 1000f
        return s * 3.2f + s * s * 1.6f
    }

    /** Síla žáru z hloubky 0..1 – s časem roste. */
    fun heat(t: Long): Float = (t.toFloat() / MineDescent.END).coerceIn(0f, 1f).let { it * it }

    fun render(t: Long, out: IntArray) {
        require(out.size >= w * h)
        val cam = travelled(t)
        val hot = heat(t)
        val endFade = CaveTransition.progress(t, MineDescent.END - 350, MineDescent.END)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val dx = (x + 0.5f - cx) / unit
                val dy = (y + 0.5f - cy) / unit
                val b = CaveTransition.BAYER[(y and 3) * 4 + (x and 3)]
                var c = shaft(dx, dy, cam, hot, b)
                if (endFade > 0f) c = CaveTransition.mix(c, CaveTransition.scale(MineDescent.LAVA_GLOW, 0.35f), endFade * 0.8f)
                out[y * w + x] = c
            }
        }
        embers(t, out)
    }

    private fun shaft(dx: Float, dy: Float, cam: Float, hot: Float, b: Float): Int {
        val ax = abs(dx); val ay = abs(dy) / aspect
        val m = max(ax, ay)
        // úplný střed = vzdálený žár
        if (m < 0.035f) return CaveTransition.mix(MineDescent.VOID, MineDescent.LAVA_GLOW, 0.35f + hot * 0.65f)
        val z = 1f / m                                     // hloubka stěny pod pixelem
        val d = z + cam                                    // poloha ve štole (roste do hloubky)
        val floorSide = dy > 0f && ay >= ax
        val ceil = dy < 0f && ay >= ax
        val u = if (ax >= ay) dy / (m * aspect) else dx / m // příčná souřadnice na ploše (-1..1)

        // dřevěná výztuž: rám přes celý profil štoly
        val ring = d / spacing - floor(d / spacing)
        var c: Int
        if (ring < 0.14f) {
            val lvl = if (ring < 0.04f) 3 else if (ring < 0.1f) 2 else 1
            c = MineDescent.TIMBER[if (floorSide) 1 else lvl]
        } else if (floorSide) {
            // koleje a pražce
            val sleeper = (d * 2.5f) - floor(d * 2.5f) < 0.32f
            c = when {
                abs(abs(u) - 0.34f) < 0.035f -> MineDescent.RAIL
                sleeper && abs(u) < 0.5f -> MineDescent.SLEEPER
                else -> MineDescent.ROCK[(1 + (CaveTransition.hash(floor(d * 3).toInt(), floor(u * 6).toInt()) * 1.6f + b).toInt()).coerceIn(0, 3)]
            }
        } else if (ceil) {
            // strop z prken mezi rámy
            val plank = (u * 5f) - floor(u * 5f) < 0.12f
            c = if (plank) MineDescent.ROCK[0] else CaveTransition.scale(MineDescent.TIMBER[(CaveTransition.hash(floor(u * 5).toInt(), floor(d).toInt()) * 1.4f + b).toInt().coerceIn(0, 1)], 0.8f)
        } else {
            // stěny: hrubá skála, krystaly a žhnoucí praskliny
            val row = floor(d * 3f).toInt(); val col = floor(u * 7f).toInt()
            val hsh = CaveTransition.hash(row, col)
            c = MineDescent.ROCK[(1 + (hsh * 2.2f + b).toInt()).coerceIn(0, 3)]
            val gem = CaveTransition.hash(row * 7 + 3, col * 5 + (if (dx < 0) 1 else 2))
            if (gem > 0.955f) c = (if (gem > 0.978f) MineDescent.CRYSTAL_V else MineDescent.CRYSTAL_R)[if (hsh > 0.5f) 1 else 0]
            else if (abs(sin(d * 5f + u * 9f)) < 0.05f && CaveTransition.hash(row, 91) > 0.55f)
                c = CaveTransition.mix(MineDescent.LAVA_GLOW, MineDescent.HEAT, hot)
        }
        // mlha hloubky, do ní prosvítá žár
        val light = exp(-(z - 1f) * 0.28f)
        val far = CaveTransition.mix(MineDescent.VOID, MineDescent.LAVA_GLOW, hot * (1f - light) * 0.8f)
        return CaveTransition.mix(far, c, light)
    }

    /** Jiskry letí z hloubky proti nám (ven od středu, rychlejší u okraje). */
    private fun embers(t: Long, out: IntArray) {
        val n = 26
        for (i in 0 until n) {
            val period = 900 + (CaveTransition.hash(i, 31) * 900).toInt()
            val ph = ((t + (CaveTransition.hash(i, 32) * period).toLong()) % period) / period.toFloat()
            val ang = CaveTransition.hash(i, 33) * 6.283f
            val r = unit * (0.04f + ph * ph * 1.3f)
            val x = (cx + kotlin.math.cos(ang) * r).toInt()
            val y = (cy + sin(ang) * r * aspect).toInt()
            if (x !in 0 until w || y !in 0 until h) continue
            val a = (0.35f + ph * 0.65f) * (0.4f + heat(t) * 0.6f)
            out[y * w + x] = CaveTransition.mix(out[y * w + x], MineDescent.EMBER, a)
            if (ph > 0.6f) {
                val x2 = (cx + kotlin.math.cos(ang) * (r - unit * 0.03f)).toInt()
                val y2 = (cy + sin(ang) * (r - unit * 0.03f) * aspect).toInt()
                if (x2 in 0 until w && y2 in 0 until h) out[y2 * w + x2] = CaveTransition.mix(out[y2 * w + x2], MineDescent.EMBER, a * 0.5f)
            }
        }
    }
}

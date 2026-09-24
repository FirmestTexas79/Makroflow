package cz.uhk.macroflow.pokemon.cave

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/**
 * Přechod do jeskyně a z ní (čistý Kotlin, pokryto testy). Kreslí CaveTransitionView;
 * podklady v docs/adr/0013.
 *
 * Vstup: blížíme se k ústí v tmavě modré skále obrostlé mechem, oblouk roste, až zaplní
 * obrazovku, a pak letíme tunelem (soustředné prstence kamene a mechu, svítící spory).
 * Pod plnou tmou se vymění mapa a tma se rozplyne do jeskyně.
 * Výstup: zevnitř jeskyně se blížíme k teplému dennímu světlu, které nakonec vše přezáří.
 */
object CaveTransition {
    const val APPROACH_END = 1000L     // ústí dorostlo přes celou obrazovku
    const val COVERED_AT = 1000L       // od teď je vidět jen vnitřek → bezpečná výměna mapy
    const val END = 1650L              // konec scény; pak se překryv rozplyne

    fun progress(t: Long, start: Long, end: Long): Float =
        ((t - start).toFloat() / (end - start)).coerceIn(0f, 1f)

    // Paleta: tmavě modrý kámen, mech, spory, denní světlo
    internal val ROCK = intArrayOf(0xFF0C1022.toInt(), 0xFF161E38.toInt(), 0xFF202C50.toInt(), 0xFF2C3C68.toInt(), 0xFF3C5084.toInt())
    internal val RIM = intArrayOf(0xFF1A2442.toInt(), 0xFF2E3E6A.toInt(), 0xFF46598E.toInt())
    internal val MOSS = intArrayOf(0xFF1E4A3E.toInt(), 0xFF2A624C.toInt(), 0xFF3E805A.toInt(), 0xFF68AA6A.toInt())
    internal const val VOID = 0xFF04060E.toInt()
    internal const val SPORE = 0xFF8CF0D2.toInt()
    internal const val WARM = 0xFFE8C48C.toInt()
    internal const val DAY = 0xFFFFF4DC.toInt()

    internal val BAYER = floatArrayOf(0f, 8f, 2f, 10f, 12f, 4f, 14f, 6f, 3f, 11f, 1f, 9f, 15f, 7f, 13f, 5f)
        .map { it / 16f - 0.5f }.toFloatArray()

    /** Deterministický „náhodný“ šum 0..1 pro dvojici celých čísel. */
    internal fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777216f
    }

    internal fun mix(c1: Int, c2: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(s: Int) = (((c1 shr s) and 0xFF) * (1 - k) + ((c2 shr s) and 0xFF) * k).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    internal fun scale(c: Int, f: Float): Int {
        fun ch(s: Int) = (((c shr s) and 0xFF) * f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

class CaveTransitionScene(val w: Int, val h: Int, val exiting: Boolean) {

    private val cx = w / 2f
    private val cy = h * 0.56f
    private val r0 = w * 0.30f
    private val rMax = hypot(w.toFloat(), h.toFloat()) * 1.35f

    /** Poloměr ústí v pixelech scény – roste exponenciálně (dojem stálé rychlosti chůze). */
    fun archRadius(t: Long): Float {
        val p = CaveTransition.progress(t, 0, CaveTransition.APPROACH_END)
        return r0 * (rMax / r0).pow(p.pow(1.6f))
    }

    /** Ústí pokrývá celou obrazovku (všechny rohy jsou uvnitř oblouku). */
    fun isCovered(t: Long): Boolean {
        val r = archRadius(t)
        return listOf(0f to 0f, w.toFloat() to 0f, 0f to h.toFloat(), w.toFloat() to h.toFloat())
            .all { (x, y) -> archMetric((x - cx) / r, (y - cy) / r) <= 1f }
    }

    /** 0 = střed ústí, 1 = hrana oblouku (půlkruh nahoře, svislé stěny dole). */
    private fun archMetric(dx: Float, dy: Float): Float = if (dy >= 0f) abs(dx) else hypot(dx, dy)

    fun render(t: Long, out: IntArray) {
        require(out.size >= w * h)
        val r = archRadius(t)
        val phase = t / 1000f * 1.7f
        val fadeEnd = CaveTransition.progress(t, CaveTransition.COVERED_AT, CaveTransition.END)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val dx = (x + 0.5f - cx) / r
                val dy = (y + 0.5f - cy) / r
                val m = archMetric(dx, dy)
                val b = CaveTransition.BAYER[(y and 3) * 4 + (x and 3)]
                var c = if (m > 1f) rock(dx, dy, m - 1f, b) else inside(dx, dy, m, b, phase)
                c = if (exiting) CaveTransition.mix(c, CaveTransition.DAY, fadeEnd * fadeEnd)
                    else CaveTransition.scale(c, 1f - 0.45f * fadeEnd)
                out[y * w + x] = c
            }
        }
        particles(t, out)
    }

    // ── Skála kolem ústí: kvádry s maltou, lem oblouku, mech ──
    private fun rock(dx: Float, dy: Float, e: Float, b: Float): Int {
        // lem oblouku z klenáků
        if (e < 0.16f) {
            val seg = if (dy < 0f) floor((kotlin.math.atan2(dy, dx) + Math.PI.toFloat()) / 0.26f).toInt()
                else 100 + floor(dy / 0.34f).toInt() * (if (dx < 0) 1 else -1)
            val joint = if (dy < 0f) {
                val a = (kotlin.math.atan2(dy, dx) + Math.PI.toFloat()) / 0.26f
                a - floor(a) < 0.1f
            } else (dy / 0.34f) - floor(dy / 0.34f) < 0.1f
            if (joint || e > 0.14f) return CaveTransition.RIM[0]
            val lvl = if (e < 0.04f) 2 else 1
            val mossy = dy < 0.1f && CaveTransition.hash(seg, 7) > 0.35f && e < 0.08f + CaveTransition.hash(seg, 9) * 0.06f
            return if (mossy) CaveTransition.MOSS[if (e < 0.03f) 3 else 2] else CaveTransition.RIM[lvl]
        }
        // Přírodní skála, ne zeď: řady různě vysoké, kameny různě široké a posunuté
        val v = dy * 7f + sin(dx * 5f) * 0.35f
        val row = floor(v).toInt()
        val u = dx * 7f * (0.7f + CaveTransition.hash(row, 2) * 0.7f)
        val uu = u + CaveTransition.hash(row, 1) + sin(dy * 9f) * 0.2f
        val col = floor(uu).toInt()
        val fu = uu - col
        val fv = v - row
        if (fu < 0.07f || fv < 0.1f) return CaveTransition.ROCK[0]           // spáry
        var lvl = 2 + (CaveTransition.hash(col, row) * 1.8f + b * 0.6f).toInt()
        if (fv < 0.26f) lvl++                                                  // horní hrana kvádru
        if (fu > 0.86f || fv > 0.88f) lvl--                                    // stín vpravo dole
        lvl -= (e * 1.4f).toInt()                                              // dál od ústí tmavší
        var c = CaveTransition.ROCK[lvl.coerceIn(1, 4)]
        // mech na horních plochách kvádrů, víc blízko ústí a nahoře
        val mossChance = 0.75f - e * 0.55f - (dy.coerceAtLeast(0f)) * 0.6f
        if (fv in 0.1f..0.42f && CaveTransition.hash(col * 3, row * 5) < mossChance) {
            c = CaveTransition.MOSS[if (fv < 0.2f) 2 else if (b > 0.1f) 1 else 0]
        }
        if (exiting && e < 0.5f) c = CaveTransition.mix(c, CaveTransition.WARM, (0.5f - e) * 0.5f)   // světlo zvenku
        return c
    }

    // ── Vnitřek: vstup = tunel, výstup = denní světlo ──
    private fun inside(dx: Float, dy: Float, m: Float, b: Float, phase: Float): Int {
        // mech visící z klenby do ústí
        if (dy < 0f && m > 0.72f) {
            val colIdx = floor(dx * 26f).toInt()
            val len = CaveTransition.hash(colIdx, 3) * 0.26f
            if (1f - m < len) return CaveTransition.MOSS[if (1f - m > len - 0.03f) 3 else 1]
        }
        if (exiting) {
            val ray = (sin(kotlin.math.atan2(dy, dx) * 9f + phase * 0.6f) + 1f) * 0.5f
            return CaveTransition.mix(CaveTransition.DAY, CaveTransition.WARM, m * 0.85f - ray * 0.15f + b * 0.12f)
        }
        if (m < 0.02f) return CaveTransition.VOID
        // tunel: soustředné prstence, každý o kus dál a tmavší
        val d = ln(m) / ln(0.62f)                                              // 0 u ústí, roste do hloubky
        val band = floor(d + phase).toInt()
        val fb = d + phase - band
        val light = kotlin.math.exp(-d * 0.62f)
        if (fb < 0.12f) return CaveTransition.scale(CaveTransition.ROCK[0], light)
        val seg = floor((kotlin.math.atan2(dy, dx) + Math.PI.toFloat()) / 0.5f).toInt()
        var c = CaveTransition.ROCK[(2 + (CaveTransition.hash(band, seg) * 2f + b).toInt()).coerceIn(1, 4)]
        if (dy < 0.15f && fb < 0.5f && CaveTransition.hash(band * 7, seg) > 0.45f) {
            c = CaveTransition.MOSS[if (fb < 0.22f) 2 else 1]
        }
        return CaveTransition.mix(CaveTransition.VOID, c, light)
    }

    // ── Svítící spory (vstup) / prach ve světle (výstup) ──
    private fun particles(t: Long, out: IntArray) {
        val n = if (exiting) 22 else 34
        for (i in 0 until n) {
            val speed = 5f + CaveTransition.hash(i, 21) * 12f
            val x0 = CaveTransition.hash(i, 11) * w
            val y0 = CaveTransition.hash(i, 13) * h
            var y = (y0 - t / 1000f * speed) % h
            if (y < 0) y += h
            val x = x0 + sin(t / 650f + i * 1.3f) * 2.5f
            val tw = (sin(t / 280f + i * 1.7f) + 1f) * 0.5f
            val xi = x.toInt(); val yi = y.toInt()
            if (xi !in 0 until w || yi !in 0 until h) continue
            val col = if (exiting) CaveTransition.WARM else CaveTransition.SPORE
            val a = if (exiting) 0.35f * tw else 0.35f + 0.6f * tw
            out[yi * w + xi] = CaveTransition.mix(out[yi * w + xi], col, a)
            if (!exiting && tw > 0.8f) for ((ox, oy) in NEIGHBORS) {           // jasná spora září do kříže
                val xx = xi + ox; val yy = yi + oy
                if (xx in 0 until w && yy in 0 until h) out[yy * w + xx] = CaveTransition.mix(out[yy * w + xx], col, 0.35f)
            }
        }
    }

    private companion object {
        val NEIGHBORS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    }
}

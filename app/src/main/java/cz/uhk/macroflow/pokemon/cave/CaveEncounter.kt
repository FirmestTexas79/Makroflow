package cz.uhk.macroflow.pokemon.cave

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Intro setkání v jeskyni (čistý Kotlin, pokryto testy). Kreslí CaveEncounterView; docs/adr/0013.
 *
 * Tmavá síň s krápníky a mechem se rozsvítí svítícími houbami, z temné prohlubně uprostřed
 * vyletí vyplašení netopýři, ve tmě se otevřou dvě zářící oči, mrknou a vyrazí proti hráči
 * (otřes) → záblesk a souboj.
 */
object CaveEncounter {
    const val FADE_IN_END = 380L
    const val BATS_START = 260L
    const val BATS_END = 1150L
    const val EYES_OPEN = 1150L
    const val BLINK_AT = 1420L
    const val LUNGE_START = 1600L
    const val REVEAL_AT = 1900L
    const val END = 2300L

    fun progress(t: Long, start: Long, end: Long): Float =
        ((t - start).toFloat() / (end - start)).coerceIn(0f, 1f)

    /** Otřes v pixelech scény: jen při výpadu očí. */
    fun shake(t: Long): Int = if (t in LUNGE_START until REVEAL_AT + 150) (if ((t / 40) % 2 == 0L) 1 else -1) * 2 else 0

    /** Otevření očí 0..1 (0 = zavřené), včetně jednoho mrknutí. */
    fun eyeOpen(t: Long): Float = when {
        t < EYES_OPEN -> 0f
        t < BLINK_AT -> progress(t, EYES_OPEN, EYES_OPEN + 160)
        t < BLINK_AT + 70 -> 1f - progress(t, BLINK_AT, BLINK_AT + 70)
        else -> progress(t, BLINK_AT + 70, BLINK_AT + 150)
    }

    internal const val BAT = 0xFF5E5078.toInt()
    internal const val BAT_EDGE = 0xFF2A2238.toInt()
    internal const val BAT_EYE = 0xFFFF5A64.toInt()
    internal const val EYE = 0xFFFFF4B0.toInt()
    internal const val EYE_GLOW = 0xFFFFC04A.toInt()
    internal const val DRIP = 0xFF9CE6FF.toInt()
    internal const val CAP = 0xFF56D8CE.toInt()
    internal const val STEM = 0xFFB0BACC.toInt()
}

class CaveEncounterScene(val w: Int, val h: Int) {

    private val bg = IntArray(w * h)
    private val cx = w / 2f
    private val cy = h * 0.47f
    private val holeRx = w * 0.30f
    private val holeRy = h * 0.17f
    private val stalactites: List<Triple<Int, Int, Int>>   // x, délka, šířka
    private val mushrooms: List<Pair<Int, Int>>

    init {
        val rock = CaveTransition.ROCK
        val moss = CaveTransition.MOSS
        val floorTop = (h * 0.8f).toInt()
        // krápníky ze stropu a ze dna
        val st = ArrayList<Triple<Int, Int, Int>>()
        var x = 2
        var i = 0
        while (x < w - 2) {
            val len = (h * (0.08f + CaveTransition.hash(i, 5) * 0.24f)).toInt()
            val wid = 2 + (CaveTransition.hash(i, 6) * 4).toInt()
            st += Triple(x, len, wid)
            x += wid * 2 + 2 + (CaveTransition.hash(i, 7) * 8).toInt()
            i++
        }
        stalactites = st
        mushrooms = (0 until 6).map { k ->
            val mx = (w * (0.08f + k * 0.17f + CaveTransition.hash(k, 30) * 0.06f)).toInt().coerceIn(3, w - 4)
            mx to floorTop + 2 + (CaveTransition.hash(k, 31) * (h - floorTop - 6)).toInt()
        }

        for (y in 0 until h) for (xx in 0 until w) {
            val b = CaveTransition.BAYER[(y and 3) * 4 + (xx and 3)]
            // stěna: vodorovné vrstvy horniny, zvlněné
            val layer = y / (h / 11f) + sin(xx * 0.09f) * 0.35f + sin(xx * 0.23f + y * 0.03f) * 0.12f
            val band = layer - kotlin.math.floor(layer)
            var lvl = 1 + (CaveTransition.hash(layer.toInt(), xx / 7) * 1.6f + b * 0.7f).toInt()
            if (band < 0.12f) lvl = 0 else if (band < 0.3f) lvl++
            var c = rock[lvl.coerceIn(0, 4)]
            // mech na horních hranách vrstev
            if (band in 0.12f..0.3f && CaveTransition.hash(layer.toInt() * 3, xx / 3) > 0.55f) c = moss[if (b > 0f) 2 else 1]
            if (y >= floorTop) {                                               // dno síně
                val fy = (y - floorTop).toFloat() / (h - floorTop)
                c = CaveTransition.mix(rock[3], rock[4], fy + b * 0.3f)
                if (CaveTransition.hash(xx / 2, y / 2) > 0.86f - fy * 0.1f) c = moss[if (fy > 0.5f) 2 else 1]
                if ((xx - cx.toInt()) * 3 % (h - floorTop + y) == 0 && fy > 0.1f) c = rock[2]   // spáry dna v perspektivě
                if (y == floorTop) c = rock[0]
            }
            // temná prohlubeň uprostřed, odkud se to vyřítí
            val d = hypot((xx - cx) / holeRx, (y - cy) / holeRy)
            if (d < 1.25f) c = CaveTransition.mix(c, CaveTransition.VOID, ((1.25f - d) / 0.6f + b * 0.25f).coerceIn(0f, 1f))
            bg[y * w + xx] = c
        }
        for ((sx, len, wid) in stalactites) {                                    // krápníky
            for (k in 0 until len) {
                val half = (wid * (1f - k.toFloat() / len)).toInt()
                for (dx in -half..half) {
                    val px = sx + dx
                    if (px !in 0 until w) continue
                    bg[k * w + px] = if (abs(dx) == half && half > 0) rock[0] else if (dx < 0) rock[4] else if (k < 3) moss[2] else rock[3]
                    val yb = floorTop - 1 - k / 2                                 // menší stalagmit dole
                    if (k < len / 2 && yb in 0 until h && abs(dx) <= half / 2) bg[yb * w + px] = if (dx < 0) rock[4] else rock[2]
                }
            }
        }
        for ((mx, my) in mushrooms) {                                            // svítící houby + světlo
            for (y in (my - 14).coerceAtLeast(0) until (my + 8).coerceAtMost(h)) for (xx in (mx - 16).coerceAtLeast(0) until (mx + 16).coerceAtMost(w)) {
                val g = 1f - hypot((xx - mx) / 16f, (y - my + 3) / 12f)
                if (g > 0f) bg[y * w + xx] = CaveTransition.mix(bg[y * w + xx], CaveEncounter.CAP, g * 0.28f)
            }
            fun p(x: Int, y: Int, c: Int) { if (x in 0 until w && y in 0 until h) bg[y * w + x] = c }
            p(mx, my, CaveEncounter.STEM); p(mx, my - 1, CaveEncounter.STEM)
            for (dx in -2..2) p(mx + dx, my - 2, CaveEncounter.CAP)
            for (dx in -1..1) p(mx + dx, my - 3, CaveEncounter.CAP)
            p(mx, my - 3, 0xFFD4FFF8.toInt())
        }
    }

    fun render(t: Long, out: IntArray) {
        require(out.size >= w * h)
        val light = 0.15f + 0.85f * CaveEncounter.progress(t, 0, CaveEncounter.FADE_IN_END)
        val sh = CaveEncounter.shake(t)
        // při výpadu prohlubeň pohltí okolí (tunelové vidění)
        val tunnel = CaveEncounter.progress(t, CaveEncounter.LUNGE_START, CaveEncounter.REVEAL_AT)
        for (y in 0 until h) for (x in 0 until w) {
            val sx = (x + sh).coerceIn(0, w - 1)
            var c = CaveTransition.scale(bg[y * w + sx], light)
            if (tunnel > 0f) {
                val d = hypot((x - cx) / (w * 0.7f), (y - cy) / (h * 0.55f))
                c = CaveTransition.mix(c, CaveTransition.VOID, ((d - 1f + tunnel) * 2f).coerceIn(0f, 0.85f))
            }
            out[y * w + x] = c
        }
        drips(t, out)
        bats(t, out)
        eyes(t, out, sh)
    }

    private fun put(out: IntArray, x: Int, y: Int, c: Int) { if (x in 0 until w && y in 0 until h) out[y * w + x] = c }

    private fun drips(t: Long, out: IntArray) {
        stalactites.forEachIndexed { i, (sx, len, _) ->
            if (i % 3 != 0) return@forEachIndexed
            val period = 700 + (CaveTransition.hash(i, 40) * 600).toInt()
            val ph = ((t + i * 131) % period) / period.toFloat()
            val y = len + (ph * ph * (h * 0.8f - len)).toInt()
            put(out, sx, y, CaveEncounter.DRIP); put(out, sx, y - 1, CaveTransition.mix(out[(y - 1).coerceIn(0, h - 1) * w + sx], CaveEncounter.DRIP, 0.5f))
        }
    }

    /** Netopýři vylétají z prohlubně ven a zvětšují se (letí k hráči). */
    private fun bats(t: Long, out: IntArray) {
        if (t < CaveEncounter.BATS_START || t > CaveEncounter.BATS_END + 200) return
        for (i in 0 until 8) {
            val start = CaveEncounter.BATS_START + (i * 90L)
            val p = CaveEncounter.progress(t, start, start + 700)
            if (p <= 0f || p >= 1f) continue
            val ang = (i * 2.4f + CaveTransition.hash(i, 50)) % 6.283f
            val dist = p * p * hypot(w.toFloat(), h.toFloat()) * 0.6f
            val x = cx + cos(ang) * dist + sin(p * 12f + i) * 3f
            val y = cy + sin(ang) * dist * 0.7f - p * 10f
            val size = 1 + (p * 3f).toInt()
            val wingsUp = ((t / 70) + i) % 2 == 0L
            drawBat(out, x.toInt(), y.toInt(), size, wingsUp)
        }
    }

    private fun drawBat(out: IntArray, x: Int, y: Int, s: Int, up: Boolean) {
        // tělo
        for (dy in 0 until s + 1) for (dx in 0 until s) put(out, x + dx - s / 2, y + dy, CaveEncounter.BAT)
        // křídla: dva lomené pruhy do stran
        for (k in 1..s * 3) {
            val wy = if (up) -(k / 2).coerceAtMost(s + 1) + (if (k > s * 2) (k - s * 2) else 0)
                     else (k / 3)
            for (th in 0 until s.coerceAtLeast(1)) {
                put(out, x - s / 2 - k, y + wy + th, if (th == 0) CaveEncounter.BAT else CaveEncounter.BAT_EDGE)
                put(out, x + s - s / 2 - 1 + k, y + wy + th, if (th == 0) CaveEncounter.BAT else CaveEncounter.BAT_EDGE)
            }
        }
        put(out, x - s / 2, y, CaveEncounter.BAT_EYE)
        if (s > 1) put(out, x + s - s / 2 - 1, y, CaveEncounter.BAT_EYE)
    }

    /** Zářící oči ve tmě; při výpadu se zvětšují a rozestupují. */
    private fun eyes(t: Long, out: IntArray, sh: Int) {
        val open = CaveEncounter.eyeOpen(t)
        if (open <= 0f) return
        val lunge = CaveEncounter.progress(t, CaveEncounter.LUNGE_START, CaveEncounter.REVEAL_AT)
        val k = 1f + lunge * lunge * 3.5f
        val gap = w * 0.09f * k
        val ew = (3 * k).toInt().coerceAtLeast(2)
        val eh = ((2 * k) * open).toInt().coerceAtLeast(if (open > 0.3f) 1 else 0)
        if (eh == 0) return
        for (side in listOf(-1, 1)) {
            val ex = (cx + side * gap / 2 - sh).toInt()
            val ey = cy.toInt()
            // záře
            val gr = (ew * 2.5f).toInt()
            for (y in ey - gr..ey + gr) for (x in ex - gr..ex + gr) {
                if (x !in 0 until w || y !in 0 until h) continue
                val g = 1f - hypot((x - ex).toFloat(), (y - ey) * 1.6f) / gr
                if (g > 0f) out[y * w + x] = CaveTransition.mix(out[y * w + x], CaveEncounter.EYE_GLOW, g * 0.45f * open)
            }
            // šikmé zlé oko: vnitřní roh níž
            for (dx in -ew / 2..ew / 2) {
                val slant = if (side < 0) (dx + ew / 2) / 2 else (ew / 2 - dx) / 2
                for (dy in 0 until eh) put(out, ex + dx, ey - eh / 2 + dy + slant / 2, CaveEncounter.EYE)
            }
        }
    }
}

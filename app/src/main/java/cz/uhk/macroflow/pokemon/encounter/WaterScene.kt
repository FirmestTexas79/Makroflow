package cz.uhk.macroflow.pokemon.encounter

import cz.uhk.macroflow.pokemon.encounter.MountainScene.Companion.mix
import cz.uhk.macroflow.pokemon.encounter.MountainScene.Companion.rgb
import cz.uhk.macroflow.pokemon.encounter.WaterIntro.BURST_START
import cz.uhk.macroflow.pokemon.encounter.WaterIntro.FADE_IN_END
import cz.uhk.macroflow.pokemon.encounter.WaterIntro.progress
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Intro setkání u vody jako pixel art v nízkém rozlišení (čistý Kotlin, bez Androidu).
 * Stejně jako [MountainScene] je každý snímek čistá funkce času – jde přeskočit, testovat
 * a vyrenderovat mimo telefon (tools/encounter/WaterPreview.kt).
 */
class WaterScene(val w: Int, val h: Int, seed: Int = 7) : PixelScene {

    // ── Paleta (soumrak nad rybníkem, barvy ladí s mapou louky) ──
    private val sky = intArrayOf(rgb(24, 30, 62), rgb(40, 52, 96), rgb(70, 86, 128), rgb(128, 110, 140), rgb(214, 140, 118), rgb(246, 190, 130))
    private val sunCore = rgb(255, 228, 170); private val sunGlow = rgb(250, 196, 136)
    private val treesFar = rgb(46, 66, 70); private val treesNear = rgb(28, 44, 38)
    private val waterTop = rgb(74, 118, 140); private val waterDeep = rgb(14, 42, 66)
    private val shimmer = rgb(150, 206, 222); private val warmShimmer = rgb(248, 196, 140)
    private val reedDark = rgb(38, 64, 36); private val reedLight = rgb(74, 108, 52); private val cattail = rgb(104, 64, 36)
    private val pad = rgb(62, 118, 58); private val padLight = rgb(96, 150, 72); private val lotus = rgb(244, 170, 196)
    private val bobberRed = rgb(214, 58, 48); private val bobberWhite = rgb(244, 240, 228); private val line = rgb(190, 196, 200)
    private val foam = rgb(236, 248, 252); private val spray = rgb(170, 222, 240)
    private val shadowCol = rgb(6, 20, 32)

    // ── Rozvržení ──
    val horizon = (h * 0.34f).toInt()
    val bobberX = w / 2
    val bobberY = (h * 0.60f).toInt()
    private val maxRipple = w * 0.42f

    private val skyLayer = IntArray(w * h)
    private val frame = IntArray(w * h)
    private val farRidge = MountainIntro.ridge(seed + 11, w, (h * 0.05f).toInt().coerceAtLeast(4), 0.7f)
    private val nearRidge = MountainIntro.ridge(seed + 23, w, (h * 0.035f).toInt().coerceAtLeast(3), 0.8f)

    private class Streak(val y: Int, val phase: Float, val len: Int, val speed: Float, val warm: Boolean)
    private class Reed(val x: Int, val height: Int, val tail: Boolean, val light: Boolean, val idx: Int)
    private class Drop(val vx: Float, val vy: Float, val t0: Long, val size: Int, val white: Boolean)

    private val streaks = mutableListOf<Streak>()
    private val reeds = mutableListOf<Reed>()
    private val drops = mutableListOf<Drop>()

    init {
        val rnd = Random(seed)
        buildSky()
        for (y in horizon + 2 until h step 2) {
            val depth = (y - horizon).toFloat() / (h - horizon)
            repeat(1 + rnd.nextInt(2)) {
                streaks += Streak(y, rnd.nextFloat() * (w + 20), 2 + (depth * 7).toInt() + rnd.nextInt(3),
                    (0.004f + rnd.nextFloat() * 0.006f) * (0.5f + depth), warm = depth < 0.25f && rnd.nextInt(3) > 0)
            }
        }
        // rákosí vlevo a vpravo dole
        var i = 0
        for (side in 0..1) repeat(9) {
            val x = if (side == 0) rnd.nextInt((w * 0.30f).toInt()) else w - 1 - rnd.nextInt((w * 0.30f).toInt())
            reeds += Reed(x, (h * (0.16f + rnd.nextFloat() * 0.16f)).toInt(), rnd.nextInt(3) == 0, rnd.nextBoolean(), i++)
        }
        // kapky vodního sloupu
        repeat(90) {
            val a = -PI / 2 + (rnd.nextDouble() - 0.5) * PI * 0.9
            val v = 45f + rnd.nextFloat() * 110f
            drops += Drop((cos(a) * v).toFloat(), (sin(a) * v).toFloat() - 30f, BURST_START + rnd.nextLong(240),
                1 + rnd.nextInt(2), rnd.nextInt(3) > 0)
        }
    }

    private fun buildSky() {
        for (y in 0 until horizon) {
            val p = y.toFloat() / horizon
            val f = p * (sky.size - 1)
            val c = mix(sky[f.toInt().coerceAtMost(sky.size - 2)], sky[(f.toInt() + 1).coerceAtMost(sky.size - 1)], f - f.toInt())
            for (x in 0 until w) skyLayer[y * w + x] = c
        }
    }

    override val width get() = w
    override val height get() = h

    override fun render(t: Long, out: IntArray) {
        skyLayer.copyInto(frame)
        drawSun()
        drawTreeline()
        drawWater(t)
        drawPads(t)
        drawRipples(t)
        drawShadow(t)
        drawLineAndBobber(t)
        drawColumn(t)
        drawReeds(t)

        // otřes a roztmívání
        val shake = WaterIntro.shakeAmplitude(t)
        val dx = if (shake > 0f) ((if ((t / 40) % 2 == 0L) 1 else -1) * shake).roundToInt() else 0
        val fade = progress(t, 0, FADE_IN_END)
        val black = rgb(0, 0, 0)
        for (y in 0 until h) for (x in 0 until w) {
            val sx = (x - dx).coerceIn(0, w - 1)
            val c = frame[y * w + sx]
            out[y * w + x] = if (fade < 1f) mix(black, c, fade) else c
        }
    }

    private fun drawSun() {
        val cx = w * 0.70f; val cy = horizon - h * 0.025f
        circle(cx, cy, w * 0.13f, sunGlow, 0.25f)
        circle(cx, cy, w * 0.085f, sunGlow, 0.5f)
        circle(cx, cy, w * 0.055f, sunCore, 1f)
    }

    private fun drawTreeline() {
        for (x in 0 until w) {
            val far = farRidge[x] + 2
            for (y in horizon - far until horizon) put(x, y, treesFar)
            val near = nearRidge[x] + 1
            for (y in horizon - near until horizon + 1) put(x, y, treesNear)
        }
    }

    private fun drawWater(t: Long) {
        for (y in horizon + 1 until h) {
            val p = (y - horizon).toFloat() / (h - horizon)
            val c = mix(waterTop, waterDeep, minOf(1f, p * 1.3f))
            for (x in 0 until w) frame[y * w + x] = c
        }
        // odraz slunce – teplý pruh pod sluncem
        val sx = (w * 0.70f).toInt()
        for (y in horizon + 1 until horizon + (h * 0.14f).toInt()) {
            val p = (y - horizon).toFloat() / (h * 0.14f)
            val half = (w * 0.05f * (1f - p * 0.5f)).toInt()
            val wob = (sin(t * 0.01 + y * 0.9) * 1.5).toInt()
            if ((y + (t / 120).toInt()) % 2 == 0) for (x in sx - half + wob..sx + half + wob) put(x, y, mix(frame[y * w + x.coerceIn(0, w - 1)], warmShimmer, 0.55f * (1f - p)))
        }
        // třpyt na hladině
        for (s in streaks) {
            val x0 = (((s.phase + t * s.speed * 10f) % (w + 20)) - 10).toInt()
            val col = if (s.warm) warmShimmer else shimmer
            val a = if (s.warm) 0.55f else 0.35f + 0.25f * sin(t * 0.008 + s.phase).toFloat()
            for (x in x0 until x0 + s.len) if (x in 0 until w) frame[s.y * w + x] = mix(frame[s.y * w + x], col, a)
        }
    }

    private fun drawPads(t: Long) {
        fun lily(cx: Float, cy: Float, r: Float, flower: Boolean) {
            val bob = sin(t * 0.004 + cx).toFloat() * 0.4f
            ellipse(cx, cy + bob, r, r * 0.42f, pad, 1f)
            ellipse(cx - r * 0.25f, cy + bob - r * 0.08f, r * 0.5f, r * 0.18f, padLight, 0.8f)
            // zářez
            for (k in 0..(r * 0.6f).toInt()) put((cx + k).toInt(), (cy + bob).toInt(), mix(frame[((cy + bob).toInt().coerceIn(0, h - 1)) * w + (cx + k).toInt().coerceIn(0, w - 1)], waterDeep, 0.6f))
            if (flower) {
                circle(cx - r * 0.1f, cy + bob - r * 0.3f, 2f, lotus, 1f)
                put((cx - r * 0.1f).toInt(), (cy + bob - r * 0.3f - 1).toInt(), rgb(255, 230, 236))
            }
        }
        lily(w * 0.40f, h * 0.84f, w * 0.10f, true)
        lily(w * 0.76f, h * 0.70f, w * 0.07f, false)
        lily(w * 0.62f, h * 0.88f, w * 0.06f, false)
    }

    private fun drawRipples(t: Long) {
        for ((p, a) in WaterIntro.ripples(t)) {
            val rx = 3f + p * maxRipple
            ring(bobberX.toFloat(), bobberY + 2f, rx, rx * 0.32f, shimmer, 0.7f * a)
        }
    }

    private fun drawShadow(t: Long) {
        val (dx, dy, a) = WaterIntro.shadow(t)
        if (a <= 0f) return
        val cx = bobberX + dx * maxRipple * 0.9f
        val cy = bobberY + 6f + dy * maxRipple * 0.9f
        // směr pohybu → ocas za tělem
        val (px, py, _) = WaterIntro.shadow(t - 40)
        val bx = bobberX + px * maxRipple * 0.9f; val byy = bobberY + 6f + py * maxRipple * 0.9f
        val len = kotlin.math.sqrt((cx - bx) * (cx - bx) + (cy - byy) * (cy - byy)).coerceAtLeast(0.01f)
        val ux = (cx - bx) / len; val uy = (cy - byy) / len
        ellipse(cx, cy, w * 0.08f, w * 0.032f, shadowCol, a)
        val wag = sin(t * 0.03).toFloat() * 1.5f
        ellipse(cx - ux * w * 0.09f, cy - uy * w * 0.04f + wag * 0.3f, w * 0.035f, w * 0.022f, shadowCol, a * 0.85f)
    }

    private fun drawLineAndBobber(t: Long) {
        if (!WaterIntro.bobberVisible(t)) return
        val by = bobberY + WaterIntro.bobberOffset(t)
        // vlasec z levého horního rohu (prut mimo záběr)
        val x0 = -2f; val y0 = h * 0.18f
        val steps = 80
        for (i in 0..steps) {
            val p = i / steps.toFloat()
            val sag = sin(p * PI).toFloat() * h * 0.03f
            val x = x0 + (bobberX - x0) * p
            val y = y0 + (by - 3 - y0) * p + sag
            if (y < by - 2) put(x.toInt(), y.toInt(), mix(frame[(y.toInt().coerceIn(0, h - 1)) * w + x.toInt().coerceIn(0, w - 1)], line, 0.7f))
        }
        val y = by.roundToInt()
        rect(bobberX - 1, y - 4, 3, 3, bobberRed)
        put(bobberX, y - 5, bobberRed)
        rect(bobberX - 1, y - 1, 3, 2, bobberWhite)
        // voda přes spodek splávku
        rect(bobberX - 2, y + 1, 5, 1, mix(waterTop, shimmer, 0.6f))
    }

    private fun drawColumn(t: Long) {
        val c = WaterIntro.column(t)
        if (c <= 0f && t < BURST_START) return
        val baseY = bobberY + 2
        val topY = baseY - c * h * 0.40f
        // sloup
        for (y in topY.toInt()..baseY) {
            val p = (baseY - y) / (baseY - topY + 1f)
            val halfW = (4f + 8f * (1f - p) * (1f - p) + sin(y * 0.7 + t * 0.03).toFloat() * 1.4f) * (0.6f + 0.4f * c)
            for (x in (bobberX - halfW).toInt()..(bobberX + halfW).toInt()) {
                val edge = abs(x - bobberX) / halfW
                put(x, y, if (edge > 0.65f) spray else foam)
            }
        }
        // korunka
        if (c > 0.3f) for (k in -4..4) circle(bobberX + k * 2.6f, topY + abs(k) * 1.6f - 1f, 3.2f - abs(k) * 0.3f, if (abs(k) > 2) spray else foam, 0.95f)
        // spodní koruna (odstřik do stran)
        if (c > 0.05f) for (k in listOf(-1, 1)) for (j in 0..3) {
            val sx = bobberX + k * (w * 0.08f + j * 3f) * (0.5f + 0.5f * c)
            circle(sx, baseY - (4 - j) * 2.5f * c, 1.6f, if (j % 2 == 0) foam else spray, 0.9f)
        }
        // pěna u paty
        val foamR = w * 0.10f + progress(t, BURST_START, WaterIntro.END) * w * 0.18f
        ring(bobberX.toFloat(), baseY + 1f, foamR, foamR * 0.3f, foam, 0.8f * (1f - progress(t, BURST_START, WaterIntro.END)))
        ellipse(bobberX.toFloat(), baseY + 1f, w * 0.09f, w * 0.03f, foam, 0.9f * c)
        // kapky
        for (d in drops) {
            if (t < d.t0) continue
            val s = (t - d.t0) / 1000f
            val x = bobberX + d.vx * s
            val y = baseY - c * h * 0.30f + d.vy * s + 170f * s * s
            if (y > baseY + 6) continue
            circle(x, y, d.size * 0.6f, if (d.white) foam else spray, 1f)
        }
    }

    private fun drawReeds(t: Long) {
        for (r in reeds) {
            val sway = WaterIntro.reedSway(t, r.idx)
            val col = if (r.light) reedLight else reedDark
            for (k in 0 until r.height) {
                val p = k / r.height.toFloat()
                val x = r.x + (sway * p * p * 2f).roundToInt()
                val y = h - 1 - k
                put(x, y, col)
                if (p < 0.4f) put(x + 1, y, col)
            }
            val topX = r.x + (sway * 2f).roundToInt()
            val topY = h - r.height
            if (r.tail) rect(topX - 1, topY - 2, 3, 6, cattail)
            else { put(topX + 1, topY - 1, col); put(topX + 2, topY - 2, col) }   // list ohnutý do strany
        }
    }

    // ── Kreslení ──

    private fun put(x: Int, y: Int, c: Int) { if (x in 0 until w && y in 0 until h) frame[y * w + x] = c }

    private fun rect(x: Int, y: Int, rw: Int, rh: Int, c: Int) {
        for (yy in y until y + rh) for (xx in x until x + rw) put(xx, yy, c)
    }

    private fun circle(cx: Float, cy: Float, r: Float, c: Int, alpha: Float) {
        val ri = r.toInt() + 1
        for (y in (cy - ri).toInt()..(cy + ri).toInt()) for (x in (cx - ri).toInt()..(cx + ri).toInt()) {
            if (x !in 0 until w || y !in 0 until h) continue
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) frame[y * w + x] = mix(frame[y * w + x], c, alpha)
        }
    }

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, c: Int, alpha: Float) {
        for (y in (cy - ry).toInt()..(cy + ry).toInt()) for (x in (cx - rx).toInt()..(cx + rx).toInt()) {
            if (x !in 0 until w || y !in 0 until h) continue
            val nx = (x - cx) / rx; val ny = (y - cy) / ry
            if (nx * nx + ny * ny <= 1f) frame[y * w + x] = mix(frame[y * w + x], c, alpha)
        }
    }

    /** Obrys elipsy (kruh na hladině) – jednopixelový pruh mezi 0,82 a 1 poloměru. */
    private fun ring(cx: Float, cy: Float, rx: Float, ry: Float, c: Int, alpha: Float) {
        if (alpha <= 0.01f || rx < 1f || ry < 0.5f) return
        for (y in (cy - ry - 1).toInt()..(cy + ry + 1).toInt()) for (x in (cx - rx - 1).toInt()..(cx + rx + 1).toInt()) {
            if (x !in 0 until w || y !in 0 until h) continue
            val nx = (x - cx) / rx; val ny = (y - cy) / ry
            val d = nx * nx + ny * ny
            if (d in 0.72f..1.0f) frame[y * w + x] = mix(frame[y * w + x], c, alpha)
        }
    }
}

/** Scéna intra kreslená do pole pixelů (nízké rozlišení, čistá funkce času). */
interface PixelScene {
    val width: Int
    val height: Int
    fun render(t: Long, out: IntArray)
}

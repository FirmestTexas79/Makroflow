package cz.uhk.macroflow.pokemon.encounter

import cz.uhk.macroflow.pokemon.encounter.ForestIntro.BURST_START
import cz.uhk.macroflow.pokemon.encounter.ForestIntro.FADE_IN_END
import cz.uhk.macroflow.pokemon.encounter.ForestIntro.progress
import cz.uhk.macroflow.pokemon.encounter.MountainScene.Companion.mix
import cz.uhk.macroflow.pokemon.encounter.MountainScene.Companion.rgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Intro setkání v Hvozdu jako pixel art v nízkém rozlišení (čistý Kotlin, bez Androidu).
 * Stejně jako [WaterScene] je každý snímek čistá funkce času – jde přeskočit, testovat
 * a vyrenderovat mimo telefon (tools/encounter/ForestPreview.kt).
 */
class ForestScene(val w: Int, val h: Int, seed: Int = 36) : PixelScene {

    // ── Paleta: soumrak v lese, barvy ladí s mapou Hvozdu ──
    private val skyTop = rgb(34, 58, 52); private val skyGlow = rgb(236, 206, 132); private val skyMid = rgb(122, 150, 96)
    private val farTrunk = rgb(46, 70, 62); private val midTrunk = rgb(30, 46, 38); private val nearTrunk = rgb(20, 30, 24)
    private val canopyDark = rgb(22, 48, 34); private val canopy = rgb(36, 74, 46); private val canopyLight = rgb(62, 108, 58)
    private val ground = rgb(70, 112, 58); private val groundLight = rgb(108, 150, 72); private val groundDark = rgb(44, 76, 44)
    private val moss = rgb(132, 176, 84)
    private val bushDark = rgb(26, 62, 38); private val bush = rgb(48, 100, 54); private val bushLight = rgb(90, 150, 74); private val bushTip = rgb(146, 196, 102)
    private val fernCol = rgb(40, 96, 66); private val fernLight = rgb(96, 170, 102)
    private val ray = rgb(250, 230, 160); private val firefly = rgb(236, 255, 150)
    private val leafCols = intArrayOf(rgb(196, 150, 60), rgb(150, 176, 70), rgb(214, 110, 52), rgb(110, 160, 80))
    private val eyeCol = rgb(255, 232, 110); private val eyeCore = rgb(255, 255, 230)
    private val birdCol = rgb(16, 22, 20)

    // ── Rozvržení ──
    val horizon = (h * 0.46f).toInt()
    val bushX = w / 2
    val bushY = (h * 0.66f).toInt()
    private val bushR = w * 0.24f

    private val base = IntArray(w * h)
    private val frame = IntArray(w * h)

    private class Blob(val dx: Float, val dy: Float, val r: Float, val ang: Float, val speed: Float)
    private class Leaf(val x: Float, val phase: Float, val speed: Float, val col: Int, val drift: Float)
    private class Fly(val x: Float, val y: Float, val phase: Float, val r: Float)
    private class Bit(val vx: Float, val vy: Float, val col: Int, val size: Int, val spin: Float)
    private class Bird(val x: Float, val y: Float, val vx: Float, val vy: Float, val flap: Float)

    private val blobs = mutableListOf<Blob>()
    private val leaves = mutableListOf<Leaf>()
    private val flies = mutableListOf<Fly>()
    private val bits = mutableListOf<Bit>()
    private val birds = mutableListOf<Bird>()
    private val trunks = mutableListOf<Triple<Int, Int, Int>>()     // x, šířka, vrstva

    init {
        val rnd = Random(seed)
        // shluky listí, ze kterých se skládá keř (při rozletu letí každý svým směrem)
        repeat(11) {
            val a = rnd.nextFloat() * PI.toFloat() * 2
            val d = rnd.nextFloat() * 0.55f
            blobs += Blob(cos(a) * d * bushR, sin(a) * d * bushR * 0.55f - bushR * 0.15f, bushR * (0.38f + rnd.nextFloat() * 0.22f),
                a, 0.7f + rnd.nextFloat() * 0.6f)
        }
        blobs.sortBy { it.dy }
        repeat(26) { leaves += Leaf(rnd.nextFloat() * w, rnd.nextFloat(), 0.012f + rnd.nextFloat() * 0.02f, leafCols[rnd.nextInt(leafCols.size)], rnd.nextFloat() * 6f) }
        repeat(14) { flies += Fly(rnd.nextFloat() * w, horizon * 0.6f + rnd.nextFloat() * (h - horizon * 0.6f), rnd.nextFloat() * 6.28f, 2f + rnd.nextFloat() * 6f) }
        repeat(70) {
            val a = -PI / 2 + (rnd.nextDouble() - 0.5) * PI * 1.5
            val v = 40f + rnd.nextFloat() * 120f
            bits += Bit((cos(a) * v).toFloat(), (sin(a) * v).toFloat() - 20f, leafCols[rnd.nextInt(leafCols.size)], 1 + rnd.nextInt(2), rnd.nextFloat() * 10f)
        }
        repeat(5) { birds += Bird(w * (0.2f + rnd.nextFloat() * 0.6f), horizon * (0.25f + rnd.nextFloat() * 0.35f), (rnd.nextFloat() - 0.5f) * 90f, -40f - rnd.nextFloat() * 50f, rnd.nextFloat() * 6f) }
        var x = -4
        while (x < w + 4) { trunks += Triple(x, 3 + rnd.nextInt(4), 0); x += 7 + rnd.nextInt(9) }
        x = -6
        while (x < w + 6) { trunks += Triple(x, 5 + rnd.nextInt(5), 1); x += 16 + rnd.nextInt(18) }
        trunks += Triple(-2, 10, 2); trunks += Triple(w - 9, 11, 2)
        buildBase(rnd)
    }

    /** Statické pozadí: obloha mezi korunami, kmeny ve třech vrstvách, zem. */
    private fun buildBase(rnd: Random) {
        for (y in 0 until h) {
            val c = when {
                y < horizon -> {
                    val p = y.toFloat() / horizon
                    if (p < 0.6f) mix(skyTop, skyMid, p / 0.6f) else mix(skyMid, skyGlow, (p - 0.6f) / 0.4f)
                }
                else -> {
                    val p = (y - horizon).toFloat() / (h - horizon)
                    mix(groundLight, groundDark, minOf(1f, p * 1.2f))
                }
            }
            for (x in 0 until w) base[y * w + x] = c
        }
        // kmeny
        for ((tx, tw, layer) in trunks) {
            val col = when (layer) { 0 -> farTrunk; 1 -> midTrunk; else -> nearTrunk }
            val bottom = horizon + when (layer) { 0 -> 2; 1 -> 6; else -> h / 3 }
            for (y in 0 until bottom) for (xx in tx until tx + tw) if (xx in 0 until w) {
                base[y * w + xx] = if (xx == tx + tw - 1 && layer < 2) mix(col, skyGlow, 0.18f) else col
            }
        }
        // koruny nahoře (tmavý baldachýn s otvory, kudy svítí nebe)
        for (y in 0 until (horizon * 0.42f).toInt()) for (x in 0 until w) {
            val n = sin(x * 0.37 + y * 0.21) + sin(x * 0.13 - y * 0.47) * 0.8 + sin((x + y) * 0.09) * 0.6
            val thr = (y / (horizon * 0.42f)) * 2.4f - 0.4f
            if (n > thr) base[y * w + x] = if (n > thr + 1.2) canopyDark else if (n > thr + 0.5) canopy else canopyLight
        }
        // mech a trsy trávy na zemi
        for (i in 0 until w * 2) {
            val x = rnd.nextInt(w); val y = horizon + 2 + rnd.nextInt(h - horizon - 2)
            base[y * w + x] = if (rnd.nextBoolean()) moss else groundDark
        }
    }

    override val width get() = w
    override val height get() = h

    override fun render(t: Long, out: IntArray) {
        base.copyInto(frame)
        drawRays(t)
        drawLeaves(t, behind = true)
        drawBushShadow(t)
        drawBush(t)
        drawBurst(t)
        drawFerns(t)
        drawFireflies(t)
        drawLeaves(t, behind = false)
        drawBirds(t)

        val shake = ForestIntro.shakeAmplitude(t)
        val dx = if (shake > 0f) ((if ((t / 40) % 2 == 0L) 1 else -1) * shake).roundToInt() else 0
        val fade = progress(t, 0, FADE_IN_END)
        val black = rgb(0, 0, 0)
        for (y in 0 until h) for (x in 0 until w) {
            val sx = (x - dx).coerceIn(0, w - 1)
            val c = frame[y * w + sx]
            out[y * w + x] = if (fade < 1f) mix(black, c, fade) else c
        }
    }

    /** Šikmé paprsky světla mezi kmeny. */
    private fun drawRays(t: Long) {
        val a = ForestIntro.rays(t)
        for ((i, x0) in listOf(w * 0.22f, w * 0.55f, w * 0.8f).withIndex()) {
            val width = w * (0.07f + i * 0.02f)
            for (y in (horizon * 0.3f).toInt() until h) {
                val cx = x0 + (y - horizon * 0.3f) * 0.35f
                for (x in (cx - width).toInt()..(cx + width).toInt()) {
                    if (x !in 0 until w) continue
                    val e = 1f - abs(x - cx) / width
                    val fadeY = 1f - (y - horizon * 0.3f) / (h - horizon * 0.3f)
                    val alpha = 0.16f * a * e * fadeY
                    if ((x + y) % 2 == 0) frame[y * w + x] = mix(frame[y * w + x], ray, alpha)
                }
            }
        }
    }

    private fun drawBushShadow(t: Long) {
        val b = ForestIntro.burst(t)
        ellipse(bushX.toFloat(), bushY + bushR * 0.35f, bushR * (1.1f - 0.4f * b), bushR * 0.22f, groundDark, 0.7f * (1f - b * 0.6f))
    }

    /** Keř ze shluků listí; třese se, v jeho stínu svítí oči; při rozletu se rozletí. */
    private fun drawBush(t: Long) {
        val b = ForestIntro.burst(t)
        val sway = ForestIntro.rustle(t)
        // tmavé nitro keře (tam jsou oči)
        if (b < 0.3f) ellipse(bushX + sway * 0.5f, bushY - bushR * 0.12f, bushR * 0.8f, bushR * 0.42f, bushDark, 1f - b * 3f)
        for ((i, bl) in blobs.withIndex()) {
            // po rozletu shluky pořád letí ven ze záběru a zmenšují se (nezůstanou viset)
            val fly = if (t < BURST_START) 0f else (t - BURST_START) / 420f * bl.speed
            val cx = bushX + bl.dx + sway * (0.6f + (i % 3) * 0.3f) + cos(bl.ang) * fly * w * 0.45f
            val cy = bushY + bl.dy - sin(abs(bl.ang)) * fly * h * 0.10f + fly * fly * h * 0.10f
            val r = bl.r * (1f - minOf(1f, fly * 0.55f))
            if (r < 1f) continue
            leafyBlob(cx, cy, r, i)
        }
        val (ea, es) = ForestIntro.eyes(t)
        if (ea > 0f && b < 0.2f) {
            val ey = bushY - bushR * 0.14f
            val gap = 4f * es
            for (s in listOf(-1, 1)) {
                val ex = bushX + sway * 0.5f + s * gap
                circle(ex, ey, 2.4f * es, eyeCol, 0.35f * ea)              // záře
                circle(ex, ey, 1.1f * es, eyeCol, ea)
                put(ex.roundToInt(), ey.roundToInt(), mix(frame[(ey.roundToInt().coerceIn(0, h - 1)) * w + ex.roundToInt().coerceIn(0, w - 1)], eyeCore, ea))
            }
        }
    }

    /** Shluk listí: tmavý obrys, stín dole vpravo, světlé lístky nahoře vlevo. */
    private fun leafyBlob(cx: Float, cy: Float, r: Float, seed: Int) {
        val ri = r.toInt() + 2
        for (y in (cy - ri).toInt()..(cy + ri).toInt()) for (x in (cx - ri).toInt()..(cx + ri).toInt()) {
            if (x !in 0 until w || y !in 0 until h) continue
            val ang = kotlin.math.atan2(y - cy, x - cx)
            val rim = 1f + 0.12f * sin(ang * 7 + seed).toFloat() + 0.06f * sin(ang * 13 + seed * 3).toFloat()
            val d = kotlin.math.hypot(x - cx, (y - cy) * 1.15f) / (r * rim)
            if (d < 1f) {
                val l = -((x - cx) * 0.7f + (y - cy)) / r + (((x * 7 + y * 13 + seed) % 5) - 2) * 0.12f
                frame[y * w + x] = when { l > 0.75f -> bushTip; l > 0.2f -> bushLight; l > -0.45f -> bush; else -> bushDark }
            } else if (d < 1.14f) frame[y * w + x] = bushDark
        }
    }

    /** Rozlet: lístky vyletí do všech stran a padají. */
    private fun drawBurst(t: Long) {
        if (t < BURST_START) return
        val s = (t - BURST_START) / 1000f
        for (bt in bits) {
            val x = bushX + bt.vx * s + sin(s * bt.spin) * 3f
            val y = bushY - bushR * 0.2f + bt.vy * s + 120f * s * s
            if (y > h) continue
            rect(x.toInt(), y.toInt(), bt.size, 1 + (bt.size + (s * bt.spin).toInt()) % 2, bt.col)
        }
    }

    /** Kapradí v popředí v rozích – mírně se houpe. */
    private fun drawFerns(t: Long) {
        for ((side, x0) in listOf(0 to w * 0.06f, 1 to w * 0.94f)) {
            for (f in 0 until 7) {
                val dir = if (side == 0) 1 else -1
                val ang = -PI / 2 + dir * (0.15 + f * 0.2) + sin(t * 0.003 + f).toFloat() * 0.05
                val ln = h * (0.2f + (f % 3) * 0.04f)
                for (s in 0 until ln.toInt()) {
                    val p = s / ln
                    val x = x0 + cos(ang).toFloat() * s
                    val y = h - 1 + sin(ang).toFloat() * s * 0.9f + p * p * ln * 0.35f
                    put(x.toInt(), y.toInt(), if (p < 0.4f) fernCol else fernLight)
                    if (s % 2 == 0 && p > 0.15f) { put(x.toInt() - 1, y.toInt() - 1, fernLight); put(x.toInt() + 1, y.toInt() - 1, fernCol) }
                }
            }
        }
    }

    private fun drawFireflies(t: Long) {
        for (f in flies) {
            val x = f.x + sin(t * 0.0011 + f.phase) * f.r
            val y = f.y + cos(t * 0.0014 + f.phase * 1.3) * f.r * 0.6f
            val pulse = 0.5f + 0.5f * sin(t * 0.006 + f.phase * 2).toFloat()
            if (pulse < 0.25f) continue
            circle(x.toFloat(), y.toFloat(), 1.8f, firefly, 0.25f * pulse)
            put(x.roundToInt(), y.roundToInt(), mix(frame[(y.roundToInt().coerceIn(0, h - 1)) * w + x.roundToInt().coerceIn(0, w - 1)], firefly, pulse))
        }
    }

    /** Padající listí: zadní vrstva menší a tmavší, přední větší. */
    private fun drawLeaves(t: Long, behind: Boolean) {
        for ((i, l) in leaves.withIndex()) {
            if ((i % 2 == 0) != behind) continue
            val cycle = ((l.phase + t * l.speed / 1000f * 40f) % 1f)
            val y = cycle * (h + 10) - 5
            val x = l.x + sin(cycle * 12f + l.drift) * 6f
            val c = if (behind) mix(l.col, canopyDark, 0.35f) else l.col
            put(x.toInt(), y.toInt(), c)
            if (!behind) put(x.toInt() + (if ((t / 200 + i) % 2 == 0L) 1 else -1), y.toInt(), c)
        }
    }

    private fun drawBirds(t: Long) {
        val p = ForestIntro.birds(t)
        if (p <= 0f) return
        val s = (t - BURST_START - 60) / 1000f
        for (b in birds) {
            val x = b.x + b.vx * s; val y = b.y + b.vy * s
            val up = sin(t * 0.04 + b.flap) > 0
            put(x.toInt(), y.toInt(), birdCol)
            put(x.toInt() - 1, y.toInt() + (if (up) -1 else 0), birdCol); put(x.toInt() + 1, y.toInt() + (if (up) -1 else 0), birdCol)
            put(x.toInt() - 2, y.toInt() + (if (up) -2 else 1), birdCol); put(x.toInt() + 2, y.toInt() + (if (up) -2 else 1), birdCol)
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
        if (rx < 0.5f || ry < 0.5f || alpha <= 0f) return
        for (y in (cy - ry).toInt()..(cy + ry).toInt()) for (x in (cx - rx).toInt()..(cx + rx).toInt()) {
            if (x !in 0 until w || y !in 0 until h) continue
            val nx = (x - cx) / rx; val ny = (y - cy) / ry
            if (nx * nx + ny * ny <= 1f) frame[y * w + x] = mix(frame[y * w + x], c, alpha.coerceIn(0f, 1f))
        }
    }
}

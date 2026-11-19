package cz.uhk.macroflow.pokemon.cave

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import java.util.Random
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Živé interiéry (Věž, hala Arény): drobnosti, díky kterým místnost „dýchá“ – lampa občas zabliká,
 * oknem přeletí padající hvězda, kyvadlo pod hodinami se kývá, podél regálu proběhne myš, ve světle
 * se vznáší prach, potrubní poštou proletí kapsle, pochodně plápolají a odletují z nich jiskry.
 * Velikost = celý svět mapy, [scale] = násobek art pixelu; vše se kreslí po celých art pixelech.
 * Souřadnice jsou v art px a pocházejí z generátorů (tools/mapgen/gen_archive.py, gen_tower.py, gen_arena_hall.py).
 */
class InteriorFxView(context: Context, private val scale: Int, private val fx: InteriorFx) : View(context) {
    private val s = scale.toFloat()
    private val paint = Paint()
    private val rnd = Random(fx.hashCode().toLong())
    private val t0 = SystemClock.uptimeMillis()

    // zablikání lampy: kdy začne další a která to bude
    private var flickerAt = t0 + 4000L + rnd.nextInt(6000)
    private var flickerLamp = 0
    // padající hvězda
    private var starAt = t0 + 6000L + rnd.nextInt(14000)
    private var starWin = 0; private var starX = 0f; private var starY = 0f
    // myš
    private var mouseAt = t0 + 9000L + rnd.nextInt(20000)
    private var mouseBack = false
    // potrubní pošta
    private var tubeAt = t0 + 3000L + rnd.nextInt(8000)
    private var tubeIdx = 0

    private fun px(c: Canvas, x: Int, y: Int, color: Int) {
        paint.color = color
        c.drawRect(x * s, y * s, (x + 1) * s, (y + 1) * s, paint)
    }

    private fun row(c: Canvas, x0: Int, x1: Int, y: Int, color: Int) {
        paint.color = color
        c.drawRect(x0 * s, y * s, (x1 + 1) * s, (y + 1) * s, paint)
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val t = (now - t0) / 1000f
        motes(canvas, t)
        ash(canvas, t)
        lamps(canvas, now)
        stars(canvas, now)
        fx.pendulums.forEach { pendulum(canvas, it, t) }
        blinks(canvas, now)
        mouse(canvas, now)
        tubes(canvas, now)
        torches(canvas, t)
        grass(canvas, t)
        butterflies(canvas, t)
        sprinklers(canvas, now)
        sunFlicker(canvas, now)
        postInvalidateOnAnimation()
    }

    /** Zablikání: krátká sekvence ztlumení (tmavá elipsa přes kruh světla, stínidlo zhasne). */
    private fun lamps(c: Canvas, now: Long) {
        if (fx.lamps.isEmpty()) return
        if (now < flickerAt) return
        val ms = now - flickerAt
        // zap/vyp: 0–70 tma, 70–130 světlo, 130–240 tma, 240–290 světlo, 290–420 napůl
        val dim = when {
            ms < 70 -> 1f; ms < 130 -> 0f; ms < 240 -> 1f; ms < 290 -> 0f; ms < 420 -> 0.5f
            else -> { flickerAt = now + 7000L + rnd.nextInt(14000); flickerLamp = rnd.nextInt(fx.lamps.size); return }
        }
        if (dim == 0f) return
        val l = fx.lamps[flickerLamp]
        val alpha = (if (dim >= 1f) 110 else 55) shl 24
        for (dy in -l.ry..l.ry) {
            val k = 1f - (dy.toFloat() / l.ry).let { it * it }
            val hw = (l.rx * sqrt(k.coerceAtLeast(0f))).roundToInt()
            if (hw > 0) row(c, l.cx - hw, l.cx + hw, l.cy + dy, alpha or 0x080C1E)
        }
        l.glow?.let { g -> for (y in g.y0..g.y1) row(c, g.x0, g.x1, y, if (dim >= 1f) 0xFF1C3424.toInt() else 0xFF8A7A4A.toInt()) }
    }

    /** Padající hvězda v okně: hlava a slábnoucí ohon, ořízne se rámem okna. */
    private fun stars(c: Canvas, now: Long) {
        if (fx.stars.isEmpty() || now < starAt) return
        val ms = now - starAt
        if (ms == 0L || (starX == 0f && starY == 0f)) {
            starWin = rnd.nextInt(fx.stars.size)
            val w = fx.stars[starWin]
            starX = w.x1 - rnd.nextInt(maxOf(1, (w.x1 - w.x0) / 2)).toFloat()
            starY = w.y0 + rnd.nextInt(maxOf(1, (w.y1 - w.y0) / 3)).toFloat()
        }
        if (ms > 650) { starAt = now + 16000L + rnd.nextInt(30000); starX = 0f; starY = 0f; return }
        val w = fx.stars[starWin]
        val d = ms / 1000f * 34f
        c.save()
        c.clipRect(w.x0 * s, w.y0 * s, (w.x1 + 1) * s, (w.y1 + 1) * s)
        for (i in 0..4) {
            val x = (starX - (d - i * 1.2f) * 1f).toInt()
            val y = (starY + (d - i * 1.2f) * 0.55f).toInt()
            val a = if (i == 0) 255 else 200 - i * 45
            px(c, x, y, (a shl 24) or (if (i == 0) 0xFFFFFF else 0xC8D4FF))
        }
        c.restore()
    }

    /** Kyvadlo: tyčka po pixelech, mosazná čočka 3×3 se světlem shora zleva. */
    private fun pendulum(c: Canvas, p: FxPendulum, t: Float) {
        val off = sin(t * 2f * PI.toFloat() / p.periodS) * p.amp
        val dir = if (p.up) -1 else 1
        for (i in 1 until p.len) px(c, (p.px + off * i / p.len).roundToInt(), p.py + i * dir, p.rod)
        val bx = (p.px + off).roundToInt(); val by = p.py + p.len * dir
        if (p.up) { row(c, bx - 1, bx, by + 2, 0xFFE0BC5C.toInt()); return }       // metronom: závažíčko na ramínku
        row(c, bx - 1, bx + 1, by - 1, 0xFFE0BC5C.toInt())
        row(c, bx - 1, bx + 1, by, 0xFFBC9238.toInt())
        row(c, bx - 1, bx + 1, by + 1, 0xFF8A6424.toInt())
        px(c, bx - 1, by - 1, 0xFFFFE9A0.toInt())
    }

    private val blinkAt = LongArray(fx.blinks.size) { t0 + 5000L + rnd.nextInt(15000) }

    /** Kontrolka (OBSAZENO…), která se občas sama rozsvítí na pár vteřin. */
    private fun blinks(c: Canvas, now: Long) {
        for ((i, b) in fx.blinks.withIndex()) {
            val ms = now - blinkAt[i]
            if (ms < 0) continue
            if (ms > 2600) { blinkAt[i] = now + 18000L + rnd.nextInt(30000); continue }
            if (ms in 200..400) continue                                               // krátké zakolísání
            for (y in b.box.y0..b.box.y1) row(c, b.box.x0, b.box.x1, y, b.color)
            px(c, b.box.x0, b.box.y0, 0xFFFFFFFF.toInt())
        }
    }

    /** Myš: přeběhne trasu, uprostřed se zastaví a čenichá; občas běží pozpátku. */
    private fun mouse(c: Canvas, now: Long) {
        val path = fx.mouse ?: return
        if (now < mouseAt) return
        val pts = if (mouseBack) path.reversed() else path
        val legs = pts.zipWithNext { a, b -> hypot((b.first - a.first).toFloat(), (b.second - a.second).toFloat()) }
        val speed = 46f / 1000f
        val pause = 900L
        val ms = now - mouseAt
        val first = (legs[0] / speed).toLong()
        val total = (legs.sum() / speed).toLong() + pause
        if (ms > total) { mouseAt = now + 22000L + rnd.nextInt(45000); mouseBack = rnd.nextInt(3) == 0; return }
        val (x, y, dir, moving) = when {
            ms < first -> lerp(pts[0], pts[1], ms * speed / legs[0]).let { Quad(it.first, it.second, sign(pts[0], pts[1]), true) }
            ms < first + pause -> Quad(pts[1].first.toFloat(), pts[1].second.toFloat(), sign(pts[0], pts[1]), false)
            else -> {
                val d = (ms - first - pause) * speed
                var i = 1; var rest = d
                while (i < legs.size - 1 && rest > legs[i]) { rest -= legs[i]; i++ }
                lerp(pts[i], pts[i + 1], (rest / legs[i]).coerceAtMost(1f)).let { Quad(it.first, it.second, sign(pts[i], pts[i + 1]), true) }
            }
        }
        val mx = x.roundToInt(); val my = y.roundToInt()
        val sniff = !moving && (now / 140) % 2 == 0L
        val step = moving && (now / 90) % 2 == 0L
        fun m(dx: Int, dy: Int, col: Int) = px(c, mx + dx * dir, my + dy, col)
        val fur = 0xFF8A8494.toInt(); val dark = 0xFF5E5868.toInt(); val pink = 0xFFE0A0A8.toInt()
        m(-4, 0, 0xFFA08088.toInt()); m(-5, 0, 0xFFA08088.toInt()); m(-6, -1, 0xFFA08088.toInt())     // ocásek
        for (dx in -2..0) m(dx, -2, fur)
        for (dx in -3..1) m(dx, -1, if (dx == -3) dark else fur)
        m(1, -2, fur); m(2, -1 - if (sniff) 1 else 0, fur)
        m(3, -1 - if (sniff) 1 else 0, pink)                                                               // čumáček
        m(2, -2 - if (sniff) 1 else 0, 0xFF101014.toInt())                                                // oko
        m(0, -3, pink)                                                                                    // ouško
        if (step) { m(-2, 0, dark); m(1, 0, dark) } else { m(-1, 0, dark); m(0, 0, dark) }
    }

    private data class Quad(val x: Float, val y: Float, val dir: Int, val moving: Boolean)
    private fun lerp(a: Pair<Int, Int>, b: Pair<Int, Int>, k: Float) = Pair(a.first + (b.first - a.first) * k, a.second + (b.second - a.second) * k)
    private fun sign(a: Pair<Int, Int>, b: Pair<Int, Int>) = if (b.first >= a.first) 1 else -1

    /** Prach ve světle lamp a oken: pár pomalu kroužících světlých pixelů, které se rozsvěcejí a hasnou. */
    private fun motes(c: Canvas, t: Float) {
        for ((bi, b) in fx.motes.withIndex()) for (i in 0 until 6) {
            val h = ((bi * 31 + i) * 2654435761L).toInt() ushr 1
            val w = b.x1 - b.x0; val hh = b.y1 - b.y0
            val x = b.x0 + (h % w + sin(t * 0.21f + i) * 4f).let { ((it % w) + w) % w }
            val y = b.y0 + ((h / 7 % hh) - t * (1.2f + i % 3 * 0.5f)).let { ((it % hh) + hh) % hh }
            val a = ((sin(t * 0.9f + h % 10) + 1) * 60).toInt()
            if (a > 20) px(c, x.toInt(), y.toInt(), (a shl 24) or fx.moteColor)
        }
    }

    /** Popel (nebo sníh) padající za oknem. */
    private fun ash(c: Canvas, t: Float) {
        for ((bi, b) in fx.ash.withIndex()) for (i in 0 until 5) {
            val h = ((bi * 17 + i + 3) * 2246822519L).toInt() ushr 1
            val w = b.x1 - b.x0 + 1; val hh = b.y1 - b.y0 + 1
            val y = b.y0 + ((h % hh) + t * (2.5f + i % 3)).let { it % hh }
            val x = b.x0 + ((h / 5 % w) + sin(t * 0.8f + i * 1.7f) * 1.5f).let { ((it % w) + w) % w }
            px(c, x.toInt(), y.toInt(), 0xB4D6D2CA.toInt())
        }
    }

    /** Potrubní pošta: mosazná kapsle proletí trubkou nahoru. */
    private fun tubes(c: Canvas, now: Long) {
        if (fx.tubes.isEmpty() || now < tubeAt) return
        val ms = now - tubeAt
        if (ms > 800) { tubeAt = now + 9000L + rnd.nextInt(18000); tubeIdx = rnd.nextInt(fx.tubes.size); return }
        val b = fx.tubes[tubeIdx]
        val y = b.y1 - ((b.y1 - b.y0) * (ms / 800f)).toInt()
        row(c, b.x0, b.x1, y, 0xFFFFE9A0.toInt())
        row(c, b.x0, b.x1, y + 1, 0xFFE0BC5C.toInt())
        row(c, b.x0, b.x1, y + 2, 0xFF8A6424.toInt())
        row(c, b.x0, b.x1, y + 3, 0xFF5A3E12.toInt())
    }

    /** Pochodně: plamen „tančí“ (jazyky nad plamenem, jádro bliká), záře na zdi pulzuje, odletují jiskry. */
    private fun torches(c: Canvas, t: Float) {
        for ((i, b) in fx.torches.withIndex()) {
            val f = ((t * 9 + i * 3).toInt() * 7919 + i) % 5
            val cx = (b.x0 + b.x1) / 2
            val glow = (sin(t * 7f + i) * 10 + sin(t * 13f + i * 2) * 6 + 30).toInt().coerceIn(10, 50)
            for (dy in -9..9) {
                val hw = (12 * sqrt(1f - (dy / 9.5f).let { it * it })).roundToInt()
                if (hw > 0) row(c, cx - hw, cx + hw, b.y0 + 4 + dy, (glow shl 24) or 0xFFB040)
            }
            when (f) {                                                           // jazyky plamene
                0 -> { px(c, cx, b.y0 - 1, 0xFFE8682E.toInt()); px(c, cx, b.y0 - 2, 0xC0E8682E.toInt()) }
                1 -> { px(c, cx - 1, b.y0 - 1, 0xFFE8682E.toInt()) }
                2 -> { px(c, cx + 1, b.y0, 0xFFFFB040.toInt()); px(c, cx + 1, b.y0 - 1, 0xFFE8682E.toInt()) }
                3 -> { px(c, cx, b.y0 - 1, 0xFFFFB040.toInt()) }
                else -> {}
            }
            if (f % 2 == 0) px(c, cx, b.y0 + 4, 0xFFFFF2C0.toInt())             // jádro
            val sp = (t * 0.7f + i * 0.37f) % 1f                                // jiskra
            if (sp < 0.6f) {
                val k = sp / 0.6f
                val a = ((1 - k) * 230).toInt()
                px(c, cx + (sin((t + i) * 5f) * 2).toInt(), b.y0 - 2 - (k * 9).toInt(), (a shl 24) or 0xFFB040)
            }
        }
    }

    /** Vysoká tráva ve větru z ventilace: konce stébel se pohupují (vlna běží přes trs). */
    private fun grass(c: Canvas, t: Float) {
        for ((bi, b) in fx.grass.withIndex()) {
            val w = b.x1 - b.x0
            for (i in 0 until w / 2) {
                val x = b.x0 + 1 + i * 2 + ((bi * 7 + i) % 3) / 2
                val h = 2 + (bi * 13 + i * 7) % 3
                val top = b.y0 + (bi * 5 + i * 11) % 5
                val sway = sin(t * 2.1f - x * 0.18f + bi) * 1.4f
                for (k in 0 until h) {
                    val dx = (sway * (h - k) / h).roundToInt()
                    px(c, x + dx, top + k, if (k == 0) 0xFFA0C462.toInt() else 0xFF78A44A.toInt())
                }
            }
        }
    }

    /** Motýli: bílí a žlutí, mávají křídly a kličkují v oblasti. */
    private fun butterflies(c: Canvas, t: Float) {
        for ((bi, b) in fx.butterflies.withIndex()) for (i in 0 until 2) {
            val k = bi * 2 + i
            val w = b.x1 - b.x0; val h = b.y1 - b.y0
            val x = (b.x0 + w / 2 + sin(t * (0.35f + k * 0.07f) + k) * w / 2.2f + sin(t * 1.3f + k * 2) * 3).toInt()
            val y = (b.y0 + h / 2 + sin(t * (0.5f + k * 0.05f) + k * 1.7f) * h / 2.2f + sin(t * 2.2f + k) * 2).toInt()
            val col = if (k % 2 == 0) 0xFFF8F4E8.toInt() else 0xFFF4D860.toInt()
            val flap = ((t * 9 + k) % 2f) < 1f
            px(c, x - 1, y, col); px(c, x + 1, y, col)
            if (flap) { px(c, x - 1, y - 1, col); px(c, x + 1, y - 1, col) }
            px(c, x, y, 0xFF3A2A1A.toInt())
        }
    }

    private val sprayAt = LongArray(fx.sprinklers.size) { t0 + 3000L + rnd.nextInt(12000) }

    /** Postřikovač: na pár vteřin se roztočí a rozhazuje kapky do kruhu. */
    private fun sprinklers(c: Canvas, now: Long) {
        for ((i, p) in fx.sprinklers.withIndex()) {
            val ms = now - sprayAt[i]
            if (ms < 0) continue
            if (ms > 3500) { sprayAt[i] = now + 12000L + rnd.nextInt(20000); continue }
            val a0 = ms / 1000f * 5f
            for (j in 0 until 10) {
                val a = a0 + j * 0.63f
                val r = 3f + ((ms / 60 + j * 3) % 12)
                val x = p.first + (kotlin.math.cos(a) * r).roundToInt()
                val y = p.second - 2 + (sin(a) * r * 0.55f).roundToInt() - (if (r < 7) 2 else 0)
                px(c, x, y, if (j % 3 == 0) 0xFFE8F6FF.toInt() else 0xC09CCCEE.toInt())
            }
        }
    }

    private var sunAt = t0 + 20000L + rnd.nextInt(25000)

    /** „Slunce“ je jen lampa: občas zakolísá a celé patro na okamžik potemní. */
    private fun sunFlicker(c: Canvas, now: Long) {
        if (!fx.sunFlicker || now < sunAt) return
        val ms = now - sunAt
        val a = when { ms < 90 -> 120; ms < 160 -> 0; ms < 300 -> 150; ms < 380 -> 40; ms < 700 -> 70; else -> {
            sunAt = now + 25000L + rnd.nextInt(35000); return } }
        if (a == 0) return
        paint.color = (a shl 24) or 0x0A1420
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    companion object {
        /** Archiv okruhů (1. patro Věže): lampy, hvězdy v oknech, kyvadlo hodin na 6:12, myš u spodního regálu. */
        val ARCHIVE = InteriorFx(
            lamps = listOf(FxLamp(68, 228, 26, 16, FxBox(65, 227, 71, 227)), FxLamp(106, 228, 26, 16, FxBox(103, 227, 109, 227)),
                FxLamp(94, 272, 22, 14, FxBox(92, 269, 96, 269))),
            stars = listOf(FxBox(14, 26, 29, 50), FxBox(146, 26, 161, 50)),
            ash = listOf(FxBox(14, 40, 29, 66), FxBox(146, 40, 161, 66)),
            motes = listOf(FxBox(50, 206, 126, 244), FxBox(74, 258, 112, 290), FxBox(14, 90, 60, 160)),
            moteColor = 0xFFE6B0,
            pendulums = listOf(FxPendulum(42, 40, 6, 1.6f, 2f)),
            mouse = listOf(12 to 314, 40 to 314, 66 to 313)
        )

        /** Vestibul Věže: prach v kuželech světla, popel za okny, potrubní pošta, Septova lampa. */
        val LOBBY = InteriorFx(
            lamps = listOf(FxLamp(156, 168, 14, 9, null)),
            ash = listOf(FxBox(14, 24, 29, 60), FxBox(146, 24, 161, 60)),
            motes = listOf(FxBox(14, 92, 70, 190), FxBox(110, 92, 166, 190)),
            moteColor = 0xF4F6FF,
            tubes = listOf(FxBox(63, 10, 64, 84), FxBox(113, 10, 114, 84))
        )

        /** Měřírna (2. patro Věže): kyvadlo hodin, metronom na stole úředníka, potrubní pošta, kontrolky kabin. */
        val MEASURE = InteriorFx(
            pendulums = listOf(FxPendulum(131, 21, 6, 1.4f, 2f), FxPendulum(153, 273, 8, 3f, 1f, up = true, rod = 0xFF6A4026.toInt())),
            tubes = listOf(FxBox(124, 48, 125, 66), FxBox(130, 48, 131, 66), FxBox(136, 48, 137, 66)),
            blinks = listOf(FxBlink(FxBox(39, 97, 41, 99), 0xFFFF4A3A.toInt()), FxBlink(FxBox(155, 97, 157, 99), 0xFFFF4A3A.toInt())),
            motes = listOf(FxBox(14, 92, 70, 200), FxBox(110, 92, 166, 200)),
            moteColor = 0xF4FAFA
        )

        /** Kalibrační louka (3. patro): tráva ve větru, motýli, postřikovače, pyl, „slunce“, které občas zabliká. */
        val PLAIN = InteriorFx(
            grass = listOf(FxBox(16, 174, 56, 182), FxBox(120, 246, 160, 254), FxBox(18, 300, 58, 308)),
            butterflies = listOf(FxBox(20, 120, 80, 220), FxBox(100, 200, 160, 300)),
            sprinklers = listOf(70 to 162, 108 to 286),
            motes = listOf(FxBox(20, 110, 156, 330)),
            moteColor = 0xFFF0A0,
            sunFlicker = true
        )

        /** Hala Arény: dvě pochodně u brány. */
        val HALL = InteriorFx(
            torches = listOf(FxBox(50, 22, 57, 29), FxBox(118, 22, 125, 29)),
            motes = listOf(FxBox(30, 90, 146, 150)),
            moteColor = 0xFFD8A0
        )
    }
}

data class FxBox(val x0: Int, val y0: Int, val x1: Int, val y1: Int)
data class FxLamp(val cx: Int, val cy: Int, val rx: Int, val ry: Int, val glow: FxBox?)
data class FxPendulum(val px: Int, val py: Int, val len: Int, val amp: Float, val periodS: Float,
                      val up: Boolean = false, val rod: Int = 0xFFBC9238.toInt())
data class FxBlink(val box: FxBox, val color: Int)
data class InteriorFx(
    val lamps: List<FxLamp> = emptyList(),
    val stars: List<FxBox> = emptyList(),
    val ash: List<FxBox> = emptyList(),
    val motes: List<FxBox> = emptyList(),
    val moteColor: Int = 0xFFE6B0,
    val pendulums: List<FxPendulum> = emptyList(),
    val blinks: List<FxBlink> = emptyList(),
    val mouse: List<Pair<Int, Int>>? = null,
    val tubes: List<FxBox> = emptyList(),
    val torches: List<FxBox> = emptyList(),
    val grass: List<FxBox> = emptyList(),
    val butterflies: List<FxBox> = emptyList(),
    val sprinklers: List<Pair<Int, Int>> = emptyList(),
    val sunFlicker: Boolean = false
)

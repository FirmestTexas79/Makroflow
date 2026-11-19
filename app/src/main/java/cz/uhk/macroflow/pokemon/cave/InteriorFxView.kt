package cz.uhk.macroflow.pokemon.cave

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
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
        shafts(canvas, t)
        leaves(canvas, t)
        peeks(canvas, now)
        snail(canvas, now)
        pebbles(canvas, now)
        crumbles(canvas, now)
        scorpion(canvas, now)
        dustDevil(canvas, now)
        glows(canvas, t)
        sparkles(canvas, t)
        arcs(canvas, now)
        drips(canvas, now)
        glowworms(canvas, t)
        bat(canvas, now)
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

    /** Paprsky světla mezi korunami: šikmé pruhy, které se pomalu pohupují a dýchají (víc a míň světla). */
    private fun shafts(c: Canvas, t: Float) {
        for ((i, sh) in fx.shafts.withIndex()) {
            val sway = sin(t * 0.23f + i * 1.9f) * 4f + sin(t * 0.61f + i) * 1.5f
            val breathe = (sin(t * 0.4f + i * 2.3f) + 1f) / 2f
            val a = (14 + breathe * 26).toInt()
            val w = sh.w + (breathe * 2).toInt()
            for (y in sh.y0..sh.y1) {
                val x0 = (sh.x + sway + (y - sh.y0) * sh.slant).roundToInt()
                val fade = 1f - ((y - sh.y0).toFloat() / (sh.y1 - sh.y0)).let { it * it }
                paint.color = ((a * fade).toInt() shl 24) or 0xFFE8A8
                c.drawRect(x0 * s, y * s, (x0 + w) * s, (y + 1) * s, paint)
                paint.color = ((a * fade * 0.5f).toInt() shl 24) or 0xFFE8A8               // měkčí okraj (1 px)
                c.drawRect((x0 - 1) * s, y * s, x0 * s, (y + 1) * s, paint)
                c.drawRect((x0 + w) * s, y * s, (x0 + w + 1) * s, (y + 1) * s, paint)
            }
        }
    }

    /** Padající listí: každý list se odlepí od koruny, kolébá se ze strany na stranu, dopadne a vybledne. */
    private fun leaves(c: Canvas, t: Float) {
        val cols = intArrayOf(0xFF5A9040.toInt(), 0xFF84B254.toInt(), 0xFFC89838.toInt(), 0xFFB06A28.toInt())
        for ((bi, b) in fx.leaves.withIndex()) for (i in 0 until 3) {
            val k = bi * 3 + i
            val period = 7f + (k % 4) * 1.3f
            val ph = ((t + k * 2.7f) % period) / period
            val fall = 34f + (k % 3) * 8
            val x0 = b.x0 + ((k * 37) % maxOf(1, b.x1 - b.x0))
            val x = (x0 + sin(ph * 9f + k) * 3f).roundToInt()
            val y = (b.y1 + minOf(ph * 1.25f, 1f) * fall).roundToInt()
            if (ph > 0.92f) continue                                                   // dopadlý list zmizí
            val col = cols[k % cols.size]
            val tilt = (ph * 9f + k).toInt() % 2 == 0
            px(c, x, y, col); px(c, x + if (tilt) 1 else -1, y + if (tilt) 0 else 1, col)
        }
    }

    private val peekAt = LongArray(fx.peeks.size) { t0 + 6000L + rnd.nextInt(12000) }

    /** Dutina ve stromu: občas se v tmě rozsvítí dvě oči, dvakrát mrknou a zase zmizí. */
    private fun peeks(c: Canvas, now: Long) {
        for ((i, p) in fx.peeks.withIndex()) {
            val ms = now - peekAt[i]
            if (ms < 0) continue
            if (ms > 3200) { peekAt[i] = now + 14000L + rnd.nextInt(22000); continue }
            if (ms in 900..1000 || ms in 1900..2000) continue                           // mrknutí
            val look = if (ms in 1100..1800) 1 else 0                                   // ohlédne se
            val eye = 0xFFFFE070.toInt()
            px(c, p.first - 2 + look, p.second, eye); px(c, p.first + 1 + look, p.second, eye)
            if (ms in 400..2600) { px(c, p.first - 1 + look, p.second + 2, 0xFF3A2A1A.toInt()); px(c, p.first + look, p.second + 2, 0xFF3A2A1A.toInt()) }
        }
    }

    private var snailT0 = t0

    /** Slimák: leze tam a zpátky před padlým kmenem (pár px za vteřinu), za ním lesklá stopa, kroutí tykadly. */
    private fun snail(c: Canvas, now: Long) {
        val path = fx.snail ?: return
        val (a, b) = path
        val len = abs(b.first - a.first).toFloat()
        val leg = (len / 2.4f * 1000).toLong()
        val pause = 4000L
        val cycle = (leg + pause) * 2
        val m = (now - snailT0) % cycle
        val back = m >= leg + pause
        val mm = if (back) m - leg - pause else m
        val k = (mm.toFloat() / leg).coerceAtMost(1f)
        val dir = if (back) -1 else 1
        val start = if (back) b.first else a.first
        val x = (start + dir * k * len).roundToInt()
        val y = a.second
        val trail = 0x60D8E8F0
        for (i in 1..(k * len).toInt().coerceAtMost(18)) px(c, x - dir * (i + 4), y, if (i % 3 == 0) 0x90F4FCFF.toInt() else trail)
        fun p(dx: Int, dy: Int, col: Int) = px(c, x + dx * dir, y + dy, col)
        val body = 0xFF9AB0A0.toInt(); val bodyD = 0xFF6E8676.toInt()
        for (dx in -4..3) p(dx, 0, bodyD)
        for (dx in -3..3) p(dx, -1, body)
        val wig = ((now / 300) % 2).toInt()
        p(3, -2, body); p(4, -3 - wig, body); p(2, -3, body); p(2, -4 + wig, body)        // tykadla
        p(4, -4 - wig, 0xFF1A1A20.toInt()); p(2, -5 + wig, 0xFF1A1A20.toInt())
        val sh = intArrayOf(0xFFE8C890.toInt(), 0xFFC49A5A.toInt(), 0xFF8A5E30.toInt(), 0xFF5A3A1E.toInt())
        for (dy in -6..-2) for (dx in -4..0) {                                         // ulita se spirálou
            val cx = dx + 2f; val cy = dy + 4f
            if (cx * cx / 6.5f + cy * cy / 7f > 1f) continue
            val r = kotlin.math.sqrt(cx * cx + cy * cy)
            val ang = kotlin.math.atan2(cy, cx)
            val band = ((r * 1.2f + ang / 3.1416f) % 2f + 2f) % 2f
            p(dx, dy, if (band < 0.7f) sh[1] else if (cx < 0 && cy < 0) sh[0] else sh[2])
        }
        p(-2, -4, sh[3])
    }

    private val pebbleAt = LongArray(fx.pebbles.size) { t0 + 2000L + rnd.nextInt(10000) }

    /** Kamínek se utrhne z vršku sutě, kutálí se dolů, poskočí a zůstane chvíli ležet. */
    private fun pebbles(c: Canvas, now: Long) {
        for ((i, p) in fx.pebbles.withIndex()) {
            val ms = now - pebbleAt[i]
            if (ms < 0) continue
            if (ms > 2600) { pebbleAt[i] = now + 6000L + rnd.nextInt(14000); continue }
            val k = (ms / 900f).coerceAtMost(1f)
            val ease = k * k
            var x = p.x0 + (p.x1 - p.x0) * ease
            var y = p.y0 + (p.y1 - p.y0) * ease
            if (ms in 900..1300) { val b = (ms - 900) / 400f; x = p.x1 + b * 4; y = p.y1 - sin(b * PI.toFloat()) * 3 }
            else if (ms > 1300) { x = p.x1 + 4f; y = p.y1.toFloat() }
            val xi = x.roundToInt(); val yi = y.roundToInt()
            val roll = (ms / 120) % 2 == 0L && ms < 1300
            px(c, xi, yi, if (roll) 0xFFAE9A80.toInt() else 0xFF8C7A64.toInt())
            px(c, xi + 1, yi, 0xFF6A5A4A.toInt()); px(c, xi, yi + 1, 0xFF4A3E34.toInt())
            if (ms in 900..1100) { px(c, xi - 2, yi + 1, 0x80BEAA8C.toInt()); px(c, xi + 3, yi + 1, 0x80BEAA8C.toInt()) }   // prášek
        }
    }

    private val crumbleAt = LongArray(fx.crumbles.size) { t0 + 4000L + rnd.nextInt(16000) }
    private val crumbleX = IntArray(fx.crumbles.size)

    /** Ze skalní římsy se uvolní drobek, spadne ke zdi a zvedne obláček prachu. */
    private fun crumbles(c: Canvas, now: Long) {
        for ((i, b) in fx.crumbles.withIndex()) {
            val ms = now - crumbleAt[i]
            if (ms < 0) continue
            if (crumbleX[i] == 0) crumbleX[i] = b.x0 + rnd.nextInt(maxOf(1, b.x1 - b.x0))
            val fallMs = 700L
            if (ms > fallMs + 900) { crumbleAt[i] = now + 9000L + rnd.nextInt(20000); crumbleX[i] = 0; continue }
            val x = crumbleX[i]
            if (ms < fallMs) {
                val k = ms / fallMs.toFloat()
                val y = b.y0 + ((b.y1 - b.y0) * k * k).roundToInt()
                px(c, x, y, 0xFF8C7A64.toInt()); px(c, x, y - 1, 0x60AE9A80)
            } else {
                val k = (ms - fallMs) / 900f
                val a = ((1 - k) * 150).toInt()
                val r = (2 + k * 5).roundToInt()
                for (dx in -r..r step 2) px(c, x + dx, b.y1 - (k * 3).roundToInt() - (if (dx % 4 == 0) 1 else 0), (a shl 24) or 0xBEAA8C)
            }
        }
    }

    private var scorpAt = t0 + 7000L + rnd.nextInt(15000)
    private var scorpBack = false

    /** Štír: rychle přeběhne, uprostřed se zastaví, zvedne a zakmitá ocasem, pak zaleze. */
    private fun scorpion(c: Canvas, now: Long) {
        val path = fx.scorpion ?: return
        if (now < scorpAt) return
        val pts = if (scorpBack) path.reversed() else path
        val legs = pts.zipWithNext { a, b -> hypot((b.first - a.first).toFloat(), (b.second - a.second).toFloat()) }
        val speed = 34f / 1000f
        val pause = 1400L
        val ms = now - scorpAt
        val first = (legs[0] / speed).toLong()
        val total = (legs.sum() / speed).toLong() + pause
        if (ms > total) { scorpAt = now + 18000L + rnd.nextInt(30000); scorpBack = rnd.nextBoolean(); return }
        val (x, y, dir, moving) = when {
            ms < first -> lerp(pts[0], pts[1], ms * speed / legs[0]).let { Quad(it.first, it.second, sign(pts[0], pts[1]), true) }
            ms < first + pause -> Quad(pts[1].first.toFloat(), pts[1].second.toFloat(), sign(pts[0], pts[1]), false)
            else -> lerp(pts[1], pts[2], ((ms - first - pause) * speed / legs[1]).coerceAtMost(1f)).let { Quad(it.first, it.second, sign(pts[1], pts[2]), true) }
        }
        val sx = x.roundToInt(); val sy = y.roundToInt()
        fun p(dx: Int, dy: Int, col: Int) = px(c, sx + dx * dir, sy + dy, col)
        val shell = 0xFF7A4A22.toInt(); val dark = 0xFF4A2A12.toInt(); val lite = 0xFFA8703A.toInt()
        for (dx in -2..2) { p(dx, -1, shell); p(dx, 0, dark) }
        p(-1, -2, lite); p(0, -2, lite)
        val step = moving && (now / 70) % 2 == 0L
        for (dx in intArrayOf(-2, 0, 2)) p(dx + (if (step) 1 else 0), 1, dark)                    // nohy
        p(3, -1, shell); p(4, -2, shell); p(4, 0, shell); p(5, -2, lite); p(5, 0, lite)          // klepeta
        val curl = if (!moving) ((now / 160) % 3).toInt() else 0
        p(-3, -1, shell); p(-4, -2, shell); p(-4, -3 - curl % 2, shell); p(-3, -4 - curl % 2, shell)  // ocas
        p(-2, -4 - curl % 2, 0xFF2A1408.toInt())                                                     // žihadlo
    }

    private var devilAt = t0 + 12000L + rnd.nextInt(18000)
    private var devilX = 0f; private var devilY = 0f

    /** Prachový vír: malá točící se spirála prachu přejede kus podlahy a rozplyne se. */
    private fun dustDevil(c: Canvas, now: Long) {
        val b = fx.dustDevil ?: return
        if (now < devilAt) return
        val ms = now - devilAt
        if (ms > 3200) { devilAt = now + 20000L + rnd.nextInt(30000); devilX = 0f; return }
        if (devilX == 0f) { devilX = (b.x0 + rnd.nextInt(maxOf(1, (b.x1 - b.x0) / 2))).toFloat(); devilY = (b.y0 + rnd.nextInt(maxOf(1, b.y1 - b.y0))).toFloat() }
        val k = ms / 3200f
        val cx = devilX + k * 46f; val cy = devilY - k * 6f
        val strength = sin(k * PI.toFloat())
        for (j in 0 until 14) {
            val h = j / 13f
            val a = ms / 90f + j * 0.9f
            val r = 1.5f + h * 4.5f * strength
            val x = cx + kotlin.math.cos(a) * r
            val y = cy - h * 12f + sin(a) * r * 0.3f
            px(c, x.roundToInt(), y.roundToInt(), (((1 - h) * 170 * strength).toInt() shl 24) or 0xBEAA8C)
        }
    }

    /** Krystaly „dýchají“: kruh světla kolem nich pomalu sílí a slábne (pixelové prstence). */
    private fun glows(c: Canvas, t: Float) {
        for ((i, g) in fx.glows.withIndex()) {
            val k = (sin(t * 2f * PI.toFloat() / g.periodS + i * 1.7f) + 1f) / 2f
            for (ring in 0..2) {
                val f = 1f - ring * 0.3f
                val rx = g.rx * f; val ry = g.ry * f
                val a = (k * 22 * (ring + 1) / 3).toInt()
                if (a <= 0) continue
                for (dy in -ry.toInt()..ry.toInt()) {
                    val hw = (rx * sqrt((1f - (dy / ry) * (dy / ry)).coerceAtLeast(0f))).roundToInt()
                    if (hw > 0) row(c, g.cx - hw, g.cx + hw, g.cy + dy, (a shl 24) or g.color)
                }
            }
        }
    }

    /** Jiskřičky na krystalech: náhodný pixel se na okamžik rozsvítí bíle (křížek). */
    private fun sparkles(c: Canvas, t: Float) {
        for ((bi, b) in fx.sparkles.withIndex()) for (i in 0 until 3) {
            val slot = (t * 2.2f + i * 0.33f + bi * 0.21f).toInt()
            val ph = (t * 2.2f + i * 0.33f + bi * 0.21f) % 1f
            val h = ((slot * 2654435761L + bi * 97 + i * 31).toInt() ushr 1)
            val x = b.x0 + h % maxOf(1, b.x1 - b.x0); val y = b.y0 + (h / 7) % maxOf(1, b.y1 - b.y0)
            if (ph > 0.5f) continue
            val a = ((0.5f - kotlin.math.abs(ph - 0.25f) * 2) * 2 * 255).toInt().coerceIn(0, 255)
            px(c, x, y, (a shl 24) or 0xFFFFFF)
            if (ph in 0.15f..0.35f) { px(c, x - 1, y, (a / 2 shl 24) or 0xE0FFFF); px(c, x + 1, y, (a / 2 shl 24) or 0xE0FFFF)
                px(c, x, y - 1, (a / 2 shl 24) or 0xE0FFFF); px(c, x, y + 1, (a / 2 shl 24) or 0xE0FFFF) }
        }
    }

    private val arcAt = LongArray(fx.arcs.size) { t0 + 5000L + rnd.nextInt(12000) }

    /** Výboj: mezi krystalem a přístrojem přeskočí klikatá jiskra (pár záblesků, pokaždé jiný tvar). */
    private fun arcs(c: Canvas, now: Long) {
        for ((i, a) in fx.arcs.withIndex()) {
            val ms = now - arcAt[i]
            if (ms < 0) continue
            if (ms > 520) { arcAt[i] = now + 8000L + rnd.nextInt(16000); continue }
            if ((ms / 70) % 2 == 1L) continue
            val seed = (ms / 70).toInt() * 31 + i
            val n = 10
            var px0 = a.x0.toFloat(); var py0 = a.y0.toFloat()
            for (k in 1..n) {
                val t = k / n.toFloat()
                val jx = if (k == n) 0f else (((seed * 7 + k * 13) % 7) - 3).toFloat()
                val jy = if (k == n) 0f else (((seed * 5 + k * 11) % 5) - 2).toFloat()
                val x1 = a.x0 + (a.x1 - a.x0) * t + jx; val y1 = a.y0 + (a.y1 - a.y0) * t + jy
                val steps = maxOf(abs(x1 - px0), abs(y1 - py0)).toInt().coerceAtLeast(1)
                for (st in 0..steps) {
                    val x = (px0 + (x1 - px0) * st / steps).roundToInt(); val y = (py0 + (y1 - py0) * st / steps).roundToInt()
                    px(c, x, y, 0xFFE8FFFF.toInt()); px(c, x, y + 1, 0x8060E0F0.toInt())
                }
                px0 = x1; py0 = y1
            }
        }
    }

    private val dripAt = LongArray(fx.drips.size) { t0 + rnd.nextInt(6000).toLong() }

    /** Kapka ze stropu: krátce spadne a na hladině se rozběhne kroužek. */
    private fun drips(c: Canvas, now: Long) {
        for ((i, d) in fx.drips.withIndex()) {
            val ms = now - dripAt[i]
            if (ms < 0) continue
            if (ms > 1500) { dripAt[i] = now + 2500L + rnd.nextInt(5000); continue }
            if (ms < 300) {
                val y = d.second - 10 + (ms / 30).toInt()
                px(c, d.first, y, 0xC0B8E8F8.toInt())
            } else {
                val k = (ms - 300) / 1200f
                val r = (1 + k * 6).roundToInt()
                val a = ((1 - k) * 160).toInt()
                for (dx in -r..r) {
                    val dy = (sqrt((1f - (dx.toFloat() / r).let { it * it }).coerceAtLeast(0f)) * r * 0.45f).roundToInt()
                    px(c, d.first + dx, d.second - dy, (a shl 24) or 0x9AD8EC)
                    px(c, d.first + dx, d.second + dy, (a shl 24) or 0x9AD8EC)
                }
            }
        }
    }

    /** Světlušky na stropě jeskyně: drobné tyrkysové tečky, každá svítí a hasne svým tempem. */
    private fun glowworms(c: Canvas, t: Float) {
        val b = fx.glowworms ?: return
        for (i in 0 until 26) {
            val h = ((i * 2654435761L).toInt() ushr 1)
            val x = b.x0 + h % (b.x1 - b.x0); val y = b.y0 + (h / 13) % (b.y1 - b.y0)
            val a = ((sin(t * (0.6f + (h % 7) * 0.1f) + h % 13) + 1) * 0.5f * 200).toInt()
            if (a > 40) px(c, x, y, (a shl 24) or 0x7AE8EC)
        }
    }

    private var batAt = t0 + 9000L + rnd.nextInt(15000)
    private var batY = 0; private var batBack = false

    /** Netopýr: jednou za čas přeletí místností po vlnovce, křídla mávají. */
    private fun bat(c: Canvas, now: Long) {
        val b = fx.bats ?: return
        if (now < batAt) return
        val ms = now - batAt
        val dur = 2600L
        if (ms > dur) { batAt = now + 16000L + rnd.nextInt(26000); batY = 0; return }
        if (batY == 0) { batY = b.y0 + rnd.nextInt(maxOf(1, b.y1 - b.y0)); batBack = rnd.nextBoolean() }
        val k = ms / dur.toFloat()
        val x = (if (batBack) b.x1 - (b.x1 - b.x0) * k else b.x0 + (b.x1 - b.x0) * k).roundToInt()
        val y = (batY + sin(k * 12f) * 6f).roundToInt()
        val up = (now / 90) % 2 == 0L
        val col = 0xFF2A1E36.toInt(); val ear = 0xFF4A3A5A.toInt()
        px(c, x, y, col); px(c, x + 1, y, col); px(c, x, y - 1, ear); px(c, x + 1, y - 1, ear)
        if (up) { px(c, x - 1, y - 1, col); px(c, x - 2, y - 2, col); px(c, x + 2, y - 1, col); px(c, x + 3, y - 2, col) }
        else { px(c, x - 1, y, col); px(c, x - 2, y + 1, col); px(c, x + 2, y, col); px(c, x + 3, y + 1, col) }
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

        /** Zarostlé patro (4.): paprsky mezi korunami, padající listí, oči v dutině stromu, slimák, prach ve světle. */
        val FOREST = InteriorFx(
            shafts = listOf(FxShaft(66, 96, 232, 9, 0.18f), FxShaft(86, 120, 284, 7, 0.1f), FxShaft(64, 236, 336, 6, 0.14f)),
            leaves = listOf(FxBox(6, 158, 58, 174), FxBox(118, 154, 170, 170), FxBox(6, 244, 58, 260), FxBox(118, 272, 170, 288)),
            peeks = listOf(143 to 185),
            snail = (80 to 310) to (122 to 310),
            motes = listOf(FxBox(56, 110, 120, 300)),
            moteColor = 0xFFF0B8
        )

        /** Skalní patro (5.): kamínky ze sutí, drobky z říms, štír, prachový vír, prach. */
        val ROCK = InteriorFx(
            pebbles = listOf(FxPath(40, 164, 60, 186), FxPath(134, 258, 154, 280), FxPath(44, 298, 64, 320)),
            crumbles = listOf(FxBox(8, 42, 44, 96), FxBox(124, 36, 166, 96), FxBox(32, 68, 58, 96), FxBox(132, 72, 164, 96)),
            scorpion = listOf(16 to 276, 48 to 286, 92 to 302),
            dustDevil = FxBox(20, 280, 120, 330),
            motes = listOf(FxBox(20, 110, 156, 330)),
            moteColor = 0xE8D8B8
        )

        /** Krystalová jeskyně (6.): dýchající krystaly, jiskřičky, výboj ke přístroji, kapky, světlušky, netopýr, lucerna. */
        val CAVE = InteriorFx(
            glows = listOf(FxGlow(40, 186, 30, 20, 0x5AE6F0, 3.4f), FxGlow(140, 206, 30, 20, 0xB478FA, 4.1f), FxGlow(52, 292, 34, 22, 0x5AE6F0, 3.8f)),
            sparkles = listOf(FxBox(26, 160, 54, 190), FxBox(126, 180, 154, 210), FxBox(36, 262, 68, 298)),
            arcs = listOf(FxPath(136, 182, 105, 171)),
            drips = listOf(124 to 300, 146 to 305, 136 to 296),
            glowworms = FxBox(4, 4, 172, 56),
            bats = FxBox(10, 120, 166, 260),
            lamps = listOf(FxLamp(76, 134, 24, 15, null)),
            motes = listOf(FxBox(20, 150, 156, 320)),
            moteColor = 0x9AE8F0
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
    val sunFlicker: Boolean = false,
    val shafts: List<FxShaft> = emptyList(),
    val leaves: List<FxBox> = emptyList(),
    val peeks: List<Pair<Int, Int>> = emptyList(),
    val snail: Pair<Pair<Int, Int>, Pair<Int, Int>>? = null,
    val pebbles: List<FxPath> = emptyList(),
    val crumbles: List<FxBox> = emptyList(),
    val scorpion: List<Pair<Int, Int>>? = null,
    val dustDevil: FxBox? = null,
    val glows: List<FxGlow> = emptyList(),
    val sparkles: List<FxBox> = emptyList(),
    val arcs: List<FxPath> = emptyList(),
    val drips: List<Pair<Int, Int>> = emptyList(),
    val glowworms: FxBox? = null,
    val bats: FxBox? = null
)

/** Pulzující záře (krystal): střed, poloměry, barva RGB a perioda v sekundách. */
data class FxGlow(val cx: Int, val cy: Int, val rx: Int, val ry: Int, val color: Int, val periodS: Float)

/** Úsečka pohybu (odkud – kam), např. kamínek z vršku sutě k zemi. */
data class FxPath(val x0: Int, val y0: Int, val x1: Int, val y1: Int)

/** Paprsek světla: horní okraj [x] na řádku [y0], konec na [y1], šířka [w], posun doprava na řádek [slant]. */
data class FxShaft(val x: Int, val y0: Int, val y1: Int, val w: Int, val slant: Float)

package cz.uhk.macroflow.pokemon.encounter

import cz.uhk.macroflow.pokemon.encounter.Kit.CLIMAX
import cz.uhk.macroflow.pokemon.encounter.Kit.PEAK
import cz.uhk.macroflow.pokemon.encounter.Kit.TELL1
import cz.uhk.macroflow.pokemon.encounter.Kit.TELL2
import cz.uhk.macroflow.pokemon.encounter.Kit.easeOut
import cz.uhk.macroflow.pokemon.encounter.Kit.hex
import cz.uhk.macroflow.pokemon.encounter.Kit.mix
import cz.uhk.macroflow.pokemon.encounter.Kit.p
import cz.uhk.macroflow.pokemon.encounter.Kit.pulse
import cz.uhk.macroflow.pokemon.encounter.Kit.since
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Bitmapa mapy jako ARGB pole (načítá ji Android, v testech ImageIO). */
class Img(val w: Int, val h: Int, val px: IntArray)

/**
 * Intro setkání jako „kamera“ přiblížená do skutečné mapy lokace (1 pixel scény = 1 pixel mapy):
 * pomalý nájezd, filmové pruhy, ztmavení s reflektorem na místo události, stínované pixelové částice.
 * Podtřídy kreslí jen samotnou událost v souřadnicích mapy ([ex], [ey] = místo setkání).
 */
abstract class MapScene(private val map: Img, vw: Int, vh: Int, val ex: Int, val ey: Int, seed: Int,
                        private val anchor: Float = 0.6f) : KitScene(vw, vh, seed) {
    /** Barva stínu, do které se scéna stmívá (každá lokace má vlastní). */
    protected abstract val shade: Int
    protected open val spot = 30f
    protected open val dimMax = 0.5f
    /** Barva úderu (rázová vlna, záblesk) – ladí se zábleskem souboje. */
    protected open val accent = WHITE
    protected var camX = 0; protected var camY = 0
    private val baseX = (ex - w / 2).coerceIn(0, maxOf(0, map.w - w))
    private val baseY = (ey - (h * anchor).toInt()).coerceIn(0, maxOf(0, map.h - h))
    private var lastCam = Int.MIN_VALUE

    override fun paintBase() = fillBase()

    private fun fillBase() {
        for (y in 0 until h) {
            val my = (camY + y).coerceIn(0, map.h - 1)
            for (x in 0 until w) base[y * w + x] = map.px[my * map.w + (camX + x).coerceIn(0, map.w - 1)] or (0xFF shl 24)
        }
    }

    override fun render(t: Long, out: IntArray) {
        val pan = (10 * (1f - easeOut(p(t, 0, 1400)))).roundToInt()           // pomalý nájezd shora
        camX = baseX; camY = (baseY - pan).coerceAtLeast(0)
        val key = camY * 10000 + camX
        if (key != lastCam) { lastCam = key; if (lastCam != Int.MIN_VALUE) fillBase() }
        super.render(t, out)
    }

    final override fun draw(t: Long) {
        before(t)
        grade(t)
        act(t)
        impact(t)
        letterbox(t)
    }

    /** Úpravy obrazu mapy před ztmavením (vlnění trávy, pulzování světel). */
    protected open fun before(t: Long) {}
    /** Událost: znamení, oči, vyvrcholení. */
    protected abstract fun act(t: Long)

    /** Mapa → obrazovka. */
    protected fun sx(x: Float) = x - camX
    protected fun sy(y: Float) = y - camY
    protected val cx get() = (ex - camX).toFloat()
    protected val cy get() = (ey - camY).toFloat()

    /** Ztmavení k barvě [shade] s ditherem; okolí události zůstává ve světle. */
    private fun grade(t: Long) {
        val d = (0.18f + (dimMax - 0.18f) * p(t, TELL1 - 200, CLIMAX)) * (1f - 0.6f * p(t, CLIMAX, CLIMAX + 250))
        val r = spot * (1f + 0.15f * sinf(t * 0.004f))
        for (y in 0 until h) for (x in 0 until w) {
            val k = ((hypot(x - cx, (y - cy) * 1.25f) / r) - 0.55f).coerceIn(0f, 1f)
            val a = d * (0.25f + 0.75f * k)
            val q = floor(a * 6f + bayer(x, y)) / 6f
            if (q > 0f) blend(x, y, shade, q)
        }
    }

    /** Společný filmový úder: soustředné čáry do místa události, rázová vlna, záblesk. */
    private fun impact(t: Long) {
        // čáry houstnou a sbíhají se k místu události těsně před výbuchem
        val fl = p(t, CLIMAX - 420, CLIMAX - 40) * (1f - p(t, CLIMAX + 60, CLIMAX + 260))
        if (fl > 0f) {
            val seed = (t / 55).toInt()
            val n = (10 + 22 * fl).toInt()
            for (i in 0 until n) {
                val a = hf(i, seed, 11) * 6.2832f
                val r0 = 26f + 18f * hf(i, seed, 12) - 10f * fl
                val r1 = 120f
                val c = if (i % 3 == 0) accent else BLACK
                line(cx + cosf(a) * r0, cy + sinf(a) * r0, cx + cosf(a) * r1, cy + sinf(a) * r1, c, (if (c == BLACK) 0.55f else 0.45f) * fl)
            }
        }
        val s = since(t)
        if (s > 0f && s < 0.6f) {
            val k = easeOut(minOf(1f, s / 0.45f))
            ring(cx, cy, 3f + k * 70f, 1.2f, accent, 0.85f * (1f - k))
            if (s > 0.08f) ring(cx, cy, 3f + easeOut(minOf(1f, (s - 0.08f) / 0.45f)) * 52f, 0.7f, WHITE, 0.6f * (1f - k))
            if (s < 0.12f) { glow(cx, cy, 30f, WHITE, 1f - s / 0.12f); circle(cx, cy, 4f * (1f - s / 0.12f) + 1f, WHITE) }
        }
    }

    private fun letterbox(t: Long) {
        val bh = ((h * 0.075f) * easeOut(p(t, 0, 450))).roundToInt()
        rect(0, 0, w, bh, BLACK); rect(0, h - bh, w, bh, BLACK)
    }

    protected fun bayer(x: Int, y: Int): Float = BAY[(y and 3) * 4 + (x and 3)]

    // ── Úpravy mapy ──
    /** Vlnění oblasti (tráva, křoví, vlajky): horní řádky se kývou víc. Zdroj = nezměněná mapa. */
    protected fun wobble(x0: Float, y0: Float, x1: Float, y1: Float, amp: Float, t: Long, freq: Float = 0.012f, anchorBottom: Boolean = true) {
        if (abs(amp) < 0.2f) return
        val X0 = sx(x0).toInt().coerceAtLeast(0); val X1 = sx(x1).toInt().coerceAtMost(w - 1)
        val Y0 = sy(y0).toInt().coerceAtLeast(0); val Y1 = sy(y1).toInt().coerceAtMost(h - 1)
        for (y in Y0..Y1) {
            val k = if (anchorBottom) 1f - (y - Y0).toFloat() / (Y1 - Y0 + 1) else 1f
            val off = (amp * k * sinf(t * freq + y * 0.35f)).roundToInt()
            if (off == 0) continue
            for (x in X0..X1) put(x, y, base[y * w + (x - off).coerceIn(X0, X1)])
        }
    }

    /** Zesvětlí pixely mapy, které splňují [pick] (oheň, krystaly, láva …) – „dýchající“ světla. */
    protected fun tintWhere(x0: Float, y0: Float, x1: Float, y1: Float, col: Int, a: Float, pick: (Int) -> Boolean) {
        if (a <= 0f) return
        for (y in sy(y0).toInt().coerceAtLeast(0)..sy(y1).toInt().coerceAtMost(h - 1))
            for (x in sx(x0).toInt().coerceAtLeast(0)..sx(x1).toInt().coerceAtMost(w - 1))
                if (pick(base[y * w + x])) blend(x, y, col, a)
    }

    // ── Stínované pixelové tvary ──
    /** Koule osvětlená zleva shora, rampa tmavá→světlá, tmavý obrys (kámen, kapka, kus ledu). */
    protected fun ball(x: Float, y: Float, r: Float, ramp: IntArray, a: Float = 1f, lit: Float = 0f) {
        if (r < 1.1f) {                                                                 // drobek: 1–2 px bez obrysu
            val c = ramp[((ramp.size - 1) * (0.55f + 0.45f * lit)).toInt()]
            put(x.roundToInt(), y.roundToInt(), c); if (r > 0.8f) put(x.roundToInt() + 1, y.roundToInt(), ramp[((ramp.size - 1) * (0.3f + 0.4f * lit)).toInt()])
            return
        }
        for (yy in floor(y - r).toInt()..(y + r).toInt()) for (xx in floor(x - r).toInt()..(x + r).toInt()) {
            val dx = (xx + 0.5f - x) / r; val dy = (yy + 0.5f - y) / r; val d2 = dx * dx + dy * dy
            if (d2 > 1f) continue
            if (a < 1f && bayer(xx, yy) >= a) continue
            val nz = sqrt(1f - d2)
            val v = (lit + (1f - lit) * (0.2f + 0.8f * (-0.55f * dx - 0.72f * dy + 0.45f * nz).coerceAtLeast(0f))).coerceAtMost(1f)
            val edge = r >= 2.2f && d2 > (1f - 1.1f / r).let { it * it } && dx + dy > -0.4f
            val f = v * (ramp.size - 1); val i = f.toInt()
            val c = if (edge) ramp[0] else ramp[if (f - i > bayer(xx, yy) * 0.8f + 0.1f) minOf(i + 1, ramp.size - 1) else i]
            put(xx, yy, c)
        }
    }

    /** Průsvitný oblak (kouř, prach, pára, mrak) – stínovaný, průhlednost ditherem. */
    protected fun puff(x: Float, y: Float, r: Float, ramp: IntArray, a: Float) {
        if (a <= 0.02f || r < 0.6f) return
        for (yy in floor(y - r).toInt()..(y + r).toInt()) for (xx in floor(x - r).toInt()..(x + r).toInt()) {
            val dx = (xx + 0.5f - x) / r; val dy = (yy + 0.5f - y) / r; val d2 = dx * dx + dy * dy
            if (d2 > 1f) continue
            if (d2 > 0.78f && bayer(xx, yy) > 0.5f) continue                       // měkký, ale pixelový okraj
            val q = floor(a * (1f - d2 * 0.75f) * 4f + 0.5f) / 4f
            if (q <= 0f) continue
            val nz = sqrt(1f - d2)
            val v = (0.25f + 0.75f * (-0.55f * dx - 0.72f * dy + 0.45f * nz).coerceAtLeast(0f)).coerceAtMost(1f)
            blend(xx, yy, ramp[(v * (ramp.size - 1)).roundToInt()], q.coerceAtMost(1f))
        }
    }

    /** Čtyřcípá jiskra (třpyt, hvězdička). */
    protected fun star(x: Float, y: Float, c: Int, big: Boolean) {
        val X = x.roundToInt(); val Y = y.roundToInt()
        put(X, Y, WHITE)
        put(X - 1, Y, c); put(X + 1, Y, c); put(X, Y - 1, c); put(X, Y + 1, c)
        if (big) { blend(X - 2, Y, c, 0.5f); blend(X + 2, Y, c, 0.5f); blend(X, Y - 2, c, 0.5f); blend(X, Y + 2, c, 0.5f) }
    }

    /** Oči v měřítku mapy: dvě svítící zorničky s odleskem a září; [blink] zavře na chvilku. */
    protected fun mapEyes(x: Float, y: Float, gap: Float, c: Int, a: Float, big: Boolean = false) {
        if (a <= 0f) return
        for (s in intArrayOf(-1, 1)) {
            val ex = x + s * gap
            glow(ex, y, if (big) 5f else 3.5f, c, 0.55f * a)
            blend(ex.roundToInt(), y.roundToInt(), c, a); blend(ex.roundToInt() + s, y.roundToInt(), c, a * (if (big) 1f else 0.6f))
            if (big) { blend(ex.roundToInt(), y.roundToInt() - 1, c, a * 0.8f) }
            blend(ex.roundToInt() - (if (s < 0) 0 else 0), y.roundToInt(), WHITE, a * 0.9f)
        }
    }

    /** Odhalení: oči se objeví po TELL2, jednou mrknou, zmizí ve výbuchu. */
    protected fun eyeAlpha(t: Long, at: Long = TELL2): Float {
        val a = p(t, at, at + 180) * (1f - p(t, CLIMAX, CLIMAX + 90))
        val blink = t in (at + 300) until (at + 380)
        return if (blink) 0f else a
    }

    /** Vlnka na hladině / na zemi (elipsa). */
    protected fun ripple(x: Float, y: Float, r: Float, c: Int, a: Float) = ring(x, y, r, 0.5f, c, a, 0.55f)

    /** Kužel světla z (ox, oy) k (tx, ty): pixelově odstupňovaný, slábne do dálky (světlomet, signální lampa). */
    protected fun beam(ox: Float, oy: Float, tx: Float, ty: Float, a: Float, col: Int = hex(0xFFE0A0)) {
        val dx = tx - ox; val dy = ty - oy
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val ux = dx / len; val uy = dy / len
        for (y in 0 until h) for (x in 0 until w) {
            val px = x - ox; val py = y - oy
            val along = px * ux + py * uy
            if (along < 0f || along > len * 1.15f) continue
            val half = 2f + along * 0.22f
            val across = abs(px * uy - py * ux)
            if (across > half) continue
            val k = (1f - across / half) * (1f - along / (len * 1.15f) * 0.5f) * a
            val q = floor(k * 4f) / 4f
            if (q > 0f) blend(x, y, col, q * 0.45f)
        }
        glow(tx, ty, 10f, col, 0.35f * a)
    }

    /** Částice s počáteční rychlostí: [x, y, vx, vy, seed]. */
    protected fun spray(n: Int, vMin: Float, vMax: Float, up: Float, spread: Double = PI * 2, dir: Double = -PI / 2) = List(n) {
        val a = dir + (rnd.nextDouble() - 0.5) * spread
        val v = vMin + rnd.nextFloat() * (vMax - vMin)
        floatArrayOf((cos(a) * v).toFloat(), (sin(a) * v).toFloat() - up * rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat() * 6.28f)
    }

    /** Ambientní částice: [x, y, rychlost, fáze] po celé ploše. */
    protected fun motes(n: Int) = List(n) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 0.4f + rnd.nextFloat(), rnd.nextFloat() * 6.28f) }

    protected fun ramp(vararg c: Int) = IntArray(c.size) { hex(c[it]) }

    protected fun warm(c: Int): Boolean { val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255; return r > 150 && r > b + 60 && g > 60 }
    protected fun bright(c: Int, min: Int = 150): Boolean = (((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255)) / 3 > min

    /** Pixelové plameny: šum stoupající ve směru [dir] (−1 nahoru, +1 dolů), barva podle síly. */
    protected fun flames(x: Float, y: Float, wid: Float, len: Float, ramp: IntArray, t: Long, dir: Int = -1, power: Float = 1f) {
        if (wid < 1f || len < 1f) return
        val y0 = if (dir < 0) y - len else y; val y1 = if (dir < 0) y else y + len
        for (yy in y0.toInt()..y1.toInt()) for (xx in (x - wid).toInt()..(x + wid).toInt()) {
            val along = abs(yy - y) / len
            val across = abs(xx - x) / (wid * (1f - along * 0.55f))
            if (across > 1f) continue
            val n = fbm(xx / 3.2f, (yy - dir * t * 0.08f) / 3.2f, 21)
            val k = n * 0.9f + (1f - across) * (1f - along) * 1.3f * power - 0.85f
            if (k <= 0f) continue
            put(xx, yy, ramp[when { k > 0.8f -> 5; k > 0.55f -> 4; k > 0.35f -> 3; k > 0.16f -> 2; else -> 1 }.coerceAtMost(ramp.size - 1)])
        }
    }

    /** Pták / netopýr shora: tělo, křídla nahoru/dolů, světlý lem na horní hraně. */
    protected fun bird(x: Float, y: Float, up: Boolean, c: Int, rim: Int) {
        val X = x.roundToInt(); val Y = y.roundToInt()
        rect(X, Y, 2, 2, c); put(X, Y, rim)
        val wy = if (up) -1 else 1
        for (s in intArrayOf(-1, 1)) {
            val bx = if (s < 0) X - 1 else X + 2
            put(bx, Y, c); put(bx + s, Y + wy, c); put(bx + 2 * s, Y + 2 * wy, c); put(bx + 3 * s, Y + 2 * wy + (if (up) 0 else -1), c)
            put(bx + s, Y + wy - 1, rim)
        }
    }

    override fun shake(t: Long): Float =
        (if (t in TELL2 until TELL2 + 200) 0.6f else 0f) +
            (if (t in CLIMAX until CLIMAX + 450) 2f * (1f - p(t, CLIMAX, CLIMAX + 450)) else 0f)

    companion object {
        private val BAY = floatArrayOf(0f, 8f, 2f, 10f, 12f, 4f, 14f, 6f, 3f, 11f, 1f, 9f, 15f, 7f, 13f, 5f).map { (it + 0.5f) / 16f }.toFloatArray()
    }
}

// ═══════════════════════════════════════ PATRA VĚŽE ═══════════════════════════════════════

/** Kalibrační louka: vysoká tráva se dvakrát zavlní, v ní oči – tráva vybuchne, létají stébla a hlína. */
class PlainIntro(m: Img, w: Int, h: Int) : MapScene(m, w, h, 138, 262, 3) {
    override val shade = hex(0x10200C)
    override val accent = hex(0xFFF4C0)
    private val blades = spray(46, 20f, 75f, 40f, PI * 1.4)
    private val clods = spray(14, 15f, 50f, 30f, PI * 1.2)
    private val pollen = motes(22)
    private val grassR = ramp(0x24501C, 0x3E7A2A, 0x6AAA3A, 0xA6D85A)
    private val dirt = ramp(0x2E1E10, 0x5A3E22, 0x86643A, 0xB08E5A)
    private val dust = ramp(0x8A7A50, 0xB8A878, 0xE0D4A8)

    override fun before(t: Long) {
        val rustle = 0.5f + 1.6f * pulse(t, TELL1, 450) + 2.2f * pulse(t, TELL2 - 100, 600) + 2f * p(t, CLIMAX - 250, CLIMAX)
        wobble(ex - 20f, ey - 16f, ex + 24f, ey + 8f, rustle, t, 0.018f)
        wobble(ex - 36f, ey - 70f, ex - 14f, ey - 54f, 0.6f, t + 400, 0.01f)
    }

    override fun act(t: Long) {
        for (m in pollen) {
            val x = ((m[0] * w + t * 0.004f * m[2] + 3f * sinf(t * 0.002f + m[3])) % w)
            val y = ((m[1] * h - t * 0.003f * m[2]) % h + h) % h
            if (sinf(t * 0.005f + m[3]) > -0.3f) blend(x.toInt(), y.toInt(), hex(0xFFF4B0), 0.8f)
        }
        // stébla odlétají při zavlnění
        for (i in 0 until 6) { val k = p(t, TELL1 + i * 40L, TELL1 + 600 + i * 40L); if (k in 0.01f..0.99f) blend((cx - 10 + i * 4 + k * 12).toInt(), (cy - 12 - k * 14 + k * k * 10).toInt(), grassR[3], 1f - k) }
        mapEyes(cx + 2, cy - 4, 2f, hex(0xFFE860), eyeAlpha(t))
        val s = since(t)
        if (s > 0f) {
            for (i in 0 until 5) puff(cx + (i - 2) * 6f, cy - 2 - s * 10f, 4f + s * 12f, dust, 0.7f * (1f - s * 1.4f))
            for (b in clods) { val (x, y) = ballistic(cx, cy - 2, b[0], b[1], 90f, s); ball(x, y, 1f + b[2], dirt) }
            for (b in blades) {
                val (x, y) = ballistic(cx + (b[2] - 0.5f) * 20, cy - 4, b[0], b[1], 70f, s)
                val a = b[3] + s * 10f
                line(x, y, x + cosf(a) * 2.5f, y + sinf(a) * 2.5f, grassR[1 + (b[2] * 3).toInt().coerceAtMost(2)])
            }
        }
    }
}

/** Zarostlé patro: z keře vyletí můry, keř se třese, padá listí; dvoje oči – keř se rozletí. */
class ForestIntro2(m: Img, w: Int, h: Int) : MapScene(m, w, h, 128, 268, 4) {
    override val shade = hex(0x081408)
    override val accent = hex(0xC8FFA0)
    private val leaves = spray(90, 20f, 90f, 30f)
    private val fall = motes(18)
    private val moths = List(4) { floatArrayOf(rnd.nextFloat() * 6.28f, 0.6f + rnd.nextFloat() * 0.5f, rnd.nextFloat()) }
    private val leafC = intArrayOf(hex(0x3E8A2E), hex(0x6AB43A), hex(0xA8D85A), hex(0xC88A2A), hex(0x8A5A1E))
    private val spore = ramp(0x6A7A50, 0x9AAE70, 0xD0E0A0)

    override fun before(t: Long) {
        val shakeB = 0.4f + 1.5f * pulse(t, TELL1, 400) + 1.5f * pulse(t, TELL2, 500) + 2.2f * p(t, CLIMAX - 250, CLIMAX)
        wobble(ex - 16f, ey - 26f, ex + 20f, ey + 14f, shakeB, t, 0.02f)
    }

    override fun act(t: Long) {
        for ((i, f) in fall.withIndex()) {
            val cyc = (f[1] + t * 0.00018f * f[2]) % 1f
            val x = f[0] * w + 5f * sinf(cyc * 14f + f[3]); val y = cyc * h
            put(x.toInt(), y.toInt(), leafC[i % leafC.size]); if ((t / 160 + i) % 2 == 0L) put(x.toInt() + 1, y.toInt(), leafC[i % leafC.size])
        }
        // můry vyletí při prvním zachvění
        for (mo in moths) {
            val k = p(t, TELL1 + 60, TELL1 + 1300)
            if (k <= 0f || k >= 1f) continue
            val x = cx + cosf(mo[0]) * k * 40f + sinf(t * 0.02f + mo[0]) * 3f
            val y = cy - 10 - k * 50f * mo[1] + sinf(t * 0.03f + mo[2]) * 2f
            val open = (t / 70) % 2 == 0L
            put(x.toInt(), y.toInt(), hex(0xE8E0C8)); if (open) { put(x.toInt() - 1, y.toInt() - 1, hex(0xC8C0A8)); put(x.toInt() + 1, y.toInt() - 1, hex(0xC8C0A8)) }
        }
        val ea = eyeAlpha(t)
        mapEyes(cx - 5, cy - 8, 1.6f, hex(0xFFD040), ea)
        mapEyes(cx + 6, cy - 2, 1.6f, hex(0xFFD040), ea * p(t, TELL2 + 200, TELL2 + 350))
        val s = since(t)
        if (s > 0f) {
            for (i in 0 until 6) puff(cx + (i - 2.5f) * 5f, cy - 8 - s * 14f, 3f + s * 10f, spore, 0.55f * (1f - s * 1.3f))
            for (b in leaves) {
                val (x, y) = ballistic(cx, cy - 8, b[0], b[1], 60f, s)
                val c = leafC[(b[2] * leafC.size).toInt().coerceAtMost(leafC.size - 1)]
                put(x.toInt(), y.toInt(), c); if (sinf(s * 12f + b[3]) > 0) put(x.toInt() + 1, y.toInt(), c) else put(x.toInt(), y.toInt() + 1, c)
            }
        }
    }
}

/** Skalní patro: kamínky na suti poskakují, dlaždicemi se šíří praskliny; oči mezi kameny – suť vybuchne. */
class RockIntro(m: Img, w: Int, h: Int) : MapScene(m, w, h, 134, 264, 5) {
    override val shade = hex(0x1A120A)
    override val accent = hex(0xFFD090)
    private val stoneR = ramp(0x3A3028, 0x6A5C4C, 0x9C8C76, 0xC8BAA0, 0xE8DCC4)
    private val dust = ramp(0x6A5A44, 0x9A886C, 0xC8B898)
    private val rocks = spray(26, 25f, 90f, 50f)
    private val hops = List(8) { floatArrayOf((rnd.nextFloat() - 0.5f) * 26f, (rnd.nextFloat() - 0.5f) * 8f, rnd.nextFloat()) }
    private val cracks = List(6) { floatArrayOf(it * 1.05f + rnd.nextFloat() * 0.4f, 14f + rnd.nextFloat() * 14f, rnd.nextInt(90).toFloat()) }

    override fun act(t: Long) {
        // praskliny ze suti
        val g = 0.45f * p(t, TELL1, TELL1 + 250) + 0.55f * p(t, TELL2, TELL2 + 300)
        for (c in cracks) {
            val len = c[1] * g; if (len < 1f) continue
            val x1 = cx + cos(c[0]) * len; val y1 = cy + 4 + sin(c[0]) * len * 0.5f
            zigzag(cx, cy + 4, x1, y1, 2f, c[2].toInt(), hex(0x2A2018), 1f, 5)
            zigzag(cx + 1, cy + 4, x1 + 1, y1, 2f, c[2].toInt(), hex(0xD8CCB0), 0.35f, 5)
        }
        // poskakující kamínky + prach
        for (hp in hops) {
            val j = pulse(t, TELL1 + (hp[2] * 120).toLong(), 260) + pulse(t, TELL2 + (hp[2] * 100).toLong(), 300)
            if (j > 0f) { ball(cx + hp[0], cy + hp[1] - j * 4f, 1.2f, stoneR); puff(cx + hp[0], cy + hp[1] + 1, 2f, dust, 0.4f * j) }
        }
        if (t in TELL2 until CLIMAX) glow(cx, cy, 12f, hex(0xFFB040), 0.25f * p(t, TELL2, CLIMAX))
        mapEyes(cx, cy - 1, 2.2f, hex(0xFFB030), eyeAlpha(t))
        val s = since(t)
        if (s > 0f) {
            for (i in 0 until 9) { val a = i * 0.7f; puff(cx + cosf(a) * (4 + s * 26f), cy - 2 + sinf(a) * (2 + s * 10f) - s * 8f, 3f + s * 9f, dust, 0.75f * (1f - s * 1.2f)) }
            for (b in rocks) { val (x, y) = ballistic(cx, cy - 2, b[0], b[1], 110f, s); ball(x, y, 1f + b[2] * 2f, stoneR) }
        }
    }
}

/** Krystalová jeskyně: krystal pulzuje, k menším krystalům přeskakují výboje; v krystalu oči – roztříští se. */
class CaveIntro2(m: Img, w: Int, h: Int) : MapScene(m, w, h, 136, 198, 6) {
    override val shade = hex(0x06020E)
    override val accent = hex(0xE0C0FF)
    override val dimMax = 0.6f
    private val shardR = ramp(0x3A1A6A, 0x7A44C8, 0xB884F4, 0xE8D0FF)
    private val shards = spray(30, 25f, 95f, 30f)
    private val motes = motes(20)
    private fun violet(c: Int): Boolean { val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255; return b > 140 && r > g + 20 }

    override fun before(t: Long) {
        val k = 0.25f + 0.25f * sinf(t * 0.006f) + 0.5f * pulse(t, TELL1, 300) + 0.6f * p(t, TELL2, CLIMAX)
        tintWhere(ex - 18f, ey - 22f, ex + 18f, ey + 14f, hex(0xF0E0FF), 0.45f * k) { violet(it) }
    }

    override fun act(t: Long) {
        for (mo in motes) {
            val x = mo[0] * w + 4f * sinf(t * 0.001f + mo[3]); val y = ((mo[1] * h - t * 0.004f * mo[2]) % h + h) % h
            if (sinf(t * 0.004f + mo[3] * 2) > 0f) blend(x.toInt(), y.toInt(), hex(0xC8A0FF), 0.8f)
        }
        glow(cx, cy - 4, 22f, hex(0x9A5AF0), 0.25f + 0.35f * p(t, TELL2, CLIMAX))
        // výboje ke krystalům v okolí
        for ((i, at) in listOf(TELL1, TELL1 + 200, TELL2 - 60, TELL2 + 160, CLIMAX - 150).withIndex()) {
            val a = pulse(t, at, 150); if (a <= 0f) continue
            val tx = sx(listOf(118f, 156f, 112f, 150f, 130f)[i]); val ty = sy(listOf(150f, 232f, 260f, 176f, 240f)[i])
            zigzag(cx, cy - 6, tx, ty, 3f, i * 13 + (t / 40).toInt(), hex(0x80F0FF), a, 7)
            zigzag(cx, cy - 6, tx, ty, 3f, i * 13 + (t / 40).toInt(), WHITE, a * 0.7f, 7)
            glow(tx, ty, 6f, hex(0x80F0FF), a * 0.6f)
        }
        for (at in listOf(TELL1 + 80, TELL2 + 80)) { val k = p(t, at, at + 650); if (k in 0.01f..0.99f) ring(cx, cy - 4, 4f + k * 34f, 0.6f, hex(0xD8B8FF), 0.55f * (1f - k), 0.7f) }
        mapEyes(cx, cy - 6, 2.2f, hex(0xF4E8FF), eyeAlpha(t), true)
        val s = since(t)
        if (s > 0f) {
            glow(cx, cy - 4, 40f * easeOut(minOf(1f, s * 2f)), hex(0xE0C8FF), 0.7f * (1f - minOf(1f, s * 1.1f)))
            ring(cx, cy - 4, 6f + s * 70f, 1f, hex(0xF0E0FF), 0.7f * (1f - minOf(1f, s * 1.3f)), 0.7f)
            for (b in shards) {
                val (x, y) = ballistic(cx, cy - 6, b[0], b[1], 50f, s)
                val l = 1.5f + b[2] * 2.5f; val a = b[3] + s * 9f
                line(x, y, x + cosf(a) * l, y + sinf(a) * l, shardR[1 + (b[2] * 2.9f).toInt()]); put(x.toInt(), y.toInt(), shardR[3])
            }
        }
    }
}

/** Zatopené patro: pod hladinou krouží stín, stoupají bubliny a kruhy; vynoří se oči – voda vytryskne. */
class WaterIntro2(m: Img, w: Int, h: Int) : MapScene(m, w, h, 44, 250, 7) {
    override val shade = hex(0x02101A)
    override val accent = hex(0xB8F0FF)
    private val drop = ramp(0x2A6A8A, 0x5AAACA, 0xA8E4F4, 0xF0FFFF)
    private val foam = ramp(0x8AC4D4, 0xC8EEF6, 0xFFFFFF)
    private val droplets = spray(50, 20f, 70f, 70f)
    private val bubbles = List(8) { floatArrayOf((rnd.nextFloat() - 0.5f) * 10f, (rnd.nextFloat() - 0.5f) * 6f, rnd.nextFloat() * 600f) }

    override fun act(t: Long) {
        // stín krouží a zmenšuje kruh
        if (t < CLIMAX + 60) {
            val r = 16f * (1f - 0.75f * p(t, TELL1, CLIMAX)); val a = t * 0.004f
            val x = cx + cosf(a) * r; val y = cy + sinf(a) * r * 0.6f
            ellipse(x, y, 6f, 3.5f, hex(0x041A26), 0.55f); ellipse(x - cosf(a + 1.6f) * 5f, y - sinf(a + 1.6f) * 3f, 2.5f, 1.5f, hex(0x041A26), 0.45f)
            if (t > TELL2) mapEyes(x, y - 1, 1.8f, hex(0xFFE070), eyeAlpha(t))
        }
        for (b in bubbles) {
            val ph = ((t + b[2].toLong()) % 700) / 700f
            if (t < TELL1 - 200) continue
            if (ph < 0.75f) ball(cx + b[0], cy + b[1] - ph * 2f, 0.9f + ph * 0.6f, drop)
            else ripple(cx + b[0], cy + b[1] - 1.5f, 1f + (ph - 0.75f) * 20f, foam[1], 1f - (ph - 0.75f) * 4f)
        }
        for (at in listOf(TELL1, TELL1 + 300, TELL2, TELL2 + 250)) { val k = p(t, at, at + 800); if (k in 0.01f..0.99f) ripple(cx, cy, 3f + k * 24f, foam[1], 0.7f * (1f - k)) }
        val s = since(t)
        if (s > 0f) {
            val hgt = 30f * easeOut(minOf(1f, s * 3f)) * (1f - p(t, PEAK + 200, Kit.END) * 0.7f)
            for (y in (cy - hgt).toInt() until cy.toInt()) { val k = (cy - y) / (hgt + 1f); val half = 3.5f - k * 1.5f + sinf(y * 0.8f + t * 0.03f); for (x in (cx - half).toInt()..(cx + half).toInt()) put(x, y, if (x < cx - half * 0.3f) foam[2] else if (x > cx + half * 0.4f) drop[1] else foam[1]) }
            puff(cx, cy - hgt, 4f + s * 4f, foam, 0.9f * (1f - s))
            for (b in droplets) { val (x, y) = ballistic(cx, cy - hgt * 0.8f, b[0], b[1], 110f, s); ball(x, y, 0.6f + b[2] * 1.1f, drop) }
            for (k in 0..2) ripple(cx, cy, 4f + s * (40f + k * 14f), foam[2], 0.8f * (1f - minOf(1f, s * 1.3f)))
        }
    }
}

/** Dormitoř: svíčky blikají, po podlaze leze chladná mlha; z postele se zvedne duch v prostěradle a vzlétne. */
class DormIntro(m: Img, w: Int, h: Int) : MapScene(m, w, h, 53, 220, 8) {
    override val shade = hex(0x05061A)
    override val accent = hex(0xD0D8FF)
    override val dimMax = 0.62f
    private val sheet = ramp(0x3A4478, 0x7884B8, 0xB8C2E8, 0xE8EEFF)
    private val mist = ramp(0x5A6490, 0x8A94C0, 0xB8C0E4)
    private val wisps = spray(24, 15f, 45f, 10f)

    override fun before(t: Long) {
        val fl = 0.25f * sinf(t * 0.03f) * sinf(t * 0.017f) + 0.5f * pulse(t, TELL1, 260) - 0.6f * p(t, TELL2, TELL2 + 200)
        if (fl > 0f) tintWhere(0f, ey - 110f, 100f, ey + 40f, hex(0xFFE0A0), fl * 0.6f) { warm(it) }
        else tintWhere(0f, ey - 110f, 100f, ey + 40f, hex(0x10101C), -fl * 0.9f) { warm(it) }
    }

    override fun act(t: Long) {
        val fa = 0.16f + 0.16f * p(t, TELL1, CLIMAX)                                 // chladná mlha při zemi
        for (y in (h * 0.5f).toInt() until h) for (x in 0 until w) {
            val n = fbm((x + t * 0.008f) / 16f, y / 7f, 31) - 0.55f + (y - h * 0.5f) / h * 0.6f
            if (n > 0f) blend(x, y, mist[2], if (n > 0.12f) fa else fa * 0.5f)
        }
        // duch se zvedá z postele
        val rise = p(t, TELL2 - 250, CLIMAX)
        val s = since(t)
        val gy = cy - 2f - rise * 8f - easeOut(minOf(1f, s * 1.6f)) * 70f
        val gx = cx + sinf(t * 0.005f) * 1.5f + s * 10f
        val ga = rise.coerceAtMost(1f) * 0.92f * (1f - p(t, PEAK + 200, Kit.END))
        if (ga > 0f) ghost(gx, gy, 1.35f + s * 0.3f, ga, t)
        if (s > 0f) for (b in wisps) { val (x, y) = ballistic(cx, cy - 6, b[0], b[1], -20f, s); puff(x, y, 1.5f, mist, 0.8f * (1f - s)) }
    }

    private fun ghost(x: Float, y: Float, sc: Float, a: Float, t: Long) {
        val hw = 6.5f * sc; val top = y - 10f * sc; val bot = y + 8f * sc
        for (yy in top.toInt()..bot.toInt()) {
            val q = (yy - top) / (bot - top)
            val half = if (q < 0.42f) hw * sqrt((1f - ((0.42f - q) / 0.42f).let { it * it }).coerceAtLeast(0f)) else hw + (q - 0.42f) * 6f * sc
            for (xx in (x - half).toInt()..(x + half).toInt()) {
                val hem = q > 0.82f && sinf((xx - x) * 1.3f + t * 0.025f) < (q - 0.82f) * 11f - 1f
                if (hem) continue
                val nx = (xx - x) / (half + 0.01f)
                val v = (0.75f - 0.55f * nx - 0.25f * q).coerceIn(0f, 1f)
                val edge = abs(nx) > 0.82f
                blend(xx, yy, if (edge) sheet[0] else sheet[(v * 3f).roundToInt().coerceIn(1, 3)], floor(a * 4f) / 4f)
            }
        }
        for (s in intArrayOf(-1, 1)) { ellipse(x + s * 2.4f * sc, top + 4.5f * sc, 1f * sc, 1.6f * sc, hex(0x0A0C24), a) }
        ellipse(x, top + 8f * sc, 1.2f * sc, 1f * sc, hex(0x0A0C24), a * 0.8f)
        glow(x, y, 16f, hex(0xB8C8FF), 0.3f * a)
    }
}

/** Mrazírna: od okrajů leze jinovatka, kryokomora praská, zmrzlý tvor otevře oči – led se roztříští. */
class IceIntro(m: Img, w: Int, h: Int) : MapScene(m, w, h, 130, 304, 9, 0.55f) {
    override val shade = hex(0x0A1830)
    override val accent = hex(0xD8FAFF)
    private val iceR = ramp(0x4A7A9A, 0x8AC0DA, 0xC8EEFA, 0xFFFFFF)
    private val snowR = ramp(0xA8BCD0, 0xD8E6F2, 0xFFFFFF)
    private val shards = spray(36, 25f, 90f, 40f)
    private val snow = motes(50)
    private val frost by lazy { FloatArray(w * h) { i -> val x = i % w; val y = i / w; val e = minOf(minOf(x, w - 1 - x).toFloat() / w, minOf(y, h - 1 - y).toFloat() / h * 0.7f); fbm(x / 6f, y / 6f, 5) * 0.16f + noise(x / 2f, y / 2f, 9) * 0.04f - e } }
    private val cracks = List(5) { floatArrayOf(rnd.nextFloat() * 6.28f, 6f + rnd.nextFloat() * 10f, rnd.nextInt(80).toFloat()) }

    override fun act(t: Long) {
        val bx = sx(136f); val by = sy(313f)                                       // kryokomora se zmrzlým tvorem
        val fr = -0.02f + 0.07f * p(t, TELL1, CLIMAX) + 0.04f * p(t, CLIMAX, PEAK)
        for (i in frost.indices) {
            val v = frost[i] + fr - 0.1f; if (v <= 0f) continue
            val x = i % w; val y = i / w
            if (v < 0.012f) blend(x, y, WHITE, 0.85f)                                   // ostrá hrana krystalů
            else if (v < 0.04f) blend(x, y, snowR[1], 0.55f) else blend(x, y, snowR[2], 0.8f)
        }
        val g = 0.5f * p(t, TELL1, TELL1 + 200) + 0.5f * p(t, TELL2, TELL2 + 250)
        if (since(t) <= 0f) for (c in cracks) {
            val l = c[1] * g; if (l < 1f) continue
            zigzag(bx, by - 4, bx + cos(c[0]) * l, by - 4 + sin(c[0]) * l * 0.8f, 1.5f, c[2].toInt(), WHITE, 0.95f, 4)
        }
        glow(bx, by - 4, 16f, hex(0x9AF0FF), 0.2f + 0.35f * p(t, TELL2, CLIMAX))
        mapEyes(sx(144f), sy(309f), 1.2f, hex(0x7AF4FF), eyeAlpha(t), true)
        val s = since(t)
        if (s > 0f) {
            glow(bx, by, 34f * easeOut(minOf(1f, s * 2f)), WHITE, 0.6f * (1f - minOf(1f, s)))
            for (i in 0 until 7) { val a = i * 0.9f; puff(bx + cosf(a) * (3 + s * 22f), by + sinf(a) * (2 + s * 10f), 3f + s * 7f, snowR, 0.8f * (1f - s * 1.3f)) }
            for (b in shards) {
                val (x, y) = ballistic(bx, by - 3, b[0], b[1], 100f, s)
                val l = 1.5f + b[2] * 2.5f; val a = b[3] + s * 10f
                line(x, y, x + cosf(a) * l, y + sinf(a) * l, iceR[1 + (b[2] * 2.9f).toInt()]); put(x.toInt(), y.toInt(), WHITE)
            }
        }
        val gust = 1f + 2f * (pulse(t, TELL1, 450) + pulse(t, TELL2, 450)) + 4f * p(t, CLIMAX, CLIMAX + 200)
        for (f in snow) {
            val x = ((f[0] * w - t * 0.012f * f[2] * gust) % w + w) % w
            val y = (f[1] * h + t * 0.008f * f[2] + 2f * sinf(t * 0.003f + f[3])) % h
            put(x.toInt(), y.toInt(), WHITE); if (f[2] > 1.1f) blend(x.toInt() + 1, y.toInt(), WHITE, 0.5f)
        }
    }
}

/** Spalovna spisů: oheň v peci dýchá, vylétají jiskry a hořící útržky; v plamenech oči – pec vychrlí ohnivou kouli. */
class FireIntro(m: Img, w: Int, h: Int) : MapScene(m, w, h, 88, 262, 10, 0.5f) {
    override val shade = hex(0x140402)
    override val accent = hex(0xFFC060)
    override val spot = 36f
    private val fireR = ramp(0x5A0A04, 0xB82A08, 0xF06A10, 0xFFB030, 0xFFE890, 0xFFFFF0)
    private val smoke = ramp(0x1A1412, 0x3A302C, 0x5A4E48)
    private val sparks = spray(60, 30f, 110f, 40f, PI * 0.9, PI / 2)
    private val embers = motes(30)
    private val scraps = List(5) { floatArrayOf((rnd.nextFloat() - 0.5f) * 40f, -20f - rnd.nextFloat() * 25f, rnd.nextFloat() * 500f, rnd.nextFloat() * 6f) }

    override fun before(t: Long) {
        val k = 0.3f + 0.2f * sinf(t * 0.013f) + 0.15f * sinf(t * 0.031f) + 0.5f * pulse(t, TELL1, 400) + 0.7f * p(t, TELL2, CLIMAX)
        tintWhere(ex - 16f, ey - 16f, ex + 16f, ey + 16f, hex(0xFFF0B0), 0.45f * k) { warm(it) && bright(it, 120) }
    }

    override fun act(t: Long) {
        glow(cx, cy + 4, 30f, hex(0xFF7A20), 0.3f + 0.25f * p(t, TELL2, CLIMAX))
        for (e in embers) {
            val life = ((t * 0.0006f * e[2] + e[1]) % 1f)
            val x = cx + (e[0] - 0.5f) * 20f + sinf(life * 9f + e[3]) * 4f; val y = cy - 2 - life * 70f
            val c = if (life < 0.4f) hex(0xFFE890) else if (life < 0.7f) hex(0xFF9A30) else hex(0xA03010)
            if ((t / 80 + e[3].toLong()) % 5 != 0L) put(x.toInt(), y.toInt(), c)
        }
        for (sc in scraps) {
            val st = TELL1 + sc[2].toLong(); if (t < st) continue
            val q = (t - st) / 1000f; if (q > 1.4f) continue
            val x = cx + sc[0] * q + sinf(q * 7f + sc[3]) * 3f; val y = cy - 4 + sc[1] * q + 18f * q * q
            rect(x.toInt(), y.toInt(), 2, 2, mix(hex(0xE8DCC0), hex(0x2A2018), q * 0.7f)); put(x.toInt() + 2, y.toInt(), hex(0xFFB030)); put(x.toInt(), y.toInt() + 2, hex(0xF06A10))
        }
        if (t in TELL2 until CLIMAX) flames(cx, cy + 6, 9f, 10f + 6f * p(t, TELL2, CLIMAX), fireR, t, -1, 0.7f + 0.5f * p(t, TELL2, CLIMAX))
        mapEyes(cx, cy - 2, 2.6f, hex(0xFFFFF0), eyeAlpha(t), true)
        val s = since(t)
        if (s > 0f) {
            val k = easeOut(minOf(1f, s * 2.6f)) * (1f - 0.5f * p(t, PEAK + 150, Kit.END))
            glow(cx, cy + 10, 40f * k, hex(0xFF8A20), 0.5f)
            flames(cx, cy - 6, 9f + 16f * k, 12f + 46f * k, fireR, t, +1, 1.1f)      // jazyk ohně z dvířek k divákovi
            flames(cx, cy - 4, 7f + 6f * k, 8f + 20f * k, fireR, t, -1, 0.9f)
            for (i in 0 until 6) puff(cx + (i - 2.5f) * 7f, cy - 10 - s * 30f, 4f + s * 10f, smoke, 0.6f * p(t, CLIMAX + 250, PEAK + 200))
            for (b in sparks) { val (x, y) = ballistic(cx, cy + 2, b[0], b[1], 60f, s); put(x.toInt(), y.toInt(), if (b[2] > 0.5f) hex(0xFFE890) else hex(0xFF8A20)); if (b[2] > 0.8f) blend(x.toInt(), y.toInt() - 1, hex(0xFF8A20), 0.6f) }
        }
    }
}

/** Duhová zahrada: houby v kruhu se rozsvítí dokola, víly krouží; uprostřed oči – kruh vybuchne třpytem. */
class FairyIntro(m: Img, w: Int, h: Int) : MapScene(m, w, h, 112, 250, 11) {
    override val shade = hex(0x2A0E3A)
    override val accent = hex(0xFFB8F0)
    override val dimMax = 0.42f
    private val stars = spray(50, 20f, 70f, 30f)
    private val pixies = List(5) { floatArrayOf(rnd.nextFloat() * 6.28f, 12f + rnd.nextFloat() * 14f, 0.7f + rnd.nextFloat() * 0.6f, rnd.nextFloat() * 8f) }
    private val twinkle = motes(28)
    private val cols = intArrayOf(hex(0xFFF0A0), hex(0xFFA8E0), hex(0xA8F0FF), hex(0xD0B0FF), WHITE)
    private val petal = ramp(0xD86AA8, 0xFFA8D8, 0xFFE0F4)

    override fun act(t: Long) {
        for (m in twinkle) if (sinf(t * 0.006f + m[3] * 3f) > 0.6f) star(m[0] * w, m[1] * h, cols[(m[3] * 0.8f).toInt() % cols.size], false)
        val lit = p(t, TELL1, CLIMAX) * 10f
        for (i in 0 until 10) {
            val a = PI * 2 * i / 10 - PI / 2
            val x = cx + (cos(a) * 16).toFloat(); val y = cy + (sin(a) * 9).toFloat()
            val on = (lit - i).coerceIn(0f, 1f)
            if (on > 0f) { glow(x, y - 2, 7f, cols[i % 4], 0.6f * on); if ((t / 90 + i) % 3 == 0L) star(x, y - 4, cols[i % 4], false) }
        }
        val pull = p(t, TELL1, CLIMAX); val s = since(t)
        for (pz in pixies) {
            val a = pz[0] + t * 0.004f * pz[2] * (1f + pull)
            val r = pz[1] * (1f - pull * 0.5f) + s * 80f
            val x = cx + cosf(a) * r; val y = cy - 6f - pz[3] + sinf(a) * r * 0.5f
            for (k in 1..5) blend((cx + cosf(a - k * 0.15f) * r).toInt(), (cy - 6f - pz[3] + sinf(a - k * 0.15f) * r * 0.5f).toInt(), hex(0xFFF0A0), 0.6f - k * 0.1f)
            glow(x, y, 4f, hex(0xFFF0A0), 0.7f); put(x.roundToInt(), y.roundToInt(), WHITE)
            if ((t / 60 + a.toLong()) % 2 == 0L) { put(x.roundToInt() - 1, y.roundToInt() - 1, hex(0xC8F0FF)); put(x.roundToInt() + 1, y.roundToInt() - 1, hex(0xC8F0FF)) }
        }
        mapEyes(cx, cy - 2, 2.2f, hex(0xFF7AD8), eyeAlpha(t), true)
        if (s > 0f) {
            val k = easeOut(minOf(1f, s * 2f))
            glow(cx, cy - 2, 40f * k, WHITE, 0.6f * (1f - minOf(1f, s * 1.2f)))
            for ((i, c) in cols.withIndex()) ring(cx, cy - 2, 4f + s * (50f + i * 6f), 0.7f, c, 0.7f * (1f - minOf(1f, s * 1.4f)), 0.6f)
            for ((i, b) in stars.withIndex()) {
                val (x, y) = ballistic(cx, cy - 4, b[0], b[1], 30f, s)
                if (i % 4 == 0) ball(x, y, 1f, petal) else star(x, y, cols[i % cols.size], b[2] > 0.7f)
            }
        }
    }
}

/** Jedová laboratoř: louže bublá a kouří, mlha houstne; ve sliznu se otevřou oči – louže vybuchne. */
class PoisonIntro(m: Img, w: Int, h: Int) : MapScene(m, w, h, 90, 178, 12) {
    override val shade = hex(0x020A04)
    override val accent = hex(0xC8FF70)
    override val dimMax = 0.58f
    private val slime = ramp(0x1A4A0C, 0x3E8A1A, 0x7ACC30, 0xC4F070, 0xF4FFC0)
    private val gas = ramp(0x2A5A1A, 0x5A9A3A, 0x9ACC6A)
    private val blobs = spray(34, 25f, 80f, 60f)
    private val bubbles = List(7) { floatArrayOf((rnd.nextFloat() - 0.5f) * 18f, (rnd.nextFloat() - 0.5f) * 6f, rnd.nextFloat() * 700f, 0.8f + rnd.nextFloat()) }
    private val wisps = List(9) { floatArrayOf((rnd.nextFloat() - 0.5f) * 24f, rnd.nextFloat(), 0.5f + rnd.nextFloat(), rnd.nextFloat() * 6f) }
    private fun toxic(c: Int): Boolean { val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255; return g > 120 && g > r + 30 && g > b + 50 }

    override fun before(t: Long) {
        val k = 0.25f + 0.2f * sinf(t * 0.007f) + 0.4f * pulse(t, TELL1, 450) + 0.6f * p(t, TELL2, CLIMAX)
        tintWhere(ex - 16f, ey - 10f, ex + 16f, ey + 10f, hex(0xE8FFA0), 0.4f * k) { toxic(it) }
    }

    override fun act(t: Long) {
        glow(cx, cy, 24f, hex(0x7AE83A), 0.3f + 0.3f * p(t, TELL2, CLIMAX), 0.6f)
        val boil = 1f + 2f * p(t, TELL1, CLIMAX)
        for (b in bubbles) {
            val ph = (((t * boil).toLong() + b[2].toLong()) % 800) / 800f
            if (ph < 0.8f) ball(cx + b[0], cy + b[1], 0.6f + ph * 1.4f * b[3], slime)
            else ripple(cx + b[0], cy + b[1], 1f + (ph - 0.8f) * 18f, slime[3], 1f - (ph - 0.8f) * 5f)
        }
        for (wp in wisps) {
            val life = (wp[1] + t * 0.0004f * wp[2]) % 1f
            puff(cx + wp[0] + sinf(life * 6f + wp[3]) * 3f, cy - life * 40f, 2f + life * 4f, gas, (0.45f + 0.3f * p(t, TELL1, CLIMAX)) * (1f - life))
        }
        mapEyes(cx + 1, cy - 1, 2.4f, hex(0xF4FF60), eyeAlpha(t), true)
        val s = since(t)
        if (s > 0f) {
            val k = easeOut(minOf(1f, s * 3f))
            for (y in (cy - 22 * k).toInt() until cy.toInt()) { val q = (cy - y) / (22 * k + 1); val half = 4f * (1f - q) + 1.5f + sinf(y * 0.9f + t * 0.03f) * 0.6f; for (x in (cx - half).toInt()..(cx + half).toInt()) put(x, y, if (x < cx - half * 0.2f) slime[3] else slime[2]) }
            for (b in blobs) { val (x, y) = ballistic(cx, cy - 10 * k, b[0], b[1], 120f, s); ball(x, y, 0.8f + b[2] * 1.5f, slime, 1f, 0.35f) }
            for (i in 0 until 8) { val a = i * 0.8f; puff(cx + cosf(a) * (4 + s * 22f), cy - 4 + sinf(a) * (2 + s * 9f) - s * 10f, 4f + s * 9f, gas, 0.7f * (1f - s * 1.1f)) }
        }
    }
}

// ═══════════════════════════════════════ VENKOVNÍ LOKACE ═══════════════════════════════════════

/** Nebeský průsmyk: praporky se třepetají, vítr žene mraky; přes stezku přelétne obří stín, z mraků oči – mrak se rozfoukne. */
class SkyPassIntro(m: Img, w: Int, h: Int) : MapScene(m, w, h, 112, 312, 51) {
    override val shade = hex(0x140A2A)
    override val accent = hex(0xE8F4FF)
    override val dimMax = 0.45f
    private val cloudR = ramp(0x8A84AA, 0xC0BCD8, 0xE8E6F6, 0xFFFFFF)
    private val streaks = List(18) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 4f + rnd.nextFloat() * 8f, 0.6f + rnd.nextFloat()) }
    private val puffs = spray(16, 25f, 70f, 10f)

    private fun gust(t: Long) = 0.4f + pulse(t, TELL1, 500) * 1.3f + pulse(t, TELL2, 600) * 1.6f + if (t >= CLIMAX) 2.5f * (1f - p(t, PEAK, Kit.END)) else 0f

    override fun before(t: Long) {
        val g = gust(t)
        wobble(ex - 50f, ey - 4f, ex + 30f, ey + 6f, 1f + g, t, 0.03f, false)                 // praporky
        wobble(ex - 50f, ey + 58f, ex + 30f, ey + 68f, 0.8f + g * 0.8f, t + 300, 0.03f, false)
    }

    override fun act(t: Long) {
        val g = gust(t)
        // stín obřího tvora přelétne přes stezku
        val k = p(t, TELL2 - 350, TELL2 + 450)
        if (k in 0.01f..0.99f) {
            val x = -20f + k * (w + 40f); val y = cy + 30f - k * 40f
            ellipse(x, y, 9f, 4f, hex(0x0A0820), 0.5f)
            for (sd in intArrayOf(-1, 1)) for (i in 0 until 14) { val q = i / 14f; ellipse(x - 2f + q * 3f, y + sd * (3f + i * 1.3f), 2.5f * (1f - q) + 0.5f, 1.2f, hex(0x0A0820), 0.45f) }
        }
        mapEyes(cx + 2, cy - 2, 2.2f, hex(0xA8E4FF), eyeAlpha(t, TELL2 + 300), true)
        for (st in streaks) {
            val a = (g * 0.35f).coerceAtMost(0.8f); if (a < 0.1f) continue
            val x = ((st[0] * (w + 20) - t * 0.06f * st[3]) % (w + 20) + w + 20) % (w + 20) - 10f
            line(x, st[1] * h, x + st[2], st[1] * h, WHITE, a)
        }
        val s = since(t)
        if (s > 0f) {
            for (b in puffs) { val (x, y) = ballistic(cx, cy, b[0], b[1], -6f, s); puff(x, y, 3f + b[2] * 4f + s * 4f, cloudR, 0.9f * (1f - p(t, PEAK, Kit.END))) }
            glow(cx, cy, 30f * easeOut(minOf(1f, s * 2f)), hex(0xE0F0FF), 0.5f * (1f - minOf(1f, s)))
        }
    }
}

/** Zapomenutý háj: runy na kamenech se rozsvítí, po hladině jezírka běží kruhy; z hvězdné hladiny se dívají oči – vytryskne světlo. */
class GroveIntro(m: Img, w: Int, h: Int) : MapScene(m, w, h, 88, 160, 46) {
    override val shade = hex(0x020C0E)
    override val accent = hex(0xA8FFE8)
    override val dimMax = 0.55f
    private val water = ramp(0x1A3A6A, 0x4A7AB8, 0xA8D0F4, 0xF0FAFF)
    private val spores = motes(30)
    private val drops = spray(40, 15f, 55f, 40f)
    private fun runeC(c: Int): Boolean { val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255; return b > 160 && g > 150 && r < 140 }

    override fun before(t: Long) {
        val k = 0.3f + 0.3f * sinf(t * 0.004f) + 0.6f * pulse(t, TELL1, 600) + 0.8f * p(t, TELL2, CLIMAX)
        tintWhere(0f, 0f, 200f, 400f, WHITE, 0.55f * k) { runeC(it) }
    }

    override fun act(t: Long) {
        for (sp in spores) {
            val x = sp[0] * w + 5f * sinf(t * 0.0012f + sp[3]); val y = ((sp[1] * h - t * 0.003f * sp[2]) % h + h) % h
            val a = 0.5f + 0.5f * sinf(t * 0.006f + sp[3] * 3f)
            if (a > 0.3f) { glow(x, y, 2.5f, hex(0x9AF8E0), 0.4f * a); put(x.toInt(), y.toInt(), hex(0xD8FFF4)) }
        }
        for (at in listOf(TELL1, TELL1 + 350, TELL2, TELL2 + 300)) { val k = p(t, at, at + 900); if (k in 0.01f..0.99f) ring(cx, cy, 2f + k * 18f, 0.5f, hex(0xC8E8FF), 0.7f * (1f - k), 0.6f) }
        val ea = eyeAlpha(t)
        if (ea > 0f) { glow(cx, cy, 12f, hex(0x9AF8E0), 0.4f * ea, 0.6f); mapEyes(cx, cy - 1, 3f, hex(0xE0FFF8), ea, true) }
        val s = since(t)
        if (s > 0f) {
            val k = easeOut(minOf(1f, s * 2.5f))
            val fade = 1f - p(t, PEAK, Kit.END)
            for (y in 0 until cy.toInt()) {
                val q = (cy - y) / cy; val half = (3f + 4f * k) * (1f - q * 0.35f)
                for (x in (cx - half - 1).toInt()..(cx + half + 1).toInt()) {
                    val d = abs(x + 0.5f - cx) / half
                    val (c, a) = when { d < 0.35f -> WHITE to 0.85f; d < 0.7f -> hex(0xC8FFF4) to 0.6f; d < 1f -> hex(0x7AE8D0) to 0.35f; else -> hex(0x7AE8D0) to 0.15f }
                    blend(x, y, c, a * fade * k)
                }
            }
            glow(cx, cy, 20f * k, hex(0xC8FFF4), 0.6f * fade)
            for (b in drops) { val (x, y) = ballistic(cx, cy, b[0], b[1], 80f, s); ball(x, y, 0.6f + b[2], water) }
            for (i in 0..2) ring(cx, cy, 4f + s * (30f + i * 12f), 0.6f, hex(0xC8F8FF), 0.8f * (1f - minOf(1f, s * 1.3f)), 0.6f)
        }
    }
}

/** Doly: láva dýchá a bublá, ze stropu padá suť; v lávě se otevřou oči – láva vybuchne. */
class MinesIntro(m: Img, w: Int, h: Int) : MapScene(m, w, h, 88, 254, 49) {
    override val shade = hex(0x0A0204)
    override val accent = hex(0xFFA040)
    override val dimMax = 0.55f
    private val lava = ramp(0x3A0A04, 0x9A2008, 0xE85A10, 0xFFA830, 0xFFE890)
    private val rock = ramp(0x1A1416, 0x3E3434, 0x6A5C58, 0x948680)
    private val smoke = ramp(0x140E0E, 0x2E2424, 0x4A3E3C)
    private val blobs = spray(36, 25f, 90f, 60f)
    private val falling = List(10) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 900f, 0.6f + rnd.nextFloat()) }

    override fun before(t: Long) {
        val k = 0.3f + 0.25f * sinf(t * 0.009f) + 0.5f * pulse(t, TELL1, 450) + 0.7f * p(t, TELL2, CLIMAX)
        tintWhere(0f, 0f, 200f, 600f, hex(0xFFF0A0), 0.4f * k) { warm(it) && bright(it, 110) }
    }

    override fun act(t: Long) {
        glow(cx, cy, 34f, hex(0xFF6A10), 0.3f + 0.3f * p(t, TELL2, CLIMAX), 0.5f)
        val boil = 1f + 2f * p(t, TELL1, CLIMAX)
        for (i in 0 until 6) {
            val ph = (((t * boil).toLong() + i * 170L) % 900) / 900f
            val x = cx - 18f + i * 7f
            if (ph < 0.7f) ball(x, cy + (i % 2) * 2f, 0.8f + ph * 1.8f, lava, 1f, 0.45f) else put(x.toInt(), (cy - (ph - 0.7f) * 20f).toInt(), lava[4])
        }
        for (f in falling) {
            val st = TELL1 + f[1].toLong(); if (t < st) continue
            val q = (t - st) / 1000f; val y = q * q * 140f
            if (y < cy - 4) { ball(f[0] * w, y, f[2], rock); blend((f[0] * w).toInt(), (y - 2).toInt(), rock[2], 0.4f) }
        }
        mapEyes(cx, cy - 1, 2.6f, hex(0xFFFFE0), eyeAlpha(t), true)
        val s = since(t)
        if (s > 0f) {
            for (b in blobs) { val (x, y) = ballistic(cx, cy - 2, b[0], b[1], 120f, s); ball(x, y, 0.8f + b[2] * 1.6f, lava, 1f, 0.5f) }
            for (i in 0 until 7) { val a = i * 0.9f + 0.3f; puff(cx + cosf(a) * (4f + s * 18f), cy - 6 - s * 30f + sinf(a) * (3f + s * 8f), 3f + s * 9f, smoke, 0.75f * p(t, CLIMAX + 100, PEAK)) }
            glow(cx, cy, 40f * easeOut(minOf(1f, s * 2f)), hex(0xFFB040), 0.6f * (1f - minOf(1f, s)))
        }
    }
}

/** Popelavá pláň (u brány): obilí se vlní, vlna v něm míří k divákovi; z obilí oči – vyletí ptáci a plevy. */
class AshIntro(m: Img, w: Int, h: Int) : MapScene(m, w, h, 122, 142, 88) {
    override val shade = hex(0x1A1408)
    override val accent = hex(0xFFE8A0)
    private val chaff = ramp(0x8A6A20, 0xC8A040, 0xF0D47A)
    private val dust = ramp(0x9A8A60, 0xC0B48A, 0xE4DAB8)
    private val bits = spray(80, 20f, 80f, 40f)
    private val birds = List(7) { floatArrayOf((rnd.nextFloat() - 0.5f) * 70f, -40f - rnd.nextFloat() * 40f, rnd.nextFloat() * 6f) }

    override fun before(t: Long) {
        wobble(ex - 26f, ey - 26f, ex + 26f, ey + 30f, 0.7f, t, 0.008f)
        val k = p(t, TELL1, CLIMAX)
        val by = ey + 30f - k * 34f
        wobble(ex - 26f, by - 6f, ex + 26f, by + 6f, 2.2f * (if (k in 0.01f..0.99f) 1f else 0f), t, 0.04f)
    }

    override fun act(t: Long) {
        mapEyes(cx, cy - 2, 2f, hex(0xFFD040), eyeAlpha(t))
        val s = since(t)
        if (s > 0f) {
            for (i in 0 until 8) { val a = i * 0.8f; puff(cx + cosf(a) * (3f + s * 20f), cy + sinf(a) * (2f + s * 8f) - s * 10f, 3f + s * 8f, chaff, 0.7f * (1f - s * 1.2f)) }
            for (b in bits) { val (x, y) = ballistic(cx, cy - 2, b[0], b[1], 60f, s); val a = b[3] + s * 9f; line(x, y, x + cosf(a) * 2f, y + sinf(a) * 2f, chaff[1 + (b[2] * 1.9f).toInt()]) }
            for (bd in birds) {
                val x = cx + bd[0] * s; val y = cy - 6 + bd[1] * s
                val up = sinf(t * 0.05f + bd[2]) > 0
                bird(x, y, up, hex(0x1A1410), hex(0x4A3A2A))
            }
        }
    }
}

/** Ztichlý sad v noci: svítí plody a světlušky, koruna se zachvěje a upustí plod; v koruně oči – vylétnou netopýři. */
class OrchardIntro(m: Img, w: Int, h: Int) : MapScene(m, w, h, 74, 258, 89) {
    override val shade = hex(0x02040E)
    override val accent = hex(0xFFE0A0)
    override val dimMax = 0.5f
    private val fruit = ramp(0x6A2A08, 0xC8601A, 0xFFA040, 0xFFE0A0)
    private val leafC = intArrayOf(hex(0x2A4A3A), hex(0x3E6A4E), hex(0x5A8A6A))
    private val flies = motes(18)
    private val bats = List(9) { floatArrayOf((rnd.nextFloat() - 0.5f) * 90f, -30f - rnd.nextFloat() * 60f, rnd.nextFloat() * 6f) }
    private val leaves = spray(30, 15f, 50f, 20f)

    override fun before(t: Long) {
        val sh = 0.3f + 1.5f * pulse(t, TELL1, 450) + 1.5f * pulse(t, TELL2, 500) + 2f * p(t, CLIMAX - 200, CLIMAX)
        wobble(ex - 18f, ey - 22f, ex + 18f, ey + 14f, sh, t, 0.025f)
        tintWhere(ex - 22f, ey - 26f, ex + 22f, ey + 18f, hex(0xFFE0A0), 0.35f + 0.25f * sinf(t * 0.006f)) { warm(it) }
    }

    override fun act(t: Long) {
        for (f in flies) {
            val x = f[0] * w + 6f * sinf(t * 0.0013f + f[3]); val y = f[1] * h + 4f * cosf(t * 0.0011f + f[3] * 1.4f)
            val a = 0.5f + 0.5f * sinf(t * 0.007f + f[3] * 2f)
            if (a > 0.3f) { glow(x, y, 3f, hex(0xE8FF90), 0.5f * a); put(x.toInt(), y.toInt(), hex(0xF4FFC0)) }
        }
        // plod spadne a odskočí
        val q = (t - TELL1 - 120) / 1000f
        if (q > 0f) {
            val gy = cy + 18f; val y0 = cy - 4f
            var y = y0 + 160f * q * q
            if (y > gy) { val tb = q - sqrt((gy - y0) / 160f); y = gy - (30f * tb - 120f * tb * tb).coerceAtLeast(0f) }
            ball(cx + 6f + q * 4f, y, 1.4f, fruit)
        }
        val ea = eyeAlpha(t)
        mapEyes(cx - 4, cy - 6, 1.6f, hex(0xFFE060), ea)
        mapEyes(cx + 5, cy - 10, 1.6f, hex(0xFFE060), ea * p(t, TELL2 + 150, TELL2 + 300))
        val s = since(t)
        if (s > 0f) {
            for (b in leaves) { val (x, y) = ballistic(cx, cy - 6, b[0], b[1], 40f, s); put(x.toInt(), y.toInt(), leafC[(b[2] * 2.9f).toInt()]) }
            for (bt in bats) {
                val x = cx + bt[0] * s; val y = cy - 8 + bt[1] * s
                bird(x, y, sinf(t * 0.06f + bt[2]) > 0, hex(0x06040C), hex(0x8A9AC8))
            }
        }
    }
}

/** Ozvěnový kaňon: z jeskyně se nesou kruhy ozvěny, po stěně se sypou kamínky a písek; ve tmě oči – z jeskyně vyrazí sesuv. */
class CanyonIntro(m: Img, w: Int, h: Int) : MapScene(m, w, h, 98, 168, 90) {
    override val shade = hex(0x1A0A04)
    override val accent = hex(0xFFD8A0)
    private val rockR = ramp(0x4A2214, 0x8A4428, 0xBA6A3E, 0xDC9A62)
    private val sand = ramp(0xA8784A, 0xD0A070, 0xF0D0A0)
    private val rocks = spray(26, 25f, 80f, 40f, PI * 1.1, PI / 2)
    private val grains = motes(40)

    override fun act(t: Long) {
        for (g in grains) {
            val x = ((g[0] * w + t * 0.025f * g[2]) % w); val y = g[1] * h + sinf(t * 0.004f + g[3]) * 2f
            blend(x.toInt(), y.toInt(), sand[2], 0.6f); blend(x.toInt() - 1, y.toInt(), sand[1], 0.3f)
        }
        for (at in listOf(TELL1, TELL1 + 220, TELL2, TELL2 + 220)) { val k = p(t, at, at + 900); if (k in 0.01f..0.99f) ring(cx, cy, 3f + k * 40f, 0.5f, hex(0xFFF0D0), 0.65f * (1f - k), 0.65f) }
        for (i in 0 until 7) {
            val st = TELL1 + i * 130L; if (t < st) continue
            val q = (t - st) / 1000f; val x = cx - 20f + i * 6f; val y = cy - 40f + 120f * q * q
            if (y < cy - 4) ball(x + sinf(q * 8f) * 1f, y, 0.9f, rockR)
        }
        if (t in TELL2 until CLIMAX) glow(cx, cy, 8f, hex(0xFFB060), 0.25f)
        mapEyes(cx, cy, 2.4f, hex(0xFFC050), eyeAlpha(t), true)
        val s = since(t)
        if (s > 0f) {
            for (i in 0 until 9) { val a = PI.toFloat() * (0.15f + i * 0.09f); puff(cx + cosf(a) * (3 + s * 26f), cy + 2 + sinf(a) * (2 + s * 22f), 3f + s * 8f, sand, 0.8f * (1f - s * 1.2f)) }
            for (b in rocks) { val (x, y) = ballistic(cx, cy, b[0], b[1], 90f, s); ball(x, y, 0.9f + b[2] * 1.6f, rockR) }
        }
    }
}


/** Pozorovatelna B (zóna 3): prasklý světlomet na terase zaprská, kužel světla přejede terasu zprava
 *  a zastaví se na očích ve tmě; po výpadu se zvedne prach a popel. */
class WatchIntro(m: Img, w: Int, h: Int) : MapScene(m, w, h, 112, 196, 13) {
    override val shade = hex(0x04060E)
    override val accent = hex(0xFFD890)
    override val dimMax = 0.7f
    private val dust = ramp(0x5A4A36, 0x8A7658, 0xB8A27E)
    private val bits = spray(22, 20f, 70f, 40f)

    override fun act(t: Long) {
        val ox = sx(74f); val oy = sy(232f)
        val on = (pulse(t, TELL1, 140) + pulse(t, TELL1 + 260, 120) + p(t, TELL2 - 200, TELL2)).coerceAtMost(1f)
        if (on > 0.05f) {
            val sweep = p(t, TELL2, CLIMAX)
            beam(ox, oy, sx(160f) + (cx - sx(160f)) * sweep, cy, on)
        }
        mapEyes(cx, cy - 2, 2.4f, hex(0xE8F4FF), eyeAlpha(t))
        val s = since(t)
        if (s > 0f) for (b in bits) { val (x, y) = ballistic(cx, cy, b[0], b[1], 90f, s); puff(x, y, 1.6f, dust, 0.7f * (1f - s)) }
    }

}


/** Tábor na svahu (zóna 3): lucerna ve stanu výpravy 7 dvakrát zakolísá a zhasne, plachta se vydme, jako by
 *  se uvnitř někdo zvedal, a ve vchodu se rozsvítí oči; při výpadu se z vchodu vyvalí mlha. */
class SlopeCampIntro(m: Img, w: Int, h: Int) : MapScene(m, w, h, 132, 290, 21) {
    override val shade = hex(0x05070F)
    override val accent = hex(0xC8DCFF)
    override val dimMax = 0.68f
    private val mist = ramp(0x3A4A6A, 0x6A7A9A, 0x9AAACA)
    private val wisps = spray(20, 15f, 55f, 20f)

    override fun before(t: Long) {
        val lamp = (1f - p(t, TELL2 - 150, TELL2)) * (0.8f + 0.2f * sinf(t * 0.05f)) - 0.6f * pulse(t, TELL1, 160)
        if (lamp > 0f) tintWhere(cx - 20f, cy - 26f, cx + 20f, cy + 6f, hex(0xFFD890), lamp * 0.35f) { bright(it, 90) }
    }

    override fun act(t: Long) {
        val bulge = p(t, TELL1 + 150, CLIMAX)
        if (bulge > 0f) wobble(cx - 18f, cy - 26f, cx + 18f, cy + 2f, 1.6f * bulge, t, 0.02f)
        mapEyes(cx, cy - 6, 2.2f, hex(0xE0ECFF), eyeAlpha(t))
        val s = since(t)
        if (s > 0f) for (b in wisps) { val (x, y) = ballistic(cx, cy - 4, b[0], b[1], -15f, s); puff(x, y, 2.2f, mist, 0.7f * (1f - s)) }
    }
}


/** Signální útes (zóna 3): signální lampa vyšle přes propast tři krátké záblesky, pak se kužel stočí dolů k okraji
 *  útesu – a v mlze pod hranou se rozsvítí oči; při výpadu se zvedne zelená mlha. */
class SignalIntro(m: Img, w: Int, h: Int) : MapScene(m, w, h, 96, 196, 17) {
    override val shade = hex(0x030806)
    override val accent = hex(0xB8FFD8)
    override val dimMax = 0.6f
    private val mist = ramp(0x2E6A54, 0x5CB496, 0x9AE8C8)
    private val wisps = spray(20, 15f, 50f, 20f)

    override fun act(t: Long) {
        val ox = sx(106f); val oy = sy(143f)
        val flash = pulse(t, Kit.FADE + 100, 110) + pulse(t, Kit.FADE + 330, 110) + pulse(t, Kit.FADE + 560, 110)
        if (flash > 0.05f) beam(ox, oy, sx(48f), sy(110f), flash.coerceAtMost(1f), hex(0xFFE8B0))
        val dip = p(t, TELL2, CLIMAX)
        if (dip > 0f) beam(ox, oy, sx(48f) + (cx - sx(48f)) * dip, sy(110f) + (cy - sy(110f)) * dip, 0.9f, hex(0xFFE8B0))
        mapEyes(cx, cy, 2.4f, hex(0xB8FFD8), eyeAlpha(t))
        val s = since(t)
        if (s > 0f) for (b in wisps) { val (x, y) = ballistic(cx, cy, b[0], b[1], -18f, s); puff(x, y, 2.2f, mist, 0.7f * (1f - s)) }
    }
}


/** Kořenový most (zóna 3): drobná bytost na trnu ve svatyňce Lumenců zesílí, zabliká a zhasne – a za hranou kořene
 *  se v propasti rozsvítí oči; při výpadu se zvedne zelená mlha z hlubiny. */
class BridgeIntro(m: Img, w: Int, h: Int) : MapScene(m, w, h, 70, 352, 19) {
    override val shade = hex(0x030806)
    override val accent = hex(0xC8E8FF)
    override val dimMax = 0.64f
    private val mist = ramp(0x2E6A54, 0x5CB496, 0x9AE8C8)
    private val wisps = spray(20, 15f, 50f, 20f)

    override fun before(t: Long) {
        val glow = (0.7f + 0.3f * sinf(t * 0.03f)) * (1f - p(t, TELL2 - 150, TELL2)) + pulse(t, TELL1, 200)
        if (glow > 0f) tintWhere(sx(46f), sy(292f), sx(62f), sy(308f), hex(0xBFE6FF), glow.coerceAtMost(1f) * 0.5f) { bright(it, 110) }
    }

    override fun act(t: Long) {
        mapEyes(cx, cy, 2.3f, hex(0xC8E8FF), eyeAlpha(t))
        val s = since(t)
        if (s > 0f) for (b in wisps) { val (x, y) = ballistic(cx, cy, b[0], b[1], -18f, s); puff(x, y, 2.2f, mist, 0.7f * (1f - s)) }
    }
}

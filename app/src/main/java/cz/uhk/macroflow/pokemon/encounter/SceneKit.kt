package cz.uhk.macroflow.pokemon.encounter

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Společný základ pixelových inter dalších lokací (čistý Kotlin, bez Androidu).
 * Všechna používají stejnou časovou osu [Kit]: dvě „znamení“, vyvrcholení, záblesk.
 * Podklad se kreslí jednou do [base], každý snímek se na něj dokreslí pohyb.
 */
object Kit {
    const val FADE = 350L        // ze tmy
    const val TELL1 = 600L       // první znamení (něco se pohne)
    const val TELL2 = 1150L      // druhé znamení (oči / blíž)
    const val CLIMAX = 1650L     // vyvrcholení
    const val PEAK = 1950L
    const val REVEAL = 2050L     // záblesk a souboj
    const val END = 2650L
    const val SKIP = 1600L       // klepnutí přeskočí sem

    fun p(t: Long, a: Long, b: Long): Float = ((t - a).toFloat() / (b - a)).coerceIn(0f, 1f)
    fun easeOut(p: Float): Float { val q = 1f - p; return 1f - q * q * q }
    /** Krátký pulz 0→1→0 v okně [a, a+len]. */
    fun pulse(t: Long, a: Long, len: Long): Float = if (t !in a until a + len) 0f else sin(p(t, a, a + len) * Math.PI).toFloat()
    /** Sekundy od vyvrcholení (0 před ním). */
    fun since(t: Long, a: Long = CLIMAX): Float = if (t < a) 0f else (t - a) / 1000f

    fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    fun hex(v: Int): Int = (0xFF shl 24) or v
    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(s: Int) = (((a shr s) and 0xFF) + (((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * k).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

abstract class KitScene(val w: Int, val h: Int, seed: Int) : PixelScene {
    override val width get() = w
    override val height get() = h

    protected val rnd = Random(seed)
    protected val base = IntArray(w * h)
    private val frame = IntArray(w * h)
    private var cur = base
    private var built = false

    /** Statický podklad (volá se jednou, kreslí do [base]). */
    protected abstract fun paintBase()
    /** Pohyb v čase t (kreslí do snímku). */
    protected abstract fun draw(t: Long)
    /** Otřes obrazu v pixelech – výchozí: náraz při vyvrcholení. */
    protected open fun shake(t: Long): Float =
        if (t in Kit.CLIMAX until Kit.CLIMAX + 420) 2.5f * (1f - Kit.p(t, Kit.CLIMAX, Kit.CLIMAX + 420)) else 0f

    override fun render(t: Long, out: IntArray) {
        if (!built) { cur = base; paintBase(); built = true }
        base.copyInto(frame); cur = frame
        draw(t)
        val s = shake(t)
        val dx = if (s > 0f) ((if ((t / 40) % 2 == 0L) 1 else -1) * s).roundToInt() else 0
        val dy = if (s > 1f) ((if ((t / 60) % 2 == 0L) 1 else -1) * (s * 0.5f)).roundToInt() else 0
        val fade = Kit.p(t, 0, Kit.FADE)
        for (y in 0 until h) {
            val sy = (y - dy).coerceIn(0, h - 1)
            for (x in 0 until w) {
                val c = frame[sy * w + (x - dx).coerceIn(0, w - 1)]
                out[y * w + x] = if (fade < 1f) Kit.mix(BLACK, c, fade) else c
            }
        }
    }

    // ── Šum ──
    protected fun hash(x: Int, y: Int, s: Int = 0): Int {
        var n = x * 374761393 + y * 668265263 + s * 982451653
        n = (n xor (n ushr 13)) * 1274126177
        return (n xor (n ushr 16)) and 0x7FFFFFFF
    }
    protected fun hf(x: Int, y: Int, s: Int = 0): Float = (hash(x, y, s) % 1000) / 1000f
    /** Hladký šum 0..1. */
    protected fun noise(x: Float, y: Float, s: Int = 0): Float {
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        val fx = x - xi; val fy = y - yi
        val u = fx * fx * (3 - 2 * fx); val v = fy * fy * (3 - 2 * fy)
        val a = hf(xi, yi, s); val b = hf(xi + 1, yi, s); val c = hf(xi, yi + 1, s); val d = hf(xi + 1, yi + 1, s)
        return (a + (b - a) * u) + ((c + (d - c) * u) - (a + (b - a) * u)) * v
    }
    protected fun fbm(x: Float, y: Float, s: Int = 0): Float =
        noise(x, y, s) * 0.55f + noise(x * 2.1f, y * 2.1f, s + 7) * 0.3f + noise(x * 4.3f, y * 4.3f, s + 13) * 0.15f
    /** Šachovnicový rozptyl pro „pixelové“ přechody. */
    protected fun dither(x: Int, y: Int, a: Float): Boolean = a > BAYER[(y and 3) * 4 + (x and 3)]

    // ── Kreslení (do podkladu v paintBase, jinak do snímku) ──
    protected fun get(x: Int, y: Int): Int = cur[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]
    protected fun put(x: Int, y: Int, c: Int) { if (x in 0 until w && y in 0 until h) cur[y * w + x] = c }
    protected fun blend(x: Int, y: Int, c: Int, a: Float) {
        if (x in 0 until w && y in 0 until h && a > 0f) cur[y * w + x] = Kit.mix(cur[y * w + x], c, a)
    }
    protected fun rect(x: Int, y: Int, rw: Int, rh: Int, c: Int, a: Float = 1f) {
        for (yy in y until y + rh) for (xx in x until x + rw) if (a >= 1f) put(xx, yy, c) else blend(xx, yy, c, a)
    }
    protected fun vgrad(y0: Int, y1: Int, c0: Int, c1: Int, x0: Int = 0, x1: Int = w) {
        for (y in y0 until y1) { val c = Kit.mix(c0, c1, (y - y0).toFloat() / (y1 - y0).coerceAtLeast(1)); for (x in x0 until x1) put(x, y, c) }
    }
    protected fun circle(cx: Float, cy: Float, r: Float, c: Int, a: Float = 1f) = ellipse(cx, cy, r, r, c, a)
    protected fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, c: Int, a: Float = 1f) {
        if (rx < 0.4f || ry < 0.4f || a <= 0f) return
        for (y in floor(cy - ry).toInt()..(cy + ry).toInt()) for (x in floor(cx - rx).toInt()..(cx + rx).toInt()) {
            val nx = (x + 0.5f - cx) / rx; val ny = (y + 0.5f - cy) / ry
            if (nx * nx + ny * ny <= 1f) { if (a >= 1f) put(x, y, c) else blend(x, y, c, a) }
        }
    }
    /** Měkká záře s pixelovým rozptylem. */
    protected fun glow(cx: Float, cy: Float, r: Float, c: Int, a: Float, sy: Float = 1f) {
        if (a <= 0f || r < 0.5f) return
        for (y in floor(cy - r * sy).toInt()..(cy + r * sy).toInt()) for (x in floor(cx - r).toInt()..(cx + r).toInt()) {
            val d = hypot(x + 0.5f - cx, (y + 0.5f - cy) / sy) / r
            if (d < 1f) { val k = (1f - d) * (1f - d) * a; if (k > 0.04f && dither(x, y, minOf(1f, k * 2.2f))) blend(x, y, c, minOf(0.85f, k * 1.6f)) }
        }
    }
    protected fun ring(cx: Float, cy: Float, r: Float, th: Float, c: Int, a: Float, sy: Float = 1f) {
        if (a <= 0f) return
        val R = r + th
        for (y in floor(cy - R * sy).toInt()..(cy + R * sy).toInt()) for (x in floor(cx - R).toInt()..(cx + R).toInt()) {
            val d = hypot(x + 0.5f - cx, (y + 0.5f - cy) / sy)
            if (abs(d - r) <= th) blend(x, y, c, a)
        }
    }
    protected fun line(x0: Float, y0: Float, x1: Float, y1: Float, c: Int, a: Float = 1f) {
        val n = maxOf(abs(x1 - x0), abs(y1 - y0)).toInt().coerceAtLeast(1)
        for (i in 0..n) {
            val x = (x0 + (x1 - x0) * i / n).roundToInt(); val y = (y0 + (y1 - y0) * i / n).roundToInt()
            if (a >= 1f) put(x, y, c) else blend(x, y, c, a)
        }
    }
    /** Lomená čára blesku/praskliny se seedem (stabilní mezi snímky). */
    protected fun zigzag(x0: Float, y0: Float, x1: Float, y1: Float, jag: Float, seed: Int, c: Int, a: Float = 1f, segs: Int = 7) {
        var px = x0; var py = y0
        for (i in 1..segs) {
            val k = i.toFloat() / segs
            val off = if (i == segs) 0f else (hf(i, seed, 3) - 0.5f) * 2f * jag
            val nx = x0 + (x1 - x0) * k + off * (y1 - y0).sign()
            val ny = y0 + (y1 - y0) * k - off * (x1 - x0).sign() * 0.6f
            line(px, py, nx, ny, c, a); px = nx; py = ny
        }
    }
    private fun Float.sign(): Float = if (this > 0f) 1f else if (this < 0f) -1f else 0.5f

    /** Pár svítících očí (záře, duhovka, odlesk). */
    protected fun eyes(cx: Float, cy: Float, gap: Float, c: Int, a: Float, size: Float = 1f, slant: Float = 0f) {
        if (a <= 0f) return
        for (s in intArrayOf(-1, 1)) {
            val ex = cx + s * gap
            val ey = cy + s * slant
            glow(ex, ey, 4f * size, c, 0.5f * a)
            ellipse(ex, ey, 1.4f * size, 1f * size, c, a)
            blend(ex.roundToInt(), ey.roundToInt(), WHITE, a)
        }
    }

    /** Okamžitá pozice částice s počáteční rychlostí a gravitací. */
    protected fun ballistic(x: Float, y: Float, vx: Float, vy: Float, g: Float, s: Float) = Pair(x + vx * s, y + vy * s + g * s * s)
    protected fun cosf(a: Float) = cos(a)
    protected fun sinf(a: Float) = sin(a)

    companion object {
        val BLACK = Kit.rgb(0, 0, 0)
        val WHITE = Kit.rgb(255, 255, 255)
        private val BAYER = floatArrayOf(0f, 8f, 2f, 10f, 12f, 4f, 14f, 6f, 3f, 11f, 1f, 9f, 15f, 7f, 13f, 5f).map { (it + 0.5f) / 16f }.toFloatArray()
    }
}

/** Intro jedné lokace: mapa (drawable bez přípony), barva záblesku (ARGB) a scéna nad výřezem mapy. */
class LocationIntro(val map: String, val flash: Int, val make: (Img, Int, Int) -> PixelScene) {
    val revealAt get() = Kit.REVEAL
    val end get() = Kit.END
    val skipTo get() = Kit.SKIP
    /** Šířka scény v pixelech mapy (1 pixel mapy = 1 pixel scény). */
    val targetWidth get() = 72
}

object LocationIntros {
    private fun argb(r: Int, g: Int, b: Int) = (235 shl 24) or (r shl 16) or (g shl 8) or b

    /** Podle názvu BiomeType (balíček zůstává bez Androidu); null = lokace má vlastní starší intro. */
    fun forBiome(name: String): LocationIntro? = when (name) {
        "SKY_PASS" -> LocationIntro("sky_pass", argb(236, 244, 255)) { m, w, h -> SkyPassIntro(m, w, h) }
        "HIDDEN_GROVE" -> LocationIntro("hidden_grove", argb(210, 255, 240)) { m, w, h -> GroveIntro(m, w, h) }
        "MINES" -> LocationIntro("mines", argb(255, 200, 120)) { m, w, h -> MinesIntro(m, w, h) }
        "ASH_PLAIN" -> LocationIntro("ash_plain", argb(255, 240, 200)) { m, w, h -> AshIntro(m, w, h) }
        "QUIET_ORCHARD" -> LocationIntro("quiet_orchard_night", argb(220, 230, 255)) { m, w, h -> OrchardIntro(m, w, h) }
        "ECHO_CANYON" -> LocationIntro("echo_canyon", argb(255, 230, 190)) { m, w, h -> CanyonIntro(m, w, h) }
        "TOWER_PLAIN" -> LocationIntro("tower_plain", argb(255, 250, 220)) { m, w, h -> PlainIntro(m, w, h) }
        "TOWER_FOREST" -> LocationIntro("tower_forest", argb(220, 255, 200)) { m, w, h -> ForestIntro2(m, w, h) }
        "TOWER_ROCK" -> LocationIntro("tower_rock", argb(245, 225, 190)) { m, w, h -> RockIntro(m, w, h) }
        "TOWER_CAVE" -> LocationIntro("tower_cave", argb(236, 214, 255)) { m, w, h -> CaveIntro2(m, w, h) }
        "TOWER_WATER" -> LocationIntro("tower_water", argb(200, 240, 255)) { m, w, h -> WaterIntro2(m, w, h) }
        "TOWER_DORM" -> LocationIntro("tower_dorm", argb(226, 222, 255)) { m, w, h -> DormIntro(m, w, h) }
        "TOWER_ICE" -> LocationIntro("tower_ice", argb(235, 250, 255)) { m, w, h -> IceIntro(m, w, h) }
        "TOWER_FIRE" -> LocationIntro("tower_fire", argb(255, 214, 140)) { m, w, h -> FireIntro(m, w, h) }
        "TOWER_FAIRY" -> LocationIntro("tower_fairy", argb(255, 228, 248)) { m, w, h -> FairyIntro(m, w, h) }
        "TOWER_POISON" -> LocationIntro("tower_poison", argb(214, 255, 170)) { m, w, h -> PoisonIntro(m, w, h) }
        "ROOT_WATCH" -> LocationIntro("root_watch", argb(255, 232, 180)) { m, w, h -> WatchIntro(m, w, h) }
        "ROOT_SLOPECAMP" -> LocationIntro("root_slopecamp", argb(220, 236, 255)) { m, w, h -> SlopeCampIntro(m, w, h) }
        else -> null
    }
}

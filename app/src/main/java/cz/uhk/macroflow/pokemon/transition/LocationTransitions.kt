package cz.uhk.macroflow.pokemon.transition

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * Přechody mezi lokacemi (docs/adr/0042) – každá lokace má vlastní pixelovou scénu přes celou
 * obrazovku, stejně jako vstup do jeskyně. Čistý Kotlin, pokryto testy.
 *
 * Scéna nejdřív obrazovku zakryje (v čase [TransitionScene.coveredAt] je celá neprůhledná –
 * pod ní se vymění mapa), pak se sama „otevře“: průhledné pixely nechají prosvítat novou mapu.
 */
interface TransitionScene {
    val w: Int
    val h: Int
    val coveredAt: Long
    /** Od kdy se scéna otevírá – do té doby může počkat, než se nová mapa vykreslí. */
    val openAt: Long
    val end: Long
    /** Vykreslí snímek do [out] (ARGB, 0 = průhledné). */
    fun render(t: Long, out: IntArray)
}

object LocationTransitions {
    /** Scéna podle cílové lokace, null = obyčejné prolnutí. */
    fun forBiome(biome: String, w: Int, h: Int): TransitionScene? = when (biome) {
        "TOWN" -> GateScene(w, h)
        "MEADOW" -> GrassScene(w, h)
        "FOREST" -> LeafScene(w, h)
        "MOUNTAINS" -> MistScene(w, h)
        else -> null
    }

    internal fun progress(t: Long, start: Long, end: Long): Float = ((t - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    internal fun easeInOut(p: Float): Float = if (p < 0.5f) 2 * p * p else 1 - (-2 * p + 2).let { it * it } / 2
    internal fun easeOut(p: Float): Float = 1 - (1 - p).let { it * it * it }
    internal fun easeIn(p: Float): Float = p * p * p

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

    /** Barva s průhledností 0–1. */
    internal fun alpha(c: Int, a: Float): Int = ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or (c and 0xFFFFFF)

    internal val BAYER = floatArrayOf(0f, 8f, 2f, 10f, 12f, 4f, 14f, 6f, 3f, 11f, 1f, 9f, 15f, 7f, 13f, 5f)
        .map { it / 16f - 0.5f }.toFloatArray()

    internal fun bayer(x: Int, y: Int) = BAYER[(y and 3) * 4 + (x and 3)]

    /** Hodnotový šum 0..1 se třemi oktávami. */
    internal fun fbm(x: Float, y: Float): Float {
        var sum = 0f; var amp = 0.5f; var fx = x; var fy = y
        repeat(3) { sum += amp * valueNoise(fx, fy); fx *= 2.03f; fy *= 2.03f; amp *= 0.5f }
        return sum / 0.875f
    }

    private fun valueNoise(x: Float, y: Float): Float {
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        val fx = x - xi; val fy = y - yi
        val sx = fx * fx * (3 - 2 * fx); val sy = fy * fy * (3 - 2 * fy)
        val a = hash(xi, yi); val b = hash(xi + 1, yi); val c = hash(xi, yi + 1); val d = hash(xi + 1, yi + 1)
        return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sy
    }
}

private typealias LT = LocationTransitions

// ─────────────────────────────────────────────────────────────────────────────
// Město: zavřou se dřevěné městské brány se znakem Makroballu, pak se otevřou do světla
// ─────────────────────────────────────────────────────────────────────────────

class GateScene(override val w: Int, override val h: Int) : TransitionScene {
    override val coveredAt = 720L
    override val end = 1700L
    private val closeEnd = 700L
    private val openStart = 980L
    override val openAt = openStart

    private val WOOD = intArrayOf(0xFF3A2212.toInt(), 0xFF5A3620.toInt(), 0xFF7A4A2A.toInt(), 0xFF96603A.toInt(), 0xFFB07848.toInt())
    private val IRON = intArrayOf(0xFF1C1C22.toInt(), 0xFF34343E.toInt(), 0xFF5A5A68.toInt(), 0xFF8A8A9A.toInt())
    private val LIGHT = 0xFFFFE6A8.toInt()

    /** Kolik pixelů od kraje každé křídlo zakrývá. */
    fun doorWidth(t: Long): Float {
        val half = w / 2f + 1f
        return when {
            t < closeEnd -> half * LT.easeInOut(LT.progress(t, 0, closeEnd))
            t < openStart -> half
            else -> half * (1 - LT.easeInOut(LT.progress(t, openStart, end - 60)))
        }
    }

    override fun render(t: Long, out: IntArray) {
        val d = doorWidth(t)
        val opening = t >= openStart
        val glow = if (opening) 1 - LT.progress(t, openStart, end - 200) else 0f
        // při dovření se brány lehce otřesou
        val shake = if (t in closeEnd..closeEnd + 160) (sin((t - closeEnd) / 16f) * 1.5f * (1 - (t - closeEnd) / 160f)).toInt() else 0
        for (y in 0 until h) for (x in 0 until w) {
            val b = LT.bayer(x, y)
            val yy = y + shake
            val c = when {
                x < d -> door(d - x - 1f, yy, b)                 // levé křídlo, u = vzdálenost od švu
                x >= w - d -> door(x - (w - d), yy, b)           // pravé křídlo
                else -> {
                    // světlo mezi otevírajícími se křídly
                    if (glow > 0f) {
                        val edge = minOf(x - d, (w - d) - x)
                        val a = glow * (0.85f - edge / (w * 0.6f)).coerceIn(0f, 0.85f)
                        LT.alpha(LIGHT, a + b * 0.08f)
                    } else 0
                }
            }
            out[y * w + x] = c
        }
    }

    /** Pixel křídla; [u] = vzdálenost od švu (0 = vnitřní hrana), [y] = řádek. */
    private fun door(u: Float, y: Int, b: Float): Int {
        val ui = u.toInt()
        val emblemCy = h * 0.42f; val er = w * 0.16f
        val dx = u; val dy = y - emblemCy
        val r = kotlin.math.hypot(dx, dy)
        // znak Makroballu přes šev (půlka na každém křídle)
        if (r < er) {
            return when {
                r > er - 2f -> 0xFF101014.toInt()
                abs(dy) < er * 0.14f -> 0xFF101014.toInt()
                r < er * 0.32f -> if (r < er * 0.2f) 0xFFF4F0E4.toInt() else 0xFF101014.toInt()
                dy < 0 -> if (dx + dy * 0.4f < -er * 0.35f + b * 3) 0xFF7CC468.toInt() else 0xFF4E8A3A.toInt()
                else -> if (b > 0.2f) 0xFFD8D2C4.toInt() else 0xFFF4F0E4.toInt()
            }
        }
        // železné pásy s nýty
        for (band in floatArrayOf(0.14f, 0.74f)) {
            val by = h * band
            if (y >= by && y < by + 5) {
                val rivet = ui % 9 == 4 && (y - by.toInt()) in 1..3
                return if (rivet) IRON[3] else IRON[if (y - by.toInt() == 0) 2 else if (y - by.toInt() == 4) 0 else 1]
            }
        }
        // svislá prkna
        val plank = ui / 7
        if (ui % 7 == 6) return WOOD[0]
        if (ui < 2) return WOOD[1]                                   // tmavá hrana u švu
        val grain = LT.hash(plank, y / 5)
        var lvl = 2 + (grain * 1.6f + b * 0.8f).toInt()
        if (ui % 7 == 0) lvl++                                        // světlá hrana prkna
        if ((y + plank * 13) % 37 == 0) lvl = 1                       // suk / spára
        return WOOD[lvl.coerceIn(1, 4)]
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Louka: vyroste vysoká tráva s kvítím a motýly, pak ji poryv větru rozhrne od středu
// ─────────────────────────────────────────────────────────────────────────────

class GrassScene(override val w: Int, override val h: Int) : TransitionScene {
    override val coveredAt = 760L
    override val end = 1720L
    private val growEnd = 740L
    private val partStart = 960L
    override val openAt = partStart

    private val GRASS = intArrayOf(0xFF1E4A1C.toInt(), 0xFF2E6A26.toInt(), 0xFF428A30.toInt(), 0xFF5EA83E.toInt(), 0xFF86C656.toInt())
    private val FLOWERS = intArrayOf(0xFFF4E04A.toInt(), 0xFFF28AB6.toInt(), 0xFFFFFFFF.toInt(), 0xFFB48CF0.toInt())

    /** Jak vysoko je tráva vyrostlá (0 = pod okrajem, 1 = přes celou obrazovku). */
    private fun rise(t: Long) = LT.easeInOut(LT.progress(t, 0, growEnd))

    /**
     * Horní hrana stébla ve sloupci [x] (menší = výš). Stébla jsou 3 px široká a zašpičatělá,
     * zadní vrstva je vyšší a tmavší, přední nižší a světlejší.
     */
    fun top(x: Int, t: Long, back: Boolean): Float {
        val shift = if (back) 1 else 0
        val k = Math.floorDiv(x + shift, 3); val within = Math.floorMod(x + shift, 3)
        val bladeH = LT.hash(k, if (back) 11 else 12) * 22f
        val tip = if (within == 1) 0f else 3f
        val sway = sin(k * 0.6f + t / 140f) * 2f
        val base = h + 24f - (h * 1.3f + 60f) * rise(t)
        return base + bladeH + tip + sway + (if (back) -14f else 10f)
    }

    /** Polovina šířky rozhrnuté mezery uprostřed. */
    fun gap(t: Long): Float = if (t < partStart) 0f else (w / 2f + 24f) * LT.easeInOut(LT.progress(t, partStart, end - 40))

    override fun render(t: Long, out: IntArray) {
        val g = gap(t)
        val cx = w / 2f
        for (y in 0 until h) for (x in 0 until w) {
            val dist = abs(x + 0.5f - cx)
            if (dist < g) { out[y * w + x] = 0; continue }
            // stébla se u mezery naklánějí ven
            val lean = if (g > 0f) ((g + 10f - dist) / 10f).coerceIn(0f, 1f) else 0f
            val sx = if (x < cx) x + (lean * 4).toInt() else x - (lean * 4).toInt()
            val b = LT.bayer(x, y)
            val front = top(sx, t, back = false)
            val backTop = top(sx, t, back = true)
            out[y * w + x] = when {
                y >= front -> blade(sx, y, front, b, lean, back = false)
                y >= backTop -> blade(sx, y, backTop, b, lean, back = true)
                else -> 0
            }
        }
        butterflies(t, out)
    }

    private fun blade(x: Int, y: Int, tp: Float, b: Float, lean: Float, back: Boolean): Int {
        val shift = if (back) 1 else 0
        val k = Math.floorDiv(x + shift, 3); val within = Math.floorMod(x + shift, 3)
        // květ na špičce některých předních stébel
        if (!back && y - tp < 3 && within == 1 && LT.hash(k, 5) > 0.8f)
            return FLOWERS[(LT.hash(k, 6) * FLOWERS.size).toInt().coerceAtMost(FLOWERS.size - 1)]
        val depth = ((y - tp) / (h * 0.8f)).coerceIn(0f, 1f)
        var lvl = if (back) 2 else 4
        lvl -= (depth * 2.5f + b * 0.8f).toInt()
        if (within == 0) lvl--                    // levá hrana stébla ve stínu
        if (within == 1 && y - tp < 6) lvl++      // světlá špička
        if (LT.hash(k, 7) < 0.25f) lvl--          // některá stébla tmavší
        if (lean > 0f) lvl++
        return GRASS[lvl.coerceIn(0, 4)]
    }

    private fun butterflies(t: Long, out: IntArray) {
        val colors = intArrayOf(0xFFFFD54F.toInt(), 0xFFF4A6C8.toInt(), 0xFFFFFFFF.toInt())
        for (i in 0 until 3) {
            val bx = ((LT.hash(i, 30) * w + t / 1000f * (18f + i * 7f)) % (w + 10)) - 5
            val by = h * (0.3f + 0.4f * LT.hash(i, 31)) + sin(t / 180f + i * 2f) * 6f
            val flap = (t / 90 + i) % 2 == 0L
            val px = bx.toInt(); val py = by.toInt()
            val pts = if (flap) listOf(-1 to -1, 1 to -1, 0 to 0) else listOf(-1 to 0, 1 to 0, 0 to 0)
            for ((ox, oy) in pts) {
                val xx = px + ox; val yy = py + oy
                if (xx in 0 until w && yy in 0 until h && out[yy * w + xx] != 0) out[yy * w + xx] = if (ox == 0) 0xFF3A2A18.toInt() else colors[i]
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hvozd: zprava se přižene vichr listí, chvíli světlušky ve tmě, pak listí odletí doleva
// ─────────────────────────────────────────────────────────────────────────────

class LeafScene(override val w: Int, override val h: Int) : TransitionScene {
    override val coveredAt = 760L
    override val end = 1760L
    private val inEnd = 740L
    private val outStart = 1000L
    override val openAt = outStart

    private val LEAF = intArrayOf(0xFF0E2410.toInt(), 0xFF173816.toInt(), 0xFF1F4A1C.toInt(), 0xFF2A5E25.toInt(), 0xFF3C7A34.toInt(), 0xFF5A9A40.toInt())
    private val AUTUMN = intArrayOf(0xFF7A3A12.toInt(), 0xFFB0601E.toInt(), 0xFFD08A2E.toInt())
    private val FIREFLY = 0xFFE8FF9A.toInt()

    /** Náběžná hrana (vše vpravo od ní je listí). */
    fun lead(t: Long): Float = (w + 30f) - (w + 90f) * LT.easeInOut(LT.progress(t, 0, inEnd))
    /** Odtoková hrana (vpravo od ní už zase prosvítá mapa). */
    fun trail(t: Long): Float = if (t < outStart) w + 999f else (w + 60f) - (w + 140f) * LT.easeInOut(LT.progress(t, outStart, end - 40))

    private fun edge(y: Int, seed: Int): Float = sin(y * 0.21f + seed) * 5f + LT.hash(y / 4, seed) * 12f

    override fun render(t: Long, out: IntArray) {
        val l = lead(t); val tr = trail(t)
        val drift = t / 1000f * 40f
        for (y in 0 until h) for (x in 0 until w) {
            val covered = x + edge(y, 3) > l && x - edge(y, 7) < tr
            out[y * w + x] = if (!covered) 0 else foliage(x + drift, y, LT.bayer(x, y))
        }
        looseLeaves(t, l, tr, out)
        if (t in 600..1150) fireflies(t, out)
    }

    /** Hustá spleť listů: náhodně posunuté, různě velké a naklopené lístky, které se překrývají. */
    private fun foliage(xf: Float, y: Int, b: Float): Int {
        val cw = 6f; val ch = 5f
        val col0 = floor(xf / cw).toInt(); val row0 = floor(y / ch).toInt()
        var bestZ = -1f; var color = LEAF[if (b > 0.1f) 1 else 0]
        for (dr in -1..1) for (dc in -1..1) {
            val col = col0 + dc; val row = row0 + dr
            val z = LT.hash(col, row * 7 + 1)
            if (z <= bestZ) continue
            val cx = (col + 0.2f + LT.hash(col, row * 7 + 2) * 0.6f) * cw
            val cy = (row + 0.2f + LT.hash(col, row * 7 + 3) * 0.6f) * ch
            val rx = 2.6f + LT.hash(col, row * 7 + 4) * 1.8f; val ry = 1.6f + LT.hash(col, row * 7 + 5) * 1.1f
            val tilt = (LT.hash(col, row * 7 + 6) - 0.5f) * 1.4f
            val dx = xf - cx; val dy = y + 0.5f - cy
            val u = dx + dy * tilt; val v = dy - dx * tilt * 0.5f
            val r = (u * u) / (rx * rx) + (v * v) / (ry * ry)
            if (r >= 1f) continue
            bestZ = z
            val autumn = LT.hash(col, row * 7 + 8) > 0.94f
            color = when {
                abs(v) < 0.35f && r < 0.7f -> if (autumn) AUTUMN[0] else LEAF[2]          // žilka
                autumn -> AUTUMN[if (v < 0) 2 else 1]
                r > 0.7f -> LEAF[(2 + (z * 1.5f).toInt()).coerceAtMost(3)]                // okraj
                v < 0 -> LEAF[(4 + (z * 1.6f + b * 0.5f).toInt()).coerceAtMost(5)]        // osvětlená půlka
                else -> LEAF[(3 + (z * 1.4f + b * 0.5f).toInt()).coerceAtMost(4)]
            }
        }
        return color
    }

    private fun looseLeaves(t: Long, l: Float, tr: Float, out: IntArray) {
        for (i in 0 until 40) {
            val y = (LT.hash(i, 40) * h + sin(t / 200f + i) * 4f).toInt()
            // lístky létají kolem obou hran
            val base = if (i % 2 == 0) l else tr
            if (base > w + 200f) continue
            val x = (base - 6f - LT.hash(i, 41) * 30f + ((t / 7 + i * 13) % 20)).toInt()
            val c = if (LT.hash(i, 42) > 0.8f) AUTUMN[1] else LEAF[4]
            for ((ox, oy) in listOf(0 to 0, 1 to 0, 1 to 1)) {
                val xx = x + ox; val yy = y + oy
                if (xx in 0 until w && yy in 0 until h) out[yy * w + xx] = c
            }
        }
    }

    private fun fireflies(t: Long, out: IntArray) {
        for (i in 0 until 14) {
            val x = (LT.hash(i, 50) * w + sin(t / 300f + i * 1.3f) * 4f).toInt()
            val y = (LT.hash(i, 51) * h + sin(t / 410f + i) * 3f).toInt()
            val tw = (sin(t / 110f + i * 2f) + 1f) / 2f
            if (x in 1 until w - 1 && y in 1 until h - 1 && out[y * w + x] != 0) {
                out[y * w + x] = LT.mix(out[y * w + x], FIREFLY, 0.5f + tw * 0.5f)
                if (tw > 0.7f) for ((ox, oy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1))
                    out[(y + oy) * w + x + ox] = LT.mix(out[(y + oy) * w + x + ox], FIREFLY, 0.35f)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hory: obrazovku zahalí mraky se sněhem a v mlze prosvitnou štíty, pak mlha odtaje
// ─────────────────────────────────────────────────────────────────────────────

class MistScene(override val w: Int, override val h: Int) : TransitionScene {
    override val coveredAt = 780L
    override val end = 1800L
    private val inEnd = 760L
    private val outStart = 1040L
    override val openAt = outStart

    private val CLOUD = intArrayOf(0xFF8A9CB8.toInt(), 0xFFA8B8D0.toInt(), 0xFFC8D4E4.toInt(), 0xFFE4ECF4.toInt(), 0xFFFFFFFF.toInt())
    private val PEAK = intArrayOf(0xFF5E6E8A.toInt(), 0xFF7A8AA6.toInt(), 0xFFEEF4FA.toInt())

    /** Práh zakrytí: pixel je mrak, když šum < práh (−0,2 = nic, 1,2 = vše). */
    fun threshold(t: Long): Float = when {
        t < inEnd -> -0.2f + 1.4f * LT.easeInOut(LT.progress(t, 0, inEnd))
        t < outStart -> 1.2f
        else -> 1.2f - 1.4f * LT.easeInOut(LT.progress(t, outStart, end - 40))
    }

    /** Hřeben štítů (řádek), uprostřed obrazovky. */
    private fun ridge(x: Int): Float {
        val u = x.toFloat() / w
        val peaks = abs(((u * 2.6f) % 1f) - 0.5f) * 2f
        return h * (0.36f + 0.16f * peaks) + sin(x * 0.7f) * 1.5f
    }

    override fun render(t: Long, out: IntArray) {
        val th = threshold(t)
        val wind = t / 1000f * 0.9f
        val peaksVisible = if (t in 500..1400) (1f - abs((t - 900) / 450f)).coerceIn(0f, 1f) else 0f
        for (y in 0 until h) for (x in 0 until w) {
            val n = LT.fbm(x * 0.045f + wind, y * 0.07f)
            val b = LT.bayer(x, y)
            if (n + b * 0.06f >= th) { out[y * w + x] = 0; continue }
            val dens = ((th - n) * 3f).coerceIn(0f, 1f)          // okraj mraku je řidší a světlejší
            var c = CLOUD[(4 - (n * 4.5f + b * 0.8f).toInt()).coerceIn(0, 4)]
            if (dens < 0.25f) c = CLOUD[4]
            // štíty prosvítající mlhou
            if (peaksVisible > 0f && y > ridge(x)) {
                val snow = y < ridge(x) + h * 0.05f
                val peak = if (snow) PEAK[2] else PEAK[if ((x + y) % 7 == 0) 0 else 1]
                c = LT.mix(c, peak, 0.55f * peaksVisible * (1f - (y - ridge(x)) / (h * 0.35f)).coerceIn(0f, 1f))
            }
            out[y * w + x] = c
        }
        snow(t, out)
    }

    private fun snow(t: Long, out: IntArray) {
        for (i in 0 until 50) {
            val speed = 18f + LT.hash(i, 60) * 26f
            var y = (LT.hash(i, 61) * h + t / 1000f * speed) % h
            val x = ((LT.hash(i, 62) * w - t / 1000f * speed * 0.6f) % w + w) % w
            if (y < 0) y += h
            val xi = x.toInt(); val yi = y.toInt()
            if (xi in 0 until w && yi in 0 until h && out[yi * w + xi] != 0) out[yi * w + xi] = 0xFFFFFFFF.toInt()
        }
    }
}

/** Kontrola pro testy: podíl neprůhledných pixelů ve snímku. */
fun TransitionScene.coverage(t: Long): Float {
    val px = IntArray(w * h)
    render(t, px)
    return px.count { (it ushr 24) == 0xFF }.toFloat() / px.size
}

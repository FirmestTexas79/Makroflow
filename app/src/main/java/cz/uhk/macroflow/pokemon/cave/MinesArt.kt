package cz.uhk.macroflow.pokemon.cave

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Živé Doly (docs/adr/0049): tekoucí láva a poletující mušky. Čistý Kotlin (ARGB pixely),
 * kreslí je MinesFxView přes obrázek mapy. Pokryto testy.
 */
object MinesArt {

    // Láva od chladné kůry po bílý žár
    val LAVA = intArrayOf(
        0xFF2A0804.toInt(), 0xFF5E1308.toInt(), 0xFFA0260C.toInt(), 0xFFE0520E.toInt(),
        0xFFFF8A1E.toInt(), 0xFFFFC04A.toInt(), 0xFFFFF0A8.toInt()
    )

    private fun hash(a: Int, b: Int, c: Int = 0): Float {
        var h = a * 374761393 + b * 668265263 + c * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777216f
    }

    private val BAYER = floatArrayOf(0f, 8f, 2f, 10f, 12f, 4f, 14f, 6f, 3f, 11f, 1f, 9f, 15f, 7f, 13f, 5f)
        .map { it / 16f - 0.5f }.toFloatArray()

    private fun lvl(v: Float): Int = LAVA[v.toInt().coerceIn(0, LAVA.size - 1)]

    /**
     * Barva lávy v art pixelu [x], [y] v čase [t] (ms), 0 = tu láva není.
     * Vodopád teče dolů (pruhy ujíždějí), jezírko vře, řeka teče doprava s plovoucí kůrou.
     */
    fun lava(x: Int, y: Int, t: Long): Int {
        val M = MinesMap
        val b = BAYER[(y and 3) * 4 + (x and 3)]
        if (M.isFall(x, y)) {
            val v = y - t * 0.05f
            val row = floor(v / 2f).toInt()
            val stripe = hash(x, row, 3)
            val edge = x == M.FALL_X0 || x == M.FALL_X1
            val nearEdge = x == M.FALL_X0 + 1 || x == M.FALL_X1 - 1
            // pod puklinou je láva nejžhavější, dole ve vodopádu trochu chladne
            val depth = (y - M.FALL_Y0).toFloat() / (M.FALL_Y1 - M.FALL_Y0)
            var l = 3.6f + stripe * 2.2f - depth * 0.8f + b * 0.6f
            if (nearEdge) l -= 1.3f
            if (edge) l = 1.4f + stripe * 1.2f
            return lvl(l)
        }
        if (!M.isFloorLava(x, y)) return 0
        val dx = (x + 0.5f - M.POOL_CX) / M.POOL_RX; val dy = (y + 0.5f - M.POOL_CY) / M.POOL_RY
        val inPool = dx * dx + dy * dy < 1f
        if (inPool) {
            // vření: pomalé kruhy od místa dopadu + praskající bubliny
            val r = kotlin.math.sqrt(dx * dx + dy * dy)
            val ring = sin(r * 9f - t / 180f)
            val bubbleCell = hash(x / 3, y / 2, (t / 420).toInt())
            var l = 3.4f + ring * 0.9f - r * 1.2f + b * 0.5f
            if (bubbleCell > 0.93f) l = 6f
            return lvl(l)
        }
        if (x in M.CHANNEL_X0..M.CHANNEL_X1 && y in M.CHANNEL_Y0..M.CHANNEL_Y1) {
            val v = y - t * 0.018f
            val stripe = hash(x, floor(v / 3f).toInt(), 5)
            var l = 3.2f + stripe * 1.8f + b * 0.5f
            if (x == M.CHANNEL_X0 || x == M.CHANNEL_X1) l -= 1.6f
            return lvl(l)
        }
        // řeka: teče doprava, kůra plave v destičkách, u břehů chladne
        val u = x - t * 0.014f
        val plate = hash(floor(u / 5f).toInt(), (y - M.RIVER_Y0) / 3, 7)
        val bank = y == M.RIVER_Y0 || y == M.RIVER_Y1
        val nearBank = y == M.RIVER_Y0 + 1 || y == M.RIVER_Y1 - 1
        var l = 3.4f + hash(floor(u / 2f).toInt(), y, 9) * 1.6f + b * 0.6f
        if (plate > 0.78f) l = 1.2f + b                        // plovoucí kůra
        if (nearBank) l -= 0.9f
        if (bank) l = 1.2f + b * 0.8f
        // u mostu a na konci řeky (puklina) se láva víc žhne
        if (x in M.BRIDGE_X0 - 3..M.BRIDGE_X1 + 3 || x > M.RIVER_X1 - 5) l += 0.5f
        return lvl(l)
    }

    /** Šířka a výška animované oblasti lávy (MinesMap.LAVA_BOUNDS). */
    val lavaW: Int get() = MinesMap.LAVA_BOUNDS[2] - MinesMap.LAVA_BOUNDS[0] + 1
    val lavaH: Int get() = MinesMap.LAVA_BOUNDS[3] - MinesMap.LAVA_BOUNDS[1] + 1

    /**
     * Snímek lávy do [out] (lavaW × lavaH, 0 = průhledné) včetně jisker, které stoupají
     * z jezírka a z řeky.
     */
    fun lavaFrame(t: Long, out: IntArray) {
        val x0 = MinesMap.LAVA_BOUNDS[0]; val y0 = MinesMap.LAVA_BOUNDS[1]
        val w = lavaW; val h = lavaH
        for (y in 0 until h) for (x in 0 until w) out[y * w + x] = lava(x0 + x, y0 + y, t)
        // jiskry
        for (i in 0 until 14) {
            val fromPool = i < 8
            val period = 1400 + (hash(i, 1) * 1400).toInt()
            val ph = ((t + (hash(i, 2) * period).toLong()) % period) / period.toFloat()
            val sx = if (fromPool) MinesMap.POOL_CX + (hash(i, 3) - 0.5f) * 18f
                else MinesMap.RIVER_X0 + hash(i, 3) * (MinesMap.RIVER_X1 - MinesMap.RIVER_X0)
            val sy = if (fromPool) MinesMap.POOL_CY - 2f else MinesMap.RIVER_Y0.toFloat()
            val px = (sx + sin(ph * 6f + i) * 2f).toInt() - x0
            val py = (sy - ph * (10f + hash(i, 4) * 14f)).toInt() - y0
            if (px !in 0 until w || py !in 0 until h) continue
            val idx = py * w + px
            if (out[idx] != 0) continue
            val a = ((1f - ph) * 230).toInt().coerceIn(0, 255)
            val col = if (ph < 0.4f) 0xFFE27A else 0xFF8A1E
            out[idx] = (a shl 24) or col
        }
    }

    // ── Mušky ────────────────────────────────────────────────────────────────

    enum class Bug(val body: Int, val light: Int, val wing: Int, val glow: Int) {
        /** Jiskřivka: hnědé tělíčko, žhnoucí žlutooranžový zadeček. */
        SPARK(0xFF3A2414.toInt(), 0xFFFFD050.toInt(), 0xCCF4ECDC.toInt(), 0xFFFFC04A.toInt()),
        /** Krystalová muška: fialové tělo, křídla lámou světlo do tyrkysova a růžova. */
        CRYSTAL(0xFF4A2A7A.toInt(), 0xFFB8F4FF.toInt(), 0xCC9AF0FF.toInt(), 0xFF9AB8FF.toInt()),
        /** Magmová muška: černý krunýř s rudými puklinami, kouřová křídla. */
        MAGMA(0xFF141010.toInt(), 0xFFFF5A1E.toInt(), 0xAA8A8078.toInt(), 0xFFFF6A2A.toInt());
    }

    /** Každé místo má tři mušky. */
    const val FLIES = 3

    const val SPRITE_W = 5
    const val SPRITE_H = 4

    /**
     * Muška 5 × 4 (ARGB): křídla nahoře / dole ([up]) a svítící zadeček ([lit]).
     * Krystalová muška střídá barvu křídel.
     */
    fun sprite(bug: Bug, up: Boolean, lit: Boolean, alt: Boolean = false): IntArray {
        val out = IntArray(SPRITE_W * SPRITE_H)
        fun set(x: Int, y: Int, c: Int) { out[y * SPRITE_W + x] = c }
        val wing = if (bug == Bug.CRYSTAL && alt) 0xCCFFB8E8.toInt() else bug.wing
        if (up) { set(1, 0, wing); set(3, 0, wing); set(0, 0, wing); set(4, 0, wing) }
        else { set(0, 1, wing); set(4, 1, wing); set(1, 2, wing); set(3, 2, wing) }
        set(2, 0, bug.body)                        // hlavička
        set(2, 1, bug.body)
        set(2, 2, if (lit) bug.light else bug.body)
        if (lit || bug == Bug.MAGMA) set(2, 3, if (lit) bug.light else 0xFF7A1E0A.toInt())
        return out
    }

    /**
     * Kde je [i]-tá muška v čase [t] (ms): posun od středu hejna v art px. Každá krouží po
     * vlastní ležaté osmičce jinou rychlostí, občas trochu popoletí – nikdy nestojí na místě.
     */
    fun flyOffset(bug: Bug, i: Int, t: Long): Pair<Float, Float> {
        val speed = when (bug) { Bug.SPARK -> 1f; Bug.CRYSTAL -> 0.75f; Bug.MAGMA -> 1.35f }
        val ph = i * 2.1f + bug.ordinal * 0.7f
        val s = t / 1000f * speed * (0.8f + i * 0.17f)
        val rx = 7f + i * 1.5f; val ry = 4f + (i % 2) * 1.5f
        val x = sin(s * 1.3f + ph) * rx + sin(s * 3.1f + ph * 2) * 1.5f
        val y = sin(s * 2.6f + ph) * ry * 0.6f + cos(s * 1.7f + ph) * ry * 0.5f
        // magmové mušky stoupají v žáru a zase se snáší
        val lift = if (bug == Bug.MAGMA) sin(s * 0.9f + ph) * 3f else 0f
        return x to (y + lift)
    }

    /** Mávnutí křídel (~12 Hz, každá muška trochu jinak). */
    fun wingsUp(i: Int, t: Long): Boolean = ((t + i * 37) / 42) % 2 == 0L

    /** Zadeček bliká (jiskřivky pomalu dýchají, ostatní skoro pořád svítí). */
    fun lit(bug: Bug, i: Int, t: Long): Boolean = when (bug) {
        Bug.SPARK -> sin(t / 380f + i * 2f) > -0.2f
        Bug.CRYSTAL -> sin(t / 520f + i * 1.3f) > -0.6f
        Bug.MAGMA -> true
    }

    /** Síla záře kolem mušky 0..1. */
    fun glow(bug: Bug, i: Int, t: Long): Float = when (bug) {
        Bug.SPARK -> ((sin(t / 380f + i * 2f) + 1f) / 2f).coerceIn(0f, 1f)
        Bug.CRYSTAL -> 0.5f + 0.3f * sin(t / 700f + i)
        Bug.MAGMA -> 0.7f + 0.3f * sin(t / 160f + i * 3f)
    }
}

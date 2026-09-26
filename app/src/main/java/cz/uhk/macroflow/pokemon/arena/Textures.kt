package cz.uhk.macroflow.pokemon.arena

import kotlin.math.floor

/**
 * Procedurální pixelové textury bloků (6 × 6 texelů na blok). Texel se počítá ze světových
 * souřadnic, takže velký kvádr vypadá jako řada samostatných bloků (docs/adr/0038).
 */
object Textures {

    fun hash(a: Int, b: Int, c: Int, seed: Int): Float {
        var h = a * 374761393 + b * 668265263 + c * 1274126177 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777216f
    }

    private fun mod(a: Int, m: Int) = ((a % m) + m) % m

    private fun darker(c: Int, k: Float): Int {
        val r = (((c shr 16) and 0xFF) * k).toInt(); val g = (((c shr 8) and 0xFF) * k).toInt(); val b = ((c and 0xFF) * k).toInt()
        return (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }

    private fun pick(m: Mat, r: Float, a: Float = 0.2f, b: Float = 0.65f, c: Float = 0.93f) = when {
        r < a -> m.c0; r < b -> m.c1; r < c -> m.c2; else -> m.c3
    }

    private val DIRT_SIDE = Mat(Pattern.DIRT, 0xFF5E3B1E.toInt(), 0xFF7A4E28.toInt(), 0xFF8E5E33.toInt(), 0xFF6B4423.toInt())

    fun texel(m: Mat, box: Box, n: Int, p: V3, seed: Int): Int {
        val T = VoxelRenderer.TEX
        val (u, v) = when (n) { 0, 1 -> p.x to p.z; 2, 3 -> p.z to p.y; else -> p.x to p.y }
        val tu = floor(u * T + 1e-3f).toInt(); val tv = floor(v * T + 1e-3f).toInt()
        val iu = mod(tu, T); val iv = mod(tv, T)
        val side = n >= 2
        val r = hash(tu, tv, n * 31 + m.pattern.ordinal, seed)
        val grid = iu == 0 || iv == 0
        return when (m.pattern) {
            Pattern.GRASS, Pattern.FLOWERS, Pattern.MOSS -> {
                if (side) {
                    val fromTop = floor((box.y1 - p.y) * T + 1e-3f).toInt()
                    val rows = 1 + (if (hash(tu, 0, 9, seed) > 0.5f) 1 else 0)
                    if (fromTop < rows) pick(m, r) else pick(if (m.pattern == Pattern.MOSS) DIRT_SIDE else DIRT_SIDE, hash(tu, tv, 3, seed))
                        .let { if (grid) darker(it, 0.9f) else it }
                } else {
                    var c = pick(m, r, 0.14f, 0.58f, 0.95f)
                    if (m.pattern == Pattern.FLOWERS && hash(tu, tv, 55, seed) > 0.93f)
                        c = if (hash(tu, tv, 56, seed) > 0.5f) 0xFFF4E04A.toInt() else 0xFFF28AB6.toInt()
                    if (grid) darker(c, 0.93f) else c
                }
            }
            Pattern.DIRT, Pattern.SAND, Pattern.SNOW, Pattern.GRAVEL ->
                pick(m, r, 0.18f, 0.62f, 0.94f).let { if (grid) darker(it, if (m.pattern == Pattern.SNOW) 0.96f else 0.9f) else it }
            Pattern.STONE -> {
                val crack = hash(tu / 2, tv, 12, seed) > 0.9f && iv % 3 == 1
                (if (crack) darker(m.c0, 0.85f) else pick(m, r, 0.15f, 0.6f, 0.93f)).let { if (grid) darker(it, 0.86f) else it }
            }
            Pattern.TILES -> {
                // 2 × 2 dlaždice na blok, spáry a šachovnice
                val tile = (floor(u * 2f).toInt() + floor(p.z * 2f + (if (side) p.y * 2f else 0f)).toInt())
                if (iu % 3 == 0 || iv % 3 == 0) m.c0 else if (mod(tile, 2) == 0) (if (r > 0.85f) m.c3 else m.c2) else (if (r > 0.85f) m.c3 else m.c1)
            }
            Pattern.COBBLE -> {
                val row = floor(tv / 2f).toInt()
                val off = if (mod(row, 2) == 0) 0 else 1
                val stone = hash((tu + off) / 3, row, 21, seed)
                if (mod(tv, 2) == 0 || mod(tu + off, 3) == 0) m.c0 else if (stone > 0.66f) m.c2 else if (stone > 0.2f) m.c1 else m.c3
            }
            Pattern.BRICK -> {
                val row = floor(tv / 3f).toInt()
                val off = if (mod(row, 2) == 0) 0 else 3
                if (mod(tv, 3) == 0 || mod(tu + off, 6) == 0) m.c0
                else { val br = hash((tu + off) / 6, row, 22, seed); if (br > 0.7f) m.c2 else if (br > 0.15f) m.c1 else m.c3 }
            }
            Pattern.PLASTER -> pick(m, r, 0.06f, 0.7f, 0.97f).let { if (!side && grid) darker(it, 0.95f) else it }
            Pattern.PLANK -> {
                if (side || n == 0) {
                    val board = floor(tv / 2f).toInt()
                    if (mod(tv, 2) == 0) m.c0 else if (hash(board, tu / 6, 23, seed) > 0.5f) m.c2 else m.c1
                } else m.c1
            }
            Pattern.LEAF -> when {
                r < 0.12f -> m.c0
                r < 0.55f -> m.c1
                r < 0.9f -> m.c2
                else -> m.c3
            }.let { if (grid && r < 0.5f) darker(it, 0.85f) else it }
            Pattern.BARK -> if (n == 0 || n == 1) (if (iu in 1..4 && iv in 1..4) (if (mod(iu + iv, 3) == 0) m.c0 else m.c3) else m.c1)
                else { val col = hash(tu, 0, 24, seed); if (col > 0.7f) m.c0 else if (col > 0.25f) m.c1 else m.c2 }.let { if (r > 0.94f) m.c0 else it }
            Pattern.ROOF -> {
                val row = floor(tv / 2f).toInt()
                if (mod(tv, 2) == 0) m.c0 else if (mod(tu / 2 + row, 2) == 0) m.c1 else m.c2
            }
            Pattern.WATER -> {
                val wave = mod(tu + tv * 3 + (hash(tu / 4, tv / 2, 25, seed) * 4).toInt(), 11)
                if (wave == 0) m.c3 else if (r < 0.3f) m.c0 else if (r < 0.8f) m.c1 else m.c2
            }
            Pattern.CRYSTAL -> {
                val lvl = (v - box.y0) / (box.y1 - box.y0).coerceAtLeast(0.01f)
                if (iu == 0 || (side && mod(tu + tv, 5) == 0)) m.c0 else if (lvl > 0.7f || r > 0.85f) m.c3 else if (lvl > 0.35f) m.c2 else m.c1
            }
            Pattern.LAMP -> if (iu == 0 || iv == 0) m.c0 else if (r > 0.5f) m.c2 else m.c1
            Pattern.WINDOW -> if (iu == 0 || iv == 0 || iu == 3 || iv == 3) m.c0 else if (iu + iv < 4) m.c3 else m.c1
            Pattern.METAL -> if (grid) m.c0 else if (r > 0.8f) m.c2 else m.c1
            Pattern.MUSHROOM -> if (n == 0 || side && p.y > box.y1 - 0.34f) (if (hash(tu / 2, tv / 2, 26, seed) > 0.72f) m.c3 else if (r > 0.7f) m.c2 else m.c1) else m.c3
            Pattern.LILY -> if (r < 0.2f) m.c0 else if (r < 0.75f) m.c1 else m.c2
        }
    }
}

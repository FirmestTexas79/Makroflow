package cz.uhk.macroflow.pokemon.ui

/** Pixel art ukazatele kroků (ARGB, 0 = průhledné). */
object StepBarArt {
    const val H = 16
    const val BAR_W = 70
    const val SQUARE = 16
    /** Vnitřek drážky: x 4..(w-5), y 4..11. */
    const val INNER_TOP = 4
    const val INNER_BOTTOM = 11
    val INNER_W = BAR_W - 8

    private fun c(hex: Long) = hex.toInt()

    // dřevo
    private val OUTLINE = c(0xFF2E1B0E)
    private val WOOD_LIGHT = c(0xFFB9803F)
    private val WOOD = c(0xFF93602C)
    private val WOOD_DARK = c(0xFF6C4420)
    private val WOOD_DEEP = c(0xFF4F3016)
    private val GRAIN = c(0xFF7A4D24)
    // drážka
    private val WELL = c(0xFF2A1C11)
    private val WELL_SHADOW = c(0xFF1A110A)
    // zelená náplň
    private val G_SHADOW = c(0xFF4A552C)
    private val G_HIGHLIGHT = c(0xFFB3C877)
    private val G_LIGHT = c(0xFF7F9148)
    private val G = c(0xFF606C38)
    private val G_DARK = c(0xFF434E27)
    private val G_EDGE = c(0xFF8FA35A)

    /** Kolik sloupců drážky zaplnit pro podíl 0–1 (nenulový postup je vidět aspoň 1 px). */
    fun fillColumns(fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val cols = (f * INNER_W).toInt()
        return if (f > 0f && cols == 0) 1 else cols
    }

    /** Dřevěný rámeček se zaoblenými rohy a zapuštěnou drážkou. */
    private fun frame(w: Int): IntArray {
        val px = IntArray(w * H)
        fun set(x: Int, y: Int, col: Int) { if (x in 0 until w && y in 0 until H) px[y * w + x] = col }
        for (y in 0 until H) for (x in 0 until w) {
            val edge = x == 0 || y == 0 || x == w - 1 || y == H - 1
            val wood = x <= 2 || y <= 2 || x >= w - 3 || y >= H - 3
            val innerRim = !wood && (x == 3 || y == 3 || x == w - 4 || y == H - 4)
            set(x, y, when {
                edge -> OUTLINE
                wood -> when {
                    y == 1 -> WOOD_LIGHT                  // světlo na horní hraně
                    y >= H - 3 -> if (y == H - 2) WOOD_DEEP else WOOD_DARK
                    x == 1 -> WOOD_LIGHT
                    x >= w - 3 -> WOOD_DARK
                    else -> WOOD
                }
                innerRim -> OUTLINE
                // vnitřní stín: horní řada a levý sloupec drážky tmavší
                y == INNER_TOP || x == 4 -> WELL_SHADOW
                else -> WELL
            })
        }
        // léta dřeva
        for (x in 5 until w - 5 step 7) { set(x, 2, GRAIN); set(x + 3, H - 3, WOOD_DEEP) }
        // zaoblené rohy (pixelové schody)
        for ((cx, cy) in listOf(0 to 0, w - 1 to 0, 0 to H - 1, w - 1 to H - 1)) {
            val dx = if (cx == 0) 1 else -1; val dy = if (cy == 0) 1 else -1
            set(cx, cy, 0); set(cx + dx, cy, 0); set(cx, cy + dy, 0)
            set(cx + dx, cy + dy, OUTLINE)
        }
        return px
    }

    /** Pruh s [fill] zaplněnými sloupci drážky. */
    fun bar(fill: Int): IntArray {
        val w = BAR_W
        val px = frame(w)
        val n = fill.coerceIn(0, INNER_W)
        for (i in 0 until n) {
            val x = 4 + i
            for (y in INNER_TOP..INNER_BOTTOM) {
                px[y * w + x] = when (y) {
                    INNER_TOP -> G_SHADOW                          // stín rámu přes náplň
                    INNER_TOP + 1 -> if (x == 4) G_SHADOW else G_HIGHLIGHT   // hlavní odlesk
                    INNER_TOP + 2 -> if (x == 4) G_SHADOW else G_LIGHT
                    INNER_BOTTOM -> G_DARK
                    else -> if (x == 4) G_SHADOW else G
                }
            }
            // krátký druhý odlesk ve spodní polovině
            if (i in 3 until n - 2 && (i % 11) in 1..3) px[(INNER_BOTTOM - 2) * w + x] = G_LIGHT
        }
        // čelo náplně
        if (n in 2 until INNER_W) for (y in INNER_TOP + 1 until INNER_BOTTOM) px[y * w + 3 + n] = G_EDGE
        return px
    }

    /** Čtvereček se zelenou pixelovou fajfkou (cíl splněn). */
    fun check(): IntArray {
        val w = SQUARE
        val px = frame(w)
        // fajfka ve vnitřku 4..11 × 4..11: tah 2 px silný, nad ním světlo, pod ním stín
        val stroke = listOf(4 to 8, 5 to 9, 6 to 10, 7 to 9, 8 to 8, 9 to 7, 10 to 6, 11 to 5)
        fun put(x: Int, y: Int, col: Int) { if (x in 4..11 && y in INNER_TOP..INNER_BOTTOM) px[y * w + x] = col }
        for ((x, y) in stroke) { put(x, y + 1, G_DARK) }
        for ((x, y) in stroke) { put(x, y, G); put(x, y - 1, G) }
        for ((x, y) in stroke) { put(x, y - 2, G_HIGHLIGHT) }
        return px
    }
}

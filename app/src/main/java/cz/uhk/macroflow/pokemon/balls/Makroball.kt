package cz.uhk.macroflow.pokemon.balls

/**
 * Makrobally (čistý Kotlin, pokryto testy). Podklady v docs/adr/0011.
 *
 * Interní ID zůstávají původní (poke_ball / great_ball), aby seděla data v DB, Firebase a promo kódech.
 * Sprite je pixel art 12 × 12 (černý pás s kulatým tlačítkem je u všech stejný) složený ze dvou půlek (vršek řádky 0–5, spodek 6–11) –
 * při dopadu se vršek odklopí na pantu.
 */
enum class Makroball(
    val id: String,
    val label: String,
    /** Násobitel šance na chycení. */
    val catchMultiplier: Float,
    val price: Int,
    val packSize: Int,
    val description: String,
    private val palette: Palette,
    private val decor: Map<Pair<Int, Int>, Char> = emptyMap()
) {
    MAKRO(
        "poke_ball", "Makroball", 1.0f, 20, 5,
        "Základní Makroball. Na běžné tréninky stačí.",
        Palette(top = 0xFF606C38, topShade = 0xFF3E4A22, topLight = 0xFFA3B46B,
            bottom = 0xFFFEFAE0, bottomShade = 0xFFD8D2B0,
            outline = 0xFF1C2410)
    ),
    PROTEIN(
        "great_ball", "Proteinball", 1.5f, 50, 3,
        "Šejkr plný proteinu – 1,5× větší šance na chycení.",
        Palette(top = 0xFF2E86DE, topShade = 0xFF1B5FA6, topLight = 0xFF9CD0FF,
            bottom = 0xFFF4F7FA, bottomShade = 0xFFC9D3DD,
            outline = 0xFF0E1A26),
        // Bílý pruh přes víčko jako na šejkru
        decor = (3..8).associate { (it to 3) to 'W' }
    ),
    KREATIN(
        "ultra_ball", "Kreatinball", 2.0f, 120, 2,
        "Síla kotouče činky – dvojnásobná šance na chycení.",
        Palette(top = 0xFF2B2B2B, topShade = 0xFF141414, topLight = 0xFF6E6E6E,
            bottom = 0xFF9EA4AA, bottomShade = 0xFF6C7278,
            outline = 0xFF0A0A0A),
        // Zlaté nýty na víčku (jako šrouby na kotouči)
        decor = mapOf((3 to 2) to 'G', (8 to 2) to 'G')
    );

    data class Palette(
        val top: Long, val topShade: Long, val topLight: Long,
        val bottom: Long, val bottomShade: Long,
        val outline: Long
    )

    /** Pixely 12 × 12 (ARGB, 0 = průhledné), řádek po řádku. */
    val pixels: IntArray by lazy { render() }

    private fun render(): IntArray {
        val out = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val ch = decor[x to y] ?: SHAPE[y][x]
            out[y * SIZE + x] = when (ch) {
                'K' -> palette.outline
                'T' -> palette.top
                't' -> palette.topShade
                'H' -> palette.topLight
                'W' -> palette.bottom
                'w' -> palette.bottomShade
                'B' -> BAND
                'C' -> BUTTON
                'G' -> 0xFFFFD54F
                else -> 0L
            }.toInt()
        }
        return out
    }

    companion object {
        const val SIZE = 12
        /** Střed je u všech ballů stejný: černý pás a bílé tlačítko. */
        private const val BAND = 0xFF181818
        private const val BUTTON = 0xFFF4F4F4
        /** Řádek, od kterého začíná spodní půlka (pant je na jejím horním okraji). */
        const val SPLIT_ROW = 6

        /**
         * Tvar koule: K obrys, T vršek, t stín, H odlesk, W/w spodek,
         * B černý pás a C bílé kulaté tlačítko vpředu – u všech ballů stejné.
         */
        val SHAPE = listOf(
            "....KKKK....",
            "..KKTTTTKK..",
            ".KHHTTTTTtK.",
            ".KHTTTTTTtK.",
            "KTTTTKKTTTtK",
            "KBBBKCCKBBBK",
            "KBBKCCCCKBBK",
            "KWWWKCCKWWwK",
            "KWWWWKKWWWwK",
            ".KWWWWWWWwK.",
            ".KKwWWWWwKK.",
            "....KKKK...."
        )

        fun from(id: String?): Makroball? = entries.firstOrNull { it.id == id }

        /**
         * Šance na chycení (0..255 z 256) – stejný vzorec jako dřív v BattleEngine:
         * čím méně HP, tím víc; násobí se druhem Makromona a ballem.
         */
        fun catchValue(hpFraction: Double, speciesMultiplier: Float, ball: Makroball): Int =
            (((1.0 - hpFraction.coerceIn(0.0, 1.0)) * 220 + 20) * speciesMultiplier * ball.catchMultiplier)
                .toInt().coerceIn(0, 255)

        fun catchProbability(hpFraction: Double, speciesMultiplier: Float, ball: Makroball): Double =
            catchValue(hpFraction, speciesMultiplier, ball) / 256.0
    }
}

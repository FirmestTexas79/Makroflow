package cz.uhk.macroflow.pokemon.skills

/**
 * Rozmístění dílny na louce v pixelech obrázku meadow.png (688 × 1536). Stejné obdélníky
 * blokuje generátor map chůze (tools/walkmask/gen_walkmask.py, MEADOW_FIX) a uzly
 * „vyrobna“ a „zahon_1–4“ v BiomeRegistry leží před nimi.
 */
object MeadowLayout {
    data class R(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val w get() = x1 - x0
        val h get() = y1 - y0
        val cx get() = (x0 + x1) / 2f
        val cy get() = (y0 + y1) / 2f
    }

    /** Celá zahrada (cesta ve tvaru plus sahá až k okrajům). */
    val GARDEN = R(345, 745, 520, 925)
    /** Záhony: 0 levý horní, 1 pravý horní, 2 levý dolní, 3 pravý dolní (0 a 1 otevřené od začátku). */
    val PLOTS = listOf(R(360, 769, 426, 824), R(448, 769, 514, 824), R(360, 846, 426, 901), R(448, 846, 514, 901))
    val PATH_H = R(345, 824, 520, 846)
    val PATH_V = R(426, 745, 448, 925)

    /** Pracovní stůl na světlé trávě u cedule (spodní hrana = nohy stolu). */
    val TABLE = R(91, 776, 163, 830)

    fun plotNode(index: Int) = "zahon_${index + 1}"
    fun plotIndex(node: String): Int? = node.removePrefix("zahon_").toIntOrNull()?.minus(1)?.takeIf { node.startsWith("zahon_") && it in 0 until Garden.PLOTS }
    const val TABLE_NODE = "vyrobna"
}

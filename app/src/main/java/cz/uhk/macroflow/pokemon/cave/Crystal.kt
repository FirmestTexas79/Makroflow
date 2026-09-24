package cz.uhk.macroflow.pokemon.cave

/**
 * Krystaly na konci jeskyní. Každý se dá sebrat jednou; oba dohromady později otevřou
 * legendární souboj v jiné lokaci (zatím jen stav, viz docs/adr/0013).
 */
enum class CrystalColor(
    val label: String,
    val genitive: String,
    /** Obrys, tmavá, střední, světlá stěna, odlesk (ARGB). */
    val palette: IntArray,
    /** Barva záře kolem krystalu. */
    val glow: Int
) {
    BLUE("Modrý krystal", "Modrého krystalu",
        intArrayOf(0xFF0E1A40.toInt(), 0xFF2446A8.toInt(), 0xFF3C82E6.toInt(), 0xFF96D2FF.toInt(), 0xFFF0FAFF.toInt()),
        0xFF5AB4FF.toInt()),
    RED("Červený krystal", "Červeného krystalu",
        intArrayOf(0xFF3A0A12.toInt(), 0xFF8C1C2C.toInt(), 0xFFD43C48.toInt(), 0xFFFF9C8C.toInt(), 0xFFFFEEE8.toInt()),
        0xFFFF5A5A.toInt());

    /** Klíč v GamePrefs – true = krystal byl sebrán z oltáře. */
    val prefKey: String get() = "crystal_$name"

    /** Předmět v inventáři (tabulka user_items). */
    val itemId: String get() = "crystal_${name.lowercase()}"

    val description: String get() = when (this) {
        BLUE -> "Chladný krystal z Mechové jeskyně. Uvnitř se převaluje světlo jako hladina podzemního jezírka. " +
            "Patří do svatyně na vrcholu Hor – spolu s Červeným krystalem."
        RED -> "Horký krystal z hlubin Starého dolu. Pulzuje jako žhavé uhlíky. " +
            "Patří do svatyně na vrcholu Hor – spolu s Modrým krystalem."
    }

    companion object {
        fun fromItem(itemId: String): CrystalColor? = entries.firstOrNull { it.itemId == itemId }
    }
}

object Crystals {
    const val W = 11
    const val H = 18

    /** Čtvercová ikona pro inventář ([H] × [H]), krystal uprostřed. */
    fun iconPixels(color: CrystalColor): IntArray {
        val src = pixels(color)
        val out = IntArray(H * H)
        val ox = (H - W) / 2
        for (y in 0 until H) for (x in 0 until W) out[y * H + x + ox] = src[y * W + x]
        return out
    }

    /** Malý krystal 3 × 6 do lůžka svatyně na vrcholu. */
    const val SMALL_W = 3
    const val SMALL_H = 6
    fun smallPixels(color: CrystalColor): IntArray {
        val p = color.palette
        val o = p[0]
        return intArrayOf(
            0, p[4], 0,
            o, p[3], p[1],
            p[3], p[2], p[1],
            p[3], p[2], p[1],
            o, p[2], o,
            0, o, 0
        )
    }

    /** Legendární souboj se otevře, až hráč přinese oba krystaly. */
    fun legendaryUnlocked(collected: Set<CrystalColor>): Boolean = collected.containsAll(CrystalColor.entries)

    /**
     * Pixel art krystalu [W] × [H]: šestiboký hranol se špičkami, světlá levá stěna,
     * střední hrana, tmavá pravá stěna a odlesk. 0 = průhledné.
     */
    fun pixels(color: CrystalColor): IntArray {
        val p = color.palette
        val out = IntArray(W * H)
        val cx = W / 2
        for (y in 0 until H) {
            val half = when {
                y < 5 -> y                    // horní špička
                y < 13 -> 4                   // hranol
                else -> (H - 1 - y)           // spodní špička
            }.coerceAtMost(4)
            for (dx in -half..half) {
                val x = cx + dx
                val c = when {
                    dx == -half || dx == half || y == 0 || y == H - 1 -> p[0]
                    dx == -2 && y in 6..9 -> p[4]                     // odlesk
                    dx < -1 -> p[3]
                    dx <= 0 -> if (y < 5) p[4] else p[2]
                    dx == 1 -> p[2]
                    else -> p[1]
                }
                out[y * W + x] = c
            }
        }
        // vodorovná hrana mezi špičkou a hranolem
        for (dx in -3..3) if (out[5 * W + cx + dx] != p[0]) out[5 * W + cx + dx] = p[3]
        return out
    }
}

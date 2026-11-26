package cz.uhk.macroflow.pokemon.cave

/**
 * Šipky východů v Hlubině (zóna 3) a na dně Věže: nad každým přechodem do jiné lokace se vznáší malá pixelová šipka
 * se jménem cíle, pohupuje se o 2 px a jde na ni klepnout. Šipka nesedí přímo na uzlu u okraje mapy (tam ji schová
 * horní lišta nebo spodní okraj), ale kus dovnitř a míří ven ke kraji. Zamčený přechod má šedou šipku a zámek.
 */
object ExitMarkers {
    enum class Dir { UP, DOWN, LEFT, RIGHT }

    /** Přechod: uzel na mapě a název cíle, který se ukáže pod šipkou. */
    data class Exit(val node: String, val label: String)

    val EXITS: Map<String, List<Exit>> = mapOf(
        "TOWER_ABYSS" to listOf(Exit(TowerAbyss.CAGE_NODE, "Stanice 8")),
        "ROOT_CAMP" to listOf(Exit(RootCamp.CAGE_NODE, "Věž −2"), Exit(RootCamp.DOWN_NODE, "Pozorovatelna B")),
        "ROOT_WATCH" to listOf(Exit(RootWatch.ENTRY_NODE, "Stanice 8"), Exit(RootWatch.RAVINE_NODE, "Signální útes"),
            Exit(RootWatch.DOWN_NODE, "Tábor na svahu")),
        "ROOT_SIGNAL" to listOf(Exit(RootSignal.ENTRY_NODE, "Pozorovatelna B"), Exit("su_lanovka", "Gondola – Kořenový most")),
        "ROOT_BRIDGE" to listOf(Exit(RootBridge.ENTRY_NODE, "Gondola – Signální útes")),
        "ROOT_SLOPECAMP" to listOf(Exit(RootSlopeCamp.ENTRY_NODE, "Pozorovatelna B"), Exit(RootSlopeCamp.DOWN_NODE, "Svah bezejmenných")),
        "ROOT_NAMELESS" to listOf(Exit(RootNameless.ENTRY_NODE, "Tábor na svahu"), Exit("sb_chram", "Chrám Lumenců"),
            Exit("sb_les", "Les pod korunou")),
        "ROOT_TEMPLE" to listOf(Exit(RootTemple.ENTRY_NODE, "Svah bezejmenných")),
        "ROOT_FOREST" to listOf(Exit(RootForest.ENTRY_NODE, "Svah bezejmenných"), Exit(RootForest.THRESHOLD_NODE, "Práh")),
        "ROOT_THRESHOLD" to listOf(Exit(RootThreshold.ENTRY_NODE, "Les pod korunou"), Exit(RootThreshold.BRANCH_NODE, "Kmen")),
        "ROOT_TRUNK" to listOf(Exit(RootTrunk.ENTRY_NODE, "Práh"), Exit(RootTrunk.CRACK_NODE, "Střed kořenů"))
    )

    /** Kam šipka míří: uzel u horního/dolního/bočního okraje → ven tím směrem; uprostřed mapy shora dolů na uzel. */
    fun dir(x: Int, y: Int, artW: Int, artH: Int): Dir = when {
        y < artH * 0.15f -> Dir.UP
        y > artH * 0.85f -> Dir.DOWN
        x < artW * 0.15f -> Dir.LEFT
        x > artW * 0.85f -> Dir.RIGHT
        else -> Dir.DOWN
    }

    /** Střed šipky v art px: kus od uzlu dovnitř mapy, ať ji nezakryje lišta ani okraj obrazovky. */
    fun anchor(x: Int, y: Int, d: Dir, artW: Int, artH: Int): Pair<Int, Int> = when (d) {
        Dir.UP -> x to maxOf(y + 22, 34)
        Dir.DOWN -> x to minOf(y - 22, artH - 40)
        Dir.LEFT -> maxOf(x + 18, 12) to y
        Dir.RIGHT -> minOf(x - 18, artW - 12) to y
    }

    const val SIZE = 9

    private val ARROW_DOWN = listOf(
        "..ooooo..",
        "..oyyyo..",
        "..oyYyo..",
        "oooyYyooo",
        "oyyyYyyyo",
        ".oyyYyyo.",
        "..oyYyo..",
        "...oyo...",
        "....o...."
    )

    /** Šipka dolů (9 × 9, ARGB): zlatá, zamčená šedá; tmavý obrys, světlý hřbet. */
    fun arrowPixels(d: Dir, locked: Boolean): IntArray {
        val fill = if (locked) 0xFF8A8A90.toInt() else 0xFFF2C94C.toInt()
        val hi = if (locked) 0xFFC8C8CE.toInt() else 0xFFFFF4C0.toInt()
        val line = 0xFF1A1410.toInt()
        val out = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val (sx, sy) = when (d) {                                       // otočení předlohy (míří dolů)
                Dir.DOWN -> x to y
                Dir.UP -> x to SIZE - 1 - y
                Dir.RIGHT -> y to x
                Dir.LEFT -> y to SIZE - 1 - x
            }
            out[y * SIZE + x] = when (ARROW_DOWN[sy][sx]) {
                'o' -> line
                'y' -> fill
                'Y' -> hi
                else -> 0
            }
        }
        return out
    }
}

package cz.uhk.macroflow.pokemon.cave

/**
 * Hvozd nad loukou (docs/adr/0015) – bludiště palouků mezi stromy, propletenější než Starý důl:
 * smyčky, slepé uličky, jezírka. Stejná kamera jako jeskyně (celá šířka na obrazovce).
 * Souřadnice = tools/mapgen/gen_forest.py (NODES / EDGES), zdroj pravdy v obou místech.
 */
object ForestMap {
    private const val A = 22
    private const val B = 58
    private const val C = 94
    private const val D = 128

    /** Kolik splněných fází úkolů (celkem napříč questy) je potřeba ke vstupu. */
    const val REQUIRED_TASKS = 5

    val MAP = CaveMap(
        artW = 150, artH = 520,
        nodes = listOf(
            CaveNode("vstup_z_louky", D, 508),
            CaveNode("l_d1", D, 462), CaveNode("l_c1", C, 462), CaveNode("l_b1", B, 462), CaveNode("trava_1", A, 462),
            CaveNode("l_c2", C, 420),
            CaveNode("l_b3", B, 378), CaveNode("l_c3", C, 378), CaveNode("l_d3", D, 378),
            CaveNode("jezirko_1", D, 336),
            CaveNode("l_a4", A, 336), CaveNode("l_b4", B, 336), CaveNode("l_c4", C, 336),
            CaveNode("l_a5", A, 294), CaveNode("trava_2", B, 294), CaveNode("l_c5", C, 294), CaveNode("houstina", D, 294),
            CaveNode("l_a6", A, 252), CaveNode("l_b6", B, 252), CaveNode("l_c6", C, 252), CaveNode("l_d6", D, 252),
            CaveNode("trava_3", A, 210), CaveNode("l_b7", B, 210), CaveNode("l_c7", C, 210), CaveNode("l_d7", D, 210),
            CaveNode("l_b8", B, 168), CaveNode("l_c8", C, 168),
            CaveNode("l_b9", B, 126), CaveNode("l_c9", C, 126), CaveNode("jezirko_2", D, 126),
            CaveNode("l_a10", A, 84), CaveNode("l_b10", B, 84), CaveNode("l_c10", C, 84),
            CaveNode("stary_dub", A, 42), CaveNode("mytina", C, 40)
        ),
        edges = listOf(
            "vstup_z_louky" to "l_d1", "l_d1" to "l_c1", "l_c1" to "l_b1", "l_b1" to "trava_1",
            "l_c1" to "l_c2", "l_c2" to "l_c3", "l_c3" to "l_b3", "l_c3" to "l_d3", "l_d3" to "jezirko_1",
            "l_b3" to "l_b4", "l_b4" to "l_a4", "l_b4" to "l_c4", "l_a4" to "l_a5", "l_a5" to "trava_2",
            "l_c4" to "l_c5", "l_c5" to "houstina", "l_c5" to "l_c6",
            "l_a5" to "l_a6", "l_a6" to "l_b6", "l_b6" to "l_c6", "l_c6" to "l_d6", "l_d6" to "l_d7",
            "l_a6" to "trava_3", "l_b6" to "l_b7", "l_d7" to "l_c7", "l_c7" to "l_c8",
            "l_b7" to "l_b8", "l_b8" to "l_c8", "l_c8" to "l_c9", "l_c9" to "jezirko_2", "l_c9" to "l_b9",
            "l_b9" to "l_b10", "l_b10" to "l_a10", "l_a10" to "stary_dub", "l_b10" to "l_c10", "l_c10" to "mytina"
        ),
        exitNode = "vstup_z_louky",
        mountainNode = "les_sever",
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("trava_1", "jezirko_1", "trava_2", "houstina", "trava_3", "jezirko_2", "stary_dub"),
        parentBiome = "MEADOW",
        isCave = false
    )

    /**
     * Splněné fáze úkolů napříč všemi questy: dokončený quest = všechny jeho fáze,
     * rozpracovaný = fáze před aktuální. Vstupy: (počet fází, index aktuální fáze, dokončeno).
     */
    fun completedTasks(quests: List<Triple<Int, Int, Boolean>>): Int =
        quests.sumOf { (total, current, done) -> if (done) total else current.coerceIn(0, total) }

    fun canEnter(completedTasks: Int): Boolean = completedTasks >= REQUIRED_TASKS
}

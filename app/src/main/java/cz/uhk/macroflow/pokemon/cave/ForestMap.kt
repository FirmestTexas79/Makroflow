package cz.uhk.macroflow.pokemon.cave

/**
 * Hvozd nad loukou (docs/adr/0015, přepracováno v 0036) – palouky spojené vlnitými stezkami,
 * 300 × 600 art px. Na obrazovce je vidět 150 px na šířku, kamera jezdí do všech stran.
 * Souřadnice = tools/mapgen/gen_forest.py (NODES / EDGES), zdroj pravdy v obou místech.
 */
object ForestMap {
    /** Kolik splněných fází úkolů (celkem napříč questy) je potřeba ke vstupu. */
    const val REQUIRED_TASKS = 5

    val MAP = CaveMap(
        artW = 300, artH = 600,
        nodes = listOf(
            CaveNode("vstup_z_louky", 150, 588),
            CaveNode("f_a", 150, 540),
            CaveNode("f_w1", 104, 522),
            CaveNode("trava_1", 62, 512),
            CaveNode("f_e1", 196, 508),
            CaveNode("strom_briza", 238, 482),
            CaveNode("f_b", 134, 462),
            CaveNode("f_c", 158, 414),
            CaveNode("f_mid", 150, 372),
            CaveNode("f_w2", 102, 382),
            CaveNode("jezirko_1", 66, 386),
            CaveNode("trava_2", 212, 338),
            CaveNode("f_d", 138, 304),
            CaveNode("f_e", 152, 262),
            CaveNode("f_w3", 110, 258),
            CaveNode("houstina", 68, 256),
            CaveNode("f_e2", 200, 256),
            CaveNode("jezirko_2", 240, 250),
            CaveNode("f_f", 142, 214),
            CaveNode("f_g", 162, 170),
            CaveNode("f_w4", 106, 160),
            CaveNode("trava_3", 60, 156),
            CaveNode("f_nw", 118, 118),
            CaveNode("stary_dub", 92, 92),
            CaveNode("f_n", 156, 104),
            CaveNode("mytina", 150, 54),
            CaveNode("f_ne", 198, 110),
            CaveNode("strom_javor", 232, 112)
        ),
        edges = listOf(
            "vstup_z_louky" to "f_a",
            "f_a" to "f_w1",
            "f_w1" to "trava_1",
            "f_a" to "f_e1",
            "f_e1" to "strom_briza",
            "f_a" to "f_b",
            "f_b" to "f_c",
            "f_c" to "f_mid",
            "f_mid" to "f_w2",
            "f_w2" to "jezirko_1",
            "f_mid" to "trava_2",
            "f_mid" to "f_d",
            "f_d" to "f_e",
            "f_e" to "f_w3",
            "f_w3" to "houstina",
            "f_e" to "f_e2",
            "f_e2" to "jezirko_2",
            "f_e" to "f_f",
            "f_f" to "f_g",
            "f_g" to "f_w4",
            "f_w4" to "trava_3",
            "f_w4" to "f_nw",
            "f_nw" to "stary_dub",
            "f_g" to "f_n",
            "f_n" to "mytina",
            "f_n" to "f_ne",
            "f_ne" to "strom_javor",
            "f_w2" to "f_w3",
            "trava_2" to "f_e2"
        ),
        exitNode = "vstup_z_louky",
        mountainNode = "les_sever",
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("trava_1", "jezirko_1", "trava_2", "houstina", "jezirko_2", "trava_3", "stary_dub"),
        artPixelsAcross = 150,
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

package cz.uhk.macroflow.pokemon.cave

/**
 * Nebeský průsmyk (docs/adr/0044): serpentina za svatyní na vrcholu Hor, na jejímž konci
 * stojí Brána světů s prázdným lůžkem ve tvaru listu (Srdce Hvozdu) a výhled do druhého regionu.
 * Uzly a hrany kopírují tools/mapgen/gen_skypass.py – ZDROJ PRAVDY je v obou místech.
 */
object SkyPass {
    /** Klíče postupu (GamePrefs, synchronizované přes story_* předměty). */
    const val VISITED_KEY = "sky_pass_visited"
    const val GATE_SEEN_KEY = "world_gate_seen"
    const val HEART_PLACED_KEY = "forest_heart_placed"

    const val GATE_NODE = "brana_svetu"
    const val CLAWS_NODE = "drapy"
    const val CAIRN_NODE = "muzik"
    const val VISTA_NODE = "vyhlidka"
    const val LEDGE_NODE = "plosina"

    val MAP = CaveMap(
        artW = 160, artH = 440,
        nodes = listOf(
            CaveNode("vstup_ze_svatyne", 80, 424),
            CaveNode("p1", 54, 396), CaveNode("p2", 98, 362), CaveNode(CLAWS_NODE, 110, 344),
            CaveNode("p3", 62, 326), CaveNode("p4", 100, 290), CaveNode(CAIRN_NODE, 54, 280),
            CaveNode("p5", 78, 256), CaveNode(VISTA_NODE, 40, 222), CaveNode(GATE_NODE, 80, 226),
            CaveNode(LEDGE_NODE, 116, 222)
        ),
        edges = listOf(
            "vstup_ze_svatyne" to "p1", "p1" to "p2", "p2" to CLAWS_NODE, "p2" to "p3", "p3" to "p4",
            "p4" to CAIRN_NODE, CAIRN_NODE to "p5", "p4" to "p5", "p5" to GATE_NODE,
            GATE_NODE to VISTA_NODE, GATE_NODE to LEDGE_NODE
        ),
        exitNode = "vstup_ze_svatyne",
        mountainNode = "peak",
        crystalNode = null,
        crystal = null,
        encounterNodes = emptySet(),
        parentBiome = "MOUNTAINS",
        isCave = false,
        tapAreas = mapOf(
            GATE_NODE to Triple(80, 190, 26),
            CLAWS_NODE to Triple(128, 336, 14),
            CAIRN_NODE to Triple(49, 278, 9),
            VISTA_NODE to Triple(26, 232, 10),
            LEDGE_NODE to Triple(134, 232, 10)
        )
    )

    /** Uzly, které něco dělají (ostatní jsou jen cesta). */
    val ACTION_NODES = setOf(GATE_NODE, CLAWS_NODE, CAIRN_NODE, VISTA_NODE, LEDGE_NODE)

    /** Výřez obrázku mapy s bránou a podstavcem (x, y, šířka, výška v art px) pro detail na tabuli. */
    val GATE_CROP = intArrayOf(40, 158, 80, 76)

    /** Texty tabule u Brány světů podle postupu. */
    data class GateBoard(val subtitle: String, val lines: List<String>, val missing: String?)

    fun gateBoard(heartPlaced: Boolean): GateBoard =
        if (heartPlaced) GateBoard(
            "Prstenec se chvěje a září.",
            listOf("Srdce Hvozdu v lůžku tepe zeleným světlem a závoj uvnitř brány se rozestupuje.",
                "Za ním se rozlévá světlo nového kraje…", "(Cesta do druhého regionu se teprve chystá.)"),
            missing = null)
        else GateBoard(
            "Prastarý prstenec nad mořem mraků.",
            listOf("Kámen je pokrytý tyrkysovými runami a uvnitř se mihotá závoj – brána je zapečetěná.",
                "Na podstavci pod ní je prázdné lůžko ve tvaru listu.",
                "Runy šeptají: „Jen ten, komu les svěří své srdce, projde.“"),
            missing = "Srdce Hvozdu")
}

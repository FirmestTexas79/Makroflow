package cz.uhk.macroflow.pokemon.cave

/**
 * Duhová zahrada – 11. patro Věže Řádu Váhy, patro víl. Pohádka až příliš dokonalá: duha dopadá do hrnce zlata,
 * čtyřlístky rostou v pravidelných rozestupech, pixies jsou zavřené ve sklenicích a přání se evidují. V kruzích
 * muchomůrek se potkávají divocí Makromoni typu VÍLA (SpawnManager.TYPED_FLOORS).
 * Uzly kopírují tools/mapgen/gen_fairy.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.FAIRY.
 */
object TowerFairy {
    const val ELEVATOR_NODE = "vila_vytah"
    val RING_NODES = setOf("vila_kruh_1", "vila_kruh_2", "vila_kruh_3")

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ELEVATOR_NODE, 88, 104), CaveNode("vila_stred", 88, 166), CaveNode("vila_zvon", 30, 152),
            CaveNode("vila_hrnec", 138, 138), CaveNode("vila_kruh_1", 44, 190), CaveNode("vila_domek", 138, 210),
            CaveNode("vila_sklenice", 38, 262), CaveNode("vila_kruh_2", 130, 258), CaveNode("vila_kruh_3", 46, 316),
            CaveNode("vila_studna", 112, 322)
        ),
        edges = listOf(ELEVATOR_NODE to "vila_stred", ELEVATOR_NODE to "vila_hrnec", "vila_stred" to "vila_zvon",
            "vila_stred" to "vila_kruh_1", "vila_stred" to "vila_domek", "vila_kruh_1" to "vila_sklenice",
            "vila_domek" to "vila_kruh_2", "vila_sklenice" to "vila_kruh_3", "vila_kruh_2" to "vila_studna",
            "vila_kruh_3" to "vila_studna"),
        exitNode = ELEVATOR_NODE,
        mountainNode = TowerLobby.ELEVATOR_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = RING_NODES,
        parentBiome = "TOWER_LOBBY",
        isCave = false,
        tapAreas = mapOf(
            ELEVATOR_NODE to Triple(88, 66, 22), "vila_zvon" to Triple(26, 118, 16), "vila_hrnec" to Triple(150, 112, 16),
            "vila_domek" to Triple(142, 178, 20), "vila_sklenice" to Triple(36, 232, 22), "vila_studna" to Triple(140, 304, 20)
        )
    )

    val TEXTS = mapOf(
        "vila_zvon" to "🍀 Pod skleněným zvonem roste obří čtyřlístek. Cedulka: VZOREK ŠTĚSTÍ – OKRUH 213. NEPŘESAZOVAT.\nVšechny čtyřlístky na trávníku rostou přesně stejně daleko od sebe.",
        "vila_hrnec" to "🌈 Duha končí v hrnci zlata. Mince ale nejsou zlaté – jsou mosazné a ražené čísly okruhů. Úplně navrchu leží 213, ještě teplá.",
        "vila_domek" to "🍄 Muchomůrkový domek. Za okénkem svítí a uvnitř se něco tiše chichotá. Na dveřích je přišpendlený lístek: POČET OBYVATEL – EVIDOVÁN.",
        "vila_sklenice" to "✨ Zavařovací sklenice s víčky z látky. V každé svítí pixie a klepe na sklo. Na štítcích: ŠTĚSTÍ, NADĚJE, NÁHODA, ÚSMĚV, PŘÁNÍ.",
        "vila_studna" to "🪙 Studna přání. Na cedulce stojí: PŘÁNÍ SE EVIDUJÍ. Hluboko dole je slyšet, jak někdo pečlivě přepisuje přání do knihy."
    )

    const val EMPTY_TEXT = "Muchomůrky se zachichotaly, ale nikdo nevylezl. Zkus to znovu."

    val ACTION_NODES = TEXTS.keys + ELEVATOR_NODE
}

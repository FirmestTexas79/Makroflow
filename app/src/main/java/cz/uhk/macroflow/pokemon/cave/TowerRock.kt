package cz.uhk.macroflow.pokemon.cave

/**
 * Skalní patro – 5. patro Věže Řádu Váhy, patro zemních Makromonů. Věž tu prorazila horu: holá skála ve vrstvách,
 * kamenná plotna, sutě, balvany, trhlina za provazem, teodolit a vytyčovací praporky. V sutích se potkávají divocí
 * Makromoni typu GROUND (SpawnManager.TYPED_FLOORS). Uzly kopírují tools/mapgen/gen_rock.py – ZDROJ PRAVDY je
 * v obou místech. Živé detaily: InteriorFxView.ROCK.
 */
object TowerRock {
    const val ELEVATOR_NODE = "hora_vytah"
    val SCREE_NODES = setOf("hora_sut_1", "hora_sut_2", "hora_sut_3")

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ELEVATOR_NODE, 88, 104), CaveNode("hora_stred", 88, 160), CaveNode("hora_sut_1", 40, 196),
            CaveNode("hora_sut_2", 134, 290), CaveNode("hora_sut_3", 44, 330), CaveNode("hora_teodolit", 112, 186),
            CaveNode("hora_lebka", 60, 268), CaveNode("hora_trhlina", 124, 210), CaveNode("hora_pilir", 34, 142),
            CaveNode("hora_balvan", 142, 150)
        ),
        edges = listOf(ELEVATOR_NODE to "hora_stred", "hora_stred" to "hora_pilir", "hora_stred" to "hora_balvan",
            "hora_stred" to "hora_sut_1", "hora_stred" to "hora_teodolit", "hora_teodolit" to "hora_trhlina",
            "hora_sut_1" to "hora_lebka", "hora_lebka" to "hora_sut_3", "hora_lebka" to "hora_sut_2"),
        exitNode = ELEVATOR_NODE,
        mountainNode = TowerLobby.ELEVATOR_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = SCREE_NODES,
        parentBiome = "TOWER_LOBBY",
        isCave = false,
        tapAreas = mapOf(
            ELEVATOR_NODE to Triple(88, 66, 22), "hora_sut_1" to Triple(40, 174, 20), "hora_sut_2" to Triple(134, 268, 20),
            "hora_sut_3" to Triple(44, 308, 20), "hora_teodolit" to Triple(112, 164, 12), "hora_lebka" to Triple(62, 250, 14),
            "hora_trhlina" to Triple(132, 236, 22), "hora_pilir" to Triple(30, 112, 14), "hora_balvan" to Triple(144, 128, 16)
        )
    )

    val TEXTS = mapOf(
        "hora_teodolit" to "📐 Teodolit na trojnožce. Míří na skalní stěnu, kde je křídou napsáno 213 a šipka dolů.\nŘád zaměřuje i horu. Nebo spíš to, co je pod ní.",
        "hora_lebka" to "💀 Napůl zasypaná lebka s jedním rohem. Na kosti je vyrytý malý symbol vah a číslo 019.",
        "hora_trhlina" to "🕳️ Trhlina v podlaze za provazovým zábradlím. Hodíš do ní kamínek a dopad neuslyšíš.\nZezdola táhne teplý vzduch – a slabě to voní po popelu.",
        "hora_pilir" to "🪨 Věž ze tří balvanů, vyvážených jeden na druhém. Někdo je sem poskládal pečlivě, jako závaží na váhy.",
        "hora_balvan" to "🪨 Velký balvan s lišejníkem. Na boku má namalovaný bílý křížek a číslo bodu: V-5."
    )

    val ACTION_NODES = TEXTS.keys + ELEVATOR_NODE
}

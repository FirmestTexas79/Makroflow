package cz.uhk.macroflow.pokemon.cave

/**
 * Zatopené patro – 7. patro Věže Řádu Váhy, patro vodních Makromonů. Kancelář zaplavená vodou: zadní stěna je
 * akvárium, po hladině vedou lávky ke dvěma plošinám, pod vodou utopené stoly a židle. Na krajích lávek se potkávají
 * divocí Makromoni typu VODA (SpawnManager.TYPED_FLOORS). Uzly kopírují tools/mapgen/gen_water.py – ZDROJ PRAVDY
 * je v obou místech. Živé detaily: InteriorFxView.WATER.
 */
object TowerWater {
    const val ELEVATOR_NODE = "zat_vytah"
    val WATER_NODES = setOf("zat_voda_1", "zat_voda_2", "zat_voda_3")

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ELEVATOR_NODE, 88, 104), CaveNode("zat_stred", 88, 194), CaveNode("zat_voda_1", 32, 216),
            CaveNode("zat_voda_2", 150, 300), CaveNode("zat_voda_3", 88, 326), CaveNode("zat_kruh", 24, 184),
            CaveNode("zat_lodka", 126, 300), CaveNode("zat_ventil", 66, 196), CaveNode("zat_rakos", 156, 262)
        ),
        edges = listOf(ELEVATOR_NODE to "zat_stred", "zat_stred" to "zat_ventil", "zat_ventil" to "zat_kruh", "zat_kruh" to "zat_voda_1",
            "zat_stred" to "zat_voda_3", "zat_voda_3" to "zat_lodka", "zat_lodka" to "zat_voda_2", "zat_voda_2" to "zat_rakos"),
        exitNode = ELEVATOR_NODE,
        mountainNode = TowerLobby.ELEVATOR_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = WATER_NODES,
        parentBiome = "TOWER_LOBBY",
        isCave = false,
        tapAreas = mapOf(
            ELEVATOR_NODE to Triple(88, 66, 22), "zat_voda_1" to Triple(36, 250, 18), "zat_voda_2" to Triple(150, 240, 16),
            "zat_voda_3" to Triple(60, 300, 18), "zat_kruh" to Triple(22, 168, 10), "zat_lodka" to Triple(140, 326, 18),
            "zat_ventil" to Triple(66, 222, 10), "zat_rakos" to Triple(160, 230, 10)
        )
    )

    val TEXTS = mapOf(
        "zat_kruh" to "🛟 Záchranný kruh na kůlu. Na štítku: „Pro personál. Subjekty se nezachraňují, subjekty se měří.“",
        "zat_lodka" to "🚣 Loďka přivázaná k lávce. Na dně leží veslo a složka s razítkem: HLADINA STOUPÁ – DOBA SPÁNKU KLESÁ.",
        "zat_ventil" to "🔴 Ventil na trubce, která trčí z vody. Je zarezlý napůl. Někdo se ho snažil zavřít – nebo otevřít.",
        "zat_rakos" to "🌾 Rákos u stěny. Mezi stébly plave pořadový lístek 210. Inkoust se dávno rozpil."
    )

    val ACTION_NODES = TEXTS.keys + ELEVATOR_NODE
}

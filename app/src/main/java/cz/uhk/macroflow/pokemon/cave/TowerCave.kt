package cz.uhk.macroflow.pokemon.cave

/**
 * Krystalová jeskyně – 6. patro Věže Řádu Váhy, patro psychických a elektrických Makromonů. Tma, světlo dávají jen
 * krystaly, lucerna a výtah; krápníky, podzemní jezírko, bedny s úlomky, měřicí přístroj s kabelem, důlní vozík.
 * U velkých krystalů se potkávají divocí Makromoni typu PSYCHO a BLESK (SpawnManager.TYPED_FLOORS).
 * Uzly kopírují tools/mapgen/gen_cave.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.CAVE.
 */
object TowerCave {
    const val ELEVATOR_NODE = "jes_vytah"
    val CRYSTAL_NODES = setOf("jes_krystal_1", "jes_krystal_2", "jes_krystal_3")

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ELEVATOR_NODE, 88, 98), CaveNode("jes_stred", 88, 160), CaveNode("jes_krystal_1", 42, 206),
            CaveNode("jes_krystal_2", 138, 226), CaveNode("jes_krystal_3", 54, 314), CaveNode("jes_pristroj", 98, 210),
            CaveNode("jes_jezirko", 126, 270), CaveNode("jes_vozik", 64, 262), CaveNode("jes_bedny", 132, 146),
            CaveNode("jes_lucerna", 72, 176)
        ),
        edges = listOf(ELEVATOR_NODE to "jes_stred", "jes_stred" to "jes_bedny", "jes_stred" to "jes_lucerna",
            "jes_lucerna" to "jes_krystal_1", "jes_stred" to "jes_pristroj", "jes_pristroj" to "jes_krystal_2",
            "jes_krystal_1" to "jes_vozik", "jes_vozik" to "jes_jezirko", "jes_vozik" to "jes_krystal_3"),
        exitNode = ELEVATOR_NODE,
        mountainNode = TowerLobby.ELEVATOR_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = CRYSTAL_NODES,
        parentBiome = "TOWER_LOBBY",
        isCave = false,
        tapAreas = mapOf(
            ELEVATOR_NODE to Triple(88, 62, 22), "jes_krystal_1" to Triple(40, 176, 18), "jes_krystal_2" to Triple(140, 196, 18),
            "jes_krystal_3" to Triple(52, 282, 20), "jes_pristroj" to Triple(100, 186, 14), "jes_jezirko" to Triple(136, 302, 24),
            "jes_vozik" to Triple(64, 240, 14), "jes_bedny" to Triple(140, 126, 18), "jes_lucerna" to Triple(76, 140, 10)
        )
    )

    val TEXTS = mapOf(
        "jes_pristroj" to "📟 Měřicí přístroj Řádu. Kabel vede k fialovému krystalu, ručička kmitá v rytmu jeho světla.\nŠtítek: „Rezonance snů – Subjekt 213“. Dnes v noci prý naměřil hodně.",
        "jes_jezirko" to "💧 Podzemní jezírko. Voda je tak klidná, že se v ní zrcadlí krystaly. Do hladiny pomalu kape ze stropu.",
        "jes_vozik" to "🛒 Důlní vozík plný svítících úlomků. Na boku křídou: „Do patra 7 – NEOTVÍRAT PŘED KALIBRACÍ“.",
        "jes_bedny" to "📦 Bedny s úlomky krystalů. Na víku razítko vah a seznam: tyrkysové – spánek, fialové – sny.",
        "jes_lucerna" to "🏮 Lucerna na tyči. Jediné teplé světlo v jeskyni. Občas zakolísá, jako by na ni někdo dýchl."
    )

    val ACTION_NODES = TEXTS.keys + ELEVATOR_NODE
}

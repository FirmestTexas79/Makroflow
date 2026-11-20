package cz.uhk.macroflow.pokemon.cave

/**
 * Patro −1 Věže Řádu Váhy – Hunfreyho skrýš (docs/adr/0104). Technické podlaží, které na panelu výtahu nemá
 * tlačítko: holý beton, trubky, louže. Hunfrey (Okruh 212) se tu skrýval po útěku z Mrazírny. Bez setkání.
 * Uzly kopírují tools/mapgen/gen_hideout.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.HIDEOUT.
 */
object TowerHideout {
    const val ELEVATOR_NODE = "minus_vytah"
    const val NOTEBOOK_NODE = "minus_sesit"

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ELEVATOR_NODE, 88, 104), CaveNode("minus_stred", 88, 168), CaveNode("minus_mapa", 40, 104),
            CaveNode("minus_carky", 142, 104), CaveNode("minus_maska", 158, 112), CaveNode("minus_lehatko", 50, 190),
            CaveNode(NOTEBOOK_NODE, 86, 200), CaveNode("minus_spisy", 140, 214), CaveNode("minus_poklop", 88, 270)
        ),
        edges = listOf(ELEVATOR_NODE to "minus_stred", ELEVATOR_NODE to "minus_mapa", ELEVATOR_NODE to "minus_carky",
            "minus_carky" to "minus_maska", "minus_stred" to "minus_lehatko", "minus_stred" to NOTEBOOK_NODE,
            "minus_stred" to "minus_spisy", NOTEBOOK_NODE to "minus_poklop", "minus_spisy" to "minus_poklop",
            "minus_lehatko" to "minus_poklop"),
        exitNode = ELEVATOR_NODE,
        mountainNode = TowerLobby.ELEVATOR_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = emptySet(),
        parentBiome = "TOWER_LOBBY",
        isCave = false,
        tapAreas = mapOf(
            ELEVATOR_NODE to Triple(88, 66, 22), "minus_mapa" to Triple(38, 44, 24), "minus_carky" to Triple(146, 36, 22),
            "minus_maska" to Triple(158, 66, 8), "minus_lehatko" to Triple(44, 178, 22), NOTEBOOK_NODE to Triple(86, 180, 14),
            "minus_spisy" to Triple(146, 196, 20), "minus_poklop" to Triple(88, 282, 18)
        )
    )

    val TEXTS get() = cz.uhk.macroflow.pokemon.story.TowerStory.HIDEOUT_TEXTS
    val ACTION_NODES = TEXTS.keys + ELEVATOR_NODE + NOTEBOOK_NODE
}

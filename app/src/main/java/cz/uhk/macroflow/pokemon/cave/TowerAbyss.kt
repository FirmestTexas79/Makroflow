package cz.uhk.macroflow.pokemon.cave

/**
 * Patro −2 Věže Řádu Váhy – Sektor Hlubiny (docs/adr/0104). Lávka nad propastí, pod ní visí Lumira (Spící),
 * mrtvý strom hlavou dolů; kořeny drží svět, koruna se ztrácí v mlze. Konec kapitoly V, vstup do Aktu III.
 * Bez setkání. Uzly kopírují tools/mapgen/gen_abyss.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.ABYSS.
 */
object TowerAbyss {
    const val ELEVATOR_NODE = "hlub_vytah"
    const val VIEW_NODE = "hlub_vyhled"

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ELEVATOR_NODE, 88, 102), CaveNode("hlub_most", 88, 140), CaveNode("hlub_cedule", 76, 186),
            CaveNode(VIEW_NODE, 88, 196), CaveNode("hlub_klec", 114, 184)
        ),
        edges = listOf(ELEVATOR_NODE to "hlub_most", "hlub_most" to VIEW_NODE, "hlub_most" to "hlub_cedule",
            VIEW_NODE to "hlub_klec", "hlub_cedule" to VIEW_NODE),
        exitNode = ELEVATOR_NODE,
        mountainNode = TowerLobby.ELEVATOR_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = emptySet(),
        parentBiome = "TOWER_LOBBY",
        isCave = false,
        tapAreas = mapOf(
            ELEVATOR_NODE to Triple(88, 62, 22), "hlub_cedule" to Triple(66, 174, 8), VIEW_NODE to Triple(88, 280, 60),
            "hlub_klec" to Triple(135, 208, 14)
        )
    )

    val TEXTS get() = cz.uhk.macroflow.pokemon.story.TowerStory.ABYSS_TEXTS
    val ACTION_NODES = TEXTS.keys + ELEVATOR_NODE
}

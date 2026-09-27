package cz.uhk.macroflow.pokemon.cave

import cz.uhk.macroflow.pokemon.story.SecretGrove

/**
 * Zapomenutý háj (docs/adr/0046) – skrytá mýtina za trním na západě Hvozdu.
 * Uzly a hrany kopírují tools/mapgen/gen_grove.py – ZDROJ PRAVDY je v obou místech.
 */
object GroveMap {
    val MAP = CaveMap(
        artW = 160, artH = 300,
        nodes = listOf(
            CaveNode(SecretGrove.EXIT_NODE, 80, 290), CaveNode("g1", 80, 250),
            CaveNode("kruh_jih", 80, 214), CaveNode(SecretGrove.POOL_NODE, 80, 194),
            CaveNode("mural_1", 42, 170), CaveNode("mural_2", 118, 170), CaveNode("mural_3", 60, 132),
            CaveNode("g2", 80, 120), CaveNode(SecretGrove.ALTAR_NODE, 80, 100), CaveNode(SecretGrove.GRAVE_NODE, 122, 104)
        ),
        edges = listOf(
            SecretGrove.EXIT_NODE to "g1", "g1" to "kruh_jih", "kruh_jih" to SecretGrove.POOL_NODE,
            "kruh_jih" to "mural_1", "kruh_jih" to "mural_2", "mural_1" to "mural_3", "mural_3" to "g2",
            "mural_2" to "g2", "g2" to SecretGrove.ALTAR_NODE, SecretGrove.ALTAR_NODE to SecretGrove.GRAVE_NODE
        ),
        exitNode = SecretGrove.EXIT_NODE,
        mountainNode = SecretGrove.FOREST_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = emptySet(),
        parentBiome = "FOREST",
        isCave = false,
        tapAreas = mapOf(
            "mural_1" to Triple(24, 148, 12), "mural_2" to Triple(136, 148, 12), "mural_3" to Triple(46, 104, 12),
            SecretGrove.POOL_NODE to Triple(80, 165, 20), SecretGrove.ALTAR_NODE to Triple(80, 84, 15),
            SecretGrove.GRAVE_NODE to Triple(124, 94, 10)
        )
    )

    /** Uzly, které něco dělají. */
    val ACTION_NODES = SecretGrove.MURAL_NODES.toSet() + setOf(SecretGrove.POOL_NODE, SecretGrove.ALTAR_NODE, SecretGrove.GRAVE_NODE)
}

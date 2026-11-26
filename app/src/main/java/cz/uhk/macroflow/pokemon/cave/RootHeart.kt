package cz.uhk.macroflow.pokemon.cave

/**
 * Zóna 3 (Akt III – Hlubina): STŘED KOŘENŮ (docs/adr/0110) – finální lokace pod puklinou u paty kmene. Kokon z teplých
 * kořenů, mělká klidná voda, kameny s mechem k ostrůvku s posledním světlem Lumiry, mechové lůžko. Žádný popel, žádný
 * Řád, bez setkání. Tady se dohraje konec (story/Finale.kt). Uzly kopírují tools/mapgen/gen_root_heart.py – ZDROJ PRAVDY
 * je v obou místech. Živé detaily: InteriorFxView.ROOT_HEART.
 */
object RootHeart {
    const val ENTRY_NODE = "st_vstup"
    const val LIGHT_NODE = "st_svetlo"
    const val MOSS_NODE = "st_mech"
    const val VISITED_KEY = "zona3_stred_korenu"

    /** Poslední světlo (art px) – živá záře a jiskry. */
    const val LIGHT_X = 88
    const val LIGHT_Y = 310

    val MAP = CaveMap(
        artW = 176, artH = 448,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 88, 40), CaveNode("st_schody", 88, 116), CaveNode("st_koreny", 42, 168), CaveNode("st_odraz", 134, 206),
            CaveNode("st_brod", 90, 252), CaveNode(MOSS_NODE, 36, 300), CaveNode(LIGHT_NODE, 88, 312), CaveNode("st_ticho", 116, 380)
        ),
        edges = listOf(ENTRY_NODE to "st_schody", "st_schody" to "st_koreny", "st_schody" to "st_odraz", "st_koreny" to "st_brod",
            "st_odraz" to "st_brod", "st_brod" to MOSS_NODE, "st_brod" to LIGHT_NODE, MOSS_NODE to LIGHT_NODE, LIGHT_NODE to "st_ticho"),
        exitNode = ENTRY_NODE,
        mountainNode = RootTrunk.CRACK_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = emptySet(),
        parentBiome = "ROOT_TRUNK",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(88, 24, 14), "st_koreny" to Triple(30, 150, 18), "st_odraz" to Triple(140, 222, 16),
            MOSS_NODE to Triple(38, 298, 18), LIGHT_NODE to Triple(LIGHT_X, LIGHT_Y, 16), "st_ticho" to Triple(116, 382, 12)
        )
    )

    val TEXTS get() = cz.uhk.macroflow.pokemon.story.Finale.TEXTS
    val ACTION_NODES = TEXTS.keys + ENTRY_NODE + LIGHT_NODE + MOSS_NODE
}

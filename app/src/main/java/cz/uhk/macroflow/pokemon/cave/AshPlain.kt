package cz.uhk.macroflow.pokemon.cave

/**
 * Popelavá pláň (Akt II, issue #26, docs/adr/0088): první kraj za Branou světů. Jde se
 * zdola od zbořeného oblouku po staré cestě nahoru ke strážnici Ozvěny. Uzly a hrany kopírují
 * tools/mapgen/gen_ashplain.py – ZDROJ PRAVDY je v obou místech.
 */
object AshPlain {
    const val VISITED_KEY = "ash_plain_visited"

    const val ENTRY_NODE = "vstup_z_brany"
    const val SIGN_NODE = "cedule"
    const val MILESTONE_NODE = "meznik"
    const val CITY_GATE_NODE = "brana_mesta"

    val MAP = CaveMap(
        artW = 160, artH = 448,
        nodes = listOf(
            CaveNode(CITY_GATE_NODE, 80, 72),
            CaveNode("n8", 80, 112), CaveNode(SIGN_NODE, 56, 88), CaveNode(MILESTONE_NODE, 120, 104), CaveNode("n7", 48, 112),
            CaveNode("n6", 48, 192), CaveNode("ohniste", 40, 232), CaveNode("n5", 96, 192), CaveNode("pole", 104, 168),
            CaveNode("n4", 96, 272), CaveNode("hajek", 120, 248), CaveNode("n3", 64, 272),
            CaveNode("n2", 64, 352), CaveNode("sutiny", 40, 344), CaveNode("n1", 80, 352), CaveNode("duny", 120, 360),
            CaveNode(ENTRY_NODE, 80, 416)
        ),
        edges = listOf(
            CITY_GATE_NODE to "n8", "n8" to SIGN_NODE, "n8" to MILESTONE_NODE, "n8" to "n7", "n7" to "n6",
            "n6" to "ohniste", "n6" to "n5", "n5" to "pole", "n5" to "n4", "n4" to "hajek", "n4" to "n3",
            "n3" to "n2", "n2" to "sutiny", "n2" to "n1", "n1" to "duny", "n1" to ENTRY_NODE
        ),
        exitNode = ENTRY_NODE,
        mountainNode = SkyPass.GATE_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("duny", "hajek", "ohniste", "sutiny", "pole"),
        parentBiome = "SKY_PASS",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(80, 424, 20),
            CITY_GATE_NODE to Triple(80, 50, 16),
            SIGN_NODE to Triple(56, 72, 8),
            MILESTONE_NODE to Triple(120, 88, 8),
            "pole" to Triple(116, 160, 20),
            "ohniste" to Triple(40, 216, 10),
            "hajek" to Triple(124, 256, 20),
            "sutiny" to Triple(24, 312, 22),
            "duny" to Triple(116, 368, 20)
        )
    )

    /** Uzly, které něco dělají mimo divoká setkání. */
    val ACTION_NODES = setOf(ENTRY_NODE, SIGN_NODE, MILESTONE_NODE, CITY_GATE_NODE)

    const val SIGN_TEXT = "🪧 Cedule je ohořelá. Šipka ukazuje k bílým hradbám a pod ní jde přečíst jen:\n„OZVĚNA – vítejte zpět.“"
    const val MILESTONE_TEXT = "🪨 Kamenný mezník. Na čele je vytesané „S“ a pod ním číslo, které někdo pečlivě oškrábal."
    const val MILESTONE_TEXT_INSIGHT = "\nKdyž se podíváš zblízka, pořád je tam vidět nula. S-0."
    const val CITY_GATE_TEXT = "🏰 Dveře strážnice jsou zamčené. Za hradbami je slyšet zvon a smích, jako by tam byl svátek.\n" +
        "(Město se teprve staví – brzy se otevře.)"

    fun emptyEncounterText(node: String): String? = when (node) {
        "duny" -> "Suchá tráva zašustila a ve vzduchu se zatočil popel. Zkus to znovu."
        "hajek" -> "Ve spálených stromech zapraskalo… jen větev. Zkus to znovu."
        "ohniste" -> "Uhlíky v ohništi zažhnuly a zase pohasly. Zkus to znovu."
        "sutiny" -> "V sutinách se něco pohnulo… jen kámen. Zkus to znovu."
        "pole" -> "Strašák se otočil ve větru. Nikdo tu není. Zkus to znovu."
        else -> null
    }
}

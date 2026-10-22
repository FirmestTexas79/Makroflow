package cz.uhk.macroflow.pokemon.cave

/**
 * Popelavá pláň (Akt II, issue #26, docs/adr/0088): první kraj za Branou světů. Shora se sestupuje
 * od zbořeného oblouku po staré cestě k hradbám Ozvěny. Uzly a hrany kopírují
 * tools/mapgen/gen_ashplain.py – ZDROJ PRAVDY je v obou místech.
 */
object AshPlain {
    const val VISITED_KEY = "ash_plain_visited"

    const val ENTRY_NODE = "vstup_z_brany"
    const val SIGN_NODE = "cedule"
    const val MILESTONE_NODE = "meznik"
    const val CITY_GATE_NODE = "brana_mesta"

    val MAP = CaveMap(
        artW = 160, artH = 440,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 80, 40),
            CaveNode("c1", 66, 66), CaveNode("duny", 120, 82), CaveNode("c2", 86, 104), CaveNode(SIGN_NODE, 48, 116),
            CaveNode("c3", 70, 148), CaveNode("hajek", 122, 168), CaveNode("c4", 96, 198), CaveNode("ohniste", 40, 212),
            CaveNode("c5", 70, 244), CaveNode("sutiny", 116, 268), CaveNode("c6", 86, 298), CaveNode("pole", 38, 318),
            CaveNode("c7", 66, 348), CaveNode(MILESTONE_NODE, 112, 358), CaveNode("c8", 80, 384), CaveNode(CITY_GATE_NODE, 80, 404)
        ),
        edges = listOf(
            ENTRY_NODE to "c1", "c1" to "c2", "c2" to "duny", "c2" to SIGN_NODE, "c2" to "c3",
            "c3" to "c4", "c4" to "hajek", "c4" to "ohniste", "c4" to "c5", "c5" to "c6",
            "c6" to "sutiny", "c6" to "pole", "c6" to "c7", "c7" to MILESTONE_NODE, "c7" to "c8",
            "c8" to CITY_GATE_NODE
        ),
        exitNode = ENTRY_NODE,
        mountainNode = SkyPass.GATE_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("duny", "hajek", "ohniste", "sutiny", "pole"),
        parentBiome = "SKY_PASS",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(80, 20, 18),
            "duny" to Triple(126, 86, 16),
            SIGN_NODE to Triple(41, 104, 8),
            "hajek" to Triple(126, 170, 18),
            "ohniste" to Triple(32, 206, 9),
            "sutiny" to Triple(122, 268, 16),
            "pole" to Triple(34, 320, 16),
            MILESTONE_NODE to Triple(122, 352, 8),
            CITY_GATE_NODE to Triple(80, 418, 14)
        )
    )

    /** Uzly, které něco dělají mimo divoká setkání. */
    val ACTION_NODES = setOf(ENTRY_NODE, SIGN_NODE, MILESTONE_NODE, CITY_GATE_NODE)

    const val SIGN_TEXT = "🪧 Cedule je ohořelá. Šipka dolů ukazuje k bílým hradbám a pod ní jde přečíst jen:\n„OZVĚNA – vítejte zpět.“"
    const val MILESTONE_TEXT = "🪨 Kamenný mezník. Na čele je vytesané „S“ a pod ním číslo, které někdo pečlivě oškrábal."
    const val MILESTONE_TEXT_INSIGHT = "\nKdyž se podíváš zblízka, pořád je tam vidět nula. S-0."
    const val CITY_GATE_TEXT = "🏰 Vrata Ozvěny jsou zavřená. Za hradbami je slyšet zvon a smích, jako by tam byl svátek.\n" +
        "(Město se teprve staví – brzy se otevře.)"

    fun emptyEncounterText(node: String): String? = when (node) {
        "duny" -> "Vítr zvedl obláček popela a zase ho položil. Zkus to znovu."
        "hajek" -> "Ve spálených stromech zapraskalo… jen větev. Zkus to znovu."
        "ohniste" -> "Uhlíky v ohništi zažhnuly a zase pohasly. Zkus to znovu."
        "sutiny" -> "V sutinách se něco pohnulo… jen kámen. Zkus to znovu."
        "pole" -> "Strašák se otočil ve větru. Nikdo tu není. Zkus to znovu."
        else -> null
    }
}

package cz.uhk.macroflow.pokemon.cave

/**
 * Popelavá pláň (Akt II, issue #26, docs/adr/0088): první kraj za Branou světů. Hráč vyjde
 * nahoře z Brány světů a sestupuje po staré cestě dolů k bráně starého města Ozvěny. Uzly a hrany kopírují
 * tools/mapgen/gen_ashplain.py – ZDROJ PRAVDY je v obou místech.
 */
object AshPlain {
    const val VISITED_KEY = "ash_plain_visited"

    const val ENTRY_NODE = "vstup_z_brany"
    const val SIGN_NODE = "cedule"
    const val MILESTONE_NODE = "meznik"
    const val CITY_GATE_NODE = "brana_mesta"
    /** Střed prstence Brány světů na mapě pláně (art px) – sem se kreslí točící se závoj. */
    const val GATE_X = 80
    const val GATE_Y = 36

    val MAP = CaveMap(
        artW = 160, artH = 448,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 80, 78),
            CaveNode("n1", 80, 112), CaveNode(SIGN_NODE, 52, 100), CaveNode("n2", 48, 112), CaveNode("duny", 112, 160),
            CaveNode("n3", 48, 192), CaveNode(QuietOrchard.PLAIN_NODE, 10, 192), CaveNode("ohniste", 40, 236), CaveNode("n4", 96, 192), CaveNode(EchoCanyon.PLAIN_NODE, 150, 192), CaveNode("hajek", 124, 244),
            CaveNode("n5", 96, 272), CaveNode(MILESTONE_NODE, 112, 300), CaveNode("n6", 64, 272),
            CaveNode("sutiny", 36, 326), CaveNode("pole", 124, 326),
            CaveNode("n7", 64, 336), CaveNode(CITY_GATE_NODE, 80, 432)
        ),
        edges = listOf(
            ENTRY_NODE to "n1", "n1" to SIGN_NODE, "n1" to "n2", "n1" to "duny", "n2" to "n3",
            "n3" to "ohniste", "n3" to QuietOrchard.PLAIN_NODE, "n3" to "n4", "n4" to "hajek", "n4" to EchoCanyon.PLAIN_NODE, "n4" to "n5", "n5" to MILESTONE_NODE, "n5" to "n6",
            "n6" to "sutiny", "n6" to "n7", "n7" to "pole", "n7" to CITY_GATE_NODE
        ),
        exitNode = ENTRY_NODE,
        mountainNode = SkyPass.GATE_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("duny", "hajek", "ohniste", "sutiny", "pole"),
        parentBiome = "SKY_PASS",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(80, 36, 26),
            QuietOrchard.PLAIN_NODE to Triple(14, 192, 18),
            EchoCanyon.PLAIN_NODE to Triple(146, 192, 18),
            CITY_GATE_NODE to Triple(80, 422, 16),
            SIGN_NODE to Triple(52, 80, 8),
            MILESTONE_NODE to Triple(112, 284, 8),
            "duny" to Triple(126, 148, 22),
            "hajek" to Triple(132, 258, 20),
            "ohniste" to Triple(40, 220, 10),
            "sutiny" to Triple(24, 300, 22),
            "pole" to Triple(126, 326, 22)
        )
    )

    /** Uzly, které něco dělají mimo divoká setkání. */
    val ACTION_NODES = setOf(ENTRY_NODE, SIGN_NODE, MILESTONE_NODE, CITY_GATE_NODE, QuietOrchard.PLAIN_NODE, EchoCanyon.PLAIN_NODE)

    const val SIGN_TEXT = "🪧 Cedule je ohořelá. Šipka ukazuje k bílým hradbám a pod ní jde přečíst jen:\n„OZVĚNA – vítejte zpět.“"
    const val MILESTONE_TEXT = "🪨 Kamenný mezník. Na čele je vytesané „S“ a pod ním číslo, které někdo pečlivě oškrábal."
    const val MILESTONE_TEXT_INSIGHT = "\nKdyž se podíváš zblízka, pořád je tam vidět nula. S-0."

    fun emptyEncounterText(node: String): String? = when (node) {
        "duny" -> "Suchá tráva zašustila a ve vzduchu se zatočil popel. Zkus to znovu."
        "hajek" -> "Ve spálených stromech zapraskalo… jen větev. Zkus to znovu."
        "ohniste" -> "Uhlíky v ohništi zažhnuly a zase pohasly. Zkus to znovu."
        "sutiny" -> "V sutinách se něco pohnulo… jen kámen. Zkus to znovu."
        "pole" -> "Strašák se otočil ve větru. Nikdo tu není. Zkus to znovu."
        else -> null
    }
}

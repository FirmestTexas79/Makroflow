package cz.uhk.macroflow.pokemon.cave

/**
 * Ztichlý sad (Akt II, docs/adr/0089): noční sad vlevo od Popelavé pláně – vchází se do něj zprava branou mezi sloupky. Ve dne je slunný,
 * v noci (19:00–5:00) má jinou, měsíční mapu a světlušky. Uzly a hrany kopírují tools/mapgen/gen_orchard.py – ZDROJ PRAVDY je v obou místech.
 */
object QuietOrchard {
    const val VISITED_KEY = "quiet_orchard_visited"
    const val ENTRY_NODE = "vstup_ze_plane"
    const val WELL_NODE = "studna"
    const val HUT_NODE = "bouda"
    /** Uzel na Popelavé pláni, odkud se do sadu chodí. */
    const val PLAIN_NODE = "k_sadu"

    const val NIGHT_FROM = 19
    const val NIGHT_UNTIL = 5

    /** V noci (19:00–5:00) má sad noční mapu a světlušky; přes den je otevřený a slunný. */
    fun isNight(hour: Int): Boolean = hour >= NIGHT_FROM || hour < NIGHT_UNTIL

    val MAP = CaveMap(
        artW = 160, artH = 320,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 152, 80),
            CaveNode("s1", 80, 80), CaveNode("s2", 80, 160), CaveNode("s3", 40, 160), CaveNode("s4", 120, 160),
            CaveNode("jezirko_sad", 64, 214), CaveNode("ule", 120, 216), CaveNode(WELL_NODE, 96, 232),
            CaveNode("s5", 80, 238), CaveNode("stara_jablon", 52, 292), CaveNode("trava_sad", 28, 270), CaveNode(HUT_NODE, 124, 300),
            CaveNode(cz.uhk.macroflow.pokemon.story.Fenn.NODE, 94, 306)
        ),
        edges = listOf(
            ENTRY_NODE to "s1", "s1" to "s2", "s2" to "s3", "s2" to "s4", "s3" to "jezirko_sad",
            "s4" to "ule", "s2" to WELL_NODE, "s2" to "s5", "s5" to "stara_jablon", "s5" to "trava_sad", "s5" to HUT_NODE,
            "s5" to cz.uhk.macroflow.pokemon.story.Fenn.NODE
        ),
        exitNode = ENTRY_NODE,
        mountainNode = PLAIN_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("jezirko_sad", "ule", "stara_jablon", "trava_sad"),
        parentBiome = "ASH_PLAIN",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(136, 80, 20),
            "jezirko_sad" to Triple(34, 210, 22),
            "ule" to Triple(126, 202, 18),
            WELL_NODE to Triple(96, 216, 10),
            "stara_jablon" to Triple(80, 270, 24),
            "trava_sad" to Triple(28, 272, 18),
            HUT_NODE to Triple(126, 278, 22),
            cz.uhk.macroflow.pokemon.story.Fenn.NODE to Triple(cz.uhk.macroflow.pokemon.story.Fenn.X, cz.uhk.macroflow.pokemon.story.Fenn.Y - 12, 9)
        )
    )

    val ACTION_NODES = setOf(ENTRY_NODE, WELL_NODE, HUT_NODE, cz.uhk.macroflow.pokemon.story.Fenn.NODE)

    const val WELL_TEXT = "🪣 Studna je hluboká a voda v ní stojí bez jediné vlnky. Když se nakloníš, odraz se pohne o chvilku později než ty."
    const val HUT_TEXT = "🏚 Zahradníkova chalupa. Na stole leží seznam stromů a u každého je škrtnuté jméno.\n" +
        "Lucerna svítí, ale nikdo tu není. Košík jablek je ještě teplý."

    fun emptyEncounterText(node: String): String? = when (node) {
        "jezirko_sad" -> "Na hladině se zachvěl odraz měsíce. Zkus to znovu."
        "ule" -> "Z úlů není nic slyšet. Ani bzukot. Zkus to znovu."
        "stara_jablon" -> "Zlatá jablka se zhoupla, ale nespadlo žádné. Zkus to znovu."
        "trava_sad" -> "Ve vlhké trávě se jen zatřpytila rosa. Zkus to znovu."
        else -> null
    }
}

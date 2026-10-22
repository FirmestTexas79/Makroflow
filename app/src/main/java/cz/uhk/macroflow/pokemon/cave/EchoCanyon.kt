package cz.uhk.macroflow.pokemon.cave

/**
 * Ozvěnový kaňon (Akt II, docs/adr/0090): odbočka vpravo z Popelavé pláně. Hráč přijde zleva, sestupuje dnem kaňonu,
 * přejde visutý most přes průrvu a dojde do kotliny pod Ozvěnovou stěnou. Uzly a hrany kopírují
 * tools/mapgen/gen_canyon.py – ZDROJ PRAVDY je v obou místech.
 */
object EchoCanyon {
    const val VISITED_KEY = "echo_canyon_visited"
    const val ENTRY_NODE = "vstup_z_plane"
    const val BRIDGE_NODE = "most"
    const val WALL_NODE = "ozvenova_stena"
    const val BONES_NODE = "kosti"
    /** Uzel na Popelavé pláni, odkud se do kaňonu chodí. */
    const val PLAIN_NODE = "ke_kanonu"

    val MAP = CaveMap(
        artW = 160, artH = 384,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 6, 58), CaveNode("k1", 80, 58), CaveNode("hnizdo", 72, 88), CaveNode("k2", 88, 120),
            CaveNode("jeskynka", 104, 186), CaveNode("rimsa", 40, 168), CaveNode(BRIDGE_NODE, 80, 196), CaveNode("k3", 80, 226),
            CaveNode("koryto", 52, 232), CaveNode("k4", 40, 264), CaveNode("k5", 120, 264), CaveNode(WALL_NODE, 80, 328),
            CaveNode(BONES_NODE, 44, 344)
        ),
        edges = listOf(
            ENTRY_NODE to "k1", "k1" to "hnizdo", "k1" to "k2", "k2" to "jeskynka", "k2" to "rimsa", "k2" to BRIDGE_NODE,
            BRIDGE_NODE to "k3", "k3" to "koryto", "k3" to "k4", "k3" to "k5", "k4" to WALL_NODE, "k5" to WALL_NODE,
            WALL_NODE to BONES_NODE
        ),
        exitNode = ENTRY_NODE,
        mountainNode = PLAIN_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("hnizdo", "jeskynka", "rimsa", "koryto"),
        parentBiome = "ASH_PLAIN",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(20, 58, 20),
            "hnizdo" to Triple(54, 78, 12),
            "jeskynka" to Triple(104, 166, 12),
            "rimsa" to Triple(38, 156, 14),
            BRIDGE_NODE to Triple(80, 197, 14),
            "koryto" to Triple(64, 228, 18),
            WALL_NODE to Triple(88, 300, 22),
            BONES_NODE to Triple(46, 358, 18)
        )
    )

    val ACTION_NODES = setOf(ENTRY_NODE, BRIDGE_NODE, WALL_NODE, BONES_NODE)

    const val BRIDGE_TEXT = "🌉 Visutý most se zhoupne. Z průrvy pod ním stoupá teplý vzduch a ozvěna tvých kroků přichází o krok dřív než ty."
    const val BONES_TEXT = "🦴 Obří kosti napůl zavátá pískem. Na žebru je vypálené razítko Kustodiátu a číslo 019."

    /** Co ozvěna vrátí, když hráč u stěny zavolá (střídá se podle dne). */
    private val ECHOES = listOf(
        "Zavoláš do kaňonu… a ozvěna ti vrátí jméno, které jsi neřekl: „…Hunfrey…“",
        "Zavoláš své jméno. Ozvěna ho zopakuje třikrát – a potřetí ho řekne cizím hlasem.",
        "Zavoláš. Ozvěna odpoví: „Subjekt připraven.“ Pak je dlouho ticho.",
        "Ozvěna vrací jen šepot. Rozeznáš z něj počítání: dvě stě jedenáct… dvě stě dvanáct…"
    )

    fun echo(dayOfYear: Int): String =
        "🗿 Ozvěnová stěna je posetá vyrytými dlaněmi a čárkami – stovky jmen, která už nikdo nepřečte.\n" +
            ECHOES[Math.floorMod(dayOfYear, ECHOES.size)]

    fun emptyEncounterText(node: String): String? = when (node) {
        "hnizdo" -> "V hnízdě se pohnula vejce… ale nic se nevylíhlo. Zkus to znovu."
        "jeskynka" -> "Z jeskyňky zafoukal vítr a vrátil ti tvůj dech. Zkus to znovu."
        "rimsa" -> "Z římsy se sesypalo pár kamínků. Zkus to znovu."
        "koryto" -> "Ve vyschlém korytě zachrastily oblázky. Zkus to znovu."
        else -> null
    }
}

package cz.uhk.macroflow.pokemon.cave

/**
 * Dormitoř – 8. patro Věže Řádu Váhy, patro duchů. Věčná noc v ložnici „kalibrovaných“: řady železných postelí,
 * pod přikrývkami spáči, kteří se neprobudí, svíčky, okna s měsícem, stolek noční sestry s knihou spánku. Druhý vrchol
 * tématu spánku. Ve studené mlze se potkávají divocí Makromoni typu DUCH (SpawnManager.TYPED_FLOORS).
 * Uzly kopírují tools/mapgen/gen_dorm.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.DORM.
 */
object TowerDorm {
    const val ELEVATOR_NODE = "duch_vytah"
    val FOG_NODES = setOf("duch_mlha_1", "duch_mlha_2", "duch_mlha_3")

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ELEVATOR_NODE, 88, 104), CaveNode("duch_stred", 88, 206), CaveNode("duch_mlha_1", 30, 292),
            CaveNode("duch_mlha_2", 146, 288), CaveNode("duch_mlha_3", 88, 300), CaveNode("duch_postele_l", 42, 200),
            CaveNode("duch_postele_p", 134, 200), CaveNode("duch_stul", 112, 330), CaveNode("duch_paravan", 62, 330),
            CaveNode("duch_okno", 36, 104)
        ),
        edges = listOf(ELEVATOR_NODE to "duch_stred", ELEVATOR_NODE to "duch_okno", "duch_stred" to "duch_postele_l",
            "duch_stred" to "duch_postele_p", "duch_stred" to "duch_mlha_3", "duch_mlha_3" to "duch_mlha_1",
            "duch_mlha_3" to "duch_mlha_2", "duch_mlha_3" to "duch_stul", "duch_mlha_3" to "duch_paravan"),
        exitNode = ELEVATOR_NODE,
        mountainNode = TowerLobby.ELEVATOR_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = FOG_NODES,
        parentBiome = "TOWER_LOBBY",
        isCave = false,
        tapAreas = mapOf(
            ELEVATOR_NODE to Triple(88, 66, 22), "duch_postele_l" to Triple(42, 160, 26), "duch_postele_p" to Triple(134, 160, 26),
            "duch_stul" to Triple(120, 306, 18), "duch_paravan" to Triple(40, 306, 18), "duch_okno" to Triple(24, 40, 14)
        )
    )

    val TEXTS = mapOf(
        "duch_postele_l" to "🛏️ Železné postele s čísly 198–201. Pod přikrývkami se rýsují tvary. Dýchají – pomalu, všichni ve stejném rytmu.\nNikdo se neprobudí, ani když promluvíš.",
        "duch_postele_p" to "🛏️ Postele 202–205. Jedna je prázdná a ustlaná do ostrých hran. Na cedulce číslo 213.",
        "duch_stul" to "📖 Stolek noční sestry. V knize spánku je u každého jména stejný údaj: SPÁNEK – ∞. Poslední řádek je nadepsaný, ale prázdný: SUBJEKT 213.",
        "duch_paravan" to "🩻 Plátěná zástěna. Za ní stojí stín postavy. Když ji obejdeš, nikdo tam není.",
        "duch_okno" to "🌙 Vysoké okno se záclonou. Venku je pořád stejný měsíc. Hodiny vedle stojí na 6:12, ale kyvadlo se kývá."
    )

    val ACTION_NODES = TEXTS.keys + ELEVATOR_NODE
}

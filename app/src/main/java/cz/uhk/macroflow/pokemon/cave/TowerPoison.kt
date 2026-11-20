package cz.uhk.macroflow.pokemon.cave

/**
 * Jedová laboratoř – 12. patro Věže Řádu Váhy (typ JED). Zarostlý skleník, kde Řád vaří spánek a zapomnění.
 * V louži jedu se vzácně potká Mydrus (jediný druh typu JED; SpawnManager.TYPED_FLOORS) – proto nižší šance setkání.
 * Uzly kopírují tools/mapgen/gen_poison.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.POISON.
 */
object TowerPoison {
    const val ELEVATOR_NODE = "jed_vytah"
    val PUDDLE_NODES = setOf("jed_louze_1", "jed_louze_2", "jed_louze_3")

    /** Šance (v %), že z louže něco vyleze – jediný jedový druh má být vzácný. */
    const val ENCOUNTER_CHANCE = 35

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ELEVATOR_NODE, 88, 104), CaveNode("jed_masky", 146, 106), CaveNode("jed_stred", 88, 158),
            CaveNode("jed_stul", 44, 170), CaveNode("jed_past", 138, 176), CaveNode("jed_louze_1", 88, 178),
            CaveNode("jed_kad", 34, 244), CaveNode("jed_kotel", 100, 268), CaveNode("jed_louze_2", 52, 292),
            CaveNode("jed_louze_3", 126, 300), CaveNode("jed_zahon", 88, 318), CaveNode("jed_valec", 114, 162)
        ),
        edges = listOf(ELEVATOR_NODE to "jed_stred", ELEVATOR_NODE to "jed_masky", "jed_stred" to "jed_stul",
            "jed_stred" to "jed_past", "jed_stred" to "jed_louze_1", "jed_louze_1" to "jed_kad",
            "jed_louze_1" to "jed_kotel", "jed_kad" to "jed_louze_2", "jed_kotel" to "jed_louze_3",
            "jed_louze_2" to "jed_zahon", "jed_louze_3" to "jed_zahon", "jed_stred" to "jed_valec"),
        exitNode = ELEVATOR_NODE,
        mountainNode = TowerLobby.ELEVATOR_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = PUDDLE_NODES,
        parentBiome = "TOWER_LOBBY",
        isCave = false,
        tapAreas = mapOf(
            ELEVATOR_NODE to Triple(88, 66, 22), "jed_masky" to Triple(152, 72, 14), "jed_stul" to Triple(36, 140, 20),
            "jed_past" to Triple(146, 144, 18), "jed_kad" to Triple(30, 212, 14), "jed_kotel" to Triple(88, 228, 24),
            "jed_zahon" to Triple(88, 326, 60), "jed_valec" to Triple(114, 128, 12)
        )
    )

    val TEXTS = mapOf(
        "jed_stul" to "⚗️ Pracovní stůl. Hmoždíř s fialovým práškem, lékárnické váhy a lahvičky se štítky SPÁNEK – DÁVKA 207, 208, 209…\nPoslední lahvička je prázdná a vymytá. Na štítku: 213.",
        "jed_past" to "🌱 Masožravka velká jako pes. Když se přiblížíš, sklapne naprázdno. Na květináči je cedulka: KRMIT JEN NEZAPSANÝMI.",
        "jed_kotel" to "🧪 Destilační přístroj. Z hadu kape zelená tekutina do baňky s nápisem ZAPOMNĚNÍ – KONCENTRÁT.\nPára voní jako čerstvý chléb z Brannovy pekárny.",
        "jed_kad" to "🫧 Káď plná bublající zelené kaše. Na hladině se občas něco vynoří – možná list, možná prst. Na obruči je vyraženo: ŠARŽE 7.",
        "jed_zahon" to "🍇 Záhony rulíku, bolehlavu a fialových hub. Mezi rostlinami jsou zapíchnuté jmenovky jako na hrobech. Jména jsou vybledlá, čitelná zůstala jen čísla.",
        "jed_valec" to "🧫 Preparátový válec. V zelené tekutině se vznáší bledý zárodek rostliny s kořínky jako prsty. Když se pohneš, pomalu se za tebou otočí.\nŠtítek: KONTROLNÍ VZOREK – SPÍ OD OKRUHU 1.",
        "jed_masky" to "😷 Plynové masky na háčcích. Jeden háček je prázdný a visí na něm cedulka: VYDÁNO – SUBJEKT 212. NEVRÁCENO."
    )

    const val EMPTY_TEXT = "Z louže vyšla bublina a s mlasknutím praskla. Zkus to znovu."

    val ACTION_NODES = TEXTS.keys + ELEVATOR_NODE
}

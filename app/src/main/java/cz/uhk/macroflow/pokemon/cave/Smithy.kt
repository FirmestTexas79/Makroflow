package cz.uhk.macroflow.pokemon.cave

/**
 * Stará kovárna v Horách – region 1, domov minihry Výheň (docs/adr/0118). Kovář Magmor (Makromon) hráče seznámí
 * s výhní, měchem a přetavením; u výhně se minihra otevírá. Bez divokých setkání.
 * Uzly kopírují tools/mapgen/gen_smithy.py – ZDROJ PRAVDY je v obou místech.
 */
object Smithy {
    const val ENTRY_NODE = "kv_vstup"
    const val MAGMOR_NODE = "kv_magmor"
    const val FORGE_NODE = "kv_vyhen"
    /** Uzel dveří na mapě Hor. */
    const val DOOR_NODE = "kovarna"
    const val NAME = "Kovář Magmor"
    const val MET_KEY = "kovarna_magmor"
    val KEYS = setOf(MET_KEY)

    /** Pata spritu kováře Magmora v místnosti (kreslí ho aplikace přes mapu – placeMakromonNpc; gen_smithy.py: MAGMOR_X/Y). */
    const val MAGMOR_X = 116
    const val MAGMOR_Y = 172

    /** Tři podstavce u pece (střed, horní plocha) – na ně aplikace staví relikvie ukované přetavením Výhně (gen_smithy.py: PEDESTALS). */
    val PEDESTALS = listOf(100 to 88, 116 to 88, 132 to 88)
    const val PEDESTAL_NODE = "kv_podstavce"

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 88, 342), CaveNode("kv_stred", 88, 238), CaveNode(MAGMOR_NODE, 114, 190), CaveNode(FORGE_NODE, 40, 124),
            CaveNode("kv_kovadlina", 76, 188), CaveNode("kv_brus", 150, 200), CaveNode("kv_stojan", 144, 268), CaveNode("kv_ponk", 40, 258),
            CaveNode("kv_police", 152, 122), CaveNode(PEDESTAL_NODE, 116, 124)
        ),
        edges = listOf(ENTRY_NODE to "kv_stred", "kv_stred" to MAGMOR_NODE, "kv_stred" to "kv_kovadlina", "kv_kovadlina" to FORGE_NODE,
            "kv_stred" to "kv_brus", "kv_stred" to "kv_stojan", "kv_stred" to "kv_ponk", MAGMOR_NODE to "kv_police",
            "kv_brus" to "kv_police", MAGMOR_NODE to FORGE_NODE, MAGMOR_NODE to PEDESTAL_NODE),
        exitNode = ENTRY_NODE,
        mountainNode = DOOR_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = emptySet(),
        parentBiome = "MOUNTAINS",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(88, 346, 12), MAGMOR_NODE to Triple(116, 160, 14), FORGE_NODE to Triple(40, 84, 26),
            "kv_kovadlina" to Triple(76, 166, 12), "kv_brus" to Triple(150, 173, 12), "kv_stojan" to Triple(144, 240, 18),
            "kv_ponk" to Triple(40, 235, 20), "kv_police" to Triple(152, 88, 16), PEDESTAL_NODE to Triple(116, 88, 22)
        )
    )

    val TEXTS = mapOf(
        "kv_kovadlina" to "⚒️ Kovadlina na dubovém pařezu. Plocha je vyleštěná do zrcadla tisíci údery a uprostřed má mělkou prohlubeň – " +
            "tady se kovalo vždycky na stejném místě.",
        "kv_brus" to "🪨 Brus s klikou. Kámen je ohlazený do vajíčka. Na rámu jsou vyrytá jména – tolik, že se překrývají.",
        "kv_stojan" to "🗡️ Stojan se zbraněmi. Meče, sekera, kopí. Nikdo je nevyzvedl. Na každé visí cedulka „HOTOVO“ a pod ní datum, " +
            "které už nikdo nepočítá.",
        "kv_ponk" to "🔨 Ponk: kus kůže, rozdělaná čepel, kleště. Všechno připravené, jako by kovář jen na chvíli odešel. Magmor ale stojí u kovadliny.",
        PEDESTAL_NODE to "🏺 Tři kamenné podstavce se zlatou obrubou. Každý má v desce mělké lůžko. Magmor říká, že na ně patří " +
            "relikvie, které vzejdou z přetavení výhně – Srdce výhně, Kovadlina předků a Kladivo mistra.",
        "kv_police" to "🧪 Regál s lahvičkami. Kalicí oleje, sůl, borax – a jedna lahvička bez nálepky, ve které se pomalu točí světlo."
    )

    const val INTRO = "U kovadliny stojí Magmor. Po šupinách mu přebíhají žhavé žilky a kladivo drží, jako by ho nosil odjakživa.\n\n" +
        "„Další poutník. Výheň už dlouho nikdo pořádně nerozdmýchal,“ zabručí.\n\n" +
        "„Tady se nekuje jen železo. Kdo umí šlapat měch, ukove si i kůži – obranu, odolnost proti ohni a zemi, síť, ze které nic neuteče. " +
        "Za každé šlápnutí na měch dostaneš uhlíky. A když chodíš a spíš, jak se patří, oheň hoří líp – výheň pozná, jestli jsi odpočatý.“\n\n" +
        "„Rudu, dřevo a brouky si přines z hor, z louky a z dolů. A až výheň rozpálíš do běla, přetavíme ji. Začneš znovu – " +
        "ale z každého přetavení zůstane něco, co se už nikdy neztratí.“"
    const val GREETING = "„Měch je tamhle. Šlapej,“ kývne Magmor ke výhni. „A nezapomeň se vyspat. Unavená ruka kove křivě.“"
    const val OPEN = "🔥 Rozdmýchat výheň"

    val ACTION_NODES = TEXTS.keys + setOf(ENTRY_NODE, MAGMOR_NODE, FORGE_NODE)
}

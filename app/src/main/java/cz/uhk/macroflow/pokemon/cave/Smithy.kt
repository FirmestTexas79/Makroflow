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

    val MAP = CaveMap(
        artW = 176, artH = 240,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 88, 230), CaveNode("kv_stred", 88, 190), CaveNode(MAGMOR_NODE, 106, 164), CaveNode(FORGE_NODE, 46, 126),
            CaveNode("kv_kovadlina", 66, 166), CaveNode("kv_brus", 146, 182), CaveNode("kv_stojan", 138, 210), CaveNode("kv_ponk", 40, 208),
            CaveNode("kv_police", 150, 128)
        ),
        edges = listOf(ENTRY_NODE to "kv_stred", "kv_stred" to MAGMOR_NODE, "kv_stred" to "kv_kovadlina", "kv_kovadlina" to FORGE_NODE,
            "kv_stred" to "kv_brus", "kv_stred" to "kv_stojan", "kv_stred" to "kv_ponk", MAGMOR_NODE to "kv_police",
            "kv_brus" to "kv_police", MAGMOR_NODE to FORGE_NODE),
        exitNode = ENTRY_NODE,
        mountainNode = DOOR_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = emptySet(),
        parentBiome = "MOUNTAINS",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(88, 234, 12), MAGMOR_NODE to Triple(104, 142, 16), FORGE_NODE to Triple(46, 88, 26),
            "kv_kovadlina" to Triple(68, 140, 14), "kv_brus" to Triple(150, 158, 14), "kv_stojan" to Triple(146, 188, 18),
            "kv_ponk" to Triple(38, 186, 20), "kv_police" to Triple(152, 92, 16)
        )
    )

    val TEXTS = mapOf(
        "kv_kovadlina" to "⚒️ Kovadlina na dubovém pařezu. Plocha je vyleštěná do zrcadla tisíci údery a uprostřed má mělkou prohlubeň – " +
            "tady se kovalo vždycky na stejném místě.",
        "kv_brus" to "🪨 Brus s klikou. Kámen je ohlazený do vajíčka. Na rámu jsou vyrytá jména – tolik, že se překrývají.",
        "kv_stojan" to "🗡️ Stojan se zbraněmi. Meče, sekera, kopí. Nikdo je nevyzvedl. Na každé visí cedulka „HOTOVO“ a pod ní datum, " +
            "které už nikdo nepočítá.",
        "kv_ponk" to "🔨 Ponk: kus kůže, rozdělaná čepel, kleště. Všechno připravené, jako by kovář jen na chvíli odešel. Magmor ale stojí u kovadliny.",
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

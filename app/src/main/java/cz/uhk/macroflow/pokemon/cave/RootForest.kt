package cz.uhk.macroflow.pokemon.cave

/**
 * Zóna 3 (Akt III – Hlubina), lokace 7 z 8: LES POD KORUNOU (docs/adr/0106) – poslední lokace před Kmenem. Dno dutiny pod
 * obrácenou korunou: holé větve visí shora jako obrácený les, dole se rozpadají na svítící záclony větviček. Mlha zapomnění
 * z rány leží ve vrstvách, stopy chodí v kruhu, ztracená sonda Řádu pořád pípá, značky na kůře se rozmazávají (přečíst je
 * půjde po spánku – fáze kapitoly VI). Dole přes jedinou větev ke kmeni leží Drakirra (NPC; souboj přijde s kapitolou VI).
 * Uzly kopírují tools/mapgen/gen_crown_forest.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.ROOT_FOREST.
 */
object RootForest {
    const val ENTRY_NODE = "lp_vstup"
    const val DRAKIRRA_NODE = "lp_drakirra"
    const val TRUNK_NODE = "lp_kmen"
    const val VISITED_KEY = "zona3_les_pod_korunou"
    /** Pata Drakirry (art px). */
    const val DRAKIRRA_X = 88
    const val DRAKIRRA_Y = 486

    val MAP = CaveMap(
        artW = 176, artH = 560,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 90, 22), CaveNode("lp_cesta1", 70, 96), CaveNode("lp_znacky1", 124, 140), CaveNode("lp_kruh", 56, 200),
            CaveNode("lp_sonda", 120, 250), CaveNode("lp_cesta2", 82, 300), CaveNode("lp_znacky2", 38, 352), CaveNode("lp_ticho", 112, 392),
            CaveNode("lp_cesta3", 70, 440), CaveNode(DRAKIRRA_NODE, 88, 492), CaveNode(TRUNK_NODE, 88, 548)
        ),
        edges = listOf(ENTRY_NODE to "lp_cesta1", "lp_cesta1" to "lp_znacky1", "lp_cesta1" to "lp_kruh", "lp_kruh" to "lp_sonda",
            "lp_znacky1" to "lp_sonda", "lp_kruh" to "lp_cesta2", "lp_sonda" to "lp_cesta2", "lp_cesta2" to "lp_znacky2",
            "lp_cesta2" to "lp_ticho", "lp_znacky2" to "lp_cesta3", "lp_ticho" to "lp_cesta3", "lp_cesta3" to DRAKIRRA_NODE,
            DRAKIRRA_NODE to TRUNK_NODE),
        exitNode = ENTRY_NODE,
        mountainNode = "sb_les",
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("lp_cesta1", "lp_cesta2", "lp_cesta3"),
        parentBiome = "ROOT_NAMELESS",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(90, 16, 14), "lp_znacky1" to Triple(150, 112, 16), "lp_kruh" to Triple(66, 200, 26),
            "lp_sonda" to Triple(124, 232, 12), "lp_znacky2" to Triple(16, 300, 14), "lp_ticho" to Triple(112, 392, 30),
            DRAKIRRA_NODE to Triple(88, 460, 30), TRUNK_NODE to Triple(92, 540, 16)
        )
    )

    val TEXTS = mapOf(
        ENTRY_NODE to "⬆️ Kolíky Řádu vedou zpátky nahoru na Svah bezejmenných.",
        "lp_znacky1" to "🌳 Na kůře visící větve jsou vyryté značky. Když se na ně díváš, rozmazávají se – jako bys je četl ve snu.\n" +
            "Možná by šly přečíst, kdyby ses pořádně vyspal.",
        "lp_kruh" to "👣 Stopy v popelu chodí v kruhu. Desítky párů, pořád dokola, až je kruh vyšlapaný do hloubky. Uprostřed nikdo není.\n" +
            "Vedle kolík Řádu se třemi směrovkami. Na všech třech stojí VEN. Každá ukazuje jinam.",
        "lp_sonda" to "📡 Ztracená sonda Řádu zapletená ve větvičkách, které ji pomalu obrůstají. Pořád pípá.\n" +
            "Na displeji: STANICE 8 – SIGNÁL: 0 – HLÁŠENÍ ODESLÁNO: 4 113. Žádné nepřijato.",
        "lp_znacky2" to "🌳 Další značky na kůře, tyhle čerstvější – vyryté nehtem. Tři rovné zářezy a čtvrtý nedokončený.\n" +
            "Někdo tu počítal noci. Pak přestal.",
        "lp_ticho" to "🌫️ Mýtina ticha. Mlha z rány tu leží nejhustší a nepípá ani sonda. Na chvíli zapomeneš, proč jsi sem šel.\n" +
            "Pak si vzpomeneš. Nebo si to jen myslíš.",
        DRAKIRRA_NODE to "🐉 Drakirra. Leží přes cestu ke kmeni – přes jedinou větev, po které se dá vylézt nahoru. Oči má otevřené. Vidí.\n" +
            "Dívá se na tebe a pak na Spirru. Mezi zuby jí doutná žár. Tentokrát neodletí.",
        TRUNK_NODE to "⬆️ Nejtlustší větev se sklání až k zemi a stoupá vzhůru do tmy – ke kmeni. Ve kůře jsou vyšlapané stupy.\n" +
            "Přes ni leží Drakirra.\nZATÍM NEPŘÍSTUPNÉ – kapitola VI."
    )
    val ACTION_NODES = TEXTS.keys
}

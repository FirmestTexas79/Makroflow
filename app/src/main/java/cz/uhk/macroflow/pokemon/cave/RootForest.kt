package cz.uhk.macroflow.pokemon.cave

/**
 * Zóna 3 (Akt III – Hlubina), lokace 7 z 8: LES POD KORUNOU (docs/adr/0106, 0107) – rozlehlý les (704 × 1120) před Prahem.
 * Dno dutiny pod obrácenou korunou: holé větve visí shora jako obrácený les, mlha zapomnění z rány leží ve vrstvách,
 * stezky se větví a vracejí. Kruh stop s Gudwinem, který zapomenout nemůže (vedlejší úkol Jména v mlze –
 * story/ForestNames.kt), strom značek, ztracená sonda Řádu, opuštěný tábor výpravy 6, tůň, mýtina ticha, lucerny, sedm
 * jmenovek ve větvičkách, šest mechových palouků se setkáními (PŘÍRODA, JED). Dole brána na Práh (RootThreshold).
 * Uzly kopírují tools/mapgen/gen_crown_forest.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.ROOT_FOREST.
 */
object RootForest {
    const val ENTRY_NODE = "lp_vstup"
    const val THRESHOLD_NODE = "lp_prah"
    const val GUDWIN_NODE = "lp_gudwin"
    const val VISITED_KEY = "zona3_les_pod_korunou"

    /** Gudwin u kruhu stop (art px, pata). */
    const val GUDWIN_X = 200
    const val GUDWIN_Y = 304

    /** Uzly sedmi jmenovek (sprite visí kousek nad uzlem ve větvičkách). */
    val TAG_NODES = listOf("lp_jm1", "lp_jm2", "lp_jm3", "lp_jm4", "lp_jm5", "lp_jm6", "lp_jm7")

    val MAP = CaveMap(
        artW = 704, artH = 1120,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 352, 24), CaveNode("lp_cesta1", 330, 140), CaveNode("lp_znacky1", 440, 196), CaveNode("lp_mech1", 246, 214),
            CaveNode("lp_kruh", 168, 300), CaveNode(GUDWIN_NODE, 214, 318), CaveNode("lp_jm1", 86, 236), CaveNode("lp_sonda", 560, 300),
            CaveNode("lp_jm2", 646, 226), CaveNode("lp_mech2", 470, 340), CaveNode("lp_cesta2", 360, 430), CaveNode("lp_mech3", 500, 560),
            CaveNode("lp_tabor", 592, 520), CaveNode("lp_jm3", 660, 610), CaveNode("lp_mech4", 232, 600), CaveNode("lp_znacky2", 118, 520),
            CaveNode("lp_jm4", 58, 612), CaveNode("lp_tun", 176, 760), CaveNode("lp_jm5", 84, 840), CaveNode("lp_ticho", 420, 720),
            CaveNode("lp_jm6", 480, 792), CaveNode("lp_mech5", 300, 826), CaveNode("lp_lucerny", 604, 816), CaveNode("lp_jm7", 652, 904),
            CaveNode("lp_mech6", 530, 920), CaveNode("lp_cesta3", 342, 930), CaveNode("lp_brana", 352, 1012), CaveNode(THRESHOLD_NODE, 352, 1100)
        ),
        edges = listOf(ENTRY_NODE to "lp_cesta1", "lp_cesta1" to "lp_znacky1", "lp_cesta1" to "lp_mech1", "lp_mech1" to "lp_kruh",
            "lp_kruh" to GUDWIN_NODE, "lp_kruh" to "lp_jm1", GUDWIN_NODE to "lp_mech1", "lp_znacky1" to "lp_sonda",
            "lp_sonda" to "lp_jm2", "lp_sonda" to "lp_mech2", "lp_mech2" to "lp_cesta2", "lp_cesta1" to "lp_cesta2",
            "lp_cesta2" to "lp_mech4", "lp_mech4" to "lp_znacky2", "lp_znacky2" to "lp_jm4", GUDWIN_NODE to "lp_mech4",
            "lp_cesta2" to "lp_mech3", "lp_mech2" to "lp_mech3", "lp_mech3" to "lp_tabor", "lp_tabor" to "lp_jm3",
            "lp_mech4" to "lp_tun", "lp_tun" to "lp_jm5", "lp_tun" to "lp_mech5", "lp_cesta2" to "lp_ticho",
            "lp_ticho" to "lp_jm6", "lp_ticho" to "lp_mech5", "lp_ticho" to "lp_lucerny", "lp_lucerny" to "lp_jm7",
            "lp_lucerny" to "lp_mech6", "lp_mech3" to "lp_lucerny", "lp_mech5" to "lp_cesta3", "lp_mech6" to "lp_cesta3",
            "lp_ticho" to "lp_cesta3", "lp_cesta3" to "lp_brana", "lp_brana" to THRESHOLD_NODE),
        exitNode = ENTRY_NODE,
        mountainNode = "sb_les",
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("lp_mech1", "lp_mech2", "lp_mech3", "lp_mech4", "lp_mech5", "lp_mech6"),
        parentBiome = "ROOT_NAMELESS",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(352, 20, 16), "lp_znacky1" to Triple(466, 170, 18), "lp_kruh" to Triple(168, 300, 30),
            GUDWIN_NODE to Triple(GUDWIN_X, GUDWIN_Y - 12, 16), "lp_sonda" to Triple(564, 296, 14), "lp_znacky2" to Triple(88, 494, 18),
            "lp_tabor" to Triple(592, 510, 30), "lp_tun" to Triple(110, 736, 24), "lp_ticho" to Triple(420, 720, 40),
            "lp_lucerny" to Triple(604, 780, 34), "lp_brana" to Triple(352, 990, 34), THRESHOLD_NODE to Triple(352, 1096, 18)
        )
    )

    private const val MARKS_BLUR = "🌳 Na kůře visící větve jsou vyryté značky. Když se na ně díváš, rozmazávají se – jako bys je četl ve snu.\n" +
        "Možná by šly přečíst, kdyby ses pořádně vyspal."
    private const val MARKS_READ = "🌳 Značky na kůře se ti konečně nerozmazávají. Šipky a čísla výprav: 3, 5, 6, 7 – a u každé směr, " +
        "kam ten člověk odešel z kruhu. Všechny šipky vedou od stezky do větviček. Tam, kde mlha shodila jmenovky."

    val TEXTS = mapOf(
        ENTRY_NODE to "⬆️ Kolíky Řádu vedou zpátky nahoru na Svah bezejmenných.",
        "lp_znacky1" to MARKS_BLUR,
        "lp_kruh" to "👣 Stopy v popelu chodí v kruhu. Desítky párů, pořád dokola, až je kruh vyšlapaný do hloubky. Uprostřed nikdo není.\n" +
            "Vedle kolík Řádu se třemi směrovkami. Na všech třech stojí VEN. Každá ukazuje jinam.",
        "lp_sonda" to "📡 Ztracená sonda Řádu zapletená ve větvičkách, které ji pomalu obrůstají. Pořád pípá.\n" +
            "Na displeji: STANICE 8 – SIGNÁL: 0 – HLÁŠENÍ ODESLÁNO: 4 113. Žádné nepřijato.",
        "lp_znacky2" to "🌳 Další značky na kůře, tyhle čerstvější – vyryté nehtem. Tři rovné zářezy a čtvrtý nedokončený.\n" +
            "Někdo tu počítal noci. Pak přestal.",
        "lp_tabor" to "⛺ Tábor výpravy 6. Dva stany obrostlé větvičkami, jako by je strom zkoušel spolknout. Uvnitř rozložené spacáky – " +
            "nikdo v nich nespal. Na bedně deník hlídek: noci odškrtané do dvaceti. Pak už jen jedna věta pořád dokola: „Kdo jsme?“",
        "lp_tun" to "💧 Tůň černá jako inkoust. Když se nad ni skloníš, odraz má tvůj obličej – ale chvíli trvá, než si vzpomene, jak se usmívat.",
        "lp_ticho" to "🌫️ Mýtina ticha. Mlha z rány tu leží nejhustší a nepípá ani sonda. Na chvíli zapomeneš, proč jsi sem šel.\n" +
            "Pak si vzpomeneš. Nebo si to jen myslíš.",
        "lp_lucerny" to "🏮 Na větvičkách visí lucerny – ne z Řádu, z papíru a vosku. Jako by je sem pověsil někdo, kdo chtěl, aby se tu dalo " +
            "najít cestu zpátky. Některé pořád hoří. Nikdo je nepřikládá.",
        "lp_brana" to "🌳 Dvě nejtlustší větve se nad cestou kříží jako brána a sklánějí se až k zemi. Zpoza nich jde bledé světlo.\n" +
            "Tam dole nepípá ani sonda. Je tam ticho – takové, jaké bývá v kostele, než se začne zpívat."
    )

    /** Text místa podle toho, jestli se hráč vyspal (úkol Jména v mlze, fáze po spánku): značky jdou přečíst. */
    fun textFor(node: String, rested: Boolean): String? =
        if (node == "lp_znacky1" && rested) MARKS_READ else TEXTS[node]

    val ACTION_NODES = TEXTS.keys + THRESHOLD_NODE + GUDWIN_NODE + TAG_NODES
}

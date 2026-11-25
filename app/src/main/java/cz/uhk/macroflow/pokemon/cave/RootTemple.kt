package cz.uhk.macroflow.pokemon.cave

/**
 * Zóna 3 (Akt III – Hlubina), lokace 6 z 8: CHRÁM LUMENCŮ (docs/adr/0106). Ze Svahu bezejmenných po schodech Lumenců do lodi
 * chrámu, skoro celého v kořenech (pohlcení 6/8). Čtyři malby na zadní stěně (strom s hvězdou → král hvězdu utrhne → sní ji
 * a strom se obrací → žena ve stromě pod světem, lidé odcházejí do tmy), oltář přání, bezhlavá socha jelena ve sloupu
 * světla a jediný kolík Řádu NEINVENTARIZOVAT. Střípky pravé historie – bez prozrazení, že Makromoni byli lidé.
 * Uzly kopírují tools/mapgen/gen_temple.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.ROOT_TEMPLE.
 */
object RootTemple {
    const val ENTRY_NODE = "ch_vstup"
    const val VISITED_KEY = "zona3_chram_lumencu"

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 88, 338), CaveNode("ch_lod", 88, 292), CaveNode("ch_jelen", 58, 262), CaveNode("ch_kolik", 118, 276),
            CaveNode("ch_lod2", 88, 216), CaveNode("ch_oltar", 88, 170), CaveNode("ch_m1", 28, 116), CaveNode("ch_m2", 64, 112),
            CaveNode("ch_m3", 112, 112), CaveNode("ch_m4", 148, 116)
        ),
        edges = listOf(ENTRY_NODE to "ch_lod", "ch_lod" to "ch_jelen", "ch_lod" to "ch_kolik", "ch_lod" to "ch_lod2", "ch_jelen" to "ch_lod2",
            "ch_lod2" to "ch_oltar", "ch_oltar" to "ch_m2", "ch_oltar" to "ch_m3", "ch_m1" to "ch_m2", "ch_m3" to "ch_m4"),
        exitNode = ENTRY_NODE,
        mountainNode = "sb_chram",
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("ch_lod", "ch_lod2"),
        parentBiome = "ROOT_NAMELESS",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(88, 342, 16), "ch_jelen" to Triple(58, 226, 18), "ch_kolik" to Triple(124, 254, 18),
            "ch_oltar" to Triple(88, 146, 22), "ch_m1" to Triple(27, 54, 18), "ch_m2" to Triple(67, 54, 18),
            "ch_m3" to Triple(111, 54, 18), "ch_m4" to Triple(151, 54, 18)
        )
    )

    val TEXTS = mapOf(
        ENTRY_NODE to "🪜 Schody Lumenců vedou nahoru zpátky na Svah bezejmenných.",
        "ch_m1" to "🖼️ První malba. Strom uprostřed světa a v jeho koruně hvězda. Pod ním řada lidí se světlem v dlaních.\n" +
            "Všichni stejně velcí. Nikdo nemá korunu.",
        "ch_m2" to "🖼️ Druhá malba. Muž s korunou šplhá do koruny stromu a sahá po hvězdě. Ostatní pod ním se dívají nahoru.\n" +
            "Malíř mu nenamaloval obličej – jen korunu.",
        "ch_m3" to "🖼️ Třetí malba. Král drží hvězdu u úst. Ze stromu stéká tma a strom se obrací: kořeny nahoru, koruna dolů do modré tmy.\n" +
            "Barva je tu vydrápaná víc než jinde, jako by ji někdo chtěl seškrábat.",
        "ch_m4" to "🖼️ Čtvrtá malba, skoro celá pod kořeny. Obrácený strom visí pod světem a v jeho kmeni bledá žena s rukama dolů. " +
            "Lidé se světly od ní odcházejí do tmy, jeden za druhým.\nPod malbou vyrytý řádek znaků. Stejné znaky jsi viděl ve studni Věže: AŤ NÁS NIKDO NENAJDE.",
        "ch_oltar" to "🏺 Oltář přání. Kamenná mísa plná drobností: knoflíky, hřebínky, sušené květy, dětská píšťalka, prsten. " +
            "Ke každé věci je přivázaný proužek kůry s jedním znakem.\nNad mísou se pořád vznášejí drobné modré jiskry. Když k nim natáhneš ruku, na chvíli ti něco připomenou – a hned to zapomeneš.",
        "ch_jelen" to "🦌 Bezhlavá socha jelena ve sloupu bledého světla z prasklé klenby. Hlavu někdo odsekl čistě, dávno.\n" +
            "Kořeny chrámu se k soše natahují ze všech stran – a těsně před ní se zastaví.",
        "ch_kolik" to "📌 Jediný průzkumný kolík Řádu v celém chrámu. Na papírovém štítku: NEINVENTARIZOVAT. V rohu razítko S-0.\n" +
            "Výpravy Řádu všude měřily a počítaly. Tady ne. Někdo nahoře nechtěl, aby se to sem zapisovalo."
    )
    val ACTION_NODES = TEXTS.keys
}

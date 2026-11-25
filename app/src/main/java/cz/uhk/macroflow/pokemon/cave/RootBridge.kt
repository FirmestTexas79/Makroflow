package cz.uhk.macroflow.pokemon.cave

/**
 * Zóna 3 (Akt III – Hlubina) – vedlejší: KOŘENOVÝ MOST (docs/adr/0106). Gondola ze Signálního útesu (až po odpovědi
 * šesti světel) doveze hráče na konec lana: plošina na kořeni, který se klene přes propast ke kmeni Spící. Vysoká mapa
 * (kamera jede s hráčem vzhůru), nahoře celou šířku kmen. Pohlcení 6/8: sklad Operace Pečeť napůl v kůře, svatyňka
 * Lumenců s bytostí na trnu, obrys člověka v kůře. Vedlejší úkol „Noční směna“ (story/NightShift.kt).
 * Uzly kopírují tools/mapgen/gen_root_bridge.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.ROOT_BRIDGE.
 */
object RootBridge {
    private val N = cz.uhk.macroflow.pokemon.story.NightShift
    const val ENTRY_NODE = "km_vstup"
    const val VISITED_KEY = "zona3_korenovy_most"

    val MAP = CaveMap(
        artW = 176, artH = 560,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 104, 546), CaveNode("km_kotva", 70, 516), CaveNode("km_cesta1", 88, 470), CaveNode(N.TAPE_NODE, 76, 442),
            CaveNode("km_cesta2", 90, 392), CaveNode(N.SHRINE_NODE, 70, 346), CaveNode("km_nadrze", 124, 282), CaveNode("km_cesta3", 100, 236),
            CaveNode(N.VALVE_NODE, 136, 214), CaveNode(N.SCAFFOLD_NODE, 132, 152), CaveNode("km_obrys", 58, 148)
        ),
        edges = listOf(ENTRY_NODE to "km_kotva", ENTRY_NODE to "km_cesta1", "km_cesta1" to N.TAPE_NODE, "km_cesta1" to "km_cesta2",
            "km_cesta2" to N.SHRINE_NODE, "km_cesta2" to "km_nadrze", "km_nadrze" to "km_cesta3", "km_cesta3" to N.VALVE_NODE,
            "km_cesta3" to N.SCAFFOLD_NODE, "km_cesta3" to "km_obrys", "km_obrys" to N.SCAFFOLD_NODE),
        exitNode = ENTRY_NODE,
        mountainNode = "su_lanovka",
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("km_cesta1", "km_cesta2", "km_cesta3"),
        parentBiome = "ROOT_SIGNAL",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(130, 506, 20), "km_kotva" to Triple(46, 508, 10), N.TAPE_NODE to Triple(62, 418, 16),
            N.SHRINE_NODE to Triple(54, 312, 16), "km_nadrze" to Triple(142, 256, 18), N.VALVE_NODE to Triple(148, 212, 10),
            N.SCAFFOLD_NODE to Triple(132, 104, 26), "km_obrys" to Triple(56, 114, 14)
        )
    )

    val TEXTS = mapOf(
        "km_kotva" to "⚓ Kotva druhého lana. Lano není přetržené, je přeseknuté – čistě, sekerou. Konec visí přes hranu do tmy.\n" +
            "Někdo nechtěl, aby sem dojela ještě jedna gondola.",
        "km_nadrze" to "🛢️ Tlakové nádrže s pěnou. Operace Pečeť: zalít ránu v kmeni, aby z ní přestala téct mlha. Manometry ukazují nulu.\n" +
            "Kolem zaschlé krusty pěny – žluté, tvrdé, voní připáleným cukrem. Kořen je pod nimi zvlněný, jako by se jim vyhýbal.",
        "km_obrys" to "🌳 Pata kmene. V kůře je vrostlý obrys člověka: ruce podél těla, hlava mírně zakloněná, jako by usnul vestoje.\n" +
            "Kůra je na dotek teplá. Na rameni kus olivové látky se žlutou nášivkou – písmena kůra skoro přerostla: …RIN.",
        N.SCAFFOLD_NODE to "🏗️ Lešení Řádu přišroubované přímo do kmene. Šrouby kůra obrostla, levé sloupky už jsou napůl uvnitř.\n" +
            "Cedule PEČEŤ. Nahoře nic – jen díra v kůře, kam se lila pěna. Je prázdná a zevnitř hladká.",
        N.SHRINE_NODE to "✨ Svatyňka Lumenců. Kruh kamenů se znaky, uprostřed trn z kořene a na něm nabodnutá drobná bytost s křidélky. " +
            "Svítí – pořád, i když je mrtvá. Nebo spí.\nDo kamenů je dokola vyryté totéž. Řád vedle zapíchl cedulku NESAHAT a pod ní úřední překlad: AŤ NIKDO NENÍ SÁM."
    )
    val ACTION_NODES = TEXTS.keys + N.TAPE_NODE + N.VALVE_NODE + ENTRY_NODE
}

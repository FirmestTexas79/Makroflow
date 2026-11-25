package cz.uhk.macroflow.pokemon.cave

/**
 * Zóna 3 (Akt III – Hlubina), lokace 5 z 8: SVAH BEZEJMENNÝCH (docs/adr/0106). Vysoká mapa: hráč sestupuje po levém okraji svahu,
 * zbytek je výhled přes údolí na protější kopec, kde se k lampě na vrcholu plahočí tisíce těl. Svah pod Táborem, kam sestupovaly
 * výpravy 1–7. Pohlcení 5/8: z Řádu jen trosky (stativ, kolíky, tabule, vodočetná lať), po svahu leží členové výprav –
 * vyhublí, všichni hlavou vzhůru. Devět štítků V7 (Keshova výprava). Kořeny pijí vodu ze skály.
 * Zásada: hrůza je v tom, že zapomněli jíst – hra hladovění nikdy neodměňuje.
 * Uzly kopírují tools/mapgen/gen_nameless.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.ROOT_NAMELESS.
 */
object RootNameless {
    const val ENTRY_NODE = "sb_vstup"
    const val VISITED_KEY = "zona3_svah_bezejmennych"

    val MAP = CaveMap(
        artW = 176, artH = 560,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 24, 26), CaveNode("sb_kolik", 28, 92), CaveNode("sb_teodolit", 46, 150), CaveNode("sb_rada", 26, 204),
            CaveNode("sb_tabule", 52, 262), CaveNode("sb_cesta", 26, 304), CaveNode("sb_voda", 48, 390), CaveNode("sb_v7", 46, 452),
            CaveNode("sb_vyhled", 70, 488), CaveNode("sb_cesta2", 24, 506), CaveNode("sb_chram", 16, 546), CaveNode("sb_les", 54, 548)
        ),
        edges = listOf(ENTRY_NODE to "sb_kolik", "sb_kolik" to "sb_teodolit", "sb_kolik" to "sb_rada", "sb_teodolit" to "sb_rada",
            "sb_rada" to "sb_tabule", "sb_rada" to "sb_cesta", "sb_tabule" to "sb_cesta", "sb_cesta" to "sb_voda", "sb_voda" to "sb_v7",
            "sb_cesta" to "sb_cesta2", "sb_v7" to "sb_vyhled", "sb_v7" to "sb_cesta2", "sb_vyhled" to "sb_cesta2",
            "sb_cesta2" to "sb_chram", "sb_cesta2" to "sb_les"),
        exitNode = ENTRY_NODE,
        mountainNode = RootSlopeCamp.DOWN_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("sb_kolik", "sb_cesta", "sb_cesta2"),
        parentBiome = "ROOT_SLOPECAMP",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(24, 20, 12), "sb_teodolit" to Triple(58, 136, 12), "sb_rada" to Triple(20, 222, 22),
            "sb_tabule" to Triple(48, 236, 22), "sb_cesta" to Triple(38, 302, 24), "sb_voda" to Triple(62, 352, 16),
            "sb_v7" to Triple(44, 428, 30), "sb_vyhled" to Triple(122, 300, 56), "sb_chram" to Triple(14, 546, 12),
            "sb_les" to Triple(60, 548, 12)
        )
    )

    val TEXTS = mapOf(
        ENTRY_NODE to "⬆️ Řada kolíků vede nahoru zpátky k Táboru na svahu.",
        "sb_kolik" to "📏 Měřicí kolíky výprav. Lano mezi nimi je na několika místech přetržené. Na každém kolíku štítek s datem.\n" +
            "Čím níž, tím starší datum. Jako by svah rostl nahoru.",
        "sb_teodolit" to "🔭 Zlámaný stativ měřiče. Přístroj leží převrácený vedle, okulár rozbitý. Baterka Řádu v suti pořád svítí.\n" +
            "V zápisníku poslední řádek: „Sklon svahu 34°. Ne. 35°. Svah se zvedá. Nebo klesáme my.“",
        "sb_rada" to "Leží tu, všude kolem stezky. V kombinézách Řádu, ve vlněných kabátech, v bundách, s batohy i bez – a všichni hlavou vzhůru. " +
            "Plazí se, natahují ruku, jeden se schoulil na bok, jiný se zabalil do deky, další sedí s hlavou na kolenou.\n" +
            "Jsou vyhublí, látka na nich visí. V kapsách mají nedotčené příděly – zapomněli jíst. Na kotnících papírové štítky s číslem výpravy. Jména nikde.",
        "sb_tabule" to "📋 Sčítací tabule Řádu na dvou sloupcích. Čárky po pěti – stovky. Pak se počítat přestalo: poslední řádky jsou přeškrtané.",
        "sb_cesta" to "Přes celou skalní desku na stezce rudou barvou Řádu, kterou se značí bedny: NEPOČÍTEJTE JE.\nStojíš přímo na nápisu.",
        "sb_v7" to "Devět vedle sebe, ruce natažené k sobě, jako by se drželi. Na štítcích V7.\n" +
            "Jeden má na bradě jizvu. Další má pod kapucí pramen zrzavých vlasů. Mezi pátým a šestým je volné místo – tam, kde šel ten s lucernou.\n" +
            "Nahoře na útesu pořád někdo svítí.",
        "sb_voda" to "💧 Vodočetná lať Řádu. Tůňka je skoro vypitá – mokrá čára na kamenech je o dlaň výš než hladina.\n" +
            "Pramínky tečou dolů svahem a končí v kořenech. Kořeny pijí. Na lati tužkou: „Hladina klesá. Kořeny rostou.“",
        "sb_vyhled" to "Přes údolí je druhý svah. Leží na něm stovky – možná tisíce. Všichni lezou nahoru, ve stopách jeden za druhým, " +
            "k sodíkové lampě na vrcholu, kterou někdo z Řádu nechal rozsvícenou, aby trefili zpátky.\n" +
            "Čím výš, tím jsou menší. U lampy už jsou jen stíny proti světlu. Nahoru nedošel nikdo.\nA ty jdeš dolů.",
        "sb_chram" to "⬇️ Schody Lumenců vytesané do skály vedou dolů do mlhy – k Chrámu Lumenců. Vedle nich se do země noří žebra dlouhého domu.\nZATÍM NEPŘÍSTUPNÉ.",
        "sb_les" to "⬇️ Kolíky Řádu mizí v zelené mlze – dole je Les pod korunou. Dál už je nikdo nezatloukal.\nZATÍM NEPŘÍSTUPNÉ."
    )
    val ACTION_NODES = TEXTS.keys
}

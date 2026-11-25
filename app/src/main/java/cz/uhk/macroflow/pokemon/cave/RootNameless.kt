package cz.uhk.macroflow.pokemon.cave

/**
 * Zóna 3 (Akt III – Hlubina), lokace 5 z 8: SVAH BEZEJMENNÝCH (docs/adr/0106). Suťový svah pod Táborem, kam sestupovaly
 * výpravy 1–7. Pohlcení 5/8: z Řádu jen trosky (stativ, kolíky, tabule, vodočetná lať), po svahu leží členové výprav –
 * vyhublí, všichni hlavou vzhůru. Devět štítků V7 (Keshova výprava). Kořeny pijí vodu ze skály.
 * Zásada: hrůza je v tom, že zapomněli jíst – hra hladovění nikdy neodměňuje.
 * Uzly kopírují tools/mapgen/gen_nameless.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.ROOT_NAMELESS.
 */
object RootNameless {
    const val ENTRY_NODE = "sb_vstup"
    const val VISITED_KEY = "zona3_svah_bezejmennych"

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 88, 64), CaveNode("sb_ruina", 140, 66), CaveNode("sb_kolik", 50, 110), CaveNode("sb_teodolit", 104, 138),
            CaveNode("sb_rada", 140, 172), CaveNode("sb_tabule", 62, 194), CaveNode("sb_cesta", 96, 222), CaveNode("sb_v7", 34, 250),
            CaveNode("sb_voda", 130, 248), CaveNode("sb_cesta2", 88, 286), CaveNode("sb_zebra", 150, 292), CaveNode("sb_chram", 22, 336),
            CaveNode("sb_les", 152, 338)
        ),
        edges = listOf(ENTRY_NODE to "sb_ruina", ENTRY_NODE to "sb_kolik", "sb_kolik" to "sb_teodolit", "sb_teodolit" to "sb_rada",
            "sb_teodolit" to "sb_tabule", "sb_tabule" to "sb_cesta", "sb_rada" to "sb_cesta", "sb_cesta" to "sb_v7", "sb_cesta" to "sb_voda",
            "sb_cesta" to "sb_cesta2", "sb_voda" to "sb_zebra", "sb_cesta2" to "sb_chram", "sb_cesta2" to "sb_les", "sb_zebra" to "sb_les",
            "sb_v7" to "sb_chram"),
        exitNode = ENTRY_NODE,
        mountainNode = RootSlopeCamp.DOWN_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("sb_kolik", "sb_cesta", "sb_cesta2"),
        parentBiome = "ROOT_SLOPECAMP",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(88, 58, 12), "sb_ruina" to Triple(136, 30, 24), "sb_teodolit" to Triple(114, 120, 12),
            "sb_rada" to Triple(150, 140, 22), "sb_tabule" to Triple(70, 160, 22), "sb_v7" to Triple(44, 258, 26),
            "sb_voda" to Triple(136, 230, 16), "sb_zebra" to Triple(148, 304, 22), "sb_chram" to Triple(22, 330, 16),
            "sb_les" to Triple(152, 332, 16)
        )
    )

    val TEXTS = mapOf(
        ENTRY_NODE to "⬆️ Řada kolíků vede nahoru zpátky k Táboru na svahu.",
        "sb_ruina" to "🏛️ Fasáda Lumenců. Skála ji polyká – spodek oblouku už je pod sutí a celá se naklání dovnitř, jako by ji něco táhlo za nohy.\n" +
            "Na římse pořád svítí znaky: hvězdy a vlny. Nad obloukem obrácená koruna.",
        "sb_kolik" to "📏 Měřicí kolíky výprav. Lano mezi nimi je na několika místech přetržené. Na každém kolíku štítek s datem.\n" +
            "Čím níž, tím starší datum. Jako by svah rostl nahoru.",
        "sb_teodolit" to "🔭 Zlámaný stativ měřiče. Přístroj leží převrácený vedle, okulár rozbitý. Baterka Řádu v suti pořád svítí.\n" +
            "V zápisníku poslední řádek: „Sklon svahu 34°. Ne. 35°. Svah se zvedá. Nebo klesáme my.“",
        "sb_rada" to "Leží tu. Desítky. V kombinézách Řádu, tváří dolů a všichni hlavou vzhůru – jako by lezli nahoru, pryč od něčeho dole.\n" +
            "Jsou vyhublí, látka na nich visí. V kapsách mají nedotčené příděly – zapomněli jíst. Na kotnících papírové štítky s číslem výpravy. Jména nikde.",
        "sb_tabule" to "📋 Sčítací tabule Řádu, nakloněná, jedna noha v suti. Čárky po pěti – stovky. Poslední řádky jsou přeškrtané.\n" +
            "Na skále nad svahem někdo stejnou rudou barvou, jakou Řád značí bedny, nastříkal: NEPOČÍTEJTE JE.",
        "sb_v7" to "Devět vedle sebe, ruce natažené k sobě, jako by se drželi. Na štítcích V7.\n" +
            "Jeden má na bradě jizvu. Další má pod kapucí pramen zrzavých vlasů. Mezi pátým a šestým je volné místo – tam, kde šel ten s lucernou.\n" +
            "Nahoře na útesu pořád někdo svítí.",
        "sb_voda" to "💧 Vodočetná lať Řádu. Tůňka je skoro vypitá – mokrá čára na kamenech je o dlaň výš než hladina.\n" +
            "Pramínky ze skály tečou dolů svahem a končí v kořenech. Kořeny pijí. Na lati tužkou: „Hladina klesá. Kořeny rostou.“",
        "sb_zebra" to "🏛️ Žebra dlouhého domu Lumenců. Zadní oblouky už jsou skoro celé v zemi. Kořeny je stahují dolů pomalu a jemně, jako když se někdo přikrývá.",
        "sb_chram" to "⬇️ Schody Lumenců vytesané do skály vedou dolů do mlhy – k Chrámu Lumenců.\nZATÍM NEPŘÍSTUPNÉ.",
        "sb_les" to "⬇️ Kolíky Řádu mizí v zelené mlze – dole je Les pod korunou. Dál už je nikdo nezatloukal.\nZATÍM NEPŘÍSTUPNÉ."
    )
    val ACTION_NODES = TEXTS.keys
}

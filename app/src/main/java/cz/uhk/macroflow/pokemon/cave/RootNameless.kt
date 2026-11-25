package cz.uhk.macroflow.pokemon.cave

/**
 * Zóna 3 (Akt III – Hlubina), lokace 5 z 8: SVAH BEZEJMENNÝCH (docs/adr/0106). Vysoká mapa: široký svah (80 %), vpravo úzký výhled
 * na Spící a kopec pod korunou, odkud se těla rozbíhají pryč od stromu; vlevo pukliny a jeskyně, kudy chtěli utéct. Svah pod Táborem, kam sestupovaly
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
            CaveNode(ENTRY_NODE, 62, 24), CaveNode("sb_kolik", 94, 78), CaveNode("sb_pukl1", 44, 100), CaveNode("sb_teodolit", 114, 136),
            CaveNode("sb_rada", 70, 186), CaveNode("sb_pukl2", 44, 238), CaveNode("sb_tabule", 112, 250), CaveNode("sb_cesta", 72, 300),
            CaveNode("sb_voda", 112, 372), CaveNode("sb_pukl3", 44, 386), CaveNode("sb_v7", 80, 420), CaveNode("sb_vyhled", 130, 470),
            CaveNode("sb_cesta2", 70, 492), CaveNode("sb_chram", 44, 546), CaveNode("sb_les", 110, 548)
        ),
        edges = listOf(ENTRY_NODE to "sb_kolik", ENTRY_NODE to "sb_pukl1", "sb_kolik" to "sb_teodolit", "sb_kolik" to "sb_rada",
            "sb_pukl1" to "sb_rada", "sb_teodolit" to "sb_rada", "sb_rada" to "sb_pukl2", "sb_rada" to "sb_tabule", "sb_rada" to "sb_cesta",
            "sb_tabule" to "sb_cesta", "sb_pukl2" to "sb_cesta", "sb_cesta" to "sb_voda", "sb_cesta" to "sb_pukl3", "sb_cesta" to "sb_v7",
            "sb_voda" to "sb_v7", "sb_pukl3" to "sb_v7", "sb_v7" to "sb_vyhled", "sb_v7" to "sb_cesta2", "sb_vyhled" to "sb_cesta2",
            "sb_cesta2" to "sb_chram", "sb_cesta2" to "sb_les"),
        exitNode = ENTRY_NODE,
        mountainNode = RootSlopeCamp.DOWN_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("sb_kolik", "sb_cesta", "sb_cesta2"),
        parentBiome = "ROOT_SLOPECAMP",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(62, 18, 12), "sb_pukl1" to Triple(16, 100, 16), "sb_teodolit" to Triple(128, 120, 12),
            "sb_rada" to Triple(70, 170, 30), "sb_pukl2" to Triple(16, 238, 18), "sb_tabule" to Triple(116, 222, 22),
            "sb_cesta" to Triple(72, 300, 30), "sb_voda" to Triple(118, 340, 16), "sb_pukl3" to Triple(16, 386, 18),
            "sb_v7" to Triple(86, 412, 40), "sb_vyhled" to Triple(160, 300, 20), "sb_chram" to Triple(44, 546, 12),
            "sb_les" to Triple(116, 548, 12)
        )
    )

    val TEXTS = mapOf(
        ENTRY_NODE to "⬆️ Řada kolíků vede nahoru zpátky k Táboru na svahu.",
        "sb_kolik" to "📏 Měřicí kolíky výprav. Lano mezi nimi je na několika místech přetržené. Na každém kolíku štítek s datem.\n" +
            "Čím níž, tím starší datum. Jako by svah rostl nahoru.",
        "sb_teodolit" to "🔭 Zlámaný stativ měřiče. Přístroj leží převrácený vedle, okulár rozbitý. Baterka Řádu v suti pořád svítí.\n" +
            "V zápisníku poslední řádek: „Sklon svahu 34°. Ne. 35°. Svah se zvedá. Nebo klesáme my.“",
        "sb_rada" to "Leží tu, po celém svahu. V kombinézách Řádu, ve vlněných kabátech, v bundách, s batohy i bez. Plazí se, natahují ruku, " +
            "jeden se schoulil na bok, jiný se zabalil do deky, další sedí s hlavou na kolenou.\n" +
            "A všichni míří pryč od stromu – nahoru a ke stěně. Jsou vyhublí, látka na nich visí. V kapsách nedotčené příděly – zapomněli jíst. " +
            "Na kotnících papírové štítky s číslem výpravy. Jména nikde.",
        "sb_pukl1" to "🕳️ Puklina ve stěně dutiny. Výtah nebyl jediná cesta ven – tudy chtěli utéct. Před ústím leží hlavou dovnitř.\n" +
            "Uvnitř je tma a průvan. Kořeny prorostly ústím a zavřely ho jako prsty.",
        "sb_pukl2" to "🕳️ Jeskyně ve stěně, dost široká pro člověka. Na skále vedle nehty vyškrábáno VEN.\n" +
            "Kořen se do ní nasoukal celý. Z hloubky jde teplo, jako dech.",
        "sb_pukl3" to "🕳️ Další ústí. Někdo do něj zatloukl kolík Řádu a uvázal lano, aby se ostatní měli čeho držet. " +
            "Lano mizí ve tmě a je napnuté. Nikdo za něj netáhne.\nVedle zase VEN.",
        "sb_tabule" to "📋 Sčítací tabule Řádu na dvou sloupcích. Čárky po pěti – stovky. Pak se počítat přestalo: poslední řádky jsou přeškrtané.",
        "sb_cesta" to "Přes skalní desku na stezce, napsáno prstem, krví: NEPOČÍTEJTE JE.\nKe konci se písmena třesou a stékají. Vedle rozmazaný otisk dlaně. Stojíš přímo na nápisu.",
        "sb_v7" to "Devět vedle sebe, ruce natažené k sobě, jako by se drželi. Na štítcích V7.\n" +
            "Jeden má na bradě jizvu. Další má pod kapucí pramen zrzavých vlasů. Mezi pátým a šestým je volné místo – tam, kde šel ten s lucernou.\n" +
            "Nahoře na útesu pořád někdo svítí.",
        "sb_voda" to "💧 Vodočetná lať Řádu. Tůňka je skoro vypitá – mokrá čára na kamenech je o dlaň výš než hladina.\n" +
            "Pramínky tečou dolů svahem a končí v kořenech. Kořeny pijí. Na lati tužkou: „Hladina klesá. Kořeny rostou.“",
        "sb_vyhled" to "🌳 Za srázem, blízko, visí Spící – kmen do prázdna, rána, ze které teče zelená mlha, kousek nad ní kov kopí.\n" +
            "Pod korunou je v mlze druhý svah. I tam leží. Rozbíhají se od stromu na všechny strany jako kruhy na vodě – " +
            "ke stěnám, k puklinám, nahoru. Nikdo z nich nedošel.\nA ty jdeš dolů.",
        "sb_chram" to "⬇️ Schody Lumenců vytesané do skály vedou dolů do mlhy – k Chrámu Lumenců. Vedle nich se do země noří žebra dlouhého domu.\nZATÍM NEPŘÍSTUPNÉ.",
        "sb_les" to "⬇️ Kolíky Řádu mizí v zelené mlze – dole je Les pod korunou. Dál už je nikdo nezatloukal.\nZATÍM NEPŘÍSTUPNÉ."
    )
    val ACTION_NODES = TEXTS.keys
}

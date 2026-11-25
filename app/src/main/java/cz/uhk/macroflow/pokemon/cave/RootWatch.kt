package cz.uhk.macroflow.pokemon.cave

/**
 * Zóna 3 (Akt III – Hlubina), lokace 2 z 8: POZOROVATELNA B (docs/adr/0106), pohlcení 2/8. Bunkr Řádu zapuštěný do
 * skály a betonová terasa nad propastí se zábradlím; sestup ze Stanice 8 po schodech Lumenců. Světlomet v půlkruhu
 * pytlů, dálkoměr, agregát, stojan plamenometů, nástěnný graf KONTROLA, polní telefon. Kořen se valí po pravém konci
 * bunkru přes terasu a ohýbá zábradlí. Setkání DUCH a ZEMĚ.
 * Uzly kopírují tools/mapgen/gen_watch_b.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.ROOT_WATCH.
 */
object RootWatch {
    const val ENTRY_NODE = "pb_vstup"
    const val RAVINE_NODE = "pb_k_rokli"
    const val DOWN_NODE = "pb_dolu"
    const val VISITED_KEY = "zona3_pozorovatelna_b"

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 30, 128), CaveNode("pb_dvere", 100, 132), CaveNode("pb_telefon", 128, 134),
            CaveNode("pb_tabule", 66, 132), CaveNode("pb_stred", 100, 196), CaveNode("pb_generator", 40, 182),
            CaveNode("pb_svetlomet", 74, 222), CaveNode("pb_hlidka", 136, 156), CaveNode("pb_dalkomer", 100, 290),
            CaveNode(RAVINE_NODE, 168, 300), CaveNode(DOWN_NODE, 24, 340)
        ),
        edges = listOf(ENTRY_NODE to "pb_tabule", "pb_tabule" to "pb_dvere", "pb_dvere" to "pb_telefon", "pb_telefon" to "pb_hlidka",
            "pb_tabule" to "pb_stred", ENTRY_NODE to "pb_generator", "pb_generator" to "pb_stred", "pb_stred" to "pb_svetlomet",
            "pb_stred" to "pb_dalkomer", "pb_svetlomet" to RAVINE_NODE, "pb_dalkomer" to DOWN_NODE),
        exitNode = ENTRY_NODE,
        mountainNode = RootCamp.DOWN_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("pb_stred", "pb_generator", "pb_svetlomet", "pb_dalkomer"),
        parentBiome = "ROOT_CAMP",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(16, 60, 18), "pb_stred" to Triple(100, 40, 30), "pb_dvere" to Triple(100, 100, 14),
            "pb_telefon" to Triple(130, 100, 9), "pb_tabule" to Triple(76, 96, 22), "pb_generator" to Triple(38, 150, 22),
            "pb_svetlomet" to Triple(74, 232, 18), "pb_hlidka" to Triple(140, 132, 12), "pb_dalkomer" to Triple(100, 292, 14),
            RAVINE_NODE to Triple(150, 292, 16), DOWN_NODE to Triple(44, 296, 16)
        )
    )

    val TEXTS = mapOf(
        ENTRY_NODE to "🪜 Schody vytesané do skály. Řád je nestavěl – jsou ohlazené tisíci nohou, které tu chodily dávno před ním.\nNahoru vedou zpátky ke Stanici 8.",
        "pb_stred" to "🏗️ Bunkr POZOROVATELNA B zapuštěný do skály. Cedule přišroubované, nástřik obnovovaný, beton zametený – jako by tu pořád někdo sloužil.\nNikdo tu není.",
        "pb_dvere" to "🚪 Ocelové dveře s pečetí vah, zamčené. Když přiložíš ucho, je zevnitř slyšet tiché, pravidelné ťukání. Jednou za hodinu. Jako tep.",
        "pb_telefon" to "☎️ Polní telefon. Ve sluchátku šum… a pak Sept, velmi zdaleka: „Pozorovatelno B, hlaste stav kořenů.“\nNikdo neodpovídá. Sept to zopakuje. A znovu.",
        "pb_tabule" to "📈 KONTROLA – graf vzdálenosti kořenů od bunkru. Křivka klesá den po dni až k nule. Poslední bod je zakreslený červeně a přes něj tužkou: „Zítra už neměřit.“",
        "pb_generator" to "⚙️ Bouda ZDROJ. Agregát pořád běží – nikdo ho nevypnul a nafta pořád nedošla. Kabel vede k zamčeným dveřím bunkru.",
        "pb_svetlomet" to "🔦 Velký světlomet na točně v půlkruhu z pytlů, natočený dolů ke kmeni. Čočka je prasklá, přesto občas sám zaprská a na chvíli posvítí do tmy.\nKdo ho zapíná, se neví. Na kličce jsou drobné otisky – jako od tlapek.",
        "pb_hlidka" to "🔥 Stojan ohňové hlídky: dva plamenomety, nádrže plné. Helma leží opodál, jako by ji tu někdo upustil v půlce kroku.\nTady už nikdo nepálil. Kořeny vyhrály bez boje.",
        "pb_dalkomer" to "🔭 Dálkoměr na trojnožce míří přes zábradlí do tmy. Na stupnici ryska KMEN – 2 400 m. Pod ní tužkou 2 100 m. A ještě jedna: 1 900 m.\nKmen se nehýbe. Vzdálenost se přesto zkracuje.",
        RAVINE_NODE to "🌉 Průchod v zábradlí na ocelovou lávku podél skály – k Signálnímu útesu a horní stanici lanovky.",
        DOWN_NODE to "⬇️ Schody z terasy dolů, cedule DOLŮ: TÁBOR."
    )
    val ACTION_NODES = TEXTS.keys
}

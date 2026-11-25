package cz.uhk.macroflow.pokemon.cave

/**
 * Zóna 3 (Akt III – Hlubina), lokace 2 z 8: POZOROVATELNA B (docs/adr/0106), pohlcení 2/8. Nižší terasa na stěně
 * dutiny, sestup ze Stanice 8 po schodech Lumenců. Nakloněná věž B s kořeny v oknech, plot zmáčknutý kořenem,
 * bouda s generátorem, opuštěný stojan plamenometů, sloupek KONTROLA. Setkání DUCH a ZEMĚ.
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
            CaveNode(ENTRY_NODE, 36, 110), CaveNode("pb_brana", 66, 134), CaveNode("pb_stred", 84, 186),
            CaveNode("pb_generator", 42, 236), CaveNode("pb_vez", 122, 274), CaveNode("pb_hlidka", 64, 290),
            CaveNode("pb_kontrola", 98, 296), CaveNode(RAVINE_NODE, 168, 306), CaveNode(DOWN_NODE, 34, 342)
        ),
        edges = listOf(ENTRY_NODE to "pb_brana", "pb_brana" to "pb_stred", "pb_stred" to "pb_generator", "pb_stred" to "pb_vez",
            "pb_stred" to "pb_hlidka", "pb_generator" to "pb_hlidka", "pb_vez" to "pb_kontrola",
            "pb_kontrola" to RAVINE_NODE, "pb_hlidka" to DOWN_NODE),
        exitNode = ENTRY_NODE,
        mountainNode = RootCamp.DOWN_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("pb_stred", "pb_generator", "pb_vez", "pb_hlidka"),
        parentBiome = "ROOT_CAMP",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(22, 60, 18), "pb_brana" to Triple(140, 140, 12), "pb_generator" to Triple(40, 214, 22),
            "pb_vez" to Triple(134, 200, 24), "pb_hlidka" to Triple(52, 276, 14), "pb_kontrola" to Triple(96, 258, 18),
            RAVINE_NODE to Triple(162, 270, 16), DOWN_NODE to Triple(34, 336, 16), "pb_stred" to Triple(90, 40, 40)
        )
    )

    val TEXTS = mapOf(
        ENTRY_NODE to "🪜 Schody vytesané do skály. Řád je nestavěl – jsou ohlazené tisíci nohou, které tu chodily dávno před ním.\\nNahoru vedou zpátky ke Stanici 8.",
        "pb_stred" to "🧱 Na stěně nastříkáno POZOROVATELNA B · ŘÁD VÁHY. Barva je čerstvější než beton pod ní – někdo ji obnovoval, i když tu už nikdo nebyl.",
        "pb_brana" to "🚧 Plot z pletiva. Kořen ho zmáčkl k zemi jako stéblo; ceduli POZOR to nijak nevadilo.",
        "pb_generator" to "⚙️ Bouda ZDROJ. Agregát ještě běží – nikdo ho nevypnul a nafta pořád nedošla. Střechou prorostl kořen a teplo z motoru mu zjevně svědčí.",
        "pb_vez" to "🗼 Pozorovatelna B. Věž se naklání ke kmeni, jako by se chtěla podívat blíž. Okny kabiny prorůstají kořeny, po nohách šplhají nahoru.\\n" +
            "Reflektor je rozbitý. Přesto občas zaprská a na chvíli posvítí do tmy – kdo ho zapíná, se neví.",
        "pb_hlidka" to "🔥 Stojan ohňové hlídky. Dva plamenomety, nádrže plné. Helma leží na zemi, jako by ji tu někdo upustil v půlce kroku.\\nTady už nikdo nepálil. Kořeny vyhrály bez boje.",
        "pb_kontrola" to "📏 KONTROLA – tabulka čárek po dnech. Každá čárka je o kus níž než předchozí: kořen se každý den přiblížil. Poslední čárka je přímo u sloupku.\\nPod ní tužkou: „Zítra už neměřit.“",
        RAVINE_NODE to "🚡 Stožár lanovky. Lana vedou přes rokli do tmy, ke kmeni.\\nŘímsa za stožárem je zatím zavalená – Lanovka nad roklí: ZATÍM NEPŘÍSTUPNÉ.",
        DOWN_NODE to "⬇️ Cesta se stáčí dolů k Táboru na svahu. Zatím ji zavalují kořeny – ZATÍM NEPŘÍSTUPNÉ."
    )
    val ACTION_NODES = TEXTS.keys
}

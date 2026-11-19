package cz.uhk.macroflow.pokemon.cave

/**
 * Archiv okruhů – 1. patro Věže Řádu Váhy (Akt II, fáze Spis 212, bible příběhu). Noční archiv: zelené lampy,
 * měsíc v oknech, hodiny na 6:12, lehátko noční směny, které nikdo nepoužil, a na čtecím pultu Spis 212.
 * Uzly kopírují tools/mapgen/gen_archive.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.ARCHIVE.
 */
object TowerArchive {
    const val ELEVATOR_NODE = "archiv_vytah"
    const val FILE_NODE = "spis_212"

    val MAP = CaveMap(
        artW = 176, artH = 336,
        nodes = listOf(
            CaveNode(ELEVATOR_NODE, 88, 98), CaveNode("archiv_stred", 88, 206), CaveNode("archiv_katalog", 34, 138),
            CaveNode("archiv_vozik", 142, 136), CaveNode("archiv_regal_l", 40, 198), CaveNode("archiv_regal_p", 136, 198),
            CaveNode("archiv_studovna", 88, 266), CaveNode("archiv_globus", 146, 254), CaveNode(FILE_NODE, 84, 318),
            CaveNode("archiv_lehatko", 138, 272), CaveNode("archiv_regal_s", 36, 316), CaveNode("archiv_hodiny", 40, 98)
        ),
        edges = listOf(ELEVATOR_NODE to "archiv_stred", ELEVATOR_NODE to "archiv_hodiny", "archiv_stred" to "archiv_katalog",
            "archiv_stred" to "archiv_vozik", "archiv_stred" to "archiv_regal_l", "archiv_stred" to "archiv_regal_p",
            "archiv_stred" to "archiv_studovna", "archiv_studovna" to FILE_NODE, "archiv_studovna" to "archiv_lehatko",
            "archiv_studovna" to "archiv_regal_s", "archiv_studovna" to "archiv_globus"),
        exitNode = ELEVATOR_NODE,
        mountainNode = TowerLobby.ELEVATOR_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = emptySet(),
        parentBiome = "TOWER_LOBBY",
        isCave = false,
        tapAreas = mapOf(
            ELEVATOR_NODE to Triple(88, 60, 22), "archiv_hodiny" to Triple(42, 34, 10), "archiv_katalog" to Triple(34, 116, 16),
            "archiv_vozik" to Triple(142, 114, 14), "archiv_regal_l" to Triple(40, 162, 26), "archiv_regal_p" to Triple(136, 162, 26),
            "archiv_studovna" to Triple(88, 236, 28), "archiv_globus" to Triple(146, 226, 12), FILE_NODE to Triple(84, 284, 16),
            "archiv_lehatko" to Triple(138, 292, 22), "archiv_regal_s" to Triple(36, 286, 22)
        )
    )

    /** Texty míst (klepnutí na objekt nebo příchod k uzlu). */
    val TEXTS = mapOf(
        "archiv_hodiny" to "🕰️ Nástěnné hodiny. Ručičky stojí na 6:12, a přece se kyvadlo kývá. Tik. Tak. Čas tu běží, jen se nikam neposouvá.",
        "archiv_katalog" to "🗂️ Lístkový katalog, okruhy 190–212. Na každém lístku stejná kolonka: SPÁNEK – PRŮMĚR.\nČím novější okruh, tím menší číslo.",
        "archiv_vozik" to "🛒 Vozík se spisy, které čekají na zařazení. Navrchu krabice s nápisem OKRUH 212 – VRÁTIT DO ARCHIVU.\nJe prázdná.",
        "archiv_regal_l" to "📚 Regál OKRUHY 190–199. Spisy tlusté jako slovníky, na hřbetech jen čísla a razítko KALIBROVÁN.",
        "archiv_regal_p" to "📚 Regál OKRUHY 200–211. Čím dál, tím tenčí spisy. Na posledních razítko chybí.",
        "archiv_studovna" to "📖 Studovna. Dvě zelené lampy a otevřená kniha s tabulkami hodin a minut.\nNěkdo tu četl dlouho do noci – a odešel uprostřed řádku.",
        "archiv_globus" to "🌍 Glóbus. Na jednom místě je přilepený papírek a na něm tužkou: „Tady končí mapa.“\nTen rukopis znáš.",
        "archiv_lehatko" to "🛏️ Lehátko noční směny. Deka složená do ostrých hran, polštář bez důlku, na stolku budík a sklenice vody.\n" +
            "Nikdo na něm nikdy nespal. Cedulka: „Odpočinek personálu – zatím neschválen.“",
        "archiv_regal_s" to "📚 Nejnižší regál. Štítek OKRUH 212 tu je, spis chybí. U nohy regálu je opřený kožený tubus s mapou.",
        FILE_NODE to "📁 Na čtecím pultu leží Spis 212. Skoro všechny řádky jsou začerněné ███. Razítko: KALIBRACE NEDOKONČENA.\n" +
            "Pod ním tužkou, drobně: „Nespí.“"
    )

    val ACTION_NODES = TEXTS.keys + ELEVATOR_NODE

    const val ELEVATOR_UP = "Patro 1 · Archiv okruhů"
    const val ELEVATOR_READY = "Ručička se chvěje mezi nulou a jedničkou. Patro 1: schváleno. Výjimečně."
}

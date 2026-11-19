package cz.uhk.macroflow.pokemon.cave

/**
 * Vestibul Věže Řádu Váhy v Ozvěně (docs/adr/0103): pult archiváře Septa, výtah s ukazatelem pater (7 nahoru,
 * pod nulou dvě zaslepená), čekárna s tabulí SUBJEKT 213, kartotéky a portréty „kalibrovaných“ bez tváří.
 * Uzly kopírují tools/mapgen/gen_tower.py – ZDROJ PRAVDY je v obou místech.
 */
object TowerLobby {
    const val ENTRY_NODE = "vychod"
    const val ELEVATOR_NODE = "vytah_veze"
    const val SEPT_NODE = "sept"
    const val WAITING_NODE = "cekarna"
    const val FILES_NODE = "kartoteka"
    const val FILES2_NODE = "kartoteka_p"
    const val PORTRAITS_NODE = "portrety"
    /** Hráč poprvé prošel dveřmi (po cutscéně) – příznak příběhu, splní fázi „Pozvánka“. */
    const val VISITED_KEY = "tower_entered"
    const val SEPT_NAME = "Archivář Sept"

    val MAP = CaveMap(
        artW = 176, artH = 320,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 88, 312), CaveNode("stred", 88, 236), CaveNode(ELEVATOR_NODE, 88, 98),
            CaveNode(SEPT_NODE, 110, 210), CaveNode(WAITING_NODE, 44, 252), CaveNode(FILES_NODE, 54, 304),
            CaveNode(PORTRAITS_NODE, 54, 100), CaveNode(FILES2_NODE, 122, 304)
        ),
        edges = listOf(ENTRY_NODE to "stred", "stred" to ELEVATOR_NODE, "stred" to SEPT_NODE, "stred" to WAITING_NODE,
            "stred" to FILES_NODE, ELEVATOR_NODE to PORTRAITS_NODE, "stred" to FILES2_NODE),
        exitNode = ENTRY_NODE,
        mountainNode = OzvenaCity.TOWER_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = emptySet(),
        parentBiome = "OZVENA",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(88, 314, 14), ELEVATOR_NODE to Triple(88, 60, 24), SEPT_NODE to Triple(134, 172, 18),
            WAITING_NODE to Triple(30, 168, 18), FILES_NODE to Triple(36, 280, 22), FILES2_NODE to Triple(140, 280, 22),
            PORTRAITS_NODE to Triple(54, 38, 12)
        )
    )

    val ACTION_NODES = setOf(ENTRY_NODE, ELEVATOR_NODE, SEPT_NODE, WAITING_NODE, FILES_NODE, FILES2_NODE, PORTRAITS_NODE)

    const val WAITING_TEXT = "🪑 Čekárna. Na tabuli svítí červeně SUBJEKT 213. Hodiny nad ní stojí na 6:12 – stejně jako hodiny hodináře Oswina.\n" +
        "Automat ti vydá lístek s číslem 214. Někdo další už je na řadě."
    const val FILES_TEXT = "🗄️ Kartotéka. Štítky na šuplících: OKRUH 198 – OKRUH 212. Šuplík 213 je pootevřený a prázdný. Zatím."
    const val FILES2_TEXT = "🗄️ Druhá kartotéka nemá jména, jen čísla pater: 1 až 7. Pod nimi dva šuplíky bez úchytů a bez štítků."
    const val PORTRAITS_TEXT = "🖼️ Portréty „kalibrovaných“. Kabáty, límce, ruce – a místo tváří hladká bledá plocha. Pod každým jen číslo okruhu."
    const val PORTRAITS_INSIGHT = "\nPod jedním z čísel je přeškrtnuté jméno. Ten rukopis znáš – stejný je na zdi u Vessy."
}

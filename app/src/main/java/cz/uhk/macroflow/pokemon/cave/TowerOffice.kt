package cz.uhk.macroflow.pokemon.cave

/**
 * Kancelář Řádu – 11. patro, vrchol Věže Řádu Váhy: observatoř. Nikdo tu nesedí, a přesto se tu pracuje. Štěrbinou
 * kopule je vidět nebe, ve kterém chybí jedna hvězda. Bez setkání – příběhové patro (později ho spálí Drakirra,
 * Objekt 019). Ukazatel výtahu tu stojí za koncem stupnice.
 * Uzly kopírují tools/mapgen/gen_office.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.OFFICE.
 */
object TowerOffice {
    const val ELEVATOR_NODE = "kanc_vytah"

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ELEVATOR_NODE, 88, 104), CaveNode("kanc_stred", 88, 176), CaveNode("kanc_mapa", 144, 106),
            CaveNode("kanc_dalekohled", 52, 200), CaveNode("kanc_orloj", 128, 200), CaveNode("kanc_globus", 126, 262),
            CaveNode("kanc_kyvadlo", 88, 270), CaveNode("kanc_vahy", 34, 330), CaveNode("kanc_stul", 84, 330)
        ),
        edges = listOf(ELEVATOR_NODE to "kanc_stred", ELEVATOR_NODE to "kanc_mapa", "kanc_stred" to "kanc_dalekohled",
            "kanc_stred" to "kanc_orloj", "kanc_stred" to "kanc_kyvadlo", "kanc_kyvadlo" to "kanc_vahy",
            "kanc_kyvadlo" to "kanc_stul", "kanc_kyvadlo" to "kanc_globus"),
        exitNode = ELEVATOR_NODE,
        mountainNode = TowerLobby.ELEVATOR_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = emptySet(),
        parentBiome = "TOWER_LOBBY",
        isCave = false,
        tapAreas = mapOf(
            ELEVATOR_NODE to Triple(88, 66, 22), "kanc_mapa" to Triple(144, 36, 20), "kanc_dalekohled" to Triple(38, 156, 22),
            "kanc_orloj" to Triple(142, 166, 18), "kanc_globus" to Triple(150, 250, 14), "kanc_kyvadlo" to Triple(88, 236, 26),
            "kanc_vahy" to Triple(34, 292, 18), "kanc_stul" to Triple(128, 306, 26)
        )
    )

    val TEXTS = mapOf(
        "kanc_dalekohled" to "🔭 Dalekohled míří štěrbinou kopule do místa, kde na nebi chybí hvězda. Na okuláru je přilepená cedulka: SLEDOVAT. NEPROBOUZET.",
        "kanc_orloj" to "🪐 Orloj se točí sám, bez klíčku. Kolem zlatého slunce obíhá sedm koulí. A jedna černá, která jde opačně.",
        "kanc_kyvadlo" to "🕰️ Kyvadlo visí ze tmy pod kopulí a opisuje mosazný kruh s čísly okruhů 198–213. Rysku 213 někdo obtáhl červeně.\nKyvadlo se k ní pokaždé vrátí o kousek blíž.",
        "kanc_vahy" to "⚖️ Velké váhy, znak Řádu. Na jedné misce je hrst popela, na druhé modrý krystal, který tiše svítí. Popel je těžší.",
        "kanc_stul" to "🖋️ Stůl Řádu. Křeslo je prázdné, lampa svítí. V knize REGISTR NÁDOB se brk sám zvedne a dopíše řádek:\nSUBJEKT 213 – STAV: PŘIPRAVOVÁN. ODPOVĚDNÝ: ███",
        "kanc_mapa" to "🗺️ Mapa Makrosvěta. Připínáčky STANICE 0 až 7 a od každého vede rudá nit dolů přes okraj rámu, někam pod svět.\nStanice 7 je tvoje město.",
        "kanc_globus" to "🌍 Glóbus, stejný jako Hunfreyho v archivu. Jen papírek, který tam měl přilepený, je tady strhnutý. Zbyl po něm kousek lepidla."
    )

    val ACTION_NODES = TEXTS.keys + ELEVATOR_NODE
}

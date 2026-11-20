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
            CaveNode(ELEVATOR_NODE, 88, 104), CaveNode("kanc_stred", 88, 186), CaveNode("kanc_mapa", 146, 106),
            CaveNode("kanc_dalekohled", 46, 206), CaveNode("kanc_orloj", 140, 190), CaveNode("kanc_globus", 118, 214),
            CaveNode("kanc_kyvadlo", 88, 262), CaveNode("kanc_katalog", 30, 274), CaveNode("kanc_pult", 148, 260),
            CaveNode("kanc_stul", 60, 258), CaveNode("kanc_vahy", 124, 262)
        ),
        edges = listOf(ELEVATOR_NODE to "kanc_stred", ELEVATOR_NODE to "kanc_mapa", "kanc_stred" to "kanc_dalekohled",
            "kanc_stred" to "kanc_orloj", "kanc_stred" to "kanc_globus", "kanc_stred" to "kanc_kyvadlo",
            "kanc_kyvadlo" to "kanc_katalog", "kanc_kyvadlo" to "kanc_stul", "kanc_kyvadlo" to "kanc_vahy",
            "kanc_vahy" to "kanc_pult"),
        exitNode = ELEVATOR_NODE,
        mountainNode = TowerLobby.ELEVATOR_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = emptySet(),
        parentBiome = "TOWER_LOBBY",
        isCave = false,
        tapAreas = mapOf(
            ELEVATOR_NODE to Triple(88, 66, 22), "kanc_mapa" to Triple(145, 32, 20), "kanc_dalekohled" to Triple(40, 150, 28),
            "kanc_orloj" to Triple(140, 146, 22), "kanc_globus" to Triple(118, 186, 12), "kanc_kyvadlo" to Triple(88, 232, 26),
            "kanc_katalog" to Triple(26, 244, 16), "kanc_pult" to Triple(150, 228, 14), "kanc_stul" to Triple(56, 300, 30),
            "kanc_vahy" to Triple(136, 296, 22)
        )
    )

    val TEXTS = mapOf(
        "kanc_dalekohled" to "🔭 Velký dalekohled na pódiu míří oknem do místa, kde v souhvězdí chybí hvězda. Na okuláru je přilepená cedulka: SLEDOVAT. NEPROBOUZET.",
        "kanc_orloj" to "🪐 Orloj se točí sám, bez klíčku. Kolem zlatého slunce obíhá sedm koulí. A jedna černá, která jde opačně.",
        "kanc_kyvadlo" to "🕰️ Kyvadlo visí ze tmy pod kopulí nad znakem Řádu a shazuje mosazné kolíky. Z čísel okruhů 198–213 je ryska 213 obtažená červeně.\nKyvadlo se k ní pokaždé vrátí o kousek blíž.",
        "kanc_vahy" to "⚖️ Monumentální váhy, znak Řádu. Na jedné misce je hrst popela, na druhé modrý krystal, který tiše svítí. Popel je těžší.",
        "kanc_stul" to "🖋️ Stůl Řádu a prázdný trůn s vahami v koruně. Svíčky hoří, i když tu nikdo není. V Registru nádob se brk sám zvedne a dopíše řádek:\nSUBJEKT 213 – STAV: PŘIPRAVOVÁN. ODPOVĚDNÝ: ███",
        "kanc_mapa" to "🗺️ Mapa Makrosvěta. Připínáčky STANICE 0 až 7 a od každého vede rudá nit dolů přes okraj rámu, někam pod svět.\nStanice 7 je tvoje město.",
        "kanc_globus" to "🌍 Glóbus, stejný jako Hunfreyho v archivu. Jen papírek, který tam měl přilepený, je tady strhnutý. Zbyl po něm kousek lepidla.",
        "kanc_katalog" to "🗃️ Kartotéka Řádu. Šuplíky OKRUH 1 až 213, všechny plné. Jen 213 je vytažený a prázdný. Připravený.",
        "kanc_pult" to "📖 Pult s hvězdným atlasem. Na otevřené stránce je souhvězdí Spící. Jedna hvězda je z papíru vyškrábaná nožem a na okraji stojí: VZATO."
    )

    val ACTION_NODES = TEXTS.keys + ELEVATOR_NODE

    /** Kancelář po Objektu 019 – Drakirra ji v noci vypálila (docs/adr/0104). */
    val BURNED_TEXTS = mapOf(
        "kanc_dalekohled" to "🔭 Dalekohled leží převrácený, čočka se roztavila do jediné slzy skla. Z cedulky SLEDOVAT. NEPROBOUZET. zbylo jen ohořelé: …PROBUDIT.",
        "kanc_orloj" to "🪐 Orloj se zastavil. Mosazné koule se roztekly a slily dohromady. Jen černá koule se pořád točí – opačně, a teď rychleji.",
        "kanc_kyvadlo" to "🕰️ Lano kyvadla přehořelo. Mosazné závaží leží uprostřed znaku Řádu, přesně na rysce 213.",
        "kanc_vahy" to "⚖️ Váhy jsou sežehlé do černa. Popel z kanceláře se smísil s popelem na misce. Modrý krystal z druhé misky zmizel.",
        "kanc_stul" to "🖋️ Trůn je ohořelý, svíčky konečně zhasly. Registr nádob přežil, jen se mu zkroutila vazba. Poslední řádek, čerstvým inkoustem:
SUBJEKT 213 – TEST SÍLY NÁDOBY: ÚSPĚŠNÝ.",
        "kanc_mapa" to "🗺️ Mapa shořela. Zůstaly jen rudé nitě – visí z prázdného rámu dolů, pod svět.",
        "kanc_globus" to "🌍 Glóbus pukl vedví. Uvnitř je dutý a začazený.",
        "kanc_katalog" to "🗃️ Kartotéka je ohořelá, šuplíky se roztavily do sebe. Jen šuplík 213 je vytažený a nepoškozený. Jako by ho oheň obešel.",
        "kanc_pult" to "📖 Hvězdný atlas shořel. Zbyla jediná stránka: souhvězdí Spící. Vyškrábaná hvězda je teď propálená skrz."
    )

    fun textFor(node: String, burned: Boolean): String? = (if (burned) BURNED_TEXTS[node] else null) ?: TEXTS[node]
}

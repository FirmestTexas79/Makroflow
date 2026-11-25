package cz.uhk.macroflow.pokemon.cave

/**
 * Zóna 3 (Akt III – Hlubina), lokace 1: Tábor výprav (docs/adr/0106). Klec ze Sektoru Hlubiny (−2) přistane na kořeni
 * Spící; opuštěný tábor Řádu – stany výprav, vysílačka se Septem, nástěnka, Hunfreyho křída. Bez setkání.
 * Uzly kopírují tools/mapgen/gen_rootcamp.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.ROOT_CAMP.
 */
object RootCamp {
    const val CAGE_NODE = "kt_klec"
    const val DOWN_NODE = "kt_sestup"
    /** Hráč už sjel klecí do tábora (Výprava 8). */
    const val VISITED_KEY = "zona3_tabor"
    /** Den, kdy hráč poprvé uviděl Spící z balkonu (klec se otevře až další den – Septovo „už brzy“); GamePrefs. */
    const val ABYSS_DAY_KEY = "abyss_seen_day"
    const val CAGE_OPENED_TEXT = "Na závoře klece visí nový štítek, ještě teplý z razítka:\nSESTUP SCHVÁLEN – VÝPRAVA 8 – SUBJEKT 213.\n\n" +
        "Červený zámek je odemčený. Z reproduktoru Sept, klidný jako vždy: „Říkal jsem, že už brzy. Zapisujte, co uvidíte, Subjekte. Hlavně zapisujte.“"
    const val CAGE_WAIT_TEXT = "\nZ reproduktoru Sept: „Už brzy, Subjekte. Vyspěte se. Zítra.“"

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(CAGE_NODE, 88, 66), CaveNode("kt_radio", 64, 104), CaveNode("kt_stred", 88, 140),
            CaveNode("kt_stan1", 44, 168), CaveNode("kt_stan2", 132, 168), CaveNode("kt_ohniste", 88, 204),
            CaveNode("kt_stan3", 46, 246), CaveNode("kt_tabule", 126, 240), CaveNode("kt_dole", 88, 284),
            CaveNode("kt_krida", 100, 306), CaveNode(DOWN_NODE, 88, 340)
        ),
        edges = listOf(CAGE_NODE to "kt_stred", "kt_radio" to "kt_stred", "kt_stred" to "kt_stan1", "kt_stred" to "kt_stan2",
            "kt_stred" to "kt_ohniste", "kt_ohniste" to "kt_stan3", "kt_ohniste" to "kt_tabule", "kt_ohniste" to "kt_dole",
            "kt_dole" to "kt_krida", "kt_dole" to DOWN_NODE),
        exitNode = CAGE_NODE,
        mountainNode = TowerAbyss.CAGE_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = emptySet(),
        parentBiome = "TOWER_ABYSS",
        isCave = false,
        tapAreas = mapOf(
            CAGE_NODE to Triple(88, 38, 20), "kt_radio" to Triple(56, 96, 12), "kt_stan1" to Triple(44, 146, 18),
            "kt_stan2" to Triple(130, 146, 18), "kt_ohniste" to Triple(88, 198, 10), "kt_stan3" to Triple(46, 224, 18),
            "kt_tabule" to Triple(126, 226, 16), "kt_krida" to Triple(114, 298, 12), DOWN_NODE to Triple(88, 344, 16)
        )
    )

    val TEXTS = mapOf(
        CAGE_NODE to "⛓️ Klec stojí na kořeni, řetězy mizí nahoru do tmy. Zámek je odemčený.\nNahoru pojede, kdykoli zatáhneš za páku. Dolů to bylo schválené jen jednou.",
        "kt_radio" to "📻 Polní vysílačka Řádu. Šumí. Mezi šumem Septův hlas, klidný jako vždy:\n„Subjekte 213, slyšíme vás. Výprava 8 je na místě. Zapisujte, co vidíte. Hlavně zapisujte.“",
        "kt_stan1" to "📓 Stan výpravy 3. Na lehátku deník, den 4:\n„Kořeny jsou teplé. Kdo na ně položí dlaň, slyší tep. Jednou za hodinu. Kartograf tvrdí, že je to jen voda.“\nDalší stránky chybí.",
        "kt_stan2" to "⛺ Stan výpravy 5. Pět lehátek, všechna ustlaná, deky ostře složené. Jen na jednom polštáři je důlek.\nNa štítku: OKRUH 161 – SPAL.",
        "kt_ohniste" to "🔥 Ohniště. Nikdo tu nepálil dřevo – žádné tu není, jen kořeny, a ty nehoří.\nPopel sem padá shora, pořád, jako sníh. Je teplý.",
        "kt_stan3" to "📓 Stan výpravy 7, poslední zápis v deníku:\n„Svah dolů je posetý skořápkami. Prázdné, lehké, každá má na zádech číslo. Nepočítejte je. Nepočítejte je. Nepočítejte je.“",
        "kt_tabule" to "📋 Nástěnka tábora:\nVÝPRAVA 1 – NEVRÁCENA · VÝPRAVA 2 – NEVRÁCENA · VÝPRAVA 3 – NEVRÁCENA · … · VÝPRAVA 7 – NEVRÁCENA.\nPoslední řádek je čerstvým inkoustem: VÝPRAVA 8 – SUBJEKT 213 – PROBÍHÁ.",
        "kt_krida" to "✏️ Na kůře křídou šipka dolů a číslo 212. Pod tím Hunfreyho písmo:\n„Byl jsem až tady. Dál ne – dál se zapomíná rychleji. Vrať se vždycky nahoru spát.“",
        DOWN_NODE to "⬇️ Kořen se stáčí dolů do mlhy. Svah bezejmenných.\nCedule Řádu: SESTUP NA SVAH – ZATÍM NESCHVÁLEN.",
        "kt_stred" to "🍄 Modré houby po okrajích kořene – stejné jako v Hvozdu u Mydra. Tady jich je tolik, že by se při nich dalo číst."
    )
    val ACTION_NODES = TEXTS.keys
}

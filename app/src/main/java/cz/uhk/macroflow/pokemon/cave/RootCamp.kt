package cz.uhk.macroflow.pokemon.cave

/**
 * Zóna 3 (Akt III – Hlubina), lokace 1: STANICE 8 – předsunutá základna Řádu (docs/adr/0106). Skalní římsa pod
 * stropem dutiny: šachta s klecí ze Sektoru Hlubiny, beton se značením, ubikace s vysílačkou, nástěnka výprav,
 * pozorovatelna A, ohňová hlídka, terminál lanovky ke kmeni, závora na cestě ke Svahu. Ve skále ruina Lumenců.
 * Kořeny Spící tu teprve začínají – prolamují roh ubikace, zvedají beton, obtáčejí pozorovatelnu. Bez setkání.
 * Uzly kopírují tools/mapgen/gen_station8.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.ROOT_CAMP.
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
            CaveNode(CAGE_NODE, 88, 124), CaveNode("kt_stred", 88, 168), CaveNode("kt_ruina", 34, 124),
            CaveNode("kt_radio", 28, 210), CaveNode("kt_tabule", 78, 204), CaveNode("kt_vez", 140, 246),
            CaveNode("kt_hlidka", 108, 262), CaveNode("kt_dole", 84, 290), CaveNode("kt_lanovka", 130, 300),
            CaveNode("kt_krida", 62, 312), CaveNode(DOWN_NODE, 36, 338)
        ),
        edges = listOf(CAGE_NODE to "kt_stred", "kt_stred" to "kt_ruina", "kt_stred" to "kt_radio", "kt_stred" to "kt_tabule",
            "kt_radio" to "kt_tabule", "kt_stred" to "kt_vez", "kt_vez" to "kt_hlidka", "kt_stred" to "kt_hlidka",
            "kt_hlidka" to "kt_dole", "kt_dole" to "kt_lanovka", "kt_dole" to "kt_krida", "kt_krida" to DOWN_NODE),
        exitNode = CAGE_NODE,
        mountainNode = TowerAbyss.CAGE_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = emptySet(),
        parentBiome = "TOWER_ABYSS",
        isCave = false,
        tapAreas = mapOf(
            CAGE_NODE to Triple(88, 88, 22), "kt_ruina" to Triple(33, 76, 22), "kt_radio" to Triple(36, 180, 22),
            "kt_tabule" to Triple(78, 182, 12), "kt_vez" to Triple(142, 168, 18), "kt_hlidka" to Triple(124, 260, 16),
            "kt_lanovka" to Triple(150, 312, 18), "kt_krida" to Triple(74, 306, 9), DOWN_NODE to Triple(40, 318, 16),
            "kt_stred" to Triple(88, 214, 14)
        )
    )

    val TEXTS = mapOf(
        CAGE_NODE to "⛓️ Klec stojí v ocelové šachtě, která sjíždí ze stropu dutiny. Řetězy mizí nahoru do tmy, zámek visí odemčený.\nNahoru pojede, kdykoli zatáhneš za páku. Dolů to bylo schválené jen jednou.",
        "kt_ruina" to "🏛️ Ve skále je vytesaná fasáda – starší než Řád, starší než Ozvěna. Lomený oblouk, štíhlé sloupy, nad vchodem obrácená koruna. Drobné znaky na římse pořád slabě svítí modře.\n" +
            "Kořeny ji objímají a pomalu stahují do skály. Přes sloup je nastříkáno: OBJEKT NEPŘÍSTUPNÝ – NEINVENTARIZOVAT.",
        "kt_radio" to "📻 Ubikace S-8. V okně svítí lampa, kterou nikdo nezhasl. Šest lůžek, deky ostře složené.\n" +
            "Na stole šumí vysílačka a v šumu Sept: „Subjekte 213, Stanice 8 vás vítá. Výprava 8 je na místě. Zapisujte, co vidíte. Hlavně zapisujte.“\n" +
            "Roh ubikace prolomil kořen. Plech je ohnutý zevnitř ven.",
        "kt_tabule" to "📋 OBSAZENÍ HLUBINY\nFÁZE 1: STANICE 8 – DOKONČENO · FÁZE 2: POZOROVATELNA B – DOKONČENO · FÁZE 3: TÁBOR NA SVAHU – OPUŠTĚNO · FÁZE 4: KMEN – ███\n" +
            "VÝPRAVY 1–7: NEVRÁCENY.\nPoslední řádek čerstvým inkoustem: VÝPRAVA 8 – SUBJEKT 213 – PROBÍHÁ.",
        "kt_vez" to "🗼 Pozorovatelna A. Reflektor svítí dolů do tmy, tam, kde by měl být kmen. Kolem nohy věže se ovíjí kořen.\n" +
            "Štítek u žebříku: KONTROLA 1: KOŘENY 0 m. KONTROLA 2: KOŘENY 4 m. KONTROLA 3: KOŘENY U VĚŽE. KONTROLA 4: ███",
        "kt_hlidka" to "🔥 Ohňová hlídka: sudy s palivem, pytle s pískem a spálené pahýly kořenů, které ještě doutnají.\n" +
            "Rozpis služeb: PÁLIT KAŽDÝCH 6 HODIN. Poslední podpis je před 43 dny. Kořeny od té doby dorostly až k sudům.",
        "kt_lanovka" to "🚡 Spodní stanice lanovky ke kmeni. Gondola visí u rampy s rozbitým okénkem, lana mizí přes okraj do tmy.\n" +
            "Cedule: LANOVKA → KMEN · MIMO PROVOZ · DALŠÍ JÍZDA: ZATÍM NESCHVÁLENO.",
        "kt_krida" to "✏️ Na patce závory křídou šipka dolů a číslo 212. Pod tím Hunfreyho písmo:\n" +
            "„Byl jsem až tady. Dál ne – dál se zapomíná rychleji. Vrať se vždycky nahoru spát.“",
        DOWN_NODE to "🚧 Závora UZÁVĚRA. Za ní se cesta stáčí po římse dolů do mlhy – na Svah bezejmenných.\n" +
            "Na rameni visí fotka výpravy 7: devět lidí v kombinézách Řádu se usmívá. Na zadní straně tužkou: „Nepočítejte je.“\n" +
            "Cedule: SESTUP NA SVAH – ZATÍM NESCHVÁLEN.",
        "kt_stred" to "🟡 Přistávací kruh S-8. Žluté čáry vedou od klece ke všemu, co tu Řád postavil – a končí tam, kde začínají kořeny."
    )
    val ACTION_NODES = TEXTS.keys
}

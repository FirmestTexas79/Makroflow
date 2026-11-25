package cz.uhk.macroflow.pokemon.cave

/**
 * Zóna 3 (Akt III – Hlubina), lokace 4 z 8: TÁBOR NA SVAHU (docs/adr/0106), pohlcení 4/8. Fáze 3 obsazení Hlubiny –
 * opuštěno. Odsud výpravy 1–7 sestupovaly na Svah bezejmenných. Deníky ve stanech se stupňují: měření → tep kořenů
 * → zapomínání jmen (kdo spí, pamatuje si) → „Nepočítejte je.“ Středem prošel kořen a spolkl stany 2, 4 a 6.
 * Hunfrey sem nedošel (ve Stanici 8: „Byl jsem až tady.“). Setkání DUCH.
 * Uzly kopírují tools/mapgen/gen_slope_camp.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.ROOT_SLOPECAMP.
 */
object RootSlopeCamp {
    const val ENTRY_NODE = "tv_vstup"
    const val DOWN_NODE = "tv_dolu"
    const val VISITED_KEY = "zona3_tabor_na_svahu"

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 22, 86), CaveNode("tv_ruina", 140, 84), CaveNode("tv_registr", 62, 124),
            CaveNode("tv_kuchyne", 122, 146), CaveNode("tv_stan1", 34, 196), CaveNode("tv_most", 88, 209),
            CaveNode("tv_stan5", 146, 240), CaveNode("tv_stan3", 46, 262), CaveNode("tv_tabule", 58, 314),
            CaveNode("tv_stan7", 132, 302), CaveNode("tv_navijak", 154, 330), CaveNode(DOWN_NODE, 92, 346)
        ),
        edges = listOf(ENTRY_NODE to "tv_registr", "tv_registr" to "tv_ruina", "tv_registr" to "tv_kuchyne", "tv_registr" to "tv_stan1",
            "tv_stan1" to "tv_most", "tv_most" to "tv_kuchyne", "tv_most" to "tv_stan5", "tv_stan1" to "tv_stan3",
            "tv_stan3" to "tv_tabule", "tv_stan5" to "tv_stan7", "tv_stan7" to "tv_navijak", "tv_tabule" to DOWN_NODE,
            "tv_stan7" to DOWN_NODE),
        exitNode = ENTRY_NODE,
        mountainNode = RootWatch.DOWN_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("tv_most", "tv_stan5", "tv_stan3", "tv_stan7"),
        parentBiome = "ROOT_WATCH",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(18, 40, 16), "tv_ruina" to Triple(144, 54, 24), "tv_registr" to Triple(60, 96, 20),
            "tv_kuchyne" to Triple(124, 116, 22), "tv_stan1" to Triple(34, 172, 20), "tv_most" to Triple(118, 150, 40),
            "tv_stan5" to Triple(150, 222, 20), "tv_stan3" to Triple(40, 238, 20), "tv_tabule" to Triple(56, 280, 18),
            "tv_stan7" to Triple(138, 280, 20), "tv_navijak" to Triple(156, 312, 14), DOWN_NODE to Triple(100, 330, 14)
        )
    )

    val TEXTS = mapOf(
        ENTRY_NODE to "🪜 Schody z terasy Pozorovatelny B. Nahoru vedou zpátky k bunkru.",
        "tv_ruina" to "🏛️ Ve skále nad táborem kamenná žebra dlouhého domu – starší než Řád. Na vrcholech oblouků svítí drobné modré znaky.\\n" +
            "Kořeny je stahují do skály, každé žebro o kus hlouběji než to před ním.",
        "tv_registr" to "📒 KNIHA ZÁPISŮ – TÁBOR NA SVAHU. Každá výprava se tu zapsala.\\n" +
            "U výpravy 1 jsou jména celá. U výpravy 4 je každé druhé přeškrtnuté a nad ním číslo. Výprava 7 už jména nepsala – jen 7-1 až 7-9.\\nLampa na stolku pořád svítí.",
        "tv_kuchyne" to "🍲 Polní kuchyně. Kotel plný vychladlé polévky pod vrstvou popela, ešusy nachystané pro devět lidí. Nikdo nejedl.\\n" +
            "Na kotli lístek: NEZAPOMEŇTE JÍST. Podtrženo třikrát.",
        "tv_stan1" to "📓 Stan výpravy 1. Deník velitelky Ardis, den 2:\\n„Kořeny měříme každých šest hodin. Nehýbou se. Hlubina je mrtvá – a mrtvé se neměří, jen eviduje. Zítra sestup na svah.“\\nDalší strana je prázdná.",
        "tv_stan3" to "📓 Stan výpravy 3. Deník kartografa Tamsina:\\n„Kořeny jsou teplé. Kdo na ně položí dlaň, slyší tep – jednou za hodinu. Velitel tvrdí, že je to voda.“\\nPod tím jiným písmem: „Nebyla to voda.“",
        "tv_stan5" to "📓 Stan výpravy 5. Na plátně zevnitř tužkou jména – desítky, pořád dokola ta samá, jako by si je někdo opakoval, aby je nezapomněl. Ke konci se písmo rozpadá.\\n" +
            "Poslední řádek: „Kdo spí, pamatuje si. Kdo hlídá, zapomíná. Spěte.“",
        "tv_stan7" to "📓 Stan výpravy 7 – poslední, která sestoupila. Lehátka ustlaná, deky ostře složené, na jednom polštáři důlek. Uvnitř pořád svítí lucerna.\\n" +
            "Deník: „Svah dolů je posetý těly. Lehká, vyschlá, každé má na zádech číslo Řádu. Všechna leží hlavou vzhůru, jako by utíkala. Nepočítejte je. Nepočítejte je. Nepočítejte je.“",
        "tv_most" to "🪵 Prkenná lávka přes kořen, který prošel středem tábora. Co mu stálo v cestě – stany výprav 2, 4 a 6 – je v něm; z kůry trčí jen tyče s praporky.\\n" +
            "Když stojíš na lávce, cítíš pod nohama tep. Jednou za hodinu.",
        "tv_tabule" to "🧮 SČÍTÁNÍ – tabule s čárkami po pěti. Stovky. Někdo počítal těla na svahu, dokud nepřestal.\\nPoslední řádky jsou přeškrtnuté a přes ně křídou: NEPOČÍTEJTE.",
        "tv_navijak" to "⚙️ Ruční naviják. Lano vede dolů po svahu do mlhy a je napnuté – na druhém konci je něco těžkého.\\nKlika je zajištěná rudou pojistkou s cedulkou: NEVYTAHOVAT.",
        DOWN_NODE to "⬇️ Řada červenobílých kolíků sestupuje do mlhy – na Svah bezejmenných. Lano mezi nimi je přetržené.\\nZATÍM NEPŘÍSTUPNÉ."
    )
    val ACTION_NODES = TEXTS.keys
}

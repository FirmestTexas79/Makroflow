package cz.uhk.macroflow.pokemon.cave

/**
 * Zóna 3 (Akt III – Hlubina): KMEN (docs/adr/0106) – 8/8, bůh ve Stromu. Z Prahu po větvi se stupy na ocelovou lávku
 * Řádu u kmene Spící. V ráně zalité pěnou (Operace Pečeť) je přibitý Trpitel (Kazimír) – NPC se šesti pažemi a šesti
 * očima, které dýchá a hýbe rukama (trpitel_sheet.png, tools/mapgen/gen_trpitel.py). Na lávce sonda Řádu, nádrže
 * s pěnou, popel; u paty kmene puklina do Středu kořenů. Bez divokých setkání. Volba konce: story/Trpitel.kt.
 * Uzly kopírují tools/mapgen/gen_trunk.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.ROOT_TRUNK.
 */
object RootTrunk {
    const val ENTRY_NODE = "kn_vstup"
    const val TRPITEL_NODE = "kn_trpitel"
    const val CRACK_NODE = "kn_stred"
    const val VISITED_KEY = "zona3_kmen"

    /** Trpitel: levý horní roh spritu v mapě (art px), počet snímků v pásu a jejich délka (gen_trpitel.py). */
    const val TRPITEL_X0 = 0
    const val TRPITEL_Y0 = 16
    const val TRPITEL_FRAMES = 12
    const val TRPITEL_FRAME_MS = 280

    val MAP = CaveMap(
        artW = 432, artH = 960,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 224, 950), CaveNode("kn_vetev", 208, 830), CaveNode("kn_lavka", 216, 648), CaveNode("kn_sonda", 112, 612),
            CaveNode("kn_kotva", 88, 542), CaveNode(TRPITEL_NODE, 216, 544), CaveNode("kn_popel", 250, 560), CaveNode("kn_hadice", 288, 612),
            CaveNode(CRACK_NODE, 340, 540)
        ),
        edges = listOf(ENTRY_NODE to "kn_vetev", "kn_vetev" to "kn_lavka", "kn_lavka" to "kn_sonda", "kn_lavka" to "kn_hadice",
            "kn_lavka" to TRPITEL_NODE, "kn_sonda" to "kn_kotva", "kn_kotva" to TRPITEL_NODE, TRPITEL_NODE to "kn_popel",
            "kn_popel" to "kn_hadice", "kn_hadice" to CRACK_NODE, "kn_popel" to CRACK_NODE),
        exitNode = ENTRY_NODE,
        mountainNode = RootThreshold.BRANCH_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = emptySet(),
        parentBiome = "ROOT_THRESHOLD",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(224, 948, 16), "kn_lavka" to Triple(142, 640, 24), "kn_sonda" to Triple(112, 590, 18),
            "kn_kotva" to Triple(68, 260, 26), TRPITEL_NODE to Triple(216, 260, 140), "kn_popel" to Triple(230, 536, 24),
            "kn_hadice" to Triple(308, 612, 22), CRACK_NODE to Triple(340, 490, 18)
        )
    )

    val TEXTS = mapOf(
        ENTRY_NODE to "🌳 Větev se stupy dolů – zpátky na Práh.",
        "kn_vetev" to "🌳 Větev stoupá od trůnu Lumenců ke kmeni. Někdo do ní vysekal stupy a natáhl lano. Pod tebou je jen tma " +
            "a hluboko dole slabá záře koruny.",
        "kn_lavka" to "🪧 Cedule na zábradlí: OPERACE PEČEŤ. Pod ní menším písmem: „Úsek 8 – kmen. Vstup jen s doprovodem. " +
            "Subjekt se nesmí dotýkat nástroje.“",
        "kn_sonda" to "📡 Sonda Řádu na trojnožce. Kontrolka svítí rudě. Z reproduktoru jde tichý šum – a když se přiblížíš, " +
            "ztiší se, jako by poslouchala.",
        "kn_kotva" to "⚓ Šest ocelových kotev zatlučených do kůry kolem rány, každá s číslem K1–K6. Z kotev vedou řetězy k zápěstím. " +
            "Kůra kolem nich zarostla do tvaru jizvy. Kmen se je snaží vypudit už hodně dlouho.",
        "kn_popel" to "🌫️ Hromady popela pod ránou. Je teplý. Padá pořád – pomalu, jako sníh, z popraskané kůže toho nahoře. " +
            "V popelu jsou stopy bot Řádu. Všechny vedou pryč.",
        "kn_hadice" to "🛢️ Tlakové nádrže s pěnou a hadice do rány. Štítek: „PEČEŤ – DOPLNIT KAŽDOU NOC.“ " +
            "Poslední podpis v tabulce je Orrinův. Pak už nikdo."
    )
    val ACTION_NODES = TEXTS.keys + TRPITEL_NODE + CRACK_NODE
}

package cz.uhk.macroflow.pokemon.cave

/**
 * Zóna 3 (Akt III – Hlubina): PRÁH (docs/adr/0106) – aréna posledního souboje s Drakirrou u paty koruny Spící. Z Lesa pod
 * korunou branou zkřížených větví na procesní cestu z kamenných desek přes mělké černé jezero. Vzadu se hlavní větve koruny
 * spouštějí do vody a splétají v bledou stěnu jako žebra katedrály, uprostřed trůn Lumenců se sochou ženy v kápi (Lumira),
 * za trůnem větev se stupy ke kmeni. Shora do středu padá sloup světla z rány, jezero zrcadlí stěnu, v mělčině klečí
 * kamenní Lumenci, podél cesty sloupy s modrými plameny. Na ostrově s kruhem znaků leží Drakirra (story/DrakirraFinal.kt).
 * Bez divokých setkání. Uzly kopírují tools/mapgen/gen_threshold.py – ZDROJ PRAVDY je v obou místech.
 * Živé detaily: InteriorFxView.ROOT_THRESHOLD.
 */
object RootThreshold {
    const val ENTRY_NODE = "pr_vstup"
    const val DRAKIRRA_NODE = "pr_drakirra"
    const val BRANCH_NODE = "pr_vetev"
    const val VISITED_KEY = "zona3_prah"
    /** Pata Drakirry na ostrově (art px). */
    const val DRAKIRRA_X = 88
    const val DRAKIRRA_Y = 356

    val MAP = CaveMap(
        artW = 176, artH = 560,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 88, 550), CaveNode("pr_hraz", 88, 476), CaveNode("pr_socha_l", 40, 418), CaveNode("pr_socha_r", 136, 414),
            CaveNode(DRAKIRRA_NODE, 88, 384), CaveNode("pr_odraz", 34, 330), CaveNode("pr_trun", 88, 268), CaveNode(BRANCH_NODE, 134, 262)
        ),
        edges = listOf(ENTRY_NODE to "pr_hraz", "pr_hraz" to "pr_socha_l", "pr_hraz" to "pr_socha_r", "pr_hraz" to DRAKIRRA_NODE,
            "pr_socha_l" to "pr_odraz", "pr_odraz" to "pr_trun", "pr_socha_r" to BRANCH_NODE, "pr_trun" to BRANCH_NODE,
            DRAKIRRA_NODE to "pr_odraz"),
        exitNode = ENTRY_NODE,
        mountainNode = RootForest.THRESHOLD_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = emptySet(),
        parentBiome = "ROOT_FOREST",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(88, 550, 12), "pr_socha_l" to Triple(26, 462, 20), "pr_socha_r" to Triple(150, 456, 20),
            DRAKIRRA_NODE to Triple(88, 336, 34), "pr_odraz" to Triple(40, 290, 26), "pr_trun" to Triple(88, 178, 34),
            BRANCH_NODE to Triple(134, 150, 16)
        )
    )

    val TEXTS = mapOf(
        ENTRY_NODE to "🌳 Brána zkřížených větví – zpátky do Lesa pod korunou.",
        "pr_socha_l" to "🗿 Kamenní Lumenci klečí v mělčině čelem k trůnu, hlavy skloněné. Někteří hlavy nemají. Voda jim sahá do pasu.\n" +
            "Na zádech mají vytesané stejné znaky jako oltář přání v chrámu.",
        "pr_socha_r" to "🗿 Klečící postava, čelem k trůnu. Kořen jí vrostl do ramene a nese ji, jako by ji nechtěl nechat spadnout do vody.",
        "pr_odraz" to "🌊 Jezero je černé a úplně klidné. Zrcadlí celou stěnu větví i trůn – jen tvůj odraz se v něm objeví o chvilku později než ty.",
        "pr_trun" to "🪑 Trůn Lumenců vrostlý do kořenů. Sedí na něm socha ženy v kápi, ruce sepjaté v klíně, hlava skloněná, jako by spala.\n" +
            "Obličej pod kápí nevytesali – je tam jen tma. Nad ní obrácená koruna ze svítících znaků.\nNa podstavci vyryto jedno jméno: LUMIRA."
    )
    val ACTION_NODES = TEXTS.keys + DRAKIRRA_NODE + BRANCH_NODE
}

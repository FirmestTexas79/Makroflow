package cz.uhk.macroflow.pokemon.cave

/**
 * Zóna 3 (Akt III – Hlubina), lokace 3 z 8 – vedlejší: SIGNÁLNÍ ÚTES (docs/adr/0106). Ostroh na okraji propasti
 * s výhledem na celou Spící (kořeny po stropě, kmen s ránou a zelenou mlhou, obrácená koruna, kopí a šest světel).
 * Signální stanoviště Řádu a bouda signalisty Keshe – vedlejší úkol „Signalista“ (story/Signal.kt).
 * Uzly kopírují tools/mapgen/gen_signal.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.ROOT_SIGNAL.
 */
object RootSignal {
    const val ENTRY_NODE = "su_vstup"
    const val VISITED_KEY = "zona3_signalni_utes"

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 166, 326), CaveNode("su_bouda", 138, 318), CaveNode(cz.uhk.macroflow.pokemon.story.Signal.NODE, 96, 266),
            CaveNode(cz.uhk.macroflow.pokemon.story.Signal.LAMP_NODE, 70, 262), CaveNode("su_kniha", 52, 284), CaveNode("su_lavicka", 112, 250),
            CaveNode("su_lanovka", 30, 300), CaveNode("su_okraj", 88, 236)
        ),
        edges = listOf(ENTRY_NODE to "su_bouda", "su_bouda" to cz.uhk.macroflow.pokemon.story.Signal.NODE,
            cz.uhk.macroflow.pokemon.story.Signal.NODE to cz.uhk.macroflow.pokemon.story.Signal.LAMP_NODE,
            cz.uhk.macroflow.pokemon.story.Signal.LAMP_NODE to "su_kniha", cz.uhk.macroflow.pokemon.story.Signal.NODE to "su_lavicka",
            "su_lavicka" to "su_okraj", "su_kniha" to "su_lanovka", cz.uhk.macroflow.pokemon.story.Signal.LAMP_NODE to "su_okraj"),
        exitNode = ENTRY_NODE,
        mountainNode = RootWatch.RAVINE_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("su_okraj", "su_lavicka", "su_lanovka"),
        parentBiome = "ROOT_WATCH",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(168, 326, 10), "su_bouda" to Triple(140, 282, 22), cz.uhk.macroflow.pokemon.story.Signal.NODE to Triple(96, 258, 10),
            cz.uhk.macroflow.pokemon.story.Signal.LAMP_NODE to Triple(70, 238, 16), "su_kniha" to Triple(50, 276, 9),
            "su_lavicka" to Triple(112, 252, 12), "su_lanovka" to Triple(28, 262, 16), "su_okraj" to Triple(92, 90, 70)
        )
    )

    val TEXTS = mapOf(
        ENTRY_NODE to "🌉 Ocelová lávka zpátky k Pozorovatelně B.",
        "su_bouda" to "🏚️ Bouda signalisty. Kamna ještě hřejí, na stole hrnek s čajem a deset nabroušených tužek. Na posteli neleží nikdo – deka je složená, jako by se na ni dlouho nikdo neodvážil lehnout.",
        "su_kniha" to cz.uhk.macroflow.pokemon.story.Signal.BOOK,
        "su_lavicka" to "🪑 Lavička k propasti. Prkna jsou ohlazená – někdo tu sedává každou noc a dívá se na kmen.",
        "su_lanovka" to "🚡 Horní stanice lanovky. Lana vedou přes propast až ke kmeni, uprostřed visí žlutá gondola. Nehýbe se. Kolo je zajištěné řetězem a rudou plombou: DALŠÍ JÍZDA ZATÍM NESCHVÁLENA.",
        "su_okraj" to "🌳 Spící. Celá, odsud poprvé: kořeny rozlezlé po stropě dutiny, kmen visící do prázdna a obrácená koruna, která se ztrácí v mlze.\n" +
            "Z rány v kmeni teče zelená mlha. Kousek nad ní se něco blýská – kov. A vedle toho, když se díváš dost dlouho, šest drobných světel."
    )
    val ACTION_NODES = TEXTS.keys + cz.uhk.macroflow.pokemon.story.Signal.NODE + cz.uhk.macroflow.pokemon.story.Signal.LAMP_NODE
}

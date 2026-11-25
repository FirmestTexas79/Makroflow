package cz.uhk.macroflow.pokemon.cave

/**
 * Zóna 3 (Akt III – Hlubina), lokace 3 z 8 – vedlejší: SIGNÁLNÍ ÚTES (docs/adr/0106). Vysoká mapa (kamera jede s hráčem):
 * stezka podél stěny dutiny stoupá přes lávku na ostroh nad propastí, vlevo v pozadí celá Spící (kořeny po stropě, kmen s ránou a zelenou mlhou, obrácená koruna, kopí a šest světel).
 * Signální stanoviště Řádu a bouda signalisty Keshe – vedlejší úkol „Signalista“ (story/Signal.kt).
 * Uzly kopírují tools/mapgen/gen_signal.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.ROOT_SIGNAL.
 */
object RootSignal {
    private val S = cz.uhk.macroflow.pokemon.story.Signal
    const val ENTRY_NODE = "su_vstup"
    const val VISITED_KEY = "zona3_signalni_utes"

    val MAP = CaveMap(
        artW = 176, artH = 560,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 150, 548), CaveNode("su_cesta1", 138, 484), CaveNode("su_lavka", 138, 388), CaveNode("su_cesta2", 136, 306),
            CaveNode("su_lanovka", 138, 252), CaveNode("su_kniha", 116, 216), CaveNode(S.NODE, 124, 192), CaveNode(S.LAMP_NODE, 112, 178),
            CaveNode("su_lavicka", 136, 176), CaveNode("su_bouda", 136, 152), CaveNode("su_okraj", 108, 164)
        ),
        edges = listOf(ENTRY_NODE to "su_cesta1", "su_cesta1" to "su_lavka", "su_lavka" to "su_cesta2", "su_cesta2" to "su_lanovka",
            "su_lanovka" to "su_kniha", "su_kniha" to S.NODE, S.NODE to S.LAMP_NODE, S.NODE to "su_lavicka", "su_lavicka" to "su_bouda",
            S.LAMP_NODE to "su_okraj"),
        exitNode = ENTRY_NODE,
        mountainNode = RootWatch.RAVINE_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("su_cesta1", "su_cesta2", "su_lanovka"),
        parentBiome = "ROOT_WATCH",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(150, 550, 10), "su_lavka" to Triple(138, 388, 16), "su_bouda" to Triple(136, 120, 22),
            S.NODE to Triple(124, 184, 10), S.LAMP_NODE to Triple(108, 150, 16), "su_kniha" to Triple(108, 200, 8),
            "su_lavicka" to Triple(140, 180, 12), "su_lanovka" to Triple(126, 214, 16), "su_okraj" to Triple(46, 200, 60)
        )
    )

    val TEXTS = mapOf(
        ENTRY_NODE to "🌉 Ocelová lávka zpátky k Pozorovatelně B.",
        "su_bouda" to "🏚️ Bouda signalisty. Kamna ještě hřejí, na stole hrnek s čajem a deset nabroušených tužek. Na posteli neleží nikdo – deka je složená, jako by se na ni dlouho nikdo neodvážil lehnout.",
        "su_kniha" to cz.uhk.macroflow.pokemon.story.Signal.BOOK,
        "su_lavka" to "🪵 Dřevěná lávka přes průrvu ve stezce. Prkna jsou nová – někdo je nedávno vyměnil. Pod lávkou není nic, jen mlha a hluboko dole koruna.",
        "su_lavicka" to "🪑 Lavička k propasti. Prkna jsou ohlazená – někdo tu sedává každou noc a dívá se na kmen.",
        "su_lanovka" to "🚡 Horní stanice lanovky. Lana vedou přes propast až ke kmeni, uprostřed visí žlutá gondola. Nehýbe se. Kolo je zajištěné řetězem a rudou plombou: DALŠÍ JÍZDA ZATÍM NESCHVÁLENA.",
        "su_okraj" to "🌳 Spící. Celá, odsud poprvé: kořeny rozlezlé po stropě dutiny, kmen visící do prázdna a obrácená koruna, která se ztrácí v mlze.\n" +
            "Z rány v kmeni teče zelená mlha. Kousek nad ní se něco blýská – kov. A vedle toho, když se díváš dost dlouho, šest drobných světel."
    )
    const val CABLE_OPEN_TEXT = "🚡 Rudá plomba je přestřižená. Na kole visí lístek Keshovým písmem: „Někdo tam dole odpověděl. Jestli chcete vědět kdo, " +
        "gondola dojede až ke kořeni u kmene. Pak se vraťte nahoru a vyspěte se.“\nKolo se se skřípěním roztočí."
    val ACTION_NODES = TEXTS.keys + cz.uhk.macroflow.pokemon.story.Signal.NODE + cz.uhk.macroflow.pokemon.story.Signal.LAMP_NODE
}

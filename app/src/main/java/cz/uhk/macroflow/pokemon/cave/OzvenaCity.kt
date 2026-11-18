package cz.uhk.macroflow.pokemon.cave

/**
 * Město Ozvěna (Akt II, docs/adr/0092): střed regionu za Branou světů. Mapa 384 × 656 s přiblíženou kamerou
 * (hráč vidí 128 art px na šířku), uzly kopírují tools/mapgen/city/gen_city.py – ZDROJ PRAVDY je v obou místech.
 * Hráč přijde severní branou z Popelavé pláně; Aréna vede do vstupní haly ([ArenaHall]).
 */
object OzvenaCity {
    const val VISITED_KEY = "ozvena_visited"
    const val ENTRY_NODE = "vstup_z_plane"
    const val ARENA_NODE = "arena"
    const val BAKERY_NODE = "pekarna"
    const val VENKA_NODE = "venka"
    const val BOARD_NODE = "vyveska"
    const val FOUNTAIN_NODE = "kasna"
    const val TOWER_NODE = "vez"
    const val LIFT_NODE = "vytah"
    const val SHED_NODE = "sklad"
    const val LIDA_NODE = "lida"
    const val JARDA_NODE = "jarda"
    const val KORINEK_NODE = "korinek"
    // obyvatelé z docs/adr/0101 (uzel = id obyvatele)
    const val CORRA_NODE = "corra"
    const val OSWIN_NODE = "oswin"
    const val PELL_NODE = "pell"
    const val PIP_NODE = "pip"
    const val LIO_NODE = "lio"
    const val SABETH_NODE = "sabeth"

    /**
     * Obyvatel města: Makromon druhu [species] (číslo v Makrodexu), pata v art px, klepnutí vede na uzel [node].
     * Ve městě nežijí lidé – usadili se tu Makromoni, kteří jednoho dne začali mluvit (docs/adr/0093).
     */
    data class Townsfolk(val id: String, val species: Int, val node: String, val x: Int, val y: Int)

    /** Pekař a Vessa stojí u svých dveří, ostatní po městě. */
    val TOWNSFOLK = listOf(
        Townsfolk("bohousek", 1, BAKERY_NODE, 298, 256),      // Ignar – plamínek na ocase drží pec rozpálenou
        Townsfolk("venka", 38, VENKA_NODE, 78, 256),          // Johnsova – sova písařka
        Townsfolk("lida", 7, LIDA_NODE, 150, 308),            // Flori – jablka ze sadu
        Townsfolk("jarda", 32, JARDA_NODE, 206, 208),         // Mysnic – metař
        Townsfolk("korinek", 44, KORINEK_NODE, 234, 392),     // Gemak – starý pán s novinami
        Townsfolk("corra", 4, CORRA_NODE, 62, 332),           // Aqulin – pradlena
        Townsfolk("oswin", 31, OSWIN_NODE, 335, 345),         // Axlu – hodinář
        Townsfolk("pell", 20, PELL_NODE, 172, 404),           // Finlet – sourozenci u kašny
        Townsfolk("pip", 20, PIP_NODE, 212, 404),
        Townsfolk("lio", 39, LIO_NODE, 150, 178),             // Grifben – pošťák
        Townsfolk("sabeth", 27, SABETH_NODE, 310, 282)        // Phantil – zpívá melodii, kterou nikdo nezná
    )

    val MAP = CaveMap(
        artW = 384, artH = 656,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 192, 108), CaveNode("c_sever", 186, 182), CaveNode(VENKA_NODE, 96, 256),
            CaveNode(BAKERY_NODE, 282, 258), CaveNode("namesti", 124, 336), CaveNode(BOARD_NODE, 116, 284),
            CaveNode(FOUNTAIN_NODE, 192, 368), CaveNode("c_jih", 192, 410), CaveNode(ARENA_NODE, 318, 494),
            CaveNode(TOWER_NODE, 192, 572), CaveNode(LIFT_NODE, 170, 588), CaveNode(SHED_NODE, 352, 560),
            CaveNode(LIDA_NODE, 164, 310), CaveNode(JARDA_NODE, 220, 208), CaveNode(KORINEK_NODE, 252, 398),
            CaveNode(CORRA_NODE, 76, 336), CaveNode(OSWIN_NODE, 321, 349), CaveNode(PELL_NODE, 184, 406),
            CaveNode(PIP_NODE, 200, 406), CaveNode(LIO_NODE, 164, 180), CaveNode(SABETH_NODE, 296, 286)
        ),
        edges = listOf(
            ENTRY_NODE to "c_sever", "c_sever" to VENKA_NODE, "c_sever" to BAKERY_NODE, "c_sever" to "namesti",
            "namesti" to BOARD_NODE, "namesti" to FOUNTAIN_NODE, "namesti" to "c_jih", "c_jih" to ARENA_NODE,
            "c_jih" to TOWER_NODE, TOWER_NODE to LIFT_NODE, TOWER_NODE to SHED_NODE,
            "namesti" to LIDA_NODE, "c_sever" to JARDA_NODE, "c_jih" to KORINEK_NODE,
            "namesti" to CORRA_NODE, "c_jih" to OSWIN_NODE, "c_jih" to PELL_NODE, "c_jih" to PIP_NODE,
            "c_sever" to LIO_NODE, BAKERY_NODE to SABETH_NODE
        ),
        exitNode = ENTRY_NODE,
        mountainNode = AshPlain.CITY_GATE_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = emptySet(),
        artPixelsAcross = 128,
        parentBiome = "ASH_PLAIN",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(192, 84, 18),
            VENKA_NODE to Triple(92, 236, 24), BAKERY_NODE to Triple(276, 232, 30),
            BOARD_NODE to Triple(116, 262, 10), FOUNTAIN_NODE to Triple(192, 330, 34),
            ARENA_NODE to Triple(318, 458, 40), TOWER_NODE to Triple(192, 520, 24),
            LIFT_NODE to Triple(170, 580, 14), SHED_NODE to Triple(352, 538, 12),
            LIDA_NODE to Triple(150, 296, 12), JARDA_NODE to Triple(206, 196, 12), KORINEK_NODE to Triple(234, 380, 12),
            CORRA_NODE to Triple(62, 320, 12), OSWIN_NODE to Triple(335, 333, 12), PELL_NODE to Triple(172, 392, 10),
            PIP_NODE to Triple(212, 392, 10), LIO_NODE to Triple(150, 166, 12), SABETH_NODE to Triple(310, 270, 12)
        )
    )

    val ACTION_NODES = setOf(ENTRY_NODE, ARENA_NODE, BAKERY_NODE, VENKA_NODE, BOARD_NODE, FOUNTAIN_NODE, TOWER_NODE, LIFT_NODE, SHED_NODE,
        LIDA_NODE, JARDA_NODE, KORINEK_NODE) + RESIDENT_NODES_NEW

    const val BAKERY_TEXT = "🥖 Pekař Brann: „Dobré ráno, dobré ráno! Čerstvý chléb, jako každé ráno!“\n" +
        "Plamínek na ocase mu drží pec rozpálenou. Za ním na pultu leží bochníky – všechny stejný tvar, stejná kůrka, stejná prasklinka."
    const val VENKA_TEXT = "✍️ Písařka Vessa si načechrá peří a namočí brk: „Á, nová tvář. Počkej, ať si tě zapíšu… Tak. Teď se neztratíš.“\n" +
        "Za ní je celá zeď popsaná jmény, řádek za řádkem. Některá jsou čerstvá, jiná skoro smytá deštěm. Její vlastní mezi nimi není."
    const val BOARD_TEXT = "📌 Vývěska: „Trh každé ráno na náměstí.“ · „Aréna přijímá nové bojovníky – hlaste se u velitele cechu.“ · " +
        "„Ztratila se kočka, odpovídá na jméno…“ (jméno je přeškrtnuté)."
    const val FOUNTAIN_TEXT = "⛲ Kašna se sochou zahalené postavy s páskou přes oči. V rukou drží velké zlaté váhy a z obou misek teče voda.\n" +
        "Obě misky jsou v dokonalé rovnováze. Nikdy se nepohnou."
    const val TOWER_TEXT = "⚖️ Věž Řádu Váhy. Na dveřích visí cedulka: „Vstup pouze pro kalibrované subjekty. Žádost zatím neschválena.“"
    const val LIFT_TEXT = "🛗 Železný výtah nad propastí. Klec je zamčená a na cedulce stojí: „SUBJEKT PŘIPRAVEN“ – zatím prázdná kolonka."
    const val SHED_TEXT = "🔒 Malý sklad schovaný za stromy. Na dveřích visí těžký zámek a skrz škvíru je vidět hromada cizích věcí."

    /** Uzly obyvatel přidaných v docs/adr/0101 (id obyvatele = uzel). */
    val RESIDENT_NODES_NEW: Set<String> get() = setOf(CORRA_NODE, OSWIN_NODE, PELL_NODE, PIP_NODE, LIO_NODE, SABETH_NODE)

    /** Všechny uzly, na kterých stojí obyvatel s replikami (ne pekař a Vessa). */
    val RESIDENT_NODES: Set<String> get() = setOf(LIDA_NODE, JARDA_NODE, KORINEK_NODE) + RESIDENT_NODES_NEW

    private val SIBLINGS = listOf(
        "🐟 Pell: „My dva jsme Pell a –“ Pip: „– a Pip. A nikdy se neztratíme, protože –“ Pell: „– jeden druhého vždycky dokončí.“",
        "🐟 Pip: „Hrajeme si na schovávanou.“ Pell: „Já hledám, ona se schovává.“ Pip: „Ale ještě nikdy mě nenašel.“",
        "🐟 Pell: „U kašny je to nejlepší, protože –“ Pip: „– protože socha nevidí. Má pásku přes oči.“"
    )

    /** Pell, když Pip zmizela – mluví na prázdné místo vedle sebe. */
    private val PELL_ALONE = listOf(
        "🐟 Pell: „My dva jsme Pell a…“ Dlouho se dívá na prázdné místo vedle sebe. „…a nikdy se neztratíme.“",
        "🐟 Pell: „Hrajeme si na schovávanou. Já hledám. Už dlouho. Vždycky jsem ji našel, vždycky.“",
        "🐟 Pell: „Někdo mi tu dokončoval věty. Teď je dokončuju sám a nejsou tak hezké.“"
    )

    private val RESIDENT_LINES = mapOf(
        CORRA_NODE to listOf(
            "🧺 Pradlena Corra: „Bílé prádlo, modré prádlo, bílé prádlo. Každé ráno pověsím totéž a každý večer je to zase špinavé. Nevím od čeho.“",
            "🧺 Pradlena Corra: „Ty košilky jsou malé. Na koho asi? Nikdo si pro ně nechodí, a já je stejně peru.“",
            "🧺 Pradlena Corra: „Voda z kašny je čistá, ale prádlo z ní voní popelem. Nikomu to neříkej, ať se neplaší.“"
        ),
        OSWIN_NODE to listOf(
            "🕰️ Hodinář Oswin: „Všechny moje hodiny ukazují 6:12. Nejsou rozbité. Jen se v tu chvíli něco stalo a ony to nechtějí pustit.“",
            "🕰️ Hodinář Oswin: „Kolik je hodin? Šest dvanáct. Vždycky je šest dvanáct. Ale chleba u Branna je pokaždé čerstvý, tak asi plyne aspoň něco.“",
            "🕰️ Hodinář Oswin: „Kdybych je natáhl, rozběhly by se. Bojím se, co by naměřily.“"
        ),
        PELL_NODE to SIBLINGS,
        PIP_NODE to SIBLINGS,
        LIO_NODE to listOf(
            "✉️ Pošťák Lio: „Dopis pro… hm. Zase bez adresy. Polovina mých dopisů je bez adresy a já je stejně nosím.“",
            "✉️ Pošťák Lio: „Kam doručuju dopisy bez adresy? Ke dveřím bez jmen. Sedí to.“",
            "✉️ Pošťák Lio: „Jeden dopis má na obálce jen číslo. Žádné jméno, jen číslo. Kdo by si nechal psát číslem?“"
        ),
        SABETH_NODE to listOf(
            "🎶 Sabeth si broukne melodii, kterou nikdo nezná: „Neptej se, odkud ji mám. Byla tu dřív než já.“",
            "🎶 Sabeth: „Ta písnička má slova. Jen si je nepamatuju. Asi to tak má být.“",
            "🎶 Sabeth: „Když zpívám, popel padá pomaleji. Všiml sis?“"
        ),
        LIDA_NODE to listOf(
            "🍎 Babička Nelle: „Jablíčka ze Ztichlého sadu! … Víš, že jsem dřív vůbec nemluvila? Jednoho rána jsem otevřela pusu " +
                "a vyšla z ní slova. Lekla jsem se tak, že jsem upustila celý košík.“",
            "🍎 Babička Nelle: „Přišli jsme sem všichni skoro najednou. Nikdo nás nevolal. Prostě jsme cítili, že tohle město je naše. Že na nás čeká.“",
            "🍎 Babička Nelle: „Popel? Jaký popel? To je přece jen pyl.“"
        ),
        JARDA_NODE to listOf(
            "🧹 Metař Jarro: „Metu každé ráno. A každé ráno je tu zase stejně.“",
            "🧹 Metař Jarro: „Když jsem poprvé promluvil, myslel jsem, že to říká někdo za mnou. Otočil jsem se. Nikdo tam nebyl.“",
            "🧹 Metař Jarro: „Domy tu stály prázdné, stoly prostřené, v kamnech ještě teplo. Tak jsme se nastěhovali. Kam by se ti lidi asi poděli?“"
        ),
        KORINEK_NODE to listOf(
            "📰 Starý Tobben: „Ozvěnský posel, úterý třináctého. Počasí jasno, ztráty žádné. Výborné zprávy, jako vždycky.“",
            "📰 Starý Tobben: „Číst umím od prvního dne, co jsem tady. Nikdo mě to neučil. Ta písmenka mi prostě… dávala smysl.“",
            "📰 Starý Tobben: „V aréně prý zase někdo vyhrál. Nebo prohrál? Tady stojí obojí.“"
        )
    )

    /** Replika obyvatele pro daný den (střídá se po dnech); pekař má pořád stejnou ([BAKERY_TEXT]). */
    fun residentLine(node: String, dayOfYear: Int, pipGone: Boolean = false): String? =
        (if (node == PELL_NODE && pipGone) PELL_ALONE else RESIDENT_LINES[node])?.let { it[Math.floorMod(dayOfYear, it.size)] }
}

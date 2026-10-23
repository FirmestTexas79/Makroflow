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
        Townsfolk("korinek", 44, KORINEK_NODE, 234, 392)      // Gemak – starý pán s novinami
    )

    val MAP = CaveMap(
        artW = 384, artH = 656,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 192, 108), CaveNode("c_sever", 186, 182), CaveNode(VENKA_NODE, 96, 256),
            CaveNode(BAKERY_NODE, 282, 258), CaveNode("namesti", 124, 336), CaveNode(BOARD_NODE, 116, 284),
            CaveNode(FOUNTAIN_NODE, 192, 368), CaveNode("c_jih", 192, 410), CaveNode(ARENA_NODE, 318, 494),
            CaveNode(TOWER_NODE, 192, 572), CaveNode(LIFT_NODE, 170, 588), CaveNode(SHED_NODE, 352, 560),
            CaveNode(LIDA_NODE, 164, 310), CaveNode(JARDA_NODE, 220, 208), CaveNode(KORINEK_NODE, 252, 398)
        ),
        edges = listOf(
            ENTRY_NODE to "c_sever", "c_sever" to VENKA_NODE, "c_sever" to BAKERY_NODE, "c_sever" to "namesti",
            "namesti" to BOARD_NODE, "namesti" to FOUNTAIN_NODE, "namesti" to "c_jih", "c_jih" to ARENA_NODE,
            "c_jih" to TOWER_NODE, TOWER_NODE to LIFT_NODE, TOWER_NODE to SHED_NODE,
            "namesti" to LIDA_NODE, "c_sever" to JARDA_NODE, "c_jih" to KORINEK_NODE
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
            LIDA_NODE to Triple(150, 296, 12), JARDA_NODE to Triple(206, 196, 12), KORINEK_NODE to Triple(234, 380, 12)
        )
    )

    val ACTION_NODES = setOf(ENTRY_NODE, ARENA_NODE, BAKERY_NODE, VENKA_NODE, BOARD_NODE, FOUNTAIN_NODE, TOWER_NODE, LIFT_NODE, SHED_NODE,
        LIDA_NODE, JARDA_NODE, KORINEK_NODE)

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

    private val RESIDENT_LINES = mapOf(
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
    fun residentLine(node: String, dayOfYear: Int): String? =
        RESIDENT_LINES[node]?.let { it[Math.floorMod(dayOfYear, it.size)] }
}

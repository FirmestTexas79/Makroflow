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

    /** Postava stojící ve městě (sprite npc_<id>_map, pata v art px), klepnutí vede na uzel [node]. */
    data class Townsfolk(val id: String, val node: String, val x: Int, val y: Int)

    /** Obyvatelé (docs/adr/0093). Pekař a Věnka stojí u svých dveří, ostatní po městě. */
    val TOWNSFOLK = listOf(
        Townsfolk("bohousek", BAKERY_NODE, 266, 250), Townsfolk("venka", VENKA_NODE, 92, 252),
        Townsfolk("lida", LIDA_NODE, 152, 306), Townsfolk("jarda", JARDA_NODE, 208, 206),
        Townsfolk("korinek", KORINEK_NODE, 244, 384)
    )

    val MAP = CaveMap(
        artW = 384, artH = 656,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 192, 108), CaveNode("c_sever", 186, 182), CaveNode(VENKA_NODE, 106, 258),
            CaveNode(BAKERY_NODE, 282, 258), CaveNode("namesti", 124, 336), CaveNode(BOARD_NODE, 116, 284),
            CaveNode(FOUNTAIN_NODE, 192, 368), CaveNode("c_jih", 192, 410), CaveNode(ARENA_NODE, 318, 494),
            CaveNode(TOWER_NODE, 192, 572), CaveNode(LIFT_NODE, 170, 588), CaveNode(SHED_NODE, 352, 560),
            CaveNode(LIDA_NODE, 164, 310), CaveNode(JARDA_NODE, 220, 208), CaveNode(KORINEK_NODE, 230, 392)
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
            VENKA_NODE to Triple(98, 234, 24), BAKERY_NODE to Triple(276, 232, 30),
            BOARD_NODE to Triple(116, 262, 10), FOUNTAIN_NODE to Triple(192, 330, 34),
            ARENA_NODE to Triple(318, 458, 40), TOWER_NODE to Triple(192, 520, 24),
            LIFT_NODE to Triple(170, 580, 14), SHED_NODE to Triple(352, 538, 12),
            LIDA_NODE to Triple(152, 300, 8), JARDA_NODE to Triple(208, 200, 8), KORINEK_NODE to Triple(244, 378, 8)
        )
    )

    val ACTION_NODES = setOf(ENTRY_NODE, ARENA_NODE, BAKERY_NODE, VENKA_NODE, BOARD_NODE, FOUNTAIN_NODE, TOWER_NODE, LIFT_NODE, SHED_NODE,
        LIDA_NODE, JARDA_NODE, KORINEK_NODE)

    const val BAKERY_TEXT = "🥖 Pekař Bohoušek: „Dobré ráno, dobré ráno! Čerstvý chléb, jako každé ráno!“\n" +
        "Za ním na pultu leží bochníky. Všechny mají stejný tvar, stejnou kůrku, stejnou prasklinku."
    const val VENKA_TEXT = "✍️ Písařka Věnka: „Á, nová tvář. Počkej, ať si tě zapíšu… Tak. Teď se neztratíš.“\n" +
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
            "🍎 Babička Lída: „Jablíčka, jablíčka! Ze Ztichlého sadu, sladká jako loni.“",
            "🍎 Babička Lída: „Dřív jsem sem chodila se sousedkou. Jak ona se jen jmenovala…? Ale, to nic.“",
            "🍎 Babička Lída: „Popel? Jaký popel? To je přece jen pyl.“"
        ),
        JARDA_NODE to listOf(
            "🧹 Metař Jarda: „Metu každé ráno. A každé ráno je tu zase stejně.“",
            "🧹 Metař Jarda: „Nezlob se, zametám i stopy. Pán z Řádu říkal, že tak je to čistší.“",
            "🧹 Metař Jarda: „Šedá? Ne, ne. Dlažba je bílá. Vždycky byla bílá.“"
        ),
        KORINEK_NODE to listOf(
            "📰 Pan Kořínek: „Ozvěnský posel, úterý třináctého. Počasí jasno, ztráty žádné. Výborné zprávy, jako vždycky.“",
            "📰 Pan Kořínek: „Čtu je znovu, nové zatím nevyšly. Ale tyhle jsou dobré.“",
            "📰 Pan Kořínek: „V aréně prý zase někdo vyhrál. Nebo prohrál? Tady stojí obojí.“"
        )
    )

    /** Replika obyvatele pro daný den (střídá se po dnech); pekař má pořád stejnou ([BAKERY_TEXT]). */
    fun residentLine(node: String, dayOfYear: Int): String? =
        RESIDENT_LINES[node]?.let { it[Math.floorMod(dayOfYear, it.size)] }
}

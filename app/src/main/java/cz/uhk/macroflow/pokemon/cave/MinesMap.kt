package cz.uhk.macroflow.pokemon.cave

/**
 * Doly (docs/adr/0049) – opuštěné hlubinné doly za Starým dolem. Čistá data, pokryto testy.
 *
 * Vstup je v zatáčce Starého dolu (uzel [MAZE_NODE] u „chodba_sever“): zabedněná štola,
 * ze které táhne teplo. Doly mají tři patra:
 *  * nakládací rampa s kolejemi, vozíky a rumpálem (jiskřivky u lucerny, stará síťka na háku),
 *  * puklina, kde výdřeva končí a stěny se lámou do přirozené jeskyně s krystaly (krystalové mušky),
 *  * lávový sál: lávový vodopád padá z pukliny do jezírka a lávová řeka teče přes sál,
 *    přes ni vede kolejový most (magmové mušky). Nahoře zamčené železné dveře.
 *
 * Uzly, hrany i tvary lávy kopíruje tools/mapgen/gen_mines.py – ZDROJ PRAVDY je v obou místech.
 * Tvary lávy jsou tu proto, aby animace v aplikaci ležela přesně na lávě z obrázku.
 */
object MinesMap {
    const val W = 150
    const val H = 540

    // Uzly
    const val EXIT_NODE = "zpet_do_stoly"
    const val NET_NODE = "stara_sitka"
    const val DOOR_NODE = "zelezne_dvere"
    const val SPARK_NODE = "hmyz_jiskrivky"
    const val CRYSTAL_NODE = "hmyz_krystal"
    const val MAGMA_NODE = "hmyz_magma"

    /** Uzel ve Starém dole (zabedněná štola v zatáčce u chodba_sever), odkud se do Dolů vchází. */
    const val MAZE_NODE = "vstup_doly"
    const val MAZE_NODE_X = 26
    const val MAZE_NODE_Y = 269
    /** Oblast klepnutí na zabedněnou štolu v čele stěny Starého dolu. */
    val MAZE_DOOR = Triple(26, 261, 9)

    /** Příznak: stará síťka už je sebraná. */
    const val NET_TAKEN_KEY = "mines_net_taken"
    const val VISITED_KEY = "mines_visited"

    val MAP = CaveMap(
        artW = W, artH = H,
        nodes = listOf(
            // patro 0 – nakládací rampa
            CaveNode(EXIT_NODE, 75, 530),
            CaveNode("nakladiste", 75, 478),
            CaveNode(SPARK_NODE, 38, 470),
            CaveNode(NET_NODE, 112, 490),
            CaveNode("rumpal", 114, 448),
            // patro 1 – puklina do přirozené jeskyně
            CaveNode("prasklina", 75, 368),
            CaveNode(CRYSTAL_NODE, 108, 352),
            CaveNode("puklina", 36, 352),
            // patro 2 – lávový sál
            CaveNode("pata_mostu", 75, 278),
            CaveNode("lavovy_sal", 75, 232),
            CaveNode(MAGMA_NODE, 54, 214),
            CaveNode("popel", 114, 222),
            CaveNode(DOOR_NODE, 75, 130)
        ),
        edges = listOf(
            EXIT_NODE to "nakladiste", "nakladiste" to SPARK_NODE, "nakladiste" to NET_NODE, "nakladiste" to "rumpal",
            "nakladiste" to "prasklina",
            "prasklina" to CRYSTAL_NODE, "prasklina" to "puklina",
            "prasklina" to "pata_mostu",
            "pata_mostu" to "lavovy_sal", "lavovy_sal" to MAGMA_NODE, "lavovy_sal" to "popel", "lavovy_sal" to DOOR_NODE
        ),
        exitNode = EXIT_NODE,
        mountainNode = MAZE_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = setOf("rumpal", "puklina", "popel"),
        parentBiome = "CAVE_MAZE",
        isCave = true,
        tapAreas = mapOf(
            SPARK_NODE to Triple(SPARK_X, SPARK_Y, 11),
            CRYSTAL_NODE to Triple(CRYSTAL_X, CRYSTAL_Y, 11),
            MAGMA_NODE to Triple(MAGMA_X, MAGMA_Y, 11),
            NET_NODE to Triple(120, 476, 10),
            DOOR_NODE to Triple(75, 110, 12)
        )
    )

    /** Uzly, které něco dělají (menší dosah klepnutí jako v jeskyních). */
    val ACTION_NODES = setOf(EXIT_NODE, NET_NODE, DOOR_NODE, SPARK_NODE, CRYSTAL_NODE, MAGMA_NODE)

    // ── Místa s muškami: střed hejna (art px), mušky kolem něj krouží ──
    const val SPARK_X = 30
    const val SPARK_Y = 452
    const val CRYSTAL_X = 118
    const val CRYSTAL_Y = 336
    const val MAGMA_X = 40
    const val MAGMA_Y = 192

    // ── Láva (art px) – stejné tvary kreslí gen_mines.py ──
    /** Lávový vodopád: sloupec padající z pukliny ve skále do jezírka. */
    const val FALL_X0 = 29
    const val FALL_X1 = 37          // včetně
    const val FALL_Y0 = 156
    const val FALL_Y1 = 204
    /** Lávové jezírko pod vodopádem (elipsa). */
    const val POOL_CX = 33f
    const val POOL_CY = 206f
    const val POOL_RX = 11f
    const val POOL_RY = 6f
    /** Stoka z jezírka dolů do řeky. */
    const val CHANNEL_X0 = 29
    const val CHANNEL_X1 = 37
    const val CHANNEL_Y0 = 210
    const val CHANNEL_Y1 = 251
    /** Lávová řeka přes sál (teče doprava a mizí v puklině), přes ni kolejový most. */
    const val RIVER_X0 = 26
    const val RIVER_X1 = 127
    const val RIVER_Y0 = 252
    const val RIVER_Y1 = 261
    const val BRIDGE_X0 = 67
    const val BRIDGE_X1 = 83

    /** Leží art pixel v lávě na podlaze (jezírko, stoka, řeka – ne most)? Vodopád zvlášť. */
    fun isFloorLava(x: Int, y: Int): Boolean {
        val dx = (x + 0.5f - POOL_CX) / POOL_RX; val dy = (y + 0.5f - POOL_CY) / POOL_RY
        if (dx * dx + dy * dy < 1f) return true
        if (x in CHANNEL_X0..CHANNEL_X1 && y in CHANNEL_Y0..CHANNEL_Y1) return true
        if (y in RIVER_Y0..RIVER_Y1 && x in RIVER_X0..RIVER_X1 && x !in BRIDGE_X0..BRIDGE_X1) return true
        return false
    }

    fun isFall(x: Int, y: Int): Boolean = x in FALL_X0..FALL_X1 && y in FALL_Y0..FALL_Y1

    /** Obdélník, ve kterém se láva animuje (x0, y0, x1, y1 včetně). */
    val LAVA_BOUNDS = intArrayOf(RIVER_X0 - 4, FALL_Y0, RIVER_X1, RIVER_Y1)

    /** Text u zamčených dveří – nenápadná stopa Kustodiátu (docs/adr/0047). */
    const val DOOR_TEXT = "Železné dveře zapuštěné přímo do skály. Nemají kliku ani zámek, jen průzor zavařený plechem. " +
        "Do rzi někdo nehtem vyryl: NEKOPEJTE HLOUBĚJ."
    /** S Vhledem ≥ 3 si hráč všimne vybledlého šabloněného nápisu. */
    const val DOOR_TEXT_INSIGHT = "\n\nPod rzí prosvítá vybledlé razítko: S-7 · SEKTOR HLUBINY · VSTUP JEN S DOPROVODEM."

    const val FIRST_VISIT_TEXT = "Za shnilými prkny se otevřela štola s kolejemi. Vzduch je tu teplý a voní sírou – " +
        "odněkud zdola prosvítá rudá záře. Tohle nejsou jen štoly Starého dolu… tohle jsou Doly."

    const val NET_TEXT = "Na rezavém háku u převráceného vozíku visí stará síťka na hmyz. Pár ok je potrhaných, ale obruč drží. " +
        "Kolem lucerny tu poletují žhnoucí mušky – síťka se bude hodit."
    const val NET_GONE_TEXT = "Prázdný rezavý hák. Tady visela stará síťka."
}

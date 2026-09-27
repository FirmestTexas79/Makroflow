package cz.uhk.macroflow.pokemon.story

/**
 * Zapomenutý háj – skrytá lokace a tajná linka příběhu (docs/adr/0046). Čistá data, pokryto testy.
 *
 * Když hráč v kořenech Starého dubu porazí Soulorda, rozpadne se kletba a z trní na západním
 * okraji Hvozdu začnou svítit modré houby. Za nimi vede skrytá stezka do háje, kde bloudí duch
 * Elderana – posledního Strážce brány a Mydrusova učitele. Soulord byla jeho zkažená duše.
 *
 * Nová vrstva příběhu: Rudá hniloba nebyl Drakiřin popel. Drakirra je strážkyně brány a za Branou
 * světů leží Popelavý kraj, kde vládne Pán popela. Probuzením draka pečeť povolila a popel prošel
 * za ní. Hráč bránu právě otevřel – a Elderanův deník je první varování.
 */
object SecretGrove {
    const val QUEST_ID = "secret_grove"

    /** Uzel ve Hvozdu (úzká mezera v trní) a jeho místo na mapě Hvozdu. */
    const val FOREST_NODE = "skryta_stezka"
    const val FOREST_X = 96
    const val FOREST_Y = 312
    /** Modré houby v trní (art px Hvozdu) – svítí jen po uzdravení lesa. */
    val FOREST_MUSHROOMS = listOf(84 to 306, 87 to 314, 82 to 320, 90 to 300)

    // Uzly háje
    const val EXIT_NODE = "vstup_z_hvozdu"
    const val POOL_NODE = "studanka"
    const val ALTAR_NODE = "oltar"
    const val GRAVE_NODE = "hrob"
    val MURAL_NODES = listOf("mural_1", "mural_2", "mural_3")

    // Příznaky příběhu (StoryFlags → synchronizují se)
    const val FOUND_KEY = "grove_found"
    const val VIGIL_KEY = "grove_vigil"
    const val RELEASED_KEY = "grove_released"
    const val MYDRUS_TOLD_KEY = "grove_mydrus_told"
    val KEYS = setOf(FOUND_KEY, VIGIL_KEY, RELEASED_KEY, MYDRUS_TOLD_KEY)

    /** Fáze questu (indexy). */
    const val VIGIL_STAGE = 1
    const val RELEASE_STAGE = 4

    /** Deník – klíčový předmět, odměna za vysvobození Elderana. */
    const val DIARY_ID = "denik_strazce"
    const val DIARY_LABEL = "Deník strážce Elderana"

    /** Do háje se dá vejít až po vyhnání Soulorda (kletba padla, houby svítí). */
    fun canEnter(rotDefeated: Boolean): Boolean = rotDefeated

    /** Duchové se ukazují jen v noci: 21:00–4:59 místního času. */
    fun isNight(hour: Int): Boolean = hour >= 21 || hour < 5

    /** Text u trní ve Hvozdu. */
    fun thornText(rotDefeated: Boolean): String =
        if (rotDefeated) "Mezi trním svítí modré houby. Když se skloníš, uvidíš za nimi úzkou mezeru a z ní vane chladný vzduch, který voní mechem a kamenem…"
        else "Neprostupné trní. Větve jsou tu tak husté, že by jimi neprolezla ani myš."

    /** Obrazy na kamenech kruhu: název, drawable (jméno) a text. */
    data class Mural(val node: String, val title: String, val drawable: String, val text: String)

    val MURALS = listOf(
        Mural("mural_1", "Kámen prvního obrazu", "mural_1",
            "Do kamene je vytesaný prstenec mezi dvěma světy: vlevo stromy Hvozdu, vpravo hora, ze které šlehá oheň. " +
                "Nad prstencem krouží drak.\n\nPod obrazem stojí runy: „Bránu nepostavili druidi. Postavili ji draci, " +
                "aby světy mohly dýchat spolu.“"),
        Mural("mural_2", "Kámen druhého obrazu", "mural_2",
            "Dva druidi s parožím drží nad zkříženou bránou zářící semeno. Vpravo spí na hoře stočený drak " +
                "a vedle něj leží dva krystaly – modrý a červený.\n\nRuny: „Pečeť drží tři: spící drak, dva krystaly " +
                "a Srdce Hvozdu. Kdo vloží Srdce do lůžka, bránu otevře.“"),
        Mural("mural_3", "Kámen třetího obrazu", "mural_3",
            "Za branou hoří oči pod korunou z uhlíků. Z koruny padá popel na stromy. Pod dubem stojí druid " +
                "a jeho světlo se vpíjí do kořenů.\n\nRuny jsou tu vyryté hlouběji, jako by je někdo tesal ve spěchu: " +
                "„Ten, který spaluje, se vrátí. Strážce zůstane v kořenech, dokud bude třeba.“")
    )

    fun mural(node: String): Mural? = MURALS.firstOrNull { it.node == node }

    const val POOL_TEXT = "Ve studánce se neodráží tvoje tvář, ale cizí nebe. Hvězdy tu tvoří obraz parohů – " +
        "a nad nimi, na samém okraji hladiny, doutná malý rudý bod, jako by se na tebe někdo díval zpoza mraků."

    const val GRAVE_TEXT = "Hrobový kámen s vytesaným parožím. Jméno zarostlo mechem, dá se přečíst jen konec: „…ran, " +
        "Strážce brány. Zůstal, aby ostatní mohli odejít.“"

    const val ALTAR_DAY_TEXT = "Oltář je studený a tichý. Svítí jen runy parohů. Duchové se prý ukazují až po setmění…"

    /** Stránky deníku (titulek, text). Otevírají se z batohu. */
    val DIARY_PAGES = listOf(
        "O Bráně" to "Bránu světů postavili draci, ne my. Spojuje Hvozd s Popelavým krajem za mraky – s krajem sopek, " +
            "řek a měst, kde se kdysi Makromoni obou světů volně potkávali. Drakirra je poslední z drakobudovatelů.",
        "O pečeti" to "Když přišel popel, zapečetili jsme bránu. Pečeť drží tři věci: spícího draka, dva krystaly " +
            "a Srdce Hvozdu. Kdo vloží Srdce do lůžka, bránu otevře. Kdo ho vyjme, zavře ji – ale pečeť už nikdy nebude celá.",
        "O Pánu popela" to "Nemá tvář, jen korunu z uhlíků. Kde projde, tráva zčerná a Makromoni zapomenou, kým jsou. " +
            "Hniloba je jeho semínko. Bojí se jen dvou věcí: živé vody a srdce, které tluče pro les.",
        "Runy brány" to "Runy na prstenci brány říkají: KDO OTEVŘE, AŤ HLÍDÁ. POPEL ČEKÁ ZA MRAKY.",
        "Poslední zápis" to "Mydrusi, jestli tohle čteš: les je teď tvůj. Nevyčítej si nic – ani to, že jsi se mnou bojoval, " +
            "až ze mě hniloba udělá něco jiného. Věděl jsem, že to přijde. Byl jsem na tebe vždycky pyšný. — E."
    )

    /** Řádek na tabuli Brány světů, když hráč zná Elderanovo tajemství. */
    const val GATE_RUNES_LINE = "Runy na prstenci teď dokážeš přečíst: „KDO OTEVŘE, AŤ HLÍDÁ. POPEL ČEKÁ ZA MRAKY.“"

    /** Mydrus, když se dozví, co se stalo s jeho učitelem (jednou, po vysvobození). */
    const val MYDRUS_CLOSURE = "Počkej… voníš po starém mechu a kameni. Ty jsi byl v zapomenutém háji? U mistra Elderana?\n\n" +
        "Takže Soulord… to byl on. Celou dobu. A já s ním bojoval a nepoznal ho.\n\n" +
        "Děkuji, že jsi ho pustil. Teď už vím, proč Hvozd v noci zpívá. A jestli je za branou opravdu Pán popela… " +
        "budu hlídat les. Ty hlídej bránu."

    /** Nápověda ukrytá v rozloučení Mydruse (jediná stopa k háji). */
    const val MYDRUS_HINT = "Jo, a ještě něco: od té doby, co hniloba odešla, prý na západě lesa, kousek od tiché cesty, " +
        "svítí v trní modré houby. Mistr Elderan kdysi říkal, že modré houby rostou jen tam, kde odpočívá duše…"

    /** Ikona deníku 16×16: kožená kniha se svítícím parožím na deskách a záložkou. */
    const val ICON = 16

    fun diaryIcon(): IntArray {
        val rows = listOf(
            "................",
            "..oooooooooooo..",
            "..oLddddddddpo..",
            "..oLdddddddd po.".replace(' ', 'd'),
            "..oLddgddgddpo..",
            "..oLdddggdddpo..",
            "..oLddgddgddpo..",
            "..oLdddggdddpo..",
            "..oLddddgdddpo..",
            "..oLddddgdddpo..",
            "..oLddddddddpo..",
            "..oLddddddddpo..",
            "..oooooooooooo..",
            "...pppppppppp...",
            "........r.......",
            "........r......."
        )
        val pal = mapOf(
            'o' to 0xFF1A0E08.toInt(), 'd' to 0xFF5A3420.toInt(), 'L' to 0xFF8A5A34.toInt(),
            'p' to 0xFFE8DCC0.toInt(), 'g' to 0xFF6AF0E0.toInt(), 'r' to 0xFFC03040.toInt()
        )
        val out = IntArray(ICON * ICON)
        rows.forEachIndexed { y, r -> r.forEachIndexed { x, ch -> out[y * ICON + x] = pal[ch] ?: 0 } }
        return out
    }

    /** Kde stojí v háji duch Elderana (art px háje, střed paty). */
    val GHOST_POS = 80 to 80
}

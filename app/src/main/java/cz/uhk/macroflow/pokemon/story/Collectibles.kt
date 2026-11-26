package cz.uhk.macroflow.pokemon.story

/**
 * Sběratelské předměty se vedlejšími úkoly v zónách 1 a 2 a ve Věži (docs/adr/0109). Každá sada: šest předmětů v šesti
 * lokacích, na mapě leží jako malý sprite se zářičkou, klepnutí = dojít a sebrat (animace sebrání, panel). První sebraný
 * předmět spustí úkol; fáze 1 počítá příznaky (FLAGS_COUNT), fáze 2 je návyk nebo předání.
 *  - Kuchařka krále Mlsáka (zóna 1): stránky receptů → 2 snídaně. Hlad jako fasáda nad Hladomírem, nic neodměňuje hladovění.
 *  - Nedoručené dopisy (zóna 2): pošťák Lio ztratil brašnu → vrátit dopisy Liovi, poslední je pro 213 od 212.
 *  - Spánkové deníky (Věž): deníky okruhů 206–211, které Hunfrey vynesl z Mrazírny → jedna noc aspoň 7 h.
 */
object Collectibles {
    enum class Kind { PAGE, LETTER, LOG }

    /** Předmět: sada, lokace, uzel (sprite leží o [dx] art px vedle, pata o [dy] níž), příznak, text po sebrání. */
    data class Item(val set: String, val biome: String, val node: String, val key: String, val text: String, val dx: Int = 9, val dy: Int = -1)

    /** Sada: id úkolu, druh spritu, popisek do animace a panelu, barva zářičky. */
    data class Set(val questId: String, val kind: Kind, val label: String, val icon: String, val glow: Long, val items: List<Item>) {
        val keysCsv get() = items.joinToString(",") { it.key }
    }

    // ── Kuchařka krále Mlsáka ──
    const val COOKBOOK = "kucharka_krale_mlsaka"
    val COOKBOOK_SET = Set(COOKBOOK, Kind.PAGE, "Stránka kuchařky", "📜", 0xFFFFF2B0, listOf(
        Item(COOKBOOK, "TOWN", "krizovatka_hlavni", "kucharka_1",
            "OVESNÁ KAŠE DO MĚSTA\nHrnek vloček, mléko, hrst bobulí, lžička medu.\n\nNa okraji královským písmem: „Jíst vsedě. Nikam nespěchat. Město počká.“"),
        Item(COOKBOOK, "MEADOW", "cesta_sever", "kucharka_2",
            "LUČNÍ OMELETA\nTři vejce, pažitka z louky, kousek sýra.\n\nNa okraji: „Bílkoviny jsou cihly. Z cihel se staví hrad. Z hladu se nestaví nic.“"),
        Item(COOKBOOK, "MOUNTAINS", "zapadni_stezka", "kucharka_3",
            "HORSKÁ POLÉVKA PRO VOJSKO\nFazole, brambory, kořenová zelenina, kus masa. Vařit dlouho, ve velkém kotli.\n\nNa okraji: „Pro každého v řadě. I pro posledního. Hlavně pro posledního.“"),
        Item(COOKBOOK, "CAVE_OPEN", "terasa", "kucharka_4",
            "JESKYNNÍ HOUBY NA MÁSLE\nHouby, máslo, sůl, tymián.\n\nNa okraji kostrbatě, jinou rukou: „Jen ty, co svítí slabě. Nikdy nejíst nic, co svítí moc.“"),
        Item(COOKBOOK, "CAVE_MAZE", "stola_vstup", "kucharka_5",
            "HORNICKÝ CHLÉB\nMouka, voda, sůl a čas. Hodně času.\n\nNa okraji: „Chléb se láme, nekrade.“ Někdo to podtrhl tak silně, že se papír protrhl."),
        Item(COOKBOOK, "FOREST", "f_mid", "kucharka_6",
            "LESNÍ JAHODY S MEDEM\nHrst jahod, lžíce medu, nic víc.\n\nNa okraji: „Všechno nesníst. Nechat něco pro les.“\nÚplně dole, vybledle: „Armáda čeká na příděly. Zítra. Zítra určitě.“")
    ))
    const val COOKBOOK_NAME = "Král Mlsák"
    const val COOKBOOK_INTRO = "„Hohó! To je moje kuchařka!“ Král Mlsák by tleskal, kdyby socha mohla tleskat.\n\n" +
        "„Vítr mi ji rozfoukal po celém kraji – po městě, po louce, po horách, v jeskyni, ve starém dole i ve Hvozdu. Šest stránek! " +
        "Bez nich nevím, co si dát k snídani. A král bez snídaně je hladový král. A hladový král je to nejhorší, co se může stát. Věř mi.“"
    const val COOKBOOK_INTRO_HINT = "Najdi 6 stránek kuchařky: Město, Louka, Hory, jeskyně, Starý důl a Hvozd – třpytí se u cest."
    const val COOKBOOK_BREAKFAST = "„Všech šest! Teď to nejdůležitější: podle kuchařky se nečte. Podle kuchařky se vaří.“\n\n" +
        "„Dvě snídaně, dva dny. Král nejí sám – a ty taky ne.“"
    const val COOKBOOK_BREAKFAST_HINT = "Zapiš si snídani (jídlo mezi 4:00 a 10:30) ve 2 různých dnech."
    const val COOKBOOK_FAREWELL = "Král Mlsák by si kuchařku přitiskl k břichu, kdyby mohl. „Víš, co stojí na úplně poslední stránce? Tu jsem nikdy nečetl nahlas.“\n\n" +
        "Drobným, starým písmem: AŤ UŽ NIKDY NIKDO NEMÁ HLAD. — H.\n\n„Nevím, kdo je H.,“ řekne král a usměje se trochu moc široce. „Ale souhlasím s ním.“"

    // ── Nedoručené dopisy ──
    const val LETTERS = "ozvena_nedorucene_dopisy"
    val LETTERS_SET = Set(LETTERS, Kind.LETTER, "Dopis", "✉️", 0xFFF0E0C0, listOf(
        Item(LETTERS, "ASH_PLAIN", "sutiny", "dopis_1", "Šedá obálka bez adresy, zapečetěná voskem s otiskem malé dlaně. Uvnitř šustí sušený květ."),
        Item(LETTERS, "QUIET_ORCHARD", "ule", "dopis_2", "Obálka voní jablky. Místo adresy je na ní nakreslený strom – a pod ním jméno, které popel skoro smazal."),
        Item(LETTERS, "ECHO_CANYON", "rimsa", "dopis_3", "Dopis zaklíněný ve skále. Když ho vytáhneš, kaňon zopakuje šustnutí papíru ještě třikrát."),
        Item(LETTERS, "OZVENA", "namesti", "dopis_4", "Obálka s razítkem pošty Ozvěny. Datum na razítku je pořád stejné – den, kdy hodiny ve městě zastavily na 6:12."),
        Item(LETTERS, "OZVENA", "c_jih", "dopis_5", "Silná obálka, převázaná provázkem. Na rubu dětským písmem: PRO MAMINKU. Adresa chybí."),
        Item(LETTERS, "ARENA_HALL", "stred", "dopis_6", "Obálka bez jména – jen číslo, napsané úhledně, jako by to psal kartograf: 213.")
    ))
    const val LETTERS_NAME = "Lio"
    const val LETTERS_LOST = "Pošťák Lio: „Brašna! Cestou z pláně mi praskl popruh a dopisy se rozletěly – po pláni, po sadu, po kaňonu, " +
        "po náměstí, až do Arény. Šest dopisů. Bez adres, jak jinak. Ale já je doručím. Já je vždycky doručím.“"
    const val LETTERS_LOST_HINT = "Najdi 6 dopisů: Popelavá pláň, Ztichlý sad, Ozvěnový kaňon, dvakrát Ozvěna a Aréna."
    const val LETTERS_ALL = "Máš všech šest. Obálky jsou šedé od popela. Lio čeká u severní cesty v Ozvěně."
    const val LETTERS_ALL_HINT = "Vrať dopisy pošťákovi Liovi v Ozvěně."
    const val LETTERS_DELIVER = "Lio dopisy rovná do brašny a u každého ukáže na dům: „Tenhle s modrými okenicemi. Tenhle vedle pekárny. Tenhle u kašny…“\n\n" +
        "Pokaždé ukáže na dům, kde nikdo nebydlí. Dveře bez jmen.\n\n" +
        "Pak vytáhne poslední. Na obálce jen číslo: 213. Dlouho se na tebe dívá. „Tohle jsi ty, že jo.“\n\n" +
        "Uvnitř úhledné písmo kartografa:\n„Okruhu 213. Jestli tohle čteš, došel jsi dál než já. Nevěř vahám. Věř posteli. Jez, i když se ti nechce. A vždycky se vrať. – 212“"
    const val LETTERS_BOARD = "Pošťák tu není. Dopisy připíchneš na vývěsku, ať si je najde, kdo je má najít. Ten s číslem si necháš.\n\n" +
        "Uvnitř úhledné písmo kartografa:\n„Okruhu 213. Jestli tohle čteš, došel jsi dál než já. Nevěř vahám. Věř posteli. Jez, i když se ti nechce. A vždycky se vrať. – 212“"
    const val LETTERS_FINAL_KEY = "dopisy_doruceny"
    const val LETTERS_FINAL_STAGE = 1
    const val LETTERS_FAREWELL = "Lio nosí dál dopisy bez adres. „Někdo je přece musí nosit. Jinak by to vypadalo, že nikdo nikomu nepíše.“"

    // ── Spánkové deníky ──
    const val LOGS = "vez_spankove_deniky"
    val LOGS_SET = Set(LOGS, Kind.LOG, "Spánkový deník", "📘", 0xFFB8D0FF, listOf(
        Item(LOGS, "TOWER_MEASURE", "meri_stred", "denik_206",
            "SPÁNKOVÝ DENÍK – OKRUH 206\nTýden 1: 8 h · 8 h · 7 h. Poznámka Řádu: STABILNÍ.\nTýden 9: 4 h · 4 h · 3 h. Poznámka Řádu: VYHOVUJÍCÍ."),
        Item(LOGS, "TOWER_PLAIN", "plan_stred", "denik_207",
            "SPÁNKOVÝ DENÍK – OKRUH 207\nDen 1: spí venku pod širákem, 9 h. Den 40: „subjekt hlídá“, 3 h.\nRazítko: VHODNÝ."),
        Item(LOGS, "TOWER_FOREST", "les_stred", "denik_208",
            "SPÁNKOVÝ DENÍK – OKRUH 208\nKřivka spánku klesá jako svah. Na okraji Hunfreyho tužka: „Všechny jdou dolů. Žádná nahoru.“"),
        Item(LOGS, "TOWER_ROCK", "hora_stred", "denik_209",
            "SPÁNKOVÝ DENÍK – OKRUH 209\nŘádky vyplněné do dne 61. Pak už jen razítko: PŘEVEDEN – MRAZÍRNA, KOMORA 209."),
        Item(LOGS, "TOWER_CAVE", "jes_stred", "denik_210",
            "SPÁNKOVÝ DENÍK – OKRUH 210\nDo políček někdo kreslil sny: strom, oko, strom, šest očí. Řád připsal červeně: SNY NEEVIDOVAT."),
        Item(LOGS, "TOWER_WATER", "zat_stred", "denik_211",
            "SPÁNKOVÝ DENÍK – OKRUH 211\nDeník je promočený, čitelný jen poslední zápis: „Když spím, slyším, jak dole něco dýchá. Tak radši nespím.“\nRazítko: KALIBROVÁN.")
    ))
    const val LOGS_NAME = "Hunfrey"
    const val LOGS_FOUND = "Pod deskou deníku je přilepený papírek Hunfreyho rukou:\n\n„Kdo tohle čte, je 213. Schoval jsem po patrech spánkové deníky " +
        "okruhů 206 až 211 – co jsem stihl vynést z Mrazírny. Najdi je. Uvidíš, jak se tu z lidí dělají nádoby: ne hladem. Nevyspáním.“"
    const val LOGS_FOUND_HINT = "Najdi 6 spánkových deníků na patrech Věže 2–7."
    const val LOGS_SLEEP = "Šest deníků, šest křivek, které jdou dolů. Na zadní straně posledního Hunfrey:\n\n" +
        "„Teď ty. Jedna noc, aspoň sedm hodin. Ať máš v deníku aspoň jeden řádek, který jim nepatří.“"
    const val LOGS_SLEEP_HINT = "Vyspi se jednu noc aspoň 7 hodin (hodinky nebo ruční zápis spánku)."
    const val LOGS_FAREWELL = "Do prázdného deníku napíšeš první řádek. Číslo 213 škrtneš a napíšeš své jméno. Spánek se nedá evidovat. Dá se jen mít."

    val SETS = listOf(COOKBOOK_SET, LETTERS_SET, LOGS_SET)
    val ITEMS = SETS.flatMap { it.items }
    val KEYS: kotlin.collections.Set<String> = ITEMS.map { it.key }.toSet() + LETTERS_FINAL_KEY

    fun set(id: String) = SETS.first { it.questId == id }
    fun inBiome(biome: String) = ITEMS.filter { it.biome == biome }
    fun at(biome: String, node: String) = ITEMS.firstOrNull { it.biome == biome && it.node == node }
    fun intro(set: String) = when (set) { COOKBOOK -> COOKBOOK_INTRO; LETTERS -> LETTERS_LOST; else -> LOGS_FOUND }
}

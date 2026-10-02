package cz.uhk.macroflow.pokemon.story

/**
 * Mechaniky Kustodiátu z bible příběhu (docs/adr/0057). Čistá logika a texty, pokryto testy.
 *
 * * **Spisy** – klinické záznamy Řádu Váhy u každého druhu v Makrodexu. Ukážou se až
 *   s Vhledem [Dossiers.MIN_INSIGHT]; začerněná místa `{n|…}` se odkrývají jako u listů.
 *   Místa s `{99|…}` zůstanou černá celý akt I – pravda o Makromonech až na konci.
 * * **Sny** – ráno po spánku se občas objeví hlášení o snech personálu.
 * * **Mydrus zapomíná** – po vyhnání hniloby čas od času zapomene i poutníka.
 * * **Probuzení** – po smrti postavy Gudwin vítá poutníka doma; počítá to.
 */
object Dossiers {
    /** Od jakého Vhledu se v Makrodexu objeví tlačítko se spisem. */
    const val MIN_INSIGHT = 2

    /** Pozorování a poznámka podle druhu (číslo z Makrodexu). */
    private fun notes(id: String): Pair<String, String> = when (id) {
        "001", "002", "003" -> "Tělesná teplota roste se zátěží subjektu. {3|Reaguje na zápis tréninku dřív než subjekt sám.}" to
            "{5|Plamen nehoří palivem. Hoří něčím, co si pamatuje.}"
        "004", "005", "006" -> "Objevuje se po splnění vodního cíle subjektu. {3|Měřeno s přesností na 50 ml.}" to
            "{4|Kalibrační druh č. 2.} {6|Voda je jediná věc, kterou subjekt nemůže předstírat.}"
        "007", "008", "009" -> "Staví si z listí domky se dvěma okny." to
            "{4|Půdorys domků odpovídá stavbám, které v regionu nikdy nestály.} {99|Lumenská obydlí, typ B.}"
        "010", "011" -> "Vzniká z nahromaděného smutku nad ztraceným parťákem." to
            "{4|Jediný pár druhů, který chyběl při prvním sčítání.} {6|Vznikl až po něm. Někdo tedy truchlí dál.}"
        "012" -> "Klasifikace: NÁDOBA, pokus č. {4|2}. Vazba na tělo subjektu: {3|aktivní}." to
            "{5|Pokus č. 1 ztracen (viz spis 019).} {6|Nepřipustit opakování. Kalibraci vést pozvolna.}"
        "013", "014", "015", "016", "017", "018" -> "Forma nádoby po kalibraci podle návyků subjektu." to
            "{4|Kalibrace proběhla podle protokolu.} {5|Stabilita nižší než u formy 019.}"
        "019" -> "SPIS UZAVŘEN. {4|Pokus č. 1.}" to
            "{5|Přetvořen bez souhlasu Řádu.} {6|Oči odebrány a uloženy v sektorech S-7 a S-2.} {99|Viz sen D-7.}"
        "020", "021" -> "Běžný druh. Nevykazuje nic." to
            "{5|Při nočním pozorování plave v kruzích, vždy po směru hodinových ručiček.}"
        "022" -> "Kolonie na okrajích lesů a luk. Imunní vůči výparům, které sama vyrábí." to
            "{4|Kolonie si staví domky.} {99|Shodné s lumenskou architekturou.}"
        "023" -> "Druid. Vůdce kolonie. {3|Ztrácí paměť.}" to
            "{4|Nezasahovat.} {5|Zapomnění je v tomto případě milosrdné.}"
        "024", "025", "026" -> "Duše v obleku. Oblek si nosí sama." to
            "{6|Oblek je šitý na postavu, která chodí po dvou.}"
        "027", "028", "029" -> "Toulá se v noci u vodních ploch." to
            "{5|Plave proti proudu k místu, kde kdysi stávalo město.}"
        "030" -> "Zdroj G. O. Spolupracuje. {3|Nemůže zemřít.}" to
            "{4|Divocí jedinci jsou jeho prázdné skořápky.} {5|Žádá o výjimky pro subjekty. Vždy zamítnuto.}"
        "031" -> "Nezařazen." to
            "{4|Objevuje se jen u subjektů s vysokou disciplínou.} {6|Řád ho neregistroval. Zapsal se sám.}"
        "032" -> "Strážce. Hlídá rudý krystal. Neopouští hlubinu." to
            "{4|Koruna z plamenů není jeho.} {5|Byla mu nasazena při Přání.} {99|Nosič čeká na vystřídání.}"
        "033" -> "Horský druh. Kámen na zádech není součástí těla." to
            "{4|Kámen si vybírá sám a nikdy ho nemění.} {5|Úlomky odpovídají zdivu u Brány světů.}"
        else -> "Bez záznamu." to "{4|Spis neúplný.}"
    }

    /** Text spisu (se začerněnými místy – vykreslí [Insight.render]). */
    fun text(id: String, name: String, caught: Int): String {
        val (seen, note) = notes(id)
        return "ŘÁD VÁHY · STANICE {2|7}\n" +
            "SPIS $id — ${name.uppercase()}\n" +
            "Původ: {99|lumenský národ, před Přáním}\n" +
            "Odchyceno subjektem: ${caught}×\n\n" +
            "Pozorování: $seen\n\n" +
            "Poznámka: $note"
    }

    fun visible(insight: Int): Boolean = insight >= MIN_INSIGHT
}

object Dreams {
    const val PREFIX = "dream_"
    /** Od jakého Vhledu se zdají sny. */
    const val MIN_INSIGHT = 3
    /** Šance, že se po spánku zdá sen (když ještě nějaký zbývá). */
    const val CHANCE = 40

    data class Dream(val id: Int, val who: String, val text: String) {
        val key: String get() = PREFIX + id
    }

    val ALL = listOf(
        Dream(1, "Pracovník L-3", "Zdálo se mi, že vážím chleba. Pořád vážil víc, než měl. {3|Vzbudil jsem se s hladem, jaký jsem nikdy necítil.}"),
        Dream(2, "Pracovnice K-12", "Zase ten strom. Roste dolů. {4|Je na něm přibité něco s mnoha pažemi.} {5|Pořád se na mě dívá.}"),
        Dream(3, "Pracovník D-7", "Byl jsem veverka. Seděl jsem u kořenů a čekal, až mě někdo vystřídá. {5|Nikdo nepřišel. Pak přišlo světlo a já jsem rostl.}"),
        Dream(4, "Pracovník M-1", "Zelený medvídek mi vyprávěl o všech, kdo se tu probudili na prahu. Jmenoval je jednoho po druhém. " +
            "{4|Došel ke dvě stě dvanácti.} {5|Pak řekl moje jméno.}"),
        Dream(5, "Pracovnice K-12", "Šest očí. Šest rukou. Jedna z nich ukazuje na mě. {5|„Vytáhni kopí.“} {6|Přeložte mě. Prosím.}"),
        Dream(6, "Pracovník B-0", "Zdálo se mi o městě, kde lidé svítili. Pak zhasli. {6|Všichni najednou.} Ráno jsem nevěděl, jak se jmenuju."),
        Dream(7, "Neznámý", "{3|Tvůj} sen. Stojíš u kořenů a vedle tebe leží drak. {6|Nebojuj s ní.} {99|Ona jediná…}")
    )

    fun dreamed(flags: Set<String>): List<Dream> = ALL.filter { it.key in flags }

    /** Je dnes ráno po spánku (4–12 h, aspoň [minSleepH] h mimo hru), sen se dnes ještě nezdál a Vhled stačí? */
    fun eligible(insight: Int, hour: Int, awaySec: Long, alreadyToday: Boolean, minSleepH: Int = 5): Boolean =
        insight >= MIN_INSIGHT && !alreadyToday && hour in 4..11 && awaySec >= minSleepH * 3600L

    /** Další sen v pořadí, pokud je ráno [eligible] a padne náhoda [CHANCE] % ([roll] 0–99). */
    fun next(flags: Set<String>, insight: Int, hour: Int, awaySec: Long, alreadyToday: Boolean, roll: Int, minSleepH: Int = 5): Dream? {
        if (!eligible(insight, hour, awaySec, alreadyToday, minSleepH) || roll >= CHANCE) return null
        return ALL.firstOrNull { it.key !in flags }
    }
}

object MydrusMemory {
    /** Uzly, kde poutník Mydrusovi připomíná, co spolu zažili (stejné jako první fáze questu). */
    val PLACES = listOf("jezirko_1", "houstina", "stary_dub")

    /** Za kolik dní po posledním připomenutí zase zapomene: poprvé za 2, pak 4, 3, 2, 2… */
    fun interval(timesForgotten: Int): Int = if (timesForgotten == 0) 2 else maxOf(2, 5 - timesForgotten)

    fun forgets(rotDefeated: Boolean, today: Long, rememberedDay: Long, timesForgotten: Int): Boolean =
        rotDefeated && rememberedDay > 0 && today - rememberedDay >= interval(timesForgotten)

    /** Mydrus tě nepoznává – začíná stejně jako při prvním setkání. */
    fun stranger(times: Int): String = when (times) {
        0 -> "Pst… nelekej se. Jsem Mydrus, poslední druid Hvozdu.\n\n…Proč se tak díváš? Měli bychom se znát? " +
            "Promiň, dneska mám v hlavě mlhu. Jestli jsme spolu něco zažili, ukaž mi to. Jezírko, houština, starý dub… tam si pamatuju nejvíc."
        1 -> "Pst… nelekej se. Jsem… Mydrus. Druid.\n\nZase ten pohled. Takže jsme se už potkali, viď? " +
            "Provedeš mě znovu? Jezírko, houština, dub."
        else -> "Pst… Ty. Ten pohled znám, i když tebe ne.\n\nNapsal jsem si na kůru: „Když přijde někdo, kdo se dívá smutně, jdi s ním k jezírku, do houštiny a k dubu.“ Tak pojď."
    }

    /** Vzpomínka, kterou místo vrátí. */
    fun fragment(place: String): String = when (place) {
        "jezirko_1" -> "Mydrus se skloní k hladině. „Tady… tady jsi pil vodu, abys mi ukázal, že se pramen dá probudit. Pamatuju si chuť.“"
        "houstina" -> "Mydrus se dotkne padlého kmene. „Tady jsme se bránili Zdivočelým. Stál jsi přede mnou. Proč jsi stál přede mnou?“"
        else -> "Mydrus položí tlapku na kůru Starého dubu. „Soulord. Ty jsi ho vyhnal. Já… já jsem ho nepoznal. Někoho jsem nepoznal.“"
    }

    fun remembered(times: Int): String = when (times) {
        0 -> "Ach… ty. Promiň. Promiň mi to. Hniloba je pryč, ale díry po ní ve mně zůstaly.\nKdyž zapomenu znovu, přijdeš?"
        1 -> "Ty. Zase jsi přišel. Začínám si pamatovat aspoň to, že na tebe zapomínám. To je asi pokrok."
        else -> "Vím, kdo jsi. Teď. Kéž bych to věděl i zítra.\nMycité říkají, že pokaždé, když odejdeš, se dívám na cestu."
    }
}

object Wakeups {
    const val COUNT_KEY = "whiteout_count"

    /** Co Gudwin řekne, když se poutník po smrti probudí na prahu ([count] = kolikáté probuzení). */
    fun gudwin(count: Int, insight: Int, roll: Int): String = when {
        count <= 1 -> "Ty jsi vzhůru! Našel jsem tě na prahu, celého od prachu. Tým je v pořádku, odpočinuli si.\n…Lehni si ještě chvíli, ano?"
        count == 2 -> "Zase práh. Zase prach. Neboj, tým je v pořádku.\nJá jsem tu vždycky, když se probudíš."
        insight >= 5 && roll < 30 -> "Dvě stě dvanáct poutníků přede mnou se probouzelo tady na tom prahu.\n…Ty se aspoň pořád vracíš. Tým je v pořádku."
        insight >= 3 -> "Tohle je tvoje $count. probuzení. Počítám je všechny, víš? Každého, kdo tu kdy ležel.\nPromiň. Zvyk. Tým je v pořádku."
        else -> "Tohle už je… $count. Počítám to. Promiň – zvyk.\nTým je v pořádku. Ty už taky?"
    }
}

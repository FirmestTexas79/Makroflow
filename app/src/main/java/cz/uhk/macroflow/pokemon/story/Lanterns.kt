package cz.uhk.macroflow.pokemon.story

/**
 * Vedlejší úkol „Cesta zpátky“, zóna 3 (docs/adr/0108). Propojuje Hlubinu od Stanice 8 až po Les pod korunou: výpravy si
 * na cestě dolů věšely lucerny, aby trefily zpátky nahoru – a všechny zhasly. Kesh (Signalista) po odpovědi kmene
 * zjistí, že světlomet na Béčku nestačí, a dá hráči svou starou lucernu výpravy 7. Hráč zapálí sedm zhaslých luceren
 * v sedmi lokacích (sběratelské předměty, na mapě zůstanou svítit) a vrátí se ke Keshovi: ze skály je vidět rozsvícená
 * cesta od lesa až ke Stanici 8. Úkol nic neodměňuje za nespaní ani hladovění – lucerny jsou o tom vracet se nahoru.
 * Kesh nesl lucernu výpravy 7 – jako Asko z Gudwinovy jmenovky. Hra to nevysvětluje (odhalení patří až konci).
 */
object Lanterns {
    const val QUEST_ID = "hlubina_cesta_zpatky"
    const val NAME = "Kesh"

    const val STARTED_KEY = "lucerny_start"
    const val FINAL_KEY = "lucerny_cesta"
    const val FINAL_STAGE = 1

    /** Zhaslá lucerna: lokace, uzel na mapě (lucerna visí kousek vedle), příznak, text po zapálení. */
    data class Lantern(val biome: String, val node: String, val key: String, val lit: String)

    val ALL = listOf(
        Lantern("ROOT_CAMP", "kt_dole", "lucerna_stanice8",
            "Lucerna u schodů dolů. Na skle je vyškrábáno V1 – tudy vedla velitelka Ardis první výpravu. Knot se chytne hned, jako by čekal."),
        Lantern("ROOT_WATCH", "pb_stred", "lucerna_pozorovatelna",
            "Lucerna na háku u bunkru. Pod ní v betonu otisky malých tlapek – stejné jako na kličce světlometu. Plamen se natáhne ke schodům nahoru."),
        Lantern("ROOT_BRIDGE", "km_kotva", "lucerna_most",
            "Lucerna přivázaná ke kotvě přeseknutého lana. Na rukojeti tužkou: „ORRIN – NOČNÍ SMĚNA“. Svítí přes propast zpátky na útes."),
        Lantern("ROOT_SLOPECAMP", "tv_most", "lucerna_svah",
            "Lucerna na tyči s praporkem, který trčí z kořene. Když se rozsvítí, kořen pod lávkou na chvíli přestane tepat."),
        Lantern("ROOT_NAMELESS", "sb_kolik", "lucerna_bezejmenni",
            "Lucerna na měřicím kolíku. Světlo dopadne na štítky na kotnících – jen na chvíli, ale tak, že je jde přečíst. Nepočítáš je. Jen se díváš."),
        Lantern("ROOT_TEMPLE", "ch_lod", "lucerna_chram",
            "Lucerna Řádu v lodi chrámu, jediná věc tady, která sem nepatří. Modré jiskry od oltáře se k plameni slétnou a zase odletí."),
        Lantern("ROOT_FOREST", "lp_cesta2", "lucerna_les",
            "Lucerna Řádu mezi papírovými lucernami. Když chytne, rozsvítí se i ty nejbližší z papíru – jedna po druhé, směrem nahoru.")
    )
    val KEYS_CSV = ALL.joinToString(",") { it.key }
    val KEYS = setOf(STARTED_KEY, FINAL_KEY) + ALL.map { it.key }

    fun at(biome: String, node: String) = ALL.firstOrNull { it.biome == biome && it.node == node }
    fun inBiome(biome: String) = ALL.filter { it.biome == biome }

    const val UNLIT = "🏮 Zhaslá lucerna výprav. Sklo je začouzené, knot suchý. Nemáš čím ji zapálit.\n" +
        "Na Signálním útesu svítí Kesh každou noc – ten by věděl, kde vzít oheň."
    fun litText(l: Lantern, count: Int) = "🏮 ${l.lit}\n\nLucerna $count ze ${ALL.size}."
    const val ALREADY = "🏮 Lucerna svítí. Plamen se kloní nahoru, ke schodům – jako by věděl, kudy se jde domů."

    const val GIVE = "„Odpověděli. Jen ne ti, na které jsem čekal.“ Kesh dlouho kouká na kmen.\n\n" +
        "„Světlomet na Béčku vidí jen ten, kdo je nahoře. Výpravy to věděly – na cestě dolů si věšely lucerny, aby trefily zpátky. " +
        "U schodů, u kotev, v táboře, na kolících, v chrámu, v lese. Všechny zhasly.“\n\n" +
        "Odepne od čepice starou lucernu s vyrytým číslem 7 a podá ti ji. „Tuhle jsem nesl. Pořád hoří. Zapalte od ní ty ostatní – " +
        "sedm luceren, od Stanice 8 až dolů do lesa. Ať trefí nahoru každý, kdo ještě jde. I vy.“"
    const val GIVE_HINT = "Zapal 7 zhaslých luceren v Hlubině: Stanice 8, Pozorovatelna B, Kořenový most, Tábor na svahu, Svah bezejmenných, Chrám Lumenců, Les pod korunou."

    const val ALL_LIT = "Všech sedm luceren svítí. Lucerna výpravy 7 je lehčí – jako by z ní něco odešlo do těch ostatních. Kesh čeká na útesu."
    const val ALL_LIT_HINT = "Vrať se ke Keshovi na Signální útes."

    const val FINAL = "Kesh tě vezme na kraj útesu a ukáže dolů.\n\n" +
        "Ve tmě pod korunou svítí světla. Jedno v lese, jedno v chrámu, jedno na svahu mezi těmi, kdo nedošli, jedno v táboře, " +
        "jedno u kotvy, jedno u bunkru a jedno úplně nahoře, u schodů ke Stanici 8. Tečkovaná čára od stromu nahoru.\n\n" +
        "„Cesta zpátky,“ řekne Kesh. „Dřív jsem si myslel, že svítím pro ně. Teď to vidím líp. Svítí se pro toho, kdo ještě jde.“\n\n" +
        "Vezme si od tebe lucernu výpravy 7 a pověsí ji na stožár nad lavičkou. „Až půjdete dolů ke kmeni, otočte se. A pak se vraťte nahoru spát.“"
    const val FAREWELL = "Kesh sedí na lavičce pod lucernou výpravy 7 a dívá se na tečkovanou čáru světel. Občas na tebe kývne: nahoru, spát."
}

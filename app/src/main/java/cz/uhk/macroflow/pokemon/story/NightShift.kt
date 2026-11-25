package cz.uhk.macroflow.pokemon.story

/**
 * Vedlejší úkol „Noční směna“ na Kořenovém mostě, zóna 3 (docs/adr/0106). Technik Orrin z výpravy 8 (Operace Pečeť)
 * namlouval Řádu povinná hlášení o snech. Všem se zdálo totéž – a Řád hlášení „třídil“, tak Orrin pásky schoval:
 * jednu dal jako dárek bytosti na trnu ve svatyňce Lumenců, druhou za ceduli na lešení u kmene, poslední zavřel
 * do ventilu na potrubí s pěnou. Fáze: dvě pásky (průzkum mostu) → ventil (2 dny s tréninkem – utažené kolo)
 * → poslední hlášení. Orrin je obrys vrostlý do kůry u paty kmene. Nic se tu neodměňuje hladověním ani nespaním:
 * poslední páska radí opak – vyspat se.
 */
object NightShift {
    const val QUEST_ID = "hlubina_nocni_smena"
    const val NAME = "Orrin (páska)"
    const val TAPE_NODE = "km_paska"
    const val SHRINE_NODE = "km_svatyne"
    const val SCAFFOLD_NODE = "km_leseni"
    const val VALVE_NODE = "km_ventil"

    const val STARTED_KEY = "most_started"
    const val REEL_SHRINE_KEY = "most_civka_svatyne"
    const val REEL_SCAFFOLD_KEY = "most_civka_leseni"
    const val VALVE_KEY = "most_ventil"
    const val FINAL_KEY = "most_hlaseni"
    val REEL_KEYS = "$REEL_SHRINE_KEY,$REEL_SCAFFOLD_KEY"
    val KEYS = setOf(STARTED_KEY, REEL_SHRINE_KEY, REEL_SCAFFOLD_KEY, VALVE_KEY, FINAL_KEY)

    const val TRAINING_DAYS = 2
    const val VALVE_STAGE = 1
    const val FINAL_STAGE = 2

    const val TAPE1 = "Cvak. Jakmile se přiblížíš, magnetofon se sám rozběhne – spouští ho hlas. Nebo dech.\n\n" +
        "„…hlášení o snech, výprava 8, technik Orrin, Operace Pečeť, úsek šest. Řád chce každé ráno hlášení: co se vám zdálo, nic nevynechávat.\n\n" +
        "Tak tady to je. Zdálo se mi to samé co ostatním. Že stojím tady na kořeni a kmen dýchá. Pomalu. Jednou za celou noc.\n\n" +
        "Další dvě pásky jsem schoval. Jednu jsem dal tomu malému na trnu – Lumenci mu nosili dárky, tak jsem mu dal taky. " +
        "Druhá je nahoře na lešení, za cedulí. Řád je nesmí najít. Oni ty sny nepočítají. Oni je třídí.“\n\n" +
        "Páska doběhne a cívka se točí naprázdno."
    const val TAPE1_HINT = "Najdi Orrinovy dvě schované pásky: u svatyňky na trnu a nahoře na lešení u kmene."

    const val TAPE2 = "Založíš pásku dvě. Šum, pak Orrin, unavenější.\n\n" +
        "„Noc čtyřicet. Už se nám zdá všem úplně to samé, slovo od slova. Dole pod korunou jsou ve tmě velcí. Spí a dýchají naráz. " +
        "Když vydechnou, nahoře někdo přestane mít jméno.\n\n" +
        "A pak ten se šesti očima. Neříká nic jiného než: Vytáhni kopí. Velitel zakázal o něm mluvit. Hlášení se prý přepisují.“\n\n" +
        "Páska tři je krátká: „Poslední pásku jsem zavřel do ventilu na potrubí. Řád ho utáhl tak, aby ho nikdo neotočil. " +
        "Kdo ji chce slyšet, musí na to mít ruce.“"
    const val TAPE2_HINT = "Utažené kolo ventilu povolí jen silným rukám – zacvič si ve ${TRAINING_DAYS} různých dnech."

    const val VALVE_READY = "Cívky se točí naprázdno. Ruce už na to máš – kolo ventilu na potrubí teď povolí."
    const val VALVE_READY_HINT = "Otoč ventilem na potrubí a poslední pásku pusť na magnetofonu."

    const val VALVE_OPEN = "🔧 Kolo zaskřípe a povolí. Z potrubí syčí zbytek pěny a voní to jako připálený cukr.\n" +
        "V komoře ventilu leží cívka zabalená v igelitu. Štítek Orrinovým písmem: HLÁŠENÍ 41 – PRO TOHO, KDO PŘIJDE."
    const val VALVE_SHUT = "🔧 Ventil na potrubí, které vede podél kořene a za lešením mizí přes hranu dolů – k ráně. " +
        "Kolo je utažené a zaplombované rudou plombou Řádu. Ani se nehne."
    const val VALVE_DONE = "🔧 Otevřený ventil. Potrubí je prázdné, jen občas tiše syčí."

    const val FINAL = "Šum. Dlouho jen dech – pomalý, jednou za dlouho. Pak Orrin, šeptem, jako by mluvil ze spaní:\n\n" +
        "„Hlášení čtyřicet jedna. Zdálo se mi, že stojím tady na mostě a dívám se na sebe, jak spím. Kůra mi roste přes nohy a nebolí to. Je teplá.\n\n" +
        "Ten se šesti očima se na mě díval a poprvé neřekl ‚Vytáhni kopí‘. Řekl: ‚Tohle nech pro toho, kdo přijde po tobě. " +
        "Přijde Okruh dvě stě třináct. Ať se vyspí, než sejde až dolů.‘\n\n" +
        "Nevím, co je Okruh dvě stě třináct. Ale jestli to posloucháš ty – vyspi se. A když se ozve, neodpovídej mu.“\n\n" +
        "Cvak. Páska doběhne.\n\nNahoře u paty kmene je v kůře obrys člověka. Na rameni má nášivku. Teď už víš, čí."

    const val FAREWELL = "Magnetofon vypneš a cívky srovnáš do bedny. Řádu je neodevzdáš – hlášení o snech výpravy 8 už nikdo třídit nebude.\n\n" +
        "Kesh na Signálním útesu dnes v noci kmeni neposvítí. Řekl, že už ví, komu by odpovídal."

    const val REEL_SHRINE = "✨ V koženém váčku pod trnem, mezi sušenými bobulemi a knoflíky, je zabalená cívka s páskou. Štítek: HLÁŠENÍ 2.\n" +
        "Bytost na trnu se na okamžik rozsvítí jasněji."
    const val REEL_SCAFFOLD = "🏗️ Za cedulí PEČEŤ, mezi šroubem a kůrou, je zastrčená cívka s páskou. Štítek: HLÁŠENÍ 3.\n" +
        "Kůra ji už napůl obrostla – vytáhneš ji jen tak tak."

    /** Text magnetofonu mimo dialogy úkolu. */
    const val TAPE_IDLE = "📼 Magnetofon Řádu pod plachtou. Cívky stojí, kontrolka nahrávání svítí rudě. Na bedně šablonou S8 a lepicí páska: HLÁŠENÍ O SNECH – NEPŘEHRÁVAT."
}

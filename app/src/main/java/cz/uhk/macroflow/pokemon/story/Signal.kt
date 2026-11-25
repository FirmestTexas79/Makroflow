package cz.uhk.macroflow.pokemon.story

/**
 * Vedlejší úkol „Signalista“ na Signálním útesu, zóna 3 (docs/adr/0106). Kesh (Lumex v čepici Řádu) nosil lucernu
 * výpravy 7. Jednoho rána začal mluvit – jako obyvatelé Ozvěny – a výprava byla pryč. Od té doby každou noc
 * signalizuje ke kmeni „VÝPRAVA 7, ODPOVĚZTE“ a ve dne chodí nahoru rozsvítit světlomet na Pozorovatelně B,
 * aby výprava viděla cestu zpátky. Fáze: dynamo (kroky) → spánek (Kesh nespí, protože hlídá – a zapomíná jména;
 * „Kdo spí, pamatuje si“ z deníku výpravy 5) → odpověď: z kmene blikne šest zelených světel. Nebyla to výprava 7.
 */
object Signal {
    const val QUEST_ID = "hlubina_signal"
    const val NAME = "Kesh"
    const val NODE = "su_kesh"
    const val LAMP_NODE = "su_lampa"
    /** Pata Keshe (art px útesu). */
    const val X = 124
    const val Y = 194

    const val STARTED_KEY = "signal_started"
    const val ANSWER_KEY = "signal_answer"
    val KEYS = setOf(STARTED_KEY, ANSWER_KEY)

    const val STEPS = 6000
    const val SLEEP_NIGHTS = 2
    const val SLEEP_HOURS = "6"
    const val ANSWER_STAGE = 2

    const val DYNAMO = "„Nebojte se, nekoušu. Kdysi jsem jen nesl lucernu – výprava 7, devět lidí a já vpředu se světlem. Pak jsem jednoho rána " +
        "promluvil. Lekl jsem se vlastního hlasu. A když jsem se otočil, nikdo za mnou nebyl.\n\n" +
        "Od té doby jim každou noc svítím. VÝPRAVA 7, ODPOVĚZTE. Ve dne chodím nahoru na Béčko rozsvítit světlomet, ať vidí cestu zpátky.\n\n" +
        "Jenže dynamo už sotva točí. Lampa nedosvítí ani do půli propasti. Roztočíte ho? Klika jde ztěžka – chce to nachodit pořádný kus cesty.“"
    const val DYNAMO_HINT = "Nachoď dnes ${STEPS} kroků – každý krok roztočí Keshovo dynamo."

    const val SLEEP = "„Svítí! Až ke kmeni!“ Kesh se směje, pak zmlkne. „Výprava 7. Velitel… Ar-… Ardis? Ne, Ardis byla jednička. Náš byl… " +
        "měl jizvu na bradě. A ta zrzavá, co zpívala…“\n\n" +
        "Dlouho mlčí. „Nespím. Někdo musí hlídat. Jenže kdo hlídá, zapomíná – stálo to v deníku pětky. Vyspíte se za mě? " +
        "Dvě pořádné noci. Ráno mi řeknete, co se vám zdálo, a já si třeba vzpomenu.“"
    val SLEEP_HINT = "Vyspi se ${SLEEP_NIGHTS} noci aspoň ${SLEEP_HOURS} hodin – kdo spí, pamatuje si."

    const val READY = "Kesh si píše do Knihy signálů jména – teď už všech devět. „Vzpomněl jsem si. Na všechny. Vy jste spal a mně se vrátili.“\n\n" +
        "Podá ti kliku lampy. „Dnes to pošlete vy. VÝPRAVA 7, ODPOVĚZTE. Tři krátké, tři dlouhé, tři krátké. Já budu dívat.“"
    const val READY_HINT = "Pošli u signální lampy poslední signál ke kmeni."

    const val ANSWER = "Clona cvaká. Tři krátké, tři dlouhé, tři krátké. Světlo letí přes propast a dopadne na kmen.\n\n" +
        "Dlouho nic. Mlha se valí z rány. Kesh nedýchá.\n\n" +
        "Pak v kmeni, kousek pod místem, kde se blýská kopí, se rozsvítí světlo. Zelené. Druhé. Třetí. Čtvrté. Páté. Šesté.\n\n" +
        "Šest světel v řadě po dvou. Nemrkají jako lampa. Mrkají jako oči.\n\n" +
        "Kesh pomalu zavře clonu. „To nejsou oni,“ zašeptá. „Výprava 7 nesla jen jednu lampu. A tu jsem nesl já.“"

    const val FAREWELL = "Kesh zapíše do Knihy signálů poslední řádek: ODPOVĚĎ PŘIJATA. ODESÍLATEL: ███. SIGNALIZACE UKONČENA.\n\n" +
        "„Světlomet na Béčku nechám svítit dál,“ řekne. „Ne pro ně. Pro vás. Abyste vždycky trefil nahoru – a šel se vyspat.“"

    /** Texty lampy a knihy podle fáze (mimo finální odpověď). */
    fun lampText(stage: Int, done: Boolean): String = when {
        done -> "🔦 Signální lampa je zavřená a zakrytá plachtou. Klika dynama je svázaná provázkem, aby se nedala otočit."
        stage <= 0 -> "🔦 Signální lampa Řádu s lamelovou clonou, natočená ke kmeni. Dynamo sotva točí – světlo slabě problikne a zhasne."
        stage == 1 -> "🔦 Lampa svítí naplno, až ke kmeni. Kesh na ni nespouští oči."
        else -> ANSWER
    }

    const val BOOK = "📓 KNIHA SIGNÁLŮ. Každá noc jeden řádek: datum, čas, „VÝPRAVA 7, ODPOVĚZTE“ – a vedle prázdná kolonka ODPOVĚĎ.\n" +
        "Stránek jsou stovky. Kolonka je pořád prázdná."
}

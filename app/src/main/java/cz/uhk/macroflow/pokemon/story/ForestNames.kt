package cz.uhk.macroflow.pokemon.story

/**
 * Vedlejší úkol „Jména v mlze“ v Lese pod korunou, zóna 3 (docs/adr/0107). U kruhu stop sedí Gudwin Oliver – kletba Paměti:
 * nemůže zapomenout, pamatuje si každého poutníka. Mlha zapomnění z rány bere lidem směr a pak jména; ti, kdo zapomněli
 * kam, chodí v kruhu. Gudwin jim jména říká nahlas, aby úplně nezmizeli. Fáze: vyspat se (mlha se živí únavou – 3 noci
 * aspoň 7 h; pak jdou přečíst značky na kůře) → najít 7 jmenovek ve větvičkách lesa → vrátit se ke Gudwinovi, který jména
 * přečte naposledy. Spánek je jádro Makrosvěta: tady je to přímo zbraň proti zapomnění. Nic se neodměňuje nespaním.
 */
object ForestNames {
    const val QUEST_ID = "hlubina_jmena_v_mlze"
    const val NAME = "Gudwin Oliver"

    const val STARTED_KEY = "les_jmena_start"
    const val FINAL_KEY = "les_jmena_nahlas"
    const val SLEEP_NIGHTS = 3
    const val SLEEP_HOURS = "7"
    const val TAGS_STAGE = 1
    const val FINAL_STAGE = 2

    /** Jmenovka: uzel na mapě, příznak sebrání, text (jméno, výprava, role). */
    data class Tag(val node: String, val key: String, val text: String)

    val TAGS = listOf(
        Tag("lp_jm1", "les_jm_1", "ILVAR – VÝPRAVA 3 – KARTOGRAF"),
        Tag("lp_jm2", "les_jm_2", "SEREN – VÝPRAVA 5 – ZDRAVOTNICE"),
        Tag("lp_jm3", "les_jm_3", "DORRAN – VÝPRAVA 6 – VELITEL TÁBORA"),
        Tag("lp_jm4", "les_jm_4", "MIREL – VÝPRAVA 6 – HLÍDKA"),
        Tag("lp_jm5", "les_jm_5", "ASKO – VÝPRAVA 7 – NOSIČ LUCERNY"),
        Tag("lp_jm6", "les_jm_6", "LIESA – VÝPRAVA 7 – ZAPISOVATELKA"),
        Tag("lp_jm7", "les_jm_7", "TEOVAN – VÝPRAVA 5 – SIGNALISTA")
    )
    val TAG_KEYS = TAGS.joinToString(",") { it.key }
    val KEYS = setOf(STARTED_KEY, FINAL_KEY) + TAGS.map { it.key }

    fun tagAt(node: String) = TAGS.firstOrNull { it.node == node }

    fun tagFound(tag: Tag, count: Int) = "🏷️ Plechová jmenovka na provázku, zaklesnutá ve větvičkách. Mlha z ní skoro smyla písmena, " +
        "ale když se soustředíš, přečteš:\n\n${tag.text}\n\nJmenovka $count ze ${TAGS.size}."

    const val MEET = "Na kraji vyšlapaného kruhu sedí Gudwin Oliver. Ten samý, co tě kdysi vítal ve městě – jen hubenější, " +
        "jako by od té doby uplynuly roky.\n\n" +
        "„Ty jsi došel až sem,“ řekne a usměje se. „Já vím. Pamatuju si to. Já si pamatuju všechno.“\n\n" +
        "Ukáže na kruh stop. „Tady chodí ti, kdo zapomněli kam. Mlha jim vzala směr a pak jména. Já jim je říkám, aby úplně nezmizeli. " +
        "Ale je jich moc a já jsem unavený.“\n\n" +
        "„Mlha se živí únavou. Kdo nespí, zapomíná. Vyspi se pořádně – tři noci, aspoň sedm hodin. Pak uvidíš, co je vyryté na kůře. " +
        "Ukazuje to, kam odešli a kde nechali jmenovky.“"
    const val MEET_HINT = "Vyspi se: $SLEEP_NIGHTS noci aspoň $SLEEP_HOURS hodin (hodinky nebo ruční zápis spánku). Pak se vrať ke Gudwinovi."

    const val RESTED = "Gudwin zvedne hlavu a dlouho se na tebe dívá. „Vidíš to? Už se ti nerozmazávají.“\n\n" +
        "„Sedm jmenovek. Sedm lidí, které si pamatuju jen já. Visí ve větvičkách, kde je mlha shodila – u kruhu, u sondy, u tábora, " +
        "u tůně, na mýtině ticha a u luceren. Přines mi je. Řeknu je nahlas, naposledy.“"
    const val RESTED_HINT = "Najdi 7 jmenovek v Lese pod korunou – třpytí se ve větvičkách u stezek."

    const val ALL_FOUND = "Máš všech sedm. Plech je studený a pořád trochu vlhký od mlhy. Gudwin čeká u kruhu."
    const val ALL_FOUND_HINT = "Vrať se ke Gudwinovi u kruhu stop."

    const val FINAL = "Gudwin si stoupne doprostřed kruhu a jmenovky čte nahlas. Pomalu, jedno jméno po druhém:\n\n" +
        "„Ilvar. Seren. Dorran. Mirel. Asko. Liesa. Teovan.“\n\n" +
        "Při každém jménu se mlha kolem kruhu trochu zvedne a vyšlapaná stopa přestane být tak hluboká. Když dočte poslední, " +
        "kruh je prázdný. Nikdo už nechodí dokola.\n\n" +
        "„Děkuju,“ řekne Gudwin. „Teď si je budeme pamatovat dva.“\n\n" +
        "Pak ti podá ještě jednu jmenovku, starší a ohlazenější než ostatní. Není na ní jméno. Jen číslo: 213.\n" +
        "„Tuhle jsem tu našel dřív než ty,“ řekne tiše. „Visela tu ještě předtím, než jsi přišel. Nevím, co to znamená. " +
        "Ale vím, že se máš vždycky vrátit domů. A vyspat se.“"
    const val FAREWELL = "Gudwin zůstane sedět na kraji kruhu. Kdykoli tudy půjdeš, řekne ti tvoje jméno. Pro jistotu."
    const val IDLE = "🧓 Gudwin sedí na kraji prázdného kruhu a tiše si opakuje jména. Když tě uvidí, řekne i to tvoje. Pro jistotu."
    const val TAG_TOO_EARLY = "🏷️ Ve větvičkách se něco zablyští – a když se na to podíváš, mlha to rozmaže. Možná až se vyspíš."
}

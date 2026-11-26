package cz.uhk.macroflow.pokemon.story

/**
 * Vedlejší úkol „Kořeny, které pijí“ na Svahu bezejmenných, zóna 3 (docs/adr/0115). Vodočetná lať Řádu u vypité tůňky:
 * kořeny pijí ze skály – a z těch, kdo zapomenou pít a jíst. Výpravy 1–7 zapomněly obojí. Fáze: splnit si cíl vody
 * ve 2 dnech (tůňka se naplní a vyplave pouzdro Řádu) → jíst 2 dny po sobě (na víčku je vyškrábáno NEJDŘÍV SE NAJEZ)
 * → přečíst nahlas jména výpravy 7 u devíti na svahu. Odměňuje se pití a jídlo podle vlastního cíle, nikdy hladovění.
 * Desáté jméno (nosič lucerny) je rozmazané – prozrazení, že Makromoni byli lidé, patří jen konci III.
 */
object SlopeWater {
    private const val V7_LIST_PLACEHOLDER = "§"

    const val QUEST_ID = "hlubina_voda"
    const val NAME = "Vodočetná lať"
    const val WATER_NODE = "sb_voda"
    const val V7_NODE = "sb_v7"

    const val STARTED_KEY = "svah_voda_start"
    const val NAMES_KEY = "svah_jmena_v7"
    val KEYS = setOf(STARTED_KEY, NAMES_KEY)

    const val WATER_DAYS = 2
    const val MEAL_DAYS = 2
    const val NAMES_STAGE = 2

    val V7 = listOf("Ilsa Brodová", "Tomáš Vrána", "Hedvika Ráčková", "Rudolf Kaun", "Bára Šípková",
        "Ondřej Holan", "Míra Leskovcová", "Vít Holub", "Anděla Kroupová")

    const val INTRO = "💧 Vodočetná lať Řádu. Tůňka pod ní je skoro vypitá. Pramínky ze skály tečou dolů a mizí v kořenech.\n\n" +
        "Na lati tužkou, čím níž, tím roztřesenějším písmem:\n" +
        "„Hladina klesá. Kořeny rostou.“\n„Kořeny pijí ze skály.“\n„Kořeny pijí z nás. Kdo zapomene pít, tomu se to stane.“\n\n" +
        "Pod hladinou se v kalu něco leskne. Kov. Moc hluboko, abys na to dosáhl."
    const val INTRO_HINT = "Splň svůj denní cíl vody ve $WATER_DAYS různých dnech. Kořeny tu pijí z toho, kdo zapomene pít."

    const val RISEN = "💧 Mokrá čára na kamenech je pod vodou. Tůňka je plná – poprvé od doby, kdy sem někdo zatloukl lať.\n\n" +
        "Z kalu vyplavalo kovové pouzdro Řádu, utěsněné voskem. Na víčku je nehtem vyškrábáno:\n\n" +
        "NEJDŘÍV SE NAJEZ.\n\n" +
        "Vzpomeneš si na nedotčené příděly v jejich kapsách."
    const val RISEN_HINT = "Zapiš si jídlo $MEAL_DAYS dny po sobě. Oni zapomněli jíst. Ty ne."

    const val OPENED = "Vosk povolí. V pouzdře je složený list – docházka výpravy 7, mokrá jen na rozích:\n\n" +
        V7_LIST_PLACEHOLDER +
        "\n\nDesáté jméno rozpila voda. Zbyla jen poznámka tužkou: nosič lucerny.\n\n" +
        "Devět štítků V7 leží o kus níž na svahu."
    const val OPENED_HINT = "Dojdi k devíti z výpravy 7 a přečti jim jména nahlas."

    const val READ_TITLE = "Výprava 7"
    const val READ_BUTTON = "📜 Přečíst jména nahlas"
    const val READ = "Klekneš si k nim a čteš. Pomalu, jedno jméno po druhém, tak jak jsou na listu.\n\n" +
        "U každého jména se papírový štítek na kotníku trochu pohne, jako by ho zvedl průvan zdola.\n\n" +
        "Když dočteš, je na svahu ticho. Ale jiné ticho než předtím."

    const val FAREWELL = "Pouzdro s docházkou zastrčíš mezi ně, tam, kde mezi pátým a šestým zůstalo volné místo.\n\n" +
        "Na lati připíšeš pod poslední řádek: „Hladina stoupá.“"

    const val WATER_DONE = "💧 Plná tůňka a vodočetná lať. Pod posledním řádkem je tvým písmem: „Hladina stoupá.“"
    const val V7_DONE = "Devět vedle sebe, ruce natažené k sobě. Na štítcích už není jen V7 – připsal jsi k nim jména.\n" +
        "Mezi pátým a šestým leží pouzdro s docházkou. Nahoře na útesu pořád někdo svítí."


    /** Text docházky se jmény (v [OPENED] je místo nich značka). */
    fun opened() = OPENED.replace(V7_LIST_PLACEHOLDER, V7.joinToString("\n") { "– $it" })

    /** Text místa podle stavu úkolu (null = běžný text mapy). */
    fun textFor(node: String, done: Boolean) = if (!done) null else when (node) {
        WATER_NODE -> WATER_DONE
        V7_NODE -> V7_DONE
        else -> null
    }
}

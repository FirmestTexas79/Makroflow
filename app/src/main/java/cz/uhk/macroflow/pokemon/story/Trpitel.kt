package cz.uhk.macroflow.pokemon.story

import cz.uhk.macroflow.energy.Adherence

/**
 * Kmen (RootTrunk, docs/adr/0106, bible: finále) – Trpitel, kletba Bolesti. Kazimír byl královo kopí: držel Lumiru, když
 * jí Hladomír rval světlo. Poslední přání Lumenců mu nechalo celou bolest, kterou jí způsobil; popel, který padá na svět,
 * je jeho maso. Kopí drží ránu zavřenou a Řád chce, aby ho po Kazimírovi držel hráč (Subjekt 213).
 *
 * Trpitel se probudí jen nádobě v rovnováze: dnes 90–110 % denního cíle energie (stejné pásmo jako Adherence.KCAL).
 * Hladovění se nikdy nepočítá. Pak hráč volí konec – nevratně; dohraje se ve Středu kořenů (story/Finale.kt).
 * Tajný konec (Mydrus, Elderan, Gudwin) se odemyká pamětí na mechovém lůžku ve Středu (Finale.End.SECRET).
 */
object Trpitel {
    enum class Ending(val key: String) { SPEAR("konec_kopi"), BLOOM("konec_kveteni"), WISH("konec_prani") }

    const val MET_KEY = "trpitel_promluvil"
    val KEYS = setOf(MET_KEY) + Ending.entries.map { it.key }

    fun ending(isSet: (String) -> Boolean): Ending? = Ending.entries.firstOrNull { isSet(it.key) }

    /** Vyvážený den: kalorie v pásmu cíle. Pod pásmem (hladovění) ani nad ním se Trpitel neprobudí. */
    fun balanced(kcalPercent: Int) = Adherence.isHitPercent(Adherence.Nutrient.KCAL, kcalPercent)

    const val TITLE = "Kmen"
    const val SUBTITLE = "Kapitola VI · Trpitel"

    fun asleep(kcalPercent: Int) = "Šest očí je zavřených. Obr v ráně dýchá pomalu a těžce. Popel z něj padá jako sníh.\n\n" +
        "Ze sondy na lávce zapraská Septův hlas:\n„Nádoba 213: dnes nevyvážena. Kontakt zatím neschválen.“\n\n" +
        "Trpitel se probouzí jen nádobě v rovnováze. Dnes máš $kcalPercent % svého denního cíle energie – " +
        "potřebuješ ${Adherence.Nutrient.KCAL.minPct}–${Adherence.Nutrient.KCAL.maxPct} %. Ne hladovět, ne se přejídat."

    const val WAKE = "Pěna v ráně se zvedne, jako když se nadechne spící. Šest rukou se pohne – horní napnou řetězy, " +
        "prostřední se rozevřou, dolní sevřou ratiště. Šest očí se rozsvítí, jedno po druhém, odspodu nahoru.\n\n" +
        "Nemá ústa. Přesto ho slyšíš. Ten hlas znáš – ze snů všech, kdo tu spali před tebou.\n\n" +
        "„Přišel jsi celý. Vyspalý, najedený, v rovnováze. Přesně takového tě chtěli.“\n\n" +
        "„Jmenoval jsem se Kazimír. Byl jsem královo kopí. Když Hladomír rval Lumiře světlo z hrudi, držel jsem ji. Tímhle.“ " +
        "Dolní ruce stisknou ratiště a kmen pod tebou zasténá. „Pak vyslovili své poslední přání: ať zapomene každý, kdo jim ublížil. " +
        "Já nezapomněl. Mně zůstala bolest. Celá, kterou jsem jí způsobil. Popel, co padá na tvůj svět, jsem já.“\n\n" +
        "„Kopí drží ránu zavřenou. Pod ním je to poslední, co z ní zbylo. Řád potřebuje, aby ho někdo držel dál. Já už nemůžu.“\n\n" +
        "Šest očí sklouzne dolů, ke koruně pod světem. „A ona… chtěla ještě něco říct. Nestihla to.“"

    const val SEPT = "Ze sondy na lávce zapraská hlas. Sept, klidný jako vždycky:\n" +
        "„Subjekt 213. Převezměte nástroj. Nástupnictví je schváleno.“\nKrátká pauza.\n" +
        "„Svět vám bude vděčný. Jen si nebude pamatovat proč.“"

    const val CHOOSE = "Tohle je konec cesty. Co uděláš?"
    const val LEAVE = "Ještě ne"
    const val LEAVE_TEXT = "Ustoupíš na lávku. Šest očí se za tebou dívá, dokud nezhasne. Kazimír čekal věky. Den počká."
    const val CONFIRM = "Ano"
    const val NOT_YET = "Ještě ne"
    const val IRREVERSIBLE = "\n\nTohle už nepůjde vzít zpátky."

    fun label(e: Ending) = when (e) {
        Ending.SPEAR -> "🗡 Vzít jeho místo na kopí"
        Ending.BLOOM -> "🌸 Vytáhnout kopí"
        Ending.WISH -> "✨ Dokončit její přání"
    }

    fun confirm(e: Ending) = when (e) {
        Ending.SPEAR -> "Vystřídáš Kazimíra. Kopí budou držet tvoje ruce – a svět nahoře na tebe zapomene."
        Ending.BLOOM -> "Kazimír bude volný. Rána se otevře a všechno, co z Lumiry zbylo, vyteče na svět. Den kvetení. Řád tomu říká potopa."
        Ending.WISH -> "Lumira umřela uprostřed věty. Ve Středu kořenů ještě leží, co nestihla doříct – a ty jsi jediný, kdo tam dojde."
    } + IRREVERSIBLE

    fun chosen(e: Ending) = when (e) {
        Ending.SPEAR -> "Natáhneš ruce k ratišti. Kazimír tě zastaví – horním párem, jemně.\n" +
            "„Ještě ne. Nejdřív se jdi podívat, co budeš držet.“\n\n" +
            "Šest očí ukáže k patě kmene. „Dolů. Do Středu kořenů. Pak se vrať. Kopí počká – a já taky. V čekání jsem dobrý.“\n\n" +
            "Spirra se ti tiskne k noze. Ona zapomenout nechce."
        Ending.BLOOM -> "Zapřeš se nohou o kůru a táhneš. Dolní ruce táhnou s tebou. Objímka Řádu praskne, řetězy se rozletí.\n\n" +
            "Kopí vyjde ven s dlouhým, skoro lidským povzdechem. Z rány se vyvalí světlo – husté jako míza, teplé jako ráno – " +
            "a teče dolů po kmeni, po lávce, do propasti. Kam dopadne, vyraší zelená.\n\n" +
            "Kazimír se sesype z pěny jako popel z ohniště. Než se rozpadne docela, šest očí se na tebe usměje.\n\n" +
            "Zdola, z koruny, se ozve hukot. Voda stoupá."
        Ending.WISH -> "Kazimír dlouho mlčí. Pak dolní ruce sevřou ratiště pevněji – ne aby ho vytáhly, ale aby ho podržely ještě chvíli.\n" +
            "„Tak jdi,“ řekne. „Já tu vydržím. V tom jsem dobrý.“\n\nŠest očí se otočí k patě kmene. Kůra se tam s praskotem rozestoupí."
    } + "\n\nU paty kmene se otevřela puklina dolů – do Středu kořenů."

    /** Text u Trpitele, když už je volba za hráčem. */
    fun after(e: Ending) = when (e) {
        Ending.SPEAR -> "🗡 Kazimír drží kopí ještě chvíli – pro tebe. Šest očí ukazuje dolů, k puklině u paty kmene."
        Ending.BLOOM -> "🌸 V ráně už není nic než světlo a mladé výhonky. Na lávce leží kopí a hromádka teplého popela."
        Ending.WISH -> "✨ Kazimír svírá ratiště a tiše dýchá. Když jdeš kolem, oči se za tebou otočí: „Tak jdi.“"
    }

    fun crack(chosen: Boolean) = if (chosen)
        "⬇️ Puklina u paty kmene vede dolů, do Středu kořenů. Zevnitř jde teplo a světlo."
    else "⬇️ U paty kmene je v kůře úzká puklina zalitá pěnou. Zevnitř je slyšet vodu. Neprojdeš."
}

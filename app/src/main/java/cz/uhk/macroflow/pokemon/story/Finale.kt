package cz.uhk.macroflow.pokemon.story

/**
 * Střed kořenů a konce hry (docs/adr/0110, bible: Finále – cyklus). Volba u Trpitele (Trpitel.Ending) se dohraje tady,
 * u posledního světla Lumiry:
 *  I.   KOPÍ – vystřídáš Kazimíra, svět je v bezpečí a zapomene na tebe (na tabuli naskočí 214).
 *  II.  DEN KVETENÍ – kopí je venku, světlo vyteče nahoru, Řád tomu říká potopa, svět tomu říká jaro.
 *  III. POSLEDNÍ PŘÁNÍ – dokončíš Lumiřinu větu „…ať si vzpomeneme“: Makromoni si vzpomenou, že byli lidé (Lumenci).
 *       Teprve tady hra prozradí, co celou dobu tajila.
 *  IV.  PROBUZENÍ (tajný) – kdo posbíral paměť (Gudwinova jmenovka 213, Elderan propuštěný, Mydrus ví, aspoň 4 jména
 *       v Pamětní knize), může si lehnout na mech a opravdu se vyspat (skutečná noc ≥ 7 h). Vzpomene si na cyklus okruhů
 *       a odmítne být nádobou: vrátí se domů a jde spát. Automat ve Věži vydá lístek 214 – prázdný.
 * Žádný konec neodměňuje hladovění; tajný konec je doslova o spánku.
 */
object Finale {
    enum class End(val key: String, val roman: String, val title: String) {
        SPEAR("konec_kopi_dohran", "I", "Kopí"),
        BLOOM("konec_kveteni_dohran", "II", "Den kvetení"),
        WISH("konec_prani_dohran", "III", "Poslední přání"),
        SECRET("konec_probuzeni_dohran", "IV", "Probuzení")
    }

    const val ARRIVED_KEY = "stred_prichod"
    /** Den (epochDay), kdy si hráč lehl na mech – tajný konec čeká na skutečnou noc po něm; GamePrefs. */
    const val LAY_DAY_KEY = "stred_ulehl_den"
    const val SLEEP_HOURS = 7
    val KEYS = setOf(ARRIVED_KEY) + End.entries.map { it.key }

    fun played(isSet: (String) -> Boolean): End? = End.entries.firstOrNull { isSet(it.key) }

    /** Konec, který čeká na dohrání ve Středu podle volby u Trpitele. */
    fun pending(e: Trpitel.Ending) = when (e) {
        Trpitel.Ending.SPEAR -> End.SPEAR; Trpitel.Ending.BLOOM -> End.BLOOM; Trpitel.Ending.WISH -> End.WISH
    }

    // ── paměť pro tajný konec ──
    data class Memory(val key: String, val missing: String)

    val MEMORIES = listOf(
        Memory(ForestNames.FINAL_KEY, "Nepamatuješ si, co ti dal Gudwin v lese – jmenovku, která tam visela dřív, než jsi přišel."),
        Memory(SecretGrove.RELEASED_KEY, "Nepamatuješ si strážce brány, který bloudí v zapomenutém háji."),
        Memory(SecretGrove.MYDRUS_TOLD_KEY, "Nepamatuješ si, co ví Mydrus o svém učiteli.")
    )
    const val BOOK_NAMES = 4

    fun memoriesMissing(isSet: (String) -> Boolean, writtenNames: Int): List<String> =
        MEMORIES.filterNot { isSet(it.key) }.map { it.missing } +
            (if (writtenNames < BOOK_NAMES) listOf("Nepamatuješ si jména z Ozvěny – v Pamětní knize jich je jen $writtenNames ze $BOOK_NAMES.") else emptyList())

    fun secretReady(isSet: (String) -> Boolean, writtenNames: Int) = memoriesMissing(isSet, writtenNames).isEmpty()

    // ── texty míst ──
    const val TITLE = "Střed kořenů"
    const val ARRIVE = "Puklina tě pustí dolů a pak už není kam padat.\n\n" +
        "Je tu ticho. Ne to ticho z Lesa, které bere jména – jiné. Takové, jaké bývá ráno, než se kdokoli probudí.\n\n" +
        "Nepadá popel. Nepípá sonda. Kořeny tu nejsou bledé jako kost: jsou teplé, a když se jich dotkneš, mají puls. Pomalý. Klidný.\n\n" +
        "Dole uprostřed, v kolébce z tenkých kořínků, svítí něco malého. Jako svíčka v okně, kterou někdo nechal hořet, abys trefil domů."

    val TEXTS = mapOf(
        "st_koreny" to "🌳 Kořeny se tu sbíhají jako žebra kopule. Na kůře nejsou čísla Řádu, žádné kolíky, žádné štítky. " +
            "Jen drobné znaky Lumenců, staré a vyhlazené dotyky – jako zábradlí, kterého se tisíce rukou držely cestou dolů.",
        "st_odraz" to "💧 Voda je tak klidná, že je v ní vidět celá klenba. A ty. Odraz se na tebe usměje – a tentokrát úplně včas.",
        "st_ticho" to "🕯️ Na plochém kameni leží věci, které sem někdo donesl: dětská píšťalka, hřebínek, sušený květ, knoflík. " +
            "Stejné jako na oltáři přání v chrámu. Nikdo je nehlídá. Nikdo je neeviduje."
    )

    const val MOSS_PLAIN = "🌿 Mech je měkký a teplý. Na chvíli si sedneš. Tady by se dalo spát – kdyby sis pamatoval, kdo jsi.\n\n"
    const val MOSS_READY = "Mech je měkký a teplý. Spirra se do něj stočí a podívá se na tebe, jako by čekala, až si taky lehneš.\n\n" +
        "Vzpomínáš si. Na jmenovku z lesa, na strážce u brány, na Mydruse, na jména v knize. Všechny ti říkaly totéž, jen jinými slovy:\n\n" +
        "Vždycky se vrať. Vyspi se."
    const val LIE_DOWN = "🌙 Lehnout si a spát"
    const val LAY = "Lehneš si. Kořeny nad tebou dýchají pomalu, stejně jako ty.\n\n" +
        "Tohle se nedá zahrát. Vyspi se doopravdy – jednu celou noc, aspoň $SLEEP_HOURS hodin (hodinky nebo ruční zápis spánku). " +
        "Ráno se sem vrať."
    const val NOT_YET_SLEPT = "🌿 Mech je ještě teplý od tebe. Spirra spí. Noc, aspoň $SLEEP_HOURS hodin, ještě není zapsaná – vyspi se a vrať se."

    const val LIGHT_NONE = "✨ Světlo je drobné jako svíčka. Když se nadechneš, zachvěje se. Když vydechneš, zase se uklidní.\n\n" +
        "Nahoře u kmene čeká Kazimír na tvoje rozhodnutí."
    fun lightBefore(e: End) = when (e) {
        End.SPEAR -> "✨ Tohle je to, co budeš držet. Ne Řád, ne pečeť – tohle. Poslední světlo Lumiry, drobné jako svíčka, " +
            "a kopí nad ním je jediné, co ho drží pohromadě.\n\nTeď víš, co budeš držet."
        End.BLOOM -> "✨ Světlo už není drobné. Klidná voda kolem ostrůvku stoupá, zlátne a teče – nahoru, proti všemu, co platilo."
        End.WISH -> "✨ Nad světlem se ve vzduchu vznášejí znaky Lumenců. Konečně je umíš přečíst."
        End.SECRET -> ""
    }
    fun go(e: End) = when (e) {
        End.SPEAR -> "🗡 Vrátit se ke kopí"
        End.BLOOM -> "🌸 Nechat ho téct"
        End.WISH -> "✨ Dokončit její větu"
        End.SECRET -> "🌙 Probudit se"
    }
    fun after(e: End) = when (e) {
        End.SPEAR -> "✨ Světlo je drobné a klidné. Nahoře někdo drží kopí. Víš kdo – jen si nemůžeš vzpomenout na jméno."
        End.BLOOM -> "🌸 Ostrůvek je zarostlý květy. Voda odtekla nahoru a nechala po sobě jen teplo."
        End.WISH -> "✨ Světlo je pryč – rozletělo se do světa. V kolébce z kořínků zbyla jen vůně, jako když se otevře okno."
        End.SECRET -> "🌙 Kolébka je prázdná. Světlo je doma. Ty taky."
    }

    // ── konce: stránky, titul, dovětek ──
    class Ending(val end: End, val pages: List<String>, val epilogue: String)

    val ENDINGS = mapOf(
        End.SPEAR to Ending(End.SPEAR, listOf(
            "Vystoupáš puklinou zpátky ke kmeni. Kazimír na tebe čeká. Šest očí se usměje – poprvé to není bolest.",
            "Vezmeš ratiště. Je teplé od jeho rukou. Kazimír pouští jednu ruku po druhé – horní, prostřední, dolní – a s každou je lehčí, " +
                "až z něj zbude jen popel. Tentokrát nepadá dolů. Stoupá.",
            "Kopí drží ránu. Ty držíš kopí.\n\nZe sondy na lávce: „Nástupnictví potvrzeno. Děkujeme, Subjekte.“ Pauza. Šustění papírů. " +
                "„Subjekte…? Omlouvám se. Jak že se jmenujete?“",
            "Nahoře v Ozvěně upeče Brann chleba a podá ho někomu, koho nezná. Gudwin ve Městě uvítá nového poutníka. " +
                "V čekárně Věže naskočí na tabuli číslo 214.",
            "Spirra zůstane s tebou. Stočí se ti k noze na lávce a usne. Ona jediná nezapomene."
        ), "Svět je v bezpečí. Jen si nepamatuje proč."),
        End.BLOOM to Ending(End.BLOOM, listOf(
            "Světlo stoupá. Teče po kořenech, po kmeni, přes lávku a přes Práh. Kde se dotkne kůry, vyraší list.",
            "Les pod korunou se zazelená a mlha se rozplyne. Na Svahu bezejmenných vykvetou bílé květy – jeden u každého, kdo nedošel.",
            "Pak dorazí nahoru. Věž Řádu Váhy stojí ve vodě po kolena, po pás, po okna. Spisy plavou po hladině jako lekníny. " +
                "Sept stojí ve dveřích s deštníkem a poprvé neví, co říct.",
            "Ozvěnou proteče řeka. Popel z ulic odplave. Brann vyjde před pekárnu a poprvé si všimne, že dnešní ráno je jiné než včerejší.",
            "Kopí zůstane dole na lávce, zarostlé břečťanem. Lumira už pod světem nevisí hlavou dolů.\n\nRozkvetla."
        ), "Řád tomu říkal potopa. Svět tomu říká jaro."),
        End.WISH to Ending(End.WISH, listOf(
            "Znaky Lumenců, stejné jako na oltáři přání, ve studni Věže a na zádech klečících soch:\n\n" +
                "AŤ NÁS NIKDO NENAJDE.\nAŤ ZAPOMENE KAŽDÝ, KDO NÁM UBLÍŽIL.\nAŤ UŽ SE NIKDY NEMUSÍME BÁT.",
            "A pod tím ještě jeden řádek, nedopsaný. Lumira umřela uprostřed věty:\n\nA AŽ SE UŽ NEBUDEME BÁT…",
            "Dopovíš to za ni. Nahlas, do ticha, kde nepadá popel:\n\n„…ať si vzpomeneme.“",
            "Světlo se rozletí. Nahoru kořeny, kmenem, puklinami do světa – jako jiskry od ohně. A všude, kam dopadne, " +
                "se nějaký Makromon zastaví uprostřed kroku.",
            "Kesh na útesu upustí lucernu. „Asko,“ řekne. „Jmenoval jsem se Asko.“\n\nV Ozvěně se Brann zadívá na svoje ruce. " +
                "Na louce se malý Makromon v trávě rozpláče, protože si vzpomněl na mámu.",
            "Makromoni byli lidé. Lumenci – drobný světélkující národ, který si kdysi přál, aby je nikdo nenašel. " +
                "Celou dobu byli s tebou: v trávě, v jeskyních, ve tvém batohu. Teď se pamatují. A nikdo jim už neublíží.",
            "Spirra se ti vyšplhá na rameno a dlouho se na tebe dívá. Pak ti do ucha zašeptá tvoje jméno. Ne číslo. Jméno."
        ), "Lumenci si vzpomněli, kým byli. A ty sis vzpomněl, kým jsi."),
        End.SECRET to Ending(End.SECRET, listOf(
            "Ráno. Mech je teplý a ty jsi spal celou noc. Poprvé od chvíle, kdy jsi vešel do Makrosvěta, se ti nezdálo o číslech.",
            "A vzpomeneš si. Na všechno.\n\nJmenovka 213 visela v lese dřív, než jsi přišel – protože jsi tu už byl. A předtím taky. " +
                "Kruh za kruhem: okruh 198, 205, 211, 212, 213. Pokaždé nádoba. Pokaždé kopí, nebo potopa, nebo přání. A pak zase od začátku, s novým číslem.",
            "Gudwin si to pamatoval celou dobu. Mydrus to zapomínal. Elderan to hlídal u brány. Kniha to zapsala.\n\n" +
                "A Hunfrey ti to říkal pořád dokola, jen pokaždé jinými slovy: Vždycky se vrať. Vyspi se.",
            "Tak se vrátíš. Ne ke kopí. Domů.\n\nNahoru kořeny, lesem, kde Gudwin řekne tvoje jméno, kolem útesu, kde svítí Keshovy lucerny, " +
                "Věží, kde Sept marně listuje ve tvé složce, náměstím, kde Lio drží dopis bez adresy.",
            "Ve Městě si lehneš do své postele. Spirra se ti stočí u nohou.\n\nV čekárně Věže vydá automat lístek 214. Je prázdný. " +
                "Nikdo další už není na řadě."
        ), "Nejsilnější věc, kterou nádoba může udělat, je odmítnout být nádobou. A jít spát.")
    )

    const val BACK = "Zpět do Makrosvěta"
    fun card(e: End) = "KONEC ${e.roman} · ${e.title.uppercase()}"
}

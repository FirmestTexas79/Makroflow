package cz.uhk.macroflow.pokemon.story

/**
 * Kapitola V pokračuje ve Věži Řádu Váhy: hráč stoupá patry jako Subjekt 213 a zároveň sleduje stopu Hunfreyho
 * (Okruh 212), který Věží prošel před ním, utekl z Mrazírny a schoval se v patře, které na panelu výtahu nemá
 * tlačítko. Cesta končí v Sektoru Hlubiny pod světem, nad obrácenou korunou Lumiry (bible příběhu, docs/adr/0104).
 *
 * Každé patro schválí „měření“ – skutečný návyk (spánek, kroky, trénink, voda, snídaně) nebo souboje na patře,
 * a k tomu příběhová stopa na místě. Spánek je jádro: Spis 212, Dormitoř a Mrazírna. Hra nikdy neměří váhu ani
 * tvar těla. Čistá data a pravidla, pokryto testy.
 */
object TowerStory {
    /** Index první fáze Věže v kapitole V (za Kalibrací). */
    const val FIRST = Calibration.STAGE + 1

    // ── fáze (indexy v Ozvena questu) ──
    const val SPIS_212 = FIRST            // Archiv okruhů, 3 noci spánku
    const val MEASURE = FIRST + 1         // Měřírna: Hunfreyho křída pod vahami
    const val PLAIN = FIRST + 2           // Kalibrační louka: kroky
    const val FOREST = FIRST + 3          // Zarostlé patro: souboje
    const val ROCK = FIRST + 4            // Skalní patro: tréninky
    const val CAVE = FIRST + 5            // Krystalová jeskyně: souboje
    const val WATER = FIRST + 6           // Zatopené patro: voda
    const val DORM = FIRST + 7            // Dormitoř: noci spánku (6 h)
    const val ICE = FIRST + 8             // Mrazírna: prázdná komora 212
    const val FIRE = FIRST + 9            // Spalovna: prosévání popela (souboje)
    const val FAIRY = FIRST + 10          // Duhová zahrada: studna přání
    const val POISON = FIRST + 11         // Jedová laboratoř: snídaně bez zapomnění
    const val OFFICE = FIRST + 12         // Kancelář Řádu: Registr nádob
    const val MAP = FIRST + 13            // Hunfreyho mapa: 5 útržků
    const val HIDEOUT = FIRST + 14        // Patro −1: poslední zápis
    const val READY = FIRST + 15          // Subjekt připraven: jeden vyvážený den
    const val COUNT = 16

    /**
     * Které patro výtah pustí: patro N (1–13, −1 = 14, −2 = 15) je schválené, když hráč došel aspoň do fáze
     * [unlockStage]; −2 až po dokončení celé kapitoly.
     */
    fun approved(floor: Int, stageIndex: Int, completed: Boolean): Boolean = when (floor) {
        0 -> true
        in 1..13 -> stageIndex >= FIRST + floor - 1 || completed
        -1 -> stageIndex >= HIDEOUT || completed
        -2 -> completed
        else -> false
    }

    // ── příznaky ──
    const val MEASURE_KEY = "vez_merirna_krida"
    const val ICE_KEY = "vez_komora_212"
    const val FAIRY_KEY = "vez_studna_prani"
    const val OFFICE_KEY = "vez_registr_nadob"
    const val HIDEOUT_KEY = "vez_posledni_zapis"
    /** Hráč poprvé stál na balkonu Sektoru Hlubiny (konec kapitoly). */
    const val ABYSS_KEY = "vez_hlubina_videna"

    /** Všechny příznaky kapitoly (synchronizují se jako příběh). */
    val KEYS: Set<String> get() = setOf(MEASURE_KEY, ICE_KEY, FAIRY_KEY, OFFICE_KEY, HIDEOUT_KEY, ABYSS_KEY) + FRAGMENT_KEYS

    /** Útržky Hunfreyho mapy: uzel na patře → příznak. Sbírají se kdykoli po Spisu 212. */
    data class Fragment(val node: String, val key: String, val text: String)

    val FRAGMENTS = listOf(
        Fragment("les_dutina", "vez_utrzek_1",
            "Z dutiny vypadne složený papír. Kus mapy nakreslené tužkou: patra Věže jako krabičky nad sebou, " +
                "šipka vede dolů, kolem nuly. Na okraji: „Nahoru tě pustí. Dolů musíš sám.“"),
        Fragment("jes_lucerna", "vez_utrzek_2",
            "Za sklem lucerny, kde by měl být knot, je zastrčený proužek papíru. Na něm šachta výtahu a pod nulou " +
                "dva čtverečky. Jeden je přeškrtnutý: „−2: jen s doprovodem.“ Druhý zakroužkovaný."),
        Fragment("zat_lodka", "vez_utrzek_3",
            "Pod veslem v loďce, ve voskovaném plátně, leží suchý útržek. Rozkreslené potrubí patra 7 – jedna trubka " +
                "vede svisle dolů kolem šachty a u ní drobně: „Tudy teče voda, kterou pijí kořeny.“"),
        Fragment("led_komory_p", "vez_utrzek_4",
            "Mezi otisky dlaní v prázdné komoře 212 je ve skle zamrzlý papírek, otištěný zevnitř. Opatrně ho vydloubneš. " +
                "Plánek technického podlaží: lehátko, bedna, poklop. Podpis: „H.“"),
        Fragment("kanc_globus", "vez_utrzek_5",
            "Pod glóbem, kam ho někdo shodil, leží papírek, který na něm chyběl. Tužkou: „Tady končí mapa.“ " +
                "A na rubu, rychle, nakřivo: „Ale svět ne. Dole visí.“")
    )
    val FRAGMENT_KEYS = FRAGMENTS.map { it.key }

    /** Stopa příběhu na patře, která splní fázi s příznakem: uzel → (příznak, text, fáze od které platí). */
    data class Clue(val node: String, val key: String, val stage: Int, val text: String)

    val CLUES = listOf(
        Clue("meri_vahy", MEASURE_KEY, MEASURE,
            "Pod levou miskou vah, kam se běžně nedívá, je křídou napsáno Hunfreyho písmem:\n" +
                "„NEVÁŽÍ TĚLO. VÁŽÍ, CO ZAPOMENEŠ.“\nA pod tím šipka nahoru a číslo 3."),
        Clue("led_komory_p", ICE_KEY, ICE,
            "Komora 212 je prázdná. Námraza na skle je zevnitř poškrábaná a otisky dlaní vedou nahoru, k víku.\n" +
                "Kontrolka bliká: PROBUZEN – NEPLÁNOVANĚ. Na podlaze před komorou mokré stopy bot. Vedou k výtahu."),
        Clue("vila_studna", FAIRY_KEY, FAIRY,
            "Nakloníš se nad studnu. Z hloubky stoupá šepot přání, tisíce hlasů najednou, každé s pořadovým číslem.\n" +
                "Jedno je starší než ostatní a cizím jazykem. Pod ním úřední překlad:\n" +
                "„AŤ NÁS NIKDO NENAJDE.“ Razítko: NEVYŘÍZENO – ODESÍLATEL NEEXISTUJE."),
        Clue("kanc_stul", OFFICE_KEY, OFFICE,
            "Brk se zvedne a pod tvým pohledem dopíše do Registru nádob:\n" +
                "SUBJEKT 213 – STAV: PŘIPRAVOVÁN. ODPOVĚDNÝ: ███\nSUBJEKT 212 – STAV: ZTRACEN. POSLEDNÍ POLOHA: PATRO −1.\n" +
                "Inkoust je ještě mokrý."),
        Clue("minus_sesit", HIDEOUT_KEY, HIDEOUT,
            "Sešit na bedně. Poslední zápis Hunfreyho rukou, písmo se rozpadá:\n\n" +
                "„Jestli tohle čteš, jsi 213. Nespi tady. Spi doma.\n" +
                "Čtyřicet tři nocí jsem nezavřel oči, abych nezapomněl. A stejně zapomínám. Svoje jméno si ještě pamatuju, " +
                "tak ho píšu: Hunfrey. Hunfrey. Hunfrey.\n" +
                "Dole visí strom vzhůru nohama. Kořeny drží svět, koruna je pod ním. Řád tam posílá nádoby. " +
                "Já tam nepůjdu. Půjdu ven, k Bráně. Možná si tam vzpomenu, kdo jsem byl.\n" +
                "Ty se vrať. Ty se vždycky vrať.“")
    )

    /** Uzly, na které se klepe kvůli stopě nebo útržku (MakromonMapActivity je posílá do [tap]). */
    val TAP_NODES: Set<String> get() = (CLUES.map { it.node } + FRAGMENTS.map { it.node }).toSet()

    fun clueFor(node: String): Clue? = CLUES.firstOrNull { it.node == node }
    fun fragmentFor(node: String): Fragment? = FRAGMENTS.firstOrNull { it.node == node }

    /** Co se stane po klepnutí na stopu: text a příznaky k nastavení (prázdné = už známé). */
    data class Tap(val text: String, val setKeys: List<String>)

    /**
     * Klepnutí na uzel s příběhovou stopou nebo útržkem. [has] = je příznak nastaven, [stage] = aktuální fáze
     * kapitoly. Útržky se sbírají až po Spisu 212. null = obyčejný text patra.
     */
    fun tap(node: String, stage: Int, completed: Boolean, has: (String) -> Boolean): Tap? {
        val parts = mutableListOf<String>(); val keys = mutableListOf<String>()
        clueFor(node)?.let { c -> if (stage >= c.stage || completed) { parts += c.text; if (!has(c.key)) keys += c.key } }
        fragmentFor(node)?.let { f ->
            if ((stage > SPIS_212 || completed) && !has(f.key)) {
                val n = FRAGMENT_KEYS.count { has(it) } + 1
                parts += "🧩 Útržek Hunfreyho mapy ($n z ${FRAGMENTS.size})\n" + f.text
                keys += f.key
            }
        }
        return if (parts.isEmpty()) null else Tap(parts.joinToString("\n\n"), keys)
    }

    // ── texty fází (mluví Sept z reproduktoru výtahu, Hunfreyho zápisky) ──
    const val SEPT = "Archivář Sept"
    const val HUNFREY_NOTE = "Hunfreyho zápisky"

    const val T_SPIS = "Sept poprvé zvedne hlavu. V brýlích se odráží jen světlo.\n\n" +
        "Razítko dopadne na spis tak tvrdě, že se otřese celý pult: KALIBROVÁN.\n\n" +
        "„Subjekt kalibrován. Patro 1, Archiv okruhů: schváleno. Výjimečně.\n" +
        "Na čtecím pultu leží spis vašeho předchůdce. Je začerněný – záznamy se odkrývají jen těm, kdo spí. " +
        "Subjekt 212 přestal spát. Proto ho nedočetl nikdo.“\n\n" +
        "Ve vestibulu je prázdno. Hunfrey tu přece ještě před chvílí stál… nebo ne?"
    const val H_SPIS = "Spis 212 v Archivu okruhů (patro 1): každá noc s aspoň 3 h spánku odkryje stránku. Spi 3 noci."

    const val T_MEASURE = "Poslední stránka Spisu 212 se odkryje:\n„Subjekt 212 přestal spát. Kalibrace nedokončena.“\n\n" +
        "Na rubu, tužkou, plánek Věže. Patra nad sebou jako krabičky, patro −1 zakroužkované tolikrát, až se papír protrhl. " +
        "Na okraji: „Začni u vah. Neváží tělo.“\n\n" +
        "Z reproduktoru výtahu zachrčí Sept: „Patro 2, Měřírna: schváleno. Měření je součástí péče.“"
    const val H_MEASURE = "Měřírna (patro 2): prohlédni si velké váhy uprostřed patra."

    const val T_PLAIN = "„Patro 3, Kalibrační louka: schváleno.“\n\n" +
        "„Na louce měříme chůzi. Subjekt 212 ušel nejvíc kroků ze všech subjektů v historii Řádu. " +
        "Pokaždé stejným směrem. Ven.“\n\nPero za reproduktorem škrábe dál."
    const val H_PLAIN = "Ujdi dnes 8 000 kroků. Louka měří, kam až dojdeš."

    const val T_FOREST = "„Chůze v toleranci. Patro 4, Zarostlé patro: schváleno.“\n\n" +
        "„Upozorňujeme, že nejde o les. Jde o kontrolní výsadbu. Řád si přírodu nevypůjčil – Řád si ji opsal. " +
        "Prosíme, nekrmte vzorky.“\n\nGudwin by řekl, že to tu voní jako Hvozd. Jenže Hvozd tolik neposlouchá."
    const val H_FOREST = "Vyhraj 3 souboje na Zarostlém patře (patro 4). A podívej se do dutiny starého stromu."

    const val T_ROCK = "„Patro 5, Skalní patro: schváleno. Měříme zátěž.“\n\n" +
        "„Nádoba musí unést víc, než je jí příjemné. Doložte dva dny s tréninkem.“\n\n" +
        "Na skalní stěně je křídou 213 a šipka. Někdo vedle ní připsal jiným písmem: „Síla je jen trpělivost, která zvedá.“ " +
        "Odranova slova. Jak se dostala sem?"
    const val H_ROCK = "Odcvič 2 dny s tréninkem (deník posilovny, „Jdu na trénink“ nebo hodinky)."

    const val T_CAVE = "„Zátěž v toleranci. Patro 6, Krystalová jeskyně: schváleno.“\n\n" +
        "„Krystaly jsou majetek Řádu. Na bednách je původ: OBJEKT 019. Neptejte se, co to znamená. " +
        "Subjekty, které se ptají, se měří déle.“\n\nModré krystaly. Stejné, jaké jsi kdysi nosil do svatyně. Slzy…"
    const val H_CAVE = "Vyhraj 3 souboje v Krystalové jeskyni (patro 6). Lucerna u výtahu svítí nějak divně."

    const val T_WATER = "„Patro 7, Zatopené patro: schváleno. Měříme hydrataci.“\n\n" +
        "„Hladina stoupá. Zaznamenáno. Subjekty, které pijí, rostou. Kořeny, které pijí, rostou taky.“\n\n" +
        "Kořeny? Sept to řekl tak lhostejně, jako by mluvil o květináčích."
    const val H_WATER = "Splň svůj denní cíl vody ve 3 dnech. A prohlédni si loďku u lávky."

    const val T_DORM = "„Patro 8, Dormitoř: schváleno. Měříme spánek. Znovu.“\n\n" +
        "„Postel 213 je ustlaná. Subjekty 198 až 205 spí spokojeně. Nikdo si nestěžuje.“\n\n" +
        "Nikdo si nestěžuje, protože se nikdo neprobudí. Jestli tu nemáš jednou ležet, musíš spát doma. Pořádně."
    const val H_DORM = "Spi aspoň 6 hodin ve 3 nocích. Ve spánku rostou svaly – a nezapomíná se."

    const val T_ICE = "„Spánek v toleranci. Patro 9, Mrazírna: schváleno.“\n\n" +
        "„Mrazírna uchovává subjekty, které spí příliš krátce, dokud se nenaučí spát dlouho. Chlad zachovává. " +
        "Komora 213 je připravena. Komora 212 je…“\n\nReproduktor na dlouhou chvíli zmlkne. „…v údržbě.“"
    const val H_ICE = "Mrazírna (patro 9): prohlédni si prázdnou komoru vpravo."

    const val T_FIRE = "Otisky dlaní vedou zevnitř nahoru. Hunfrey se probudil, i když neměl. A utekl.\n\n" +
        "„Patro 10, Spalovna spisů: schváleno. Dohledejte, prosím, podklady. Některé záznamy byly zpracovány předčasně.“\n\n" +
        "Ve Spalovně se z popela po každém souboji vynoří ohořelá stránka. Jako by chtěla, aby ji někdo četl."
    const val H_FIRE = "Vyhraj 3 souboje ve Spalovně spisů (patro 10). Z popela se vynoří ohořelé stránky."

    const val T_FAIRY = "Tři ohořelé stránky. Na všech totéž, starým písmem, které Řád neumí spálit:\n\n" +
        "„…TA, JEŽ SPÍ VE HVĚZDÁCH… KRÁL UTRHL KUS SVĚTLA… SPLNILA POSLEDNÍ PŘÁNÍ A ZEMŘELA… " +
        "VISÍ POD SVĚTEM HLAVOU DOLŮ… KOŘENY DRŽÍ VŠE, CO JE NAD NÍ…“\n\n" +
        "„Patro 11, Duhová zahrada: schváleno,“ ozve se Sept. „Přání se evidují.“"
    const val H_FAIRY = "Duhová zahrada (patro 11): nakloň se nad studnu přání."

    const val T_POISON = "„Patro 12, Jedová laboratoř: schváleno.“\n\n" +
        "Pára z destilace voní jako čerstvý chléb. Jako Brannova pekárna každé ráno. „Dobré ráno, dobré ráno…“\n\n" +
        "Koncentrát ZAPOMNĚNÍ. Tak proto Brann říká každé ráno totéž. Jez svoje snídaně, ne jejich chléb."
    const val H_POISON = "Zapiš si vlastní snídani (do 10:30) ve 2 dnech. Kdo snídá sám, ví, co jí."

    const val T_OFFICE = "„Patro 13, Kancelář Řádu: schváleno. Výjimečně. Velmi výjimečně.“\n\n" +
        "Ručička ukazatele přejede konec stupnice. Nahoře nikdo nesedí, a přesto se tu pracuje.\n\n" +
        "„Registr nádob leží na stole. Subjekt si ho smí prohlédnout. Je to přece jeho.“"
    const val H_OFFICE = "Kancelář Řádu (patro 13): prohlédni si Registr nádob na stole s prázdným trůnem."

    const val T_MAP = "Patro −1. Tlačítko, které na panelu chybí.\n\n" +
        "Hunfrey po sobě nechal stopy po celé Věži – útržky mapy, které schoval tam, kam se úředníci nedívají. " +
        "Až je složíš, ukáže se cesta do patra, které oficiálně neexistuje.\n\n" +
        "Útržky: dutina stromu (4), lucerna (6), loďka (7), komora 212 (9), pod glóbem v Kanceláři (13)."
    const val H_MAP = "Najdi všech 5 útržků Hunfreyho mapy na patrech Věže."

    const val T_HIDEOUT = "Útržky do sebe zapadnou. Na rubu posledního je nákres panelu výtahu: pod nulou dvě zalepené díry " +
        "a návod – „zatlač palcem, zalepeno je jen zvenku“.\n\n" +
        "Zatlačíš. Lepicí páska povolí. Pod ní je tlačítko s vyrytým −1.\n\n" +
        "Reproduktor mlčí. Poprvé za celou dobu Sept nic neschválil."
    const val H_HIDEOUT = "Sjeď výtahem do patra −1 a najdi Hunfreyho sešit."

    const val T_READY = "Hunfreyho písmo se rozpadá do čar, které už nejsou písmena. Hunfrey. Hunfrey. Hunfrey.\n\n" +
        "Ve výtahu se rozsvítí druhé zalepené tlačítko. −2. Vedle něj kontrolka, která se nedá zhasnout:\n" +
        "SUBJEKT PŘIPRAVEN – ČEKÁ SE NA VYVÁŽENÍ.\n\n" +
        "Sept zní zase klidně, jako by se nic nestalo: „Poslední měření. Jeden vyvážený den. Ne víc, ne míň. " +
        "Nádoba nesmí přetékat, ani zůstat prázdná.“"
    const val H_READY = "Jeden vyvážený den: trefit svůj denní cíl energie. Ne hladovět, ne se přejídat."

    const val FAREWELL = "„Subjekt 213: vyvážen. Sektor Hlubiny: schváleno.“\n\n" +
        "Výtah jede dolů déle, než by měl. Mnohem déle. Když se dveře otevřou, není tam podlaha – jen lávka nad tmou.\n\n" +
        "A pod ní, obrovská, bledá, hlavou dolů: Spící. Kořeny drží svět nad tebou, koruna se ztrácí v hlubině. " +
        "Rudé nitě Řádu vedou ze všech stanic až k jejímu kmeni.\n\n" +
        "Kdesi hluboko v kůře se zablýskne kov. Kopí.\n\n" +
        "„Dobrá práce, Subjekte. Další sestup zatím neschválen. Ale už brzy.“"

    // ── patra −1 a −2: texty míst ──
    val HIDEOUT_TEXTS = mapOf(
        "minus_mapa" to "🗺️ Mapa kořenů přišpendlená na beton. Tužkou nakreslené kořeny se sbíhají dolů do jednoho zakroužkovaného místa. " +
            "Od připínáčků STANICE 0–7 k němu vedou rudé nitě. V rohu papírek: „Tady končí mapa.“",
        "minus_carky" to "🧮 Čárky křídou po pěti. Čtyřicet tři. Nad nimi: NOCI BEZ SPÁNKU.\nPoslední čárka je nedotažená.",
        "minus_maska" to "😷 Plynová maska na háčku. Na štítku: VYDÁNO – SUBJEKT 212. Ta, co chybí v Jedové laboratoři.\n" +
            "Hunfrey nedýchal jejich páru. Proto si pamatoval déle než ostatní.",
        "minus_lehatko" to "🛏️ Polní lehátko. Deka je zmačkaná a shrnutá k nohám, v polštáři důlek po hlavě.\n" +
            "Jediná postel ve Věži, na které někdo opravdu ležel. A zjevně moc nespal.",
        "minus_spisy" to "📦 Ukradené spisy. OKRUH 198, 199, 200… Na každém Hunfrey zakroužkoval stejnou kolonku: SPÁNEK – PRŮMĚR. Čísla klesají.",
        "minus_poklop" to "🔒 Kulatý ocelový poklop v podlaze se znakem vah. Přes něj řetězy a plomba Řádu.\n" +
            "Na poklopu křídou: „TUDY NE. DOLŮ JEN VÝTAHEM. A JEN KDYŽ JSI PŘIPRAVEN.“"
    )
    val ABYSS_TEXTS = mapOf(
        "hlub_cedule" to "📜 Mosazná deska na pultu: POZOROVACÍ STANOVIŠTĚ – SEKTOR HLUBINY.\nPod ní menší písmo: SLEDOVAT. NEPROBOUZET. NEPŘIBLIŽOVAT SE BEZ SCHVÁLENÍ.",
        "hlub_klec" to "⛓️ Klec na řetězech, která sjíždí do tmy. Závora je zamčená červeným zámkem.\nŠtítek: SESTUP ZATÍM NESCHVÁLEN.",
        "hlub_vyhled" to "🌳 Pod lávkou visí Spící. Mrtvý strom hlavou dolů, bledý jako kost. Kořeny drží svět nad tebou, " +
            "koruna se ztrácí v mlze. Na konečcích větví slabě svítí modrá světla, jako by si ještě na něco pamatovala.\n" +
            "Hluboko v kmeni se zablýskne kov."
    )
}

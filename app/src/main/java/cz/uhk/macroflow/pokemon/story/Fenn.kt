package cz.uhk.macroflow.pokemon.story

/**
 * Vedlejší linka „Zahradník, který sázel jména“ (Ztichlý sad, kapitola V, docs/adr/0096). Čistá data, pokryto testy.
 *
 * Každý strom v sadu někdo zasadil za člověka, kterého smazali Bezejmenní. Ve dne je sad veselý a prázdný,
 * v noci po něm chodí zahradník Fenn (Makromon Kendl, svíčka s modrým plamenem) a říká stromům jména,
 * která sám zapomíná. Teplý košík → Kdo sbírá jablka → Jména stromů → Co ví Nelle → Zalévání → Noc bez měsíce
 * (docs/adr/0097): finále s cutscénou Bezejmenných a volbou A (Fenn zůstane, zmizí Tobben), B (Fenn odejde do
 * Staré jabloně) nebo skrytou C (seznam má razítko S-0).
 */
object Fenn {
    const val QUEST_ID = "orchard_fenn"
    const val NAME = "Fenn"
    const val NELLE = "Babička Nelle"

    /** Uzel u Fenna (sad) a kde v noci stojí (art px sadu, pata). */
    const val NODE = "fenn"
    const val X = 106
    const val Y = 306

    // ── Příznaky příběhu ──
    /** Hráč viděl seznam v chalupě – quest běží (Nelle teprve pak ví, na co se ptáš). */
    const val STARTED_KEY = "fenn_started"
    const val NELLE_KEY = "fenn_nelle"
    /** Hráč viděl cutscénu „Světlo mezi stromy“ a potkal Fenna. */
    const val MET_KEY = "fenn_met"

    /** Hráč se Nelle zeptal na Maren (+ jestli jí řekl pravdu) a pak to vyřídil Fennovi. */
    const val NELLE_TOLD_KEY = "fenn_nelle_told"
    const val NELLE_TRUTH_KEY = "fenn_nelle_truth"
    const val REPORTED_KEY = "fenn_reported"
    /** Finále proběhlo; který konec hráč zvolil. */
    const val ENDING_KEY = "fenn_ending"
    const val END_A_KEY = "fenn_end_a"
    const val END_B_KEY = "fenn_end_b"
    const val END_C_KEY = "fenn_end_c"

    /** Fáze questu (indexy). */
    const val NELLE_STAGE = 0
    const val NIGHT_STAGE = 1
    const val NAMES_STAGE = 2
    const val MAREN_STAGE = 3
    const val WATER_STAGE = 4
    const val FINALE_STAGE = 5
    const val WATER_DAYS = 3

    enum class Ending { A, B, C }

    /** Skrytý konec C: všechny tři pravdy Fennovi, pravda Nelle a Vhled aspoň [SECRET_INSIGHT]. */
    const val SECRET_INSIGHT = 3
    fun secretEndingOpen(truths: Int, nelleTruth: Boolean, insight: Int): Boolean =
        truths == Clue.entries.size && nelleTruth && insight >= SECRET_INSIGHT

    fun endingKey(e: Ending) = when (e) { Ending.A -> END_A_KEY; Ending.B -> END_B_KEY; Ending.C -> END_C_KEY }

    /** Fenn zůstal (konec A) – stojí v noci u chalupy a dává jablka; Tobben z Ozvěny zmizel. */
    fun fennStays(isSet: (String) -> Boolean) = isSet(END_A_KEY)
    /** Fenn odešel do Staré jabloně (konec B nebo C) – jabloň v noci svítí a dává jablka. */
    fun inTree(isSet: (String) -> Boolean) = isSet(END_B_KEY) || isSet(END_C_KEY)

    /** Zlaté jablko jednou za den (klíč dne v herních preferencích). */
    const val APPLE_DAY_PREF = "fenn_apple_day"

    /** Stopa ke jménu stromu: kde, kdy, co hráč najde a jak Fenn zareaguje na pravdu / jen jméno. */
    enum class Clue(val key: String, val node: String, val atNight: Boolean, val treeName: String,
                    val found: String, val truth: String, val nameOnly: String) {
        WELL("fenn_clue_well", cz.uhk.macroflow.pokemon.cave.QuietOrchard.WELL_NODE, true, "Ilse",
            "V noci je hladina ve studni černá jako inkoust. Tvůj odraz se pohne o chvilku později než ty – a pak se nepohne vůbec. " +
                "Dívá se na vědro.\n\nNa okraji vědra je vyškrábané jméno, které ve dne nebylo vidět: ILSE. Pod ním menší, dětským písmem: " +
                "„nosila vodu ke všem stromům, i když ji nikdo nežádal“.",
            "Fenn se zachvěje. „Ilse… nosila vodu. Proto mám pořád pocit, že musím zalévat.“\n\nPlamen na chvíli zhasne a pak se rozhoří jasněji než předtím.",
            "Fenn kývne. „Ilse. Hezké jméno.“ Zopakuje ho stromu u studny.\n\nPlamen se ani nepohne."),
        HIVES("fenn_clue_hives", "ule", false, "Odo",
            "Úly mlčí. V tom nejstarším nejsou včely, jen plástve, které nikdo nevybral, a na vnitřní straně víka kovová známka: VČELAŘ ODO.\n\n" +
                "Na rubu někdo vyryl: „Med pro ty, co si nepamatují, jak sladké to bylo.“",
            "Fenn dlouho mlčí. „Odo… Vím, jak voní med. Nevím, odkud to vím.“\n\nPlamen se zbarví do teplé, medové barvy.",
            "„Odo,“ zopakuje Fenn a usměje se, jako by to bylo cizí slovo, které se mu líbí."),
        POND("fenn_clue_pond", "jezirko_sad", true, "Maren",
            "Měsíc na hladině se chvěje, i když nefouká. Když se zastavíš, odraz se rozpadne na střípky a ty se poskládají do písmen: MAREN.\n\n" +
                "Na okamžik na hladině vidíš ještě něco – dvě postavy na lavičce u jezírka. Pak už jen měsíc.",
            "„Maren seděla u vody s někým, kdo ji měl rád,“ řekneš mu. Fenn se otočí k jezírku. „S kým?“\n\nNa to už odpověď nemáš. Plamen hoří tiše a jasně.",
            "„Maren,“ řekne Fenn do tmy. Hladina se na okamžik uklidní.");

        /** Hráč Fennovi řekl celou pravdu (ve finále rozhoduje, co Fenn zvládne). */
        val truthKey get() = "${key}_truth"
    }

    val CLUE_KEYS: List<String> = Clue.entries.map { it.key }
    val ALL_KEYS: Set<String> = setOf(STARTED_KEY, NELLE_KEY, MET_KEY, NELLE_TOLD_KEY, NELLE_TRUTH_KEY, REPORTED_KEY,
        ENDING_KEY, END_A_KEY, END_B_KEY, END_C_KEY) + CLUE_KEYS + Clue.entries.map { it.truthKey }

    fun clueAt(node: String): Clue? = Clue.entries.firstOrNull { it.node == node }

    /** Kolik pravd hráč Fennovi řekl. */
    fun truths(isSet: (String) -> Boolean): Int = Clue.entries.count { isSet(it.truthKey) }

    // ── Texty fází (QuestRegistry) ──

    const val WARM_BASKET =
        "Dveře chalupy jsou pootevřené. Uvnitř je teplo, jako by tu před chvílí někdo zatopil.\n\n" +
            "Na stole leží seznam stromů – dvanáct řádků, u každého jméno. Všechna jsou přeškrtnutá. Jen poslední je rozmazané, " +
            "jako by ho někdo pořád psal a zase gumoval: „F…n“.\n\n" +
            "U dveří stojí košík jablek, ještě teplých od něčích dlaní. Lucerna nad stolem svítí. Nikdo tu není."
    const val WARM_BASKET_HINT = "Babička Nelle prodává na náměstí v Ozvěně jablka ze sadu. Zeptej se jí, kdo je sbírá."

    const val WHO_PICKS =
        "„Jablka? Ta leží každé ráno v košíku u chalupy. Jen si pro ně dojdu.“\n\n" +
            "A kdo je tam dává? Nelle se zarazí, jako by ji to nikdy nenapadlo. „Nikdo přece. Tak to vždycky bylo.“\n\n" +
            "Pak ztiší hlas: „Ale v noci tam nechoď. V noci tam někdo zalévá. Slyšela jsem konev. A viděla světlo – malé, modré… " +
            "jako svíčka, co nechce zhasnout.“"
    const val WHO_PICKS_HINT = "Vrať se do Ztichlého sadu, až se setmí (od 19:00)."

    const val TREE_NAMES =
        "„Ty mě vidíš? To se dlouho nestalo.“\n\n" +
            "Malá svíčka s modrým plamenem odloží konev. „Jsem Fenn. Myslím. Aspoň tak to stojí na seznamu – nebo stálo.“\n\n" +
            "„Každý strom tady za někoho roste. Říkal jsem jim jejich jména každou noc, aby nezapomněly, komu patří. " +
            "Jenže jména mi utíkají. Každou noc jedno.“\n\n" +
            "„Sad si je pamatuje líp než já. Studna v noci, úly ve dne, jezírko, když svítí měsíc. Najdi je. A zapiš je někam, " +
            "kde vydrží – mně se rozsypou v hlavě dřív, než je doříkám.“"
    const val TREE_NAMES_HINT = "Tři jména: studna (v noci), úly (ve dne), jezírko (v noci). Každé zapiš do Pamětní knihy."

    const val ASK_ABOUT_MAREN =
        "„Ilse. Odo. Maren.“ Fenn ta jména opakuje pořád dokola, jako modlitbu.\n\n" +
            "„Tři stromy už vědí, komu patří.“\n\n" +
            "Pak se na tebe podívá déle než obvykle. „To poslední… Maren. Někdo ve městě ho zná. Zeptej se na ni. " +
            "A pak mi řekni, co jsi slyšel – i kdyby to bolelo.“"
    const val ASK_ABOUT_MAREN_HINT = "Zeptej se babičky Nelle v Ozvěně na Maren. Pak se v noci vrať za Fennem."

    const val WATERING =
        "„Stromy se drží, dokud mají vodu. Ilse je zalévala každý den, i když ji nikdo nežádal. Já už na studnu nedosáhnu – " +
            "vědro je pro mě moc těžké a plamen mi v ní syčí.“\n\n" +
            "„Zalévej je za mě. Každý den, kdy se pořádně napiješ ty, se napijí i ony. Tři dny. Pak uvidíme, jestli je to dost.“"
    const val WATERING_HINT = "Splň svůj denní cíl vody ve 3 různých dnech. Každý den, kdy piješ ty, pije i sad."

    const val MOONLESS =
        "„Stromy se napily. Slyšíš? Šumí, jako by si povídaly.“\n\n" +
            "Fenn se ale netváří šťastně. Jeho plamen se naklání na stranu, jako ve větru, který nefouká.\n\n" +
            "„Dneska v noci nevyjde měsíc. Vím to. Nevím odkud. Až přijdou… zůstaň u mě. Prosím.“"
    const val MOONLESS_HINT = "Přijď v noci za Fennem k chalupě. Tohle už nepůjde vzít zpátky."

    /** Konec linky (vypravěč – chalupa). */
    const val FAREWELL = "Ztichlý sad. Každý strom ví, komu patří – aspoň dokud si to někdo pamatuje."

    // ── Co ví Nelle ──
    const val NELLE_MAREN =
        "Když vyslovíš jméno Maren, Nelle upustí jablko. Kutálí se po dlažbě a ona se za ním ani neohlédne.\n\n" +
            "„Maren…“ Chvíli hledá slova. „Tak se jmenovala moje sousedka. Sedávaly jsme u jezírka v sadu, každý večer, než… " +
            "Ne. Já žádnou sousedku neměla. Bydlím tu sama. Vždycky jsem bydlela sama.“\n\n" +
            "Ruce se jí třesou. Dívá se na tebe, jako by čekala, co řekneš."
    const val NELLE_TRUTH =
        "Řekneš jí o stromech, o seznamu a o Fennovi. Nelle dlouho mlčí. Pak sebere košík.\n\n" +
            "„Tak ty stromy nejsou jen stromy. A já celé ty roky prodávala jablka z jejího…“ Nedořekne to. " +
            "„Zítra je na trh nenesu. Ponesu je k jezírku.“"
    const val NELLE_FACADE =
        "„Asi se pletete,“ řekneš. Nelle se ulehčeně usměje a zvedne jablko z dlažby.\n\n" +
            "„Asi ano. Jablíčka, jablíčka! Ze Ztichlého sadu, sladká jako loni.“\n\nZní to přesně jako včera."
    const val NELLE_AFTER_TRUTH = "🍎 Babička Nelle: „Dneska jsem jablka na trh nenesla. Nesla jsem je k jezírku. Maren by chtěla.“"
    const val FENN_HEARS_TRUTH =
        "„Takže Nelle ví.“ Fenn se dlouho dívá k jezírku. „Bude nosit jablka sem, ne na trh. Snad jí to neublíží. " +
            "Ti, kdo si vzpomínají, bývají… vidět.“"
    const val FENN_HEARS_FACADE =
        "„Nechal jsi ji spát.“ Fenn přikývne. „Možná je to tak lepší. Jen… teď si Maren pamatujeme jen my dva.“"

    // ── Noc bez měsíce ──
    const val FINALE_TITLE = "Noc bez měsíce"
    const val FINALE =
        "Bezejmenní stojí kolem chalupy. Nemají tváře, jen místa, kde by tváře měly být. Seznam na stole se sám listuje " +
            "a jména na něm blednou.\n\n" +
            "Fennův plamen je tak malý, že by ho sfoukl dech. „Chtějí moje jméno,“ zašeptá. „Je to poslední, které tu zbylo.“\n\n" +
            "V kapse cítíš váhu Pamětní knihy."
    const val CHOICE_A = "Zapsat Fennovo jméno do Pamětní knihy"
    const val CHOICE_B = "Nechat Fenna odejít do Staré jabloně"
    const val CHOICE_C = "„Kdo ti ten seznam dal, Fenne?“"

    const val ENDING_A =
        "Napíšeš FENN přes celou stránku. Písmena se chvíli třesou, jako by se je někdo snažil vytrhnout – a pak zůstanou.\n\n" +
            "Bezejmenní se otočí. Ne k tobě. K městu.\n\n" +
            "Ráno je Fenn pořád u chalupy a plamen mu hoří jasně jako nikdy. „Pamatuju si tě,“ řekne. „A pamatuju si sebe. Děkuju.“\n\n" +
            "V Ozvěně ale zůstala prázdná lavička. Noviny na ní leží otevřené na úterý třináctého. Nikdo si nevzpomene, kdo je četl.\n\n" +
            "🍎 Fenn ti dal Zlaté jablko. Každou noc ti dá další."
    const val ENDING_B =
        "„Nech mě jít,“ řekne Fenn. „Stromy mě znají. Budu jim říkat jejich jména zevnitř.“\n\n" +
            "Přejde ke Staré jabloni a opře se o kmen. Plamen sklouzne do kůry jako kapka do vody. Bezejmenní chvíli stojí nad " +
            "prázdným místem – pak se rozplynou. Není co vzít.\n\n" +
            "Stará jabloň od té noci svítí. Na seznamu v chalupě přibyl třináctý řádek. Přeškrtnutý.\n\n" +
            "🍎 Z jabloně ti spadlo Zlaté jablko. Každou noc spadne další."
    const val ENDING_C =
        "„Kdo ti ten seznam dal, Fenne?“\n\n" +
            "Fenn ztuhne. Na okamžik je jeho plamen bílý. „Nikdo. Já… ne. Přinesli ho. V šedém. Řekli, že je to práce. " +
            "Že mám stromům říkat jména, dokud…“ Otočí seznam. Na rubu je vybledlé razítko S-0 a pod ním strojem: " +
            "KONTROLNÍ VÝSADBA – MĚŘENÍ ZÁRMUTKU.\n\n" +
            "„Věděli, že zapomenu,“ zašeptá. „Chtěli vidět, jak dlouho to bude bolet.“\n\n" +
            "Pak udělá to jediné, co mu zbývá: odejde do Staré jabloně, kam za ním nikdo nedosáhne. Bezejmenní odcházejí " +
            "s prázdnýma rukama.\n\n" +
            "🍎 Z jabloně ti spadlo Zlaté jablko. Ve spisech Kustodiátu se něco odkrylo."
    fun endingText(e: Ending) = when (e) { Ending.A -> ENDING_A; Ending.B -> ENDING_B; Ending.C -> ENDING_C }

    // ── Po konci ──
    const val FENN_AFTER = "🕯 „Ilse, Odo, Maren… a Fenn.“ Fenn se usměje. „Každou noc o jedno jméno víc, ne míň.“"
    const val TREE_AFTER = "🌳 Stará jabloň tiše svítí. Když přiložíš ucho ke kůře, slyšíš šepot. Jména."
    const val APPLE_FROM_FENN = "🍎 Fenn ti podá Zlaté jablko. „Od Ilse. Zalévala ho.“"
    const val APPLE_FROM_TREE = "🍎 Ze svítící jabloně ti do dlaně spadne Zlaté jablko. Je teplé."
    const val TOBBEN_GONE = "🪑 Prázdná lavička. Noviny leží na zemi, otevřené na úterý třináctého. Nikdo neví, kdo je četl."
    const val VESSA_AFTER_A = "\n\nVessa se na tebe dlouho dívá. „Někoho jsi zachránil. Někoho jiného ne. Tak to s Bezejmennými chodí.“"

    // ── Texty mimo fáze ──

    const val NO_BOOK =
        "📖 Jméno se ti rozmazává před očima ještě dřív, než ho dočteš. Potřebuješ ho někam zapsat – " +
            "písařka Vessa v Ozvěně má knihu, ze které jména nemizí."
    const val WELL_DAY = "🪣 Ve dne je ve studni jen kousek nebe. Fenn říkal, že v noci se tu ukáže víc."
    const val WRITTEN = "✍️ Jméno je zapsané v Pamětní knize."
    const val DAY_SPOT = "🕯 Ve dne tu nikdo není. Jen konev opřená o strom a tráva, která je ráno vždycky mokrá."
    const val NIGHT_HUT = "🏚 V noci je chalupa prázdná a lucerna zhasnutá. Světlo, které tu ve dne svítilo, je teď venku mezi stromy."

    /** Titulky cutscény „Bezejmenní“ (finále). */
    val FINALE_LINES = listOf(
        "Měsíc dnes nevyšel.",
        "Světlušky zhasly. Jedna po druhé.",
        "Přišli bez kroků. Bez tváří. Bez jmen.",
        "I▒s▒ · O▒o · M▒r▒n · ▒▒▒▒ · ▒▒▒▒▒",
        "Fennův plamen se třese.",
        "„Čí jméno si vezmeme?“"
    )

    /** Titulky cutscény „Světlo mezi stromy“; ▒ se při přehrávání přesýpá. */
    val CUTSCENE_LINES = listOf(
        "Ztichlý sad. Ve dne se tu nikdo nezdrží.",
        "V noci se tu rozsvítí jediné světlo.",
        "„Dobrou noc, I▒▒e.“",
        "„Dobrou noc, O▒o.“",
        "„Dobrou noc, M▒▒▒n…“",
        "Plamínek se zastavil. A otočil se k tobě."
    )
}

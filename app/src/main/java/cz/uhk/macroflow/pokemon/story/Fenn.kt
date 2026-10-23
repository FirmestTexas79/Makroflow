package cz.uhk.macroflow.pokemon.story

/**
 * Vedlejší linka „Zahradník, který sázel jména“ (Ztichlý sad, kapitola V, docs/adr/0096). Čistá data, pokryto testy.
 *
 * Každý strom v sadu někdo zasadil za člověka, kterého smazali Bezejmenní. Ve dne je sad veselý a prázdný,
 * v noci po něm chodí zahradník Fenn (Makromon Kendl, svíčka s modrým plamenem) a říká stromům jména,
 * která sám zapomíná. Zatím prolog až fáze II (Teplý košík → Kdo sbírá jablka → Jména stromů);
 * Nelle, zalévání a Noc bez měsíce přijdou jako další fáze (quest se po rozšíření sám znovu otevře).
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

    /** Fáze, ve které se ptá Nelle / čeká se na noc / hledají se jména (indexy). */
    const val NELLE_STAGE = 0
    const val NIGHT_STAGE = 1
    const val NAMES_STAGE = 2

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
    val ALL_KEYS: Set<String> = setOf(STARTED_KEY, NELLE_KEY, MET_KEY) + CLUE_KEYS + Clue.entries.map { it.truthKey }

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

    /** Rozloučení po fázi II – zatím konec linky, navazuje „Co ví Nelle“. */
    const val PART_ONE_FAREWELL =
        "„Ilse. Odo. Maren.“ Fenn ta jména opakuje pořád dokola, jako modlitbu.\n\n" +
            "„Tři stromy už vědí, komu patří. Ostatní ještě čekají.“\n\n" +
            "Pak se na tebe podívá déle než obvykle. „To poslední… Maren. Někdo ve městě ho zná. Dej na sebe pozor, " +
            "až se jí budeš ptát.“"

    // ── Texty mimo fáze ──

    const val NO_BOOK =
        "📖 Jméno se ti rozmazává před očima ještě dřív, než ho dočteš. Potřebuješ ho někam zapsat – " +
            "písařka Vessa v Ozvěně má knihu, ze které jména nemizí."
    const val WELL_DAY = "🪣 Ve dne je ve studni jen kousek nebe. Fenn říkal, že v noci se tu ukáže víc."
    const val WRITTEN = "✍️ Jméno je zapsané v Pamětní knize."
    const val DAY_SPOT = "🕯 Ve dne tu nikdo není. Jen konev opřená o strom a tráva, která je ráno vždycky mokrá."
    const val NIGHT_HUT = "🏚 V noci je chalupa prázdná a lucerna zhasnutá. Světlo, které tu ve dne svítilo, je teď venku mezi stromy."

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

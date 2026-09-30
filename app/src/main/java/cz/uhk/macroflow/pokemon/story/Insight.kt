package cz.uhk.macroflow.pokemon.story

/**
 * Vhled a první stopy Kustodiátu v aktu I (docs/adr/0047). Čistá logika, pokryto testy.
 *
 * Vhled je skrytá hodnota: roste s odhalenými tajemstvími (příznaky příběhu) a s každým
 * nalezeným roztrženým listem. Hráč ho nikde nevidí – projeví se jen tím, že se fasáda
 * světa nepatrně láme: začerněná místa v listech se odkrývají, postavy občas zaváhají,
 * Město na zlomek vteřiny problikne jako „STANICE 7“.
 *
 * Pravidlo z bible příběhu: náznaky jsou drobné a vzácné. Pravda o Makromonech zazní až na konci.
 */
object Insight {

    /** Kolik Vhledu dává který příznak příběhu. */
    val FLAG_WEIGHTS: Map<String, Int> = mapOf(
        "legend_faced" to 1,
        "sky_pass_visited" to 1,
        ForestHeart.ROT_DEFEATED_KEY to 1,
        SecretGrove.FOUND_KEY to 1,
        SecretGrove.RELEASED_KEY to 2
    )

    /** Vhled = součet vah nastavených příznaků + 1 za každý nalezený list. */
    fun level(flags: Set<String>): Int =
        flags.sumOf { FLAG_WEIGHTS[it] ?: 0 } + flags.count { it.startsWith(PAGE_PREFIX) }

    // ── Roztržené listy ────────────────────────────────────────────────────────

    const val PAGE_PREFIX = "torn_page_"

    /** Předmět v batohu, přes který se nalezené listy čtou. */
    const val PAGES_ITEM = "roztrzene_listy"
    const val PAGES_LABEL = "Roztržené listy"

    /** Nalezené listy seřazené podle čísla. */
    fun foundPages(flags: Set<String>): List<Page> = PAGES.filter { it.key in flags }

    /**
     * Roztržený list. Text obsahuje začerněná místa ve tvaru `{n|text}` – odkryjí se až při
     * Vhledu alespoň n. [biome]/[node] = kde leží, [requires] = příznak, bez kterého tam ještě není.
     */
    data class Page(val id: Int, val biome: String, val node: String, val requires: String?, val found: String, val text: String) {
        val key: String get() = PAGE_PREFIX + id
    }

    val PAGES = listOf(
        Page(1, "MOUNTAINS", "camp", null,
            "V popelu táboráku leží ohořelý kus papíru. Někdo ho sem hodil, aby shořel – ale nedohořel.",
            "…ek 7. Subjekt dorazil do hor. Příjem potravy za posledních sedm dní: {2|v normě}. Kroky: {2|nad očekáváním}.\n" +
                "Doporučení: {4|pokračovat v kalibraci}. Zdroj {3|G. O.} potvrzuje, že subjekt nic netuší.\n\n— ███ {3|S-7}"),
        Page(2, "SKY_PASS", "muzik", null,
            "Mezi kameny mužíku je zastrčený přeložený list. Je vlhký a písmo se rozpíjí.",
            "Hlášení o snech personálu (výňatek).\nPracovnice {3|K-12}: „Stojím na okraji propasti. Dole visí strom, kořeny nahoru. " +
                "Na stromě je {4|něco s mnoha pažemi}. Nekřičí. Jen se na mě dívá {5|šesti očima} a ukazuje na {4|kopí}.“\n\n" +
                "Poznámka: {3|čtvrtý} podobný sen tento týden. Pracovnice přeložena."),
        Page(3, "FOREST", "stary_dub", ForestHeart.ROT_DEFEATED_KEY,
            "V kůře Starého dubu, tam kde dřív bydlela hniloba, je zaklíněný zmačkaný papír.",
            "Inventura kořenů, sektor Hvozd.\nKořen č. {2|1} (místně „Starý dub“) vykazuje aktivitu. Hniloba postupuje rychlostí {3|4 m za noc}.\n" +
                "Druid ({3|Mycit, samec}) ztrácí paměť. Doporučení: nezasahovat.\n{5|Zapomnění je v tomto případě milosrdné.}"),
        Page(4, "HIDDEN_GROVE", "studanka", SecretGrove.FOUND_KEY,
            "Na dně studánky se mezi hvězdami třpytí list zatížený kamínkem. Voda ho nerozpustila.",
            "Protokol o nálezu. Předmět: deník. Majitel: {3|Strážce E.}. Obsah: nespolehlivý.\n" +
                "Strážce věřil, že za branou vládne tyran. {4|Není to tyran.} {5|Je to rána, která se nemůže zavřít.}\n" +
                "Deník ponechán na místě jako {4|návnada}."),
        Page(5, "TOWN", "obchod", SecretGrove.RELEASED_KEY,
            "Na pultu obchodu leží zapomenutý papír. Prodavač tvrdí, že ho nikdy neviděl.",
            "Stanice 7 — denní hlášení.\nSubjekt dokončil první okruh: {2|krystaly}, {2|legenda}, {3|Srdce}. Brána otevřena.\n" +
                "{4|Okruh č. 213 postupuje rychleji než všechny předchozí.}\nNávrh: přesunout subjekt do {3|Věže}.\n{5|G. O. žádá o výjimku. Zamítnuto.}")
    )

    fun page(id: Int): Page? = PAGES.firstOrNull { it.id == id }

    /** List, který na tomhle místě čeká (není nalezený a je splněná podmínka), nebo null. */
    fun pageAt(biome: String, node: String, flags: Set<String>): Page? = PAGES.firstOrNull {
        it.biome == biome && it.node == node && it.key !in flags && (it.requires == null || it.requires in flags)
    }

    private val REDACTION = Regex("""\{(\d+)\|([^}]*)\}""")

    /** Text listu podle Vhledu: odkrytá místa bez závorek, zbytek jako █ (mezery zůstanou). */
    fun render(text: String, insight: Int): String = REDACTION.replace(text) { m ->
        val need = m.groupValues[1].toInt()
        val body = m.groupValues[2]
        if (insight >= need) body else body.map { if (it == ' ' || it == '\n') it else '█' }.joinToString("")
    }

    /** Kolik začerněných míst ještě zůstává. */
    fun hiddenCount(text: String, insight: Int): Int =
        REDACTION.findAll(text).count { it.groupValues[1].toInt() > insight }

    // ── Praskliny ve fasádě ────────────────────────────────────────────────────

    /**
     * Vzácné zaváhání postavy (jen když ji hráč sám osloví podruhé). Prokletí si drží veselou
     * fasádu – praskne jen na zlomek věty. [roll] = náhodné číslo 0–99.
     */
    fun crack(questId: String, insight: Int, roll: Int): String? {
        if (insight < 2 || roll >= 12) return null
        return when (questId) {
            "town_intro_oliver" -> "Tohle jsem ti už… ne, promiň. Jsi tu přece poprvé."
            "meadow_mastery" -> "Víš, někdy si pamatuju, jak jsem měl ruce. Ha! Blbost. Keře nemají ruce."
            "mountains_macro_king" -> "…mám hlad. Ne, to nic. Kde jsem to byl? Bílkoviny!"
            else -> null
        }
    }

    /** Město na zlomek vteřiny problikne jako Stanice 7 (Vhled ≥ 4, 1 z 6 příchodů). */
    fun stationFlicker(insight: Int, roll: Int): Boolean = insight >= 4 && roll % 6 == 0

    /**
     * Drobné věty v Makrodexu u pár druhů – nevinné, dokud člověk neví.
     * (Jediný náznak, že Makromoni kdysi byli něčím jiným.)
     */
    fun dexWhisper(makrodexId: String): String? = when (makrodexId) {
        "022" -> "Někdy v noci tiše pobrukuje melodii, kterou nikdo nezná."
        "024" -> "Když ho chytíš, chvíli se dívá na tvé ruce. Dlouho."
        "007" -> "Staví si z listů malé domky. Vždycky se dvěma okny."
        else -> null
    }

    /** Ikona 16×16: přehnutý ohořelý papír se začerněnými řádky. */
    const val ICON = 16

    fun pageIcon(): IntArray {
        val rows = listOf(
            "................",
            "..oooooooooo....",
            "..opppppppppo...",
            "..opxxxxpppppo..",
            "..oppppppppppo..",
            "..opxxxxxxxppo..",
            "..opppppppppbo..",
            "..opxxppxxxxpo..",
            "..oppppppppppo..",
            "..opxxxxxpppbo..",
            "..opppppppppbbo.",
            "..opxxxpppppbo..",
            "..oppppppppbbo..",
            "..obbpppppbbo...",
            "...oooooooooo...",
            "................"
        )
        val pal = mapOf('o' to 0xFF3A2A1A.toInt(), 'p' to 0xFFEAD8B0.toInt(), 'x' to 0xFF141414.toInt(), 'b' to 0xFF6A4A2A.toInt())
        val out = IntArray(ICON * ICON)
        rows.forEachIndexed { y, r -> r.forEachIndexed { x, ch -> out[y * ICON + x] = pal[ch] ?: 0 } }
        return out
    }

    /** Číslo účtenky z obchodu – razítko S-7 (docs/adr/0047). */
    fun receipt(seq: Int): String = "Účtenka S-7/" + (seq % 10000).toString().padStart(4, '0')
}

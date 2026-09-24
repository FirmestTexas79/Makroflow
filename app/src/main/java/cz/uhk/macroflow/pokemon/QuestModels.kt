package cz.uhk.macroflow.pokemon.quests

import cz.uhk.macroflow.R

enum class RequirementType {
    VISIT_NODE,      // Návštěva konkrétního bodu na mapě
    CAPTURE_SPECIFIC,// Chycení konkrétního Makromona
    WALK_STEPS,      // Nachození kroků
    TALK_TO_NPC,    // Jen odkliknutí dialogu
    LOG_MEAL,        // Nové: Zapsání jídla
    BATTLE_TYPE,     // Nové: Souboj s konkrétním typem
    SCAN_BARCODE,    // Naskenování čárového kódu ve funkční části (přes GameEvent log)
    LOG_CALORIES,    // Dnešní příjem kcal >= targetValue (odvozeno z consumed_snacks)
    LOG_MACROS,      // Dnešní příjem makroživiny v g >= targetValue; targetId = protein|carbs|fat
    BATTLE_BIOME     // Výhry v soubojích v daném biomu; targetId = BiomeType.name
}

/** Dnešní součty z funkční části – vstup pro odvozené fáze. */
data class NutritionTotals(val kcal: Int, val proteinG: Float, val carbsG: Float, val fatG: Float)

data class QuestStage(
    val title: String,
    val text: String,
    val speakerResId: Int,
    val requirementType: RequirementType,
    val targetValue: Int,            // Počet (např. 3000 kroků nebo 3 budovy)
    val targetId: String? = null,    // Např. "starter_bush" nebo "domov,pokedex,obchod"
    val speakerName: String = ""     // Prázdné = jmenovka se v dialogu skryje
)

data class QuestDefinition(
    val id: String,
    val stages: List<QuestStage>
)

// Objekt se všemi questy ve hře
object QuestRegistry {
    private const val GUDWIN = "Gudwin Oliver"
    private const val KRAL = "Král Mlsák"

    val TOWN_INTRO_QUEST = QuestDefinition(
        id = "town_intro_oliver",
        stages = listOf(
            QuestStage(
                title = "První kroky městem",
                text = "Vítej v Town, hrdino! Já jsem tvůj Makromom Gudwin, ale přátelé mi říkají Olivere. Než se vydáš do divočiny, musíš vědět, kde co je. Projdi si své zázemí – mrkni domů, prohlédni si Makrodex a nezapomeň se stavit v Obchodě. Až budeš mít mapu v malíčku, přijď za mnou!",
                speakerResId = R.drawable.gudwin_oliver,
                speakerName = GUDWIN,
                requirementType = RequirementType.VISIT_NODE,
                targetValue = 3,
                targetId = "domov,pokedex,obchod"
            ),
            QuestStage(
                title = "Tajemství v křoví",
                text = "Slyšel jsi to? Za tvým domem v tom hustém křoví se něco hýbe. Vypadá to, že si tě vyhlédl tvůj první parťák! Běž tam a zjisti, kdo na tebe čeká.",
                speakerResId = R.drawable.gudwin_oliver,
                speakerName = GUDWIN,
                requirementType = RequirementType.CAPTURE_SPECIFIC,
                targetValue = 1,
                targetId = "starter_bush"
            ),
            QuestStage(
                title = "Dechberoucí túra",
                text = "Tvůj parťák je plný energie a Meadow je ještě daleko. Abych tě mohl pustit dál, musím vědět, že na to máš kondici. Rozhýbej nohy! Jakmile ujdeme společně 3000 kroků, cesta se ti otevře.",
                speakerResId = R.drawable.gudwin_oliver,
                speakerName = GUDWIN,
                requirementType = RequirementType.WALK_STEPS,
                targetValue = 3000
            )
        )
    )

    val MEADOW_QUEST = QuestDefinition(
        id = "meadow_mastery",
        stages = listOf(
            QuestStage(
                title = "Příprava na cestu",
                text = "Hej, ty! Meadow je zrádná louka. Pokud chceš přežít, musíš mít energii. Ukaž mi svůj jídelníček! Zapiš si dnes 5 různých jídel, ať vím, že nehladovíš.",
                speakerResId = R.drawable.npc_bush,
                requirementType = RequirementType.LOG_MEAL,
                targetValue = 5
            ),
            QuestStage(
                title = "Vlhký odpor",
                text = "Výborně! Ale teď k boji. U tamtoho jezírka se usídlili vodní Makromoni a blokují cestu. Poraz 3 z nich, aby se tvůj parťák naučil bojovat i v dešti!",
                speakerResId = R.drawable.npc_bush,
                requirementType = RequirementType.BATTLE_TYPE,
                targetValue = 3,
                targetId = "WATER"
            ),
            QuestStage(
                title = "Dálkový průzkum",
                text = "Tvé svaly tuhnou, trenére. Abychom se dostali na konec louky, musíme se pořádně projít. 5000 kroků by mělo stačit k tomu, abys prozkoumal všechna skrytá zákoutí Meadow.",
                speakerResId = R.drawable.npc_bush,
                requirementType = RequirementType.WALK_STEPS,
                targetValue = 5000
            ),
            QuestStage(
                title = "Moderní lovec",
                text = "Poslední zkouška! Našel jsem tuhle krabičku s podivným kódem. My v divočině tomu nerozumíme, ale ty máš tu svoji techniku. Naskenuj čárový kód z nějakého jídla, ať zjistíme, co je to zač!",
                speakerResId = R.drawable.npc_bush,
                requirementType = RequirementType.SCAN_BARCODE,
                targetValue = 1
            )
        )
    )

    // ════════════════════════════════════════════════════════════════════════
    // MOUNTAINS – Král Mlsák
    // Téma: kalorie a makroživiny. Král (kamenná socha uprostřed kaňonu) kdysi
    // vládl pohoří, ale zapomněl na výživu – hráč mu pomůže znovu nabrat sílu.
    // ════════════════════════════════════════════════════════════════════════
    val MOUNTAINS_QUEST = QuestDefinition(
        id = "mountains_macro_king",
        stages = listOf(
            QuestStage(
                title = "Audience u krále",
                text = "Konečně! Čekal jsem na někoho, kdo mi pomůže. Já, Král Mlsák, jsem kdysi vládl celému pohoří – ale moje armáda zeslábla. Prý za to může špatná výživa. Nesmysl! Nebo... možná ne. Nejdřív prozkoumej tábor a jeskyni, ať víš, s čím máme tu čest.",
                speakerResId = R.drawable.kral_mlsak,
                speakerName = KRAL,
                requirementType = RequirementType.VISIT_NODE,
                targetValue = 2,
                targetId = "camp,cave"
            ),
            QuestStage(
                title = "Královský inventář",
                text = "Moji vojáci jedí, co najdou – ale netuší, kolik energie tím získají. Ty prý umíš počítat kalorie? Dokaž to! Zapiš si dnes jídla tak, aby tvůj příjem přesáhl 1500 kcal. Výživa je věda, ne náhoda.",
                speakerResId = R.drawable.kral_mlsak,
                speakerName = KRAL,
                requirementType = RequirementType.LOG_CALORIES,
                targetValue = 1500
            ),
            QuestStage(
                title = "Kámen svalů",
                text = "Působivé! Jenže kalorie nejsou všechno. Moji kamenní Makromoni potřebují bílkoviny, aby jejich svaly vydržely. Bez proteinu hory nepřekonáš. Zapiš si dnes aspoň 80 g bílkovin – maso, luštěniny, tvaroh, cokoliv, co buduje!",
                speakerResId = R.drawable.kral_mlsak,
                speakerName = KRAL,
                requirementType = RequirementType.LOG_MACROS,
                targetValue = 80,
                targetId = "protein"
            ),
            QuestStage(
                title = "Výzva vrcholu",
                text = "Učíš se rychle! Teď přichází pravá zkouška. V horách sídlí Makromoni silní a tvrdohlaví jako já. Poraz v horách 2 z nich a ukaž, že se tvé znalosti výživy proměnily v bojovou sílu!",
                speakerResId = R.drawable.kral_mlsak,
                speakerName = KRAL,
                requirementType = RequirementType.BATTLE_BIOME,
                targetValue = 2,
                targetId = "MOUNTAINS"
            ),
            QuestStage(
                title = "Cukrový pochod",
                text = "Hmm... vyhrál jsi. Musím uznat tvou sílu. Moji průzkumníci jsou ale pomalí – nemají energii na rychlý pochod. Sacharidy! To je odpověď. Zapiš si dnes 200 g sacharidů – rýže, vločky, ovoce. Ukaž jim, co znamená mít palivo!",
                speakerResId = R.drawable.kral_mlsak,
                speakerName = KRAL,
                requirementType = RequirementType.LOG_MACROS,
                targetValue = 200,
                targetId = "carbs"
            ),
            QuestStage(
                title = "Zlatý tuk království",
                text = "Zbývá poslední tajemství výživy, které jsem přehlížel – tuky. Ne z koblih, ale ty zdravé! Ořechy, avokádo, olivový olej. Zapiš si dnes aspoň 50 g tuku a slibuji reformu královské kuchyně!",
                speakerResId = R.drawable.kral_mlsak,
                speakerName = KRAL,
                requirementType = RequirementType.LOG_MACROS,
                targetValue = 50,
                targetId = "fat"
            ),
            QuestStage(
                title = "Královský pochod",
                text = "Udělal jsi ze mě jiného krále. Moje armáda je silná, najedená a vyvážená. Teď je čas na velký pochod přes celé pohoří! Ujdi se mnou dnes 8000 kroků – poslední zkouška, po které ti udělím titul Výživový rytíř hor!",
                speakerResId = R.drawable.kral_mlsak,
                speakerName = KRAL,
                requirementType = RequirementType.WALK_STEPS,
                targetValue = 8000
            )
        )
    )

    val ALL: List<QuestDefinition> by lazy { listOf(TOWN_INTRO_QUEST, MEADOW_QUEST, MOUNTAINS_QUEST) }

    fun byId(id: String): QuestDefinition? = ALL.firstOrNull { it.id == id }
}

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
    /**
     * Trefit dnes OSOBNÍ cíl (ne pevné číslo) – pásmo podle Adherence.Nutrient.
     * targetId = kcal | protein | carbs | fat, metadata = snědeno v % cíle.
     */
    HIT_TARGET,
    BATTLE_BIOME,    // Výhry v soubojích v daném biomu; targetId = BiomeType.name
    /** Vypít dnes OSOBNÍ cíl vody; metadata = vypito v % cíle (docs/adr/0045). */
    HIT_WATER,
    /** Přinést NPC předměty; targetId = "itemId:počet,itemId:počet", metadata 1 = odevzdáno. */
    DELIVER_ITEMS,
    /** Příznak příběhu (StoryFlags) – např. poražený boss; targetId = klíč, metadata 1 = nastaven. */
    STORY_FLAG
}

data class QuestStage(
    val title: String,
    val text: String,
    val speakerResId: Int,
    val requirementType: RequirementType,
    val targetValue: Int,            // Počet (např. 3000 kroků nebo 3 budovy)
    val targetId: String? = null,    // Např. "starter_bush" nebo "domov,pokedex,obchod"
    val speakerName: String = "",    // Prázdné = jmenovka se v dialogu skryje
    /** Vlastní krátká připomínka při dalším oslovení (jinak ji složí QuestProgression.reminder). */
    val hint: String? = null
)

data class QuestDefinition(
    val id: String,
    val stages: List<QuestStage>,
    /** Co NPC řekne, když s ním hráč mluví po dokončení celého questu. */
    val farewell: String = "Už jsi pro mě udělal dost. Hodně štěstí na cestách, hrdino!",
    /** Kdo se loučí (0 = mluvčí poslední fáze) – ve Hvozdu dává Srdce dub, ale loučí se Mydrus. */
    val farewellSpeakerResId: Int = 0,
    val farewellSpeakerName: String = ""
)

// Objekt se všemi questy ve hře
object QuestRegistry {
    private const val GUDWIN = "Gudwin Oliver"
    private const val KRAL = "Král Mlsák"

    val TOWN_INTRO_QUEST = QuestDefinition(
        id = "town_intro_oliver",
        farewell = "Město už znáš jako své boty. Cesta do Meadow je volná – a kdyby něco, víš, kde mě najdeš!",
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
        farewell = "Louku máš v malíčku. Za můstkem na východě začínají hory – ale bez pořádné procházky tě tam nepustí!",
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
        farewell = "Výživový rytíři hor! Moje armáda je silná a kuchyně reformovaná. Jen tak dál – trefuj své cíle každý den.",
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
                text = "Moji vojáci jedí, co najdou – jeden se přejí, druhý hladoví. Ty prý umíš počítat kalorie? Dokaž to! Traf dnes svůj kalorický cíl – ani moc, ani málo, nanejvýš o desetinu vedle. Výživa je věda, ne náhoda.",
                speakerResId = R.drawable.kral_mlsak,
                speakerName = KRAL,
                requirementType = RequirementType.HIT_TARGET,
                targetValue = 100,
                targetId = "kcal"
            ),
            QuestStage(
                title = "Kámen svalů",
                text = "Působivé! Jenže kalorie nejsou všechno. Moji kamenní Makromoni potřebují bílkoviny, aby jejich svaly vydržely. Bez proteinu hory nepřekonáš. Dosáhni dnes svého cíle bílkovin – maso, luštěniny, tvaroh, cokoliv, co buduje!",
                speakerResId = R.drawable.kral_mlsak,
                speakerName = KRAL,
                requirementType = RequirementType.HIT_TARGET,
                targetValue = 100,
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
                text = "Hmm... vyhrál jsi. Musím uznat tvou sílu. Moji průzkumníci jsou ale pomalí – nemají energii na pochod. Sacharidy! To je odpověď – ale s rozumem. Traf dnes svůj cíl sacharidů: rýže, vločky, ovoce. Ukaž jim, co znamená mít palivo!",
                speakerResId = R.drawable.kral_mlsak,
                speakerName = KRAL,
                requirementType = RequirementType.HIT_TARGET,
                targetValue = 100,
                targetId = "carbs"
            ),
            QuestStage(
                title = "Zlatý tuk království",
                text = "Zbývá poslední tajemství výživy, které jsem přehlížel – tuky. Ne z koblih, ale ty zdravé! Ořechy, avokádo, olivový olej. Traf dnes svůj cíl tuků a slibuji reformu královské kuchyně!",
                speakerResId = R.drawable.kral_mlsak,
                speakerName = KRAL,
                requirementType = RequirementType.HIT_TARGET,
                targetValue = 100,
                targetId = "fat"
            ),
            QuestStage(
                title = "Královský pochod",
                text = "Udělal jsi ze mě jiného krále. Moje armáda je silná a najedená. Teď je čas na velký pochod přes celé pohoří! Ujdi se mnou dnes 8000 kroků – a pak tě čeká poslední zkouška.",
                speakerResId = R.drawable.kral_mlsak,
                speakerName = KRAL,
                requirementType = RequirementType.WALK_STEPS,
                targetValue = 8000
            ),
            QuestStage(
                title = "Královská hostina",
                text = "Bílkoviny, sacharidy, tuky – každé zvlášť už umíš. Opravdový rytíř je ale umí všechny najednou! Traf dnes v jednom dni svůj cíl bílkovin, sacharidů i tuků. Pak ti udělím titul Výživový rytíř hor a moje kuchyně bude navždy vyvážená!",
                speakerResId = R.drawable.kral_mlsak,
                speakerName = KRAL,
                requirementType = RequirementType.HIT_TARGET,
                targetValue = 3,
                targetId = QuestProgression.ALL_MACROS
            )
        )
    )

    // ════════════════════════════════════════════════════════════════════════
    // HVOZD – druid Mydrus a Starý dub (docs/adr/0045)
    // Téma: voda, pohyb, suroviny ze všech dovedností. Po probuzení legendy padal z křídel
    // Drakirry nad Hvozdem žhavý popel a z něj vyrašila Rudá hniloba. Konec: Srdce Hvozdu,
    // klíč k Bráně světů na Nebeském průsmyku.
    // ════════════════════════════════════════════════════════════════════════
    private const val MYDRUS = "Mydrus"
    private const val DUB = "Starý dub"

    val FOREST_QUEST = QuestDefinition(
        id = "forest_heart",
        farewell = "Hvozd zase dýchá – a já taky. Mycité se vracejí na mýtinu a rudé houby usychají. " +
            "Srdce Hvozdu patří do Brány světů nahoře na Nebeském průsmyku, za svatyní na vrcholu hor. " +
            "A kdyby ses tam někdy potkal s Drakirrou… vyřiď jí, že jí les odpustil.",
        farewellSpeakerResId = R.drawable.makromon_23_mydrus,
        farewellSpeakerName = MYDRUS,
        stages = listOf(
            QuestStage(
                title = "Druid z mýtiny",
                text = "Pst… nelekej se. Jsem Mydrus, poslední druid Hvozdu. Tyhle fialové skvrny na mé srsti? " +
                    "Rudá hniloba. Té noci, kdy nad lesem přeletěla probuzená Drakirra, padal z jejích křídel žhavý popel – " +
                    "a kam dopadl, vyrašily rudé houby. Pijí z kořenů život. Když jsem je zkoušel zastavit, dostala se mi hniloba do krve.\n\n" +
                    "Musím vědět, jak daleko se rozlezla. Prohlédni tiché jezírko, houštinu a Starý dub. Buď opatrný – zvěř tam zdivočela.",
                speakerResId = R.drawable.makromon_23_mydrus,
                speakerName = MYDRUS,
                requirementType = RequirementType.VISIT_NODE,
                targetValue = 3,
                targetId = "jezirko_1,houstina,stary_dub"
            ),
            QuestStage(
                title = "Živá voda",
                text = "Je to horší, než jsem čekal. Hniloba vysušuje kořeny – jezírka mělčí a pramen pod dubem zmlkl. " +
                    "Staří druidi říkali, že les pije spolu s těmi, kdo ho chrání. Vypij dnes svůj denní cíl vody " +
                    "a já s každým tvým douškem zazpívám pramenu, aby se probudil. Vodu si zapisuj v aplikaci.",
                speakerResId = R.drawable.makromon_23_mydrus,
                speakerName = MYDRUS,
                requirementType = RequirementType.HIT_WATER,
                targetValue = 100
            ),
            QuestStage(
                title = "Zdivočelí",
                text = "Slyšíš to zurčení? Pramen se probudil! Jenže hniloba otrávila i mysl zvířat – Makromoni Hvozdu útočí " +
                    "na všechno, co se hýbe. Poraz jich ve Hvozdu čtyři. Neboj, neublížíš jim: souboj z nich vytřese spory " +
                    "a oni se zase vrátí k sobě.",
                speakerResId = R.drawable.makromon_23_mydrus,
                speakerName = MYDRUS,
                requirementType = RequirementType.BATTLE_BIOME,
                targetValue = 4,
                targetId = "FOREST"
            ),
            QuestStage(
                title = "Léčivý odvar",
                text = "Teď můžu uvařit odvar, který hnilobu zastaví aspoň ve mně. Recept je starý jako Hvozd sám: " +
                    "pět modrých bobulí (na záhonu na louce dozrají za hodinu), pět březových polen na oheň pod kotlíkem " +
                    "a pět suchých listů – ty nosí listoví Makromoni. Až to budeš mít, přines mi to sem na mýtinu.",
                speakerResId = R.drawable.makromon_23_mydrus,
                speakerName = MYDRUS,
                requirementType = RequirementType.DELIVER_ITEMS,
                targetValue = 1,
                targetId = "berry_blue:5,log_birch:5,mat_leaf_dry:5"
            ),
            QuestStage(
                title = "Po kořenech",
                text = "Ach… to je lepší. Poprvé po dlouhé době cítím tlapky. A s čistou hlavou konečně vidím, kam hniloba míří: " +
                    "všechna její vlákna se pod zemí sbíhají ke Starému dubu. Musíme po kořenech projít celý les, " +
                    "abychom našli její jádro. Ujdi dnes 7000 kroků – půjdu s tebou.",
                speakerResId = R.drawable.makromon_23_mydrus,
                speakerName = MYDRUS,
                requirementType = RequirementType.WALK_STEPS,
                targetValue = 7000
            ),
            QuestStage(
                title = "Hlas Starého dubu",
                text = "Mmmmm… maličký druide… a ty, poutníku s parťákem. Jsem Starý dub. Pamatuji časy, kdy mezi světy " +
                    "vedly brány a draci je hlídali. V mé dřeni zraje Srdce Hvozdu – klíč, kterým druidi kdysi zapečetili " +
                    "Bránu světů.\n\nHniloba ho chce. V mých kořenech se usadil SOULORD, pán hniloby, a pije moje světlo. " +
                    "Přijď ke mně a vyžeň ho… dřív, než Srdce zčerná.",
                speakerResId = R.drawable.npc_stary_dub,
                speakerName = DUB,
                requirementType = RequirementType.STORY_FLAG,
                targetValue = 1,
                targetId = "forest_rot_defeated",
                hint = "Soulord pořád sídlí v mých kořenech… Klepni na Starý dub na severu Hvozdu a vyžeň ho."
            )
        )
    )

    val ALL: List<QuestDefinition> by lazy { listOf(TOWN_INTRO_QUEST, MEADOW_QUEST, MOUNTAINS_QUEST, FOREST_QUEST) }

    fun byId(id: String): QuestDefinition? = ALL.firstOrNull { it.id == id }
}

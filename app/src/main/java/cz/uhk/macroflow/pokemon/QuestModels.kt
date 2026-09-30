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
    STORY_FLAG,
    /** Mít předmět (nic se neodebírá) – např. vyrobenou síťku; targetId = itemId (docs/adr/0050). */
    HAVE_ITEM,
    /**
     * Odpracovat od začátku fáze v každé z dovedností aspoň targetValue minut (i AFK);
     * targetId = "logging,mining,bugcatching", metadata = minuty nejslabší z nich (docs/adr/0050).
     */
    AFK_MINUTES
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
    val farewellSpeakerName: String = "",
    /** Název kapitoly v deníku. */
    val chapter: String = "DOBRODRUŽSTVÍ",
    /** Vedlejší linka (v deníku za hlavními kapitolami). */
    val side: Boolean = false,
    /** Tajná linka – v deníku zlatolesklá stránka, úplně na konci. */
    val secret: Boolean = false
)

// Objekt se všemi questy ve hře
object QuestRegistry {
    private const val GUDWIN = "Gudwin Oliver"
    private const val KRAL = "Král Mlsák"

    val TOWN_INTRO_QUEST = QuestDefinition(
        id = "town_intro_oliver",
        chapter = "I · MĚSTO",
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
        chapter = "II · LOUKA",
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
        chapter = "III · HORY",
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
        chapter = "IV · HVOZD",
        farewell = "Hvozd zase dýchá – a já taky. Mycité se vracejí na mýtinu a rudé houby usychají. " +
            "Srdce Hvozdu patří do Brány světů nahoře na Nebeském průsmyku, za svatyní na vrcholu hor. " +
            "A kdyby ses tam někdy potkal s Drakirrou… vyřiď jí, že jí les odpustil.\n\n" +
            cz.uhk.macroflow.pokemon.story.SecretGrove.MYDRUS_HINT,
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

    // ════════════════════════════════════════════════════════════════════════
    // TAJEMSTVÍ: Zapomenutý háj – duch Elderana, posledního Strážce brány (docs/adr/0046)
    // Soulord byla jeho zkažená duše. Odhalí, že hniloba přišla zpoza Brány světů
    // od Pána popela a že Drakirra je strážkyně, ne viník.
    // ════════════════════════════════════════════════════════════════════════
    private const val SEPOT = "Šepot"
    private const val ELDERAN = "Elderan"

    val SECRET_GROVE_QUEST = QuestDefinition(
        id = "secret_grove",
        chapter = "✦ ZAPOMENUTÝ HÁJ",
        secret = true,
        farewell = "Háj je tichý. Na hrobovém kameni se ve svitu měsíce třpytí rosa ve tvaru parohů a studánka " +
            "odráží zase jen hvězdy tohohle světa. Elderan odešel – ale jeho deník ti zůstal. Čti ho, než projdeš branou.",
        farewellSpeakerResId = R.drawable.npc_elderan,
        farewellSpeakerName = "Zapomenutý háj",
        stages = listOf(
            QuestStage(
                title = "Kamenný kruh",
                text = "…konečně… někdo prošel trním. Neboj se mě, poutníku. Jsem jen ozvěna – to, co zbylo, když jsi " +
                    "v kořenech Starého dubu porazil Soulorda. Kdysi jsem měl jméno. Abych si na něj vzpomněl, " +
                    "potřebuju, aby sis přečetl, co jsme vytesali do kamenů.\n\nObejdi kruh a prohlédni všechny tři kameny s obrazy.",
                speakerResId = R.drawable.npc_elderan,
                speakerName = SEPOT,
                requirementType = RequirementType.VISIT_NODE,
                targetValue = 3,
                targetId = "mural_1,mural_2,mural_3"
            ),
            QuestStage(
                title = "Noční bdění",
                text = "Ano… obrazy si pamatuju. Slova ne. Vzpomínky duchů se vracejí jen v noci, když měsíc stojí nad háji. " +
                    "Přijď k oltáři, až padne tma – mezi devátou večer a pátou ráno – a posaď se ke mně.",
                speakerResId = R.drawable.npc_elderan,
                speakerName = SEPOT,
                requirementType = RequirementType.STORY_FLAG,
                targetValue = 1,
                targetId = "grove_vigil",
                hint = "Oltář se probouzí jen v noci, mezi 21:00 a 5:00. Přijď k němu po setmění."
            ),
            QuestStage(
                title = "Dary mrtvým",
                text = "Teď už vím, kdo jsem. Jmenuju se Elderan. Byl jsem posledním Strážcem brány a Mydrusovým učitelem.\n\n" +
                    "Aby moje duše unesla pravdu, kterou ti musím říct, potřebuje kotvu. Přines na oltář staré dary pro mrtvé: " +
                    "tři malé dušičky (nosí je duchové, kteří v noci bloudí krajem), tři vodní perly a jeden živý list. " +
                    "Duše, voda a život.",
                speakerResId = R.drawable.npc_elderan,
                speakerName = ELDERAN,
                requirementType = RequirementType.DELIVER_ITEMS,
                targetValue = 1,
                targetId = "mat_soul_wisp:3,mat_water_pearl:3,mat_leaf_living:1"
            ),
            QuestStage(
                title = "Hranice lesa",
                text = "Cítím, jak se mi vrací síla. Dokud jsem žil, obcházel jsem každou noc hranice Hvozdu, aby popel " +
                    "nenašel cestu dovnitř. Obejdi je dnes za mě – ujdi 10 000 kroků. Každý tvůj krok je kámen v mé staré zdi.",
                speakerResId = R.drawable.npc_elderan,
                speakerName = ELDERAN,
                requirementType = RequirementType.WALK_STEPS,
                targetValue = 10000
            ),
            QuestStage(
                title = "Poslední slovo strážce",
                text = "Teď ti můžu říct pravdu.\n\nRudá hniloba nebyl Drakiřin popel. Drakirra není zlá – je to strážkyně brány. " +
                    "Spala na vrcholu hor, aby svou vahou držela pečeť. Za Branou světů leží Popelavý kraj a v něm vládne " +
                    "Ten, který spaluje – Pán popela. Jednou už k nám prošel a my druidi jsme ho zahnali zpátky za cenu všeho.\n\n" +
                    "Když jsi vložil krystaly, drak se probudil a pečeť povolila. Popel, který padal na Hvozd, přišel zpoza brány " +
                    "a šel po Drakiřině stopě. Mě, zakletého v kořenech jako strážce Srdce, proměnil v Soulorda.\n\n" +
                    "Jdi k mému hrobu vedle oltáře a pusť mě. Pod kamenem najdeš můj deník – budeš ho potřebovat.",
                speakerResId = R.drawable.npc_elderan,
                speakerName = ELDERAN,
                requirementType = RequirementType.STORY_FLAG,
                targetValue = 1,
                targetId = "grove_released",
                hint = "Klepni na můj hrob vedle oltáře a pusť mou duši."
            )
        )
    )

    // ════════════════════════════════════════════════════════════════════════
    // DOLY – havíř Vendelín a šichtovní kniha (docs/adr/0050)
    // Učí chytat hmyz a zkouší hráče ze všech sběrných dovedností. Kniha je kalibrační
    // protokol Kustodiátu: hráč je Okruh 213, všechny předchozí zápisy psal on sám.
    // ════════════════════════════════════════════════════════════════════════
    private const val VENDELIN = cz.uhk.macroflow.pokemon.story.Vendelin.NAME

    val MINES_QUEST = QuestDefinition(
        id = cz.uhk.macroflow.pokemon.story.Vendelin.QUEST_ID,
        chapter = "⚒ DOLY",
        side = true,
        farewell = "Hó! Živá duše! Nový na šichtě? Já jsem Vendelín, havíř. Posaď se, ať ti ukážu, jak se chytají jiskřivky…\n\n" +
            "…počkej. Proč nosíš můj kahan? A proč máš ruce od sazí, jako bys tu byl celou šichtu?\n\nNo nic. Hlavně na mě nezapomeň. Já na tebe určitě ne.",
        farewellSpeakerResId = R.drawable.npc_vendelin,
        farewellSpeakerName = VENDELIN,
        stages = listOf(
            QuestStage(
                title = "Světlo v hlubině",
                text = "Hó! Živá duše! A se stínem, to se tu dole jen tak nevidí. Jsem Vendelín, havíř – poslední ze šichty. " +
                    "Ostatní… šli napřed. Kam? No… napřed.\n\nVidíš ten kahan? Nesvítí v něm olej, ale jiskřivky – mušky, co se živí " +
                    "světlem. Bez nich tu oslepneš dřív, než řekneš švec. Na rezavém háku nad převráceným vozíkem visí moje stará síťka. " +
                    "Vezmi si ji, stejně mi už ruce nesedí na násadu.",
                speakerResId = R.drawable.npc_vendelin,
                speakerName = VENDELIN,
                requirementType = RequirementType.STORY_FLAG,
                targetValue = 1,
                targetId = cz.uhk.macroflow.pokemon.cave.MinesMap.NET_TAKEN_KEY,
                hint = "Síťka visí na rezavém háku nad převráceným vozíkem, vpravo od kolejí."
            ),
            QuestStage(
                title = "Naplň kahan",
                text = "Sedí ti v ruce, co? Teď se postav k lucerně támhle na sloupu a chytej. Mušky se chytají samy – síťku " +
                    "jen držíš a čekáš. Klidně odejdi z Makrosvěta, chytá se dál. Jen nechoď po mapě pryč od hejna, to se lekneš " +
                    "a síťku pustíš. Čím lepší síťka a čím víc se naučíš, tím rychleji to jde.\n\n" +
                    "Přines mi deset jiskřivek do kahanu. Zvláštní potvůrky… když ti sednou do dlaně, chvíli jen tak sedí a čekají. " +
                    "Jako by čekaly, až jim řekneš jméno.",
                speakerResId = R.drawable.npc_vendelin,
                speakerName = VENDELIN,
                requirementType = RequirementType.DELIVER_ITEMS,
                targetValue = 1,
                targetId = "bug_spark:10"
            ),
            QuestStage(
                title = "Lepší síťka",
                text = "Svítí jako za mlada! Jenže stará síťka krystalové mušky neudrží – mají křídla ostrá jako sklo a oka " +
                    "ti rozpářou. Stav se u pracovního stolu na louce a udělej si měděnou: obruč z mědi, násada z dubu a " +
                    "pár jiskřivek na návnadu. A nasaď si ji, ať tě nevidím s tou rezavou.",
                speakerResId = R.drawable.npc_vendelin,
                speakerName = VENDELIN,
                requirementType = RequirementType.HAVE_ITEM,
                targetValue = 1,
                targetId = "tool_net_copper",
                hint = "Měděnou síťku vyrobíš u pracovního stolu na louce (Nástroje)."
            ),
            QuestStage(
                title = "Pláč krystalů",
                text = "Teď nahoru do pukliny. Tam, kde končí výdřeva, rostou krystaly. Stará parta jim říkala slzy. Vylámali " +
                    "jsme je a do rána dorostly – pořád na stejném místě, pořád stejně rudé, jako by tam někdo nad námi " +
                    "pořád plakal. Mušky z nich pijí světlo. Přines mi pět krystalových mušek. A kdyby ses cítil pozorovaný… " +
                    "to je normální. Nekoukej se na ně dlouho.",
                speakerResId = R.drawable.npc_vendelin,
                speakerName = VENDELIN,
                requirementType = RequirementType.DELIVER_ITEMS,
                targetValue = 1,
                targetId = "bug_crystal:5"
            ),
            QuestStage(
                title = "Šichtovní kniha",
                text = "Dobrá práce. Ale na šichtu tě pustím, až tě zapíšu do knihy, a do knihy tě zapíšu, až vím, co vydržíš. " +
                    "Předpis je předpis: tři hodiny u sekery, tři u krumpáče a tři u síťky. Nemusíš u toho stát, klidně spi, " +
                    "jez, choď – čas se počítá, dokud tvůj nástroj pracuje. Počítá se jen to, co odpracuješ ode dneška.\n\n" +
                    "Kdo mi ten předpis dal? Hm. Ten, kdo dává předpisy. Vrať se, až budeš mít všech devět hodin.",
                speakerResId = R.drawable.npc_vendelin,
                speakerName = VENDELIN,
                requirementType = RequirementType.AFK_MINUTES,
                targetValue = cz.uhk.macroflow.pokemon.story.Vendelin.TEST_HOURS * 60,
                targetId = cz.uhk.macroflow.pokemon.story.Vendelin.TEST_SKILLS.joinToString(",")
            ),
            QuestStage(
                title = "Podpis",
                text = "Devět hodin, na minutu. Přesně podle předpisu… jako minule. Ne, nic, to jsem si jen tak mumlal.\n\n" +
                    "Kniha leží na bedně vedle lucerny. Otevři ji na posledním řádku a podepiš se. Pak ti dám svůj kahan – " +
                    "já už ho nebudu potřebovat. Moje šichta tady dole brzy skončí.",
                speakerResId = R.drawable.npc_vendelin,
                speakerName = VENDELIN,
                requirementType = RequirementType.STORY_FLAG,
                targetValue = 1,
                targetId = cz.uhk.macroflow.pokemon.story.Vendelin.BOOK_SIGNED_KEY,
                hint = "Šichtovní kniha leží na bedně vlevo dole u vstupu. Klepni na ni a podepiš se."
            )
        )
    )

    /** Pořadí v deníku: hlavní kapitoly v pořadí příběhu, pak vedlejší linky, pak tajné. */
    val ALL: List<QuestDefinition> by lazy { listOf(TOWN_INTRO_QUEST, MEADOW_QUEST, MOUNTAINS_QUEST, FOREST_QUEST, MINES_QUEST, SECRET_GROVE_QUEST) }

    /** Pořadí kapitoly v deníku (neznámé na konec). */
    fun journalOrder(id: String): Int {
        val q = byId(id) ?: return Int.MAX_VALUE
        val group = if (q.secret) 2 else if (q.side) 1 else 0
        return group * 100 + ALL.indexOf(q)
    }

    fun byId(id: String): QuestDefinition? = ALL.firstOrNull { it.id == id }
}

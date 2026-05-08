package cz.uhk.macroflow.pokemon.quests

import cz.uhk.macroflow.R

enum class RequirementType {
    VISIT_NODE,      // Návštěva konkrétního bodu na mapě
    CAPTURE_SPECIFIC,// Chycení konkrétního Makromona
    WALK_STEPS,      // Nachození kroků
    TALK_TO_NPC,    // Jen odkliknutí dialogu
    LOG_MEAL,        // Nové: Zapsání jídla
    BATTLE_TYPE,     // Nové: Souboj s konkrétním typem
    SCAN_BARCODE
}

data class QuestStage(
    val title: String,
    val text: String,
    val speakerResId: Int,
    val requirementType: RequirementType,
    val targetValue: Int,            // Počet (např. 3000 kroků nebo 3 budovy)
    val targetId: String? = null     // Např. "starter_bush" nebo "domov,pokedex,obchod"
)

data class QuestDefinition(
    val id: String,
    val stages: List<QuestStage>
)

// Objekt se všemi questy ve hře
object QuestRegistry {
    val TOWN_INTRO_QUEST = QuestDefinition(
        id = "town_intro_oliver",
        stages = listOf(
            QuestStage(
                title = "První kroky městem",
                text = "Vítej v Town, hrdino! Já jsem tvůj Makromom Gudwin, ale přátelé mi říkají Olivere. Než se vydáš do divočiny, musíš vědět, kde co je. Projdi si své zázemí – mrkni domů, prohlédni si Makrodex a nezapomeň se stavit v Obchodě. Až budeš mít mapu v malíčku, přijď za mnou!",
                speakerResId = R.drawable.gudwin_oliver,
                requirementType = RequirementType.VISIT_NODE,
                targetValue = 3,
                targetId = "domov,pokedex,obchod"
            ),
            QuestStage(
                title = "Tajemství v křoví",
                text = "Slyšel jsi to? Za tvým domem v tom hustém křoví se něco hýbe. Vypadá to, že si tě vyhlédl tvůj první parťák! Běž tam a zjisti, kdo na tebe čeká.",
                speakerResId = R.drawable.gudwin_oliver,
                requirementType = RequirementType.CAPTURE_SPECIFIC,
                targetValue = 1,
                targetId = "starter_bush"
            ),
            QuestStage(
                title = "Dechberoucí túra",
                text = "Tvůj parťák je plný energie a Meadow je ještě daleko. Abych tě mohl pustit dál, musím vědět, že na to máš kondici. Rozhýbej nohy! Jakmile ujdeme společně 3000 kroků, cesta se ti otevře.",
                speakerResId = R.drawable.gudwin_oliver,
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
}
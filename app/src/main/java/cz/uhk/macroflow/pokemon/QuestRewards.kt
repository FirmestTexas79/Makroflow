package cz.uhk.macroflow.pokemon.quests

/**
 * Odměny za fáze questů (docs/adr/0043). Čistý Kotlin – QuestManager se ptá po splnění fáze,
 * předmět připíše MakromonMapActivity a hláška se přehraje před úvodem další fáze.
 */
object QuestRewards {
    data class Reward(val itemId: String, val label: String, val line: String)

    private val REWARDS: Map<Pair<String, Int>, Reward> = mapOf(
        ("meadow_mastery" to 0) to Reward("tool_axe_old", "Stará sekera",
            "Počkej, ještě něco! Tohle do mě zarazil nějakej lovec před tebou. Když jsem ho pozdravil, " +
                "tak s křikem utekl směrem do lesa. Vytáhni si ji, stejně mě pořád píchala do větví."),
        ("mountains_macro_king" to 0) to Reward("tool_pickaxe_old", "Starý krumpáč",
            "Eeee… koukni se mi prosím zezadu na krk. Něco mě tam tak už čtyři roky svědí. … Krumpáč?! " +
                "Tak to vysvětluje hodně. Nech si ho, hrdino – a nikomu ani slovo."),
        // docs/adr/0045 – po vyhnání Soulorda vydá Starý dub Srdce Hvozdu
        ("forest_heart" to 5) to Reward("srdce_hvozdu", "Srdce Hvozdu",
            "Hniloba… odchází. Cítím, jak mi mízou znovu stoupá světlo. Poutníku, vezmi si Srdce Hvozdu. " +
                "Druidi ho do mě kdysi vložili, abych ho chránil, dokud nepřijde někdo, kdo les uzdraví. " +
                "Nes ho k Bráně světů – na Nebeský průsmyk za svatyní. Vlož ho do lůžka… a brána se otevře.")
    )

    /** Odměna za dokončení fáze [stageIndex] questu [questId], nebo null. */
    fun forStage(questId: String, stageIndex: Int): Reward? = REWARDS[questId to stageIndex]

    val ALL: Collection<Reward> get() = REWARDS.values
}

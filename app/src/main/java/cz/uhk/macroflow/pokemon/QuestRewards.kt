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
                "Tak to vysvětluje hodně. Nech si ho, hrdino – a nikomu ani slovo.")
    )

    /** Odměna za dokončení fáze [stageIndex] questu [questId], nebo null. */
    fun forStage(questId: String, stageIndex: Int): Reward? = REWARDS[questId to stageIndex]

    val ALL: Collection<Reward> get() = REWARDS.values
}

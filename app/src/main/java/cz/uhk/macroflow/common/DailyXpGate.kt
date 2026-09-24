package cz.uhk.macroflow.common

/**
 * Denní odměna 20 XP: jednou za kalendářní den za první spuštění aplikace (čistý Kotlin, test).
 *
 * Dřív se den hlídal zvlášť pro každého Makromona (klíč lastXpDay_{id}). Výměna aktivního
 * Makromona ve světě Makromonů pak po návratu (onResume) vyplatila 20 XP znovu.
 */
object DailyXpGate {

    const val KEY = "dailyXpDate"
    const val REWARD_XP = 20

    /** true = dnes se ještě neudělovalo. */
    fun shouldAward(lastDate: String?, today: String): Boolean = lastDate != today

    /**
     * Přechod ze starého klíče: když aktivní Makromon dostal odměnu podle starého
     * systému dnes, bere se dnešek jako vyčerpaný.
     */
    fun effectiveLastDate(storedDate: String?, legacyDayOfYear: Int, todayDayOfYear: Int, today: String): String? =
        storedDate ?: if (legacyDayOfYear == todayDayOfYear) today else null
}

package cz.uhk.macroflow.pokemon.story

import cz.uhk.macroflow.pokemon.cave.SkyPass

/**
 * Čistá logika synchronizace příběhu (docs/adr/0044, bez Androidu – pokryto testy).
 * Příznak `klíč` v GamePrefs se zrcadlí jako předmět `story_<klíč>` = 1 v user_items (Firebase).
 */
object StoryProgress {
    const val PREFIX = "story_"

    fun itemId(key: String) = PREFIX + key

    /** Klíč příznaku z id předmětu (null, pokud to není příznak příběhu). */
    fun keyOf(itemId: String): String? =
        itemId.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.takeIf { isStoryKey(it) }

    /** Výsledek sloučení: co doplnit do GamePrefs a co do synchronizovaných předmětů. */
    data class Merge(val toPrefs: Set<String>, val toItems: Set<String>)

    /**
     * [prefs] = příběhové klíče nastavené lokálně, [items] = klíče ze synchronizovaných předmětů.
     * Postup se jen sčítá – co je kdekoli, platí všude.
     */
    fun merge(prefs: Set<String>, items: Set<String>): Merge {
        val p = prefs.filter(::isStoryKey).toSet()
        val i = items.filter(::isStoryKey).toSet()
        return Merge(toPrefs = i - p, toItems = p - i)
    }

    /** Pevné klíče příběhu (kromě strážců a krystalů, které mají předponu). */
    val STORY_KEYS = setOf(
        "crystals_placed", "legend_faced",
        SkyPass.VISITED_KEY, SkyPass.GATE_SEEN_KEY, SkyPass.HEART_PLACED_KEY,
        ForestHeart.ROT_DEFEATED_KEY
    ) + SecretGrove.KEYS

    fun isStoryKey(key: String): Boolean =
        key.startsWith("boss_defeated_") || key.startsWith("crystal_") || key in STORY_KEYS
}

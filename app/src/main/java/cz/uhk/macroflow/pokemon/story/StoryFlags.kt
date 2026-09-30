package cz.uhk.macroflow.pokemon.story

import android.content.Context
import cz.uhk.macroflow.pokemon.skills.SkillStore

/**
 * Postup příběhu napříč lokacemi, telefony a přeinstalacemi (docs/adr/0044).
 *
 * Příznaky příběhu (poražení strážci, krystaly, legenda, průsmyk, Brána světů) dřív žily jen
 * v GamePrefs. Teď se každý zrcadlí i do user_items jako `story_<klíč>` = 1, které se synchronizují
 * přes Firebase. Při startu mapy se obě strany sloučí ([sync]) – postup se nikdy neztratí.
 */
object StoryFlags {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

    fun isSet(ctx: Context, key: String): Boolean = prefs(ctx).getBoolean(key, false)

    /** Všechny nastavené příznaky příběhu (pro Vhled a roztržené listy). */
    fun all(ctx: Context): Set<String> = prefs(ctx).all.filter { (k, v) -> v == true && StoryProgress.isStoryKey(k) }.keys

    /** Skrytý Vhled hráče (docs/adr/0047). */
    fun insight(ctx: Context): Int = Insight.level(all(ctx))

    /** Nastaví příznak hned v GamePrefs; zápis do DB/Firebase běží na pozadí. */
    fun set(ctx: Context, key: String) {
        prefs(ctx).edit().putBoolean(key, true).apply()
        val app = ctx.applicationContext
        Thread { runCatching { mirror(app, key) } }.start()
    }

    private fun mirror(ctx: Context, key: String) {
        val id = StoryProgress.itemId(key)
        if (SkillStore.count(ctx, id) <= 0) SkillStore.add(ctx, id, 1)
    }

    /** Smaže příznak všude (jen debug reset příběhu). Volat mimo hlavní vlákno. */
    fun clear(ctx: Context, key: String) {
        prefs(ctx).edit().remove(key).apply()
        val id = StoryProgress.itemId(key)
        val n = SkillStore.count(ctx, id)
        if (n > 0) SkillStore.consume(ctx, id, n)
    }

    /**
     * Sloučí GamePrefs se synchronizovanými předměty. Obnoví postup po přeinstalaci a zároveň
     * doplní do předmětů postup získaný ještě před zavedením synchronizace.
     * Volat mimo hlavní vlákno. Vrací počet příznaků obnovených do GamePrefs.
     */
    fun sync(ctx: Context): Int {
        val p = prefs(ctx)
        val local = p.all.filter { (k, v) -> v == true && StoryProgress.isStoryKey(k) }.keys
        val synced = SkillStore.counts(ctx).filter { (_, v) -> v > 0 }.keys.mapNotNull(StoryProgress::keyOf).toSet()
        val m = StoryProgress.merge(local, synced)
        if (m.toPrefs.isNotEmpty()) p.edit().apply { m.toPrefs.forEach { putBoolean(it, true) } }.apply()
        m.toItems.forEach { SkillStore.add(ctx, StoryProgress.itemId(it), 1) }
        return m.toPrefs.size
    }
}

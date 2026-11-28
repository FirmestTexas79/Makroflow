package cz.uhk.macroflow.pokemon.forge

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.pokemon.skills.SkillStore
import java.time.LocalDate

/**
 * Uložení Výhně (docs/adr/0118). Uhlíky a vylepšení za uhlíky (mění se pořád, přetavení je stejně maže) leží
 * v SharedPreferences „Forge“; přetavení a pasivní bonusy jsou trvalé, a proto v user_items (záloha do Firebase):
 * `forge_reborn`, `forge_p_<id>`. Volat mimo hlavní vlákno.
 */
object ForgeStore {
    private const val REBORN_ITEM = "forge_reborn"
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("Forge", Context.MODE_PRIVATE)

    fun load(ctx: Context): Forge.State {
        val p = prefs(ctx)
        val coals = Double.fromBits(p.getLong("coals", 0.0.toRawBits()))
        return Forge.State(
            coals = coals,
            // starší uložení (před odemykáním) „earned“ nemá – vezme se zůstatek; vylepšení s úrovní jsou odemčená tak jako tak
            earned = if (p.contains("earned")) Double.fromBits(p.getLong("earned", 0.0.toRawBits())) else coals,
            levels = Forge.Up.entries.map { p.getInt("l${it.ordinal}", 0) },
            reborns = SkillStore.count(ctx, REBORN_ITEM),
            passives = Forge.Passive.entries.mapNotNull { pa -> SkillStore.count(ctx, pa.itemId).takeIf { it > 0 }?.let { pa.id to it } }.toMap()
        )
    }

    /** Uloží uhlíky a vylepšení; trvalé části jen když se změnily. */
    fun save(ctx: Context, st: Forge.State, before: Forge.State? = null) {
        prefs(ctx).edit().apply {
            putLong("coals", st.coals.toRawBits())
            putLong("earned", st.earned.toRawBits())
            st.levels.forEachIndexed { i, l -> putInt("l$i", l) }
            putLong("seen", System.currentTimeMillis())
        }.apply()
        if (before == null || before.reborns != st.reborns) SkillStore.set(ctx, REBORN_ITEM, st.reborns)
        Forge.Passive.entries.forEach { pa ->
            val l = st.passives[pa.id] ?: 0
            if (before == null && l > 0 || before != null && (before.passives[pa.id] ?: 0) != l) SkillStore.set(ctx, pa.itemId, l)
        }
    }

    /** Kroky dnes a spánek poslední noci (hodinky / Health Connect). */
    fun life(ctx: Context): Forge.Life {
        val db = AppDatabase.getDatabase(ctx)
        val today = LocalDate.now().toString()
        val steps = runCatching { db.stepsDao().getStepsForDateSync(today)?.count }.getOrNull()
            ?: runCatching { db.wearableDao().getDaySync(today)?.steps }.getOrNull() ?: 0
        val sleep = runCatching { db.wearableDao().getDaySync(today)?.sleepSec }.getOrNull() ?: 0
        return Forge.Life(steps, sleep / 3600.0)
    }

    /** Co výheň vydělala, když hra neběžela (od posledního uložení). */
    fun offline(ctx: Context, st: Forge.State, life: Forge.Life, now: Long = System.currentTimeMillis()): Double {
        val seen = prefs(ctx).getLong("seen", 0L)
        return if (seen <= 0) 0.0 else Forge.offlineGain(st, life, now - seen)
    }

    fun bonuses(ctx: Context): Forge.Bonuses = Forge.bonuses(load(ctx))

    /** Suroviny pasivního bonusu: má hráč dost? */
    fun hasMaterials(ctx: Context, p: Forge.Passive): Boolean = p.materials.all { (r, n) -> SkillStore.count(ctx, r.itemId) >= n }

    /** Koupí pasivní bonus: odečte uhlíky a suroviny. Vrací nový stav, nebo null. */
    fun buyPassive(ctx: Context, st: Forge.State, p: Forge.Passive): Forge.State? {
        val lvl = Forge.passiveLevel(st, p)
        val coals = Forge.passiveCoals(st, p)
        if (lvl >= p.maxLevel || st.coals < coals || !hasMaterials(ctx, p)) return null
        p.materials.forEach { (r, n) -> if (!SkillStore.consume(ctx, r.itemId, n)) return null }
        val next = st.copy(coals = st.coals - coals, passives = st.passives + (p.id to lvl + 1))
        save(ctx, next, st)
        return next
    }
}

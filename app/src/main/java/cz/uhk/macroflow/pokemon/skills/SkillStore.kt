package cz.uhk.macroflow.pokemon.skills

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.pokemon.UserItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Úložiště dovedností, surovin, záhonů a týmu (docs/adr/0034). Vše kromě týmu leží v tabulce
 * user_items (stejně jako Makrobally), takže se to samo zálohuje do Firebase:
 * `skill_xp_<dovednost>` = celkové XP, `skill_node_<uzel>` = 1, suroviny pod svým ID,
 * `garden_<i>` = zakódovaný záhon. Volat mimo hlavní vlákno.
 */
object SkillStore {

    /** ID předmětů, které nejsou vidět v inventáři (interní stav). */
    fun isInternal(itemId: String) = listOf("skill_", "garden_", "equip_", "gather_", "starter_").any { itemId.startsWith(it) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun dao(ctx: Context) = AppDatabase.getDatabase(ctx).userItemDao()

    private fun upload(ctx: Context, itemId: String) {
        if (!FirebaseRepository.isLoggedIn) return
        val item = dao(ctx).getItem(itemId) ?: UserItemEntity(itemId, 0)
        scope.launch { runCatching { FirebaseRepository.uploadUserItem(item) } }
    }

    fun count(ctx: Context, itemId: String): Int = dao(ctx).getItemCount(itemId) ?: 0

    fun counts(ctx: Context): Map<String, Int> = dao(ctx).getAllItems().associate { it.itemId to it.quantity }

    fun add(ctx: Context, itemId: String, amount: Int) {
        if (amount == 0) return
        dao(ctx).addItem(itemId, amount)
        upload(ctx, itemId)
    }

    fun consume(ctx: Context, itemId: String, amount: Int): Boolean {
        val ok = dao(ctx).consumeItem(itemId, amount)
        if (ok) upload(ctx, itemId)
        return ok
    }

    private fun set(ctx: Context, itemId: String, value: Int) {
        dao(ctx).insertOrUpdateItem(UserItemEntity(itemId, value))
        upload(ctx, itemId)
    }

    // ── Dovednosti ──

    fun state(ctx: Context): SkillState {
        val all = counts(ctx)
        return SkillState(
            xp = Skill.entries.associateWith { (all[it.xpItemId] ?: 0).toLong() },
            unlocked = SkillTree.NODES.filter { (all[it.itemId] ?: 0) > 0 }.map { it.id }.toSet()
        )
    }

    data class XpResult(val skill: Skill, val gained: Int, val oldLevel: Int, val newLevel: Int, val state: SkillState) {
        val leveledUp: Boolean get() = newLevel > oldLevel
        /** Kolik dovednostních bodů přibylo. */
        val newPoints: Int get() = SkillMath.skillPointsEarned(newLevel) - SkillMath.skillPointsEarned(oldLevel)
    }

    fun addXp(ctx: Context, skill: Skill, amount: Int): XpResult {
        val before = state(ctx)
        val old = before.level(skill)
        val total = (before.totalXp(skill) + amount.coerceAtLeast(0)).coerceAtMost(Int.MAX_VALUE.toLong())
        set(ctx, skill.xpItemId, total.toInt())
        val after = before.copy(xp = before.xp + (skill to total))
        return XpResult(skill, amount, old, after.level(skill), after)
    }

    /** Odemkne uzel stromu, pokud na něj jsou body. */
    fun unlock(ctx: Context, nodeId: String): Boolean {
        val node = SkillTree.node(nodeId) ?: return false
        if (!state(ctx).canUnlock(node)) return false
        set(ctx, node.itemId, 1)
        return true
    }

    // ── Záhony ──

    fun plots(ctx: Context): List<Garden.Plot> = (0 until Garden.PLOTS).map { Garden.decode(count(ctx, Garden.itemId(it))) }

    fun plant(ctx: Context, index: Int, berry: Berry, nowEpochSec: Long): Boolean {
        if (!consume(ctx, berry.seedItemId, 1)) return false
        set(ctx, Garden.itemId(index), Garden.encode(berry, nowEpochSec))
        return true
    }

    /** Sklizeň: vrátí počet bobulí (1–2) a výsledek XP, nebo null, když ještě neroste nic hotového. */
    fun harvest(ctx: Context, index: Int, nowEpochSec: Long): Pair<Int, XpResult>? {
        val st = state(ctx)
        val plot = Garden.decode(count(ctx, Garden.itemId(index)))
        val berry = plot.berry ?: return null
        if (!Garden.isReady(plot, nowEpochSec, st.growthSpeedup)) return null
        set(ctx, Garden.itemId(index), 0)
        val n = SkillMath.rollDouble(st.passive(Skill.HARVESTING))
        add(ctx, berry.berryItemId, n)
        val xp = addXp(ctx, Skill.HARVESTING, st.gain(Skill.HARVESTING, berry.harvestXp.toDouble()))
        return n to xp
    }

    // ── Výroba ──

    /** Vyrobí [times]× daný ball. Vrací (vyrobeno kusů, XP) nebo null, když chybí suroviny. */
    fun craft(ctx: Context, ball: cz.uhk.macroflow.pokemon.balls.Makroball, times: Int): Pair<Int, XpResult>? {
        val owned = counts(ctx)
        val n = minOf(times, Crafting.maxCraftable(ball, owned))
        if (n <= 0) return null
        val st = state(ctx)
        Crafting.recipe(ball).forEach { (id, per) -> consume(ctx, id, per * n) }
        val made = (1..n).sumOf { Crafting.roll(st.passive(Skill.CRAFTING)) }
        add(ctx, ball.id, made)
        val xp = addXp(ctx, Skill.CRAFTING, st.gain(Skill.CRAFTING, Crafting.baseXp(ball).toDouble() * n))
        return made to xp
    }

    // ── Vybavení (docs/adr/0035) ──

    fun equipped(ctx: Context, slot: GearSlot): Gear? = Gear.fromCode(count(ctx, slot.itemId))

    fun equippedAll(ctx: Context): Map<GearSlot, Gear> {
        val all = counts(ctx)
        return GearSlot.entries.mapNotNull { sl -> Gear.fromCode(all[sl.itemId] ?: 0)?.let { sl to it } }.toMap()
    }

    /** Nasadí předmět (musí ho mít), null = sundat. */
    fun equip(ctx: Context, slot: GearSlot, gear: Gear?): Boolean {
        if (gear != null && count(ctx, gear.id) <= 0) return false
        set(ctx, slot.itemId, gear?.code ?: 0)
        return true
    }

    /** Startovní sekera a krumpáč (později odměna za úkol) – dají se jen jednou a hned nasadí. */
    fun ensureStarterTools(ctx: Context): Boolean {
        if (count(ctx, "starter_tools") > 0) return false
        add(ctx, Gear.OLD_AXE.id, 1); add(ctx, Gear.OLD_PICKAXE.id, 1)
        if (equipped(ctx, GearSlot.AXE) == null) set(ctx, GearSlot.AXE.itemId, Gear.OLD_AXE.code)
        if (equipped(ctx, GearSlot.PICKAXE) == null) set(ctx, GearSlot.PICKAXE.itemId, Gear.OLD_PICKAXE.code)
        set(ctx, "starter_tools", 1)
        return true
    }

    // ── Těžba a kácení (AFK) ──

    /** Efektivita pro místo: nasazený nástroj + level + strom. 0 = chybí nástroj. */
    fun efficiency(ctx: Context, spot: GatherSpot, st: SkillState = state(ctx)): Int =
        Gathering.efficiency(equipped(ctx, spot.toolSlot)?.power ?: 0, st.level(spot.skill), st.efficiencyBonus(spot.skill))

    fun activity(ctx: Context): Gathering.Activity? =
        Gathering.decode(count(ctx, Gathering.SPOT_ITEM), count(ctx, Gathering.SINCE_ITEM))

    fun startActivity(ctx: Context, spot: GatherSpot, nowSec: Long) {
        set(ctx, Gathering.SPOT_ITEM, spot.code)
        set(ctx, Gathering.SINCE_ITEM, Gathering.encodeSince(nowSec))
    }

    fun stopActivity(ctx: Context) { set(ctx, Gathering.SPOT_ITEM, 0) }

    data class GatherResult(val spot: GatherSpot, val claim: Gathering.Claim, val xp: XpResult?)

    /**
     * Vybere hotové kusy probíhající činnosti (suroviny + XP) a posune začátek počítání.
     * Null = žádná činnost; bez nástroje nebo pod potřebnou efektivitou se nic nepřičte.
     */
    fun claimActivity(ctx: Context, nowSec: Long): GatherResult? {
        val a = activity(ctx) ?: return null
        val st = state(ctx)
        val sec = Gathering.secondsPerUnit(a.spot, efficiency(ctx, a.spot, st))
            ?: return GatherResult(a.spot, Gathering.Claim(0, 0, nowSec, 0, false), null).also {
                set(ctx, Gathering.SINCE_ITEM, Gathering.encodeSince(nowSec))
            }
        val c = Gathering.claim(a, nowSec, sec, st.passive(a.spot.skill), st.afkCapHours(a.spot.skill))
        set(ctx, Gathering.SINCE_ITEM, Gathering.encodeSince(c.newSince))
        if (c.units == 0) return GatherResult(a.spot, c, null)
        add(ctx, a.spot.resource.itemId, c.amount)
        val xp = addXp(ctx, a.spot.skill, st.gain(a.spot.skill, a.spot.xp.toDouble() * c.units))
        return GatherResult(a.spot, c, xp)
    }

    // ── Tým ──

    private const val TEAM_KEY = "teamCapturedIds"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

    fun activeId(ctx: Context): Int? {
        val p = prefs(ctx)
        if (!p.getBoolean("pokemonAcquired", true)) return null
        return p.getInt("currentOnBarCapturedId", -1).takeIf { it > 0 }
    }

    /** Tým srovnaný s aktivním parťákem, počtem míst a existujícími Makromony. */
    fun team(ctx: Context): List<Int> {
        val existing = AppDatabase.getDatabase(ctx).capturedMakromonDao().getAllCaught().map { it.id }.toSet()
        val raw = Team.parse(prefs(ctx).getString(TEAM_KEY, null))
        val t = Team.normalize(raw, activeId(ctx), state(ctx).teamSlots, existing)
        if (t != raw) saveTeam(ctx, t)
        return t
    }

    fun saveTeam(ctx: Context, team: List<Int>) {
        prefs(ctx).edit().putString(TEAM_KEY, Team.format(team)).apply()
    }
}

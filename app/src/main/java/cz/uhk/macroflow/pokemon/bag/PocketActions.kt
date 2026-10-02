package cz.uhk.macroflow.pokemon.bag

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.pokemon.CapturedMakromonEntity
import cz.uhk.macroflow.pokemon.skills.SkillStore
import cz.uhk.macroflow.pokemon.skills.Team
import kotlinx.coroutines.launch

/**
 * Akce nad Makromony v Kapse (docs/adr/0060) – tým, parťák na liště, zámek, puštění.
 * Stejná pravidla jako obrazovka inventáře; parťák na mapě se obnoví sám přes GamePrefs.
 * Volat mimo hlavní vlákno.
 */
object PocketActions {

    /** Nahrání do cloudu na pozadí – akce nečeká na síť. */
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

    /** Datum chycení Makromona, který je teď parťákem na liště (−1 = žádný). */
    fun activeCaughtDate(ctx: Context): Long {
        val p = prefs(ctx)
        return if (p.getBoolean("pokemonAcquired", false)) p.getLong("currentOnBarCaughtDate", -1L) else -1L
    }

    /** Parťák na liště = první v týmu. */
    fun makeActive(ctx: Context, m: CapturedMakromonEntity) {
        prefs(ctx).edit()
            .putBoolean("pokemonAcquired", true)
            .putLong("currentOnBarCaughtDate", m.caughtDate)
            .putString("currentOnBarName", m.name.uppercase())
            .putInt("currentOnBarCapturedId", m.id)
            .apply()
        val slots = SkillStore.state(ctx).teamSlots
        SkillStore.saveTeam(ctx, Team.makeActive(SkillStore.team(ctx), m.id, slots))
    }

    sealed class TeamResult {
        data class Added(val size: Int, val slots: Int) : TeamResult()
        object Removed : TeamResult()
        data class Full(val slots: Int) : TeamResult()
    }

    /** Přidá do týmu, nebo z něj odebere. Když odejde parťák, nastoupí další v týmu. */
    fun toggleTeam(ctx: Context, m: CapturedMakromonEntity, all: List<CapturedMakromonEntity>): TeamResult {
        val team = SkillStore.team(ctx)
        val slots = SkillStore.state(ctx).teamSlots
        if (m.id in team) {
            val rest = Team.remove(team, m.id)
            SkillStore.saveTeam(ctx, rest)
            if (activeCaughtDate(ctx) == m.caughtDate) {
                val next = all.firstOrNull { it.id == rest.firstOrNull() }
                if (next != null) makeActive(ctx, next)
                else prefs(ctx).edit().putBoolean("pokemonAcquired", false).putLong("currentOnBarCaughtDate", -1L).apply()
            }
            return TeamResult.Removed
        }
        return when (val r = Team.add(team, m.id, slots)) {
            is Team.Result.Ok -> {
                SkillStore.saveTeam(ctx, r.team)
                if (activeCaughtDate(ctx) < 0) makeActive(ctx, m)
                TeamResult.Added(r.team.size, slots)
            }
            Team.Result.Full -> TeamResult.Full(slots)
        }
    }

    fun toggleLock(ctx: Context, m: CapturedMakromonEntity) {
        m.isLocked = !m.isLocked
        AppDatabase.getDatabase(ctx).capturedMakromonDao().updateMakromon(m)
        if (FirebaseRepository.isLoggedIn) scope.launch { runCatching { FirebaseRepository.uploadCapturedMakromon(m) } }
    }

    /** Pustí Makromona (smaže ho). Zamčeného ne. */
    fun release(ctx: Context, m: CapturedMakromonEntity): Boolean {
        if (m.isLocked) return false
        val wasActive = activeCaughtDate(ctx) == m.caughtDate
        AppDatabase.getDatabase(ctx).capturedMakromonDao().deleteMakromon(m)
        SkillStore.saveTeam(ctx, Team.remove(SkillStore.team(ctx), m.id))
        if (FirebaseRepository.isLoggedIn) scope.launch { runCatching { FirebaseRepository.deleteCapturedMakromon(m.caughtDate) } }
        if (wasActive) prefs(ctx).edit().putBoolean("pokemonAcquired", false).putLong("currentOnBarCaughtDate", -1L).apply()
        return true
    }

    /** Spooky Plate: spotřebuje se a na chvíli přiláká duchy (jako v inventáři). */
    fun useLureLamp(ctx: Context): Boolean {
        val p = prefs(ctx)
        if (p.getBoolean("ghostPlateActive", false)) return false
        if (!SkillStore.consume(ctx, BagItems.LURE_LAMP, 1)) return false
        p.edit().putBoolean("ghostPlateActive", true).apply()
        return true
    }
}

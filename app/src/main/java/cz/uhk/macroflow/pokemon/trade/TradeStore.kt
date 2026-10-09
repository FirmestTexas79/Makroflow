package cz.uhk.macroflow.pokemon.trade

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.pokemon.CapturedMakromonEntity
import cz.uhk.macroflow.pokemon.bag.PocketActions
import cz.uhk.macroflow.pokemon.skills.SkillStore
import cz.uhk.macroflow.pokemon.skills.Team

/**
 * Výměna v telefonu (docs/adr/0077): rozpracovaná výměna a její provedení v lokální databázi.
 * Provedení je idempotentní – když aplikace spadne uprostřed, při dalším otevření arény se dokončí.
 * Volat mimo hlavní vlákno.
 */
object TradeStore {

    private const val CODE = "trade_pending_code"
    private const val UID = "trade_pending_uid"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

    fun pendingCode(ctx: Context): String? = prefs(ctx).getString(CODE, null)
    /** Makromon nabídnutý v rozpracované výměně – nejde ho pustit. */
    fun pendingUid(ctx: Context): String? = prefs(ctx).getString(UID, null)

    fun setPending(ctx: Context, code: String, uid: String) { prefs(ctx).edit().putString(CODE, code).putString(UID, uid).apply() }
    fun clearPending(ctx: Context) { prefs(ctx).edit().remove(CODE).remove(UID).apply() }

    /** Makromoni, které jde nabídnout: ne zamčení a ne poslední Makromon hráče. */
    fun tradeable(ctx: Context): List<CapturedMakromonEntity> {
        val all = AppDatabase.getDatabase(ctx).capturedMakromonDao().getAllCaught()
        return if (all.size <= 1) emptyList() else all.filter { !it.isLocked }
    }

    /**
     * Provede hotovou výměnu u sebe: odebere svého, přidá cizího, zapíše do cloudu „provedeno“.
     * Vrátí přijatého Makromona (null, když výměna ještě není hotová nebo už provedená).
     */
    suspend fun applyIfDone(ctx: Context, t: Trade, me: String): CapturedMakromonEntity? {
        val role = Trading.role(t, me) ?: return null
        if (Trading.phase(t) != Trading.Phase.DONE || Trading.applied(t, role)) return null
        val mine = Trading.myOffer(t, role) ?: return null
        val theirs = Trading.theirOffer(t, role) ?: return null
        val dao = AppDatabase.getDatabase(ctx).capturedMakromonDao()

        dao.getByUid(mine.uid)?.let { removeLocal(ctx, it) }
        val received = dao.getByUid(theirs.uid) ?: Trading.received(theirs, Trading.theirName(t, role), System.currentTimeMillis()).also {
            dao.insertMakromon(it)
            runCatching { FirebaseRepository.uploadCapturedMakromon(it); FirebaseRepository.uploadMakrodexStatus(it.makromonId) }
        }
        FirebaseRepository.markTradeApplied(t.code, role)
        if (pendingCode(ctx) == t.code) clearPending(ctx)
        return dao.getByUid(received.uid) ?: received
    }

    /** Odebrání odevzdaného Makromona – jako puštění, jen bez kontroly zámku. */
    private suspend fun removeLocal(ctx: Context, m: CapturedMakromonEntity) {
        val wasActive = PocketActions.activeCaughtDate(ctx) == m.caughtDate
        val dao = AppDatabase.getDatabase(ctx).capturedMakromonDao()
        dao.deleteMakromon(m)
        val rest = Team.remove(SkillStore.team(ctx), m.id)
        SkillStore.saveTeam(ctx, rest)
        if (wasActive) {
            val next = rest.firstOrNull()?.let { dao.getMakromonById(it) } ?: dao.getAllCaught().firstOrNull()
            if (next != null) PocketActions.makeActive(ctx, next)
            else prefs(ctx).edit().putBoolean("pokemonAcquired", false).putLong("currentOnBarCaughtDate", -1L).apply()
        }
        runCatching { FirebaseRepository.deleteCapturedMakromon(m.caughtDate) }
    }
}

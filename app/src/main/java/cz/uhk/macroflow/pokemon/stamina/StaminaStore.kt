package cz.uhk.macroflow.pokemon.stamina

import android.content.Context
import java.time.LocalDate

/** Uložení energie v GamePrefs (docs/adr/0065). Pravidla jsou v [Stamina]. */
object StaminaStore {
    private const val PREFS = "GamePrefs"
    private const val BASE = "stamina_base"
    private const val OVER = "stamina_over"
    private const val DAY = "stamina_refill_day"
    private const val EXIT = "stamina_last_exit"
    private const val FROM = "stamina_last_from"
    private const val MOVED = "stamina_last_move"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): Stamina.State = prefs(ctx).run {
        Stamina.State(
            base = getInt(BASE, Stamina.BASE_MAX),
            over = getInt(OVER, 0),
            refillDay = getString(DAY, null),
            lastExitAt = getLong(EXIT, 0),
            lastFrom = getString(FROM, null),
            lastMoveAt = getLong(MOVED, 0)
        )
    }

    fun save(ctx: Context, s: Stamina.State) {
        prefs(ctx).edit()
            .putInt(BASE, s.base).putInt(OVER, s.over)
            .putString(DAY, s.refillDay).putLong(EXIT, s.lastExitAt)
            .putString(FROM, s.lastFrom).putLong(MOVED, s.lastMoveAt)
            .apply()
    }

    /** Příchod do Makrosvěta: případné doplnění přes noc. */
    fun onEnter(ctx: Context, now: Long = System.currentTimeMillis()): Stamina.State {
        val s = Stamina.refill(load(ctx), LocalDate.now().toString(), now)
        save(ctx, s)
        return s
    }

    /** Odchod z Makrosvěta: odtud se počítá pauza pro doplnění. */
    fun onExit(ctx: Context, now: Long = System.currentTimeMillis()) = save(ctx, load(ctx).copy(lastExitAt = now))

    /** Zaplatí [cost]. null = nestačí (stav se nemění). */
    fun pay(ctx: Context, cost: Int): Stamina.State? = Stamina.spend(load(ctx), cost)?.also { save(ctx, it) }

    /** Debug: nastaví energii přímo (adb … --es debug_stamina 30+10). */
    fun debugSet(ctx: Context, base: Int, over: Int) =
        save(ctx, load(ctx).copy(base = base.coerceIn(0, Stamina.BASE_MAX), over = over.coerceIn(0, Stamina.OVER_MAX)))
}

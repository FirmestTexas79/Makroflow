package cz.uhk.macroflow.settings

import android.content.Context
import android.view.View

/**
 * Uživatelská nastavení (SharedPreferences „AppSettings“). Podklady v docs/adr/0022.
 * Zvuk Makrosvěta zůstává v GamePrefs (viz [cz.uhk.macroflow.pokemon.audio.GameAudio]).
 */
object AppSettings {
    private const val PREFS = "AppSettings"
    private const val NOTIFICATIONS = "notifications_enabled"
    private const val HAPTICS = "haptics_enabled"
    private const val COMPANION = "companion_on_bar"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Upozornění ──────────────────────────────────────────────────────────

    fun notificationsEnabled(ctx: Context) = prefs(ctx).getBoolean(NOTIFICATIONS, true)
    fun setNotificationsEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(NOTIFICATIONS, on).apply()

    fun reminderSwitch(ctx: Context, r: Reminder) = prefs(ctx).getBoolean("reminder_${r.key}", true)
    fun setReminderSwitch(ctx: Context, r: Reminder, on: Boolean) = prefs(ctx).edit().putBoolean("reminder_${r.key}", on).apply()

    /** Připomínka opravdu běží = hlavní vypínač i její vlastní. */
    fun isReminderOn(ctx: Context, r: Reminder) = notificationsEnabled(ctx) && reminderSwitch(ctx, r)

    fun reminderMinutes(ctx: Context, r: Reminder): Int =
        prefs(ctx).getInt("reminder_${r.key}_time", r.defaultMinutes ?: 0)
    fun setReminderMinutes(ctx: Context, r: Reminder, minutes: Int) =
        prefs(ctx).edit().putInt("reminder_${r.key}_time", minutes).apply()

    // ── Odezva a Makrosvět ──────────────────────────────────────────────────

    fun hapticsEnabled(ctx: Context) = prefs(ctx).getBoolean(HAPTICS, true)
    fun setHapticsEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(HAPTICS, on).apply()

    /** Makromon procházející se po spodní liště. */
    fun companionOnBar(ctx: Context) = prefs(ctx).getBoolean(COMPANION, true)
    fun setCompanionOnBar(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(COMPANION, on).apply()
}

/** Hmatová odezva, která respektuje nastavení „Vibrace“. */
fun View.haptic(feedbackConstant: Int) {
    if (AppSettings.hapticsEnabled(context)) performHapticFeedback(feedbackConstant)
}

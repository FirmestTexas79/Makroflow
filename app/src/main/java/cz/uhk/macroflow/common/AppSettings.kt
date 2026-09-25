package cz.uhk.macroflow.common

import android.content.Context

/**
 * Uživatelská nastavení aplikace (SharedPreferences „SettingsPrefs“) – docs/adr/0022.
 * Výchozí hodnoty = chování před zavedením obrazovky Nastavení.
 */
object AppSettings {

    private const val PREFS = "SettingsPrefs"
    private const val K_NOTIFICATIONS = "notifications_enabled"
    private const val K_MUSIC = "music_enabled"
    private const val K_SFX = "sfx_enabled"
    private const val K_COMPANION = "companion_on_screen"
    private const val K_CAMERA_DIARY = "camera_sets_to_diary"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Dřívější jediný vypínač zvuku v Makrosvětě – slouží jako výchozí hodnota hudby i efektů. */
    private fun legacySound(ctx: Context) =
        ctx.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).getBoolean("soundEnabled", true)

    // ── Upozornění ──────────────────────────────────────────────────────────

    fun notificationsEnabled(ctx: Context) = prefs(ctx).getBoolean(K_NOTIFICATIONS, true)
    fun setNotificationsEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(K_NOTIFICATIONS, on).apply()

    fun isOn(ctx: Context, r: Reminder) = notificationsEnabled(ctx) && prefs(ctx).getBoolean(r.key, true)
    fun reminderSwitch(ctx: Context, r: Reminder) = prefs(ctx).getBoolean(r.key, true)
    fun setReminder(ctx: Context, r: Reminder, on: Boolean) = prefs(ctx).edit().putBoolean(r.key, on).apply()

    fun minutes(ctx: Context, r: Reminder): Int? =
        r.defaultMinutes?.let { prefs(ctx).getInt("${r.key}_minutes", it) }
    fun setMinutes(ctx: Context, r: Reminder, minutes: Int) =
        prefs(ctx).edit().putInt("${r.key}_minutes", minutes).apply()

    // ── Zvuk ────────────────────────────────────────────────────────────────

    fun musicEnabled(ctx: Context) = prefs(ctx).getBoolean(K_MUSIC, legacySound(ctx))
    fun setMusicEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(K_MUSIC, on).apply()
    fun sfxEnabled(ctx: Context) = prefs(ctx).getBoolean(K_SFX, legacySound(ctx))
    fun setSfxEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(K_SFX, on).apply()

    // ── Zobrazení ───────────────────────────────────────────────────────────

    /** Parťák (aktivní Makromon) procházející se po spodní liště. */
    fun companionOnScreen(ctx: Context) = prefs(ctx).getBoolean(K_COMPANION, true)
    fun setCompanionOnScreen(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(K_COMPANION, on).apply()

    // ── Trénink ─────────────────────────────────────────────────────────────

    /** Série naměřené kamerou se samy zapíšou do tréninkového deníku (docs/adr/0027). */
    fun cameraToDiary(ctx: Context) = prefs(ctx).getBoolean(K_CAMERA_DIARY, true)
    fun setCameraToDiary(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(K_CAMERA_DIARY, on).apply()
}

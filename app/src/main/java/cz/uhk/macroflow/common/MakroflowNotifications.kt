package cz.uhk.macroflow.common

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import coil.Coil
import coil.request.ImageRequest
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.R
import cz.uhk.macroflow.common.MakroflowNotifications.CHANNEL_STICKY
import cz.uhk.macroflow.common.MakroflowNotifications.ID_STICKY_SERVICE
import cz.uhk.macroflow.training.TrainingTimeManager
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*

/**
 * MakroflowNotifications — centrální správa všech notifikací.
 *
 * Notifikace:
 *  Každé upozornění jde vypnout v Nastavení; časy ranního, večerního a „série“ jdou změnit
 *  (AppSettings + Reminder, docs/adr/0022).
 *
 *  1. MORNING_RITUAL    — 8:00 každý den → připomínka ranního rituálu
 *  2. PRE_WORKOUT       — 30 min před tréninkem → čas na PRE jídlo
 *  3. POST_WORKOUT      — po skončení tréninku (+75min) → POST okno otevřeno
 *  4. WATER_REMINDER    — každé 2h pokud uživatel málo pil
 *  5. EVENING_LOG       — 20:00 pokud jsou makra nezalogována
 *  6. STREAK_RISK       — 21:00 pokud hrozí přerušení streaku
 *
 * Použití — zavolej v MainActivity.onCreate():
 *   MakroflowNotifications.createChannels(this)
 *   MakroflowNotifications.scheduleAll(this)
 *
 * Při změně času tréninku (MakroflowTimePicker) zavolej:
 *   MakroflowNotifications.rescheduleWorkout(this)
 */
object MakroflowNotifications {

    // ── Kanály ────────────────────────────────────────────────────────
    const val CHANNEL_RITUAL   = "makroflow_ritual"
    const val CHANNEL_WORKOUT  = "makroflow_workout"
    const val CHANNEL_WATER    = "makroflow_water"
    const val CHANNEL_DAILY    = "makroflow_daily"

    // ── Notification IDs ─────────────────────────────────────────────
    const val ID_MORNING_RITUAL  = 1001
    const val ID_PRE_WORKOUT     = 1002
    const val ID_POST_WORKOUT    = 1003
    const val ID_WATER_REMINDER  = 1004
    const val ID_EVENING_LOG     = 1005
    const val ID_STREAK_RISK     = 1006

    // ── Request codes pro PendingIntent ──────────────────────────────
    const val REQ_MORNING_RITUAL = 2001
    const val REQ_PRE_WORKOUT    = 2002
    const val REQ_POST_WORKOUT   = 2003
    const val REQ_WATER          = 2004
    const val REQ_EVENING_LOG    = 2005
    const val REQ_STREAK_RISK    = 2006

    // ── STICKY ───────────────────────────────────

    const val ID_STICKY_SERVICE = 9999

    // ── Akce pro BroadcastReceiver ───────────────────────────────────
    const val ACTION_MORNING_RITUAL = "cz.uhk.macroflow.MORNING_RITUAL"
    const val ACTION_PRE_WORKOUT    = "cz.uhk.macroflow.PRE_WORKOUT"
    const val ACTION_POST_WORKOUT   = "cz.uhk.macroflow.POST_WORKOUT"
    const val ACTION_WATER_REMINDER = "cz.uhk.macroflow.WATER_REMINDER"
    const val ACTION_EVENING_LOG    = "cz.uhk.macroflow.EVENING_LOG"
    const val ACTION_STREAK_RISK    = "cz.uhk.macroflow.STREAK_RISK"

    const val CHANNEL_STICKY = "makroflow_ongoing"

    // ═══════════════════════════════════════════════════════════════
    // Vytvoření kanálů (volej jednou při startu)
    // ═══════════════════════════════════════════════════════════════
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        listOf(
            NotificationChannel(CHANNEL_RITUAL, "Ranní rituál",
                NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Připomínka denního check-inu"
            },
            NotificationChannel(CHANNEL_WORKOUT, "Trénink",
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "PRE/POST tréninkové notifikace"
            },
            NotificationChannel(CHANNEL_WATER, "Pitný režim",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "Připomínky pití vody"
            },
            NotificationChannel(CHANNEL_DAILY, "Denní přehled",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "Večerní shrnutí a streak upozornění"
            }
        ).forEach { nm.createNotificationChannel(it) }
    }

    // ═══════════════════════════════════════════════════════════════
    // Naplánování všech notifikací
    // ═══════════════════════════════════════════════════════════════
    /**
     * Naplánuje zapnutá upozornění a zruší vypnutá (AppSettings, docs/adr/0022).
     * Volá se při startu, po restartu telefonu a po každé změně v Nastavení.
     */
    fun scheduleAll(context: Context) {
        fun on(r: Reminder) = AppSettings.isOn(context, r)
        if (on(Reminder.MORNING)) scheduleMorningRitual(context) else cancelAlarm(context, REQ_MORNING_RITUAL, ACTION_MORNING_RITUAL)
        if (on(Reminder.WORKOUT)) scheduleWorkoutNotifications(context) else cancelWorkoutNotifications(context)
        if (on(Reminder.WATER)) scheduleWaterReminders(context) else cancelAlarm(context, REQ_WATER, ACTION_WATER_REMINDER)
        if (on(Reminder.EVENING)) scheduleEveningLog(context) else cancelAlarm(context, REQ_EVENING_LOG, ACTION_EVENING_LOG)
        if (on(Reminder.STREAK)) scheduleStreakRisk(context) else cancelAlarm(context, REQ_STREAK_RISK, ACTION_STREAK_RISK)
    }

    /** Čas denního upozornění v ms podle nastavení. */
    private fun nextDailyMs(context: Context, r: Reminder): Long {
        val minutes = AppSettings.minutes(context, r) ?: r.defaultMinutes ?: 0
        val t = ReminderSchedule.nextDaily(java.time.LocalDateTime.now(), minutes)
        return t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    // Přeplánuj jen workout notifikace (při změně času tréninku)
    fun rescheduleWorkout(context: Context) {
        cancelWorkoutNotifications(context)
        if (AppSettings.isOn(context, Reminder.WORKOUT)) scheduleWorkoutNotifications(context)
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. RANNÍ RITUÁL — každý den 8:00
    // ═══════════════════════════════════════════════════════════════
    private fun scheduleMorningRitual(context: Context) {
        setRepeatingAlarm(context, nextDailyMs(context, Reminder.MORNING),
            AlarmManager.INTERVAL_DAY, REQ_MORNING_RITUAL, ACTION_MORNING_RITUAL)
    }

    // ═══════════════════════════════════════════════════════════════
    // 2+3. WORKOUT notifikace — dynamicky podle nastaveného času
    // ═══════════════════════════════════════════════════════════════
    private fun scheduleWorkoutNotifications(context: Context) {
        val timeStr = TrainingTimeManager.getTrainingTimeForToday(context) ?: return
        val parts = timeStr.split(":")
        val h = parts[0].toIntOrNull() ?: return
        val m = parts[1].toIntOrNull() ?: return

        // PRE: 30 minut před tréninkem
        val preCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, -30)
            if (before(Calendar.getInstance())) return  // už prošel
        }
        setExactAlarm(context, preCal.timeInMillis, REQ_PRE_WORKOUT, ACTION_PRE_WORKOUT)

        // POST: po skončení tréninku (+75 min)
        val postCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, 75)
            if (before(Calendar.getInstance())) return
        }
        setExactAlarm(context, postCal.timeInMillis, REQ_POST_WORKOUT, ACTION_POST_WORKOUT)
    }

    private fun cancelWorkoutNotifications(context: Context) {
        cancelAlarm(context, REQ_PRE_WORKOUT, ACTION_PRE_WORKOUT)
        cancelAlarm(context, REQ_POST_WORKOUT, ACTION_POST_WORKOUT)
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. VODA — každé 2 hodiny 9:00–21:00
    // ═══════════════════════════════════════════════════════════════
    private fun scheduleWaterReminders(context: Context) {
        val next = ReminderSchedule.nextWaterSlot(java.time.LocalDateTime.now())
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Každé 2 hodiny; mimo 9–21 h receiver nic neukáže
        setRepeatingAlarm(context, next,
            AlarmManager.INTERVAL_HOUR * ReminderSchedule.WATER_EVERY_HOURS, REQ_WATER, ACTION_WATER_REMINDER)
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. VEČERNÍ LOG — 20:00 každý den
    // ═══════════════════════════════════════════════════════════════
    private fun scheduleEveningLog(context: Context) {
        setRepeatingAlarm(context, nextDailyMs(context, Reminder.EVENING),
            AlarmManager.INTERVAL_DAY, REQ_EVENING_LOG, ACTION_EVENING_LOG)
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. STREAK RISK — 21:00 každý den
    // ═══════════════════════════════════════════════════════════════
    private fun scheduleStreakRisk(context: Context) {
        setRepeatingAlarm(context, nextDailyMs(context, Reminder.STREAK),
            AlarmManager.INTERVAL_DAY, REQ_STREAK_RISK, ACTION_STREAK_RISK)
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════
    private fun setExactAlarm(context: Context, timeMs: Long, reqCode: Int, action: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = makePendingIntent(context, reqCode, action)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.set(AlarmManager.RTC_WAKEUP, timeMs, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, timeMs, pi)
        }
    }

    private fun setRepeatingAlarm(context: Context, timeMs: Long,
                                  intervalMs: Long, reqCode: Int, action: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, timeMs, intervalMs,
            makePendingIntent(context, reqCode, action))
    }

    fun cancelAlarm(context: Context, reqCode: Int, action: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(makePendingIntent(context, reqCode, action))
    }

    private fun makePendingIntent(context: Context, reqCode: Int, action: String): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(context, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

}

// ═══════════════════════════════════════════════════════════════════
// BroadcastReceiver — přijme alarm a zobrazí notifikaci
// ═══════════════════════════════════════════════════════════════════
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Staré alarmy po vypnutí v Nastavení nic neukážou
        val reminder = when (intent.action) {
            MakroflowNotifications.ACTION_MORNING_RITUAL -> Reminder.MORNING
            MakroflowNotifications.ACTION_PRE_WORKOUT, MakroflowNotifications.ACTION_POST_WORKOUT -> Reminder.WORKOUT
            MakroflowNotifications.ACTION_WATER_REMINDER -> Reminder.WATER
            MakroflowNotifications.ACTION_EVENING_LOG -> Reminder.EVENING
            MakroflowNotifications.ACTION_STREAK_RISK -> Reminder.STREAK
            else -> null
        }
        if (reminder != null && !AppSettings.isOn(context, reminder)) return
        when (intent.action) {
            MakroflowNotifications.ACTION_MORNING_RITUAL -> handleMorningRitual(context)
            MakroflowNotifications.ACTION_PRE_WORKOUT    -> handlePreWorkout(context)
            MakroflowNotifications.ACTION_POST_WORKOUT   -> handlePostWorkout(context)
            MakroflowNotifications.ACTION_WATER_REMINDER -> handleWater(context)
            MakroflowNotifications.ACTION_EVENING_LOG    -> handleEveningLog(context)
            MakroflowNotifications.ACTION_STREAK_RISK    -> handleStreakRisk(context)
            // Obnoví alarmy po restartu telefonu
            Intent.ACTION_BOOT_COMPLETED                 -> MakroflowNotifications.scheduleAll(context)
        }
    }

    // 1. Ranní rituál
    private fun handleMorningRitual(context: Context) {
        // Zkontroluj jestli dnes rituál nebyl hotov
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val done = AppDatabase.Companion.getDatabase(context).checkInDao().getCheckInByDateSync(today) != null
        if (done) return  // Již splněno — neruš

        showNotification(context,
            id      = MakroflowNotifications.ID_MORNING_RITUAL,
            channel = MakroflowNotifications.CHANNEL_RITUAL,
            title   = "🌅 Dobré ráno! Ranní rituál čeká",
            text    = "Zaznamenej váhu, energii a spánek — zabere to 30 sekund.",
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }

    // 2. PRE trénink
    private fun handlePreWorkout(context: Context) {
        val timeStr = TrainingTimeManager.getTrainingTimeForToday(context) ?: return
        val dayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(Date())
        val type = context.getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)
            .getString("type_$dayName", "rest")?.uppercase() ?: return
        if (type == "REST") return

        showNotification(context,
            id      = MakroflowNotifications.ID_PRE_WORKOUT,
            channel = MakroflowNotifications.CHANNEL_WORKOUT,
            title   = "⚡ $type trénink za 30 minut!",
            text    = "Čas na PRE jídlo — otevři Makroflow pro doporučení sacharidů.",
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }

    // 3. POST trénink
    private fun handlePostWorkout(context: Context) {
        val dayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(Date())
        val type = context.getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)
            .getString("type_$dayName", "rest")?.uppercase() ?: return
        if (type == "REST") return

        showNotification(context,
            id      = MakroflowNotifications.ID_POST_WORKOUT,
            channel = MakroflowNotifications.CHANNEL_WORKOUT,
            title   = "💪 Trénink hotov! Čas na jídlo s bílkovinami",
            // „Anabolické okno“ je široké (Schoenfeld et al. 2013) – rozhoduje hlavně denní příjem bílkovin
            text    = "Dej si v nejbližších hodinách jídlo s bílkovinami — rozhoduje hlavně denní součet. Makroflow ti poradí.",
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }

    // 4. Voda
    private fun handleWater(context: Context) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // Nenotifikuj před 9:00 nebo po 21:00
        if (hour < 9 || hour >= 21) return

        // Zkontroluj jestli pil nedávno (poslední 2h)
        val lastTs = AppDatabase.Companion.getDatabase(context).waterDao().getLastDrinkTimestamp(today)
        if (lastTs != null) {
            val hoursSince = (System.currentTimeMillis() - lastTs) / 3_600_000f
            if (hoursSince < 1.8f) return  // Pil nedávno — neruš
        }

        val totalMl = AppDatabase.Companion.getDatabase(context).waterDao().getTotalMlForDateSync(today)
        // Osobní cíl pitného režimu (dřív napevno 2,5 l)
        val targetMl = runCatching { (cz.uhk.macroflow.dashboard.MacroCalculator.calculate(context).water * 1000).toInt() }
            .getOrDefault(2500).takeIf { it > 0 } ?: 2500
        if (totalMl >= targetMl) return  // Splnil cíl — neruš

        val remaining = targetMl - totalMl
        showNotification(context,
            id      = MakroflowNotifications.ID_WATER_REMINDER,
            channel = MakroflowNotifications.CHANNEL_WATER,
            title   = "💧 Nezapomínej pít",
            text    = "Zbývá ${remaining}ml do denního cíle. Dej si sklenici!",
            priority = NotificationCompat.PRIORITY_LOW
        )
    }

    // 5. Večerní log
    private fun handleEveningLog(context: Context) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val consumed = AppDatabase.Companion.getDatabase(context)
            .consumedSnackDao().getAllConsumedSync()
            .filter { it.date == today }

        if (consumed.isEmpty()) {
            showNotification(context,
                id      = MakroflowNotifications.ID_EVENING_LOG,
                channel = MakroflowNotifications.CHANNEL_DAILY,
                title   = "📋 Dnes nic nezapsáno",
                text    = "Zaloguj dnešní jídla ať víme jak to bylo — jinak streak se nepočítá!",
                priority = NotificationCompat.PRIORITY_LOW
            )
        } else {
            val totalKcal = consumed.sumOf { it.calories }
            showNotification(context,
                id      = MakroflowNotifications.ID_EVENING_LOG,
                channel = MakroflowNotifications.CHANNEL_DAILY,
                title   = "📊 Dnešní přehled — $totalKcal kcal",
                text    = "Zalogováno ${consumed.size} jídel. Zkontroluj makra v Makroflow.",
                priority = NotificationCompat.PRIORITY_LOW
            )
        }
    }

    // 6. Streak risk
    private fun handleStreakRisk(context: Context) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val checkIn = AppDatabase.Companion.getDatabase(context).checkInDao().getCheckInByDateSync(today)
        if (checkIn != null) return  // Rituál udělán — streak v bezpečí

        // Série = po sobě jdoucí dny s check-inem končící včerejškem (dřív počet všech check-inů)
        val days = AppDatabase.Companion.getDatabase(context).checkInDao().getAllCheckInsSync()
            .mapNotNull { runCatching { java.time.LocalDate.parse(it.date) }.getOrNull() }.toSet()
        val streakDays = ReminderSchedule.streakAtRisk(days, java.time.LocalDate.now())

        if (streakDays < 2) return  // Nemá streak co ztratit

        showNotification(context,
            id      = MakroflowNotifications.ID_STREAK_RISK,
            channel = MakroflowNotifications.CHANNEL_DAILY,
            title   = "🔥 $streakDays dní streak v ohrožení!",
            text    = "Ještě jsi dnes nevyplnil ranní rituál. Zbývají hodiny — nezlam sérii!",
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }

    // ── Zobrazení notifikace ──────────────────────────────────────────
    private fun showNotification(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT
    ) {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
        val pi = PendingIntent.getActivity(context, id, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_logo_white)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(priority)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setColor(0xFF283618.toInt())
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Uživatel nezavolal oprávnění — tiché selhání
        }
    }
}



// ═══════════════════════════════════════════════════════════════════
// BootReceiver — obnoví alarmy po restartu telefonu
// ═══════════════════════════════════════════════════════════════════
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            MakroflowNotifications.createChannels(context)
            MakroflowNotifications.scheduleAll(context)
        }
    }
}
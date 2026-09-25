package cz.uhk.macroflow.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import cz.uhk.macroflow.R
import cz.uhk.macroflow.dashboard.MacroCalculator
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.energy.Adherence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Widget Makra 2×2 (docs/adr/0026). Obnova: po zápisu/smazání jídla ([MacroWidget.refresh]),
 * při odchodu z aplikace, po půlnoci (alarm) a systémově každých 30 min (cíle se mění s check-inem,
 * plánem tréninku a kroky). Klepnutí na kalorie přepne snědeno / zbývá, jinde otevře aplikaci.
 */
class MacroWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        MacroWidget.update(context, ids, goAsync())
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        MacroWidget.update(context, intArrayOf(id), goAsync())
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MacroWidget.ACTION_TOGGLE -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    MacroWidget.toggleMode(context, id)
                    MacroWidget.update(context, intArrayOf(id), goAsync())
                }
            }
            MacroWidget.ACTION_REFRESH -> MacroWidget.update(context, MacroWidget.ids(context), goAsync())
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val prefs = MacroWidget.prefs(context).edit()
        ids.forEach { prefs.remove(MacroWidget.modeKey(it)) }
        prefs.apply()
    }

    override fun onDisabled(context: Context) = MacroWidget.cancelMidnight(context)
}

object MacroWidget {
    const val ACTION_TOGGLE = "cz.uhk.macroflow.widget.TOGGLE"
    const val ACTION_REFRESH = "cz.uhk.macroflow.widget.REFRESH"
    private const val PREFS = "MacroWidgetPrefs"
    private const val RC_MIDNIGHT = 7301

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun modeKey(id: Int) = "mode_$id"

    fun ids(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, MacroWidgetProvider::class.java))

    fun mode(ctx: Context, id: Int): MacroWidgetModel.Mode =
        runCatching { MacroWidgetModel.Mode.valueOf(prefs(ctx).getString(modeKey(id), null) ?: "") }
            .getOrDefault(MacroWidgetModel.Mode.REMAINING)

    fun toggleMode(ctx: Context, id: Int) {
        val next = if (mode(ctx, id) == MacroWidgetModel.Mode.EATEN) MacroWidgetModel.Mode.REMAINING else MacroWidgetModel.Mode.EATEN
        prefs(ctx).edit().putString(modeKey(id), next.name).apply()
    }

    /** Obnoví všechny widgety na ploše (volat po změně snědeného). Bez widgetů nic nedělá. */
    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        val ids = runCatching { ids(app) }.getOrDefault(IntArray(0))
        if (ids.isNotEmpty()) update(app, ids, null)
    }

    /** Dnešní snědeno a cíle (cíle null, když je aplikace ještě nemá z čeho spočítat). */
    fun loadToday(ctx: Context): Pair<Adherence.Eaten, Adherence.Targets?> {
        val today = LocalDate.now().toString()
        val items = runCatching { AppDatabase.getDatabase(ctx).consumedSnackDao().getConsumedByDateSync(today) }.getOrDefault(emptyList())
        val eaten = Adherence.Eaten(
            kcal = items.sumOf { it.calories.toDouble() },
            protein = items.sumOf { it.p.toDouble() },
            carbs = items.sumOf { it.s.toDouble() },
            fat = items.sumOf { it.t.toDouble() }
        )
        val targets = runCatching { MacroCalculator.calculate(ctx) }.getOrNull()
            ?.takeIf { it.calories > 0 }
            ?.let { Adherence.Targets(it.calories, it.protein, it.carbs, it.fat) }
        return eaten to targets
    }

    fun update(ctx: Context, ids: IntArray, pending: android.content.BroadcastReceiver.PendingResult?) {
        val app = ctx.applicationContext
        scope.launch {
            try {
                if (ids.isEmpty()) return@launch
                val (eaten, targets) = loadToday(app)
                val manager = AppWidgetManager.getInstance(app)
                ids.forEach { id -> manager.updateAppWidget(id, views(app, manager, id, eaten, targets)) }
                scheduleMidnight(app)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pending?.finish()
            }
        }
    }

    private fun views(ctx: Context, manager: AppWidgetManager, id: Int, eaten: Adherence.Eaten, targets: Adherence.Targets?): RemoteViews {
        val mode = mode(ctx, id)
        val state = MacroWidgetModel.build(eaten, targets, mode)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_macro)
        rv.setImageViewBitmap(R.id.imgWidgetMacro, MacroWidgetRenderer.render(ctx, state, mode, sizePx(ctx, manager, id)))
        rv.setContentDescription(R.id.imgWidgetMacro, state.description)

        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        if (open != null) {
            rv.setOnClickPendingIntent(R.id.imgWidgetMacro,
                PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        val toggle = Intent(ctx, MacroWidgetProvider::class.java).setAction(ACTION_TOGGLE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        rv.setOnClickPendingIntent(R.id.areaWidgetKcal,
            PendingIntent.getBroadcast(ctx, id, toggle, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        rv.setContentDescription(R.id.areaWidgetKcal,
            if (mode == MacroWidgetModel.Mode.EATEN) "Přepnout na zbývající kalorie" else "Přepnout na snědené kalorie")
        return rv
    }

    /** Strana čtverce v px podle skutečné velikosti widgetu (menší z šířky a výšky). */
    private fun sizePx(ctx: Context, manager: AppWidgetManager, id: Int): Int {
        val o = manager.getAppWidgetOptions(id)
        val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 }
            ?: o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val dp = listOf(wDp, hDp).filter { it > 0 }.minOrNull() ?: 150
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), ctx.resources.displayMetrics).toInt()
    }

    private fun midnightIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, RC_MIDNIGHT, Intent(ctx, MacroWidgetProvider::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Nepřesný alarm chvíli po půlnoci – nový den začne s prázdným prstencem. */
    private fun scheduleMidnight(ctx: Context) {
        val at = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 60_000
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.RTC, at, midnightIntent(ctx))
    }

    fun cancelMidnight(ctx: Context) {
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(midnightIntent(ctx))
    }
}

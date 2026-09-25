package cz.uhk.macroflow.widget

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.ScrollView
import cz.uhk.macroflow.energy.Adherence

/** Debug náhled widgetu: ukázková data ve světlém i tmavém motivu a v obou režimech. */
class WidgetPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val targets = Adherence.Targets(2200.0, 180.0, 250.0, 60.0)
        val cases = listOf(
            Adherence.Eaten(1240.0, 92.0, 131.0, 38.0) to targets,
            Adherence.Eaten(2350.0, 176.0, 262.0, 71.0) to targets,
            Adherence.Eaten(0.0, 0.0, 0.0, 0.0) to targets,
            Adherence.Eaten(640.0, 40.0, 70.0, 20.0) to null
        )
        val size = (resources.displayMetrics.widthPixels / 2) - 24
        val grid = GridLayout(this).apply { columnCount = 2; setPadding(12, 60, 12, 12) }
        val out = java.io.File(getExternalFilesDir(null), "widget_preview").apply { mkdirs() }
        var n = 0
        for (night in listOf(false, true)) for ((e, t) in cases) for (mode in MacroWidgetModel.Mode.entries) {
            if (t == null && mode == MacroWidgetModel.Mode.EATEN) continue
            val st = MacroWidgetModel.build(e, t, mode)
            val bmp = MacroWidgetRenderer.render(this@WidgetPreviewActivity, st, mode, size, night)
            // uloží i do souborů (adb pull) – screenshot emulátoru bývá černý
            java.io.File(out, "%02d_%s_%s.png".format(n++, if (night) "dark" else "light", mode.name.lowercase())).outputStream()
                .use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            grid.addView(ImageView(this).apply {
                setImageBitmap(bmp)
                layoutParams = GridLayout.LayoutParams().apply { width = size; height = size; setMargins(6, 6, 6, 6) }
            })
        }
        // adb shell am start -n cz.uhk.macroflow/.widget.WidgetPreviewActivity --ez pin true
        if (intent.getBooleanExtra("pin", false)) {
            val mgr = android.appwidget.AppWidgetManager.getInstance(this)
            if (mgr.isRequestPinAppWidgetSupported)
                mgr.requestPinAppWidget(android.content.ComponentName(this, MacroWidgetProvider::class.java), null, null)
        }
        // --ez seed true: ukázková jídla za poslední dny (emulátor, kontrola „Zopakovat jídlo“)
        if (intent.getBooleanExtra("seed", false)) Thread { seedMeals() }.start()
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#7A8A99")); addView(grid) })
    }

    private fun seedMeals() {
        val dao = cz.uhk.macroflow.data.AppDatabase.getDatabase(applicationContext).consumedSnackDao()
        val today = java.time.LocalDate.now()
        var ts = System.currentTimeMillis() - 5_000_000
        fun add(date: java.time.LocalDate, time: String, name: String, p: Float, s: Float, t: Float, kcal: Int) =
            dao.insertConsumed(cz.uhk.macroflow.data.ConsumedSnackEntity(
                timestamp = ts++, date = date.toString(), time = time, name = name, p = p, s = s, t = t,
                calories = kcal, energyKj = kcal * 4.184f, fiber = 1f))
        val y = today.minusDays(1)
        add(y, "07:40", "Ovesné vločky", 12f, 60f, 7f, 350); add(y, "07:45", "Whey protein", 24f, 3f, 2f, 120); add(y, "08:10", "Banán", 1f, 27f, 0f, 105)
        add(y, "12:30", "Kuřecí prsa s rýží", 45f, 80f, 8f, 650)
        add(y, "15:15", "Tvaroh", 25f, 8f, 1f, 150)
        add(y, "19:00", "Losos", 40f, 0f, 22f, 380); add(y, "19:10", "Brambory", 4f, 40f, 0f, 180)
        add(today.minusDays(3), "08:00", "Vejce", 18f, 1f, 15f, 210); add(today.minusDays(3), "13:00", "Hovězí s těstovinami", 42f, 90f, 14f, 700)
        add(today, "08:05", "Ovesné vločky", 12f, 60f, 7f, 350)
    }
}

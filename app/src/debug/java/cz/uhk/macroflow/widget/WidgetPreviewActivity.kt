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
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#7A8A99")); addView(grid) })
    }
}

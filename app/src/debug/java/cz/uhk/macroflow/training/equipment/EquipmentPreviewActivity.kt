package cz.uhk.macroflow.training.equipment

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Debug: všechna náčiní pod sebou; s --ez cycle true se váhy každé 2 s mění (kontrola animací).
 * adb shell am start -n cz.uhk.macroflow/.training.equipment.EquipmentPreviewActivity [--es w "1"]
 * --es w "0|1|2" vybere sadu vah.
 */
class EquipmentPreviewActivity : Activity() {
    private val sets = listOf(
        mapOf(Rig.DUMBBELL_PAIR to 12.0, Rig.DUMBBELL_SINGLE to 32.0, Rig.OLYMPIC_BAR to 60.0, Rig.STRAIGHT_BAR to 20.0,
            Rig.EZ_BAR to 30.0, Rig.MACHINE_BOTH to 25.0, Rig.MACHINE_ONE to 5.0, Rig.STACK to 22.5),
        mapOf(Rig.DUMBBELL_PAIR to 40.0, Rig.DUMBBELL_SINGLE to 6.0, Rig.OLYMPIC_BAR to 142.5, Rig.STRAIGHT_BAR to 45.0,
            Rig.EZ_BAR to 12.5, Rig.MACHINE_BOTH to 61.25, Rig.MACHINE_ONE to 40.0, Rig.STACK to 75.0),
        mapOf(Rig.DUMBBELL_PAIR to 2.0, Rig.DUMBBELL_SINGLE to 60.0, Rig.OLYMPIC_BAR to 20.0, Rig.STRAIGHT_BAR to 5.0,
            Rig.EZ_BAR to 60.0, Rig.MACHINE_BOTH to 0.0, Rig.MACHINE_ONE to 1.25, Rig.STACK to 0.0)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding((16 * d).toInt(), (40 * d).toInt(), (16 * d).toInt(), (16 * d).toInt()) }
        val views = Rig.entries.map { rig ->
            val v = EquipmentView(this)
            val label = TextView(this).apply { textSize = 12f; setTextColor(Color.parseColor("#283618")) }
            col.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (118 * d).toInt()))
            col.addView(label)
            Triple(rig, v, label)
        }
        var idx = intent.getStringExtra("w")?.toIntOrNull() ?: 0
        fun apply() = views.forEach { (rig, v, label) ->
            val w = sets[idx % sets.size].getValue(rig)
            v.set(rig, w); label.text = "${rig.label}: ${RigMath.summary(rig, w)}"
        }
        apply()
        if (intent.getBooleanExtra("cycle", false)) {
            val h = Handler(Looper.getMainLooper())
            h.postDelayed(object : Runnable { override fun run() { idx++; apply(); h.postDelayed(this, 2000) } }, 2000)
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#F4F1DC")); addView(col) })
    }
}

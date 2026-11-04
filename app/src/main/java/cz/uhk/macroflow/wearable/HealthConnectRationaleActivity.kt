package cz.uhk.macroflow.wearable

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Vysvětlení, proč aplikace čte Health Connect (vyžaduje ho systém i Google Play). */
class HealthConnectRationaleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding((24 * d).toInt(), (32 * d).toInt(), (24 * d).toInt(), (24 * d).toInt()) }
        col.addView(TextView(this).apply { text = "Proč čteme Health Connect"; textSize = 22f })
        col.addView(TextView(this).apply {
            textSize = 16f; setPadding(0, (16 * d).toInt(), 0, (16 * d).toInt())
            text = "Makroflow z Health Connect čte spánek, kroky, tep, spálené kalorie a cvičení z tvých hodinek. " +
                "Používá je jen v tomhle telefonu k tomu, aby upravil odhad tvého výdeje, doporučení ke spánku a tréninku. " +
                "Nic z toho se neodesílá, neprodává ani nesdílí. Povolení můžeš kdykoli odebrat v nastavení Health Connect."
        })
        col.addView(Button(this).apply { text = "Rozumím"; setOnClickListener { finish() } },
            LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END })
        setContentView(ScrollView(this).apply { addView(col) })
    }
}

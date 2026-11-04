package cz.uhk.macroflow.training

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView

/**
 * Týden v jedné kartě. Každý den je úzká pilulka se třemi řádky: den, barevný štítek s názvem tréninku (PUSH, PULL…)
 * a případně druhý režim („+ BĚH“). Nahoře je shrnutí týdne, dole nápověda. Klepnutím na den se pod kartou otevře jeho úprava.
 */
class WeekStrip(private val host: LinearLayout, private val onSelect: (Int) -> Unit) {

    class Day(val short: String, val strength: String, val kardio: String)

    private val ink = Color.parseColor("#283618")
    private val amber = Color.parseColor("#B9772F")

    private fun label(t: String) = when (t) {
        "full" -> "FULL"; "push" -> "PUSH"; "pull" -> "PULL"; "legs" -> "LEGS"
        "stairs" -> "SCHODY"; "run" -> "BĚH"; "rope" -> "ŠVIH"; "bike" -> "KOLO"; else -> "VOLNO"
    }

    private fun pastel(t: String) = Color.parseColor(when (t) {
        "push" -> "#A9C98B"; "pull" -> "#F5BC8A"; "legs" -> "#F0A59B"; "full" -> "#C3B1E3"
        "run" -> "#8CC0E0"; "rope" -> "#D2A7DE"; "bike" -> "#F5AD8A"; "stairs" -> "#8CCBB0"
        else -> "#ECE9D8"
    })

    private fun plural(n: Int, one: String, few: String, many: String) = "$n ${if (n == 1) one else if (n in 2..4) few else many}"

    fun render(days: List<Day>, selected: Int, today: Int, kardio: Boolean) {
        val ctx = host.context
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        host.removeAllViews()

        val main = { x: Day -> if (kardio) x.kardio else x.strength }
        val other = { x: Day -> if (kardio) x.strength else x.kardio }
        val active = days.count { main(it) != "rest" }

        val head = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), 0, dp(6), dp(10))
            addView(TextView(ctx).apply { text = "Tvůj týden"; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(ink) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(ctx).apply {
                text = if (active == 0) "zatím nic" else plural(active, if (kardio) "kardio den" else "trénink", if (kardio) "kardio dny" else "tréninky",
                    if (kardio) "kardio dní" else "tréninků") + " · " + (7 - active) + " volno"
                textSize = 12.5f; setTextColor(ink); alpha = 0.7f
            })
        }

        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 7f }
        days.forEachIndexed { i, day ->
            val m = main(day); val o = other(day)
            val on = m != "rest"; val sel = i == selected
            val pill = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(2), dp(9), dp(2), dp(9))
                background = GradientDrawable().apply {
                    cornerRadius = 18 * d
                    setColor(if (sel) ColorUtils.blendARGB(pastel(m), Color.WHITE, 0.6f) else Color.TRANSPARENT)
                    setStroke(dp(if (sel) 2 else 1), if (sel) ink else Color.parseColor("#1F283618"))
                }
                contentDescription = "${day.short}: ${label(m)}"
                setOnClickListener { onSelect(i) }
                addView(TextView(ctx).apply {
                    text = day.short; textSize = 12f; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
                    setTextColor(if (i == today) amber else ink); alpha = if (sel || i == today) 1f else 0.75f
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(TextView(ctx).apply {
                    text = label(m); textSize = 9.5f; gravity = Gravity.CENTER; setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
                    setTextColor(ink); alpha = if (on) 1f else 0.5f; maxLines = 1; includeFontPadding = false
                    setPadding(0, dp(6), 0, dp(6))
                    background = GradientDrawable().apply { cornerRadius = 10 * d; setColor(pastel(m)) }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
                addView(TextView(ctx).apply {
                    text = if (o != "rest") "+ ${label(o)}" else " "; textSize = 9f; gravity = Gravity.CENTER; setTextColor(ink); alpha = 0.7f
                    maxLines = 1; includeFontPadding = false
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            }
            row.addView(pill, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }

        val hint = TextView(ctx).apply {
            text = "Klepni na den a níže ho uprav"; textSize = 12f; setTextColor(ink); alpha = 0.55f; gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        host.addView(MaterialCardView(ctx).apply {
            radius = 26 * d; cardElevation = 0f; strokeWidth = dp(1); strokeColor = Color.parseColor("#1F283618")
            setCardBackgroundColor(Color.parseColor("#FFFDF3"))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(14), dp(10), dp(12))
                addView(head); addView(row); addView(hint)
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
    }
}

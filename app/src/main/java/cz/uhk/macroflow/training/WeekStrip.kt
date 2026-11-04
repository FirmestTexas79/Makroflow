package cz.uhk.macroflow.training

import android.content.res.ColorStateList
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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * Týden na jedné řádce: sedm vzdušných pilulek (den + barevná bublina typu tréninku, tečka = druhý režim),
 * pod nimi pár pastelových štítků po skupinách. Klepnutím na den se pod pruhem otevře jeho úprava.
 */
class WeekStrip(private val host: LinearLayout, private val onSelect: (Int) -> Unit) {

    class Day(val short: String, val strength: String, val kardio: String)

    private val ink = Color.parseColor("#283618")
    private val warm = Color.parseColor("#E9B072")
    private val restColor = Color.parseColor("#E4E1CC")

    private fun label(t: String) = when (t) {
        "full" -> "FULL"; "push" -> "PUSH"; "pull" -> "PULL"; "legs" -> "LEGS"
        "stairs" -> "SCHODY"; "run" -> "BĚH"; "rope" -> "ŠVIH"; "bike" -> "KOLO"; else -> "VOLNO"
    }

    /** Jemná paleta jen pro tenhle pruh: měkké, ale od sebe dobře rozlišitelné barvy. */
    private fun pastel(t: String) = Color.parseColor(when (t) {
        "push" -> "#9DBE7E"; "pull" -> "#F2B27C"; "legs" -> "#EE9C92"; "full" -> "#B8A4DC"
        "run" -> "#7FB6DA"; "rope" -> "#C99AD6"; "bike" -> "#F2A47F"; "stairs" -> "#7FC2A5"
        else -> "#E4E1CC"
    })

    fun render(days: List<Day>, selected: Int, today: Int, kardio: Boolean) {
        val ctx = host.context
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        host.removeAllViews()

        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        days.forEachIndexed { i, day ->
            val main = if (kardio) day.kardio else day.strength
            val other = if (kardio) day.strength else day.kardio
            val on = main != "rest"
            val base = pastel(main)
            val sel = i == selected

            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(10), 0, dp(10))
                addView(TextView(ctx).apply {
                    text = day.short; textSize = 11.5f; setTypeface(null, Typeface.BOLD)
                    setTextColor(if (i == today) Color.parseColor("#B9772F") else ink); alpha = if (sel || i == today) 1f else 0.7f
                })
                addView(View(ctx).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (on) base else restColor) }
                }, LinearLayout.LayoutParams(dp(26), dp(26)).apply { topMargin = dp(8) })
                addView(View(ctx).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (other != "rest") pastel(other) else Color.TRANSPARENT) }
                }, LinearLayout.LayoutParams(dp(7), dp(7)).apply { topMargin = dp(8) })
            }
            row.addView(MaterialCardView(ctx).apply {
                radius = 22 * d; cardElevation = (if (sel) 5 else 0) * d
                setCardBackgroundColor(if (sel) ColorUtils.blendARGB(base, Color.WHITE, 0.68f) else Color.parseColor("#FFFDF3"))
                strokeWidth = dp(if (sel) 2 else 1); strokeColor = if (sel) ink else Color.parseColor("#1F283618")
                contentDescription = "${day.short}: ${label(main)}"
                setOnClickListener { onSelect(i) }
                addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), dp(6), dp(3), dp(6)) })
        }
        host.addView(row)

        // Štítky po skupinách (volno se neuvádí, vidět je na světlých pilulkách)
        val groups = days.groupBy { if (kardio) it.kardio else it.strength }.filterKeys { it != "rest" }
        val extra = days.groupBy { if (kardio) it.strength else it.kardio }.filterKeys { it != "rest" }
        val chips = ChipGroup(ctx).apply { chipSpacingHorizontal = dp(6); chipSpacingVertical = dp(2); setPadding(dp(4), dp(4), dp(4), 0) }
        fun chip(t: String, ds: List<Day>, dot: Boolean) = Chip(ctx).apply {
            text = "${label(t)}  ${ds.joinToString("·") { it.short }}"
            isClickable = false; isCheckable = false; textSize = 12f; setTextColor(ink)
            chipBackgroundColor = ColorStateList.valueOf(ColorUtils.blendARGB(pastel(t), Color.WHITE, if (dot) 0.75f else 0.55f))
            chipStrokeWidth = 0f; chipMinHeight = 30 * d; setEnsureMinTouchTargetSize(false)
        }
        groups.forEach { (t, ds) -> chips.addView(chip(t, ds, false)) }
        extra.forEach { (t, ds) -> chips.addView(chip(t, ds, true)) }
        if (groups.isEmpty() && extra.isEmpty()) host.addView(TextView(ctx).apply {
            text = "Zatím nic naplánováno. Klepni na den a vyber typ."; textSize = 13f; setTextColor(if (kardio) Color.parseColor("#FEFAE0") else ink)
            alpha = 0.75f; setPadding(dp(6), dp(2), 0, 0)
        }) else host.addView(chips)
    }
}

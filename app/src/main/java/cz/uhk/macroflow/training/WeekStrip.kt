package cz.uhk.macroflow.training

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView

/**
 * Týden na jedné řádce: sedm nafouknutých pastelových pilulek (den, typ tréninku, kardio) a pod nimi souhrn po skupinách
 * („PUSH Po · Čt“, „Volno Pá · So · Ne“). Klepnutím na den se pod pruhem otevře jeho úprava.
 */
class WeekStrip(private val host: LinearLayout, private val onSelect: (Int) -> Unit) {

    class Day(val short: String, val strength: String, val kardio: String)

    private val ink = Color.parseColor("#283618")
    private val cream = Color.parseColor("#FEFAE0")
    private val warm = Color.parseColor("#E9B072")

    private fun label(t: String) = when (t) {
        "full" -> "FULL"; "push" -> "PUSH"; "pull" -> "PULL"; "legs" -> "LEGS"
        "stairs" -> "SCHODY"; "run" -> "BĚH"; "rope" -> "ŠVIH"; "bike" -> "KOLO"; else -> "VOLNO"
    }

    private fun emoji(t: String) = when (t) {
        "full" -> "🏋️"; "push" -> "💪"; "pull" -> "🧗"; "legs" -> "🦵"
        "stairs" -> "🪜"; "run" -> "🏃"; "rope" -> "🪢"; "bike" -> "🚴"; else -> ""
    }

    fun render(days: List<Day>, selected: Int, today: Int, kardio: Boolean, colorOf: (String) -> Int) {
        val ctx = host.context
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        host.removeAllViews()

        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        days.forEachIndexed { i, day ->
            val main = if (kardio) day.kardio else day.strength
            val other = if (kardio) day.strength else day.kardio
            val on = main != "rest"
            val base = if (on) colorOf(main) else Color.parseColor("#9A9A86")
            val sel = i == selected

            val blob = FrameLayout(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; gradientType = GradientDrawable.RADIAL_GRADIENT
                    gradientRadius = dp(24).toFloat(); setGradientCenter(0.35f, 0.3f)
                    colors = intArrayOf(ColorUtils.blendARGB(base, Color.WHITE, 0.45f), base)
                }
                addView(TextView(ctx).apply {
                    text = if (on) emoji(main) else "–"; textSize = if (on) 16f else 18f; gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                }, FrameLayout.LayoutParams(-1, -1))
            }
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(8), 0, dp(6))
                addView(TextView(ctx).apply { text = day.short; textSize = 11f; setTypeface(null, Typeface.BOLD); setTextColor(ink) })
                addView(blob, LinearLayout.LayoutParams(dp(34), dp(34)).apply { topMargin = dp(5) })
                addView(TextView(ctx).apply {
                    text = label(main); textSize = 8.5f; setTypeface(null, Typeface.BOLD); setTextColor(ink); alpha = if (on) 0.85f else 0.5f
                    setPadding(0, dp(4), 0, 0); maxLines = 1
                })
                addView(TextView(ctx).apply { text = if (other != "rest") emoji(other) else " "; textSize = 10f; maxLines = 1 })
                addView(View(ctx).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (i == today) warm else Color.TRANSPARENT) }
                }, LinearLayout.LayoutParams(dp(5), dp(5)).apply { topMargin = dp(2) })
            }
            row.addView(MaterialCardView(ctx).apply {
                radius = 20 * d; cardElevation = (if (sel) 8 else 2) * d; useCompatPadding = false
                setCardBackgroundColor(ColorUtils.blendARGB(base, Color.WHITE, if (on) 0.82f else 0.88f))
                strokeWidth = if (sel) dp(2) else 0; strokeColor = base
                scaleX = if (sel) 1.06f else 1f; scaleY = scaleX
                contentDescription = "${day.short}: ${label(main)}"
                setOnClickListener { onSelect(i) }
                addView(col)
            }, LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), dp(8), dp(3), dp(12)) })
        }
        host.addView(row)

        // Souhrn po skupinách
        val fg = if (kardio) cream else ink
        fun group(pick: (Day) -> String): List<Pair<String, List<String>>> =
            days.groupBy(pick).filterKeys { it != "rest" }.map { (t, l) -> t to l.map { it.short } }
        val main = group { if (kardio) it.kardio else it.strength }
        val rest = days.filter { (if (kardio) it.kardio else it.strength) == "rest" }.map { it.short }
        val extra = group { if (kardio) it.strength else it.kardio }

        fun line(dot: Int, name: String, ds: List<String>, tail: String = "") = TextView(ctx).apply {
            val sb = SpannableStringBuilder("● ").apply { setSpan(ForegroundColorSpan(dot), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            val n = sb.length
            sb.append(name).setSpan(StyleSpan(Typeface.BOLD), n, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("   ").append(ds.joinToString(" · ")).append(tail)
            this.text = sb; textSize = 13f; setTextColor(fg); setPadding(0, dp(3), 0, dp(3))
        }
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, dp(8), dp(4)) }
        if (main.isEmpty()) box.addView(TextView(ctx).apply {
            this.text = "Zatím žádný ${if (kardio) "kardio" else "silový"} den. Klepni na den a vyber typ."; textSize = 13f; setTextColor(fg); alpha = 0.8f
        }) else {
            main.forEach { (t, ds) -> box.addView(line(colorOf(t), label(t), ds)) }
            if (rest.isNotEmpty()) box.addView(line(Color.parseColor("#9A9A86"), "VOLNO", rest))
        }
        if (extra.isNotEmpty()) box.addView(line(warm, if (kardio) "SILA" else "KARDIO", emptyList(),
            extra.joinToString(" · ") { (t, ds) -> "${label(t)} ${ds.joinToString(" ")}" }))
        host.addView(box)
    }
}

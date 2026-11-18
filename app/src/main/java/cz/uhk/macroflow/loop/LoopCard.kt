package cz.uhk.macroflow.loop

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import cz.uhk.macroflow.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Karta „Dnešní forma“ na Dashboardu: skóre a pásmo nahoře, barevné štítky toho, z čeho skóre vychází,
 * jedna věta „co dnes“ a návrh úprav, který jde použít nebo nechat být. „Proč?“ rozbalí důvody.
 */
object LoopCard {
    fun attach(coachCard: View, host: Fragment, onChanged: () -> Unit): () -> Unit {
        val ctx = coachCard.context
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        fun c(res: Int) = ContextCompat.getColor(ctx, res)
        fun pill(fill: Int, radius: Int = 14) = GradientDrawable().apply { cornerRadius = dp(radius).toFloat(); setColor(fill) }
        val parent = coachCard.parent as? LinearLayout ?: return {}
        val dark = c(R.color.brand_dark)

        val badge = TextView(ctx).apply {
            gravity = Gravity.CENTER; textSize = 22f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        }
        val label = TextView(ctx).apply {
            text = "DNEŠNÍ FORMA"; textSize = 10f; letterSpacing = 0.16f; setTypeface(null, Typeface.BOLD)
            setTextColor(c(R.color.brand_primary))
        }
        val band = TextView(ctx).apply { textSize = 18f; setTextColor(dark); typeface = Typeface.create("sans-serif-black", Typeface.NORMAL) }
        val chips = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val todo = TextView(ctx).apply {
            textSize = 14f; setTextColor(dark); setLineSpacing(0f, 1.1f)
            background = pill(c(R.color.brand_primary_alpha10), 16)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val why = TextView(ctx).apply {
            textSize = 13f; setTextColor(c(R.color.brand_primary)); setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(10), 0, 0)
        }
        val details = TextView(ctx).apply { textSize = 13.5f; setTextColor(dark); setLineSpacing(0f, 1.15f); visibility = View.GONE; setPadding(0, dp(6), 0, 0) }
        val actions = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        fun btn(t: String, filled: Boolean) = MaterialButton(ctx).apply {
            text = t; isAllCaps = false; cornerRadius = dp(16); insetTop = 0; insetBottom = 0
            if (filled) { backgroundTintList = ColorStateList.valueOf(dark); setTextColor(c(R.color.brand_cream)) }
            else { backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT); setTextColor(c(R.color.brand_primary)) }
        }
        val use = btn("Použít", true); val keep = btn("Nechat podle plánu", false)
        actions.addView(use); actions.addView(keep)
        val undo = btn("Vrátit", false)

        val header = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(badge, LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0)
                addView(label); addView(band)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val card = MaterialCardView(ctx).apply {
            radius = 24 * d; cardElevation = 0f; strokeWidth = dp(1)
            strokeColor = c(R.color.brand_dark_alpha12); setCardBackgroundColor(c(R.color.profile_card))
            visibility = View.GONE; isClickable = true; isFocusable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(16), dp(12), dp(16), 0) }
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(14))
                addView(header)
                addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; setPadding(0, dp(12), 0, dp(12)); addView(chips) })
                addView(todo); addView(why); addView(details); addView(actions); addView(undo)
            })
        }
        parent.addView(card, parent.indexOfChild(coachCard) + 1)
        fun toggle() {
            val open = details.visibility == View.GONE
            details.visibility = if (open) View.VISIBLE else View.GONE
            why.text = if (open) "Skrýt ▴" else "Proč? ▾"
        }
        card.setOnClickListener { toggle() }

        var current: Advice? = null
        fun act(apply: Boolean) {
            val a = current ?: return
            host.viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) { runCatching { LoopData.decide(ctx, a, apply) } }
                onChanged()
            }
        }
        use.setOnClickListener { act(true) }
        keep.setOnClickListener { act(false) }
        undo.setOnClickListener {
            host.viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) { runCatching { LoopData.undo(ctx) } }
                onChanged()
            }
        }

        fun render(s: LoopData.Snapshot) {
            val a = s.advice; val r = s.readiness
            current = a
            if (a == null || r == null) { card.visibility = View.GONE; return }
            card.visibility = View.VISIBLE
            val col = when { a.score >= 75 -> c(R.color.brand_primary); a.score >= 55 -> c(R.color.brand_accent_warm); else -> Color.parseColor("#C8553D") }
            badge.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(col) }
            badge.text = a.score.toString()
            badge.contentDescription = "Skóre formy ${a.score} ze 100"
            band.text = a.band

            chips.removeAllViews()
            r.parts.forEach { p ->
                val (fill, txt) = when {
                    p.goodness >= 70 -> c(R.color.brand_primary_alpha20) to dark
                    p.goodness >= 55 -> c(R.color.brand_accent_warm_alpha30) to dark
                    else -> c(R.color.brand_accent_deep_alpha20) to c(R.color.brand_accent_deep)
                }
                chips.addView(TextView(ctx).apply {
                    text = "${p.name} · ${p.note}"; textSize = 12f; setTextColor(txt)
                    background = pill(fill); setPadding(dp(10), dp(5), dp(10), dp(5))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) }
                })
            }

            todo.text = a.action.ifBlank { "Drž se plánu, nic měnit nemusíš." }
            details.text = buildString {
                a.reasons.forEach { append("→ $it\n") }
                a.bedtimeMin?.let { append("→ do postele nejpozději ${"%02d:%02d".format(it / 60, it % 60)}\n") }
                a.warnings.forEach { append("⚠ $it\n") }
                if (isNotEmpty()) append("\n")
                append("Skóre se počítá z tvých vlastních průměrů za posledních 28 dní. Chybí-li nějaký údaj, vynechá se.")
            }
            why.text = if (details.visibility == View.VISIBLE) "Skrýt ▴" else "Proč? ▾"
            val decided = s.decision != null
            actions.visibility = if (a.hasProposal && !decided) View.VISIBLE else View.GONE
            use.text = "Použít (${LoopData.describe(a)})"
            undo.visibility = if (decided) View.VISIBLE else View.GONE
            undo.text = if (s.decision == "applied") "Použito: ${LoopData.describe(a)}. Vrátit" else "Plán beze změny. Změnit"
        }

        return {
            host.viewLifecycleOwner.lifecycleScope.launch {
                val s = withContext(Dispatchers.IO) {
                    runCatching { LoopData.snapshot(ctx.applicationContext) }.getOrElse { android.util.Log.e("Loop", "snapshot failed", it); null }
                }
                if (s == null) card.visibility = View.GONE else render(s)
            }
        }
    }
}

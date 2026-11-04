package cz.uhk.macroflow.loop

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
 * Karta „Dnešní forma“ na Dashboardu: skóre připravenosti, věta co dnes udělat a proč, a návrh úprav,
 * který jde použít nebo nechat být. Klepnutím se rozbalí důvody.
 */
object LoopCard {
    fun attach(coachCard: View, host: Fragment, onChanged: () -> Unit): () -> Unit {
        val ctx = coachCard.context
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val parent = coachCard.parent as? LinearLayout ?: return {}
        val dark = ContextCompat.getColor(ctx, R.color.brand_dark)

        val badge = TextView(ctx).apply { gravity = Gravity.CENTER; textSize = 20f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) }
        val title = TextView(ctx).apply { textSize = 13f; setTextColor(dark); alpha = 0.7f }
        val headline = TextView(ctx).apply { textSize = 15f; setTextColor(dark); setLineSpacing(0f, 1.1f) }
        val details = TextView(ctx).apply { textSize = 13.5f; setTextColor(dark); setLineSpacing(0f, 1.15f); visibility = View.GONE; setPadding(0, dp(10), 0, 0) }
        val actions = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        fun btn(t: String, filled: Boolean) = MaterialButton(ctx).apply {
            text = t; isAllCaps = false; cornerRadius = dp(16); insetTop = 0; insetBottom = 0
            if (filled) { setBackgroundColor(ContextCompat.getColor(ctx, R.color.brand_primary)); setTextColor(Color.WHITE) }
            else { setBackgroundColor(Color.TRANSPARENT); setTextColor(ContextCompat.getColor(ctx, R.color.brand_primary)) }
        }
        val use = btn("Použít", true); val keep = btn("Nechat podle plánu", false)
        actions.addView(use); actions.addView(keep)
        val undo = btn("Vrátit", false)

        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0)
            addView(title); addView(headline); addView(details); addView(actions); addView(undo)
        }
        val card = MaterialCardView(ctx).apply {
            radius = 24 * d; cardElevation = 0f; strokeWidth = dp(1)
            strokeColor = ContextCompat.getColor(ctx, R.color.brand_primary); setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.brand_cream))
            visibility = View.GONE; isClickable = true; isFocusable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(16), dp(12), dp(16), 0) }
            addView(LinearLayout(ctx).apply {
                gravity = Gravity.TOP; setPadding(dp(16), dp(14), dp(16), dp(14))
                addView(badge, LinearLayout.LayoutParams(dp(56), dp(56)))
                addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
        parent.addView(card, parent.indexOfChild(coachCard) + 1)
        card.setOnClickListener { details.visibility = if (details.visibility == View.GONE) View.VISIBLE else View.GONE }

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
            val col = when { a.score >= 75 -> R.color.brand_primary; a.score >= 55 -> R.color.brand_accent_warm; else -> 0 }
            badge.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (col != 0) ContextCompat.getColor(ctx, col) else Color.parseColor("#C8553D")) }
            badge.text = a.score.toString()
            title.text = "Dnešní forma"
            headline.text = a.headline
            details.text = buildString {
                append("Z čeho vychází:\n")
                r.parts.forEach { append("• ${it.name}: ${it.note}\n") }
                a.reasons.forEach { append("→ $it\n") }
                a.bedtimeMin?.let { append("→ do postele nejpozději ${"%02d:%02d".format(it / 60, it % 60)}\n") }
                a.warnings.forEach { append("⚠ $it\n") }
                append("\nPočítá se z tvých vlastních průměrů za posledních 28 dní. Chybí-li nějaký údaj, vynechá se.")
            }.trimEnd()
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

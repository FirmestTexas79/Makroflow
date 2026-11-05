package cz.uhk.macroflow.report

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import cz.uhk.macroflow.R
import cz.uhk.macroflow.history.CalendarWeek
import cz.uhk.macroflow.learning.LearningData
import cz.uhk.macroflow.learning.LearningModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Týdenní report (docs/adr/0100): hlavní věta, pět oblastí se stavem, rekordy a nejvýš tři kroky na příští týden.
 * Vzhled stejný jako „Co se aplikace naučila“: krémové pozadí, pastelové karty, detail se rozbalí klepnutím.
 */
class WeeklyReportSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "WeeklyReportSheet"
        private const val MAX_WEEKS_BACK = 12L
        fun show(fm: FragmentManager) {
            if (fm.findFragmentByTag(TAG) != null) return
            WeeklyReportSheet().show(fm, TAG)
        }
    }

    private lateinit var col: LinearLayout
    private var anchor: LocalDate = LocalDate.now()
    private var job: Job? = null

    private fun c(id: Int) = ContextCompat.getColor(requireContext(), id)
    private val dark get() = c(R.color.brand_dark)
    private val cream get() = c(R.color.brand_cream)
    private val primary get() = c(R.color.brand_primary)
    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private fun statusColor(s: Status) = Color.parseColor(when (s) {
        Status.GOOD -> "#A9C98B"; Status.OK -> "#E6D9A2"; Status.WARN -> "#F5BC8A"; Status.NONE -> "#CFCFC4"
    })
    private fun domainColor(d: Domain) = Color.parseColor(when (d) {
        Domain.FOOD -> "#F5BC8A"; Domain.TRAINING -> "#A9C98B"; Domain.SLEEP -> "#C3B1E3"
        Domain.BODY -> "#8CC0E0"; Domain.MOVE -> "#8CCBB0"
    })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20f), dp(18f), dp(20f), dp(32f)) }
        return FrameLayout(ctx).apply {
            setBackgroundResource(R.drawable.bg_sheet_cream)
            addView(NestedScrollView(ctx).apply { addView(col) })
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let { d ->
            d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                sheet.layoutParams = sheet.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
            }
            d.behavior.skipCollapsed = true
            d.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { render() }

    private fun tv(text: String, sp: Float, color: Int, black: Boolean = false, alpha: Float = 1f) = TextView(requireContext()).apply {
        this.text = text; textSize = sp; setTextColor(color); this.alpha = alpha
        if (black) typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }

    private fun round(color: Int, r: Float) = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(color) }

    private fun render() {
        job?.cancel()
        col.removeAllViews()
        header()
        col.addView(tv("Počítám týden…", 14f, dark, alpha = 0.6f).apply { setPadding(dp(4f), dp(24f), 0, 0) })
        val app = requireContext().applicationContext
        val shown = anchor
        job = viewLifecycleOwner.lifecycleScope.launch {
            val (days, report) = withContext(Dispatchers.IO) {
                val used = runCatching { LearningData.quick(app).daysUsed }.getOrDefault(0)
                used to runCatching { WeeklyReportData.load(app, shown) }
                    .onFailure { android.util.Log.e("WeeklyReport", "load failed", it) }.getOrNull()
            }
            col.removeAllViews()
            header()
            when {
                days < LearningModel.UNLOCK_DAYS -> col.addView(message(
                    "Report se odemkne za ${LearningModel.days((LearningModel.UNLOCK_DAYS - days).coerceAtLeast(1))}",
                    "Do té doby sbírám váhu, jídlo, tréninky a spánek. Pak ti každý týden ukážu, jak se ti dařilo."))
                report == null -> col.addView(message("Report se nepodařilo spočítat", "Zkus to prosím za chvíli znovu."))
                else -> body(report)
            }
            footer(report)
        }
    }

    private fun header() {
        col.addView(tv("Týdenní report", 26f, dark, black = true))
        val today = LocalDate.now()
        val nav = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun arrow(label: String, enabled: Boolean, desc: String, delta: Long) = tv(label, 26f, primary, black = true).apply {
            gravity = Gravity.CENTER; minWidth = dp(44f); minHeight = dp(44f); contentDescription = desc
            alpha = if (enabled) 1f else 0.25f; isEnabled = enabled
            if (enabled) setOnClickListener { anchor = anchor.plusWeeks(delta); render() }
        }
        nav.addView(arrow("‹", anchor.isAfter(today.minusWeeks(MAX_WEEKS_BACK)), "Předchozí týden", -1))
        nav.addView(tv(CalendarWeek.label(anchor, today), 14f, primary).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(arrow("›", CalendarWeek.canGoForward(anchor, today), "Další týden", 1))
        col.addView(nav, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2f); bottomMargin = dp(10f) })
    }

    private fun message(title: String, text: String): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; background = round(dark, 28f); setPadding(dp(20f), dp(18f), dp(20f), dp(20f))
        addView(tv(title, 20f, cream, black = true))
        addView(tv(text, 14f, cream, alpha = 0.8f).apply { setPadding(0, dp(6f), 0, 0) })
    }

    private fun body(r: Report) {
        if (!r.isFinal) col.addView(tv("Předběžné: neděle se ještě dopočítá.", 12f, dark, alpha = 0.6f).apply { setPadding(dp(4f), 0, 0, dp(8f)) })
        col.addView(hero(r))
        r.cards.forEach { col.addView(card(it)) }
        if (r.records.isNotEmpty()) col.addView(records(r))
        col.addView(next(r))
        col.addView(tv("Hodnotí se jen to, co jsi zapsal. Chybějící dny se nepočítají jako neúspěch. Klepnutím na kartu se rozbalí detail.", 12f, dark, alpha = 0.6f)
            .apply { setPadding(dp(4f), dp(14f), dp(4f), 0) })
    }

    private fun hero(r: Report): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; background = round(dark, 28f); setPadding(dp(20f), dp(18f), dp(20f), dp(18f))
        addView(tv(r.headline, 19f, cream, black = true))
        addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            r.cards.forEach { card ->
                addView(LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                    contentDescription = "${card.domain.label}: ${card.status.label}"
                    addView(tv(card.domain.icon, 20f, cream))
                    addView(View(requireContext()).apply { background = round(statusColor(card.status), 4f) },
                        LinearLayout.LayoutParams(dp(28f), dp(6f)).apply { topMargin = dp(6f) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16f) })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12f) }
    }

    private fun chip(s: Status) = tv(s.label, 11f, dark, black = true).apply {
        background = round(statusColor(s), 12f); setPadding(dp(10f), dp(3f), dp(10f), dp(3f))
    }

    private fun card(k: Card): View {
        val ctx = requireContext()
        val detail = tv(k.details.joinToString("\n") { "• $it" }, 13f, dark, alpha = 0.8f).apply {
            visibility = View.GONE; setPadding(0, dp(10f), 0, 0); setLineSpacing(dp(2f).toFloat(), 1f)
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            background = round(ColorUtils.setAlphaComponent(domainColor(k.domain), 70), 22f)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10f) }
            contentDescription = "${k.domain.label}: ${k.status.label}. ${k.big} ${k.caption}. ${k.line}"
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(tv(k.domain.icon, 20f, dark).apply { setPadding(0, 0, dp(10f), 0) })
                addView(tv(k.domain.label, 13f, primary, black = true), LinearLayout.LayoutParams(0, -2, 1f))
                addView(chip(k.status))
            })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM
                addView(tv(k.big, 30f, dark, black = true))
                addView(tv(k.caption, 13f, dark, alpha = 0.7f).apply { setPadding(dp(8f), 0, 0, dp(5f)) })
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })
            addView(tv(k.line, 14.5f, dark).apply { setPadding(0, dp(4f), 0, 0) })
            if (k.details.isNotEmpty()) {
                addView(detail)
                isClickable = true; isFocusable = true
                setOnClickListener { detail.visibility = if (detail.visibility == View.GONE) View.VISIBLE else View.GONE }
            }
        }
    }

    private fun records(r: Report): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        background = round(Color.parseColor("#F6D9A6"), 22f)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10f) }
        addView(tv("🏆 Rekordy týdne", 15f, dark, black = true))
        r.records.forEach {
            addView(tv("${it.name}: 1RM ≈ ${"%.1f".format(java.util.Locale.US, it.e1rmKg).replace('.', ',').removeSuffix(",0")} kg " +
                "(+${"%.1f".format(java.util.Locale.US, it.gainKg).replace('.', ',').removeSuffix(",0")} kg)", 14f, dark).apply { setPadding(0, dp(6f), 0, 0) })
        }
    }

    private fun next(r: Report): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        background = GradientDrawable().apply { cornerRadius = dp(22f).toFloat(); setColor(Color.TRANSPARENT); setStroke(dp(1.5f), dark) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4f) }
        addView(tv("Příští týden", 15f, dark, black = true))
        r.actions.forEach { addView(tv("• $it", 14f, dark).apply { setPadding(0, dp(6f), 0, 0) }) }
    }

    private fun footer(r: Report?) {
        if (r != null) col.addView(MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Sdílet shrnutí"; isAllCaps = false; textSize = 15f; cornerRadius = dp(28f); setTextColor(dark)
            strokeColor = android.content.res.ColorStateList.valueOf(Color.argb(70, 40, 54, 24))
            setOnClickListener {
                val text = WeeklyReportModel.exportText(r, CalendarWeek.label(anchor))
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_SUBJECT, "Makroflow: týdenní report")
                }
                startActivity(Intent.createChooser(send, null))
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(52f)).apply { topMargin = dp(18f) }
        })
        col.addView(MaterialButton(requireContext()).apply {
            text = "Hotovo"; isAllCaps = false; textSize = 16f; cornerRadius = dp(28f)
            backgroundTintList = android.content.res.ColorStateList.valueOf(primary); setTextColor(cream)
            setOnClickListener { dismiss() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(56f)).apply { topMargin = dp(if (r != null) 12f else 24f) }
        })
    }
}

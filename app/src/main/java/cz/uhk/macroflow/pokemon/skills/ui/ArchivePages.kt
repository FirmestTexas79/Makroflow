package cz.uhk.macroflow.pokemon.skills.ui

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import cz.uhk.macroflow.pokemon.story.Dreams
import cz.uhk.macroflow.pokemon.story.Insight

/**
 * Spisy Kustodiátu v deníku (docs/adr/0062): tmavá cedule s Vhledem, měřák Vhledu, roztržené
 * listy a hlášení o snech jako složky spisu (štítek, razítko DŮVĚRNÉ, začerněná místa jako
 * černé pruhy). Nenalezené listy a sny jsou zavřené složky „???“ – je vidět, kolik jich chybí.
 */
object ArchivePages {

    private val WHITE = Color.parseColor("#FEFAE0")
    private val INK = Color.parseColor("#2A2118")
    private val CENSOR = Color.parseColor("#1A1612")
    private val STAMP = Color.parseColor("#A8322A")
    /** Vhled, při kterém je odkryto skoro všechno (měřák má tolik dílků). */
    private const val METER = 6

    private fun outlined(t: TextView): TextView = t.apply { setShadowLayer(3f, 0f, 1.5f, Color.parseColor("#101828")) }

    /** Začerněná místa (█) jako černé pruhy přes text. */
    fun censored(text: String): CharSequence {
        val s = SpannableString(text)
        Regex("█+").findAll(text).forEach { m ->
            s.setSpan(BackgroundColorSpan(CENSOR), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(ForegroundColorSpan(CENSOR), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return s
    }

    fun files(container: LinearLayout, flags: Set<String>) {
        container.removeAllViews()
        val ui = WoodUi(container.context)
        val u = 2f * ui.dp
        val insight = Insight.level(flags)

        // cedule archivu: tmavá ocel místo dřeva
        val plaque = ui.row().apply {
            background = BevelDrawable(2.5f * ui.dp, Color.parseColor("#3A3A3A"), Color.parseColor("#5A5A5A"), Color.parseColor("#222222"), Color.parseColor("#0E0E0E"))
            setPadding(ui.px(14f), ui.px(11f), ui.px(14f), ui.px(13f))
        }
        plaque.addView(ui.icon(Insight.pageIcon(), Insight.ICON, Insight.ICON, 28f))
        plaque.addView(outlined(ui.text("Spisy Kustodiátu", 24f, WHITE)).apply { setPadding(ui.px(8f), 0, 0, ui.px(2f)) },
            ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        plaque.addView(outlined(ui.text("S-7", 15f, Color.parseColor("#D97A6A"))))
        container.addView(plaque, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = ui.px(34f) })

        // měřák Vhledu
        val meter = ui.row().apply {
            background = BevelDrawable(1.5f * ui.dp, Color.parseColor("#EAD6AE"), Color.parseColor("#F8EBCF"), Color.parseColor("#CDB083"), Color.parseColor("#9C7A4E"))
            setPadding(ui.px(10f), ui.px(6f), ui.px(10f), ui.px(7f))
        }
        meter.addView(ui.text("Vhled $insight", 17f, INK), ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        for (i in 1..METER) meter.addView(View(ui.ctx).apply {
            background = if (i <= insight) BevelDrawable(1.2f * ui.dp, Color.parseColor("#7A3B8A"), Color.parseColor("#A867B8"), Color.parseColor("#4A1F55"), Color.parseColor("#1E140C"))
                else BevelDrawable(1.2f * ui.dp, Color.parseColor("#C9B48C"), Color.parseColor("#D9C6A0"), Color.parseColor("#A8936C"), Color.parseColor("#8C7556"))
        }, LinearLayout.LayoutParams(ui.px(14f), ui.px(14f)).apply { marginStart = ui.px(3f) })
        container.addView(meter, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(8f) })
        container.addView(ui.text("Čím víc toho víš, tím víc začerněných míst se odkryje.", 13f, ui.inkSoft, Gravity.CENTER),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(4f) })

        // roztržené listy
        val found = Insight.foundPages(flags).map { it.id }.toSet()
        sectionTitle(container, ui, "Roztržené listy", found.size, Insight.PAGES.size)
        Insight.PAGES.forEach { p ->
            if (p.id in found) folder(container, ui, "LIST ${p.id}", p.found, Insight.render(p.text, insight),
                Insight.hiddenCount(p.text, insight), Color.parseColor("#EBDDB4"), Color.parseColor("#C9B07A"))
            else closed(container, ui, "LIST ${p.id}", "Nenalezen")
        }

        // sny
        val dreamed = Dreams.dreamed(flags).map { it.id }.toSet()
        sectionTitle(container, ui, "Hlášení o snech", dreamed.size, Dreams.ALL.size)
        if (insight < Dreams.MIN_INSIGHT) container.addView(ui.text("Sny se začnou zdát od Vhledu ${Dreams.MIN_INSIGHT} – ráno po spánku.", 14f, ui.inkSoft).apply {
            setPadding(ui.px(2f), 0, 0, ui.px(6f))
        })
        Dreams.ALL.forEach { d ->
            if (d.id in dreamed) folder(container, ui, "SEN D-${d.id}", d.who, "„" + Insight.render(d.text, insight) + "“",
                Insight.hiddenCount(d.text, insight), Color.parseColor("#DCE3EA"), Color.parseColor("#9FB0C2"))
            else closed(container, ui, "SEN D-${d.id}", "Ještě se nezdál")
        }
    }

    private fun sectionTitle(container: LinearLayout, ui: WoodUi, title: String, have: Int, total: Int) {
        val r = ui.row().apply { setPadding(ui.px(2f), ui.px(14f), ui.px(2f), ui.px(6f)) }
        r.addView(ui.text(title, 21f, Color.parseColor("#8A3A1A")), ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(ui.text("$have / $total", 16f, if (have == total) Color.parseColor("#606C38") else ui.inkSoft))
        container.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** Štítek složky nad papírem. */
    private fun tab(ui: WoodUi, label: String, paper: Int, edge: Int): TextView =
        ui.text(label, 14f, INK).apply {
            background = BevelDrawable(1.5f * ui.dp, paper, SkillTreeView.blend(paper, Color.WHITE, 0.4f), edge, Color.parseColor("#6A5640"))
            setPadding(ui.px(10f), ui.px(3f), ui.px(12f), ui.px(5f))
            typeface = Typeface.create(typeface, Typeface.BOLD)
        }

    private fun folder(container: LinearLayout, ui: WoodUi, label: String, subtitle: String, body: String, hidden: Int, paper: Int, edge: Int) {
        val wrap = ui.column()
        wrap.addView(tab(ui, label, paper, edge), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { marginStart = ui.px(10f); bottomMargin = -ui.px(3f) })
        val sheet = FrameLayout(ui.ctx).apply {
            background = BevelDrawable(1.5f * ui.dp, paper, SkillTreeView.blend(paper, Color.WHITE, 0.4f), edge, Color.parseColor("#6A5640"))
            clipChildren = false
        }
        val col = ui.column().apply { setPadding(ui.px(12f), ui.px(10f), ui.px(12f), ui.px(10f)) }
        col.addView(ui.text(subtitle, 14f, Color.parseColor("#6A5640")).apply { setTypeface(typeface, Typeface.ITALIC); setPadding(0, 0, ui.px(70f), ui.px(6f)) })
        col.addView(TextView(ui.ctx).apply {
            text = censored(body); textSize = 14f; setTextColor(INK); typeface = Typeface.MONOSPACE
            setLineSpacing(ui.px(3f).toFloat(), 1f)
        })
        col.addView(ui.text(if (hidden == 0) "✓ Plně čitelné" else "Začerněno: $hidden ${if (hidden == 1) "místo" else if (hidden < 5) "místa" else "míst"}",
            13f, if (hidden == 0) Color.parseColor("#4E6B2A") else Color.parseColor("#7A3B8A")).apply { setPadding(0, ui.px(8f), 0, 0) })
        sheet.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // razítko
        sheet.addView(ui.text("DŮVĚRNÉ", 13f, STAMP, Gravity.CENTER).apply {
            background = android.graphics.drawable.GradientDrawable().apply { setStroke(ui.px(2f), STAMP) }
            setPadding(ui.px(6f), ui.px(2f), ui.px(6f), ui.px(3f))
            rotation = 8f; alpha = 0.75f
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.TOP).apply {
            setMargins(0, ui.px(8f), ui.px(10f), 0)
        })
        wrap.addView(sheet, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(wrap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ui.px(10f) })
    }

    /** Zavřená složka: jen štítek a „???“. */
    private fun closed(container: LinearLayout, ui: WoodUi, label: String, note: String) {
        val r = ui.row().apply {
            background = BevelDrawable(1.5f * ui.dp, Color.parseColor("#BFAE88"), Color.parseColor("#CDBD98"), Color.parseColor("#9C8A66"), Color.parseColor("#7A6A4E"))
            setPadding(ui.px(10f), ui.px(6f), ui.px(10f), ui.px(7f))
            alpha = 0.7f
        }
        r.addView(ui.icon(cz.uhk.macroflow.pokemon.skills.TreeArt.LOCK, cz.uhk.macroflow.pokemon.skills.TreeArt.SIZE, cz.uhk.macroflow.pokemon.skills.TreeArt.SIZE, 14f))
        r.addView(ui.text(label, 15f, INK).apply { setPadding(ui.px(8f), 0, 0, 0) }, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(ui.text("??? · $note", 13f, Color.parseColor("#5A4A34")))
        container.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ui.px(6f) })
    }
}

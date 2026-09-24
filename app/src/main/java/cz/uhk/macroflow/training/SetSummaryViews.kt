package cz.uhk.macroflow.training

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import cz.uhk.macroflow.training.analysis.RepRating
import cz.uhk.macroflow.training.analysis.RepRating.Metric
import cz.uhk.macroflow.training.analysis.SetSummary
import java.util.Locale

/**
 * Souhrn série pro uživatele hned po dokončení:
 * statistiky, tabulka opakování s barevným přechodem zelená → červená,
 * srovnání sérií daného cviku a legenda dole.
 */
object SetSummaryViews {

    private const val DARK = "#283618"
    private const val CREAM = "#FEFAE0"

    fun build(
        ctx: Context,
        title: String,
        set: SetSummary,
        todaysSets: List<SetSummary>,
        currentSetNumber: Int
    ): View {
        val dp = ctx.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(20), px(20), px(28))
            setBackgroundColor(Color.parseColor(CREAM))
        }

        root.addView(text(ctx, title, 20f, bold = true))
        root.addView(text(ctx, statsLine(set), 13f).apply { setPadding(0, px(6), 0, px(12)) })

        // ── Tabulka opakování ──
        val table = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val headers = listOf("#", "Body cm\nstart/obrat/konec", "Rozsah\ncm", "Spouštění\ns", "Zvedání\ns", "Rychlost\nm/s", "Dráha\ncm")
        val widths = listOf(32, 118, 64, 76, 70, 70, 60)
        table.addView(row(ctx, headers, widths, null, bold = true))
        set.reps.forEach { r ->
            val cells = listOf(
                "${r.index}",
                String.format(Locale.US, "%.0f / %.0f / %.0f", r.startCm, r.turnCm, r.endCm),
                String.format(Locale.US, "%.1f", r.romCm),
                String.format(Locale.US, "%.2f", r.eccentricMs / 1000.0),
                String.format(Locale.US, "%.2f", r.concentricMs / 1000.0),
                String.format(Locale.US, "%.2f", r.meanConcentricVelocity),
                String.format(Locale.US, "%.1f", r.deviationCm)
            )
            val scores = listOf(
                null, null,
                RepRating.score(Metric.ROM, r, set),
                RepRating.score(Metric.ECCENTRIC, r, set),
                RepRating.score(Metric.CONCENTRIC, r, set),
                RepRating.score(Metric.VELOCITY, r, set),
                RepRating.score(Metric.DEVIATION, r, set)
            )
            table.addView(row(ctx, cells, widths, scores, bold = false))
        }
        root.addView(HorizontalScrollView(ctx).apply { addView(table) })

        // ── Srovnání sérií ──
        if (todaysSets.size > 1) {
            root.addView(text(ctx, "Série dnes (${set.lift.label})", 15f, bold = true).apply { setPadding(0, px(16), 0, px(6)) })
            val chips = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            todaysSets.forEachIndexed { i, s ->
                val score = RepRating.setScore(s, todaysSets)
                chips.addView(chip(ctx, "${i + 1}: ${s.repCount}× · ${String.format(Locale.US, "%.2f", s.bestMcv)} m/s",
                    score, highlight = i + 1 == currentSetNumber))
            }
            root.addView(HorizontalScrollView(ctx).apply { addView(chips) })
        }

        // ── Legenda ──
        root.addView(legend(ctx))
        return root
    }

    fun statsLine(s: SetSummary): String = String.format(
        Locale("cs", "CZ"),
        "%d opakování · rozsah Ø %.1f cm (vážený %.1f, variabilita %.0f %%)\n" +
            "spouštění Ø %.2f s · zvedání Ø %.2f s · nejrychlejší %.2f m/s · ztráta rychlosti %.0f %%\n" +
            "odchylka dráhy Ø %.1f cm · kvalita sledování %.0f %%",
        s.repCount, s.avgRomCm, s.weightedRomCm, s.romCvPct,
        s.avgEccentricMs / 1000.0, s.avgConcentricMs / 1000.0, s.bestMcv, s.velocityLossPct,
        s.avgDeviationCm, s.quality * 100
    )

    private fun row(ctx: Context, cells: List<String>, widths: List<Int>, scores: List<Double?>?, bold: Boolean): View {
        val dp = ctx.resources.displayMetrics.density
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            cells.forEachIndexed { i, c ->
                val tv = text(ctx, c, if (bold) 11f else 13f, bold = bold).apply {
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams((widths[i] * dp).toInt(), (if (bold) 40 else 34).times(dp).toInt()).apply {
                        setMargins((1 * dp).toInt(), (1 * dp).toInt(), (1 * dp).toInt(), (1 * dp).toInt())
                    }
                    val score = scores?.getOrNull(i)
                    background = GradientDrawable().apply {
                        cornerRadius = 6 * dp
                        setColor(
                            if (score != null) ColorUtils.setAlphaComponent(RepRating.color(score), 150)
                            else Color.parseColor(if (bold) "#E9E5C8" else "#F4F0D6")
                        )
                    }
                }
                addView(tv)
            }
        }
    }

    private fun chip(ctx: Context, label: String, score: Double, highlight: Boolean): View {
        val dp = ctx.resources.displayMetrics.density
        return text(ctx, label, 12f, bold = highlight).apply {
            setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = (6 * dp).toInt() }
            background = GradientDrawable().apply {
                cornerRadius = 14 * dp
                setColor(ColorUtils.setAlphaComponent(RepRating.color(score), 150))
                if (highlight) setStroke((2 * dp).toInt(), Color.parseColor(DARK))
            }
        }
    }

    /** Legenda: přechodová lišta + co znamená barva u každého sloupce. */
    fun legend(ctx: Context): View {
        val dp = ctx.resources.displayMetrics.density
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (18 * dp).toInt(), 0, 0)
            addView(text(ctx, "Legenda", 13f, bold = true))
            addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (12 * dp).toInt())
                    .apply { topMargin = (6 * dp).toInt() }
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(RepRating.color(0.0), RepRating.color(0.5), RepRating.color(1.0))
                ).apply { cornerRadius = 6 * dp }
            })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(text(ctx, "jako nejlepší rep / v normě", 11f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(text(ctx, "výrazně horší", 11f).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
            Metric.entries.forEach { m -> addView(text(ctx, "• ${m.label}: ${m.legend}", 11f)) }
            addView(text(ctx, "Série se porovnávají podle nejrychlejšího repu a váženého rozsahu.", 11f))
        }
    }

    private fun text(ctx: Context, s: String, sizeSp: Float, bold: Boolean = false) = TextView(ctx).apply {
        text = s
        textSize = sizeSp
        setTextColor(Color.parseColor(DARK))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
}

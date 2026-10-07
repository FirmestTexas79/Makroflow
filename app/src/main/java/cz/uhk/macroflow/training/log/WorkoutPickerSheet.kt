package cz.uhk.macroflow.training.log

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import cz.uhk.macroflow.R
import cz.uhk.macroflow.training.body.BodyMapView
import cz.uhk.macroflow.training.body.TrainingMuscles
import java.time.LocalDate

/**
 * Výběr tréninku podle šablony (Plán → „Jiný“): karta pro PUSH / PULL / LEGS s postavičkou
 * a zapojenými partiemi, kdy se naposledy cvičil, a dlaždice variant A/B. Varianta na řadě je zvýrazněná.
 */
object WorkoutPickerSheet {

    /** [counts] = počet cviků v šabloně podle klíče (PUSH_A …), [todayKind] = trénink z dnešního plánu. */
    fun show(
        ctx: Context,
        history: List<LoggedSet>,
        todayDay: Int,
        counts: Map<String, Int>,
        todayKind: WorkoutTemplates.Kind?,
        onPick: (WorkoutTemplates.Kind, Char) -> Unit
    ) {
        val dp = ctx.resources.displayMetrics.density
        fun c(id: Int) = ContextCompat.getColor(ctx, id)
        fun px(v: Number) = (v.toFloat() * dp).toInt()
        val black = Typeface.create("sans-serif-black", Typeface.NORMAL)
        val dialog = BottomSheetDialog(ctx)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_sheet_cream)
            setPadding(px(20), px(12), px(20), px(28))
        }
        root.addView(View(ctx).apply {
            background = GradientDrawable().apply { cornerRadius = 100 * dp; setColor(c(R.color.brand_dark_alpha20)) }
        }, LinearLayout.LayoutParams(px(40), px(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = px(14) })
        root.addView(TextView(ctx).apply {
            text = "VYBER TRÉNINK"; textSize = 11f; letterSpacing = 0.12f; setTypeface(typeface, Typeface.BOLD)
            setTextColor(c(R.color.brand_cream))
            background = GradientDrawable().apply { cornerRadius = 100 * dp; setColor(c(R.color.brand_primary)) }
            setPadding(px(16), px(6), px(16), px(6))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(TextView(ctx).apply {
            text = "Který trénink?"; typeface = black; textSize = 30f; setTextColor(c(R.color.brand_dark))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(10) })
        root.addView(TextView(ctx).apply {
            text = "Varianty A a B se střídají. Zvýrazněná je na řadě."
            textSize = 13f; alpha = 0.7f; setTextColor(c(R.color.brand_dark))
        })

        WorkoutTemplates.Kind.entries.forEach { kind ->
            val planned = kind == todayKind
            val next = WorkoutTemplates.variantFor(kind, history, todayDay)
            val last = history.filter { WorkoutTemplates.parse(it.template)?.first == kind }.maxOfOrNull { it.day }
            val fg = if (planned) c(R.color.brand_cream) else c(R.color.brand_dark)

            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px(10), px(12), px(14), px(12))
                background = GradientDrawable().apply {
                    cornerRadius = 24 * dp
                    if (planned) setColor(c(R.color.brand_dark))
                    else { setColor(c(R.color.brand_dark_alpha05)); setStroke(px(1), c(R.color.brand_dark_alpha12)) }
                }
            }
            card.addView(BodyMapView(ctx).apply {
                showCaptions = false
                if (planned) { idleMuscleColor = Color.parseColor("#455A2D"); baseColor = Color.parseColor("#3A4A26") }
                setIntensities(TrainingMuscles.of(kind.planType), c(R.color.brand_accent_deep), animate = false)
                isClickable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(px(84), px(92)))

            val info = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(px(10), 0, px(8), 0) }
            if (planned) info.addView(TextView(ctx).apply {
                text = "DNES V PLÁNU"; textSize = 9.5f; letterSpacing = 0.14f; setTypeface(typeface, Typeface.BOLD)
                setTextColor(c(R.color.brand_accent_warm))
            })
            info.addView(TextView(ctx).apply { text = kind.label; typeface = black; textSize = 24f; setTextColor(fg) })
            info.addView(TextView(ctx).apply {
                text = TrainingMuscles.of(kind.planType).filterValues { it >= TrainingMuscles.PRIMARY }.keys.joinToString(" · ") { it.label }
                textSize = 12f; alpha = 0.8f; setTextColor(fg)
            })
            info.addView(TextView(ctx).apply {
                text = when (val d = last?.let { todayDay - it }) {
                    null -> "Zatím necvičeno"
                    0 -> "Naposledy dnes"
                    1 -> "Naposledy včera"
                    else -> "Naposledy před $d dny · ${LocalDate.ofEpochDay(last.toLong()).let { "${it.dayOfMonth}. ${it.monthValue}." }}"
                }
                textSize = 11.5f; alpha = 0.6f; setTextColor(fg)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(4) })
            card.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            // dlaždice variant A / B
            WorkoutTemplates.VARIANTS.forEachIndexed { i, v ->
                val isNext = v == next
                val tile = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        cornerRadius = 16 * dp
                        when {
                            isNext -> setColor(c(R.color.brand_accent_warm))
                            planned -> { setColor(Color.TRANSPARENT); setStroke(px(1.5f), c(R.color.brand_cream_alpha40)) }
                            else -> { setColor(Color.TRANSPARENT); setStroke(px(1.5f), c(R.color.brand_dark_alpha20)) }
                        }
                    }
                    isClickable = true
                    contentDescription = "${kind.label} $v" + if (isNext) ", na řadě" else ""
                    setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70).withEndAction {
                            dialog.dismiss(); onPick(kind, v)
                        }.start()
                    }
                }
                tile.addView(TextView(ctx).apply {
                    text = v.toString(); typeface = black; textSize = 22f; gravity = Gravity.CENTER
                    setTextColor(if (isNext) c(R.color.brand_dark) else fg)
                })
                tile.addView(TextView(ctx).apply {
                    val n = counts[WorkoutTemplates.key(kind, v)] ?: 0
                    text = if (isNext) "NA ŘADĚ" else "$n cviků"
                    textSize = 8.5f; letterSpacing = 0.06f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (isNext) c(R.color.brand_dark) else fg); alpha = if (isNext) 1f else 0.6f
                })
                card.addView(tile, LinearLayout.LayoutParams(px(58), px(64)).apply { if (i > 0) marginStart = px(8) })
            }
            root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = px(12) })
        }

        dialog.setContentView(NestedScrollView(ctx).apply { addView(root) })
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
            dialog.behavior.skipCollapsed = true; dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.show()
    }
}

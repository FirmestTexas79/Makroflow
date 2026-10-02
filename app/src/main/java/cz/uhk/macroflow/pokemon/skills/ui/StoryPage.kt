package cz.uhk.macroflow.pokemon.skills.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import cz.uhk.macroflow.pokemon.skills.TreeArt

/**
 * Stránka Příběh v dřevěném pixelovém stylu (docs/adr/0060): portrét v modrém rámu, cíl na
 * dřevěné cedulce, text kapitoly na pergamenu, kapitola na stužce, postup jako řada pixelových
 * kamenů a fáze jako klikací řádky (splněná = zelená, aktuální = oranžová, budoucí = zamčená).
 */
object StoryPage {

    private val WHITE = Color.parseColor("#FEFAE0")
    private val GOLD = Color.parseColor("#FFD54F")

    private fun outlined(t: TextView): TextView = t.apply { setShadowLayer(3f, 0f, 1.5f, Color.parseColor("#101828")) }

    /** Rámy a cedulky stránky; volá se při každém vykreslení (je to idempotentní). */
    fun style(
        portrait: ImageView, objective: TextView, storyScroll: View, chapterLabel: TextView,
        prev: View, next: View, objectiveDone: Boolean, secret: Boolean
    ) {
        val ui = WoodUi(portrait.context)
        val u = 2f * ui.dp
        portrait.background = BevelDrawable.navy(u, selected = secret)
        portrait.setPadding(ui.px(6f), ui.px(5f), ui.px(6f), ui.px(5f))
        portrait.layoutParams = portrait.layoutParams.apply { width = ui.px(78f); height = ui.px(78f) }

        // cíl: dřevěná cedulka, splněný zelený
        objective.background = if (objectiveDone)
            BevelDrawable(u, Color.parseColor("#4E6B2A"), Color.parseColor("#7A9A44"), Color.parseColor("#2F4518"), Color.parseColor("#1E140C"))
            else WoodPanelDrawable(1.6f * ui.dp, parchment = false)
        objective.backgroundTintList = null
        objective.setTextColor(WHITE)
        outlined(objective)
        objective.setPadding(ui.px(10f), ui.px(7f), ui.px(10f), ui.px(9f))

        // text kapitoly na pergamenovém listu
        storyScroll.background = BevelDrawable(1.5f * ui.dp, Color.parseColor("#F4E6C6"), Color.parseColor("#FBF3DF"),
            Color.parseColor("#D9C29A"), Color.parseColor("#9C7A4E"))
        storyScroll.setPadding(ui.px(9f), ui.px(7f), ui.px(9f), ui.px(7f))

        // kapitola na stužce (tajná linka zlatá)
        chapterLabel.background = if (secret)
            BevelDrawable(u, Color.parseColor("#C9961A"), GOLD, Color.parseColor("#8A6410"), Color.parseColor("#3B2A1A"))
            else WoodPanelDrawable(1.6f * ui.dp, parchment = false)
        chapterLabel.setTextColor(WHITE)
        outlined(chapterLabel)
        chapterLabel.setPadding(ui.px(10f), ui.px(5f), ui.px(10f), ui.px(7f))

        // listovací rohy jako malé dřevěné kostky
        listOf(prev, next).forEach { b ->
            b.background = BevelDrawable(u, Color.parseColor("#93602C"), Color.parseColor("#C48A4A"), Color.parseColor("#4F3016"), Color.parseColor("#2E1B0E"))
            (b as? ImageView)?.imageTintList = android.content.res.ColorStateList.valueOf(WHITE)
        }
    }

    /** Postup úkolu: pixelové kameny spojené čárkou, aktuální pulzuje, vpravo „3 / 7“. */
    fun tracker(container: LinearLayout, total: Int, active: Int) {
        // šířka stránky se zná až po rozvržení – řada se podle ní přizpůsobí
        if (container.width == 0) { container.post { if (container.width > 0) tracker(container, total, active) }; return }
        container.removeAllViews()
        val ui = WoodUi(container.context)
        val u = 1.5f * ui.dp
        val stone = ui.px(13f)
        val maxW = container.width.takeIf { it > 0 } ?: Int.MAX_VALUE
        // u dlouhých úkolů se spojky zkrátí, aby se řada vešla
        val gap = (((maxW - ui.px(44f)) - total * stone) / (total - 1).coerceAtLeast(1)).coerceIn(ui.px(2f), ui.px(12f))
        for (i in 0 until total) {
            val done = i < active
            val cur = i == active
            val v = View(ui.ctx).apply {
                background = when {
                    done -> BevelDrawable(u, Color.parseColor("#7F9148"), Color.parseColor("#A9BC6A"), Color.parseColor("#4E5E24"), Color.parseColor("#1E140C"))
                    cur -> BevelDrawable(u, Color.parseColor("#BC6C25"), Color.parseColor("#E09A50"), Color.parseColor("#7A4416"), Color.parseColor("#1E140C"), selected = true)
                    else -> BevelDrawable(u, Color.parseColor("#D9C29A"), Color.parseColor("#E9D9B6"), Color.parseColor("#B89F78"), Color.parseColor("#8C7556"))
                }
            }
            container.addView(v, LinearLayout.LayoutParams(stone, stone))
            if (cur) ObjectAnimator.ofFloat(v, View.ALPHA, 1f, 0.55f).apply {
                duration = 600; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            }.also { anim ->
                v.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(x: View) { anim.start() }
                    override fun onViewDetachedFromWindow(x: View) { anim.cancel() }
                })
            }
            if (i < total - 1) container.addView(View(ui.ctx).apply {
                setBackgroundColor(if (done) Color.parseColor("#606C38") else Color.parseColor("#B89F78"))
            }, LinearLayout.LayoutParams(gap, ui.px(3f)).apply { gravity = Gravity.CENTER_VERTICAL })
        }
        container.addView(ui.text("${active.coerceAtMost(total)}/$total", 15f, ui.rust).apply { setPadding(ui.px(6f), 0, 0, ui.px(2f)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_VERTICAL })
        container.layoutParams = container.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
    }

    /** Fáze úkolu jako řádky; známé jdou otevřít, budoucí jsou zamčené „???“. */
    fun stages(container: LinearLayout, titles: List<String>, current: Int, allDone: Boolean, selected: Int?, onSelect: (Int) -> Unit) {
        container.removeAllViews()
        val ui = WoodUi(container.context)
        val u = 1.5f * ui.dp
        titles.forEachIndexed { i, title ->
            val done = allDone || i < current
            val cur = !allDone && i == current
            val known = done || cur
            val sel = selected == i
            val row = ui.row().apply {
                background = when {
                    !known -> BevelDrawable(u, Color.parseColor("#D9C29A"), Color.parseColor("#E5D2AC"), Color.parseColor("#BFA57C"), Color.parseColor("#9C8460"))
                    cur -> BevelDrawable(u, Color.parseColor("#F4E6C6"), Color.parseColor("#FBF3DF"), Color.parseColor("#D9C29A"), Color.parseColor("#BC6C25"), selected = sel)
                    else -> BevelDrawable(u, Color.parseColor("#DCE6BE"), Color.parseColor("#EDF3D8"), Color.parseColor("#B4C48C"), Color.parseColor("#606C38"), selected = sel)
                }
                setPadding(ui.px(6f), ui.px(5f), ui.px(8f), ui.px(6f))
                alpha = if (known) 1f else 0.7f
                if (known) setOnClickListener { onSelect(i) }
                contentDescription = if (known) "Fáze ${i + 1}: $title" else "Fáze ${i + 1}: zatím neznámá"
            }
            // odznak stavu
            val badge = FrameLayout(ui.ctx).apply {
                background = when {
                    done -> BevelDrawable(u, Color.parseColor("#7F9148"), Color.parseColor("#A9BC6A"), Color.parseColor("#4E5E24"), Color.parseColor("#1E140C"))
                    cur -> BevelDrawable(u, Color.parseColor("#BC6C25"), Color.parseColor("#E09A50"), Color.parseColor("#7A4416"), Color.parseColor("#1E140C"))
                    else -> BevelDrawable(u, Color.parseColor("#8C7556"), Color.parseColor("#A08A6A"), Color.parseColor("#6A5640"), Color.parseColor("#3B2A1A"))
                }
            }
            if (known) badge.addView(outlined(ui.text(if (done) "✓" else "${i + 1}", 14f, WHITE, Gravity.CENTER)),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            else badge.addView(ui.icon(TreeArt.LOCK, TreeArt.SIZE, TreeArt.SIZE, 12f), FrameLayout.LayoutParams(ui.px(12f), ui.px(12f), Gravity.CENTER))
            row.addView(badge, LinearLayout.LayoutParams(ui.px(22f), ui.px(22f)))
            row.addView(ui.text(if (known) title else "???", 15f, when {
                cur -> Color.parseColor("#9A5518"); done -> Color.parseColor("#4E5E24"); else -> Color.parseColor("#8C7556")
            }).apply { setPadding(ui.px(7f), 0, 0, ui.px(1f)); if (sel) paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG },
                ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            container.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ui.px(5f) })
        }
    }
}

package cz.uhk.macroflow.pokemon.skills.ui

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import cz.uhk.macroflow.pokemon.balls.Makroball
import cz.uhk.macroflow.pokemon.skills.Berry
import cz.uhk.macroflow.pokemon.skills.Crafting
import cz.uhk.macroflow.pokemon.skills.Garden
import cz.uhk.macroflow.pokemon.skills.Resource
import cz.uhk.macroflow.pokemon.skills.Skill
import cz.uhk.macroflow.pokemon.skills.SkillArt
import cz.uhk.macroflow.pokemon.skills.SkillState

/** Dřevěná menu dílny na louce: výběr semínka pro záhon a pracovní stůl (docs/adr/0034). */
object WorkshopMenus {

    private const val TAG = "wood_menu"

    fun isOpen(root: ViewGroup) = root.findViewWithTag<View>(TAG) != null

    fun close(root: ViewGroup) { root.findViewWithTag<View>(TAG)?.let { root.removeView(it) } }

    /** Ztmavené pozadí + dřevěný panel uprostřed; klepnutí mimo panel menu zavře. */
    private fun show(root: FrameLayout, title: String, subtitle: String?, build: (WoodUi, LinearLayout, () -> Unit) -> Unit) {
        close(root)
        val ui = WoodUi(root.context)
        val dim = FrameLayout(root.context).apply {
            tag = TAG
            setBackgroundColor(Color.parseColor("#A6100A04"))
            isClickable = true
            elevation = 150f
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        val closeFn = { root.removeView(dim) }
        dim.setOnClickListener { closeFn() }
        val panel = ui.column().apply {
            background = WoodPanelDrawable(3f * ui.dp)
            isClickable = true
            setPadding(ui.px(20f), ui.px(18f), ui.px(20f), ui.px(18f))
        }
        val w = minOf(root.width - ui.px(32f), ui.px(380f))
        val head = ui.row()
        head.addView(ui.text(title, 26f).apply { layoutParams = ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        head.addView(ui.text("✕", 24f, ui.inkSoft).apply {
            setPadding(ui.px(10f), 0, ui.px(4f), ui.px(4f)); setOnClickListener { closeFn() }
        })
        panel.addView(head)
        subtitle?.let { panel.addView(ui.text(it, 15f, ui.inkSoft).apply { setPadding(0, ui.px(2f), 0, ui.px(8f)) }) }
        val body = ui.column()
        build(ui, body, closeFn)
        val scroll = ScrollView(root.context).apply { addView(body); isVerticalScrollBarEnabled = false }
        panel.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        dim.addView(panel, FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            topMargin = ui.px(40f); bottomMargin = ui.px(40f)
        })
        panel.scaleX = 0.92f; panel.scaleY = 0.92f; panel.alpha = 0f
        root.addView(dim)
        panel.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160).start()
    }

    private fun card(ui: WoodUi): LinearLayout = ui.row().apply {
        setPadding(ui.px(10f), ui.px(8f), ui.px(10f), ui.px(8f))
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 4 * ui.dp; setColor(Color.parseColor("#26BC6C25"))
            setStroke((1.5f * ui.dp).toInt(), Color.parseColor("#55BC6C25"))
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = ui.px(8f) }
    }

    private fun duration(sec: Long): String = when {
        sec >= 3600 && sec % 3600 == 0L -> "${sec / 3600} h"
        sec >= 3600 -> "${sec / 3600} h ${(sec % 3600) / 60} min"
        else -> "${(sec + 59) / 60} min"
    }

    /** Výběr semínka pro prázdný záhon. */
    fun seedMenu(root: FrameLayout, plotIndex: Int, owned: Map<String, Int>, state: SkillState, onPlant: (Berry) -> Unit) {
        show(root, "Záhon ${plotIndex + 1}", "Co zasadíš? Čím vzácnější bobule, tím déle roste.") { ui, body, close ->
            Berry.entries.forEach { b ->
                val n = owned[b.seedItemId] ?: 0
                val c = card(ui)
                c.addView(ui.icon(SkillArt.seed(b), SkillArt.ITEM, SkillArt.ITEM, 40f))
                val info = ui.column().apply { setPadding(ui.px(10f), 0, ui.px(6f), 0) }
                info.addView(ui.text("${b.seedLabel}  ×$n", 20f, if (n > 0) ui.ink else ui.inkSoft))
                val xp = state.gain(Skill.HARVESTING, b.harvestXp.toDouble())
                info.addView(ui.text("roste ${duration(Garden.growSeconds(b, state.growthSpeedup))} · +$xp XP Pěstování", 15f, ui.inkSoft))
                c.addView(info, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                c.addView(ui.button("Zasadit", n > 0) { close(); onPlant(b) })
                body.addView(c)
            }
            body.addView(ui.text("Semínka koupíš v obchodě ve městě, občas padnou i z travních Makromonů.", 15f, ui.inkSoft).apply {
                setPadding(0, ui.px(4f), 0, 0)
            })
        }
    }

    /** Pracovní stůl: výroba Makroballů z fragmentu energie a bobule. */
    fun craftMenu(root: FrameLayout, owned: Map<String, Int>, state: SkillState, onCraft: (Makroball, Int) -> Unit) {
        val frags = owned[Resource.ENERGY.itemId] ?: 0
        val multi = (state.passive(Skill.CRAFTING) * 100).toInt()
        show(root, "Pracovní stůl", "Fragmenty energie: $frags · šance na dvojitou výrobu $multi %") { ui, body, close ->
            Makroball.entries.forEach { ball ->
                val berry = Berry.forBall(ball)
                val max = Crafting.maxCraftable(ball, owned)
                val c = card(ui)
                c.addView(ui.icon(ball.pixels, Makroball.SIZE, Makroball.SIZE, 40f))
                val info = ui.column().apply { setPadding(ui.px(10f), 0, ui.px(6f), 0) }
                info.addView(ui.text(ball.label, 20f))
                val recipe = ui.row()
                recipe.addView(ui.icon(SkillArt.energyFragment(), SkillArt.ITEM, SkillArt.ITEM, 18f))
                recipe.addView(ui.text(" 1  + ", 15f, ui.inkSoft))
                recipe.addView(ui.icon(SkillArt.berry(berry), SkillArt.ITEM, SkillArt.ITEM, 18f))
                recipe.addView(ui.text(" 1 (${owned[berry.berryItemId] ?: 0})", 15f, ui.inkSoft))
                info.addView(recipe)
                info.addView(ui.text("+${state.gain(Skill.CRAFTING, Crafting.baseXp(ball).toDouble())} XP Výroba", 15f, ui.inkSoft))
                c.addView(info, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val buttons = ui.column()
                buttons.addView(ui.button("Vyrobit", max > 0) { close(); onCraft(ball, 1) })
                if (max > 1) buttons.addView(ui.button("Vše ×$max") { close(); onCraft(ball, max) }.apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { topMargin = ui.px(6f) }
                })
                c.addView(buttons)
                body.addView(c)
            }
            // Vybavení – odemyká strom Výroby, recepty přibudou
            val gear = card(ui).apply { alpha = 0.75f }
            gear.addView(ui.icon(SkillArt.skillIcon(Skill.CRAFTING), SkillArt.ICON, SkillArt.ICON, 40f))
            gear.addView(ui.text(
                if (state.basicEquipment) "Základní vybavení – recepty už brzy!"
                else "🔒 Základní vybavení – odemkni ve stromu Výroby (deník → Postava)",
                16f, ui.inkSoft).apply { setPadding(ui.px(10f), 0, 0, 0) }, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(gear)
            body.addView(ui.text("Fragmenty energie padají z Makromonů, bobule sklidíš na záhonech vpravo pod mostem.", 15f, ui.inkSoft))
        }
    }
}

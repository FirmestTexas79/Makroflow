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
    fun show(root: FrameLayout, title: String, subtitle: String?, build: (WoodUi, LinearLayout, () -> Unit) -> Unit) {
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

    fun card(ui: WoodUi): LinearLayout = ui.row().apply {
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

    // ── Těžba a kácení (docs/adr/0035) ──────────────────────────────────────

    /** Údaje pro dřevěnou ceduli u stromu / žíly. */
    data class GatherInfo(
        val spot: cz.uhk.macroflow.pokemon.skills.GatherSpot,
        val tool: cz.uhk.macroflow.pokemon.skills.Gear?,
        val efficiency: Int,
        val secPerUnit: Long?,
        val xpPerUnit: Int,
        val multi: Double,
        val capHours: Int,
        /** Probíhající činnost (kdekoli), null = nic. */
        val active: cz.uhk.macroflow.pokemon.skills.Gathering.Activity?,
        val pendingHere: Int
    )

    fun gatherMenu(root: FrameLayout, info: GatherInfo, onStart: () -> Unit, onCollect: () -> Unit, onStop: () -> Unit) {
        val spot = info.spot
        val activeHere = info.active?.spot == spot
        val (px, w, h) = cz.uhk.macroflow.pokemon.skills.GearArt.spot(spot)
        show(root, spot.label, if (activeHere) "Právě tu ${if (spot.skill == Skill.MINING) "těžíš" else "kácíš"} – pokračuje i když odejdeš z Makrosvěta." else null) { ui, body, close ->
            val top = ui.row()
            top.addView(ui.icon(px, w, h, 56f))
            val col = ui.column().apply { setPadding(ui.px(12f), 0, 0, 0) }
            col.addView(ui.row().apply {
                addView(ui.icon(cz.uhk.macroflow.pokemon.skills.SkillArt.resourceIcon(spot.resource), cz.uhk.macroflow.pokemon.skills.SkillArt.ITEM, cz.uhk.macroflow.pokemon.skills.SkillArt.ITEM, 22f))
                addView(ui.text("  ${spot.resource.label}", 19f))
            })
            fun line(label: String, value: String, color: Int = ui.ink) {
                val r = ui.row().apply { setPadding(0, ui.px(2f), 0, 0) }
                r.addView(ui.text(label, 16f, ui.inkSoft).apply { layoutParams = ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
                r.addView(ui.text(value, 17f, color))
                col.addView(r)
            }
            line("Potřebná efektivita", "${spot.required}")
            line("Tvoje efektivita", if (info.tool == null) "–" else "${info.efficiency}",
                if (info.tool != null && info.efficiency >= spot.required) ui.olive else ui.rust)
            line("1 kus za", info.secPerUnit?.let { Garden.clock(it) } ?: "–")
            line("XP za kus", "+${info.xpPerUnit} ${spot.skill.label}")
            line("Dvojitý kus", "${(info.multi * 100).toInt()} %")
            line("AFK nejvýš", "${info.capHours} h")
            top.addView(col, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(top)
            body.addView(ui.spacer(10f))
            when {
                info.tool == null -> body.addView(ui.text("🔒 Potřebuješ ${spot.toolSlot.label.lowercase()} – nasaď ji v deníku (Postava → TOOLS).", 16f, ui.rust))
                info.secPerUnit == null -> body.addView(ui.text("Na tohle je tvoje efektivita malá. Zvyš level ${spot.skill.label}, odemkni bonus ve stromu nebo sežeň lepší nástroj.", 16f, ui.rust))
                activeHere -> {
                    body.addView(ui.text("Hotovo k vybrání: ${info.pendingHere} ks", 18f))
                    val r = ui.row().apply { setPadding(0, ui.px(8f), 0, 0) }
                    r.addView(ui.button("Vybrat", info.pendingHere > 0) { close(); onCollect() })
                    r.addView(ui.spacer(1f).apply { layoutParams = LinearLayout.LayoutParams(ui.px(10f), 1) })
                    r.addView(ui.button("Přestat") { close(); onStop() })
                    body.addView(r)
                }
                else -> {
                    info.active?.let { a ->
                        body.addView(ui.text("Teď ${if (a.spot.skill == Skill.MINING) "těžíš" else "kácíš"}: ${a.spot.label}. Začátkem tady to ukončíš a hotové kusy se vyberou.", 15f, ui.inkSoft))
                        body.addView(ui.spacer(6f))
                    }
                    body.addView(ui.button("${spot.verb} (1 kus za ${Garden.clock(info.secPerUnit)})") { close(); onStart() })
                }
            }
        }
    }

    /** Návrat do Makrosvěta: jak dlouho jsi byl pryč a co se za tu dobu vytěžilo. */
    fun afkReport(root: FrameLayout, awaySec: Long, res: cz.uhk.macroflow.pokemon.skills.SkillStore.GatherResult, onClose: () -> Unit = {}) {
        val spot = res.spot
        show(root, "Vítej zpět!", "Byl jsi pryč ${cz.uhk.macroflow.pokemon.skills.Gathering.awayText(awaySec)}.") { ui, body, close ->
            val c = card(ui)
            c.addView(ui.icon(cz.uhk.macroflow.pokemon.skills.SkillArt.resourceIcon(spot.resource), cz.uhk.macroflow.pokemon.skills.SkillArt.ITEM, cz.uhk.macroflow.pokemon.skills.SkillArt.ITEM, 44f))
            val col = ui.column().apply { setPadding(ui.px(12f), 0, 0, 0) }
            col.addView(ui.text("${spot.label}: ${res.claim.amount}× ${spot.resource.label}", 20f))
            if (res.claim.amount > res.claim.units) col.addView(ui.text("z toho ${res.claim.amount - res.claim.units}× navíc (dvojitý kus)", 15f, ui.olive))
            res.xp?.let { x ->
                col.addView(ui.text("+${x.gained} XP ${spot.skill.label}", 17f, ui.inkSoft))
                if (x.leveledUp) col.addView(ui.text("⭐ ${spot.skill.label} Lv ${x.newLevel}!" + (if (x.newPoints > 0) " Nový dovednostní bod." else ""), 16f, ui.rust))
            }
            if (res.claim.units == 0) col.addView(ui.text("Zatím nic hotového – další kus už se dělá.", 15f, ui.inkSoft))
            c.addView(col, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(c)
            if (res.claim.capped) body.addView(ui.text("Počítá se nejvýš ${res.claim.countedSeconds / 3600} h AFK – delší směnu odemkneš ve stromu.", 15f, ui.inkSoft))
            body.addView(ui.text("${if (spot.skill == Skill.MINING) "Těžba" else "Kácení"} pokračuje dál.", 15f, ui.inkSoft))
            body.addView(ui.button("Pokračovat") { close(); onClose() }.apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(10f) }
            })
        }
    }
}

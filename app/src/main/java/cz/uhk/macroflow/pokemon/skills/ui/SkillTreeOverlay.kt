package cz.uhk.macroflow.pokemon.skills.ui

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import cz.uhk.macroflow.pokemon.skills.Skill
import cz.uhk.macroflow.pokemon.skills.SkillArt
import cz.uhk.macroflow.pokemon.skills.SkillMath
import cz.uhk.macroflow.pokemon.skills.SkillState
import cz.uhk.macroflow.pokemon.skills.SkillTree
import cz.uhk.macroflow.pokemon.skills.TreeArt

/**
 * Okno stromu dovedností (docs/adr/0058). Vyjede zespodu přes deník: dřevěný rám, nahoře
 * záložky dovedností s ikonami, uprostřed tmavá deska se stromem (SkillTreeView), dole
 * pergamenová karta vybraného uzlu s tlačítkem Odemknout.
 */
object SkillTreeOverlay {

    private val WHITE = Color.parseColor("#FEFAE0")
    private val GOLD = Color.parseColor("#FFD54F")

    /**
     * [unlock] odemkne uzel a zavolá zpět nový stav (null = nepovedlo se),
     * [onSkill] dá vědět, kterou dovednost hráč právě prohlíží.
     */
    fun show(
        root: FrameLayout,
        initial: Skill,
        initialState: SkillState,
        unlock: (SkillTree.Node, (SkillState?) -> Unit) -> Unit,
        onSkill: (Skill) -> Unit = {}
    ) {
        WorkshopMenus.close(root)
        val ui = WoodUi(root.context)
        val u = 2f * ui.dp
        var state = initialState
        var skill = initial

        val dim = FrameLayout(root.context).apply {
            tag = WorkshopMenus.TAG
            setBackgroundColor(Color.parseColor("#B3080604"))
            isClickable = true
            elevation = 150f
        }
        lateinit var panel: LinearLayout
        val close = {
            panel.animate().translationY(ui.px(60f).toFloat()).alpha(0f).setDuration(160)
                .withEndAction { (dim.parent as? ViewGroup)?.removeView(dim) }.start()
            dim.animate().alpha(0f).setDuration(180).start()
            Unit
        }
        dim.setOnClickListener { close() }

        panel = ui.column().apply {
            background = WoodPanelDrawable(3f * ui.dp, parchment = false)
            isClickable = true
        }

        // hlavička
        val head = ui.row()
        head.addView(ui.icon(TreeArt.TREE, TreeArt.SIZE, TreeArt.SIZE, 30f))
        head.addView(outlined(ui.text("Strom dovedností", 27f, WHITE)).apply {
            setPadding(ui.px(8f), 0, 0, ui.px(2f))
        }, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(outlined(ui.text("✕", 26f, WHITE)).apply {
            setPadding(ui.px(12f), 0, ui.px(4f), ui.px(4f)); setOnClickListener { close() }
        })
        panel.addView(head)

        // záložky dovedností
        val tabs = ui.row().apply { gravity = Gravity.CENTER; setPadding(0, ui.px(8f), 0, ui.px(6f)) }
        panel.addView(tabs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val info = outlined(ui.text("", 17f, Color.parseColor("#E8D8C8"), Gravity.CENTER))
        panel.addView(info, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = ui.px(6f) })

        // deska se stromem
        val board = FrameLayout(root.context).apply { background = TreeBackdropDrawable(u) }
        val tree = SkillTreeView(root.context)
        val scroll = ScrollView(root.context).apply {
            isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; isFillViewport = true
            addView(tree, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        board.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            .apply { setMargins(ui.px(4f), ui.px(4f), ui.px(4f), ui.px(4f)) })
        // tmavý rámeček desky
        val boardFrame = FrameLayout(root.context).apply {
            background = BevelDrawable(u, Color.parseColor("#0B0805"), Color.parseColor("#2E1B0E"), Color.parseColor("#4F3016"), Color.parseColor("#1E140C"))
            setPadding(ui.px(3f), ui.px(3f), ui.px(3f), ui.px(3f))
            addView(board)
        }
        val boardH = (root.height * 0.50f).toInt().coerceAtLeast(ui.px(300f))
        panel.addView(boardFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, boardH))

        // karta vybraného uzlu
        val card = ui.column().apply { background = WoodPanelDrawable(2f * ui.dp) }
        panel.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = ui.px(8f) })

        lateinit var renderTabs: () -> Unit
        fun renderCard() {
            card.removeAllViews()
            val n = SkillTree.of(skill).firstOrNull { it.id == tree.selectedId } ?: return
            val st = state.status(n)
            val top = ui.row()
            top.addView(ui.text(n.title, 23f).apply { layoutParams = ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            top.addView(ui.text(if (n.cost == 1) "1 bod" else "${n.cost} body", 17f, ui.rust))
            card.addView(top)
            card.addView(ui.text(n.description, 16f, ui.inkSoft).apply { setPadding(0, ui.px(3f), 0, ui.px(6f)) })
            val prog = state.progress(skill)
            when (st) {
                SkillState.NodeStatus.UNLOCKED -> card.addView(ui.text("✓ Odemčeno", 18f, ui.olive))
                SkillState.NodeStatus.AVAILABLE -> card.addView(ui.button("Odemknout za ${n.cost} ${if (n.cost == 1) "bod" else "body"}") {
                    unlock(n) { newState ->
                        if (newState != null) {
                            state = newState
                            tree.updateState(newState)
                            tree.celebrate(n.id)
                            renderTabs()
                        }
                        renderCard()
                    }
                }.apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) })
                SkillState.NodeStatus.NO_POINTS -> card.addView(ui.text("Chybí body – další přijde na Lv ${SkillMath.nextSkillPointLevel(prog.level)}.", 16f, ui.rust))
                SkillState.NodeStatus.LOCKED -> card.addView(ui.text("🔒 Nejdřív odemkni: ${SkillTree.node(n.requires!!)?.title}", 16f, ui.inkSoft))
            }
        }
        tree.onSelect = { renderCard() }

        // záložky a řádek s body se překreslují po odemčení i po přepnutí dovednosti
        lateinit var selectSkill: (Skill) -> Unit
        fun renderTabsAndInfoImpl() {
            tabs.removeAllViews()
            Skill.entries.forEach { s ->
                val f = FrameLayout(root.context).apply {
                    background = BevelDrawable.navy(u, selected = s == skill)
                    contentDescription = s.label
                    setOnClickListener { if (s != skill) selectSkill(s) }
                }
                f.addView(ui.icon(SkillArt.skillIcon(s), SkillArt.ICON, SkillArt.ICON, 30f),
                    FrameLayout.LayoutParams(ui.px(32f), ui.px(32f), Gravity.CENTER))
                val pts = state.availablePoints(s)
                if (pts > 0) f.addView(outlined(ui.text("+$pts", 14f, GOLD)).apply { setPadding(0, ui.px(1f), ui.px(4f), 0) },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.TOP))
                tabs.addView(f, LinearLayout.LayoutParams(0, ui.px(48f), 1f).apply { marginStart = ui.px(2f); marginEnd = ui.px(2f) })
            }
            val pts = state.availablePoints(skill)
            info.text = "${skill.label} · Lv ${state.level(skill)} · " + when (pts) {
                0 -> "žádné volné body"; 1 -> "1 volný bod"; in 2..4 -> "$pts volné body"; else -> "$pts volných bodů"
            }
            info.setTextColor(if (pts > 0) GOLD else Color.parseColor("#E8D8C8"))
        }
        renderTabs = ::renderTabsAndInfoImpl
        selectSkill = { s ->
            skill = s; onSkill(s)
            tree.selectedId = null
            tree.bind(SkillTree.of(s), state, animate = true)
            scroll.scrollTo(0, 0)
            renderTabsAndInfoImpl(); renderCard()
        }

        tree.bind(SkillTree.of(skill), state, animate = true)
        renderTabsAndInfoImpl()
        renderCard()

        val w = minOf(root.width - ui.px(20f), ui.px(420f))
        dim.addView(panel, FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(dim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        dim.alpha = 0f
        dim.animate().alpha(1f).setDuration(180).start()
        panel.translationY = ui.px(120f).toFloat(); panel.alpha = 0f
        panel.animate().translationY(0f).alpha(1f).setInterpolator(DecelerateInterpolator(2f)).setDuration(280).start()
    }

    private fun outlined(t: TextView): TextView = t.apply { setShadowLayer(3f, 0f, 1.5f, Color.parseColor("#101828")) }
}

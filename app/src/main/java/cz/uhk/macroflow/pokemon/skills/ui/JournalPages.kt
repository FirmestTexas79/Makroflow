package cz.uhk.macroflow.pokemon.skills.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.balls.Makroball
import cz.uhk.macroflow.pokemon.skills.Resource
import cz.uhk.macroflow.pokemon.skills.Skill
import cz.uhk.macroflow.pokemon.skills.SkillArt
import cz.uhk.macroflow.pokemon.skills.SkillMath
import cz.uhk.macroflow.pokemon.skills.SkillState
import cz.uhk.macroflow.pokemon.skills.SkillTree
import cz.uhk.macroflow.pokemon.skills.Team
import java.util.Locale

/**
 * Stránky deníku (docs/adr/0034) po vzoru Legends of IdleOn:
 * **Postava** – tři dovednosti pod sebou, po klepnutí rozpis (XP, pasivní bonus, násobitel XP,
 * body a strom dovedností); **Suroviny** – sklad v políčkách s počty.
 */
object JournalPages {

    private val WHITE = Color.parseColor("#FEFAE0")
    private val GOLD = Color.parseColor("#FFD54F")

    // ── Postava ─────────────────────────────────────────────────────────────

    fun character(
        container: FrameLayout,
        state: SkillState,
        teamSize: Int,
        selected: Skill,
        onSelect: (Skill) -> Unit,
        onUnlock: (SkillTree.Node) -> Unit
    ) {
        container.removeAllViews()
        val ui = WoodUi(container.context)
        val u = 2f * ui.dp
        val root = ui.column().apply { setPadding(ui.px(14f), ui.px(14f), ui.px(14f), ui.px(10f)) }

        // Hlavička: portrét, jméno, celkový level
        val head = ui.row().apply {
            background = BevelDrawable.navy(u)
            setPadding(ui.px(10f), ui.px(8f), ui.px(10f), ui.px(8f))
        }
        head.addView(ImageView(container.context).apply {
            portrait(container)?.let { setImageDrawable(BitmapDrawable(resources, it).apply { isFilterBitmap = false }) }
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = BevelDrawable(u, Color.parseColor("#2E4677"), Color.parseColor("#4B6BA3"), Color.parseColor("#1B2A4A"))
            setPadding(ui.px(6f), ui.px(4f), ui.px(6f), ui.px(4f))
        }, LinearLayout.LayoutParams(ui.px(52f), ui.px(60f)))
        val names = ui.column().apply { setPadding(ui.px(12f), 0, 0, 0) }
        names.addView(outlined(ui.text("Trenér", 24f, WHITE)))
        val total = Skill.entries.sumOf { state.level(it) }
        names.addView(outlined(ui.text("Celkový level $total", 17f, GOLD)))
        names.addView(ui.text("Tým ${teamSize.coerceAtLeast(0)}/${state.teamSlots} (max ${Team.MAX})", 15f, Color.parseColor("#C9D6F0")))
        head.addView(names, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val body = ui.row().apply { gravity = Gravity.TOP }
        // Levý sloupec: tři dovednosti pod sebou
        val tiles = ui.column().apply { setPadding(0, ui.px(10f), ui.px(10f), 0) }
        Skill.entries.forEach { s ->
            val tile = FrameLayout(container.context).apply {
                background = BevelDrawable.navy(u, selected = s == selected)
                setOnClickListener { onSelect(s) }
                contentDescription = "${s.label}, level ${state.level(s)}"
            }
            val col = ui.column().apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, ui.px(6f), 0, ui.px(4f)) }
            col.addView(ui.icon(SkillArt.skillIcon(s), SkillArt.ICON, SkillArt.ICON, 40f))
            col.addView(outlined(ui.text("LV ${state.level(s)}", 17f, WHITE, Gravity.CENTER)))
            tile.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            if (state.availablePoints(s) > 0) {
                tile.addView(outlined(ui.text("+${state.availablePoints(s)}", 15f, GOLD)).apply {
                    setPadding(0, ui.px(3f), ui.px(6f), 0)
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.TOP))
            }
            tiles.addView(tile, LinearLayout.LayoutParams(ui.px(76f), ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ui.px(8f) })
        }
        body.addView(tiles)

        // Pravý sloupec: rozpis vybrané dovednosti
        val detail = ui.column().apply { setPadding(0, ui.px(10f), 0, ui.px(16f)) }
        skillDetail(ui, detail, state, selected, onUnlock)
        val scroll = ScrollView(container.context).apply {
            isVerticalScrollBarEnabled = false; isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(ui.px(14f))
            addView(detail)
        }
        body.addView(scroll, ui.lp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        container.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun skillDetail(ui: WoodUi, box: LinearLayout, state: SkillState, s: Skill, onUnlock: (SkillTree.Node) -> Unit) {
        val prog = state.progress(s)
        val titleRow = ui.row()
        titleRow.addView(ui.text(s.label, 26f).apply { layoutParams = ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        titleRow.addView(ui.text("Lv ${prog.level}", 24f, ui.rust))
        box.addView(titleRow)

        // XP pruh
        val bar = FrameLayout(box.context).apply { background = BevelDrawable(1.5f * ui.dp, Color.parseColor("#2A1C11"), Color.parseColor("#1A110A"), Color.parseColor("#4F3016")) }
        val fill = View(box.context).apply { setBackgroundColor(Color.parseColor("#7F9148")) }
        bar.addView(fill, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(ui.px(3f), ui.px(3f), ui.px(3f), ui.px(3f)) })
        val xpText = if (prog.xpNeeded > 0) "${prog.xpInLevel} / ${prog.xpNeeded} XP" else "MAX"
        bar.addView(outlined(ui.text(xpText, 15f, WHITE, Gravity.CENTER)), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        box.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.px(24f)).apply { topMargin = ui.px(6f); bottomMargin = ui.px(8f) })
        bar.post {
            fill.layoutParams = (fill.layoutParams as FrameLayout.LayoutParams).apply { width = ((bar.width - ui.px(6f)) * prog.fraction).toInt() }
        }

        fun stat(label: String, value: String) {
            val r = ui.row().apply { setPadding(0, ui.px(2f), 0, ui.px(2f)) }
            r.addView(ui.text(label, 16f, ui.inkSoft).apply { layoutParams = ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            r.addView(ui.text(value, 17f, ui.ink))
            box.addView(r)
        }
        val passive = state.passive(s)
        fun pct(v: Double) = String.format(Locale.US, "%.0f %%", v * 100)
        when (s) {
            Skill.CATCHING -> {
                stat("Šance na útěk z ballu", "−${pct(passive)}")
                stat("Míst v týmu", "${state.teamSlots} / ${Team.MAX}")
            }
            Skill.CRAFTING -> stat("Šance na dvojitou výrobu", pct(passive))
            Skill.HARVESTING -> {
                stat("Šance na dvojitou sklizeň", pct(passive))
                stat("Otevřené záhony", "${state.plotsOpen} / 4")
                if (state.growthSpeedup > 0) stat("Rychlejší růst", pct(state.growthSpeedup))
            }
        }
        stat("XP multiplikátor", "×" + String.format(Locale("cs"), "%.2f", state.xpMultiplier(s)))
        stat("Dovednostní body", "${state.availablePoints(s)} volné")
        stat("Další bod na", "Lv ${SkillMath.nextSkillPointLevel(prog.level)}")
        box.addView(ui.text("XP získáváš ${s.verb}. Každý level přidá +1 % k pasivnímu bonusu.", 15f, ui.inkSoft).apply {
            setPadding(0, ui.px(6f), 0, ui.px(10f))
        })

        box.addView(ui.text("Strom dovedností", 22f, ui.rust))
        SkillTree.of(s).forEach { n ->
            val st = state.status(n)
            val card = ui.column().apply {
                setPadding(ui.px(10f), ui.px(8f), ui.px(10f), ui.px(8f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 4 * ui.dp
                    setColor(if (st == SkillState.NodeStatus.UNLOCKED) Color.parseColor("#26606C38") else Color.parseColor("#1FBC6C25"))
                    setStroke((1.5f * ui.dp).toInt(), if (st == SkillState.NodeStatus.UNLOCKED) Color.parseColor("#606C38") else Color.parseColor("#55BC6C25"))
                }
                alpha = if (st == SkillState.NodeStatus.LOCKED) 0.55f else 1f
            }
            val top = ui.row()
            top.addView(ui.text(n.title, 19f).apply { layoutParams = ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            top.addView(ui.text(if (n.cost == 1) "1 bod" else "${n.cost} body", 16f, ui.inkSoft))
            card.addView(top)
            card.addView(ui.text(n.description, 15f, ui.inkSoft))
            when (st) {
                SkillState.NodeStatus.UNLOCKED -> card.addView(ui.text("✓ Odemčeno", 16f, ui.olive))
                SkillState.NodeStatus.AVAILABLE -> card.addView(ui.button("Odemknout") { onUnlock(n) }.apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { topMargin = ui.px(6f) }
                })
                SkillState.NodeStatus.NO_POINTS -> card.addView(ui.text("Chybí body – další na Lv ${SkillMath.nextSkillPointLevel(prog.level)}", 15f, ui.rust))
                SkillState.NodeStatus.LOCKED -> card.addView(ui.text("🔒 Nejdřív: ${SkillTree.node(n.requires!!)?.title}", 15f, ui.inkSoft))
            }
            box.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(8f) })
        }
    }

    /** Bílý text s tmavým obrysem jako v IdleOn. */
    private fun outlined(t: TextView): TextView = t.apply { setShadowLayer(3f, 0f, 1.5f, Color.parseColor("#101828")) }

    /** Trenér z mapy (sprite postavy bez oranžového pozadí). */
    private fun portrait(v: View): Bitmap? {
        val d = ContextCompat.getDrawable(v.context, R.drawable.ash_down_idle) as? BitmapDrawable ?: return null
        val src = d.bitmap
        val px = IntArray(src.width * src.height)
        src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
        val key = Color.parseColor("#FF7F27")
        for (i in px.indices) if (px[i] == key) px[i] = Color.TRANSPARENT
        return Bitmap.createBitmap(px, src.width, src.height, Bitmap.Config.ARGB_8888)
    }

    // ── Suroviny ────────────────────────────────────────────────────────────

    private data class Entry(val id: String, val label: String, val description: String, val pixels: IntArray, val size: Int)

    fun resources(container: LinearLayout, counts: Map<String, Int>) {
        container.removeAllViews()
        val ui = WoodUi(container.context)
        container.addView(ui.text("Suroviny", 27f, container.context.getColor(R.color.journal_chapter_title_ink), Gravity.CENTER).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        container.addView(ui.text("Klepni na políčko pro popis.", 15f, ui.inkSoft, Gravity.CENTER).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = ui.px(8f) }
        })
        val info = ui.text("", 16f, ui.ink).apply {
            setPadding(ui.px(10f), ui.px(8f), ui.px(10f), ui.px(8f))
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 4 * ui.dp; setColor(Color.parseColor("#26BC6C25")) }
            visibility = View.GONE
        }

        fun res(r: Resource) = Entry(r.itemId, r.label, r.description, SkillArt.resourceIcon(r), SkillArt.ITEM)
        val sections = listOf(
            "Z Makromonů" to listOf(res(Resource.ENERGY)),
            "Ze záhonů" to listOf(res(Resource.BERRY_GREEN), res(Resource.BERRY_BLUE), res(Resource.BERRY_BLACK)),
            "Semínka" to listOf(res(Resource.SEED_GREEN), res(Resource.SEED_BLUE), res(Resource.SEED_BLACK)),
            "Vyrobené" to Makroball.entries.map { Entry(it.id, it.label, it.description, it.pixels, Makroball.SIZE) }
        )
        sections.forEach { (title, entries) ->
            container.addView(ui.text(title, 20f, ui.rust).apply { setPadding(ui.px(2f), ui.px(6f), 0, ui.px(4f)) })
            entries.chunked(4).forEach { chunk ->
                val row = ui.row()
                chunk.forEach { e -> row.addView(slot(ui, e, counts[e.id] ?: 0, info)) }
                container.addView(row)
            }
        }
        container.addView(info, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(10f) })
    }

    private fun slot(ui: WoodUi, e: Entry, n: Int, info: TextView): View {
        val f = FrameLayout(ui.ctx).apply {
            background = BevelDrawable.slot(2f * ui.dp)
            alpha = if (n > 0) 1f else 0.45f
            contentDescription = "${e.label}: $n"
            setOnClickListener {
                info.visibility = View.VISIBLE
                info.text = "${e.label} – máš $n\n${e.description}"
            }
        }
        f.addView(ui.icon(e.pixels, e.size, e.size, 40f), FrameLayout.LayoutParams(ui.px(40f), ui.px(40f), Gravity.CENTER))
        f.addView(outlined(ui.text(if (n > 999) "999+" else "$n", 16f, WHITE)).apply { setPadding(0, 0, ui.px(5f), ui.px(2f)) },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.BOTTOM))
        f.layoutParams = LinearLayout.LayoutParams(ui.px(62f), ui.px(62f)).apply { marginEnd = ui.px(8f); bottomMargin = ui.px(8f) }
        return f
    }
}

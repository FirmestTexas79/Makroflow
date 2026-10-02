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
import cz.uhk.macroflow.pokemon.skills.Award
import cz.uhk.macroflow.pokemon.skills.AwardArt
import cz.uhk.macroflow.pokemon.skills.AwardCategory
import cz.uhk.macroflow.pokemon.skills.AwardFacts
import cz.uhk.macroflow.pokemon.skills.Awards
import cz.uhk.macroflow.pokemon.skills.Gear
import cz.uhk.macroflow.pokemon.skills.GearArt
import cz.uhk.macroflow.pokemon.skills.GearSlot
import cz.uhk.macroflow.pokemon.skills.GearTab
import cz.uhk.macroflow.pokemon.skills.ItemInfo
import cz.uhk.macroflow.pokemon.skills.Resource
import cz.uhk.macroflow.pokemon.skills.Skill
import cz.uhk.macroflow.pokemon.skills.SkillArt
import cz.uhk.macroflow.pokemon.skills.SkillMath
import cz.uhk.macroflow.pokemon.skills.SkillState
import cz.uhk.macroflow.pokemon.skills.Team
import cz.uhk.macroflow.pokemon.skills.TreeArt
import java.util.Locale

/**
 * Stránky deníku (docs/adr/0034) po vzoru Legends of IdleOn:
 * **Postava** – tři dovednosti pod sebou, po klepnutí rozpis (XP, pasivní bonus, násobitel XP,
 * body; strom dovedností má vlastní okno – docs/adr/0058); **Suroviny** – sklad v políčkách s počty.
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
        equipped: Map<GearSlot, Gear>,
        gearTab: GearTab,
        onSelect: (Skill) -> Unit,
        onTree: () -> Unit,
        onGearTab: (GearTab) -> Unit,
        onSlot: (GearSlot) -> Unit
    ) {
        // posun stránky zůstane i po překreslení (odemknutí uzlu, výběr dovednosti)
        val oldScroll = (container.getChildAt(0) as? ScrollView)?.scrollY ?: 0
        container.removeAllViews()
        val ui = WoodUi(container.context)
        val u = 2f * ui.dp
        val root = ui.column().apply { setPadding(ui.px(14f), ui.px(14f), ui.px(14f), ui.px(18f)) }

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
        // vpravo nahoře je v knize zavírací křížek – hlavička mu uhne
        root.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { marginEnd = ui.px(40f) })

        // Vybavení (docs/adr/0035): záložky EQUIPS / ACCESS / TOOLS a jeden sloupec čtyř slotů
        root.addView(equipment(ui, u, equipped, gearTab, onGearTab, onSlot),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(10f) })

        val body = ui.row().apply { gravity = Gravity.TOP }
        // Levý sloupec: dovednosti pod sebou
        val tiles = ui.column().apply { setPadding(0, ui.px(10f), ui.px(10f), 0) }
        Skill.entries.forEach { s ->
            val tile = FrameLayout(container.context).apply {
                background = BevelDrawable.navy(u, selected = s == selected)
                setOnClickListener { onSelect(s) }
                contentDescription = "${s.label}, level ${state.level(s)}"
            }
            val col = ui.column().apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, ui.px(6f), 0, ui.px(4f)) }
            col.addView(ui.icon(SkillArt.skillIcon(s), SkillArt.ICON, SkillArt.ICON, 34f))
            col.addView(outlined(ui.text("LV ${state.level(s)}", 16f, WHITE, Gravity.CENTER)))
            tile.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            if (state.availablePoints(s) > 0) {
                tile.addView(outlined(ui.text("+${state.availablePoints(s)}", 15f, GOLD)).apply {
                    setPadding(0, ui.px(3f), ui.px(6f), 0)
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.TOP))
            }
            tiles.addView(tile, LinearLayout.LayoutParams(ui.px(70f), ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ui.px(8f) })
        }
        body.addView(tiles)

        // Pravý sloupec: rozpis vybrané dovednosti
        val detail = ui.column().apply { setPadding(0, ui.px(10f), 0, ui.px(6f)) }
        skillDetail(ui, detail, state, selected, onTree)
        body.addView(detail, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(container.context).apply {
            isVerticalScrollBarEnabled = false; isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(ui.px(14f))
            addView(root)
        }
        container.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (oldScroll > 0) scroll.post { scroll.scrollTo(0, oldScroll) }
    }

    /** Panel vybavení ve stylu IdleOn: hnědý rám, tři záložky, čtyři sloty. */
    private fun equipment(ui: WoodUi, u: Float, equipped: Map<GearSlot, Gear>, tab: GearTab,
                          onTab: (GearTab) -> Unit, onSlot: (GearSlot) -> Unit): View {
        val panel = ui.column().apply {
            background = BevelDrawable(u, Color.parseColor("#5A3E32"), Color.parseColor("#7A5646"), Color.parseColor("#3B281F"), Color.parseColor("#1E140C"))
            setPadding(ui.px(8f), ui.px(8f), ui.px(8f), ui.px(8f))
        }
        val tabs = ui.row().apply { gravity = Gravity.CENTER }
        GearTab.entries.forEach { t ->
            tabs.addView(outlined(ui.text(t.label, 16f, if (t == tab) WHITE else Color.parseColor("#C9B8A8"), Gravity.CENTER)).apply {
                background = BevelDrawable(1.5f * ui.dp,
                    if (t == tab) Color.parseColor("#8A6450") else Color.parseColor("#4A3328"),
                    if (t == tab) Color.parseColor("#B08A74") else Color.parseColor("#5E4236"),
                    Color.parseColor("#2E1E16"), Color.parseColor("#1E140C"), selected = t == tab)
                setPadding(ui.px(8f), ui.px(5f), ui.px(8f), ui.px(6f))
                setOnClickListener { if (t != tab) onTab(t) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ui.px(3f); marginEnd = ui.px(3f) })
        }
        panel.addView(tabs)
        val slots = ui.row().apply { gravity = Gravity.CENTER; setPadding(0, ui.px(8f), 0, 0) }
        GearSlot.of(tab).forEach { slot ->
            val g = equipped[slot]
            val f = FrameLayout(ui.ctx).apply {
                background = BevelDrawable(2f * ui.dp, Color.parseColor("#B39384"), Color.parseColor("#CDB2A4"), Color.parseColor("#8A6F62"), Color.parseColor("#2E1E16"))
                contentDescription = "${slot.label}: ${g?.label ?: "prázdné"}"
                alpha = if (slot.locked) 0.6f else 1f
                setOnClickListener { onSlot(slot) }
            }
            val pix = g?.let { GearArt.gearIcon(it) } ?: GearArt.ghost(slot)
            f.addView(ui.icon(pix, GearArt.ICON, GearArt.ICON, 44f), FrameLayout.LayoutParams(ui.px(44f), ui.px(44f), Gravity.CENTER))
            slots.addView(f, LinearLayout.LayoutParams(ui.px(62f), ui.px(62f)).apply { marginStart = ui.px(4f); marginEnd = ui.px(4f) })
        }
        panel.addView(slots)
        val names = GearSlot.of(tab).joinToString("  ·  ") { sl -> equipped[sl]?.label ?: sl.label }
        panel.addView(ui.text(names, 14f, Color.parseColor("#E8D8C8"), Gravity.CENTER).apply {
            setPadding(0, ui.px(6f), 0, 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        return panel
    }

    /**
     * Karta vybrané dovednosti (docs/adr/0058): pergamen v dřevěném rámu, hlavička s ikonou
     * a levelem, XP pruh, bonusy v políčkách a tlačítko, které otevře grafický strom.
     */
    private fun skillDetail(ui: WoodUi, box: LinearLayout, state: SkillState, s: Skill, onTree: () -> Unit) {
        val u = 2f * ui.dp
        val prog = state.progress(s)
        val card = ui.column().apply {
            background = WoodPanelDrawable(2f * ui.dp)
            setPadding(ui.px(14f), ui.px(14f), ui.px(14f), ui.px(14f))
        }
        box.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // hlavička: ikona v modré dlaždici, název, štítek s levelem
        val titleRow = ui.row()
        titleRow.addView(FrameLayout(box.context).apply {
            background = BevelDrawable.navy(u)
            addView(ui.icon(SkillArt.skillIcon(s), SkillArt.ICON, SkillArt.ICON, 26f),
                FrameLayout.LayoutParams(ui.px(28f), ui.px(28f), Gravity.CENTER))
        }, LinearLayout.LayoutParams(ui.px(38f), ui.px(38f)))
        titleRow.addView(ui.text(s.label, 23f).apply {
            setPadding(ui.px(8f), 0, ui.px(4f), 0)
            layoutParams = ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        titleRow.addView(outlined(ui.text("Lv ${prog.level}", 18f, WHITE, Gravity.CENTER)).apply {
            background = BevelDrawable(1.5f * ui.dp, Color.parseColor("#BC6C25"), Color.parseColor("#E09A50"), Color.parseColor("#7A4416"), Color.parseColor("#3B2A1A"))
            setPadding(ui.px(8f), ui.px(3f), ui.px(8f), ui.px(5f))
        })
        card.addView(titleRow)

        // XP pruh
        val bar = FrameLayout(box.context).apply { background = BevelDrawable(1.5f * ui.dp, Color.parseColor("#2A1C11"), Color.parseColor("#1A110A"), Color.parseColor("#4F3016")) }
        val fill = View(box.context).apply { setBackgroundColor(Color.parseColor("#7F9148")) }
        val shine = View(box.context).apply { setBackgroundColor(Color.parseColor("#A9BC6A")) }
        bar.addView(fill, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(ui.px(3f), ui.px(3f), ui.px(3f), ui.px(3f)) })
        bar.addView(shine, FrameLayout.LayoutParams(0, ui.px(3f)).apply { setMargins(ui.px(3f), ui.px(3f), ui.px(3f), 0) })
        val xpText = if (prog.xpNeeded > 0) "${prog.xpInLevel} / ${prog.xpNeeded} XP" else "MAX"
        bar.addView(outlined(ui.text(xpText, 15f, WHITE, Gravity.CENTER)), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        card.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.px(26f)).apply { topMargin = ui.px(10f); bottomMargin = ui.px(10f) })
        bar.post {
            val w = ((bar.width - ui.px(6f)) * prog.fraction).toInt()
            fill.layoutParams = (fill.layoutParams as FrameLayout.LayoutParams).apply { width = w }
            shine.layoutParams = (shine.layoutParams as FrameLayout.LayoutParams).apply { width = w }
            fill.pivotX = 0f; shine.pivotX = 0f
            fill.scaleX = 0f; shine.scaleX = 0f
            fill.animate().scaleX(1f).setDuration(450).start(); shine.animate().scaleX(1f).setDuration(450).start()
        }

        // bonusy: každý v pergamenovém políčku, hodnota v tmavém štítku
        card.addView(ui.text("Bonusy", 18f, ui.rust).apply { setPadding(0, 0, 0, ui.px(4f)) })
        fun stat(label: String, value: String) {
            val r = ui.row().apply {
                background = BevelDrawable(1.5f * ui.dp, Color.parseColor("#EAD6AE"), Color.parseColor("#F8EBCF"), Color.parseColor("#CDB083"), Color.parseColor("#9C7A4E"))
                setPadding(ui.px(9f), ui.px(5f), ui.px(5f), ui.px(5f))
            }
            r.addView(ui.text(label, 15f, ui.inkSoft).apply { layoutParams = ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            r.addView(outlined(ui.text(value, 16f, WHITE, Gravity.CENTER)).apply {
                background = BevelDrawable(1.5f * ui.dp, Color.parseColor("#5A3E32"), Color.parseColor("#7A5646"), Color.parseColor("#3B281F"), Color.parseColor("#1E140C"))
                setPadding(ui.px(7f), ui.px(2f), ui.px(7f), ui.px(4f))
                minWidth = ui.px(48f)
            })
            card.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ui.px(4f) })
        }
        val passive = state.passive(s)
        fun pct(v: Double) = String.format(Locale.US, "%.0f %%", v * 100)
        when (s) {
            Skill.CATCHING -> {
                stat("Šance na útěk z ballu", "−${pct(state.catchReduction)}")
                stat("Míst v týmu", "${state.teamSlots} / ${Team.MAX}")
            }
            Skill.CRAFTING -> stat("Dvojitá výroba", pct(passive))
            Skill.MINING -> {
                stat("Dvojitá ruda", pct(passive))
                if (state.efficiencyBonus(s) > 0) stat("Efektivita krumpáče", "+" + pct(state.efficiencyBonus(s)))
                stat("AFK nejvýš", "${state.afkCapHours(s)} h")
            }
            Skill.BUG_CATCHING -> {
                stat("Dvojitý úlovek", pct(state.multiChance(s)))
                if (state.efficiencyBonus(s) > 0) stat("Efektivita síťky", "+" + pct(state.efficiencyBonus(s)))
                stat("AFK nejvýš", "${state.afkCapHours(s)} h")
            }
            Skill.LOGGING -> {
                stat("Dvojité poleno", pct(passive))
                if (state.efficiencyBonus(s) > 0) stat("Efektivita sekery", "+" + pct(state.efficiencyBonus(s)))
                stat("AFK nejvýš", "${state.afkCapHours(s)} h")
            }
            Skill.HARVESTING -> {
                stat("Dvojitá sklizeň", pct(passive))
                stat("Otevřené záhony", "${state.plotsOpen} / 4")
                if (state.growthSpeedup > 0) stat("Rychlejší růst", pct(state.growthSpeedup))
            }
        }
        stat("XP multiplikátor", "×" + String.format(Locale("cs"), "%.2f", state.xpMultiplier(s)))
        stat("Body ve stromu", "${state.spentPoints(s)} / ${SkillMath.skillPointsEarned(prog.level)}")
        stat("Další bod na", SkillMath.nextSkillPointLevel(prog.level)?.let { "Lv $it" } ?: "všechny")
        card.addView(ui.text("XP získáváš ${s.verb}. Každý level přidá +1 % k pasivnímu bonusu a do Lv ${SkillMath.POINT_CAP} i bod do stromu.", 14f, ui.inkSoft).apply {
            setPadding(ui.px(2f), ui.px(4f), 0, ui.px(10f))
        })

        // tlačítko stromu: dřevo, ikona stromu, zlatý štítek s volnými body (ten pulzuje)
        val pts = state.availablePoints(s)
        val btn = FrameLayout(box.context).apply {
            background = WoodPanelDrawable(2f * ui.dp, parchment = false)
            contentDescription = "Strom dovedností"
            isClickable = true
            setOnClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); onTree() }
        }
        val inner = ui.row().apply { gravity = Gravity.CENTER; setPadding(0, ui.px(2f), 0, ui.px(4f)) }
        inner.addView(ui.icon(TreeArt.TREE, TreeArt.SIZE, TreeArt.SIZE, 24f))
        inner.addView(outlined(ui.text("Strom dovedností", 20f, WHITE)).apply { setPadding(ui.px(8f), 0, 0, ui.px(2f)) })
        btn.addView(inner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        card.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.px(56f)))
        if (pts > 0) {
            val badge = outlined(ui.text("+$pts", 16f, WHITE, Gravity.CENTER)).apply {
                background = BevelDrawable(1.5f * ui.dp, Color.parseColor("#C9961A"), GOLD, Color.parseColor("#8A6410"), Color.parseColor("#3B2A1A"))
                setPadding(ui.px(6f), ui.px(1f), ui.px(6f), ui.px(3f))
            }
            btn.addView(badge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.TOP)
                .apply { topMargin = -ui.px(2f); marginEnd = -ui.px(2f) })
            btn.clipChildren = false
            android.animation.ObjectAnimator.ofPropertyValuesHolder(badge,
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.18f),
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.18f)).apply {
                duration = 520; repeatCount = android.animation.ValueAnimator.INFINITE; repeatMode = android.animation.ValueAnimator.REVERSE
            }.also { anim ->
                // pulz běží jen, dokud je štítek na obrazovce (stránka se při změně překresluje)
                badge.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) { anim.start() }
                    override fun onViewDetachedFromWindow(v: View) { anim.cancel() }
                })
            }
        }
    }

    /** Bílý text s tmavým obrysem jako v IdleOn. */
    private fun outlined(t: TextView): TextView = t.apply { setShadowLayer(3f, 0f, 1.5f, Color.parseColor("#101828")) }

    /** Trenér z mapy (sprite postavy bez oranžového pozadí). */
    private fun portrait(v: View): Bitmap? {
        // postava ze Sunnyside World (docs/adr/0051) – první snímek stání dolů
        return runCatching { cz.uhk.macroflow.pokemon.HeroSprite(v.context).portrait() }.getOrNull()
    }

    // ── Suroviny ────────────────────────────────────────────────────────────

    private data class Entry(val id: String, val label: String, val description: String, val pixels: IntArray, val size: Int)

    fun resources(container: LinearLayout, counts: Map<String, Int>, menuRoot: FrameLayout) {
        container.removeAllViews()
        val ui = WoodUi(container.context)
        container.addView(ui.text("Suroviny", 27f, container.context.getColor(R.color.journal_chapter_title_ink), Gravity.CENTER).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        container.addView(ui.text("Klepni na políčko – kde to získat a jak často to padá.", 15f, ui.inkSoft, Gravity.CENTER).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = ui.px(8f) }
        })

        fun res(r: Resource) = Entry(r.itemId, r.label, r.description, SkillArt.resourceIcon(r), SkillArt.ITEM)
        val sections = listOf(
            "Z Makromonů" to (listOf(res(Resource.ENERGY)) + Resource.entries.filter { it.isMonsterMaterial }.map { res(it) }),
            "Ze záhonů" to listOf(res(Resource.BERRY_GREEN), res(Resource.BERRY_BLUE), res(Resource.BERRY_BLACK)),
            "Semínka" to listOf(res(Resource.SEED_GREEN), res(Resource.SEED_BLUE), res(Resource.SEED_BLACK)),
            "Z dolů" to listOf(res(Resource.ORE_COPPER), res(Resource.ORE_SILVER), res(Resource.ORE_GOLD)),
            "Ze stromů" to listOf(res(Resource.LOG_OAK), res(Resource.LOG_BIRCH), res(Resource.LOG_MAPLE)),
            "Hmyz z Dolů" to listOf(res(Resource.BUG_SPARK), res(Resource.BUG_CRYSTAL), res(Resource.BUG_MAGMA)),
            "Vyrobené" to Makroball.entries.map { Entry(it.id, it.label, it.description, it.pixels, Makroball.SIZE) }
        )
        sections.forEach { (title, entries) ->
            container.addView(ui.text(title, 20f, ui.rust).apply { setPadding(ui.px(2f), ui.px(6f), 0, ui.px(4f)) })
            entries.chunked(4).forEach { chunk ->
                // Gravity.TOP: se svislým centrováním a spodním okrajem vyjel čtverec o 4 dp nahoru
                // a řádek mu ořízl horní obrys (hnědé políčko bez horní hrany)
                val row = ui.row().apply { gravity = Gravity.TOP }
                chunk.forEach { e -> row.addView(slot(ui, e, counts[e.id] ?: 0) { showInfo(menuRoot, e, counts[e.id] ?: 0) }) }
                container.addView(row)
            }
        }
    }

    /** Dřevěná cedule: jméno, kolik máš, popis a kde se to dá získat (s šancí). */
    private fun showInfo(root: FrameLayout, e: Entry, n: Int) {
        WorkshopMenus.show(root, e.label, "Máš: $n ks") { ui, body, _ ->
            val top = ui.row().apply { gravity = Gravity.TOP }
            val frame = FrameLayout(ui.ctx).apply { background = BevelDrawable.slot(2f * ui.dp) }
            frame.addView(ui.icon(e.pixels, e.size, e.size, 48f), FrameLayout.LayoutParams(ui.px(48f), ui.px(48f), Gravity.CENTER))
            top.addView(frame, LinearLayout.LayoutParams(ui.px(66f), ui.px(66f)))
            top.addView(ui.text(e.description, 16f, ui.ink).apply { setPadding(ui.px(12f), ui.px(2f), 0, 0) },
                ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(top)
            val sources = ItemInfo.sources(e.id)
            if (sources.isNotEmpty()) {
                body.addView(ui.text("Kde získat", 21f, ui.rust).apply { setPadding(0, ui.px(12f), 0, ui.px(6f)) })
                sources.forEach { src ->
                    val c = WorkshopMenus.card(ui)
                    val col = ui.column()
                    col.addView(ui.text(src.where, 17f))
                    col.addView(ui.text(src.rate, 15f, ui.olive))
                    c.addView(col, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    body.addView(c)
                }
            }
        }
    }

    private fun slot(ui: WoodUi, e: Entry, n: Int, onClick: () -> Unit): View {
        val f = FrameLayout(ui.ctx).apply {
            background = BevelDrawable.slot(2f * ui.dp)
            alpha = if (n > 0) 1f else 0.45f
            contentDescription = "${e.label}: $n"
            setOnClickListener { onClick() }
        }
        f.addView(ui.icon(e.pixels, e.size, e.size, 40f), FrameLayout.LayoutParams(ui.px(40f), ui.px(40f), Gravity.CENTER))
        f.addView(outlined(ui.text(if (n > 999) "999+" else "$n", 16f, WHITE)).apply { setPadding(0, 0, ui.px(5f), ui.px(2f)) },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.BOTTOM))
        f.layoutParams = LinearLayout.LayoutParams(ui.px(62f), ui.px(62f)).apply { marginEnd = ui.px(8f); bottomMargin = ui.px(8f) }
        return f
    }

    // ── Ocenění (docs/adr/0037) ──

    /** Stránka Ocenění: medaile po kategoriích v polovičních políčkách, klepnutí = dřevěná cedule. */
    fun awards(container: LinearLayout, facts: AwardFacts, unlocked: Map<String, Int>, menuRoot: FrameLayout) {
        container.removeAllViews()
        val ui = WoodUi(container.context)
        container.addView(ui.text("Ocenění", 27f, container.context.getColor(R.color.journal_chapter_title_ink), Gravity.CENTER).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        container.addView(ui.text("Získáno ${unlocked.size} z ${Awards.ALL.size}. Klepni na medaili.", 15f, ui.inkSoft, Gravity.CENTER).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = ui.px(8f) }
        })
        AwardCategory.entries.forEach { cat ->
            val list = Awards.byCategory(cat)
            val got = list.count { it.id in unlocked }
            val head = ui.row().apply { setPadding(ui.px(2f), ui.px(6f), 0, ui.px(4f)) }
            head.addView(ui.text(cat.label, 20f, ui.rust), ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(ui.text("$got / ${list.size}", 16f, ui.inkSoft))
            container.addView(head)
            list.chunked(6).forEach { chunk ->
                val row = ui.row().apply { gravity = Gravity.TOP }
                chunk.forEach { a -> row.addView(awardSlot(ui, a, a.id in unlocked) { showAward(menuRoot, a, facts, unlocked[a.id]) }) }
                container.addView(row)
            }
        }
    }

    private fun awardSlot(ui: WoodUi, a: Award, done: Boolean, onClick: () -> Unit): View {
        val f = FrameLayout(ui.ctx).apply {
            background = BevelDrawable.slot(1.5f * ui.dp)
            alpha = if (done) 1f else 0.6f
            contentDescription = a.title + if (done) " (získáno)" else ""
            setOnClickListener { onClick() }
        }
        f.addView(ui.icon(AwardArt.icon(a, done), AwardArt.SIZE, AwardArt.SIZE, 32f), FrameLayout.LayoutParams(ui.px(32f), ui.px(32f), Gravity.CENTER))
        f.layoutParams = LinearLayout.LayoutParams(ui.px(42f), ui.px(42f)).apply { marginEnd = ui.px(6f); bottomMargin = ui.px(6f) }
        return f
    }

    /** Dřevěná cedule ocenění: medaile v dřevěném rámečku, popis, postup a den získání. */
    private fun showAward(root: FrameLayout, a: Award, f: AwardFacts, day: Int?) {
        val done = day != null
        WorkshopMenus.show(root, a.title, "${a.tier.label} · ${a.category.label}") { ui, body, _ ->
            val top = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
            top.addView(ui.icon(AwardArt.framed(AwardArt.icon(a, done)), AwardArt.FRAME, AwardArt.FRAME, 72f), LinearLayout.LayoutParams(ui.px(72f), ui.px(72f)))
            top.addView(ui.text(a.description, 17f, ui.ink).apply { setPadding(ui.px(14f), 0, 0, 0) },
                ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(top)

            val target = Awards.target(a, f)
            val value = if (done) target else minOf(Awards.value(a, f), target)
            val frac = if (done) 1f else Awards.fraction(a, f)
            val bar = FrameLayout(ui.ctx).apply { background = BevelDrawable(1.5f * ui.dp, Color.parseColor("#2A1C11"), Color.parseColor("#1A110A"), Color.parseColor("#4F3016")) }
            val fill = View(ui.ctx).apply { setBackgroundColor(Color.parseColor(if (done) "#D9A62A" else "#7F9148")) }
            bar.addView(fill, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(ui.px(3f), ui.px(3f), ui.px(3f), ui.px(3f)) })
            bar.addView(outlined(ui.text("$value / $target", 15f, WHITE, Gravity.CENTER)),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            body.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.px(24f)).apply { topMargin = ui.px(14f) })
            bar.post { fill.layoutParams = (fill.layoutParams as FrameLayout.LayoutParams).apply { width = ((bar.width - ui.px(6f)) * frac).toInt() } }

            val status = if (day != null) {
                val d = java.time.LocalDate.ofEpochDay(day.toLong())
                "🏅 Získáno ${d.dayOfMonth}. ${d.monthValue}. ${d.year}"
            } else "Zatím nezískáno"
            body.addView(ui.text(status, 17f, if (done) ui.olive else ui.inkSoft).apply { setPadding(0, ui.px(10f), 0, 0) })
        }
    }

}

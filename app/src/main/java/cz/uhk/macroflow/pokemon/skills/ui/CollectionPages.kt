package cz.uhk.macroflow.pokemon.skills.ui

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.CapturedMakromonEntity
import cz.uhk.macroflow.pokemon.MakrodexEntryEntity
import cz.uhk.macroflow.pokemon.PokemonLevelCalc
import cz.uhk.macroflow.pokemon.balls.Makroball
import cz.uhk.macroflow.pokemon.bag.BagItems
import cz.uhk.macroflow.pokemon.shiny.ShinyDex
import cz.uhk.macroflow.pokemon.shiny.ShinySprites
import cz.uhk.macroflow.pokemon.skills.TreeArt

/**
 * Sbírky v deníku (docs/adr/0060) ve stejném dřevěném stylu jako Postava a Denní úkoly:
 * **Kapsa** (chycení Makromoni a tým), **Batoh** (předměty po přihrádkách) a **Makrodex**.
 */
object CollectionPages {

    private val WHITE = Color.parseColor("#FEFAE0")
    private val GOLD = Color.parseColor("#FFD54F")

    private fun outlined(t: TextView): TextView = t.apply { setShadowLayer(3f, 0f, 1.5f, Color.parseColor("#101828")) }

    /** Dřevěná cedule s ikonou, názvem a údajem vpravo (stejná jako u Denních úkolů). */
    internal fun plaque(ui: WoodUi, icon: IntArray, iconSize: Int, title: String, right: String?): View {
        val p = ui.row().apply {
            background = WoodPanelDrawable(2.5f * ui.dp, parchment = false)
            setPadding(ui.px(14f), ui.px(11f), ui.px(14f), ui.px(13f))
        }
        p.addView(ui.icon(icon, iconSize, iconSize, 28f))
        p.addView(outlined(ui.text(title, 25f, WHITE)).apply { setPadding(ui.px(8f), 0, 0, ui.px(2f)) },
            ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        right?.let { p.addView(outlined(ui.text(it, 16f, GOLD))) }
        return p
    }

    internal fun header(container: LinearLayout, ui: WoodUi, icon: IntArray, iconSize: Int, title: String, right: String?, hint: String?) {
        // vpravo nahoře je v knize zavírací křížek – cedule mu uhne
        container.addView(plaque(ui, icon, iconSize, title, right), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { marginEnd = ui.px(34f) })
        hint?.let {
            container.addView(ui.text(it, 14f, ui.inkSoft, Gravity.CENTER), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = ui.px(6f); bottomMargin = ui.px(4f) })
        }
    }

    private fun section(container: LinearLayout, ui: WoodUi, title: String) =
        container.addView(ui.text(title, 20f, ui.rust).apply { setPadding(ui.px(2f), ui.px(10f), 0, ui.px(4f)) })

    /** Malý pixelový pruh (XP Makromona). */
    private fun miniBar(ui: WoodUi, fraction: Float, color: Int, heightDp: Float = 8f): View {
        val track = FrameLayout(ui.ctx).apply {
            background = BevelDrawable(1f * ui.dp, Color.parseColor("#2A1C11"), Color.parseColor("#1A110A"), Color.parseColor("#4F3016"))
        }
        val fill = View(ui.ctx).apply { setBackgroundColor(color) }
        track.addView(fill, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(ui.px(2f), ui.px(2f), ui.px(2f), ui.px(2f)) })
        track.post { fill.layoutParams = (fill.layoutParams as FrameLayout.LayoutParams).apply { width = ((track.width - ui.px(4f)) * fraction.coerceIn(0f, 1f)).toInt() } }
        track.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.px(heightDp))
        return track
    }

    /** Řádky po [perRow] dlaždicích stejné šířky. */
    internal fun grid(container: LinearLayout, ui: WoodUi, tiles: List<View>, perRow: Int) {
        tiles.chunked(perRow).forEach { chunk ->
            val row = ui.row().apply { gravity = Gravity.TOP }
            chunk.forEach { row.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = ui.px(6f); bottomMargin = ui.px(6f) }) }
            repeat(perRow - chunk.size) { row.addView(View(ui.ctx), LinearLayout.LayoutParams(0, 1, 1f).apply { marginEnd = ui.px(6f) }) }
            container.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    // ── sprity Makromonů ────────────────────────────────────────────────────

    fun spriteRes(v: View, makrodexId: String, name: String): Int {
        val shortId = if (makrodexId.length >= 3) makrodexId.takeLast(2) else makrodexId
        val n = "makromon_${shortId}_${name.lowercase().trim().replace(" ", "_")}"
        return v.resources.getIdentifier(n, "drawable", v.context.packageName)
    }

    private fun sprite(ui: WoodUi, id: String, name: String, sizeDp: Float, shiny: Boolean = false, silhouette: Boolean = false): ImageView =
        ImageView(ui.ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            val res = spriteRes(this, id, name)
            // druh ještě nemá vlastní sprite: místo ikonky domečku stín Makroballu
            if (res != 0) ShinySprites.into(this, res, id, shiny)
            else setImageBitmap(cz.uhk.macroflow.pokemon.balls.BallSprites.icon(Makroball.entries.first(), ui.px(sizeDp * 0.7f)))
            if (silhouette) {
                // neznámý druh: černý stín
                colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(0f, 0f, 0f, 0f, 20f, 0f, 0f, 0f, 0f, 16f, 0f, 0f, 0f, 0f, 12f, 0f, 0f, 0f, 0.85f, 0f)))
            }
            layoutParams = LinearLayout.LayoutParams(ui.px(sizeDp), ui.px(sizeDp))
        }

    /** Tlačítko do dřevěného menu přes celou šířku. */
    private fun wide(ui: WoodUi, label: String, enabled: Boolean = true, onClick: () -> Unit): TextView =
        ui.button(label, enabled, onClick).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(6f) }
        }

    // ── Kapsa ───────────────────────────────────────────────────────────────

    /** Co se v Kapse dá s Makromonem udělat; zpětná volání proběhnou a stránka se překreslí. */
    interface PocketCallbacks {
        fun toggleTeam(m: CapturedMakromonEntity)
        fun makeActive(m: CapturedMakromonEntity)
        fun toggleLock(m: CapturedMakromonEntity)
        fun release(m: CapturedMakromonEntity)
    }

    fun pocket(
        container: LinearLayout, menuRoot: FrameLayout,
        mons: List<CapturedMakromonEntity>, team: List<Int>, slots: Int, activeCaughtDate: Long,
        cb: PocketCallbacks
    ) {
        container.removeAllViews()
        val ui = WoodUi(container.context)
        val u = 2f * ui.dp
        header(container, ui, Makroball.entries.first().pixels, Makroball.SIZE, "Kapsa", "${mons.size} chyceno",
            "Klepni na Makromona – tým, parťák na liště, zámek.")

        // tým: místa v řadě, zamčená místa s visacím zámkem až do šesti
        section(container, ui, "Tým ${team.size} / $slots")
        val teamRow = ui.row().apply { gravity = Gravity.CENTER }
        for (i in 0 until 6) {
            val m = team.getOrNull(i)?.let { id -> mons.firstOrNull { it.id == id } }
            val active = m != null && m.caughtDate == activeCaughtDate
            val f = FrameLayout(ui.ctx).apply {
                background = if (i < slots) BevelDrawable.navy(u, selected = active)
                    else BevelDrawable(u, Color.parseColor("#2A2C33"), Color.parseColor("#3A3D46"), Color.parseColor("#17181C"))
                if (m != null) setOnClickListener { pocketDetail(menuRoot, m, team, activeCaughtDate, cb) }
                contentDescription = m?.name ?: if (i < slots) "Volné místo" else "Zamčené místo"
            }
            when {
                m != null -> f.addView(sprite(ui, m.makromonId, m.name, 40f, shiny = m.isShiny), FrameLayout.LayoutParams(ui.px(40f), ui.px(40f), Gravity.CENTER))
                i < slots -> f.addView(outlined(ui.text("+", 22f, Color.parseColor("#7A8BB0"), Gravity.CENTER)), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                else -> f.addView(ui.icon(TreeArt.LOCK, TreeArt.SIZE, TreeArt.SIZE, 20f), FrameLayout.LayoutParams(ui.px(20f), ui.px(20f), Gravity.CENTER))
            }
            if (active) f.addView(outlined(ui.text("★", 13f, GOLD)).apply { setPadding(ui.px(3f), 0, 0, 0) },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.TOP))
            teamRow.addView(f, LinearLayout.LayoutParams(0, ui.px(52f), 1f).apply { marginEnd = ui.px(4f) })
        }
        container.addView(teamRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (slots < 6) container.addView(ui.text("Další místa odemkneš ve stromu Chytání (Postava → Strom dovedností).", 13f, ui.inkSoft).apply {
            setPadding(ui.px(2f), ui.px(4f), 0, 0)
        })

        section(container, ui, "Všichni Makromoni")
        if (mons.isEmpty()) {
            container.addView(ui.text("Kapsa je zatím prázdná. Chyť prvního Makromona v trávě na louce!", 16f, ui.inkSoft))
            return
        }
        // tým první (v pořadí týmu), pak podle levelu
        val sorted = mons.sortedWith(compareBy<CapturedMakromonEntity> { team.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            .thenByDescending { it.level }.thenBy { it.name })
        val tiles = sorted.map { m ->
            val inTeam = m.id in team
            val active = m.caughtDate == activeCaughtDate
            val card = FrameLayout(ui.ctx).apply {
                background = if (active) BevelDrawable(u, Color.parseColor("#EAD6AE"), Color.parseColor("#F8EBCF"), Color.parseColor("#CDB083"), selected = true)
                    else BevelDrawable.slot(u)
                setOnClickListener { pocketDetail(menuRoot, m, team, activeCaughtDate, cb) }
                contentDescription = "${m.name}, level ${m.level}"
            }
            val col = ui.column().apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(ui.px(5f), ui.px(6f), ui.px(5f), ui.px(6f)) }
            col.addView(sprite(ui, m.makromonId, m.name, 46f, shiny = m.isShiny))
            col.addView(ui.text(m.name, 15f, ui.ink, Gravity.CENTER).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            col.addView(ui.text("Lv ${m.level}", 13f, ui.rust, Gravity.CENTER))
            col.addView(miniBar(ui, PokemonLevelCalc.progressToNextLevel(m.xp), Color.parseColor("#7F9148"), 7f).apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = ui.px(3f)
            })
            card.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            if (inTeam) card.addView(outlined(ui.text("${team.indexOf(m.id) + 1}", 14f, WHITE, Gravity.CENTER)).apply {
                background = BevelDrawable(1f * ui.dp, if (active) Color.parseColor("#C9961A") else Color.parseColor("#2E4677"),
                    if (active) GOLD else Color.parseColor("#4B6BA3"), if (active) Color.parseColor("#8A6410") else Color.parseColor("#1B2A4A"))
                setPadding(ui.px(5f), 0, ui.px(5f), ui.px(2f))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.TOP).apply {
                setMargins(ui.px(3f), ui.px(3f), 0, 0)
            })
            if (m.isLocked) card.addView(ui.icon(TreeArt.LOCK, TreeArt.SIZE, TreeArt.SIZE, 14f),
                FrameLayout.LayoutParams(ui.px(14f), ui.px(14f), Gravity.END or Gravity.TOP).apply { setMargins(0, ui.px(4f), ui.px(4f), 0) })
            if (m.isShiny) card.addView(outlined(ui.text("✦", 14f, GOLD)),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.BOTTOM).apply { setMargins(0, 0, ui.px(5f), ui.px(16f)) })
            card
        }
        grid(container, ui, tiles, 3)
    }

    private fun pocketDetail(root: FrameLayout, m: CapturedMakromonEntity, team: List<Int>, activeCaughtDate: Long, cb: PocketCallbacks) {
        val inTeam = m.id in team
        val active = m.caughtDate == activeCaughtDate
        val date = java.time.Instant.ofEpochMilli(m.caughtDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val origin = if (m.otName.isNotBlank()) "Z výměny od ${m.otName} · " else "Chycen "
        WorkshopMenus.show(root, m.name + if (m.isShiny) " ✦" else "", "$origin${date.dayOfMonth}. ${date.monthValue}. ${date.year}") { ui, body, close ->
            val top = ui.row()
            val frame = FrameLayout(ui.ctx).apply { background = BevelDrawable.navy(2f * ui.dp, selected = active) }
            frame.addView(sprite(ui, m.makromonId, m.name, 72f, shiny = m.isShiny), FrameLayout.LayoutParams(ui.px(72f), ui.px(72f), Gravity.CENTER))
            top.addView(frame, LinearLayout.LayoutParams(ui.px(90f), ui.px(90f)))
            val info = ui.column().apply { setPadding(ui.px(12f), 0, 0, 0) }
            info.addView(ui.text("Lv ${m.level}", 22f, ui.rust))
            val prog = PokemonLevelCalc.progressToNextLevel(m.xp)
            info.addView(miniBar(ui, prog, Color.parseColor("#7F9148"), 12f).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.px(4f) })
            info.addView(ui.text("${(prog * 100).toInt()} % do dalšího levelu", 14f, ui.inkSoft).apply { setPadding(0, ui.px(3f), 0, 0) })
            info.addView(ui.text(when {
                active -> "★ Parťák na liště"
                inTeam -> "V týmu (${team.indexOf(m.id) + 1}.)"
                else -> "V kapse"
            }, 16f, if (active) Color.parseColor("#9A6A10") else ui.olive).apply { setPadding(0, ui.px(4f), 0, 0) })
            top.addView(info, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(top)

            body.addView(wide(ui, if (inTeam) "Odebrat z týmu" else "Přidat do týmu") { close(); cb.toggleTeam(m) })
            if (!active) body.addView(wide(ui, "Parťák na liště") { close(); cb.makeActive(m) })
            body.addView(wide(ui, if (m.isLocked) "Odemknout" else "Zamknout") { close(); cb.toggleLock(m) })
            // puštění: druhé klepnutí potvrdí, zamčeného pustit nejde
            lateinit var rel: TextView
            rel = wide(ui, if (m.isLocked) "Pustit (nejdřív odemkni)" else "Pustit na svobodu", enabled = !m.isLocked) {
                if (rel.tag != "confirm") { rel.tag = "confirm"; rel.text = "Opravdu pustit ${m.name}?"; return@wide }
                close(); cb.release(m)
            }
            body.addView(rel)
        }
    }

    // ── Batoh ───────────────────────────────────────────────────────────────

    fun bag(container: LinearLayout, menuRoot: FrameLayout, counts: Map<String, Int>,
            readText: (BagItems.Item) -> String, onUse: (BagItems.Item) -> Unit) {
        container.removeAllViews()
        val ui = WoodUi(container.context)
        val pockets = BagItems.pockets(counts)
        header(container, ui, TreeArt.BOOT, TreeArt.SIZE, "Batoh", "${pockets.values.sumOf { it.size }} druhů",
            "Suroviny najdeš ve vlastní záložce. Klepni na předmět.")
        if (pockets.isEmpty()) {
            container.addView(ui.text("Batoh je prázdný. Makrobally a léčiva koupíš v obchodě ve městě.", 16f, ui.inkSoft).apply { setPadding(0, ui.px(10f), 0, 0) })
            return
        }
        pockets.forEach { (pocket, items) ->
            section(container, ui, pocket.label)
            val tiles = items.map { (item, n) ->
                FrameLayout(ui.ctx).apply {
                    background = if (pocket == BagItems.Pocket.KEY)
                        BevelDrawable(2f * ui.dp, Color.parseColor("#D9B86A"), Color.parseColor("#F2D98F"), Color.parseColor("#A07E30"), Color.parseColor("#3B2A1A"))
                        else BevelDrawable.slot(2f * ui.dp)
                    contentDescription = "${item.label}: $n"
                    setOnClickListener { bagDetail(menuRoot, item, n, readText, onUse) }
                    addView(ui.icon(item.pixels, item.size, item.size, 40f), FrameLayout.LayoutParams(ui.px(40f), ui.px(40f), Gravity.CENTER))
                    if (pocket != BagItems.Pocket.KEY || n > 1) addView(outlined(ui.text(if (n > 999) "999+" else "$n", 16f, WHITE)).apply { setPadding(0, 0, ui.px(5f), ui.px(2f)) },
                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.BOTTOM))
                    layoutParams = LinearLayout.LayoutParams(0, ui.px(62f), 1f)
                }.let { tile ->
                    // název pod políčkem
                    ui.column().apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        addView(tile, LinearLayout.LayoutParams(ui.px(62f), ui.px(62f)))
                        addView(ui.text(item.label, 12f, ui.inkSoft, Gravity.CENTER).apply { maxLines = 2; setPadding(0, ui.px(2f), 0, 0) },
                            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    }
                }
            }
            grid(container, ui, tiles, 4)
        }
    }

    private fun bagDetail(root: FrameLayout, item: BagItems.Item, n: Int, readText: (BagItems.Item) -> String, onUse: (BagItems.Item) -> Unit) {
        WorkshopMenus.show(root, item.label, if (item.pocket == BagItems.Pocket.KEY && n <= 1) "Klíčový předmět" else "Máš: $n ks") { ui, body, close ->
            val top = ui.row().apply { gravity = Gravity.TOP }
            val frame = FrameLayout(ui.ctx).apply { background = BevelDrawable.slot(2f * ui.dp) }
            frame.addView(ui.icon(item.pixels, item.size, item.size, 48f), FrameLayout.LayoutParams(ui.px(48f), ui.px(48f), Gravity.CENTER))
            top.addView(frame, LinearLayout.LayoutParams(ui.px(66f), ui.px(66f)))
            top.addView(ui.text(item.description, 16f, ui.ink).apply { setPadding(ui.px(12f), ui.px(2f), 0, 0) }, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(top)
            when (item.action) {
                BagItems.Action.READ -> {
                    // text na pergamenu přímo v menu
                    val sheet = ui.text(readText(item), 15f, Color.parseColor("#2A1E12")).apply {
                        typeface = android.graphics.Typeface.MONOSPACE
                        background = BevelDrawable(1.5f * ui.dp, Color.parseColor("#F2EAD6"), Color.parseColor("#FBF6EA"), Color.parseColor("#D8C9A6"), Color.parseColor("#9C7A4E"))
                        setPadding(ui.px(12f), ui.px(10f), ui.px(12f), ui.px(12f))
                        setLineSpacing(ui.px(2f).toFloat(), 1f)
                    }
                    body.addView(sheet, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(10f) })
                }
                BagItems.Action.USE -> body.addView(wide(ui, "Použít") { close(); onUse(item) })
                BagItems.Action.NONE -> {}
            }
        }
    }

    // ── Makrodex ────────────────────────────────────────────────────────────

    enum class DexMode(val label: String) { DEX("Makrodex"), SHINY_SEEN("✦ Viděno"), SHINY_CAUGHT("✦ Chyceno") }

    data class DexData(
        val entries: List<MakrodexEntryEntity>,
        val unlocked: Set<String>,
        /** Kolik kusů od druhu máš (0 = jen viděný). */
        val caught: Map<String, Int>,
        val shinySeen: Set<String>,
        val shinyCaught: Set<String>
    )

    interface DexCallbacks {
        /** Spis S-7 (null = spis se neukazuje – málo Vhledu). */
        fun dossier(e: MakrodexEntryEntity, caught: Int): String?
        fun spirraPaths(show: (String) -> Unit)
        /** Testovací evoluce – jen v ladicí verzi (null = tlačítko se nezobrazí). */
        fun evoTest(e: MakrodexEntryEntity): (() -> Unit)?
    }

    fun dex(container: LinearLayout, menuRoot: FrameLayout, d: DexData, mode: DexMode, onMode: (DexMode) -> Unit, cb: DexCallbacks) {
        container.removeAllViews()
        val ui = WoodUi(container.context)
        val ids = d.entries.map { it.makrodexId }
        val caughtN = ids.count { (d.caught[it] ?: 0) > 0 }
        val seenN = ids.count { it in d.unlocked }
        header(container, ui, TreeArt.STAR, TreeArt.SIZE, "Makrodex", "$caughtN / ${ids.size}", null)

        // přepínač: Makrodex / shiny viděno / shiny chyceno (dřevěné záložky jako EQUIPS / TOOLS)
        val tabs = ui.row().apply {
            background = BevelDrawable(2f * ui.dp, Color.parseColor("#5A3E32"), Color.parseColor("#7A5646"), Color.parseColor("#3B281F"), Color.parseColor("#1E140C"))
            setPadding(ui.px(5f), ui.px(5f), ui.px(5f), ui.px(5f))
        }
        DexMode.entries.forEach { m ->
            tabs.addView(outlined(ui.text(m.label, 16f, if (m == mode) WHITE else Color.parseColor("#C9B8A8"), Gravity.CENTER)).apply {
                background = BevelDrawable(1.5f * ui.dp,
                    if (m == mode) Color.parseColor("#8A6450") else Color.parseColor("#4A3328"),
                    if (m == mode) Color.parseColor("#B08A74") else Color.parseColor("#5E4236"),
                    Color.parseColor("#2E1E16"), Color.parseColor("#1E140C"), selected = m == mode)
                setPadding(ui.px(4f), ui.px(5f), ui.px(4f), ui.px(6f))
                setOnClickListener { if (m != mode) onMode(m) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ui.px(2f); marginEnd = ui.px(2f) })
        }
        container.addView(tabs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(8f) })

        val shiny = mode != DexMode.DEX
        val (sSeen, sCaught) = ShinyDex.counts(ids, d.shinySeen, d.shinyCaught)
        container.addView(ui.text(if (!shiny) "Viděno $seenN · chyceno $caughtN z ${ids.size}" else "✦ Viděno $sSeen · chyceno $sCaught z ${ids.size}",
            15f, ui.inkSoft, Gravity.CENTER), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(6f); bottomMargin = ui.px(6f) })

        val list = when (mode) {
            DexMode.DEX -> d.entries
            DexMode.SHINY_SEEN -> ShinyDex.visible(ids, ShinyDex.Filter.SEEN, d.shinySeen, d.shinyCaught).toSet().let { k -> d.entries.filter { it.makrodexId in k } }
            DexMode.SHINY_CAUGHT -> ShinyDex.visible(ids, ShinyDex.Filter.CAUGHT, d.shinySeen, d.shinyCaught).toSet().let { k -> d.entries.filter { it.makrodexId in k } }
        }
        if (list.isEmpty()) {
            container.addView(ui.text(if (mode == DexMode.SHINY_SEEN) "Zatím jsi žádného shiny Makromona nepotkal. Šance je 1 : 100 – každé setkání se počítá!"
                else "Zatím nemáš chyceného shiny. Až nějakého chytíš, objeví se tu v jeho barvách.", 16f, ui.inkSoft, Gravity.CENTER))
            return
        }
        val u = 2f * ui.dp
        val tiles = list.map { e ->
            val id = e.makrodexId
            val n = d.caught[id] ?: 0
            val known = id in d.unlocked || shiny
            val shinyCaught = shiny && id in d.shinyCaught
            val tile = FrameLayout(ui.ctx).apply {
                background = when {
                    shinyCaught -> BevelDrawable(u, Color.parseColor("#D9B86A"), Color.parseColor("#F2D98F"), Color.parseColor("#A07E30"), Color.parseColor("#3B2A1A"))
                    !known -> BevelDrawable(u, Color.parseColor("#4A3D33"), Color.parseColor("#5E4E42"), Color.parseColor("#2E241C"), Color.parseColor("#1E140C"))
                    n > 0 -> BevelDrawable(u, Color.parseColor("#C9D9A0"), Color.parseColor("#E2EEC0"), Color.parseColor("#8FA060"), Color.parseColor("#3B2A1A"))
                    else -> BevelDrawable.slot(u)
                }
                contentDescription = if (known) "#$id ${e.displayName}" else "#$id neznámý"
                setOnClickListener { dexDetail(menuRoot, e, known, n, shiny, d, cb) }
            }
            val col = ui.column().apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(ui.px(2f), ui.px(4f), ui.px(2f), ui.px(4f)) }
            col.addView(ui.text("#$id", 12f, if (known) ui.inkSoft else Color.parseColor("#C9B8A8"), Gravity.CENTER))
            col.addView(sprite(ui, id, e.displayName, 40f, shiny = shiny, silhouette = !known))
            col.addView(ui.text(if (known) e.displayName else "???", 12f, if (known) ui.ink else Color.parseColor("#C9B8A8"), Gravity.CENTER).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            tile.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            if (n > 0 && !shiny) tile.addView(ui.icon(Makroball.entries.first().pixels, Makroball.SIZE, Makroball.SIZE, 12f),
                FrameLayout.LayoutParams(ui.px(12f), ui.px(12f), Gravity.END or Gravity.TOP).apply { setMargins(0, ui.px(4f), ui.px(4f), 0) })
            tile
        }
        grid(container, ui, tiles, 4)
    }

    private fun dexDetail(root: FrameLayout, e: MakrodexEntryEntity, known: Boolean, caught: Int, shiny: Boolean, d: DexData, cb: DexCallbacks) {
        val id = e.makrodexId
        WorkshopMenus.show(root, "#$id ${if (known) e.displayName else "???"}", null) { ui, body, close ->
            val top = ui.row().apply { gravity = Gravity.TOP }
            val frame = FrameLayout(ui.ctx).apply { background = BevelDrawable.navy(2f * ui.dp, selected = shiny && id in d.shinyCaught) }
            frame.addView(sprite(ui, id, e.displayName, 80f, shiny = shiny, silhouette = !known), FrameLayout.LayoutParams(ui.px(80f), ui.px(80f), Gravity.CENTER))
            top.addView(frame, LinearLayout.LayoutParams(ui.px(96f), ui.px(96f)))
            val info = ui.column().apply { setPadding(ui.px(12f), 0, 0, 0) }
            val status = when {
                shiny && id in d.shinyCaught -> "✦ SHINY · CHYCENO"
                shiny -> "✦ SHINY · VIDĚNO"
                caught > 0 -> "${e.type.uppercase()} · máš $caught ×"
                known -> "${e.type.uppercase()} · viděno"
                else -> "NEOBJEVENO"
            }
            info.addView(outlined(ui.text(status, 15f, WHITE, Gravity.CENTER)).apply {
                background = BevelDrawable(1.5f * ui.dp,
                    if (caught > 0 || shiny) Color.parseColor("#BC6C25") else Color.parseColor("#5A3E32"),
                    if (caught > 0 || shiny) Color.parseColor("#E09A50") else Color.parseColor("#7A5646"),
                    if (caught > 0 || shiny) Color.parseColor("#7A4416") else Color.parseColor("#3B281F"), Color.parseColor("#3B2A1A"))
                setPadding(ui.px(8f), ui.px(3f), ui.px(8f), ui.px(5f))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val text = if (known) cz.uhk.macroflow.pokemon.story.Insight.dexWhisper(id)?.let { e.macroDesc + "\n\n" + it } ?: e.macroDesc
                else cz.uhk.macroflow.pokemon.dex.DexText.hint(id, e.unlockedHint)
            info.addView(ui.text(text, 15f, if (known) ui.ink else ui.inkSoft).apply { setPadding(0, ui.px(6f), 0, 0) })
            top.addView(info, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(top)

            if (!shiny && known && caught > 0) cb.dossier(e, caught)?.let { dossier ->
                body.addView(wide(ui, "Spis S-7/$id") {
                    close()
                    WorkshopMenus.show(root, "Spis S-7/$id", "Kustodiát · důvěrné") { ui2, b2, _ -> b2.addView(paper(ui2, dossier)) }
                })
            }
            if (caught > 0 && id == cz.uhk.macroflow.pokemon.evolution.SpirraEvolution.SPIRRA_ID) body.addView(wide(ui, "Cesty vývoje") {
                close()
                cb.spirraPaths { txt -> WorkshopMenus.show(root, "Cesty vývoje Spirry", null) { ui2, b2, _ -> b2.addView(paper(ui2, txt)) } }
            })
            if (caught > 0) cb.evoTest(e)?.let { run -> body.addView(wide(ui, "Test evoluce (ladění)") { close(); run() }) }
        }
    }

    /** Pergamenový list s textem (spisy, cesty vývoje). */
    private fun paper(ui: WoodUi, text: String): View = ui.text(text, 14f, Color.parseColor("#2A1E12")).apply {
        typeface = android.graphics.Typeface.MONOSPACE
        background = BevelDrawable(1.5f * ui.dp, Color.parseColor("#F2EAD6"), Color.parseColor("#FBF6EA"), Color.parseColor("#D8C9A6"), Color.parseColor("#9C7A4E"))
        setPadding(ui.px(12f), ui.px(10f), ui.px(12f), ui.px(12f))
        setLineSpacing(ui.px(2f).toFloat(), 1f)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}

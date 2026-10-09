package cz.uhk.macroflow.pokemon.trainer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.pokemon.CapturedMakromonEntity
import cz.uhk.macroflow.pokemon.PokemonBattleFragment
import cz.uhk.macroflow.pokemon.skills.SkillStore
import cz.uhk.macroflow.pokemon.skills.ui.BevelDrawable
import cz.uhk.macroflow.pokemon.skills.ui.WoodPanelDrawable
import cz.uhk.macroflow.pokemon.skills.ui.WoodUi
import cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus
import cz.uhk.macroflow.pokemon.species.SpeciesRegistry
import cz.uhk.macroflow.pokemon.trade.TradeSheet
import cz.uhk.macroflow.pokemon.trade.TradeStore
import cz.uhk.macroflow.pokemon.trade.Trading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Aréna v Makrosvětě (docs/adr/0076, 0078): nahoře gladiátorská aréna s tvým parťákem a vybraným
 * soupeřem čelem k sobě na písku, dole dřevěný panel jako ostatní menu Makrosvěta – tvůj tým,
 * výměna, trenéři arény a duchové hráčů. Souboj se otevře na místě arény, po něm se sem vrátíš.
 */
class ArenaFragment : Fragment() {

    private lateinit var ui: WoodUi
    private lateinit var stage: ArenaStageView
    private lateinit var body: LinearLayout
    private var myTeam: List<TrainerMon> = emptyList()
    /** Soupeři pro rychlý zápas: trenéři arény + načtení duchové. */
    private var quickPool: List<Trainer> = emptyList()
    private val spriteCache = HashMap<String, Bitmap?>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        ui = WoodUi(ctx)
        val root = FrameLayout(ctx).apply { isClickable = true; setBackgroundColor(Color.BLACK) }
        stage = ArenaStageView(ctx)
        root.addView(stage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // nadpis na obloze a zavření
        val head = ui.column().apply { gravity = Gravity.CENTER_HORIZONTAL }
        head.addView(ui.text("ARÉNA", 54f, ui.cream, Gravity.CENTER).apply { setShadowLayer(0.01f, 3 * ui.dp, 3 * ui.dp, 0xFF3B2A1A.toInt()) })
        head.addView(ui.text("Souboje trenérů", 18f, 0xFFFFF2C8.toInt(), Gravity.CENTER).apply { setShadowLayer(0.01f, 2 * ui.dp, 2 * ui.dp, 0xFF3B2A1A.toInt()) })
        root.addView(head, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP)
            .apply { topMargin = ui.px(44f) })
        root.addView(ImageView(ctx).apply {
            background = WoodPanelDrawable(1.5f * ui.dp, parchment = false)
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(ui.cream)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(ui.px(11f), ui.px(11f), ui.px(11f), ui.px(11f))
            contentDescription = "Zavřít"
            setOnClickListener { parentFragmentManager.popBackStack() }
        }, FrameLayout.LayoutParams(ui.px(48f), ui.px(48f), Gravity.TOP or Gravity.END).apply { topMargin = ui.px(44f); rightMargin = ui.px(16f) })

        // dřevěný panel dole – překrývá spodní okraj písku
        val panel = ui.column().apply {
            background = WoodPanelDrawable(3f * ui.dp)
            isClickable = true
            setPadding(ui.px(16f), ui.px(14f), ui.px(16f), ui.px(8f))
        }
        body = ui.column()
        panel.addView(ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; addView(body) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val panelLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            leftMargin = ui.px(10f); rightMargin = ui.px(10f); bottomMargin = ui.px(10f)
        }
        root.addView(panel, panelLp)
        root.post { panelLp.topMargin = (root.height * 0.44f).toInt(); panel.layoutParams = panelLp }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { load(publish = savedInstanceState == null) }

    // ── Data ──

    /** Tým v pořadí jako v souboji: aktivní parťák, pak ostatní členové. */
    private fun loadTeam(): List<CapturedMakromonEntity> {
        val ctx = requireContext().applicationContext
        val dao = AppDatabase.getDatabase(ctx).capturedMakromonDao()
        val ids = (listOfNotNull(SkillStore.activeId(ctx)) + SkillStore.team(ctx)).distinct()
        return ids.mapNotNull { dao.getMakromonById(it) }.take(Trainers.MAX_TEAM)
    }

    private fun load(publish: Boolean) {
        if (!isAdded || view == null) return
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val user = FirebaseRepository.currentUser
            val mons = withContext(Dispatchers.IO) { loadTeam() }
            val me = Arena.snapshot(user?.uid ?: "local", Arena.arenaName(ctx, user?.displayName), mons, System.currentTimeMillis())
            myTeam = me.team
            val ai = Arena.aiTrainers(me.team, LocalDate.now().toEpochDay())
            // vlevo se střídá celý tvůj tým, vpravo vedoucí Makromoni možných soupeřů rychlého zápasu
            stage.setFighters(sprites(me.team), sprites(ai.mapNotNull { it.team.firstOrNull() }))

            body.removeAllViews()
            renderMe(me)
            quickPool = ai
            renderModes(me, user != null)
            val tradeBox = ui.column(); body.addView(tradeBox)
            renderTrade(tradeBox, user?.uid)
            section("Trénink s trenéry arény", "Mění se každý den a rostou s tebou.")
            ai.forEach { body.addView(trainerCard(it)) }
            ArenaUi.cascade(body, ui.dp)
            section("Duchové hráčů", null)
            val ghostBox = ui.column(); body.addView(ghostBox)

            if (user == null) {
                ghostBox.addView(note("Přihlas se a tvůj tým začne hájit arénu proti ostatním hráčům."))
                return@launch
            }
            ghostBox.addView(note("Načítám duchy…"))
            val before = Ranked.points(ctx)
            val ghosts = withContext(Dispatchers.IO) {
                runCatching {
                    if (publish && me.team.isNotEmpty()) FirebaseRepository.publishArenaGhost(me)
                    runCatching { Ranked.syncFromCloud(ctx, FirebaseRepository.myArenaPoints()) }
                    FirebaseRepository.fetchArenaGhosts()
                }
            }
            if (Ranked.points(ctx) != before) { load(false); return@launch }   // body z jiného telefonu
            if (!isAdded) return@launch
            ghostBox.removeAllViews()
            ghosts.onSuccess { list ->
                val picked = Arena.pickGhosts(list, user.uid, me.power)
                quickPool = ai + picked
                if (picked.isEmpty()) ghostBox.addView(note("Zatím tu nejsou žádní další duchové. Pozvi kamaráda!"))
                picked.forEach { ghostBox.addView(trainerCard(it)) }
            }.onFailure { ghostBox.addView(note("Duchy se nepodařilo načíst. Zkus to později.")) }
        }
    }

    // ── Panel ──

    private var myPower = 1

    /** Karta tvého týmu: erb ranku, jméno, body s postupem, pořadí a tým (docs/adr/0080). */
    private fun renderMe(me: Trainer) {
        val ctx = requireContext()
        myPower = me.power.coerceAtLeast(1)
        val points = Ranked.points(ctx)
        val tier = Ranked.tierOf(points)
        val rec = Arena.record(ctx)
        val card = ui.column().apply { background = ArenaUi.plaque(ui.dp); setPadding(ui.px(14f), ui.px(12f), ui.px(14f), ui.px(12f)) }
        val top = ui.row()
        top.addView(ui.text(tier.emoji, 30f, ui.cream, Gravity.CENTER).apply {
            background = ArenaUi.Emblem(tier.color, ui.dp); setPadding(0, 0, 0, ui.px(6f))
        }, LinearLayout.LayoutParams(ui.px(66f), ui.px(74f)))
        val col = ui.column().apply { setPadding(ui.px(12f), 0, 0, 0) }
        col.addView(ui.text("${me.name} ✎", 28f, ui.cream).apply { setOnClickListener { editName() }; maxLines = 1 })
        val tierRow = ui.row()
        tierRow.addView(ui.text(tier.label.uppercase(), 20f, tier.color).apply { setShadowLayer(0.01f, ui.dp, ui.dp, 0xFF000000.toInt()) },
            ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tierRow.addView(ui.text("$points b.", 20f, ui.cream))
        col.addView(tierRow)
        // pruh postupu: tmavá drážka, výplň v barvě ranku
        val prog = Ranked.progress(points)
        col.addView(LinearLayout(ctx).apply {
            background = ArenaUi.pill(0xFF1A120B.toInt(), ui.dp)
            setPadding(ui.px(2f), ui.px(2f), ui.px(2f), ui.px(2f))
            addView(View(ctx).apply { background = ArenaUi.pill(tier.color, ui.dp) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, prog.coerceAtLeast(0.03f)))
            addView(View(ctx), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (1f - prog).coerceAtLeast(0f)))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.px(12f)).apply { topMargin = ui.px(4f) })
        val info = ui.text(tier.next?.let { "Do ranku ${it.label}: ${it.min - points} b." } ?: "Nejvyšší rank!", 14f, 0xFFE8CFA0.toInt())
        col.addView(info.apply { setPadding(0, ui.px(3f), 0, 0) })
        top.addView(col, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(top)
        // statistiky
        val stats = ui.row().apply { setPadding(0, ui.px(10f), 0, ui.px(8f)) }
        fun stat(v: String, l: String) = ui.column().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(ui.text(v, 22f, ui.cream, Gravity.CENTER)); addView(ui.text(l, 13f, 0xFFC9A26B.toInt(), Gravity.CENTER))
        }
        val placeStat = stat("–", "POŘADÍ")
        listOf(stat("⚔ ${me.power}", "SÍLA"), stat("${rec.wins}", "VÝHRY"), stat("${rec.losses}", "PROHRY"), placeStat)
            .forEach { stats.addView(it, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
        card.addView(stats)
        card.addView(teamRow(me.team, 46f))
        val status = when {
            me.team.isEmpty() -> "Nemáš tým – nejdřív chyť Makromona."
            FirebaseRepository.currentUser == null -> "Offline: bojuješ jen s trenéry arény."
            else -> "👻 Tvůj duch hájí arénu, i když nehraješ."
        }
        card.addView(ui.text(status, 14f, 0xFFE8CFA0.toInt()).apply { setPadding(0, ui.px(6f), 0, 0) })
        body.addView(card)
        if (FirebaseRepository.currentUser != null) viewLifecycleOwner.lifecycleScope.launch {
            val place = withContext(Dispatchers.IO) { runCatching { FirebaseRepository.arenaPlace(points) }.getOrNull() }
            if (place != null && isAdded) (placeStat.getChildAt(0) as android.widget.TextView).text = "#$place"
        }
    }

    /** Dvě velké dlaždice režimů a tlačítko žebříčku. */
    private fun renderModes(me: Trainer, loggedIn: Boolean) {
        val ctx = requireContext()
        val points = Ranked.points(ctx)
        val tier = Ranked.tierOf(points)
        val rankedOpp = Ranked.opponent(points, me.team, Ranked.matches(ctx))
        val hasTeam = me.team.isNotEmpty()
        fun mode(icon: String, title: String, sub: String, chip: String, top: Int, bottom: Int, enabled: Boolean, go: () -> Unit) = ui.column().apply {
            background = ArenaUi.tile(top, bottom, ui.dp)
            setPadding(ui.px(12f), ui.px(10f), ui.px(12f), ui.px(12f))
            alpha = if (enabled) 1f else 0.5f
            addView(ui.text(icon, 32f, ui.cream))
            addView(ui.text(title, 22f, ui.cream).apply { setShadowLayer(0.01f, ui.dp * 1.5f, ui.dp * 1.5f, 0x99000000.toInt()) })
            addView(ui.text(sub, 13f, 0xE6FEFAE0.toInt()).apply { minLines = 3; setPadding(0, ui.px(2f), 0, ui.px(8f)) })
            addView(ui.text(chip, 16f, 0xFF2A1D12.toInt(), Gravity.CENTER).apply {
                background = ArenaUi.pill(0xFFFEFAE0.toInt(), ui.dp); setPadding(ui.px(10f), ui.px(4f), ui.px(10f), ui.px(5f))
            })
            if (enabled) setOnClickListener { go() }
        }
        val modes = ui.row()
        modes.addView(mode("⚡", "RYCHLÝ ZÁPAS", "Náhodný soupeř.\nO body se nehraje.", "HRÁT ▶",
            0xFF6E9E3A.toInt(), 0xFF3D5A1E.toInt(), hasTeam) { quickPool.randomOrNull()?.let { challenge(it) } },
            ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        modes.addView(View(ctx), LinearLayout.LayoutParams(ui.px(10f), 1))
        val rankedSub = if (!loggedIn) "Jen s přihlášeným účtem."
            else "${rankedOpp.name}\n${rankedOpp.team.size}× Lv ${rankedOpp.team.first().level}\n+${Ranked.WIN} / −${Ranked.LOSS} b."
        val ranked = mode("🏆", "HODNOCENÝ", rankedSub, "${tier.emoji} HRÁT ▶", tier.color, ArenaUi.darker(tier.color, 0.5f), loggedIn && hasTeam) {
            stage.setRight(sprites(rankedOpp.team)); challenge(rankedOpp)
        }
        modes.addView(ranked, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        body.addView(modes, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(12f) })
        if (loggedIn && hasTeam) ArenaUi.pulse(ranked)
        if (loggedIn) stage.setRight(sprites(rankedOpp.team))
        body.addView(ui.row().apply {
            background = ArenaUi.plaque(ui.dp)
            setPadding(ui.px(14f), ui.px(10f), ui.px(14f), ui.px(10f))
            alpha = if (loggedIn) 1f else 0.5f
            addView(ui.text("📜", 24f, ui.cream).apply { setPadding(0, 0, ui.px(10f), 0) })
            addView(ui.text("ŽEBŘÍČEK HRÁČŮ", 22f, ui.cream), ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ui.text("›", 28f, 0xFFC9A26B.toInt()))
            if (loggedIn) setOnClickListener { showLeaderboard(points) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(10f) })
    }

    private fun showLeaderboard(myPoints: Int) {
        val root = view as? FrameLayout ?: return
        val me = FirebaseRepository.currentUser?.uid
        WorkshopMenus.show(root, "Žebříček", "Nejlepší trenéři arény") { w, b, _ ->
            b.addView(w.text("Načítám…", 16f, w.inkSoft))
            viewLifecycleOwner.lifecycleScope.launch {
                val res = withContext(Dispatchers.IO) {
                    runCatching { FirebaseRepository.arenaLeaderboard() to runCatching { FirebaseRepository.arenaPlace(myPoints) }.getOrNull() }
                }
                b.removeAllViews()
                res.onSuccess { (list, place) ->
                    if (list.isEmpty()) b.addView(w.text("Zatím nikdo nehrál hodnocený zápas. Buď první!", 16f, w.inkSoft))
                    list.forEachIndexed { i, t ->
                        val tier = Ranked.tierOf(t.points)
                        val row = WorkshopMenus.card(w)
                        if (t.id == me) row.background = BevelDrawable.slot(1.5f * w.dp)
                        row.addView(w.text("#${i + 1}", 20f, w.inkSoft).apply { minWidth = w.px(40f) })
                        row.addView(w.text(tier.emoji, 20f).apply { setPadding(0, 0, w.px(6f), 0) })
                        row.addView(w.text(t.name + if (t.id == me) " (ty)" else "", 20f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
                            w.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        row.addView(w.text("${t.points} b.", 18f, w.cream, Gravity.CENTER).apply {
                            // body na pilulce v barvě ranku – na pergamenu jsou čitelné i zlaté
                            background = ArenaUi.pill(ArenaUi.darker(tier.color, 0.8f), w.dp)
                            setPadding(w.px(10f), w.px(2f), w.px(10f), w.px(3f))
                        })
                        b.addView(row)
                    }
                    if (place != null && list.none { it.id == me }) b.addView(w.text("Ty: #$place · $myPoints b.", 18f, w.ink).apply { setPadding(0, w.px(6f), 0, 0) })
                }.onFailure { b.addView(w.text("Žebříček se nepodařilo načíst.", 16f, w.inkSoft)) }
            }
        }
    }

    /** Nadpis sekce jako stuha. */
    private fun section(title: String, sub: String?) {
        body.addView(ui.text(title.uppercase(), 20f, ui.cream).apply {
            background = ArenaUi.ribbon(ui.rust, ui.dp)
            setPadding(ui.px(12f), ui.px(4f), ui.px(22f), ui.px(5f))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(18f) })
        if (sub != null) body.addView(ui.text(sub, 14f, ui.inkSoft).apply { setPadding(ui.px(2f), ui.px(4f), 0, ui.px(4f)) })
        else body.addView(ui.spacer(6f))
    }

    private fun note(s: String) = ui.text(s, 15f, ui.inkSoft).apply { setPadding(0, ui.px(2f), 0, ui.px(8f)) }

    /** Karta soupeře: vedoucí Makromon v kruhu, štítek, obtížnost hvězdami, tým a tlačítko. */
    private fun trainerCard(t: Trainer): View {
        val ghost = t.kind == Trainer.Kind.GHOST
        val accent = if (ghost) 0xFF6B4A8A.toInt() else ui.rust
        val card = WorkshopMenus.card(ui).apply { setPadding(ui.px(10f), ui.px(10f), ui.px(10f), ui.px(10f)) }
        card.addView(ImageView(requireContext()).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor((accent and 0x00FFFFFF) or 0x33000000); setStroke(ui.px(2f), accent) }
            val p = ui.px(6f); setPadding(p, p, p, p)
            setImageBitmap(t.team.firstOrNull()?.let(::sprite)); scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(ui.px(58f), ui.px(58f)))
        val info = ui.column().apply { setPadding(ui.px(10f), 0, ui.px(8f), 0) }
        val head = ui.row()
        head.addView(ui.text(if (ghost) "👻 DUCH" else "TRENÉR", 12f, ui.cream).apply {
            background = ArenaUi.pill(accent, ui.dp); setPadding(ui.px(8f), ui.px(2f), ui.px(8f), ui.px(3f))
        })
        // obtížnost podle síly vůči tobě
        val ratio = t.power.toFloat() / myPower
        val stars = when { ratio < 0.8f -> "★☆☆"; ratio < 1.2f -> "★★☆"; else -> "★★★" }
        head.addView(ui.text("  $stars", 16f, 0xFFD9A322.toInt()))
        info.addView(head)
        info.addView(ui.text(t.name, 24f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        info.addView(ui.text("⚔ ${t.power} · ${Arena.coinsForWin(t)} 🪙", 14f, ui.inkSoft))
        info.addView(teamRow(t.team, 26f).apply { setPadding(0, ui.px(3f), 0, 0) })
        card.addView(info, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(ui.text("VYZVAT", 17f, ui.cream, Gravity.CENTER).apply {
            background = ArenaUi.tile(accent, ArenaUi.darker(accent, 0.7f), ui.dp, 8f)
            setPadding(ui.px(12f), ui.px(9f), ui.px(12f), ui.px(10f))
            setOnClickListener { challenge(t) }
        })
        card.setOnClickListener { stage.setRight(sprites(t.team)) }
        return card
    }

    /** Řada spritů v dlaždicích, pod každým level. */
    private fun teamRow(team: List<TrainerMon>, sizeDp: Float): LinearLayout {
        val row = ui.row()
        team.forEach { m ->
            val cell = ui.column().apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, 0, ui.px(6f), 0) }
            cell.addView(ImageView(requireContext()).apply {
                background = BevelDrawable.slot(1.5f * ui.dp)
                val p = ui.px(sizeDp * 0.1f)
                setPadding(p, p, p, p)
                setImageBitmap(sprite(m))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(ui.px(sizeDp), ui.px(sizeDp)))
            cell.addView(ui.text("Lv ${m.level}", 13f, ui.inkSoft, Gravity.CENTER))
            row.addView(cell)
        }
        return row
    }

    private fun sprites(team: List<TrainerMon>): List<Bitmap> = team.mapNotNull(::sprite)

    private fun sprite(m: TrainerMon): Bitmap? = spriteCache.getOrPut("${m.speciesId}${m.shiny}") {
        val sp = SpeciesRegistry.byId(m.speciesId) ?: return@getOrPut null
        val res = resources.getIdentifier(sp.sprite, "drawable", requireContext().packageName)
        if (res == 0) return@getOrPut null
        val plain = (resources.getDrawable(res, null) as? BitmapDrawable)?.bitmap ?: return@getOrPut null
        if (m.shiny) cz.uhk.macroflow.pokemon.shiny.ShinySprites.recolor(plain, sp.id) else plain
    }

    // ── Výměna (docs/adr/0077) ──

    private fun renderTrade(box: LinearLayout, uid: String?) {
        box.addView(ui.text("Výměna s kamarádem", 24f).apply { setPadding(0, ui.px(14f), 0, 0) })
        box.addView(ui.text("Jeden nabídne a ukáže kód, druhý ho opíše a nabídne svého.", 14f, ui.inkSoft).apply { setPadding(0, 0, 0, ui.px(6f)) })
        val buttons = ui.row()
        buttons.addView(ui.button("Nabídnout") { TradeSheet(this) { load(false) }.startOffer() }, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(View(requireContext()), LinearLayout.LayoutParams(ui.px(8f), 1))
        buttons.addView(ui.button("Mám kód") { TradeSheet(this) { load(false) }.startJoin() }, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(buttons)
        if (uid != null) viewLifecycleOwner.lifecycleScope.launch { resumeTrade(box, uid) }
    }

    /** Rozpracovaná výměna: dokončí potvrzenou, uklidí zrušenou, jinak nabídne pokračování. */
    private suspend fun resumeTrade(box: LinearLayout, uid: String) {
        val ctx = requireContext().applicationContext
        val code = TradeStore.pendingCode(ctx) ?: return
        val trade = withContext(Dispatchers.IO) { runCatching { FirebaseRepository.getTrade(code) }.getOrNull() }
        if (!isAdded) return
        val phase = trade?.let { Trading.phase(it) }
        when {
            trade == null || phase == Trading.Phase.CANCELLED -> TradeStore.clearPending(ctx)
            phase == Trading.Phase.DONE -> {
                val got = withContext(Dispatchers.IO) { runCatching { TradeStore.applyIfDone(ctx, trade, uid) }.getOrNull() }
                got?.let { Toast.makeText(ctx, "Výměna dokončena – ${it.name} je tvůj!", Toast.LENGTH_LONG).show() }
            }
            else -> box.addView(ui.button("Pokračovat ve výměně ${Trading.pretty(code)}") {
                TradeSheet(this) { load(false) }.resume(code)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(8f) })
        }
    }

    // ── Akce ──

    private fun challenge(t: Trainer) {
        if (myTeam.isEmpty()) {
            Toast.makeText(requireContext(), "Nejdřív si chyť Makromona.", Toast.LENGTH_SHORT).show()
            return
        }
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.mapFragmentContainer, PokemonBattleFragment.forTrainer(t))
            .addToBackStack(null)
            .commit()
    }

    private fun editName() {
        val root = view as? FrameLayout ?: return
        WorkshopMenus.show(root, "Jméno v aréně", "Tohle jméno uvidí ostatní hráči u tvého ducha.") { w, b, close ->
            val input = EditText(requireContext()).apply {
                setText(Arena.arenaName(requireContext(), FirebaseRepository.currentUser?.displayName))
                filters = arrayOf(InputFilter.LengthFilter(Trainers.NAME_MAX))
                setSingleLine(); textSize = 22f; typeface = w.font; setTextColor(w.ink)
            }
            b.addView(input)
            b.addView(w.button("Uložit") {
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) { Arena.setArenaName(requireContext(), n); close(); load(publish = true) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = w.px(8f) })
        }
    }
}

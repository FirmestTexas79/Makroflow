package cz.uhk.macroflow.pokemon.trainer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
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
        root.addView(ui.text("✕", 28f, ui.cream, Gravity.CENTER).apply {
            background = WoodPanelDrawable(1.5f * ui.dp, parchment = false)
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
            stage.setFighters(me.team.firstOrNull()?.let(::sprite), ai.getOrNull(1)?.team?.firstOrNull()?.let(::sprite))

            body.removeAllViews()
            renderMe(me)
            quickPool = ai
            renderModes(me, user != null)
            val tradeBox = ui.column(); body.addView(tradeBox)
            renderTrade(tradeBox, user?.uid)
            section("Trénink s trenéry arény", "Mění se každý den a rostou s tebou.")
            ai.forEach { body.addView(trainerCard(it)) }
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

    private fun renderMe(me: Trainer) {
        val top = ui.row()
        val col = ui.column()
        col.addView(ui.text("TVŮJ TÝM", 15f, ui.rust))
        col.addView(ui.text("${me.name} ✎", 30f).apply { setOnClickListener { editName() } })
        top.addView(col, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val rec = Arena.record(requireContext())
        val stats = ui.column().apply { gravity = Gravity.END }
        stats.addView(ui.text("⚔ ${me.power}", 30f, ui.ink, Gravity.END))
        stats.addView(ui.text("${rec.wins} výher · ${rec.losses} proher", 15f, ui.inkSoft, Gravity.END))
        top.addView(stats)
        body.addView(top)
        body.addView(teamRow(me.team, 52f).apply { setPadding(0, ui.px(8f), 0, ui.px(4f)) })
        val status = when {
            me.team.isEmpty() -> "Nemáš tým – nejdřív chyť Makromona."
            FirebaseRepository.currentUser == null -> "Offline: bojuješ jen s trenéry arény."
            else -> "👻 Tvůj duch hájí arénu, i když nehraješ."
        }
        body.addView(ui.text(status, 15f, ui.olive).apply { setPadding(0, ui.px(2f), 0, ui.px(4f)) })
    }

    // ── Rank (docs/adr/0079) ──

    private fun renderModes(me: Trainer, loggedIn: Boolean) {
        val ctx = requireContext()
        val points = Ranked.points(ctx)
        val tier = Ranked.tierOf(points)

        // odznak ranku s postupem
        val badge = WorkshopMenus.card(ui).apply { setPadding(ui.px(12f), ui.px(10f), ui.px(12f), ui.px(10f)) }
        badge.addView(ui.text(tier.emoji, 34f).apply { setPadding(0, 0, ui.px(10f), 0) })
        val col = ui.column()
        val head = ui.row()
        head.addView(ui.text(tier.label, 28f, tier.color), ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(ui.text("$points b.", 24f, ui.ink))
        col.addView(head)
        // pruh postupu k dalšímu ranku
        val prog = Ranked.progress(points)
        col.addView(LinearLayout(ctx).apply {
            background = BevelDrawable.slot(1f * ui.dp)
            setPadding(ui.px(2f), ui.px(2f), ui.px(2f), ui.px(2f))
            addView(View(ctx).apply { setBackgroundColor(tier.color) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, prog))
            addView(View(ctx), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - prog))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.px(14f)).apply { topMargin = ui.px(4f); bottomMargin = ui.px(2f) })
        val placeTv = ui.text(tier.next?.let { "Do ranku ${it.label}: ${it.min - points} b." } ?: "Nejvyšší rank – drž se na vrcholu!", 14f, ui.inkSoft)
        col.addView(placeTv)
        badge.addView(col, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        body.addView(badge)
        if (loggedIn) viewLifecycleOwner.lifecycleScope.launch {
            val place = withContext(Dispatchers.IO) { runCatching { FirebaseRepository.arenaPlace(points) }.getOrNull() }
            if (place != null && isAdded) placeTv.text = "${placeTv.text}  ·  #$place v žebříčku"
        }

        // dva režimy
        val rankedOpp = Ranked.opponent(points, me.team, Ranked.matches(ctx))
        val modes = ui.row()
        fun mode(title: String, sub: String, label: String, enabled: Boolean, onPreview: (() -> Unit)?, go: () -> Unit) = ui.column().apply {
            background = WoodPanelDrawable(1.5f * ui.dp)
            setPadding(ui.px(12f), ui.px(10f), ui.px(12f), ui.px(10f))
            addView(ui.text(title, 22f))
            addView(ui.text(sub, 13f, ui.inkSoft).apply { setPadding(0, ui.px(2f), 0, ui.px(8f)); minLines = 3 })
            addView(ui.button(label, enabled) { go() })
            onPreview?.let { p -> setOnClickListener { p() } }
        }
        modes.addView(mode("⚡ Rychlý", "Náhodný soupeř, o body se nehraje.", "Hrát", me.team.isNotEmpty(), null) {
            quickPool.randomOrNull()?.let { challenge(it) }
        }, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        modes.addView(View(ctx), LinearLayout.LayoutParams(ui.px(8f), 1))
        val rankedSub = if (!loggedIn) "Hodnocené zápasy jen s přihlášeným účtem."
            else "${rankedOpp.name} · ${rankedOpp.team.size}× Lv ${rankedOpp.team.first().level}\nvýhra +${Ranked.WIN} · prohra −${Ranked.LOSS}"
        modes.addView(mode("🏆 Hodnocený", rankedSub, "Hrát", loggedIn && me.team.isNotEmpty(),
            { stage.setRight(rankedOpp.team.firstOrNull()?.let(::sprite)) }) { challenge(rankedOpp) },
            ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        body.addView(modes, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(4f) })
        body.addView(ui.button("📜 Žebříček hráčů", loggedIn) { showLeaderboard(points) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(8f) })
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
                        row.addView(w.text("${t.points} b.", 20f, tier.color))
                        b.addView(row)
                    }
                    if (place != null && list.none { it.id == me }) b.addView(w.text("Ty: #$place · $myPoints b.", 18f, w.ink).apply { setPadding(0, w.px(6f), 0, 0) })
                }.onFailure { b.addView(w.text("Žebříček se nepodařilo načíst.", 16f, w.inkSoft)) }
            }
        }
    }

    private fun section(title: String, sub: String?) {
        body.addView(ui.text(title, 24f).apply { setPadding(0, ui.px(14f), 0, 0) })
        if (sub != null) body.addView(ui.text(sub, 14f, ui.inkSoft).apply { setPadding(0, 0, 0, ui.px(6f)) })
        else body.addView(ui.spacer(6f))
    }

    private fun note(s: String) = ui.text(s, 15f, ui.inkSoft).apply { setPadding(0, ui.px(2f), 0, ui.px(8f)) }

    private fun trainerCard(t: Trainer): View {
        val card = WorkshopMenus.card(ui)
        val info = ui.column().apply { setPadding(0, 0, ui.px(8f), 0) }
        info.addView(ui.text(if (t.kind == Trainer.Kind.GHOST) "👻 DUCH HRÁČE" else "TRENÉR ARÉNY", 13f, ui.rust))
        info.addView(ui.text(t.name, 24f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        info.addView(ui.text("⚔ ${t.power} · odměna ${Arena.coinsForWin(t)} 🪙", 14f, ui.inkSoft))
        info.addView(teamRow(t.team, 30f).apply { setPadding(0, ui.px(4f), 0, 0) })
        card.addView(info, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(ui.button("Vyzvat") { challenge(t) })
        // klepnutí na kartu: soupeř nastoupí na písek naproti tvému parťákovi
        card.setOnClickListener { stage.setRight(t.team.firstOrNull()?.let(::sprite)) }
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

package cz.uhk.macroflow.pokemon

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.pokemon.evolution.EvolutionStageView
import cz.uhk.macroflow.pokemon.evolution.MoveLearning
import cz.uhk.macroflow.pokemon.skills.ui.BevelDrawable
import cz.uhk.macroflow.pokemon.skills.ui.WoodPanelDrawable
import cz.uhk.macroflow.pokemon.skills.ui.WoodUi
import cz.uhk.macroflow.pokemon.wild.MoveDex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Evoluce Makromona (docs/adr/0064): celoobrazovková scéna ([EvolutionStageView]) s dřevěnými
 * cedulemi jako zbytek hry. Postup:
 * 1. starý Makromon se rozzáří, bliká mezi starou a novou formou, záblesk, nová forma;
 * 2. gratulace a porovnání statistik (staré → nové na stejném levelu);
 * 3. nový útok na kartě – hráč se rozhodne, jestli ho chce naučit;
 * 4. má-li už 4 útoky, vybere, který zapomene (nebo si to rozmyslí).
 * Nic se nezavře samo – až tlačítkem Hotovo.
 */
class EvolutionDialog(
    context: Context,
    private val capturedMakromonId: Int,
    private val oldId: String,
    private val newId: String,
    private val newMoveToLearn: Move?,
    /** Náhled (ladění): nic se neukládá, chybí-li Makromon, použije se vymyšlený se 4 útoky. */
    private val preview: Boolean = false,
    private val onComplete: () -> Unit
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main + kotlinx.coroutines.Job())
    private val db = AppDatabase.getDatabase(context)
    private val handler = Handler(Looper.getMainLooper())
    private val ui = WoodUi(context)

    private lateinit var stage: EvolutionStageView
    private lateinit var namesText: TextView
    private lateinit var panel: LinearLayout

    private lateinit var mon: CapturedMakromonEntity
    private var oldEntry: MakrodexEntryEntity? = null
    private var newEntry: MakrodexEntryEntity? = null
    private val oldName get() = oldEntry?.displayName ?: "Makromon"
    private val newName get() = newEntry?.displayName ?: "nová forma"

    private val WHITE = Color.parseColor("#FEFAE0")
    private val GOLD = Color.parseColor("#FFD54F")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(false)
        window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(build())
        load()
    }

    // ── kostra: scéna, cedule nahoře, dřevěný panel dole ───────────────────

    private fun build(): View {
        val root = FrameLayout(context)
        stage = EvolutionStageView(context)
        root.addView(stage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val plaque = ui.column().apply {
            background = WoodPanelDrawable(2.5f * ui.dp, parchment = false)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ui.px(22f), ui.px(10f), ui.px(22f), ui.px(12f))
        }
        plaque.addView(outlined(ui.text("✦ EVOLUCE ✦", 15f, GOLD, Gravity.CENTER)))
        namesText = outlined(ui.text("", 26f, WHITE, Gravity.CENTER))
        plaque.addView(namesText)
        root.addView(plaque, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = ui.px(44f) })

        panel = ui.column().apply {
            background = WoodPanelDrawable(2.5f * ui.dp)
            setPadding(ui.px(18f), ui.px(16f), ui.px(18f), ui.px(16f))
        }
        root.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM).apply { setMargins(ui.px(12f), 0, ui.px(12f), ui.px(26f)) })
        return root
    }

    private fun outlined(t: TextView): TextView = t.apply { setShadowLayer(3f, 0f, 1.5f, Color.parseColor("#101828")) }

    /** Vymění obsah dolního panelu s krátkým přechodem. */
    private fun show(build: (LinearLayout) -> Unit) {
        panel.animate().cancel()
        panel.animate().alpha(0f).translationY(ui.px(10f).toFloat()).setDuration(120).withEndAction {
            panel.removeAllViews()
            build(panel)
            panel.animate().alpha(1f).translationY(0f).setInterpolator(DecelerateInterpolator()).setDuration(220).start()
        }.start()
    }

    /** Text, který se vypisuje po písmenech jako v dialozích postav. */
    private fun typed(text: String, size: Float = 19f, color: Int = ui.ink): TextView {
        val tv = ui.text("", size, color)
        var i = 0
        val step = object : Runnable {
            override fun run() {
                if (!tv.isAttachedToWindow && i > 0) return
                i = (i + 1).coerceAtMost(text.length)
                tv.text = text.substring(0, i)
                if (i < text.length) handler.postDelayed(this, 22)
            }
        }
        handler.postDelayed(step, 60)
        return tv
    }

    private fun wide(label: String, enabled: Boolean = true, onClick: () -> Unit): TextView =
        ui.button(label, enabled, onClick).apply {
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(8f) }
        }

    /** Červené tlačítko pro „ne“ (nenaučit, zpět). */
    private fun redButton(label: String, onClick: () -> Unit): TextView = outlined(ui.text(label, 18f, WHITE, Gravity.CENTER)).apply {
        background = BevelDrawable(1.5f * ui.dp, Color.parseColor("#8E3B2E"), Color.parseColor("#C25B48"), Color.parseColor("#5A2219"), Color.parseColor("#1E140C"))
        setPadding(ui.px(12f), ui.px(8f), ui.px(12f), ui.px(9f))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(8f) }
    }

    // ── data ───────────────────────────────────────────────────────────────

    private fun spriteRes(e: MakrodexEntryEntity?): Int {
        e ?: return 0
        val short = if (e.makrodexId.length >= 3) e.makrodexId.takeLast(2) else e.makrodexId
        return context.resources.getIdentifier("makromon_${short}_${e.displayName.lowercase().trim().replace(" ", "_")}", "drawable", context.packageName)
    }

    /** Sprite jako bitmapa (shiny se vyvíjí zase do shiny); bez spritu stín Makroballu. */
    private fun bitmap(e: MakrodexEntryEntity?, shiny: Boolean): Bitmap? {
        val res = spriteRes(e)
        if (res == 0) return cz.uhk.macroflow.pokemon.balls.BallSprites.icon(cz.uhk.macroflow.pokemon.balls.Makroball.entries.first(), ui.px(120f))
        if (shiny) cz.uhk.macroflow.pokemon.shiny.ShinySprites.bitmap(context, res, e!!.makrodexId)?.let { return it }
        return BitmapFactory.decodeResource(context.resources, res, BitmapFactory.Options().apply { inScaled = false })
    }

    private fun load() {
        scope.launch {
            try {
                val (m, o, n) = withContext(Dispatchers.IO) {
                    val m = if (preview && capturedMakromonId < 0) null else db.capturedMakromonDao().getMakromonById(capturedMakromonId)
                    Triple(m, m?.takeIf { !preview }?.let { db.makrodexEntryDao().getEntry(it.makromonId) } ?: db.makrodexEntryDao().getEntry(oldId),
                        db.makrodexEntryDao().getEntry(newId))
                }
                val mm = m ?: if (preview) CapturedMakromonEntity(id = -1, makromonId = oldId, name = o?.displayName?.uppercase() ?: oldId,
                    level = 12, moveListStr = "TACKLE,BITE,GROWL,WATER GUN") else null
                if (mm == null) { Log.e("EVO", "Makromon $capturedMakromonId nenalezen"); finish(); return@launch }
                mon = mm; oldEntry = o; newEntry = n
                stage.oldSprite = bitmap(o, mm.isShiny)
                stage.newSprite = bitmap(n, mm.isShiny)
                namesText.text = "$oldName  ➜  ???"
                stage.onReveal = { reveal() }
                show { p ->
                    p.addView(typed("Co se to děje? $oldName se rozzářil…"))
                    p.addView(ui.text("Nech ho dokončit proměnu.", 14f, ui.inkSoft).apply { setPadding(0, ui.px(6f), 0, 0) })
                }
                stage.begin()
            } catch (e: Exception) {
                Log.e("EVO", "Chyba při načítání: ${e.message}")
                finish()
            }
        }
    }

    // ── 1. odhalení: uložit evoluci, gratulace, statistiky ──────────────────

    private fun reveal() {
        namesText.text = "$oldName  ➜  $newName"
        namesText.scaleX = 1.25f; namesText.scaleY = 1.25f
        namesText.animate().scaleX(1f).scaleY(1f).setDuration(380).start()
        stage.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)

        // co uměl, zůstává – i když měl dosud jen výchozí útoky starého druhu
        val oldMoves = cz.uhk.macroflow.pokemon.wild.MovePool.resolve(mon.moveListStr, BattleFactory.createById(mon.makromonId).moves)
        mon.moveListStr = oldMoves.joinToString(",") { it.name }
        mon.makromonId = newId
        mon.name = newEntry?.displayName?.uppercase() ?: newId
        save { updateBar() }

        // stejné hodnoty jako v souboji: uložený level (nebo vyšší podle XP) a vzorec BattleEngine
        val level = maxOf(mon.level, PokemonLevelCalc.levelFromXp(mon.xp)).coerceAtLeast(1)
        val before = BattleEngine.initializeStatsForLevel(BattleFactory.createById(oldId), level)
        val after = BattleEngine.initializeStatsForLevel(BattleFactory.createById(newId), level)

        show { p ->
            p.addView(outlined(ui.text("Gratulace!", 26f, GOLD)))
            p.addView(typed("Tvůj $oldName se vyvinul. Teď je to $newName!"))
            p.addView(ui.text("Statistiky na Lv $level – stejné jako v souboji", 13f, ui.inkSoft).apply { setPadding(0, ui.px(6f), 0, 0) })
            // statistiky staré → nové
            val box = ui.column().apply {
                background = BevelDrawable(1.5f * ui.dp, Color.parseColor("#EAD6AE"), Color.parseColor("#F8EBCF"), Color.parseColor("#CDB083"), Color.parseColor("#9C7A4E"))
                setPadding(ui.px(10f), ui.px(8f), ui.px(10f), ui.px(8f))
            }
            listOf(Triple("Životy", before.maxHp, after.maxHp), Triple("Útok", before.attack, after.attack),
                Triple("Obrana", before.defense, after.defense), Triple("Rychlost", before.speed, after.speed)).forEach { (label, a, b) ->
                box.addView(statRow(label, a, b, maxOf(after.maxHp, after.attack, after.defense, after.speed, 1)))
            }
            p.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(4f) })
            val pending = newMoveToLearn?.takeIf { MoveLearning.situation(currentMoves(), it.name) != MoveLearning.Situation.ALREADY_KNOWN }
            if (pending != null) p.addView(wide("Dál – nový útok") { offerMove(pending) })
            else p.addView(wide("Hotovo") { finish() })
        }
    }

    /** Řádek statistiky: název, pixelový pruh (nová hodnota, přírůstek zeleně) a čísla. */
    private fun statRow(label: String, a: Int, b: Int, max: Int): View {
        val row = ui.row().apply { setPadding(0, ui.px(3f), 0, ui.px(3f)) }
        row.addView(ui.text(label, 15f, ui.inkSoft), LinearLayout.LayoutParams(ui.px(72f), ViewGroup.LayoutParams.WRAP_CONTENT))
        val track = FrameLayout(context).apply {
            background = BevelDrawable(1f * ui.dp, Color.parseColor("#2A1C11"), Color.parseColor("#1A110A"), Color.parseColor("#4F3016"))
        }
        val gain = View(context).apply { setBackgroundColor(Color.parseColor("#9CC45A")) }
        val base = View(context).apply { setBackgroundColor(Color.parseColor("#C9A26B")) }
        track.addView(gain, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(ui.px(2f), ui.px(2f), ui.px(2f), ui.px(2f)) })
        track.addView(base, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(ui.px(2f), ui.px(2f), ui.px(2f), ui.px(2f)) })
        row.addView(track, LinearLayout.LayoutParams(0, ui.px(12f), 1f))
        track.post {
            val inner = track.width - ui.px(4f)
            (base.layoutParams as FrameLayout.LayoutParams).width = (inner * a / max.toFloat()).toInt().coerceIn(0, inner)
            base.requestLayout()
            gain.pivotX = 0f; gain.scaleX = a / b.coerceAtLeast(1).toFloat()
            (gain.layoutParams as FrameLayout.LayoutParams).width = (inner * b / max.toFloat()).toInt().coerceIn(0, inner)
            gain.requestLayout()
            gain.animate().scaleX(1f).setStartDelay(250).setDuration(700).setInterpolator(DecelerateInterpolator()).start()
        }
        val diff = b - a
        row.addView(ui.text("$a → $b", 15f, ui.ink, Gravity.END).apply { setPadding(ui.px(8f), 0, 0, 0) },
            LinearLayout.LayoutParams(ui.px(70f), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(ui.text(if (diff > 0) "+$diff" else if (diff < 0) "$diff" else "", 15f,
            if (diff >= 0) Color.parseColor("#4E6B2A") else Color.parseColor("#8E3B2E"), Gravity.END),
            LinearLayout.LayoutParams(ui.px(34f), ViewGroup.LayoutParams.WRAP_CONTENT))
        return row
    }

    // ── 2. nový útok: naučit, nebo ne ───────────────────────────────────────

    private fun currentMoves(): List<String> = mon.moveListStr.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun offerMove(move: Move) {
        show { p ->
            p.addView(outlined(ui.text("Nový útok", 24f, GOLD)))
            p.addView(typed("$newName se může naučit ${move.name}. Chceš, aby se ho naučil?", 18f))
            p.addView(moveCard(move, highlight = true), cardLp())
            p.addView(wide("Naučit ${move.name}") {
                when (MoveLearning.situation(currentMoves(), move.name)) {
                    MoveLearning.Situation.FREE_SLOT -> {
                        mon.moveListStr = MoveLearning.learn(currentMoves(), move.name).joinToString(",")
                        save {}
                        done("$newName se naučil ${move.name}!", learned = move)
                    }
                    MoveLearning.Situation.MUST_REPLACE -> chooseForget(move, selected = null)
                    MoveLearning.Situation.ALREADY_KNOWN -> done("${move.name} už umí.", learned = null)
                }
            })
            p.addView(redButton("Nenaučit") { confirmSkip(move) })
        }
    }

    private fun confirmSkip(move: Move) {
        show { p ->
            p.addView(typed("Opravdu? Tuhle šanci ${move.name} naučit už nedostane.", 18f))
            p.addView(moveCard(move, highlight = false), cardLp())
            p.addView(redButton("Ano, nenaučit") { done("$newName se ${move.name} nenaučil.", learned = null) })
            p.addView(wide("Zpět") { offerMove(move) })
        }
    }

    // ── 3. plná sada: který útok zapomene ──────────────────────────────────

    private fun chooseForget(move: Move, selected: Int?) {
        val moves = currentMoves()
        show { p ->
            p.addView(outlined(ui.text("Plná sada útoků", 22f, GOLD)))
            p.addView(ui.text("$newName už umí ${MoveLearning.MAX_MOVES} útoky. Vyber, který zapomene, aby se naučil ${move.name}.", 16f, ui.ink).apply {
                setPadding(0, ui.px(2f), 0, ui.px(4f))
            })
            p.addView(ui.text("Nový", 13f, ui.olive))
            p.addView(moveCard(move, highlight = true, compact = true), cardLp(2f))
            p.addView(ui.text("Umí teď – klepni na ten, který zapomene", 13f, ui.inkSoft).apply { setPadding(0, ui.px(6f), 0, 0) })
            moves.forEachIndexed { i, name ->
                val m = MoveDex.get(name) ?: Move(name, MakromonType.NORMAL, 0, 100, 10)
                p.addView(moveCard(m, highlight = false, compact = true, forget = i == selected).apply {
                    setOnClickListener { chooseForget(move, if (selected == i) null else i) }
                }, cardLp(2f))
            }
            if (selected != null) p.addView(wide("Zapomenout ${moves[selected]} → ${move.name}") {
                val forgotten = moves[selected]
                mon.moveListStr = MoveLearning.replace(moves, selected, move.name).joinToString(",")
                save {}
                done("Raz, dva, tři… a hotovo! $newName zapomněl $forgotten a naučil se ${move.name}.", learned = move)
            })
            else p.addView(wide("Vyber útok k zapomenutí", enabled = false) {})
            p.addView(redButton("Zpět") { offerMove(move) })
        }
    }

    // ── 4. konec ───────────────────────────────────────────────────────────

    private fun done(message: String, learned: Move?) {
        show { p ->
            p.addView(outlined(ui.text(if (learned != null) "Naučeno!" else "Hotovo", 24f, GOLD)))
            p.addView(typed(message, 18f))
            p.addView(ui.text("Útoky", 13f, ui.inkSoft).apply { setPadding(0, ui.px(8f), 0, 0) })
            currentMoves().forEach { name ->
                val m = MoveDex.get(name) ?: Move(name, MakromonType.NORMAL, 0, 100, 10)
                p.addView(moveCard(m, highlight = learned?.name == name, compact = true), cardLp(2f))
            }
            p.addView(wide("Hotovo") { finish() })
        }
    }

    // ── karta útoku ────────────────────────────────────────────────────────

    private fun cardLp(top: Float = 8f) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.px(top) }

    private fun typeColor(t: MakromonType): Int = Color.parseColor(when (t) {
        MakromonType.NORMAL -> "#8C8270"; MakromonType.FIRE -> "#C8552E"; MakromonType.WATER -> "#2E6FB0"
        MakromonType.GRASS -> "#4E8A2A"; MakromonType.ELECTRIC -> "#C9A21A"; MakromonType.BUG -> "#7A8A1E"
        MakromonType.FLYING -> "#6A7FC0"; MakromonType.GHOST -> "#5A3F8A"; MakromonType.GROUND -> "#8A6A3A"
        MakromonType.PSYCHIC -> "#B0477A"; MakromonType.DRAGON -> "#4A3FA8"; MakromonType.POISON -> "#7A3B8A"
        MakromonType.FAIRY -> "#C06A9A"
    })

    /** Dřevěná karta útoku: typ, název, síla, přesnost, PP a efekt. Zapomínaný útok je přeškrtnutý. */
    private fun moveCard(m: Move, highlight: Boolean, compact: Boolean = false, forget: Boolean = false): View {
        val card = ui.column().apply {
            background = when {
                forget -> BevelDrawable(1.5f * ui.dp, Color.parseColor("#E6C2B6"), Color.parseColor("#F2DAD0"), Color.parseColor("#B88A7C"), Color.parseColor("#8E3B2E"), selected = true)
                highlight -> BevelDrawable(1.5f * ui.dp, Color.parseColor("#F4E6C6"), Color.parseColor("#FBF3DF"), Color.parseColor("#D9C29A"), Color.parseColor("#9C7A4E"), selected = true)
                else -> BevelDrawable(1.5f * ui.dp, Color.parseColor("#EAD6AE"), Color.parseColor("#F8EBCF"), Color.parseColor("#CDB083"), Color.parseColor("#9C7A4E"))
            }
            setPadding(ui.px(10f), ui.px(if (compact) 5f else 8f), ui.px(10f), ui.px(if (compact) 6f else 9f))
            isClickable = true
        }
        val top = ui.row()
        top.addView(outlined(ui.text(MoveLearning.typeLabel(m.type), 13f, WHITE, Gravity.CENTER)).apply {
            val c = typeColor(m.type)
            background = BevelDrawable(1.2f * ui.dp, c, cz.uhk.macroflow.pokemon.skills.ui.SkillTreeView.blend(c, Color.WHITE, 0.3f),
                cz.uhk.macroflow.pokemon.skills.ui.SkillTreeView.blend(c, Color.BLACK, 0.35f), Color.parseColor("#1E140C"))
            setPadding(ui.px(6f), ui.px(1f), ui.px(6f), ui.px(3f))
            minWidth = ui.px(64f)
        })
        top.addView(ui.text(m.name, if (compact) 17f else 21f, if (forget) Color.parseColor("#8E3B2E") else ui.ink).apply {
            setPadding(ui.px(8f), 0, 0, ui.px(1f))
            if (forget) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        }, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (forget) top.addView(ui.text("zapomene", 13f, Color.parseColor("#8E3B2E")))
        card.addView(top)
        val power = if (m.power > 0) "Síla ${m.power}" else "Bez síly"
        card.addView(ui.text("$power · Přesnost ${m.accuracy} % · PP ${m.maxPp}", 14f, ui.inkSoft).apply { setPadding(0, ui.px(3f), 0, 0) })
        val eff = MoveLearning.effectText(m)
        if (eff.isNotEmpty() && !compact) card.addView(ui.text(eff, 14f, ui.olive))
        else if (eff.isNotEmpty()) card.addView(ui.text(eff, 13f, ui.olive))
        return card
    }

    // ── uložení ────────────────────────────────────────────────────────────

    /** Uložení běží mimo dialog – dokončí se, i když hráč hned klepne na Hotovo. */
    private fun save(after: () -> Unit) {
        if (preview) return
        val snapshot = mon.copy()
        saveScope.launch {
            try {
                db.capturedMakromonDao().updateMakromon(snapshot)
                after()
                if (FirebaseRepository.isLoggedIn) runCatching { FirebaseRepository.uploadCapturedMakromon(snapshot) }
            } catch (e: Exception) { Log.e("EVO", "Chyba při ukládání: ${e.message}") }
        }
    }

    private companion object {
        val saveScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    }

    /** Parťák na liště: přepíše se jméno i druh, ať se hned ukáže nová forma. */
    private fun updateBar() {
        val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        val e = prefs.edit()
        if (prefs.getString("currentOnBarId", "") == oldId) e.putString("currentOnBarId", newId)
        if (prefs.getInt("currentOnBarCapturedId", -1) == mon.id) e.putString("currentOnBarName", mon.name)
        e.apply()
    }

    private fun finish() {
        dismiss()
        onComplete()
    }

    override fun dismiss() {
        handler.removeCallbacksAndMessages(null)
        super.dismiss()
        scope.cancel()
    }
}

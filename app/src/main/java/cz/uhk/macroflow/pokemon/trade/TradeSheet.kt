package cz.uhk.macroflow.pokemon.trade

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.ListenerRegistration
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.pokemon.CapturedMakromonEntity
import cz.uhk.macroflow.pokemon.species.SpeciesRegistry
import cz.uhk.macroflow.pokemon.trainer.Arena
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Průvodce výměnou (docs/adr/0077) ve spodním panelu:
 * nabídnout → ukázat kód → čekat na kamaráda → oba potvrdí → hotovo.
 * Nebo: zadat kód → vidět jeho nabídku → vybrat svou → oba potvrdí → hotovo.
 */
class TradeSheet(private val host: Fragment, private val onFinished: () -> Unit) {

    private val ctx = host.requireContext()
    private val app = ctx.applicationContext
    private val dp = ctx.resources.displayMetrics.density
    private val font: Typeface? = runCatching { ResourcesCompat.getFont(ctx, R.font.jersey_15) }.getOrNull()
    private val dialog = BottomSheetDialog(ctx)
    private val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(px(8), px(4), px(8), px(12)) }
    private var listener: ListenerRegistration? = null
    private var applying = false
    private val me get() = FirebaseRepository.currentUser?.uid

    // dřevěný styl jako ostatní menu Makrosvěta (docs/adr/0082)
    private val cream = 0xFFFEFAE0.toInt()
    private val dark = 0xFF3B2A1A.toInt()
    private val olive = 0xFF7A5C3E.toInt()
    private val rust = 0xFFBC6C25.toInt()

    init {
        dialog.setContentView(ScrollView(ctx).apply {
            background = cz.uhk.macroflow.pokemon.skills.ui.WoodPanelDrawable(3f * dp)
            isVerticalScrollBarEnabled = false
            addView(body)
        })
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.setOnDismissListener { listener?.remove(); listener = null; onFinished() }
    }

    private fun px(v: Int) = (v * dp).toInt()
    private val scope get() = host.viewLifecycleOwner.lifecycleScope
    private fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    private fun myName() = Arena.arenaName(app, FirebaseRepository.currentUser?.displayName)

    // ── Vstupy ──

    fun startOffer() { if (loggedIn()) pick("Co nabídneš?", "Kamarád pak uvidí tvou nabídku a připojí svou.") { create(it) }; dialog.show() }

    fun startJoin() { if (loggedIn()) enterCode(); dialog.show() }

    fun resume(code: String) { watch(code); dialog.show() }

    private fun loggedIn(): Boolean {
        if (me != null) return true
        screen("Výměna", "Výměny fungují jen s přihlášeným účtem – Makromon putuje přes cloud.")
        return false
    }

    // ── Obrazovky ──

    private fun screen(title: String, sub: String? = null) {
        body.removeAllViews()
        body.addView(text(title, 30f, dark, font))
        sub?.let { body.addView(text(it, 13f, olive).apply { setPadding(0, px(2), 0, px(10)) }) }
    }

    private fun pick(title: String, sub: String, onPick: (CapturedMakromonEntity) -> Unit) {
        screen(title, sub)
        scope.launch {
            val mons = withContext(Dispatchers.IO) { TradeStore.tradeable(app) }
            if (mons.isEmpty()) { body.addView(text("Nemáš koho nabídnout. Potřebuješ aspoň dva Makromony a zamčené vyměnit nejde.", 13f, rust)); return@launch }
            mons.sortedByDescending { it.level }.forEach { m ->
                body.addView(monCard(Trading.offerOf(m), null).apply {
                    isClickable = true; isFocusable = true
                    setOnClickListener { onPick(m) }
                })
            }
        }
    }

    private fun enterCode() {
        screen("Mám kód", "Opiš kód z kamarádova telefonu.")
        val input = EditText(ctx).apply {
            hint = "ABC DEF"; textSize = 30f; typeface = font; gravity = Gravity.CENTER; setTextColor(dark); setHintTextColor(0x667A5C3E)
            filters = arrayOf(InputFilter.LengthFilter(8), InputFilter.AllCaps())
            setSingleLine()
        }
        body.addView(input)
        body.addView(button("Najít výměnu", primary = true) {
            val code = Trading.normalizeCode(input.text.toString())
            if (code.length != Trading.CODE_LEN) { toast("Kód má ${Trading.CODE_LEN} znaků."); return@button }
            scope.launch {
                val t = withContext(Dispatchers.IO) { runCatching { FirebaseRepository.getTrade(code) }.getOrNull() }
                when {
                    t == null -> toast("Výměnu s tímhle kódem jsem nenašel.")
                    t.a == me -> resume(code)
                    Trading.phase(t) != Trading.Phase.OPEN || t.offerA == null -> toast("Tahle výměna už není volná.")
                    else -> join(t)
                }
            }
        })
    }

    private fun join(t: Trade) {
        pick("Co dáš za něj?", "${t.aName.ifBlank { "Kamarád" }} nabízí:") { m ->
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { FirebaseRepository.joinTrade(t.code, myName(), Trading.offerOf(m)); TradeStore.setPending(app, t.code, m.uid) }.isSuccess
                }
                if (ok) watch(t.code) else toast("Připojit se nepovedlo – možná ho mezitím někdo předběhl.")
            }
        }
        // nabídka hráče A nad seznamem
        body.addView(monCard(t.offerA!!, "NABÍZÍ"), 2)
        body.addView(text("Vyber, koho dáš ty:", 13f, olive).apply { setPadding(0, px(14), 0, 0) }, 3)
    }

    private fun create(m: CapturedMakromonEntity) {
        val uid = me ?: return
        scope.launch {
            val code = withContext(Dispatchers.IO) {
                runCatching {
                    repeat(4) {
                        val c = Trading.newCode()
                        if (FirebaseRepository.createTrade(c, Trading.createMap(uid, myName(), Trading.offerOf(m), System.currentTimeMillis()))) {
                            TradeStore.setPending(app, c, m.uid); return@runCatching c
                        }
                    }
                    null
                }.getOrNull()
            }
            if (code == null) { toast("Výměnu se nepodařilo založit. Zkus to znovu."); return@launch }
            watch(code)
        }
    }

    /** Živý stav výměny – překresluje se při každé změně v cloudu. */
    private fun watch(code: String) {
        listener?.remove()
        screen("Výměna", "Načítám…")
        listener = FirebaseRepository.listenTrade(code) { t -> if (dialog.isShowing) render(code, t) }
    }

    private fun render(code: String, t: Trade?) {
        val uid = me ?: return
        val role = t?.let { Trading.role(it, uid) }
        if (t == null || role == null) {
            screen("Výměna", "Tahle výměna už neexistuje.")
            TradeStore.clearPending(app); return
        }
        val mine = Trading.myOffer(t, role)
        val theirs = Trading.theirOffer(t, role)
        val other = Trading.theirName(t, role).ifBlank { "Kamarád" }
        when (Trading.phase(t)) {
            Trading.Phase.OPEN -> {
                screen("Kód výměny", "Ukaž ho kamarádovi – v Aréně dá „Mám kód“.")
                body.addView(text(Trading.pretty(code), 50f, dark, font).apply { gravity = Gravity.CENTER; letterSpacing = 0.12f })
                body.addView(text("Čekám, až se připojí…", 13f, olive).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, px(12)) })
                mine?.let { body.addView(monCard(it, "NABÍZÍŠ")) }
                body.addView(button("Zrušit výměnu", primary = false) { cancel(code) })
            }
            Trading.Phase.OFFERED -> {
                screen("Výměna s $other", "Zkontroluj obě strany. Až potvrdíte oba, nejde to vrátit.")
                mine?.let { body.addView(monCard(it, "DÁVÁŠ")) }
                theirs?.let { body.addView(monCard(it, "DOSTANEŠ")) }
                    ?: body.addView(text("Nabídka druhé strany je neplatná – výměnu zruš.", 13f, rust))
                body.addView(text(
                    (if (Trading.confirmed(t, role)) "✓ Ty jsi potvrdil" else "• Ty ještě nepotvrdil") + "\n" +
                        (if (Trading.confirmed(t, if (role == Trading.Role.A) Trading.Role.B else Trading.Role.A)) "✓ $other potvrdil" else "• $other ještě nepotvrdil"),
                    13f, dark).apply { setPadding(0, px(10), 0, px(4)) })
                if (!Trading.confirmed(t, role) && theirs != null) body.addView(button("Potvrdit výměnu", primary = true) { confirm(code, role) })
                body.addView(button("Zrušit výměnu", primary = false) { cancel(code) })
            }
            Trading.Phase.DONE -> finish(t, uid)
            Trading.Phase.CANCELLED -> {
                screen("Výměna zrušena", "Nic se nevyměnilo, tvůj Makromon zůstává u tebe.")
                TradeStore.clearPending(app)
            }
        }
    }

    private fun finish(t: Trade, uid: String) {
        if (applying) return
        applying = true
        screen("Vyměňuji…")
        scope.launch {
            val got = withContext(Dispatchers.IO) { runCatching { TradeStore.applyIfDone(app, t, uid) } }
            applying = false
            if (!dialog.isShowing) return@launch
            got.onSuccess { r ->
                listener?.remove(); listener = null
                val role = Trading.role(t, uid)!!
                val offered = Trading.theirOffer(t, role)
                val name = r?.let { SpeciesRegistry.byId(it.makromonId)?.displayName }
                    ?: offered?.let { SpeciesRegistry.byId(Trading.speciesAfterTrade(it.speciesId))?.displayName } ?: "Makromon"
                val evolved = r != null && offered != null && r.makromonId != offered.speciesId
                screen("Výměna hotová!", if (evolved) "✨ Při výměně se vyvinul! Vítej, $name." else "Vítej v týmu, $name.")
                offered?.let { body.addView(monCard(it.copy(speciesId = r?.makromonId ?: it.speciesId), "NOVÝ PARŤÁK")) }
                body.addView(button("Hotovo", primary = true) { dialog.dismiss() })
            }.onFailure {
                screen("Výměna je potvrzená", "Dokončení se nepovedlo (síť?). Dokončí se samo při dalším otevření Arény.")
            }
        }
    }

    private fun confirm(code: String, role: Trading.Role) = scope.launch {
        withContext(Dispatchers.IO) { runCatching { FirebaseRepository.confirmTrade(code, role) } }
            .onFailure { toast("Potvrzení se nepovedlo – výměna mezitím skončila.") }
    }

    private fun cancel(code: String) = scope.launch {
        withContext(Dispatchers.IO) { runCatching { FirebaseRepository.cancelTrade(code) } }
            .onFailure { toast("Zrušit už nejde – výměna je potvrzená.") }
    }

    // ── Prvky ──

    private fun text(s: String, size: Float, color: Int, tf: Typeface? = font) = TextView(ctx).apply {
        text = s; textSize = size * 1.25f; setTextColor(color); tf?.let { typeface = it }; includeFontPadding = false
    }

    /** Hlavní akce = zelená herní dlaždice, vedlejší = dřevěné tlačítko. */
    private fun button(label: String, primary: Boolean, onClick: () -> Unit) = TextView(ctx).apply {
        text = label.uppercase(); textSize = 20f; typeface = font; gravity = Gravity.CENTER; setTextColor(cream)
        background = if (primary) cz.uhk.macroflow.pokemon.trainer.ArenaUi.tile(0xFF6E9E3A.toInt(), 0xFF3D5A1E.toInt(), dp)
            else cz.uhk.macroflow.pokemon.skills.ui.WoodPanelDrawable(1.5f * dp, parchment = false)
        setShadowLayer(0.01f, dp, dp, 0x99000000.toInt())
        setPadding(px(12), px(10), px(12), px(11))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = px(10) }
        setOnClickListener { onClick() }
    }

    /** Karta Makromona: sprite, jméno, level, útoky; [label] nad jménem. */
    private fun monCard(o: TradeOffer, label: String?): View {
        val sp = SpeciesRegistry.byId(o.speciesId)
        val evolvesTo = Trading.speciesAfterTrade(o.speciesId).takeIf { it != o.speciesId && label == "DOSTANEŠ" }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(14), px(12), px(14), px(12))
            background = cz.uhk.macroflow.pokemon.skills.ui.BevelDrawable.slot(1.5f * dp)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = px(8) }
            val res = sp?.let { ctx.resources.getIdentifier(it.sprite, "drawable", ctx.packageName) } ?: 0
            addView(ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(px(56), px(56))
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x33BC6C25); setStroke(px(2), rust) }
                setPadding(px(6), px(6), px(6), px(6))
                val bmp = if (res != 0) (ctx.resources.getDrawable(res, null) as? BitmapDrawable)?.bitmap else null
                if (bmp != null) setImageBitmap(if (o.shiny) cz.uhk.macroflow.pokemon.shiny.ShinySprites.recolor(bmp, sp!!.id) else bmp)
            })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(14), 0, 0, 0)
                label?.let { addView(text(it, 10f, cream).apply {
                    background = cz.uhk.macroflow.pokemon.trainer.ArenaUi.pill(rust, dp); setPadding(px(8), px(2), px(8), px(3))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
                addView(text((sp?.displayName ?: o.speciesId) + if (o.shiny) " ✦" else "", 24f, dark, font))
                addView(text("Lv ${o.level}" + if (o.moves.isNotEmpty()) " · " + o.moves.joinToString(", ") { it.lowercase() } else "", 12f, olive))
                evolvesTo?.let { id -> addView(text("✨ Výměnou se vyvine v ${SpeciesRegistry.byId(id)?.displayName}!", 12f, rust)) }
            })
        }
    }
}

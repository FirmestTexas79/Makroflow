package cz.uhk.macroflow.pokemon.forge

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.skills.SkillArt
import cz.uhk.macroflow.pokemon.skills.SkillStore
import cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.random.Random

/**
 * Výheň – minihra regionu 1 (docs/adr/0118). Na výšku jako zbytek aplikace: nahoře stará pec (šlápnutí na měch),
 * bublina s uhlíky, přetavení a artefakty; dole panel se 3 × 3 vylepšeními za uhlíky. Tlačítko Pasivní bonusy
 * přepne spodní panel na scrollovací seznam bonusů za suroviny (bez popupu), druhé klepnutí nebo Zpět vrátí vylepšení.
 * Mezi pecí a panelem je bar odemykání: na začátku kola je k dispozici jen Silnější měch, další vylepšení se otevírají
 * po vydělání prahů [Forge.UNLOCK].
 */
class ForgeActivity : AppCompatActivity() {
    private var st = Forge.State()
    private var saved: Forge.State? = null
    private var life = Forge.Life()
    private lateinit var cards: List<ForgeCardView>
    private lateinit var furnace: FurnaceView
    private lateinit var unlockBar: ForgeProgressView
    private var lastUnlocked = -1
    private lateinit var root: FrameLayout
    private var loaded = false

    // spodní panel: mřížka vylepšení ⇄ seznam pasivních bonusů
    private lateinit var panelTitle: TextView
    private lateinit var panelSub: TextView
    private lateinit var grid: LinearLayout
    private lateinit var passiveScroll: ScrollView
    private lateinit var passiveList: LinearLayout
    private lateinit var passBtn: TextView
    private var showingPassives = false
    private val passiveUpdaters = mutableListOf<() -> Unit>()
    private val backToUpgrades = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { showPassives(false) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#1A0E0A")) }
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, backToUpgrades)
        build()
        lifecycleScope.launch {
            val (s, l, off) = withContext(Dispatchers.IO) {
                val s = ForgeStore.load(this@ForgeActivity)
                val l = ForgeStore.life(this@ForgeActivity)
                Triple(s, l, ForgeStore.offline(this@ForgeActivity, s, l))
            }
            st = Forge.earn(s, off); saved = s; life = l; loaded = true
            refresh()
            if (off >= 1) Toast.makeText(this@ForgeActivity, "🔥 Výheň mezitím hořela: +${Forge.format(off)}", Toast.LENGTH_LONG).show()
            loop()
        }
    }

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private fun woodButton(label: String, size: Float = 19f, onClick: () -> Unit) = TextView(this).apply {
        text = label; typeface = ResourcesCompat.getFont(this@ForgeActivity, R.font.jersey_15); textSize = size; gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#FFF2D8"))
        background = GradientDrawable().apply { setColor(Color.parseColor("#8A4420")); cornerRadius = dp(6f).toFloat(); setStroke(dp(3f), Color.parseColor("#2A120A")) }
        setPadding(dp(14f), 0, dp(14f), 0)
        setOnClickListener { onClick() }
    }

    private fun build() {
        val font = ResourcesCompat.getFont(this, R.font.jersey_15)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // výřez displeje (kamera) nesmí zakrýt bublinu s uhlíky
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, ins ->
            val i = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            col.setPadding(i.left, i.top, i.right, i.bottom); ins
        }

        // horní polovina: pec
        furnace = FurnaceView(this).apply {
            onTap = { _, _ -> tap() }
            onReborn = { askReborn() }
        }
        col.addView(furnace, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.42f))
        unlockBar = ForgeProgressView(this)
        col.addView(unlockBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58f)).apply { setMargins(dp(14f), dp(2f), dp(14f), dp(2f)) })

        // dolní polovina: dřevěný panel – nahoře název, uprostřed mřížka vylepšení / seznam bonusů, dole tlačítka
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#4A2414")); cornerRadius = dp(10f).toFloat(); setStroke(dp(4f), Color.parseColor("#2A120A"))
            }
        }
        col.addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.58f).apply { setMargins(dp(8f), dp(2f), dp(8f), dp(8f)) })
        panelTitle = TextView(this).apply {
            text = "VÝHEŇ KOVÁŘE MAGMORA"; typeface = font; textSize = 24f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#FFE2B0")); setShadowLayer(0f, 2f, 2f, Color.parseColor("#2A120A"))
        }
        panel.addView(panelTitle)
        panelSub = TextView(this).apply {
            typeface = font; textSize = 17f; gravity = Gravity.CENTER; visibility = View.GONE
            setTextColor(Color.parseColor("#F4DEC0"))
        }
        panel.addView(panelSub)
        val content = FrameLayout(this)
        panel.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(6f) })

        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val list = mutableListOf<ForgeCardView>()
        for (r in 0 until 3) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            grid.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            for (c in 0 until 3) {
                val up = Forge.Up.entries[r * 3 + c]
                val card = ForgeCardView(this) { max -> buy(up, max) }
                list += card
                line.addView(card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dp(3f), dp(3f), dp(3f), dp(3f)) })
            }
        }
        cards = list

        passiveList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        passiveScroll = ScrollView(this).apply {
            visibility = View.GONE; isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
            addView(passiveList, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        content.addView(passiveScroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        panel.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46f)).apply { topMargin = dp(6f) })
        bar.addView(woodButton("← ZPĚT") { finish() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        passBtn = woodButton("⚒ PASIVNÍ BONUSY") { showPassives(!showingPassives) }
        bar.addView(passBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // ── hra ──
    private fun tap(): Double {
        if (!loaded) return 0.0
        val crit = Random.nextDouble() < Forge.critChance(st.lvl(Forge.Up.SPARK))
        val gain = Forge.perTap(st, life) * if (crit) Forge.CRIT_MULT else 1.0
        st = Forge.earn(st, gain)
        return gain
    }

    private fun buy(up: Forge.Up, max: Boolean) {
        if (!loaded) return
        if (!Forge.unlocked(st, up)) { Toast.makeText(this, "Zamčeno – vydělej ${Forge.format(Forge.UNLOCK[up.ordinal - 1])}.", Toast.LENGTH_SHORT).show(); return }
        val (next, n) = if (max) Forge.buyMax(st, up) else (Forge.buy(st, up)?.let { it to 1 } ?: (st to 0))
        if (n == 0) { Toast.makeText(this, "Málo uhlíků – šlapej měch.", Toast.LENGTH_SHORT).show(); return }
        st = next
        if (max) Toast.makeText(this, "${up.label} +$n", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun askReborn() {
        val cost = Forge.rebornCost(st) ?: run { Toast.makeText(this, "Všechny artefakty jsou ukované.", Toast.LENGTH_SHORT).show(); return }
        val next = Forge.Artifact.entries[st.reborns]
        WorkshopMenus.show(root, "Přetavit výheň", "Cena: ${Forge.format(cost)}") { ui, body, close ->
            body.addView(ui.text("Výheň se rozžhaví do běla a všechno, co sis za uhlíky vylepšil, se roztaví. Uhlíky zmizí.\n\n" +
                "Vylepšení výhně se zase zamknou a odemykají se od začátku. Pasivní bonusy za suroviny zůstanou. A z taveniny se ukove artefakt, který se už nikdy neztratí:\n\n" +
                "${next.label} – ${next.text}", 15f))
            body.addView(ui.spacer(12f))
            body.addView(ui.button(if (Forge.canReborn(st)) "🔥 Přetavit" else "Zatím málo uhlíků", Forge.canReborn(st)) {
                Forge.reborn(st)?.let { st = it; close(); save(); refresh()
                    Toast.makeText(this, "✨ Ukován artefakt: ${next.label}", Toast.LENGTH_LONG).show() }
            })
        }
    }

    /** Přepne spodní panel: mřížka vylepšení výhně ⇄ seznam pasivních bonusů (scrolluje se). */
    private fun showPassives(on: Boolean) {
        showingPassives = on
        grid.visibility = if (on) View.GONE else View.VISIBLE
        passiveScroll.visibility = if (on) View.VISIBLE else View.GONE
        panelSub.visibility = if (on) View.VISIBLE else View.GONE
        panelTitle.text = if (on) "PASIVNÍ BONUSY" else "VÝHEŇ KOVÁŘE MAGMORA"
        passBtn.text = if (on) "⚒ VYLEPŠENÍ VÝHNĚ" else "⚒ PASIVNÍ BONUSY"
        backToUpgrades.isEnabled = on
        if (on) { passiveScroll.scrollTo(0, 0); loadPassives() }
    }

    /** Postaví seznam bonusů znovu (po otevření a po každém ukování); pozice scrollu zůstane. */
    private fun loadPassives() {
        lifecycleScope.launch {
            val have = withContext(Dispatchers.IO) { SkillStore.counts(this@ForgeActivity) }
            if (!showingPassives) return@launch
            val y = passiveScroll.scrollY
            passiveList.removeAllViews(); passiveUpdaters.clear()
            Forge.Passive.entries.forEach { p -> passiveList.addView(passiveRow(p, have), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6f) }) }
            passiveScroll.post { passiveScroll.scrollTo(0, y) }
            updatePassives()
        }
    }

    /** Souhrn bonusů nahoře a barva cen podle toho, na co zrovna stačí uhlíky (volá se z refresh). */
    private fun updatePassives() {
        val b = Forge.bonuses(st)
        panelSub.text = "Obrana +${pct(b.defense)} · HP +${pct(b.hp)} · únik z ballu −${pct(b.escape)}" + (if (st.reborns >= 2) " · Kovadlina ×2" else "")
        passiveUpdaters.forEach { it() }
    }

    private fun passiveRow(p: Forge.Passive, have: Map<String, Int>): View {
        val font = ResourcesCompat.getFont(this, R.font.jersey_15)
        val cream = Color.parseColor("#FFF2D8"); val soft = Color.parseColor("#F4DEC0"); val bad = Color.parseColor("#FF8A7A")
        fun label(t: String, size: Float, color: Int) = TextView(this).apply { text = t; typeface = font; textSize = size; setTextColor(color) }
        val lvl = Forge.passiveLevel(st, p)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
            background = GradientDrawable().apply { setColor(Color.parseColor("#7A3C1C")); cornerRadius = dp(6f).toFloat(); setStroke(dp(3f), Color.parseColor("#2A120A")) }
        }
        row.addView(pixelIcon(bitmapPixels(ForgeArt.icon(this, p.icon)), ForgeArt.ICON, ForgeArt.ICON, 44f))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10f), 0, dp(8f), 0) }
        col.addView(label("${p.label}  ${if (p.maxLevel > 1) "$lvl/${p.maxLevel}" else if (lvl > 0) "✓" else ""}", 22f, cream))
        col.addView(label(p.text, 17f, soft))
        if (lvl < p.maxLevel) {
            val need = Forge.passiveCoals(st, p)
            val (coin, amount) = Forge.amount(need)
            // cena: mince + částka a za ní ikona každé suroviny s potřebným množstvím (kolik máš ve skladu se neukazuje,
            // jen barva: červená = chybí)
            val costRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            costRow.addView(pixelIcon(bitmapPixels(ForgeArt.icon(this, coin.icon)), ForgeArt.ICON, ForgeArt.ICON, 26f))
            val coalTv = label(" $amount", 18f, cream); costRow.addView(coalTv)
            p.materials.forEach { (r, n) ->
                costRow.addView(View(this), LinearLayout.LayoutParams(dp(12f), 1))
                SkillArt.resourceIcon(r).let { costRow.addView(pixelIcon(it, SkillArt.ITEM, SkillArt.ITEM, 28f)) }
                costRow.addView(label(" $n", 18f, if ((have[r.itemId] ?: 0) >= n) cream else bad))
            }
            col.addView(costRow)
            passiveUpdaters += { coalTv.setTextColor(if (st.coals >= need) cream else bad) }
        }
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (lvl < p.maxLevel) row.addView(woodButton("UKOVAT", 18f) { buyPassive(p) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42f)))
        return row
    }

    private fun buyPassive(p: Forge.Passive) {
        lifecycleScope.launch {
            val next = withContext(Dispatchers.IO) { ForgeStore.buyPassive(this@ForgeActivity, st, p) }
            if (next == null) Toast.makeText(this@ForgeActivity, "Chybí uhlíky nebo suroviny.", Toast.LENGTH_SHORT).show()
            else { st = next; saved = next; refresh(); loadPassives() }
        }
    }

    /** Pixelová ikona zvětšená celočíselným násobkem, bez vyhlazení. */
    private fun pixelIcon(pixels: IntArray, w: Int, h: Int, sizeDp: Float) = ImageView(this).apply {
        val k = maxOf(1, dp(sizeDp) / maxOf(w, h))
        setImageBitmap(Bitmap.createScaledBitmap(Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888), w * k, h * k, false))
        layoutParams = LinearLayout.LayoutParams(w * k, h * k)
    }

    private fun pct(v: Double) = "${Math.round(v * 100)} %"

    private fun bitmapPixels(b: android.graphics.Bitmap): IntArray = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    private fun refresh() {
        Forge.Up.entries.forEachIndexed { i, up ->
            val c = cards[i]
            val cost = Forge.cost(st, up)
            c.icon = ForgeArt.icon(this, up.icon)
            c.title = up.label
            c.desc = describe(up)
            c.level = st.lvl(up)
            val (coin, s) = Forge.amount(cost)
            c.costIcon = ForgeArt.icon(this, coin.icon); c.cost = s
            c.affordable = st.coals >= cost
            c.locked = !Forge.unlocked(st, up)
            c.lockText = if (c.locked) "Odemkne se po vydělání ${Forge.format(Forge.UNLOCK[up.ordinal - 1])}" else ""
            c.invalidate()
        }
        refreshUnlockBar()
        val (coin, s) = Forge.amount(st.coals)
        furnace.amount = s
        furnace.amountIcon = ForgeArt.icon(this, coin.icon)
        furnace.rates = "+${Forge.amount(Forge.perTap(st, life)).second} za šlápnutí · +${Forge.amount(Forge.perSecond(st, life)).second}/s"
        furnace.artifacts = Forge.artifacts(st)
        val rc = Forge.rebornCost(st)
        furnace.rebornLabel = rc?.let { "PŘETAVIT · ${Forge.format(it)}" } ?: "VŠECHNY ARTEFAKTY UKOVÁNY"
        furnace.rebornReady = Forge.canReborn(st)
        if (showingPassives) updatePassives()
    }

    private fun refreshUnlockBar() {
        val count = Forge.Up.entries.count { Forge.unlocked(st, it) }
        if (lastUnlocked in 0 until count) {
            val fresh = Forge.Up.entries.last { Forge.unlocked(st, it) }
            Toast.makeText(this, "🔓 Odemčeno: ${fresh.label}", Toast.LENGTH_SHORT).show()
        }
        lastUnlocked = count
        val next = Forge.nextUnlock(st)
        if (next == null) {
            unlockBar.caption = "Všechna vylepšení jsou odemčená"; unlockBar.value = "✓"; unlockBar.progress = 1f
        } else {
            val (up, need) = next
            unlockBar.caption = "Další vylepšení: ${up.label}"
            unlockBar.value = "${Forge.amount(st.earned).second} / ${Forge.amount(need).second}"
            unlockBar.progress = (st.earned / need).toFloat()
        }
        unlockBar.invalidate()
    }

    private fun describe(up: Forge.Up): String {
        val l = st.lvl(up)
        fun x(v: Double) = "×" + String.format(java.util.Locale.forLanguageTag("cs"), "%.2f", v)
        return when (up) {
            Forge.Up.BELLOWS -> "+1 uhlík za šlápnutí na měch"
            Forge.Up.SPARK -> "Šance na jiskru (×3): ${Math.round(Forge.critChance(l) * 100)} %"
            Forge.Up.DISCOUNT -> "Vylepšení levnější o ${Math.round(Forge.discountPct(l))} %"
            Forge.Up.AUTO -> "Výheň hoří sama: +1 uhlík za sekundu"
            Forge.Up.HEARTH -> "Druhé ohniště: +8 uhlíků za sekundu"
            Forge.Up.HEAT -> "Veškerý žár ${x(Forge.heatMult(l))} (+5 % za úroveň)"
            Forge.Up.ASH -> "Když nehraješ, výheň dá ${Math.round(Forge.offlineFraction(l) * 100)} % (až ${Forge.OFFLINE_CAP_H} h)"
            Forge.Up.STEPS -> "Kroky dnes ${life.steps}: ${x(Forge.stepsMult(l, life.steps))}"
            Forge.Up.SLEEP -> "Spánek ${String.format(java.util.Locale.forLanguageTag("cs"), "%.1f", life.sleepHours)} h: ${x(Forge.sleepMult(l, life.sleepHours))}"
        }
    }

    private fun loop() {
        lifecycleScope.launch {
            var last = System.currentTimeMillis(); var lastSave = last; var lastLife = last
            while (isActive) {
                delay(250)
                val now = System.currentTimeMillis()
                st = Forge.earn(st, Forge.perSecond(st, life) * (now - last) / 1000.0)
                last = now
                refresh()
                if (now - lastSave > 10_000) { save(); lastSave = now }
                if (now - lastLife > 60_000) { life = withContext(Dispatchers.IO) { ForgeStore.life(this@ForgeActivity) }; lastLife = now }
            }
        }
    }

    private fun save() {
        if (!loaded) return
        val s = st; val before = saved
        saved = s
        lifecycleScope.launch(Dispatchers.IO) { ForgeStore.save(this@ForgeActivity, s, before) }
    }

    override fun onPause() {
        super.onPause()
        if (loaded) { val s = st; val before = saved; saved = s; Thread { ForgeStore.save(applicationContext, s, before) }.start() }
    }
}

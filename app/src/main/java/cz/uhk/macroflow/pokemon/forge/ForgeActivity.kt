package cz.uhk.macroflow.pokemon.forge

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.skills.MaterialArt
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
 * Výheň – minihra regionu 1 (docs/adr/0118). Na šířku jako předloha: vlevo 3 × 3 vylepšení za uhlíky,
 * vpravo stará pec (šlápnutí na měch), bublina s uhlíky, přetavení a artefakty. Pasivní bonusy za suroviny v menu.
 */
class ForgeActivity : AppCompatActivity() {
    private var st = Forge.State()
    private var saved: Forge.State? = null
    private var life = Forge.Life()
    private lateinit var cards: List<ForgeCardView>
    private lateinit var furnace: FurnaceView
    private lateinit var root: FrameLayout
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#1A0E0A")) }
        setContentView(root)
        build()
        lifecycleScope.launch {
            val (s, l, off) = withContext(Dispatchers.IO) {
                val s = ForgeStore.load(this@ForgeActivity)
                val l = ForgeStore.life(this@ForgeActivity)
                Triple(s, l, ForgeStore.offline(this@ForgeActivity, s, l))
            }
            st = s.copy(coals = s.coals + off); saved = s; life = l; loaded = true
            refresh()
            if (off >= 1) Toast.makeText(this@ForgeActivity, "🔥 Výheň mezitím hořela: +${Forge.format(off)}", Toast.LENGTH_LONG).show()
            loop()
        }
    }

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private fun build() {
        val font = ResourcesCompat.getFont(this, R.font.jersey_15)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // levý panel: rám jako v předloze, nahoře název, mřížka 3 × 3, dole tlačítka
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#4A2414")); cornerRadius = dp(10f).toFloat(); setStroke(dp(4f), Color.parseColor("#2A120A"))
            }
        }
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.58f).apply { setMargins(dp(10f), dp(10f), dp(4f), dp(10f)) })
        left.addView(TextView(this).apply {
            text = "VÝHEŇ KOVÁŘE MAGMORA"; typeface = font; textSize = 20f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#FFE2B0")); setShadowLayer(0f, 2f, 2f, Color.parseColor("#2A120A"))
        })
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(6f) })
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
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        left.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40f)).apply { topMargin = dp(6f) })
        fun btn(label: String, onClick: () -> Unit) = TextView(this).apply {
            text = label; typeface = font; textSize = 17f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#FFF2D8"))
            background = GradientDrawable().apply { setColor(Color.parseColor("#8A4420")); cornerRadius = dp(6f).toFloat(); setStroke(dp(3f), Color.parseColor("#2A120A")) }
            setPadding(dp(14f), 0, dp(14f), 0)
            setOnClickListener { onClick() }
        }
        bar.addView(btn("← ZPĚT") { finish() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(btn("⚒ PASIVNÍ BONUSY") { openPassives() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))

        furnace = FurnaceView(this).apply {
            onTap = { _, _ -> tap() }
            onReborn = { askReborn() }
            onArtifact = { a -> Toast.makeText(this@ForgeActivity, a?.let { "${it.label}: ${it.text}" } ?: "Artefakt se ukove při přetavení výhně.", Toast.LENGTH_LONG).show() }
        }
        row.addView(furnace, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.42f))
    }

    // ── hra ──
    private fun tap(): Double {
        if (!loaded) return 0.0
        val crit = Random.nextDouble() < Forge.critChance(st.lvl(Forge.Up.SPARK))
        val gain = Forge.perTap(st, life) * if (crit) Forge.CRIT_MULT else 1.0
        st = st.copy(coals = st.coals + gain)
        return gain
    }

    private fun buy(up: Forge.Up, max: Boolean) {
        if (!loaded) return
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
                "Pasivní bonusy za suroviny zůstanou. A z taveniny se ukove artefakt, který se už nikdy neztratí:\n\n" +
                "${next.label} – ${next.text}", 15f))
            body.addView(ui.spacer(12f))
            body.addView(ui.button(if (Forge.canReborn(st)) "🔥 Přetavit" else "Zatím málo uhlíků", Forge.canReborn(st)) {
                Forge.reborn(st)?.let { st = it; close(); save(); refresh()
                    Toast.makeText(this, "✨ Ukován artefakt: ${next.label}", Toast.LENGTH_LONG).show() }
            })
        }
    }

    private fun openPassives() {
        lifecycleScope.launch {
            val have = withContext(Dispatchers.IO) { SkillStore.counts(this@ForgeActivity) }
            val b = Forge.bonuses(st)
            WorkshopMenus.show(root, "Pasivní bonusy", "Obrana +${pct(b.defense)} · HP +${pct(b.hp)} · únik z ballu −${pct(b.escape)}" +
                (if (st.reborns >= 2) " · Kovadlina ×2" else "")) { ui, body, close ->
                Forge.Passive.entries.forEach { p ->
                    val lvl = Forge.passiveLevel(st, p)
                    val card = WorkshopMenus.card(ui)
                    card.addView(ui.icon(bitmapPixels(ForgeArt.icon(this@ForgeActivity, p.icon)), ForgeArt.ICON, ForgeArt.ICON, 40f))
                    val col = ui.column().apply { setPadding(ui.px(10f), 0, ui.px(6f), 0) }
                    col.addView(ui.text("${p.label}  ${if (p.maxLevel > 1) "$lvl/${p.maxLevel}" else if (lvl > 0) "✓" else ""}", 17f))
                    col.addView(ui.text(p.text, 14f, ui.inkSoft))
                    if (lvl < p.maxLevel) {
                        val costRow = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
                        costRow.addView(ui.icon(bitmapPixels(ForgeArt.icon(this@ForgeActivity, Forge.amount(Forge.passiveCoals(st, p)).first.icon)), ForgeArt.ICON, ForgeArt.ICON, 18f))
                        costRow.addView(ui.text(" ${Forge.amount(Forge.passiveCoals(st, p)).second}  ", 14f, if (st.coals >= Forge.passiveCoals(st, p)) ui.ink else ui.rust))
                        p.materials.forEach { (r, n) ->
                            MaterialArt.icon(r)?.let { costRow.addView(ui.icon(it, SkillArt.ITEM, SkillArt.ITEM, 18f)) }
                            costRow.addView(ui.text(" ${have[r.itemId] ?: 0}/$n  ", 14f, if ((have[r.itemId] ?: 0) >= n) ui.ink else ui.rust))
                        }
                        col.addView(costRow)
                    }
                    card.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    if (lvl < p.maxLevel) card.addView(ui.button("Ukovat") {
                        lifecycleScope.launch {
                            val next = withContext(Dispatchers.IO) { ForgeStore.buyPassive(this@ForgeActivity, st, p) }
                            if (next == null) Toast.makeText(this@ForgeActivity, "Chybí uhlíky nebo suroviny.", Toast.LENGTH_SHORT).show()
                            else { st = next; saved = next; close(); refresh(); openPassives() }
                        }
                    })
                    body.addView(card)
                }
            }
        }
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
            c.invalidate()
        }
        val (coin, s) = Forge.amount(st.coals)
        furnace.amount = s
        furnace.amountIcon = ForgeArt.icon(this, coin.icon)
        furnace.rates = "+${Forge.amount(Forge.perTap(st, life)).second} za šlápnutí · +${Forge.amount(Forge.perSecond(st, life)).second}/s"
        furnace.artifacts = Forge.artifacts(st)
        val rc = Forge.rebornCost(st)
        furnace.rebornLabel = rc?.let { "PŘETAVIT · ${Forge.format(it)}" } ?: "VŠECHNY ARTEFAKTY UKOVÁNY"
        furnace.rebornReady = Forge.canReborn(st)
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
                st = st.copy(coals = st.coals + Forge.perSecond(st, life) * (now - last) / 1000.0)
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

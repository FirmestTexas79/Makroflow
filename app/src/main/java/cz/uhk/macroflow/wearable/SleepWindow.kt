package cz.uhk.macroflow.wearable

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import cz.uhk.macroflow.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// Provizorní tokeny (aplikace zatím nemá DESIGN.md): noční obloha + pastelové „nafouknuté“ tvary.
private val SKY_TOP = Color.parseColor("#0A1738")
private val SKY_BOTTOM = Color.parseColor("#1F4286")
private val TXT = Color.parseColor("#EEF4FF")
private val TXT2 = Color.parseColor("#BCD0F0")
private val LINE = Color.parseColor("#2EFFFFFF")
private val NAVY = Color.parseColor("#1F2B5E")      // text na pastelu
private val NAVY2 = Color.parseColor("#4A5688")
private val P_PERI = Color.parseColor("#B4C0F8")    // hluboký
private val P_LAV = Color.parseColor("#DCCBF7")     // REM
private val P_SKY = Color.parseColor("#AEDDF7")     // lehký
private val P_PEACH = Color.parseColor("#F9D2AE")   // vzhůru / pozor
private val P_MINT = Color.parseColor("#B2EBD5")    // v pořádku / tip
private val P_CLOUD = Color.parseColor("#E5EBFA")   // neutrální
private val P_SUN = Color.parseColor("#FFE79E")     // hvězdy
private val MOON = Color.parseColor("#FFF1C4")
private const val NEED_SEC = 8 * 3600

internal fun fmtDur(sec: Int?): String = sec?.let { "${it / 3600} h ${(it % 3600) / 60} min" } ?: "–"

/** Poslední noc s daty z posledních 8 dnů. */
internal fun latestNight(days: List<WearableDayEntity>): WearableDayEntity? = days.filter { it.sleepSec != null }.maxByOrNull { it.date }

/** Garmin skóre → 1–5 hvězd. */
internal fun starsOf(score: Int?): Int = when { score == null -> 0; score >= 90 -> 5; score >= 80 -> 4; score >= 60 -> 3; score >= 40 -> 2; else -> 1 }
internal fun nightWord(score: Int?): String? = score?.let { when { it >= 90 -> "Výborná noc"; it >= 80 -> "Dobrá noc"; it >= 60 -> "Průměrná noc"; else -> "Slabá noc" } }

/** Osvětlená část měsíce pro podíl [f] 0..1 (0 = nov, 0,5 = první čtvrť, 1 = úplněk). */
internal fun litPath(cx: Float, cy: Float, r: Float, f: Float): Path {
    val p = Path()
    if (f >= 0.985f) { p.addCircle(cx, cy, r, Path.Direction.CW); return p }
    if (f <= 0.01f) return p
    p.arcTo(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 180f)
    val rx = r * abs(1 - 2 * f)
    p.arcTo(RectF(cx - rx, cy - r, cx + rx, cy + r), 90f, if (f < 0.5f) -180f else 180f)
    p.close(); return p
}

private fun reducedMotion(ctx: Context) = Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Karta na dashboardu pod trenérem; vrací funkci pro obnovení (volat v onResume). Bez klíče a bez dat je schovaná. */
object SleepCard {
    fun attach(coachCard: View, host: Fragment): () -> Unit {
        val ctx = coachCard.context
        val d = ctx.resources.displayMetrics.density
        val parent = coachCard.parent as? LinearLayout ?: return {}
        val icon = MoonView(ctx, animate = false)
        val title = TextView(ctx).apply { text = "Spánek"; setTextColor(TXT); textSize = 16f; typeface = Typeface.create("sans-serif-black", Typeface.NORMAL) }
        val sub = TextView(ctx).apply { setTextColor(TXT2); textSize = 13f }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (12 * d).toInt())
            addView(icon, LinearLayout.LayoutParams((48 * d).toInt(), (48 * d).toInt()))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; setPadding((14 * d).toInt(), 0, 0, 0); addView(title); addView(sub)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val card = MaterialCardView(ctx).apply {
            radius = 24 * d; cardElevation = 0f; setCardBackgroundColor(Color.parseColor("#12294F"))
            strokeColor = Color.parseColor("#3C63A8"); strokeWidth = d.toInt()
            visibility = View.GONE; isClickable = true; isFocusable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), 0)
            }
            addView(row)
            setOnClickListener { SleepWindow().show(host.childFragmentManager, "sleep") }
        }
        parent.addView(card, parent.indexOfChild(coachCard) + 1)

        return {
            host.viewLifecycleOwner.lifecycleScope.launch {
                val app = ctx.applicationContext
                val days = withContext(Dispatchers.IO) {
                    if (GarminKeyStore.hasKey(app)) runCatching { WearableSync.runIfStale(app) }
                    val today = LocalDate.now()
                    AppDatabase.getDatabase(app).wearableDao().getDaysSync(today.minusDays(8).toString(), today.toString())
                }
                val last = latestNight(days)
                card.visibility = if (last != null || GarminKeyStore.hasKey(app)) View.VISIBLE else View.GONE
                icon.setFraction((last?.sleepSec ?: 0) / NEED_SEC.toFloat())
                sub.text = when {
                    last == null -> "Zatím žádná noc z hodinek – klepni a synchronizuj"
                    last.date == LocalDate.now().toString() ->
                        listOfNotNull(fmtDur(last.sleepSec), nightWord(last.sleepScore)).joinToString(" · ")
                    else -> "Dnešní noc ještě není načtená (poslední: ${fmtDur(last.sleepSec)})"
                }
            }
        }
    }
}

class SleepWindow : DialogFragment() {
    private lateinit var col: LinearLayout
    private var selected: String? = null        // datum zobrazené noci; null = nejnovější
    private var detailsOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext(); val d = ctx.resources.displayMetrics.density
        col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx).apply {
            clipToPadding = false
            setPadding((18 * d).toInt(), (40 * d).toInt(), (18 * d).toInt(), (32 * d).toInt()); addView(col)
        }
        return FrameLayout(ctx).apply { addView(NightSkyView(ctx)); addView(scroll) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { reload() }

    private data class Data(val days: List<WearableDayEntity>, val acts: List<WearableActivityEntity>,
                            val plan: String?, val planCardio: Boolean, val acc: Pair<Int, Double>?)

    private fun reload() {
        val app = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                val today = LocalDate.now(); val dao = AppDatabase.getDatabase(app).wearableDao()
                val prefs = app.getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)
                val dayName = java.text.SimpleDateFormat("EEEE", Locale.ENGLISH).format(java.util.Date())
                Data(
                    dao.getDaysSync(today.minusDays(90).toString(), today.toString()),
                    dao.getActivitiesBetweenSync(today.minusDays(90).toString(), today.toString()),
                    prefs.getString("type_$dayName", "rest"),
                    (prefs.getString("kardio_type_$dayName", "rest") ?: "rest") != "rest",
                    RitualPrefill.accuracy(app)
                )
            }
            render(data)
        }
    }

    private fun render(data: Data) {
        val ctx = requireContext(); val d = ctx.resources.displayMetrics.density
        val days = data.days
        col.removeAllViews()
        val nights = days.filter { it.sleepSec != null }.sortedBy { it.date }
        val night = nights.firstOrNull { it.date == selected } ?: nights.lastOrNull()
        val isToday = night?.date == LocalDate.now().toString()
        val idx = nights.indexOfFirst { it.date == night?.date }

        // ── 1. Navigace mezi nocemi ──
        if (night != null) {
            fun arrow(label: String, desc: String, enabled: Boolean, to: Int) = TextView(ctx).apply {
                text = label; textSize = 24f; gravity = Gravity.CENTER; setTextColor(if (enabled) NAVY else Color.parseColor("#80A0A8C8"))
                contentDescription = desc; minimumWidth = (48 * d).toInt(); minimumHeight = (44 * d).toInt()
                isClickable = enabled; isFocusable = enabled
                if (enabled) setOnClickListener { selected = nights[to].date; render(data) }
            }
            val nav = Puff(ctx, P_CLOUD, 26f).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding((6 * d).toInt(), (2 * d).toInt(), (6 * d).toInt(), (2 * d).toInt() + shadowPx) }
            nav.addView(arrow("‹", "Předchozí noc", idx > 0, idx - 1))
            nav.addView(TextView(ctx).apply {
                val dt = LocalDate.parse(night.date)
                text = dt.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("cs")).replaceFirstChar { it.uppercase() } + " ${dt.dayOfMonth}. ${dt.monthValue}."
                setTextColor(NAVY); textSize = 15f; gravity = Gravity.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            nav.addView(arrow("›", "Další noc", idx < nights.size - 1, idx + 1))
            col.addView(nav, LinearLayout.LayoutParams(-1, -2))
        }

        // ── 2. Hrdina: měsíc (měřidlo) + délka + hvězdy ──
        val frac = ((night?.sleepSec ?: 0) / NEED_SEC.toFloat()).coerceIn(0f, 1f)
        val moon = MoonView(ctx, animate = true).apply {
            contentDescription = if (night == null) "Měsíc: žádná změřená noc" else "Měsíc svítí z ${(frac * 100).toInt()} % potřeby spánku"
        }
        val heroText = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding((18 * d).toInt(), 0, 0, 0)
            addView(TextView(ctx).apply {
                setTextColor(TXT); typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
                text = if (night?.sleepSec == null) "–" else bigDuration(night.sleepSec)
            })
            if (night != null) {
                val n = starsOf(night.sleepScore)
                if (n > 0) addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; setPadding(0, (6 * d).toInt(), 0, 0)
                    contentDescription = "Kvalita noci $n z 5 hvězd"
                    (1..5).forEach { i -> addView(PuffStar(ctx, if (i <= n) P_SUN else Color.parseColor("#5A6EA8")), LinearLayout.LayoutParams((26 * d).toInt(), (26 * d).toInt())) }
                })
                nightWord(night.sleepScore)?.let { addView(TextView(ctx).apply { text = it; setTextColor(TXT2); textSize = 14f; setPadding(0, (4 * d).toInt(), 0, 0) }) }
            } else addView(TextView(ctx).apply { text = "Zatím žádná noc z hodinek"; setTextColor(TXT2); textSize = 14f })
        }
        val hero = FrameLayout(ctx).apply {
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(moon, LinearLayout.LayoutParams((132 * d).toInt(), (132 * d).toInt()))
                addView(heroText, LinearLayout.LayoutParams(0, -2, 1f))
            })
            addView(PuffStar(ctx, P_SUN).apply { rotation = 14f }, FrameLayout.LayoutParams((34 * d).toInt(), (34 * d).toInt(), Gravity.TOP or Gravity.END))
            addView(PuffStar(ctx, P_LAV).apply { rotation = -12f }, FrameLayout.LayoutParams((20 * d).toInt(), (20 * d).toInt(), Gravity.TOP or Gravity.END).apply { topMargin = (46 * d).toInt(); rightMargin = (30 * d).toInt() })
        }
        col.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (14 * d).toInt() })
        moon.setFraction(frac)
        if (night != null && night.date == nights.last().date && nights.last().date != LocalDate.now().toString())
            col.addView(small(ctx, "Dnešní noc se ještě nenačetla. Nech hodinky synchronizovat přes Garmin Connect.").apply { setPadding(0, (6 * d).toInt(), 0, 0) })

        if (night != null) {
            val summary = SleepInsights.summarize(days, night.date)
            val recs = SleepInsights.recommendations(summary, SleepInsights.stressEffect(days, night.date))

            // ── 3. Jedna věc pro dnešek ──
            recs.firstOrNull()?.let { tip ->
                col.addView(Puff(ctx, P_MINT, 28f).apply {
                    addView(TextView(ctx).apply { text = "Pro dnešek"; setTextColor(NAVY2); textSize = 12f })
                    addView(TextView(ctx).apply { text = tip; setTextColor(NAVY); textSize = 15f; setPadding(0, (3 * d).toInt(), 0, 0) })
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (18 * d).toInt() })
            }

            // ── 4. Stavba noci: 2×2 ──
            val total = (night.sleepSec ?: 1).coerceAtLeast(1).toFloat()
            fun pct(v: Int?) = (v ?: 0) / total * 100f
            heading(ctx, "Stavba noci")
            fun state(p: Float, lo: Int, hi: Int) = if (p < lo) "málo" else if (p > hi) "moc" else "ok"
            val dp = pct(night.deepSec); val rp = pct(night.remSec); val lp = pct(night.lightSec); val ap = pct(night.awakeSec)
            col.addView(pair(ctx,
                blob(ctx, "Hluboký", "${(night.deepSec ?: 0) / 60} min", state(dp, 16, 33), P_PERI, "${dp.toInt()} %"),
                blob(ctx, "REM", "${(night.remSec ?: 0) / 60} min", state(rp, 21, 31), P_LAV, "${rp.toInt()} %")))
            col.addView(pair(ctx,
                blob(ctx, "Lehký", "${(night.lightSec ?: 0) / 60} min", state(lp, 30, 64), P_SKY, "${lp.toInt()} %"),
                blob(ctx, "Vzhůru", "${(night.awakeSec ?: 0) / 60} min", null, P_PEACH, "${ap.toInt()} %"), (10 * d).toInt()))

            // ── 5. Regenerace: Body Battery vlevo, kyslík vpravo ──
            val bbBlob = night.bbSleepGain?.let { blob(ctx, "Body Battery", "+$it", null, P_SKY, "nabito spánkem") }
            val spoBlob = night.avgSpo2?.let { blob(ctx, "Kyslík", "${it.toInt()} %", null, P_MINT, night.lowSpo2?.let { l -> "nejnižší $l %" } ?: "ve spánku") }
            if (bbBlob != null || spoBlob != null) { heading(ctx, "Regenerace"); col.addView(pair(ctx, bbBlob, spoBlob)) }

            // ── 6. Týden: průměr vlevo, dluh vpravo ──
            heading(ctx, "Poslední týden")
            val trend = summary.trendSec?.let { t -> "${if (t >= 0) "+" else "−"}${abs(t) / 60} min proti předchozímu" } ?: "průměr za 7 nocí"
            col.addView(pair(ctx,
                blob(ctx, "Průměr", fmtDur(summary.avg7Sec), null, P_CLOUD, trend),
                blob(ctx, "Dluh", if (summary.debt7Sec == 0) "žádný" else fmtDur(summary.debt7Sec), null,
                    if (summary.debt7Sec >= 4 * 3600) P_PEACH else P_MINT, "do 8 h za noc")))
        }

        // ── 7. Posledních 14 nocí ──
        heading(ctx, "Posledních 14 nocí")
        val byDate = days.associateBy { it.date }
        val endDay = LocalDate.parse((nights.lastOrNull()?.date) ?: LocalDate.now().toString())
        val items = (13 downTo 0).map { back ->
            val dt = endDay.minusDays(back.toLong())
            Triple(dt.dayOfMonth.toString(), byDate[dt.toString()]?.sleepSec, dt.toString())
        }
        col.addView(NightsChartView(ctx, items, night?.date) { date ->
            if (nights.any { it.date == date }) { selected = date; render(data) }
        }.apply { contentDescription = "Délka spánku za posledních 14 nocí. Klepnutím vybereš noc." }, LinearLayout.LayoutParams(-1, (130 * d).toInt()))
        col.addView(small(ctx, "Čárkovaně 8 h · klepnutím vybereš noc"))

        // ── 8. Podrobnosti (sbalené) ──
        if (night != null) {
            val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; visibility = if (detailsOpen) View.VISIBLE else View.GONE }
            fillDetails(ctx, box, data, night, isToday)
            val toggle = Puff(ctx, P_CLOUD, 26f).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding((16 * d).toInt(), (10 * d).toInt(), (16 * d).toInt(), (10 * d).toInt() + shadowPx); isClickable = true; isFocusable = true }
            val label = TextView(ctx).apply { setTextColor(NAVY); textSize = 15f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
            fun sync() { label.text = if (detailsOpen) "Skrýt podrobnosti" else "Podrobnosti a čísla"; box.visibility = if (detailsOpen) View.VISIBLE else View.GONE }
            toggle.addView(label); sync()
            toggle.setOnClickListener { detailsOpen = !detailsOpen; sync() }
            col.addView(toggle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (22 * d).toInt() })
            col.addView(box)
        }

        val actions = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, (18 * d).toInt(), 0, 0) }
        if (GarminKeyStore.hasKey(ctx)) actions.addView(button(ctx, "Synchronizovat hodinky", true) { b ->
            b.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val msg = withContext(Dispatchers.IO) {
                    runCatching { WearableSync.run(ctx.applicationContext) }.fold({ "Načteno dní: $it" }, { "Synchronizace selhala: ${it.message}" })
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show(); reload()
            }
        })
        actions.addView(button(ctx, "Zavřít", false) { dismiss() })
        col.addView(actions)
    }

    /** Všechna hustá čísla, schovaná pod „Podrobnosti“. */
    private fun fillDetails(ctx: Context, c: LinearLayout, data: Data, night: WearableDayEntity, isToday: Boolean) {
        val d = ctx.resources.displayMetrics.density
        val s = SleepInsights.summarize(data.days, night.date)
        val recs = SleepInsights.recommendations(s, SleepInsights.stressEffect(data.days, night.date))
        fun sub(t: String) = c.addView(TextView(ctx).apply { text = t; setTextColor(TXT); textSize = 15f; typeface = Typeface.create("sans-serif-black", Typeface.NORMAL); setPadding(0, (18 * d).toInt(), 0, (4 * d).toInt()) })

        sub("Za delší dobu")
        c.addView(kv(ctx, "Průměr 30 nocí", "${fmtDur(s.avg30Sec)} · ${s.nights} nocí"))
        s.avgBedtimeMin?.let { m -> c.addView(kv(ctx, "Usínání", "kolem %02d:%02d ±%d min".format((m / 60) % 24, m % 60, s.bedtimeSdMin ?: 0))) }
        s.avgScore7?.let { c.addView(kv(ctx, "Skóre 7 nocí", "$it")) }
        recs.drop(1).forEach { r -> c.addView(TextView(ctx).apply { text = r; setTextColor(P_MINT); textSize = 14f; setPadding(0, (6 * d).toInt(), 0, 0) }) }

        sub("Noc v číslech")
        night.sleepScore?.let { c.addView(kv(ctx, "Skóre", "$it")) }
        c.addView(kv(ctx, "Hluboký / REM / lehký / vzhůru", "${fmtDur(night.deepSec)} / ${fmtDur(night.remSec)} / ${fmtDur(night.lightSec)} / ${fmtDur(night.awakeSec)}"))
        night.avgResp?.let { c.addView(kv(ctx, "Dech", "${it.toInt()} / min")) }
        night.sleepAvgHr?.let { c.addView(kv(ctx, "Tep ve spánku", "${it.toInt()}")) }
        night.sleepStress?.let { c.addView(kv(ctx, "Stres ve spánku", "${it.toInt()}")) }
        night.awakeCount?.let { c.addView(kv(ctx, "Probuzení", "$it×")) }
        c.addView(small(ctx, "Doporučená rozmezí: hluboký 16–33 % (orientačně), REM 21–31 %, lehký 30–64 %. Garmin posílá součty fází, ne průběh noci.").apply { setPadding(0, (6 * d).toInt(), 0, 0) })

        val dayActs = data.acts.filter { it.date == night.date }
        val sums = ActivityGroups.summarize(dayActs)
        val note = if (isToday) ActivityGroups.planNote(data.plan, data.planCardio, dayActs) else null
        val fullDay = data.days.lastOrNull { it.date <= night.date && it.totalKcal != null && GarminParser.isWorn(it) }
        if (sums.isNotEmpty() || note != null || fullDay != null) {
            sub("Pohyb z hodinek")
            sums.forEach { c.addView(kv(ctx, "${it.group.label} (${it.count}×)", "${it.minutes} min · ${it.kcal} kcal")) }
            note?.let { c.addView(TextView(ctx).apply { text = it; setTextColor(P_PEACH); textSize = 14f; setPadding(0, (6 * d).toInt(), 0, 0) }) }
            fullDay?.let { c.addView(small(ctx, "Poslední plný den ${it.date}: aktivní ${it.activeKcal} kcal, celkem ${it.totalKcal} kcal, ${it.steps} kroků").apply { setPadding(0, (6 * d).toInt(), 0, 0) }) }
        }
        data.acc?.let { (n, mae) ->
            sub("Odhad ranního rituálu")
            c.addView(small(ctx, "Z hodinek odhadnuto $n×, průměrná odchylka od tvých hodnot %.1f bodu.".format(mae)))
        }
    }

    // ── stavební prvky ──────────────────────────────────────────────────────

    private val shadowPx get() = (10 * resources.displayMetrics.density).toInt()

    private fun bigDuration(sec: Int): CharSequence {
        val sb = SpannableStringBuilder()
        fun part(num: String, unit: String) {
            val s = sb.length; sb.append(num); sb.setSpan(RelativeSizeSpan(2.6f), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(" $unit ")
        }
        part("${sec / 3600}", "h"); part("${(sec % 3600) / 60}", "min")
        return sb.trimEnd()
    }

    private fun small(ctx: Context, s: String) = TextView(ctx).apply { text = s; setTextColor(TXT2); textSize = 13f }

    private fun heading(ctx: Context, s: String) {
        val d = ctx.resources.displayMetrics.density
        col.addView(TextView(ctx).apply {
            text = s; setTextColor(TXT); textSize = 17f; typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setPadding((4 * d).toInt(), (24 * d).toInt(), 0, (8 * d).toInt())
        })
    }

    /** Dva puffy vedle sebe (vlevo / vpravo); chybějící se nahradí prázdným místem. */
    private fun pair(ctx: Context, left: View?, right: View?, topMargin: Int = 0): View {
        val d = ctx.resources.displayMetrics.density
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            fun lp(first: Boolean) = LinearLayout.LayoutParams(0, -2, 1f).apply { if (first) rightMargin = (5 * d).toInt() else leftMargin = (5 * d).toInt() }
            addView(left ?: View(ctx), lp(true)); addView(right ?: View(ctx), lp(false))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { this.topMargin = topMargin }
        }
    }

    /** Pastelový „nafouknutý“ blok: název vlevo nahoře, stav vpravo nahoře, velká hodnota, drobný popisek. */
    private fun blob(ctx: Context, name: String, value: String, status: String?, tone: Int, subText: String): View {
        val d = ctx.resources.displayMetrics.density
        return Puff(ctx, tone, 28f).apply {
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(TextView(ctx).apply { text = name; setTextColor(NAVY2); textSize = 13f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }, LinearLayout.LayoutParams(0, -2, 1f))
                status?.let { st ->
                    addView(TextView(ctx).apply {
                        text = st; setTextColor(NAVY); textSize = 11f; setPadding((8 * d).toInt(), (2 * d).toInt(), (8 * d).toInt(), (2 * d).toInt())
                        background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 20 * d; setColor(if (st == "ok") Color.argb(150, 255, 255, 255) else Color.argb(190, 255, 214, 170)) }
                    })
                }
            })
            addView(TextView(ctx).apply { text = value; setTextColor(NAVY); textSize = 22f; typeface = Typeface.create("sans-serif-black", Typeface.NORMAL); setPadding(0, (4 * d).toInt(), 0, 0) })
            addView(TextView(ctx).apply { text = subText; setTextColor(NAVY2); textSize = 12f })
        }
    }

    private fun kv(ctx: Context, label: String, value: String, valueColor: Int = TXT): View {
        val d = ctx.resources.displayMetrics.density
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, (6 * d).toInt(), 0, (6 * d).toInt())
            addView(TextView(ctx).apply { text = label; setTextColor(TXT2); textSize = 14f }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(ctx).apply { text = value; setTextColor(valueColor); textSize = 14f; gravity = Gravity.END; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = (12 * d).toInt() })
        }
    }

    private fun button(ctx: Context, label: String, primary: Boolean, onClick: (MaterialButton) -> Unit): MaterialButton {
        val d = ctx.resources.displayMetrics.density
        return MaterialButton(ctx).apply {
            text = label; isAllCaps = false; textSize = 15f; minimumHeight = (52 * d).toInt(); cornerRadius = (26 * d).toInt()
            if (primary) { backgroundTintList = android.content.res.ColorStateList.valueOf(P_PEACH); setTextColor(NAVY) }
            else { backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT); setTextColor(TXT) }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8 * d).toInt() }
            setOnClickListener { onClick(this) }
        }
    }
}

// ── nafouknuté tvary ────────────────────────────────────────────────────────

/** Pastelový „nafouknutý“ blok: světlejší kopule, měkký stín na hranách dovnitř, odlesk a stín pod ním. */
class Puff(ctx: Context, private val tone: Int, private val radiusDp: Float = 26f) : LinearLayout(ctx) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowH = 10 * d
    private val edge = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(tone, Color.parseColor("#4B4FA0"), 0.40f), 140)

    init {
        orientation = VERTICAL; setWillNotDraw(false); setLayerType(LAYER_TYPE_SOFTWARE, null)
        setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (12 * d + shadowH).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height - shadowH
        val rect = RectF(2 * d, 0f, w - 2 * d, h); val r = min(radiusDp * d, h / 2)
        p.reset(); p.isAntiAlias = true; p.color = Color.argb(85, 4, 10, 38); p.maskFilter = BlurMaskFilter(9 * d, BlurMaskFilter.Blur.NORMAL)
        c.drawRoundRect(RectF(rect.left + 3 * d, rect.top + 7 * d, rect.right - 3 * d, rect.bottom + 6 * d), r, r, p)
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(0f, 0f, 0f, h, ColorUtils.blendARGB(tone, Color.WHITE, 0.38f), tone, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, r, r, p)
        c.save(); c.clipPath(Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) })
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = 18 * d; p.color = edge; p.maskFilter = BlurMaskFilter(10 * d, BlurMaskFilter.Blur.NORMAL)
        c.drawRoundRect(rect, r, r, p)
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(rect.left + rect.width() * 0.28f, h * 0.28f, max(rect.width(), h) * 0.65f, intArrayOf(0x70FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, r, r, p)
        c.restore()
        p.reset(); p.isAntiAlias = true; p.color = Color.argb(150, 255, 255, 255); p.maskFilter = BlurMaskFilter(2.5f * d, BlurMaskFilter.Blur.NORMAL)
        val sx = rect.left + r * 0.8f
        c.drawRoundRect(RectF(sx, 5 * d, sx + min(rect.width() * 0.25f, 56 * d), 9.5f * d), 3 * d, 3 * d, p)
    }
}

/** Nafouknutá pěticípá hvězda se zaoblenými cípy. */
class PuffStar(ctx: Context, private val tone: Int) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init { setLayerType(LAYER_TYPE_SOFTWARE, null); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f + height * 0.03f; val R = min(width, height) * 0.44f
        val path = Path()
        for (i in 0 until 10) {
            val ang = Math.toRadians(-90.0 + i * 36.0); val rad = if (i % 2 == 0) R else R * 0.52f
            val x = (cx + rad * cos(ang)).toFloat(); val y = (cy + rad * sin(ang)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val round = CornerPathEffect(R * 0.28f)
        p.reset(); p.isAntiAlias = true; p.pathEffect = round
        p.shader = RadialGradient(cx - R * 0.25f, cy - R * 0.3f, R * 1.3f, ColorUtils.blendARGB(tone, Color.WHITE, 0.55f), tone, Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        c.save(); c.clipPath(path)
        p.reset(); p.isAntiAlias = true; p.pathEffect = round; p.style = Paint.Style.STROKE; p.strokeWidth = R * 0.5f
        p.color = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(tone, Color.parseColor("#4B4FA0"), 0.35f), 120); p.maskFilter = BlurMaskFilter(R * 0.3f, BlurMaskFilter.Blur.NORMAL)
        c.drawPath(path, p); c.restore()
    }
}

/** Pozadí okna: noční obloha. Hvězdy jsou řidší dolů, aby nerušily čitelnost textu. */
class NightSkyView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stars = Random(7).let { r -> List(70) { Triple(r.nextFloat(), r.nextFloat().pow(1.8f), r.nextFloat()) } }
    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), SKY_TOP, SKY_BOTTOM, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint); paint.shader = null
        stars.forEach { (x, y, s) ->
            paint.color = Color.WHITE; paint.alpha = ((60 + 140 * s) * (1f - 0.7f * y)).toInt()
            c.drawCircle(x * width, y * height, (0.6f + 1.3f * s) * d, paint)
        }
    }
}

/** Měsíc jako měřidlo: osvětlená část = podíl přespané doby z potřeby. Při otevření okna se jednou „rozsvítí“. */
class MoonView(ctx: Context, private val animate: Boolean) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shown = 0f
    private var target = 0f

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES; setLayerType(LAYER_TYPE_SOFTWARE, null) }

    fun setFraction(f: Float) {
        target = f.coerceIn(0f, 1f)
        if (animate && !reducedMotion(context)) {
            ValueAnimator.ofFloat(0f, target).apply { duration = 1100; interpolator = DecelerateInterpolator(); addUpdateListener { shown = it.animatedValue as Float; invalidate() } }.start()
        } else { shown = target; invalidate() }
    }

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val r = min(width, height) / 2f - 8 * d; val cx = width / 2f; val cy = height / 2f
        if (shown > 0.02f) { paint.reset(); paint.isAntiAlias = true; paint.color = Color.argb((60 * shown).toInt(), 255, 241, 196); paint.maskFilter = BlurMaskFilter(12 * d, BlurMaskFilter.Blur.NORMAL); c.drawCircle(cx, cy, r + 6 * d, paint) }
        paint.reset(); paint.isAntiAlias = true
        paint.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.4f, Color.parseColor("#2A3F8E"), Color.parseColor("#101F55"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, paint)
        paint.reset(); paint.isAntiAlias = true
        paint.shader = RadialGradient(cx - r * 0.25f, cy - r * 0.3f, r * 1.3f, Color.parseColor("#FFFBE6"), Color.parseColor("#F6D98F"), Shader.TileMode.CLAMP)
        c.drawPath(litPath(cx, cy, r, shown), paint)
        paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * d; paint.color = Color.argb(120, 200, 215, 245)
        c.drawCircle(cx, cy, r, paint)
    }
}

/** Sloupce posledních nocí; klepnutím se vybere noc, vybraná je zvýrazněná. */
class NightsChartView(ctx: Context, private val items: List<Triple<String, Int?, String>>, private val selected: String?,
                      private val onPick: (String) -> Unit) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        if (e.action == android.view.MotionEvent.ACTION_UP) {
            val i = (e.x / (width / items.size.toFloat())).toInt().coerceIn(0, items.size - 1)
            performClick(); onPick(items[i].third)
        }
        return true
    }
    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density; val labelH = 18 * d; val top = 16 * d
        val maxSec = maxOf(9 * 3600, items.maxOfOrNull { it.second ?: 0 } ?: 0).toFloat()
        val plotH = height - labelH - top; val slot = width / items.size.toFloat(); val bw = slot * 0.62f
        fun y(sec: Float) = top + plotH * (1 - sec / maxSec)
        paint.color = TXT2; paint.strokeWidth = d; paint.style = Paint.Style.STROKE
        paint.pathEffect = DashPathEffect(floatArrayOf(6 * d, 5 * d), 0f)
        c.drawLine(0f, y(NEED_SEC.toFloat()), width.toFloat(), y(NEED_SEC.toFloat()), paint)
        paint.pathEffect = null; paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER; paint.textSize = 10 * d
        items.forEachIndexed { i, (label, sec, date) ->
            val cx = slot * (i + 0.5f); val sel = date == selected
            if (sec != null) {
                val base = if (sec >= 7 * 3600) P_SKY else if (sec >= 6 * 3600) P_LAV else P_PEACH
                val rect = RectF(cx - bw / 2, y(sec.toFloat()), cx + bw / 2, y(0f))
                paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, ColorUtils.blendARGB(base, Color.WHITE, 0.35f), base, Shader.TileMode.CLAMP)
                paint.alpha = if (sel) 255 else 130
                c.drawRoundRect(rect, bw / 2, bw / 2, paint); paint.shader = null; paint.alpha = 255
                if (sel) { paint.color = TXT; c.drawText("%.1f".format(sec / 3600f), cx, rect.top - 4 * d, paint) }
            }
            paint.color = if (sel) TXT else TXT2; c.drawText(label, cx, height - 3 * d, paint)
        }
    }
}

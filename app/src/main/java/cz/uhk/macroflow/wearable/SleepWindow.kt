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
import kotlin.math.pow
import kotlin.random.Random

// Provizorní tokeny (aplikace zatím nemá DESIGN.md): noční modrá, jeden teplý akcent převzatý z aplikace (brand_accent_warm).
private val SKY_TOP = Color.parseColor("#071330")
private val SKY_BOTTOM = Color.parseColor("#1B3B7A")
private val TXT = Color.parseColor("#EEF4FF")
private val TXT2 = Color.parseColor("#BCD0F0")
private val LINE = Color.parseColor("#2EFFFFFF")
private val C_DEEP = Color.parseColor("#5B6FE0")
private val C_LIGHT = Color.parseColor("#86B7F5")
private val C_REM = Color.parseColor("#BBA7EE")
private val C_AWAKE = Color.parseColor("#E9B072")
private val C_OK = Color.parseColor("#8FD9C4")
private val MOON = Color.parseColor("#F6F1CF")
private const val NEED_SEC = 8 * 3600

internal fun fmtDur(sec: Int?): String = sec?.let { "${it / 3600} h ${(it % 3600) / 60} min" } ?: "–"

/** Poslední noc s daty z posledních 8 dnů. */
internal fun latestNight(days: List<WearableDayEntity>): WearableDayEntity? = days.filter { it.sleepSec != null }.maxByOrNull { it.date }

internal fun scoreWord(score: Int?): String? = score?.let { when { it >= 90 -> "výborný"; it >= 80 -> "dobrý"; it >= 60 -> "přijatelný"; else -> "slabý" } }

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

internal fun starPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    moveTo(cx, cy - r); quadTo(cx, cy, cx + r, cy); quadTo(cx, cy, cx, cy + r); quadTo(cx, cy, cx - r, cy); quadTo(cx, cy, cx, cy - r); close()
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
                        listOfNotNull(fmtDur(last.sleepSec), last.sleepScore?.let { "skóre $it" }, last.bbSleepGain?.let { "Body Battery +$it" }).joinToString(" · ")
                    else -> "Dnešní noc ještě není načtená (poslední: ${fmtDur(last.sleepSec)})"
                }
            }
        }
    }
}

class SleepWindow : DialogFragment() {
    private lateinit var col: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext(); val d = ctx.resources.displayMetrics.density
        col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx).apply {
            clipToPadding = false
            setPadding((20 * d).toInt(), (44 * d).toInt(), (20 * d).toInt(), (32 * d).toInt()); addView(col)
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
                    dao.getDaysSync(today.minusDays(8).toString(), today.toString()),
                    dao.getActivitiesSync(today.minusDays(1).toString()) + dao.getActivitiesSync(today.toString()),
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
        val night = latestNight(days)
        val isToday = night?.date == LocalDate.now().toString()

        // ── Hrdina okna: měsíc svítí podle toho, kolik z 8 hodin se přespalo ──
        val frac = ((night?.sleepSec ?: 0) / NEED_SEC.toFloat()).coerceIn(0f, 1f)
        val moon = MoonView(ctx, animate = true).apply {
            contentDescription = if (night == null) "Měsíc: žádná změřená noc" else "Měsíc svítí z ${(frac * 100).toInt()} % potřeby spánku"
        }
        val heroText = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding((20 * d).toInt(), 0, 0, 0)
            addView(small(ctx, if (night == null) "Zatím žádná noc" else if (isToday) "Tuto noc" else "Poslední změřená noc, ${night.date}"))
            addView(TextView(ctx).apply {
                setTextColor(TXT); typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
                text = if (night?.sleepSec == null) "–" else bigDuration(night.sleepSec)
            })
            val word = scoreWord(night?.sleepScore)
            if (night != null) addView(small(ctx, (word?.let { "Skóre ${night.sleepScore} · $it" } ?: "") + (if (word != null) "\n" else "") + "${(frac * 100).toInt()} % z 8 hodin"))
        }
        col.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(moon, LinearLayout.LayoutParams((128 * d).toInt(), (128 * d).toInt()))
            addView(heroText, LinearLayout.LayoutParams(0, -2, 1f))
        })
        moon.setFraction(frac)
        if (night != null && !isToday)
            col.addView(small(ctx, "Dnešní noc se ještě nenačetla. Nech hodinky synchronizovat přes Garmin Connect a pak zde.").apply { setPadding(0, (12 * d).toInt(), 0, 0) })

        if (night != null) {
            val total = (night.sleepSec ?: 1).coerceAtLeast(1).toFloat()
            fun pct(v: Int?) = (v ?: 0) / total * 100f
            section(ctx, "Stavba noci") { c ->
                // Doporučená rozmezí: REM a lehký podle Garminu, hluboký orientačně.
                stageRow(ctx, c, "Hluboký", night.deepSec, pct(night.deepSec), 16, 33, C_DEEP)
                stageRow(ctx, c, "REM", night.remSec, pct(night.remSec), 21, 31, C_REM)
                stageRow(ctx, c, "Lehký", night.lightSec, pct(night.lightSec), 30, 64, C_LIGHT)
                c.addView(kv(ctx, "Vzhůru", "${fmtDur(night.awakeSec)} · ${pct(night.awakeSec).toInt()} %", C_AWAKE))
                c.addView(small(ctx, "Svislá značka je tvůj podíl, čára pod ní doporučené rozmezí. Garmin posílá součty fází, ne průběh noci.").apply { setPadding(0, (8 * d).toInt(), 0, 0) })
            }
            section(ctx, "Regenerace") { c ->
                night.bbSleepGain?.let { g ->
                    c.addView(kv(ctx, "Body Battery", "+$g", TXT))
                    c.addView(GainBarView(ctx, g).apply { contentDescription = "Spánek nabil Body Battery o $g" }, LinearLayout.LayoutParams(-1, (10 * d).toInt()).apply { bottomMargin = (6 * d).toInt() })
                }
                night.avgSpo2?.let { c.addView(kv(ctx, "SpO₂", "${it.toInt()} %" + (night.lowSpo2?.let { l -> " (min $l %)" } ?: ""))) }
                night.avgResp?.let { c.addView(kv(ctx, "Dech", "${it.toInt()} / min")) }
                night.sleepAvgHr?.let { c.addView(kv(ctx, "Tep ve spánku", "${it.toInt()}")) }
                night.sleepStress?.let { c.addView(kv(ctx, "Stres ve spánku", "${it.toInt()}")) }
                night.awakeCount?.let { c.addView(kv(ctx, "Probuzení", "$it×")) }
            }
        }

        section(ctx, "Posledních 7 nocí") { c ->
            val byDate = days.associateBy { it.date }
            val items = (6 downTo 0).map { back ->
                val dt = LocalDate.now().minusDays(back.toLong())
                dt.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("cs")) to byDate[dt.toString()]?.sleepSec
            }
            c.addView(WeekBarsView(ctx, items).apply {
                contentDescription = "Délka spánku za posledních 7 nocí, čárkovaně osm hodin"
            }, LinearLayout.LayoutParams(-1, (140 * d).toInt()))
            c.addView(small(ctx, "Čárkovaně 8 h"))
        }

        val sums = ActivityGroups.summarize(data.acts)
        val note = ActivityGroups.planNote(data.plan, data.planCardio, data.acts.filter { it.date == LocalDate.now().toString() })
        val fullDay = days.lastOrNull { it.totalKcal != null && GarminParser.isWorn(it) }
        if (sums.isNotEmpty() || note != null || fullDay != null) section(ctx, "Pohyb z hodinek") { c ->
            sums.forEach { c.addView(kv(ctx, "${it.group.label} (${it.count}×)", "${it.minutes} min · ${it.kcal} kcal")) }
            note?.let { c.addView(TextView(ctx).apply { text = it; setTextColor(C_AWAKE); textSize = 14f; setPadding(0, (6 * d).toInt(), 0, 0) }) }
            fullDay?.let { c.addView(small(ctx, "Poslední plný den ${it.date}: aktivní ${it.activeKcal} kcal, celkem ${it.totalKcal} kcal, ${it.steps} kroků").apply { setPadding(0, (8 * d).toInt(), 0, 0) }) }
        }

        data.acc?.let { (n, mae) ->
            section(ctx, "Odhad ranního rituálu") { c ->
                c.addView(TextView(ctx).apply {
                    text = "Z hodinek odhadnuto $n×, průměrná odchylka od tvých hodnot %.1f bodu.".format(mae); setTextColor(TXT); textSize = 14f
                })
            }
        }

        val actions = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, (24 * d).toInt(), 0, 0) }
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

    // ── stavební prvky ──────────────────────────────────────────────────────

    private fun bigDuration(sec: Int): CharSequence {
        val sb = SpannableStringBuilder()
        fun part(num: String, unit: String) {
            val s = sb.length; sb.append(num); sb.setSpan(RelativeSizeSpan(2.7f), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(" $unit ")
        }
        part("${sec / 3600}", "h"); part("${(sec % 3600) / 60}", "min")
        return sb.trimEnd()
    }

    private fun small(ctx: Context, s: String) = TextView(ctx).apply { text = s; setTextColor(TXT2); textSize = 13f }

    private fun kv(ctx: Context, label: String, value: String, valueColor: Int = TXT): View {
        val d = ctx.resources.displayMetrics.density
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, (7 * d).toInt(), 0, (7 * d).toInt())
            addView(TextView(ctx).apply { text = label; setTextColor(TXT2); textSize = 15f }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(ctx).apply { text = value; setTextColor(valueColor); textSize = 15f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
        }
    }

    private fun stageRow(ctx: Context, c: LinearLayout, name: String, sec: Int?, pct: Float, lo: Int, hi: Int, color: Int) {
        val d = ctx.resources.displayMetrics.density
        val inRange = pct >= lo && pct <= hi
        c.addView(kv(ctx, name, "${fmtDur(sec)} · ${pct.toInt()} %"))
        c.addView(StageRangeView(ctx, pct, lo, hi, color, if (inRange) C_OK else C_AWAKE).apply {
            contentDescription = "$name ${pct.toInt()} %, doporučeno $lo až $hi %, " + if (inRange) "v rozmezí" else if (pct < lo) "pod rozmezím" else "nad rozmezím"
        }, LinearLayout.LayoutParams(-1, (22 * d).toInt()))
    }

    /** Sekce bez karty: tenká linka, nadpis větou a obsah přímo na obloze. */
    private fun section(ctx: Context, title: String, body: (LinearLayout) -> Unit) {
        val d = ctx.resources.displayMetrics.density
        col.addView(View(ctx).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(-1, d.toInt().coerceAtLeast(1)).apply { topMargin = (26 * d).toInt() })
        col.addView(TextView(ctx).apply {
            text = title; setTextColor(TXT); textSize = 17f; typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setPadding(0, (14 * d).toInt(), 0, (6 * d).toInt())
        })
        val inner = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        body(inner); col.addView(inner)
    }

    private fun button(ctx: Context, label: String, primary: Boolean, onClick: (MaterialButton) -> Unit): MaterialButton {
        val d = ctx.resources.displayMetrics.density
        return MaterialButton(ctx).apply {
            text = label; isAllCaps = false; textSize = 15f; minimumHeight = (52 * d).toInt(); cornerRadius = (26 * d).toInt()
            if (primary) { backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E9B072")); setTextColor(Color.parseColor("#1A1305")) }
            else { backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT); setTextColor(TXT) }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8 * d).toInt() }
            setOnClickListener { onClick(this) }
        }
    }
}

// ── kreslené prvky ──────────────────────────────────────────────────────────

/** Pozadí okna: noční obloha. Hvězdy jsou řidší dolů, aby nerušily čitelnost textu. */
class NightSkyView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stars = Random(7).let { r -> List(80) { Triple(r.nextFloat(), r.nextFloat().pow(1.8f), r.nextFloat()) } }
    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), SKY_TOP, SKY_BOTTOM, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint); paint.shader = null
        stars.forEach { (x, y, s) ->
            paint.color = Color.WHITE; paint.alpha = ((70 + 150 * s) * (1f - 0.6f * y)).toInt()
            if (s > 0.85f) c.drawPath(starPath(x * width, y * height, (3 + 4 * s) * d), paint)
            else c.drawCircle(x * width, y * height, (0.6f + 1.3f * s) * d, paint)
        }
    }
}

/** Měsíc jako měřidlo: osvětlená část = podíl přespané doby z potřeby. Při otevření okna se jednou „rozsvítí“. */
class MoonView(ctx: Context, private val animate: Boolean) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shown = 0f
    private var target = 0f

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }

    fun setFraction(f: Float) {
        target = f.coerceIn(0f, 1f)
        if (animate && !reducedMotion(context)) {
            ValueAnimator.ofFloat(0f, target).apply { duration = 1100; interpolator = DecelerateInterpolator(); addUpdateListener { shown = it.animatedValue as Float; invalidate() } }.start()
        } else { shown = target; invalidate() }
    }

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val r = minOf(width, height) / 2f - 6 * d; val cx = width / 2f; val cy = height / 2f
        if (shown > 0.02f) { paint.color = Color.argb((50 * shown).toInt(), 246, 241, 207); c.drawCircle(cx, cy, r + 5 * d, paint) }
        paint.color = Color.parseColor("#0F2350"); paint.style = Paint.Style.FILL; c.drawCircle(cx, cy, r, paint)
        paint.color = MOON; c.drawPath(litPath(cx, cy, r, shown), paint)
        paint.color = Color.argb(110, 188, 208, 240); paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * d; c.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.FILL
    }
}

/** Řádek fáze: dráha, tvůj podíl (značka) a pod ní čára s doporučeným rozmezím. Barva značky říká, zda jsi v rozmezí. */
class StageRangeView(ctx: Context, private val pct: Float, private val lo: Int, private val hi: Int, private val color: Int, private val status: Int) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density; val w = width.toFloat(); val max = 80f
        fun x(p: Float) = w * (p.coerceIn(0f, max) / max)
        val trackTop = 3 * d; val trackH = 8 * d
        paint.color = LINE; c.drawRoundRect(0f, trackTop, w, trackTop + trackH, trackH / 2, trackH / 2, paint)
        paint.color = color; c.drawRoundRect(0f, trackTop, maxOf(x(pct), trackH), trackTop + trackH, trackH / 2, trackH / 2, paint)
        paint.color = status; c.drawRoundRect(x(pct) - 1.5f * d, 0f, x(pct) + 1.5f * d, trackTop * 2 + trackH, 1.5f * d, 1.5f * d, paint)
        val by = height - 3 * d; paint.color = TXT2; paint.strokeWidth = 2 * d
        c.drawLine(x(lo.toFloat()), by, x(hi.toFloat()), by, paint)
        c.drawLine(x(lo.toFloat()), by - 3 * d, x(lo.toFloat()), by + 3 * d, paint); c.drawLine(x(hi.toFloat()), by - 3 * d, x(hi.toFloat()), by + 3 * d, paint)
    }
}

class GainBarView(ctx: Context, private val gain: Int) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val r = height / 2f; paint.color = LINE
        c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, paint)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, C_DEEP, C_LIGHT, Shader.TileMode.CLAMP)
        c.drawRoundRect(0f, 0f, width * (gain.coerceIn(0, 100) / 100f).coerceAtLeast(0.04f), height.toFloat(), r, r, paint)
        paint.shader = null
    }
}

class WeekBarsView(ctx: Context, private val items: List<Pair<String, Int?>>) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density; val labelH = 18 * d; val top = 16 * d
        val maxSec = maxOf(9 * 3600, items.maxOfOrNull { it.second ?: 0 } ?: 0).toFloat()
        val plotH = height - labelH - top; val slot = width / items.size.toFloat(); val bw = slot * 0.46f
        fun y(sec: Float) = top + plotH * (1 - sec / maxSec)
        paint.color = TXT2; paint.strokeWidth = d; paint.style = Paint.Style.STROKE
        paint.pathEffect = DashPathEffect(floatArrayOf(6 * d, 5 * d), 0f)
        c.drawLine(0f, y(NEED_SEC.toFloat()), width.toFloat(), y(NEED_SEC.toFloat()), paint)
        paint.pathEffect = null; paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER; paint.textSize = 11 * d
        val lastIdx = items.indexOfLast { it.second != null }
        items.forEachIndexed { i, (label, sec) ->
            val cx = slot * (i + 0.5f)
            if (sec != null) {
                val base = if (sec >= 7 * 3600) C_LIGHT else if (sec >= 6 * 3600) C_REM else C_AWAKE
                paint.color = base; paint.alpha = if (i == lastIdx) 255 else 150
                c.drawRoundRect(cx - bw / 2, y(sec.toFloat()), cx + bw / 2, y(0f), 6 * d, 6 * d, paint)
                paint.alpha = 255; paint.color = TXT; c.drawText("%.1f".format(sec / 3600f), cx, y(sec.toFloat()) - 4 * d, paint)
            }
            paint.color = TXT2; c.drawText(label, cx, height - 3 * d, paint)
        }
    }
}

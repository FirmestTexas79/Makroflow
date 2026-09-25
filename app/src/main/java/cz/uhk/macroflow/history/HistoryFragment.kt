package cz.uhk.macroflow.history

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.CheckInEntity
import cz.uhk.macroflow.data.ConsumedSnackDao
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.dashboard.MacroCalculator
import cz.uhk.macroflow.dashboard.MacroFlowEngine
import cz.uhk.macroflow.energy.WeightProjection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import kotlin.math.*

class HistoryFragment : Fragment() {

    private lateinit var graph: SymmetryGraphView
    private lateinit var projectionChart: WeightProjectionView
    private lateinit var tvDate: TextView
    private lateinit var tvKcal: TextView
    private lateinit var tvTraining: TextView
    private lateinit var tvProtein: TextView
    private lateinit var tvCarbs: TextView
    private lateinit var tvFat: TextView
    private lateinit var tvSymmetryStatus: TextView
    private lateinit var tvMonthLabel: TextView
    private lateinit var calGrid: GridLayout
    private lateinit var layoutNoMetrics: View
    private lateinit var tvStepsCount: TextView
    private lateinit var tvStepsBurned: TextView
    private lateinit var tvFiber: TextView
    private lateinit var heatmap: ActivityHeatmapView
    private lateinit var heatChips: LinearLayout
    private lateinit var tvHeatSummary: TextView
    private lateinit var tvHeatNote: TextView
    private var heatMetric = HeatMetric.STEPS
    /** Spočtené hodnoty podle metriky – výdej a cíle jsou dražší (model pro každý den). */
    private val heatCache = mutableMapOf<HeatMetric, Map<LocalDate, Double?>>()

    companion object {
        private const val HEAT_WEEKS = 53
    }

    private val monthSdf = SimpleDateFormat("LLLL yyyy", Locale("cs"))
    private val calendar = Calendar.getInstance()
    private val dateKeySdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var selectedDateKey: String = dateKeySdf.format(Date())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)

        graph            = view.findViewById(R.id.historySymmetryGraph)
        projectionChart  = view.findViewById(R.id.projectionChart)
        tvDate           = view.findViewById(R.id.tvSelectedDate)
        tvKcal           = view.findViewById(R.id.tvHistoryKcal)
        tvTraining       = view.findViewById(R.id.tvHistoryTraining)
        tvProtein        = view.findViewById(R.id.tvHistoryProtein)
        tvCarbs          = view.findViewById(R.id.tvHistoryCarbs)
        tvFat            = view.findViewById(R.id.tvHistoryFat)
        tvFiber          = view.findViewById(R.id.tvHistoryFiber)
        tvSymmetryStatus = view.findViewById(R.id.tvSymmetryStatus)
        tvMonthLabel     = view.findViewById(R.id.tvMonthLabel)
        calGrid          = view.findViewById(R.id.calGrid)
        layoutNoMetrics  = view.findViewById(R.id.layoutNoMetrics)
        tvStepsCount     = view.findViewById(R.id.tvHistoryStepsCount)
        tvStepsBurned    = view.findViewById(R.id.tvHistoryStepsBurned)
        heatmap          = view.findViewById(R.id.activityHeatmap)
        heatChips        = view.findViewById(R.id.heatMetricChips)
        tvHeatSummary    = view.findViewById(R.id.tvHeatSummary)
        tvHeatNote       = view.findViewById(R.id.tvHeatNote)

        tvCalToggle = view.findViewById(R.id.tvCalToggle)
        btnNextMonth = view.findViewById(R.id.btnNextMonth)
        weekAnchor = LocalDate.parse(selectedDateKey)
        calExpanded = requireContext().getSharedPreferences("HistoryPrefs", Context.MODE_PRIVATE).getBoolean("cal_expanded", false)
        view.findViewById<ImageButton>(R.id.btnPrevMonth).setOnClickListener {
            if (calExpanded) calendar.add(Calendar.MONTH, -1) else weekAnchor = weekAnchor.minusWeeks(1)
            renderCalendar()
        }
        view.findViewById<ImageButton>(R.id.btnNextMonth).setOnClickListener {
            if (!calExpanded) {
                if (CalendarWeek.canGoForward(weekAnchor, LocalDate.now())) { weekAnchor = weekAnchor.plusWeeks(1); renderCalendar() }
                return@setOnClickListener
            }
            val now = Calendar.getInstance()
            if (calendar.get(Calendar.YEAR) < now.get(Calendar.YEAR) ||
                (calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                        calendar.get(Calendar.MONTH) < now.get(Calendar.MONTH))) {
                calendar.add(Calendar.MONTH, 1)
                renderCalendar()
            }
        }
        view.findViewById<TextView>(R.id.tvCalToggle).setOnClickListener {
            calExpanded = !calExpanded
            requireContext().getSharedPreferences("HistoryPrefs", Context.MODE_PRIVATE).edit().putBoolean("cal_expanded", calExpanded).apply()
            if (calExpanded) {
                calendar.time = Date.from(weekAnchor.atStartOfDay(ZoneId.systemDefault()).toInstant())
                calendar.set(Calendar.DAY_OF_MONTH, 1)
            } else weekAnchor = LocalDate.parse(selectedDateKey)
            renderCalendar()
        }

        calendar.set(Calendar.DAY_OF_MONTH, 1)
        renderCalendar()
        loadData(selectedDateKey)

        heatMetric = HeatMetric.entries.firstOrNull {
            it.name == requireContext().getSharedPreferences("HistoryPrefs", Context.MODE_PRIVATE).getString("heat_metric", null)
        } ?: HeatMetric.STEPS
        heatmap.onDayClick = { d -> selectDate(dateKeySdf.format(Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant()))) }
        buildHeatChips()
        loadHeatmap()

        return view
    }

    /** Týden ⇄ celý měsíc (docs/adr/0022); výchozí je kompaktní týden vybraného dne. */
    private var calExpanded = false
    private var tvCalToggle: TextView? = null
    private var btnNextMonth: View? = null
    private var weekAnchor: LocalDate = LocalDate.now()

    private fun renderCalendar() {
        calGrid.removeAllViews()
        val today = dateKeySdf.format(Date())
        tvCalToggle?.text = if (calExpanded) "JEN TÝDEN  ▴" else "CELÝ MĚSÍC  ▾"
        if (!calExpanded) {
            val now = LocalDate.now()
            tvMonthLabel.text = CalendarWeek.label(weekAnchor, now)
            btnNextMonth?.alpha = if (CalendarWeek.canGoForward(weekAnchor, now)) 1f else 0.35f
            CalendarWeek.days(weekAnchor).forEach { d ->
                val key = d.toString()
                calGrid.addView(makeDayCell(d.dayOfMonth.toString(), key, key == today, key == selectedDateKey,
                    isFuture = d.isAfter(now), isWeekend = d.dayOfWeek.value >= 6))
            }
            return
        }
        btnNextMonth?.alpha = 1f
        tvMonthLabel.text = monthSdf.format(calendar.time).replaceFirstChar { it.uppercase() }
        val displayCal = calendar.clone() as Calendar
        displayCal.set(Calendar.DAY_OF_MONTH, 1)
        var firstDow = displayCal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
        if (firstDow < 0) firstDow += 7
        val daysInMonth = displayCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        repeat(firstDow) { calGrid.addView(makeDayCell("", null, false, false)) }
        for (day in 1..daysInMonth) {
            displayCal.set(Calendar.DAY_OF_MONTH, day)
            val dateKey = dateKeySdf.format(displayCal.time)
            val isToday = dateKey == today
            val isSelected = dateKey == selectedDateKey
            val isFuture = displayCal.after(Calendar.getInstance())
            val isWeekend = displayCal.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.SUNDAY)
            calGrid.addView(makeDayCell(day.toString(), dateKey, isToday, isSelected, isFuture, isWeekend))
        }
    }

    private fun makeDayCell(label: String, dateKey: String?, isToday: Boolean, isSelected: Boolean, isFuture: Boolean = false, isWeekend: Boolean = false): View {
        val cell = TextView(requireContext())
        val size = (resources.displayMetrics.widthPixels - dpToPx(20*2 + 4*2 + 8*2 + 4*7)) / 7
        cell.layoutParams = GridLayout.LayoutParams().apply { width = size; height = size; setMargins(2, 2, 2, 2) }
        cell.gravity = Gravity.CENTER
        cell.text = label
        cell.textSize = 13f
        when {
            label.isEmpty() -> { }
            isSelected -> {
                cell.setBackgroundResource(R.drawable.bg_status_pill)
                cell.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DDA15E"))
                cell.setTextColor(Color.parseColor("#283618"))
                cell.typeface = Typeface.DEFAULT_BOLD
            }
            isToday -> {
                cell.setBackgroundResource(R.drawable.bg_status_pill)
                cell.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#30FEFAE0"))
                cell.setTextColor(Color.parseColor("#FEFAE0"))
                cell.typeface = Typeface.DEFAULT_BOLD
            }
            isFuture -> cell.setTextColor(Color.parseColor("#30FEFAE0"))
            isWeekend -> cell.setTextColor(Color.parseColor("#70FEFAE0"))
            else -> cell.setTextColor(Color.parseColor("#BDFEFAE0"))
        }
        if (dateKey != null && !isFuture) {
            cell.setOnClickListener { selectDate(dateKey) }
        }
        return cell
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    /** Výběr dne z kalendáře i z heatmapy: kalendář přeskočí na měsíc dne. */
    private fun selectDate(dateKey: String) {
        selectedDateKey = dateKey
        weekAnchor = LocalDate.parse(dateKey)
        dateKeySdf.parse(dateKey)?.let { calendar.time = it; calendar.set(Calendar.DAY_OF_MONTH, 1) }
        renderCalendar()
        loadData(dateKey)
        if (::heatmap.isInitialized) heatmap.setSelected(LocalDate.parse(dateKey))
    }

    // ── Heatmapa aktivity ────────────────────────────────────────────────────

    private fun buildHeatChips() {
        val dp = resources.displayMetrics.density
        heatChips.removeAllViews()
        HeatMetric.entries.forEach { m ->
            heatChips.addView(TextView(requireContext()).apply {
                text = m.label
                textSize = 11.5f
                val sel = m == heatMetric
                setTextColor(Color.parseColor(if (sel) "#283618" else "#FEFAE0"))
                typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
                setBackgroundResource(R.drawable.bg_status_pill)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (sel) "#E9B072" else "#30FEFAE0"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { marginEnd = (6 * dp).toInt() }
                setOnClickListener {
                    if (heatMetric == m) return@setOnClickListener
                    heatMetric = m
                    requireContext().getSharedPreferences("HistoryPrefs", Context.MODE_PRIVATE)
                        .edit().putString("heat_metric", m.name).apply()
                    buildHeatChips()
                    loadHeatmap()
                }
            })
        }
    }

    private fun loadHeatmap() {
        val metric = heatMetric
        val today = LocalDate.now()
        val columns = Heatmap.weekColumns(today, HEAT_WEEKS)
        tvHeatNote.text = when (metric) {
            HeatMetric.STEPS -> "Hranice 4 000 / 7 000 / 10 000 kroků – přínos pro zdraví se u dospělých ustaluje kolem 8–10 tisíc."
            HeatMetric.ACTIVE_KCAL -> "Chůze z naměřených kroků + trénink podle plánu (ne celkový výdej). Stupně = čtvrtiny tvých vlastních dní."
            HeatMetric.GOALS -> "Kolik ze 4 cílů (kalorie, bílkoviny, sacharidy, tuky) padlo do pásma. Počítají se jen uzavřené dny."
        }
        val cached = heatCache[metric]
        if (cached == null) tvHeatSummary.text = "Počítám…"
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val values = cached ?: withContext(Dispatchers.IO) {
                HeatmapRepository.load(ctx, metric, columns.first().first()!!, today)
            }.also { heatCache[metric] = it }
            if (!isAdded || heatMetric != metric) return@launch
            heatmap.setData(columns, Heatmap.levels(metric, values), LocalDate.parse(selectedDateKey))
            tvHeatSummary.text = Heatmap.summary(metric, values, today)
            heatmap.contentDescription = "Heatmapa ${metric.label}: ${tvHeatSummary.text}"
        }
    }


    private fun loadData(dateKey: String) {
        val parsed = dateKeySdf.parse(dateKey) ?: Date()
        val isToday = dateKey == dateKeySdf.format(Date())
        tvDate.text = if (isToday) "DNES" else dateKey

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())

            // Snědené za den
            val consumedList = withContext(Dispatchers.IO) {
                db.consumedSnackDao().getConsumedByDateSync(dateKey)
            }

            // Cíle i výdej pro ten den ze stejného modelu jako dashboard
            // (kroky dne jsou už započtené v cíli – nic dalšího nepřičítat)
            val ctx = requireContext().applicationContext
            val status = withContext(Dispatchers.IO) {
                MacroFlowEngine.calculateDailyStatusForDate(ctx, parsed, consumedList)
            }
            val targetData = status.target

            tvStepsCount.text = String.format("%,d", status.stepsCount).replace(',', ' ')
            tvStepsBurned.text = "+${status.stepsCalories.toInt()} kcal"

            // Formát: "Snědeno / Cíl"
            tvKcal.text = "${status.eatenCal.toInt()} / ${targetData.calories.toInt()} kcal"
            tvProtein.text = "${status.eatenP.toInt()} / ${targetData.protein.toInt()} g"
            tvCarbs.text = "${status.eatenS.toInt()} / ${targetData.carbs.toInt()} g"
            tvFat.text = "${status.eatenT.toInt()} / ${targetData.fat.toInt()} g"
            tvFiber.text = "${String.format("%.1f", status.eatenFiber)} / ${targetData.fiber.toInt()} g"

            tvTraining.text = targetData.trainingType

            // 7. BIOLOGICKÁ PROJEKCE A SYMETRIE
            updateProjection(dateKey)
            updateSymmetry(dateKey)
        }
    }

    // ── Biologická projekce (docs/adr/0020) ─────────────────────────────────

    private var projectionJob: kotlinx.coroutines.Job? = null

    private fun updateProjection(dateKey: String) {
        val root = view ?: return
        projectionJob?.cancel()
        projectionJob = lifecycleScope.launch {
            val ctx = requireContext().applicationContext
            val result = withContext(Dispatchers.IO) {
                runCatching { ProjectionRepository.load(ctx, LocalDate.parse(dateKey)) }
                    .onFailure { Log.e("Projection", "Výpočet projekce selhal", it) }
                    .getOrNull()
            }
            bindProjection(root, result)
        }
    }

    private fun bindProjection(root: View, r: ProjectionRepository.Result?) {
        val tag = root.findViewById<TextView>(R.id.tvCurrentTrendTag)
        val now = root.findViewById<TextView>(R.id.tvProjNow)
        val nowSub = root.findViewById<TextView>(R.id.tvProjNowSub)
        val pace = root.findViewById<TextView>(R.id.tvProjPace)
        val paceSub = root.findViewById<TextView>(R.id.tvProjPaceSub)
        val end = root.findViewById<TextView>(R.id.tvProjEnd)
        val endSub = root.findViewById<TextView>(R.id.tvProjEndSub)
        val verdictBox = root.findViewById<View>(R.id.llProjVerdict)
        val verdict = root.findViewById<TextView>(R.id.tvProjVerdict)
        val dot = root.findViewById<View>(R.id.vProjVerdictDot)
        val check = root.findViewById<TextView>(R.id.tvProjCheck)
        val source = root.findViewById<TextView>(R.id.tvProjSource)

        projectionChart.setData(r?.chart)
        if (r == null) {
            tag.text = "BEZ DAT"
            tag.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.brand_dark_alpha40))
            listOf(now, pace, end).forEach { it.text = "–" }
            listOf(nowSub, paceSub, endSub).forEach { it.text = "" }
            verdictBox.visibility = View.GONE
            check.visibility = View.GONE
            source.text = "Projekce potřebuje aspoň jedno ranní vážení do vybraného dne."
            return
        }

        val p = r.projection
        val ctx = requireContext()
        val color = ContextCompat.getColor(ctx, when (r.pace.verdict) {
            WeightProjection.Verdict.ON_TRACK -> R.color.brand_primary
            WeightProjection.Verdict.TOO_SLOW -> R.color.brand_accent_warm
            WeightProjection.Verdict.TOO_FAST, WeightProjection.Verdict.WRONG_WAY -> R.color.brand_accent_deep
            WeightProjection.Verdict.UNCERTAIN -> R.color.brand_dark_alpha40
        })
        tag.text = when (r.pace.direction) {
            WeightProjection.Direction.LOSING -> "HUBNUTÍ"
            WeightProjection.Direction.STABLE -> "STABILNÍ"
            WeightProjection.Direction.GAINING -> "NABÍRÁNÍ"
        }
        tag.backgroundTintList = ColorStateList.valueOf(color)

        now.text = "${kg(p.start.level)} kg"
        nowSub.text = "± ${kg(1.96 * p.start.sdLevel)} kg"
        val week = p.slopeKgPerWeek
        pace.text = "${signed(week)} kg"
        paceSub.text = "týdně · ${signed(r.pace.percentPerWeek)} %"
        end.text = "${kg(p.end.mean)} kg"
        endSub.text = "${kg(p.end.lower)}–${kg(p.end.upper)}"

        verdictBox.visibility = View.VISIBLE
        dot.backgroundTintList = ColorStateList.valueOf(color)
        verdict.text = r.pace.message

        // Minulý den: porovnání projekce se skutečností
        val lastActual = r.actualAfter.maxByOrNull { it.key }
        if (lastActual != null) {
            val f = p.forecast.firstOrNull { it.day == lastActual.key }
            val date = LocalDate.ofEpochDay(lastActual.key.toLong())
            val inside = f != null && lastActual.value in f.lower..f.upper
            check.visibility = View.VISIBLE
            check.text = if (f == null) "" else
                "Skutečnost ${date.dayOfMonth}.${date.monthValue}.: ${kg(lastActual.value)} kg · projekce ${kg(f.mean)} kg " +
                "(${kg(f.lower)}–${kg(f.upper)}) " + if (inside) "✓ v pásmu" else "✗ mimo pásmo"
        } else check.visibility = View.GONE

        val e = p.energy
        val energyPct = (p.energyWeight * 100).roundToInt()
        source.text = buildString {
            append("Tempo: ${100 - energyPct} % z vážení (${p.weighIns}×), $energyPct % z energetické bilance")
            if (e != null) {
                append(" – ")
                append(if (e.source == WeightProjection.IntakeSource.LOGGED) "Ø zapsaný příjem " else "cíl ")
                append("${e.intakeKcal.roundToInt()} kcal vs. výdej ${e.expenditureKcal.roundToInt()} kcal")
                append(" (${signedInt(e.balanceKcal)} kcal/den).")
            } else append(".")
            append(" Pásmo = 95% interval; body jsou ranní vážení, čára vyhlazený trend bez výkyvů vody.")
        }
    }

    private fun kg(v: Double) = String.format(Locale.US, "%.1f", v).replace('.', ',')
    private fun signed(v: Double) = (if (v > 0.049) "+" else if (v < -0.049) "−" else "") + kg(abs(v))
    private fun signedInt(v: Double) = (if (v > 0) "+" else if (v < 0) "−" else "") + abs(v).roundToInt()

    private fun updateSymmetry(dateKey: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val metrics = withContext(Dispatchers.IO) { db.bodyMetricsDao().getByDateSync(dateKey) }
            if (metrics == null || metrics.neck <= 0f) {
                graph.visibility = View.INVISIBLE
                layoutNoMetrics.visibility = View.VISIBLE
                tvSymmetryStatus.text = "BEZ DAT"
                tvSymmetryStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#30283618"))
                return@launch
            }
            graph.visibility = View.VISIBLE
            layoutNoMetrics.visibility = View.GONE
            tvSymmetryStatus.text = "AKTIVNÍ"
            tvSymmetryStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#606C38"))
            val wristEst = metrics.neck * 0.406f
            fun asymScore(actual: Float, ideal: Float, tL: Float = 0.10f, tH: Float = 0.16f): Float {
                if (ideal <= 0f) return 0.5f
                val ratio = actual / ideal
                val t = if (ratio < 1f) tL else tH
                return exp(-((ratio - 1f).pow(2)) / (2f * t.pow(2))).coerceIn(0.1f, 1f)
            }
            val sChest   = asymScore(metrics.chest, metrics.neck * 2.87f)
            val sWaist   = asymScore(metrics.waist, metrics.neck * 2.14f)
            val sBicep   = asymScore(metrics.bicep, wristEst * 2.50f)
            val sForearm = asymScore(metrics.forearm, metrics.bicep * 0.853f)
            val sAbdomen = if (metrics.abdomen <= metrics.waist) 1.0f else exp(-((metrics.abdomen - metrics.waist) / 8f).pow(2)).coerceIn(0.1f, 1.0f)
            val sThigh   = asymScore(metrics.thigh, metrics.neck * 1.70f)
            val sCalf    = asymScore(metrics.calf, (wristEst * 2.50f + metrics.bicep) / 2f)
            graph.dataPoints = floatArrayOf(sChest, sWaist, sBicep, sForearm, sAbdomen, sThigh, sCalf, 1.0f)
            graph.invalidate()
        }
    }
}
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
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import cz.uhk.macroflow.R
import cz.uhk.macroflow.analytics.BioLogicEngine
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.CheckInEntity
import cz.uhk.macroflow.data.ConsumedSnackDao
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.dashboard.MacroCalculator
import cz.uhk.macroflow.dashboard.MacroFlowEngine
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
    private lateinit var historyChart: LineChart
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
        historyChart     = view.findViewById(R.id.historyChart)
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

        setupChartStyle()

        view.findViewById<ImageButton>(R.id.btnPrevMonth).setOnClickListener {
            calendar.add(Calendar.MONTH, -1)
            renderCalendar()
        }
        view.findViewById<ImageButton>(R.id.btnNextMonth).setOnClickListener {
            val now = Calendar.getInstance()
            if (calendar.get(Calendar.YEAR) < now.get(Calendar.YEAR) ||
                (calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                        calendar.get(Calendar.MONTH) < now.get(Calendar.MONTH))) {
                calendar.add(Calendar.MONTH, 1)
                renderCalendar()
            }
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

    private fun setupChartStyle() {
        historyChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawGridBackground(false)

            xAxis.apply {
                textColor = ContextCompat.getColor(requireContext(), R.color.brand_dark)
                gridColor = ContextCompat.getColor(requireContext(), R.color.brand_dark_alpha5)
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                // Formátování osy X: Timestamp na Datum
                valueFormatter = object : ValueFormatter() {
                    private val labelSdf = SimpleDateFormat("d.M.", Locale("cs"))
                    override fun getFormattedValue(value: Float): String {
                        return labelSdf.format(Date(value.toLong()))
                    }
                }
            }

            axisLeft.apply {
                textColor = ContextCompat.getColor(requireContext(), R.color.brand_dark)
                gridColor = ContextCompat.getColor(requireContext(), R.color.brand_dark_alpha5)
                setDrawGridLines(true)
                setDrawZeroLine(false)
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
        }
    }

    private fun renderCalendar() {
        tvMonthLabel.text = monthSdf.format(calendar.time).replaceFirstChar { it.uppercase() }
        calGrid.removeAllViews()
        val today = dateKeySdf.format(Date())
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

            // 7. GRAFY A ANALYTIKA
            val allHistory = withContext(Dispatchers.IO) { db.checkInDao().getAllCheckInsSync() }
            val freshAnalytics = if (allHistory.isNotEmpty()) {
                cz.uhk.macroflow.analytics.BioLogicEngine.calculateFullAnalytics(allHistory)
            } else null

            updateBioLogicChart(allHistory, freshAnalytics)
            updateSymmetry(dateKey)
        }
    }

    // --- TATO ČÁST JE PŘEPSANÁ POŘÁDNĚ ---
    private fun updateBioLogicChart(allHistory: List<CheckInEntity>, currentAnalytics: cz.uhk.macroflow.data.AnalyticsCacheEntity?) {
        if (allHistory.isEmpty()) return

        val selectedDate = dateKeySdf.parse(selectedDateKey) ?: Date()
        val historyEntries = mutableListOf<Entry>()
        val predMainEntries = mutableListOf<Entry>()
        val upperEntries = mutableListOf<Entry>()
        val lowerEntries = mutableListOf<Entry>()
        val dayLabels = mutableMapOf<Float, String>()
        val displaySdf = SimpleDateFormat("d.M", Locale.getDefault())

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val profile = withContext(Dispatchers.IO) { db.userProfileDao().getProfileSync() }

            val rangeDays = 7f // Středový bod
            val totalDays = 14f
            val sortedHistory = allHistory.sortedBy { it.date }

            // --- 2. PLNĚNÍ HISTORIE (Index 0 až 7) ---
            val historyMap = sortedHistory.associateBy { it.date }
            for (i in 0..rangeDays.toInt()) {
                val checkCal = Calendar.getInstance().apply {
                    time = selectedDate
                    add(Calendar.DAY_OF_YEAR, -(rangeDays.toInt() - i))
                }
                val key = dateKeySdf.format(checkCal.time)
                val xPos = i.toFloat()
                dayLabels[xPos] = displaySdf.format(checkCal.time)

                historyMap[key]?.let { checkIn ->
                    historyEntries.add(Entry(xPos, checkIn.weight.toFloat()))
                }
            }

            // --- 3. PREDIKCE (Index 7 až 14) – Kalmanův trend + 95% interval ---
            // U minulého dne jen data známá k tomu dni → predikci lze porovnat se skutečností
            val selectedKey = dateKeySdf.format(selectedDate)
            val knownHistory = sortedHistory.filter { it.date <= selectedKey }
            if (knownHistory.isNotEmpty()) {
                val selectedDay = BioLogicEngine.dayOf(selectedKey)
                val lastObsDay = BioLogicEngine.dayOf(knownHistory.last().date)
                val offset = (selectedDay - lastObsDay).coerceAtLeast(0)
                val forecast = BioLogicEngine.forecast(knownHistory, days = offset + 7)

                // i=0 je vybraný den (index 7), i=7 je +7 dní (index 14)
                for (i in 0..7) {
                    val f = forecast.getOrNull(offset + i) ?: break
                    val x = rangeDays + i
                    predMainEntries.add(Entry(x, f.mean.toFloat()))
                    upperEntries.add(Entry(x, f.upper.toFloat()))
                    lowerEntries.add(Entry(x, f.lower.toFloat()))

                    if (i > 0) { // Nechceme přepsat label pro dnešek
                        val futCal = Calendar.getInstance().apply {
                            time = selectedDate
                            add(Calendar.DAY_OF_YEAR, i)
                        }
                        dayLabels[x] = displaySdf.format(futCal.time)
                    }
                }
            }

            // --- 4. DATASETS (Prémiový styling & Fix kornoutu) ---
            val lineData = LineData()

            if (upperEntries.isNotEmpty()) {
                // S - Horní stín (BC6C25)
                lineData.addDataSet(LineDataSet(upperEntries, "S").apply {
                    color = Color.TRANSPARENT; setDrawCircles(false); setDrawValues(false)
                    setDrawFilled(true); fillColor = Color.parseColor("#BC6C25"); fillAlpha = 45
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                })
                // C - Spodní maska (FEFAE0 - musí být 255 alpha)
                lineData.addDataSet(LineDataSet(lowerEntries, "C").apply {
                    color = Color.TRANSPARENT; setDrawCircles(false); setDrawValues(false)
                    setDrawFilled(true); fillColor = Color.parseColor("#FEFAE0"); fillAlpha = 255
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                })
            }

            // P - Predikce (DDA15E)
            lineData.addDataSet(LineDataSet(predMainEntries, "P").apply {
                color = Color.parseColor("#DDA15E"); lineWidth = 2.2f
                enableDashedLine(12f, 10f, 0f); setDrawCircles(false); setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            })

            // H - Historie (606C38)
            lineData.addDataSet(LineDataSet(historyEntries, "H").apply {
                color = Color.parseColor("#606C38"); lineWidth = 3.5f
                setCircleColor(Color.parseColor("#606C38"))
                circleRadius = 5f; setDrawCircleHole(true); circleHoleColor = Color.parseColor("#FEFAE0")
                setDrawValues(false); mode = LineDataSet.Mode.CUBIC_BEZIER
            })

            // --- 5. FINÁLNÍ NASTAVENÍ GRAFU ---
            historyChart.apply {
                data = lineData
                description.isEnabled = false; legend.isEnabled = false
                setExtraOffsets(10f, 10f, 10f, 15f)
                setTouchEnabled(true); setPinchZoom(false); setScaleEnabled(false)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    textColor = Color.parseColor("#80283618")
                    textSize = 10f
                    setDrawGridLines(false); setDrawAxisLine(false)
                    axisMinimum = 0f; axisMaximum = totalDays; labelCount = 7

                    valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                        override fun getFormattedValue(value: Float): String = dayLabels[value] ?: ""
                    }

                    removeAllLimitLines()
                    addLimitLine(LimitLine(rangeDays).apply {
                        lineColor = Color.parseColor("#40283618")
                        lineWidth = 1.5f; enableDashedLine(10f, 10f, 0f)
                    })
                }

                axisLeft.apply {
                    textColor = Color.parseColor("#80283618")
                    textSize = 10f; setDrawGridLines(true); setDrawAxisLine(false)
                    gridColor = Color.parseColor("#15283618")
                    xOffset = 12f

                    val allY = (historyEntries + upperEntries + lowerEntries).map { it.y }
                    if (allY.isNotEmpty()) {
                        axisMinimum = (allY.minOrNull() ?: 70f) - 1.5f
                        axisMaximum = (allY.maxOrNull() ?: 80f) + 1.5f
                    }
                }
                axisRight.isEnabled = false
                animateY(1000, Easing.EaseOutCubic)
                invalidate()
            }
        }
    }

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
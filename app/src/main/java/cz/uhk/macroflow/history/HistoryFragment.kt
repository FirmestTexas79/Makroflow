package cz.uhk.macroflow.history

import android.R.attr.height
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
import cz.uhk.macroflow.dashboard.EliteMetabolicEngine
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
            cell.setOnClickListener {
                selectedDateKey = dateKey
                renderCalendar()
                loadData(dateKey)
            }
        }
        return cell
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()


    private fun loadData(dateKey: String) {
        val parsed = dateKeySdf.parse(dateKey) ?: Date()
        val isToday = dateKey == dateKeySdf.format(Date())
        tvDate.text = if (isToday) "DNES" else dateKey

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())

            // 1. NAČTENÍ PROFILU (pro zjištění typu diety)
            val profile = withContext(Dispatchers.IO) {
                db.userProfileDao().getProfileSync()
            }
            val dietType = profile?.dietType ?: "Vyvážená"

            // 2. TEORETICKÝ ZÁKLAD (Cíle z kalkulačky)
            val targetData = MacroCalculator.calculateForDate(requireContext(), parsed)

            // 3. NAČTENÍ REÁLNÉ KONZUMACE
            val consumedList = withContext(Dispatchers.IO) {
                db.consumedSnackDao().getConsumedByDateSync(dateKey)
            }

            // Výpočet sumy snědených hodnot
            val eatenCal = consumedList.sumOf { it.calories.toDouble() }
            val eatenP = consumedList.sumOf { it.p.toDouble() }
            val eatenS = consumedList.sumOf { it.s.toDouble() }
            val eatenT = consumedList.sumOf { it.t.toDouble() }
            val eatenFiber = consumedList.sumOf { it.fiber.toDouble() }

            // 4. KROKY A DYNAMICKÝ VÝDEJ
            val stepsEntity = withContext(Dispatchers.IO) { db.stepsDao().getStepsForDateSync(dateKey) }
            val stepsCount = stepsEntity?.count ?: 0
            val burnedKcalFromSteps = MacroFlowEngine.calculateCaloriesFromSteps(stepsCount, targetData.weight)

            // Dynamické navýšení cílů podle kroků
            val extraCarbs = (burnedKcalFromSteps * 0.8) / 4.0
            val extraFat = (burnedKcalFromSteps * 0.2) / 9.0

            val finalTargetCal = targetData.calories + burnedKcalFromSteps
            val finalTargetCarbs = targetData.carbs + extraCarbs
            val finalTargetFat = targetData.fat + extraFat

            // 5. LOGIKA VLÁKNINY PODLE TYPU DIETY (shodná s dashboardem)
            val fiberMultiplier = when (dietType) {
                "Keto" -> 10.0
                "Low Carb" -> 12.0
                "Vegan" -> 18.0
                "High Protein" -> 15.0
                else -> 14.0 // Vyvážená
            }

            // Výpočet cílové vlákniny (násobitel na 1000kcal, s limity)
            val finalTargetFiber = ((finalTargetCal / 1000.0) * fiberMultiplier)
                .coerceAtLeast(targetData.weight * 0.4)
                .coerceAtLeast(25.0)

            // 6. UPDATE UI
            tvStepsCount.text = String.format("%, d", stepsCount).replace(',', ' ')
            tvStepsBurned.text = if (burnedKcalFromSteps > 0) "+${burnedKcalFromSteps.toInt()} kcal" else "+0 kcal"

            // Formát: "Snědeno / Cíl"
            tvKcal.text = "${eatenCal.toInt()} / ${finalTargetCal.toInt()} kcal"
            tvProtein.text = "${eatenP.toInt()} / ${targetData.protein.toInt()} g"
            tvCarbs.text = "${eatenS.toInt()} / ${finalTargetCarbs.toInt()} g"
            tvFat.text = "${eatenT.toInt()} / ${finalTargetFat.toInt()} g"

            // Vláknina s dynamickým cílem podle diety
            tvFiber.text = "${String.format("%.1f", eatenFiber)} / ${finalTargetFiber.toInt()} g"

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

            val isElite = profile?.isEliteMode ?: false
            val rawDiet = (profile?.dietType ?: "Vyvážená").lowercase().trim()
            val bodyFat = profile?.bodyFatPercentage ?: 22.0
            val height = profile?.height ?: 175.0
            val wrist = profile?.lastWristMeasurement ?: 16.0

            val rangeDays = 7f // Středový bod
            val totalDays = 14f

            // --- 1. ENGINE LOGIKA ---
            val trendSensitivity = if (isElite) 1.0f else 0.35f
            val (tef, stability) = when {
                rawDiet.contains("protein") -> 0.22 to 0.80f
                rawDiet.contains("keto")    -> 0.08 to 0.50f
                rawDiet.contains("low")     -> 0.16 to 0.60f
                rawDiet.contains("vegan")   -> 0.13 to 0.90f
                else                        -> 0.11 to 1.00f
            }
            val bfFactor = if (bodyFat < 12.0) 0.65 else 1.0 + ((bodyFat - 22.0) / 100.0)
            val sortedHistory = allHistory.sortedBy { it.date }
            val lastWeight = sortedHistory.lastOrNull()?.weight?.toFloat() ?: 75f

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

            // --- 3. PREDIKCE (Index 7 až 14) ---
            if (currentAnalytics != null) {
                val baseSlope = currentAnalytics.trendSlope.toFloat() * trendSensitivity
                val metabolicPower = (tef * bfFactor).toFloat()
                val slopeAdjustment = (metabolicPower - 0.12f) * (if (isElite) 0.5f else 0.15f)
                val targetSlope = (baseSlope - slopeAdjustment).coerceIn(-0.4f, 0.4f)

                val wristRatio = height / wrist
                val somatotypeFactor = when {
                    wristRatio > 10.4 -> 0.85f
                    wristRatio < 9.6 -> 1.30f
                    else -> 1.05f
                }
                val biologicSpreadMultiplier = stability * somatotypeFactor * (bodyFat / 22.0).coerceIn(0.7, 1.5).toFloat()

                // i=0 je DNES (index 7), i=7 je +7 dní (index 14)
                for (i in 0..7) {
                    val x = rangeDays + i
                    val damping = (1.0 - (i * 0.03)).coerceAtLeast(0.5)
                    val predWeight = lastWeight + (targetSlope * i * damping).toFloat()

                    val spreadBase = if (isElite) 0.12f else 0.28f
                    val spread = (spreadBase + (i * 0.07f)) * biologicSpreadMultiplier

                    predMainEntries.add(Entry(x, predWeight))
                    upperEntries.add(Entry(x, predWeight + spread))
                    lowerEntries.add(Entry(x, predWeight - spread))

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
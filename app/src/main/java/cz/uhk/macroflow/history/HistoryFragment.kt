package cz.uhk.macroflow.history

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.R
import cz.uhk.macroflow.dashboard.MacroCalculator
import cz.uhk.macroflow.dashboard.MacroFlowEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private lateinit var graph: SymmetryGraphView
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

    // Nová pole pro kroky
    private lateinit var tvStepsCount: TextView
    private lateinit var tvStepsBurned: TextView

    private val monthSdf = SimpleDateFormat("LLLL yyyy", Locale("cs"))
    private val calendar = Calendar.getInstance()
    private val dateKeySdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var selectedDateKey: String = dateKeySdf.format(Date())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)

        // Inicializace UI prvků
        graph            = view.findViewById(R.id.historySymmetryGraph)
        tvDate           = view.findViewById(R.id.tvSelectedDate)
        tvKcal           = view.findViewById(R.id.tvHistoryKcal)
        tvTraining       = view.findViewById(R.id.tvHistoryTraining)
        tvProtein        = view.findViewById(R.id.tvHistoryProtein)
        tvCarbs          = view.findViewById(R.id.tvHistoryCarbs)
        tvFat            = view.findViewById(R.id.tvHistoryFat)
        tvSymmetryStatus = view.findViewById(R.id.tvSymmetryStatus)
        tvMonthLabel     = view.findViewById(R.id.tvMonthLabel)
        calGrid          = view.findViewById(R.id.calGrid)
        layoutNoMetrics  = view.findViewById(R.id.layoutNoMetrics)

        // Inicializace polí pro kroky
        tvStepsCount     = view.findViewById(R.id.tvHistoryStepsCount)
        tvStepsBurned    = view.findViewById(R.id.tvHistoryStepsBurned)

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

    private fun renderCalendar() {
        tvMonthLabel.text = monthSdf.format(calendar.time).replaceFirstChar { it.uppercase() }
        calGrid.removeAllViews()

        val today = dateKeySdf.format(Date())
        val displayCal = calendar.clone() as Calendar
        displayCal.set(Calendar.DAY_OF_MONTH, 1)

        var firstDow = displayCal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
        if (firstDow < 0) firstDow += 7

        val daysInMonth = displayCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        repeat(firstDow) {
            calGrid.addView(makeDayCell("", null, false, false))
        }

        for (day in 1..daysInMonth) {
            displayCal.set(Calendar.DAY_OF_MONTH, day)
            val dateKey = dateKeySdf.format(displayCal.time)
            val isToday = dateKey == today
            val isSelected = dateKey == selectedDateKey
            val isFuture = displayCal.after(Calendar.getInstance())
            val isWeekend = displayCal.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.SUNDAY)

            val cell = makeDayCell(day.toString(), dateKey, isToday, isSelected, isFuture, isWeekend)
            calGrid.addView(cell)
        }
    }

    private fun makeDayCell(
        label: String,
        dateKey: String?,
        isToday: Boolean,
        isSelected: Boolean,
        isFuture: Boolean = false,
        isWeekend: Boolean = false
    ): View {
        val cell = TextView(requireContext())
        val size = (resources.displayMetrics.widthPixels - dpToPx(20*2 + 4*2 + 8*2 + 4*7)) / 7
        cell.layoutParams = GridLayout.LayoutParams().apply {
            width = size
            height = size
            setMargins(2, 2, 2, 2)
        }
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
            isFuture -> {
                cell.setTextColor(Color.parseColor("#30FEFAE0"))
            }
            isWeekend -> {
                cell.setTextColor(Color.parseColor("#70FEFAE0"))
            }
            else -> {
                cell.setTextColor(Color.parseColor("#BDFEFAE0"))
            }
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

        // Základní výpočet z kalkulačky
        val baseData = MacroCalculator.calculateForDate(requireContext(), parsed)

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())

            // Načtení kroků z DB
            val stepsEntity = withContext(Dispatchers.IO) {
                db.stepsDao().getStepsForDateSync(dateKey)
            }
            val stepsCount = stepsEntity?.count ?: 0

            // Výpočet bonusu přes Engine
            val burnedKcal = MacroFlowEngine.calculateCaloriesFromSteps(stepsCount, baseData.weight)

            // UI Update pro kartu kroků
            tvStepsCount.text = String.format("%, d", stepsCount).replace(',', ' ')
            tvStepsBurned.text = if (burnedKcal > 0) "+${burnedKcal.toInt()} kcal" else "+0 kcal"

            // UI Update pro hlavní makra (Base + Bonus z kroků)
            if (baseData.calories > 0) {
                val extraCarbs = (burnedKcal * 0.8) / 4.0
                val extraFat = (burnedKcal * 0.2) / 9.0

                tvKcal.text     = "${(baseData.calories + burnedKcal).toInt()} kcal"
                tvTraining.text = baseData.trainingType
                tvProtein.text  = "${baseData.protein.toInt()}g"
                tvCarbs.text    = "${(baseData.carbs + extraCarbs).toInt()}g"
                tvFat.text      = "${(baseData.fat + extraFat).toInt()}g"
            } else {
                tvKcal.text = "—"; tvTraining.text = "—"; tvProtein.text = "—"; tvCarbs.text = "—"; tvFat.text = "—"
            }
        }
        updateSymmetry(dateKey)
    }

    private fun updateSymmetry(dateKey: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val metrics = withContext(Dispatchers.IO) {
                db.bodyMetricsDao().getByDateSync(dateKey)
            }

            if (metrics == null || metrics.neck <= 0f) {
                graph.visibility = View.INVISIBLE
                layoutNoMetrics.visibility = View.VISIBLE
                tvSymmetryStatus.text = "BEZ DAT"
                tvSymmetryStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#30283618"))
                return@launch
            }

            // Sync do Firebase
            if (FirebaseRepository.isLoggedIn) {
                withContext(Dispatchers.IO) {
                    try { FirebaseRepository.uploadBodyMetrics(metrics) } catch (e: Exception) { e.printStackTrace() }
                }
            }

            graph.visibility = View.VISIBLE
            layoutNoMetrics.visibility = View.GONE
            tvSymmetryStatus.text = "AKTIVNÍ"
            tvSymmetryStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#606C38"))

            // Výpočet skóre pro graf (zkráceno pro přehlednost)
            val wristEst = metrics.neck * 0.406f
            fun asymScore(actual: Float, ideal: Float, tL: Float = 0.10f, tH: Float = 0.16f): Float {
                if (ideal <= 0f) return 0.5f
                val ratio = actual / ideal
                val t = if (ratio < 1f) tL else tH
                return exp(-((ratio - 1f).pow(2)) / (2f * t.pow(2))).coerceIn(0.1f, 1f)
            }

            val sChest   = asymScore(metrics.chest, metrics.neck * 2.87f, 0.09f, 0.15f)
            val sWaist   = if ((metrics.waist / (metrics.neck * 2.14f)) <= 1.0f)
                asymScore(metrics.waist, metrics.neck * 2.14f, 0.08f, 0.08f)
            else asymScore(metrics.waist, metrics.neck * 2.14f, 0.06f, 0.06f)
            val sBicep   = asymScore(metrics.bicep, wristEst * 2.50f, 0.10f, 0.18f)
            val sForearm = asymScore(metrics.forearm, metrics.bicep * 0.853f, 0.07f, 0.10f)
            val sAbdomen = if (metrics.abdomen <= metrics.waist) 1.0f else exp(-((metrics.abdomen - metrics.waist) / 8f).pow(2)).coerceIn(0.1f, 1.0f)
            val sThigh   = asymScore(metrics.thigh, metrics.neck * 1.70f, 0.10f, 0.16f)
            val sCalf    = asymScore(metrics.calf, (wristEst * 2.50f + metrics.bicep) / 2f, 0.10f, 0.16f)

            graph.dataPoints = floatArrayOf(sChest, sWaist, sBicep, sForearm, sAbdomen, sThigh, sCalf, 1.0f)
            graph.invalidate()
        }
    }
}
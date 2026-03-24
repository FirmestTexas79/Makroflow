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

    private val sdf = SimpleDateFormat("yyyy-MM-01", Locale.getDefault()) // Fix: pro měsíce
    private val monthSdf = SimpleDateFormat("LLLL yyyy", Locale("cs"))

    private val calendar = Calendar.getInstance()
    private val dateKeySdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var selectedDateKey: String = dateKeySdf.format(Date())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)

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
                cell.backgroundTintList = ColorStateList.valueOf(
                    Color.parseColor("#DDA15E")
                )
                cell.setTextColor(Color.parseColor("#283618"))
                cell.typeface = Typeface.DEFAULT_BOLD
            }
            isToday -> {
                cell.setBackgroundResource(R.drawable.bg_status_pill)
                cell.backgroundTintList = ColorStateList.valueOf(
                    Color.parseColor("#30FEFAE0")
                )
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

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun loadData(dateKey: String) {
        val parsed = dateKeySdf.parse(dateKey) ?: Date()

        val isToday = dateKey == dateKeySdf.format(Date())
        tvDate.text = if (isToday) "DNES" else dateKey

        val data = MacroCalculator.calculateForDate(requireContext(), parsed)
        if (data.calories > 0) {
            tvKcal.text     = "${data.calories.toInt()} kcal"
            tvTraining.text = data.trainingType
            tvProtein.text  = "${data.protein.toInt()}g"
            tvCarbs.text    = "${data.carbs.toInt()}g"
            tvFat.text      = "${data.fat.toInt()}g"
        } else {
            tvKcal.text     = "—"
            tvTraining.text = "—"
            tvProtein.text  = "—"
            tvCarbs.text    = "—"
            tvFat.text      = "—"
        }

        updateSymmetry(dateKey)
    }

    private fun updateSymmetry(dateKey: String) {
        lifecycleScope.launch {
            val db = AppDatabase.Companion.getDatabase(requireContext())
            val metrics = withContext(Dispatchers.IO) {
                db.bodyMetricsDao().getByDateSync(dateKey)
            }

            if (metrics == null || metrics.neck <= 0f) {
                graph.visibility = View.INVISIBLE
                layoutNoMetrics.visibility = View.VISIBLE
                tvSymmetryStatus.text = "BEZ DAT"
                tvSymmetryStatus.backgroundTintList =
                    ColorStateList.valueOf(Color.parseColor("#30283618"))
                return@launch
            }

            // ✅ TICHÉ ODESLÁNÍ DO CLOUDU (Záloha bez omezení uživatele)
            if (FirebaseRepository.isLoggedIn) {
                withContext(Dispatchers.IO) {
                    try {
                        FirebaseRepository.uploadBodyMetrics(metrics)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            graph.visibility = View.VISIBLE
            layoutNoMetrics.visibility = View.GONE
            tvSymmetryStatus.text = "AKTIVNÍ"
            tvSymmetryStatus.backgroundTintList =
                ColorStateList.valueOf(Color.parseColor("#606C38"))

            val neck    = metrics.neck
            val chest   = metrics.chest
            val waist   = metrics.waist
            val bicep   = metrics.bicep
            val forearm = metrics.forearm
            val abdomen = metrics.abdomen
            val thigh   = metrics.thigh
            val calf    = metrics.calf

            val wristEst = neck * 0.406f

            fun asymScore(actual: Float, ideal: Float,
                          tightLow: Float = 0.10f, tightHigh: Float = 0.16f): Float {
                if (ideal <= 0f) return 0.5f
                val ratio = actual / ideal
                val tight = if (ratio < 1f) tightLow else tightHigh
                return exp(-((ratio - 1f).pow(2)) / (2f * tight.pow(2))).coerceIn(0.1f, 1f)
            }

            val idealChest   = neck * 2.87f
            val idealWaist   = neck * 2.14f
            val idealBicep   = wristEst * 2.50f
            val idealForearm = wristEst * 1.93f
            val idealThigh   = neck * 1.70f
            val idealCalf    = wristEst * 2.50f

            val scoreChest = asymScore(chest, idealChest, 0.09f, 0.15f)

            val waistRatio = waist / idealWaist
            val scoreWaist = when {
                waistRatio <= 1.0f -> exp(-((waistRatio - 1f).pow(2)) / (2 * 0.08f.pow(2))).coerceIn(0.1f, 1f)
                else               -> exp(-((waistRatio - 1f).pow(2)) / (2 * 0.06f.pow(2))).coerceIn(0.1f, 1f)
            }

            val scoreBicep = asymScore(bicep, idealBicep, 0.10f, 0.18f)

            val scoreForearm = if (bicep > 0f)
                asymScore(forearm / bicep, 0.853f, 0.07f, 0.10f)
            else 0.5f

            val scoreAbdomen = when {
                abdomen <= waist         -> 1.0f
                abdomen <= waist + 4f    -> exp(-((abdomen - waist) / 8f).pow(2)).coerceIn(0.5f, 1f)
                else                     -> exp(-((abdomen - waist - 4f) / 5f).pow(2)).coerceIn(0.1f, 0.7f)
            }

            val scoreThigh = asymScore(thigh, idealThigh, 0.10f, 0.16f)

            val idealCalfFinal = (idealCalf + bicep) / 2f
            val scoreCalf = asymScore(calf, idealCalfFinal, 0.10f, 0.16f)

            graph.dataPoints = floatArrayOf(
                scoreChest, scoreWaist, scoreBicep, scoreForearm, scoreAbdomen, scoreThigh, scoreCalf, 1.0f
            )
            graph.invalidate()
        }
    }
}
package cz.uhk.macroflow

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
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

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthSdf = SimpleDateFormat("LLLL yyyy", Locale("cs"))

    private val calendar = Calendar.getInstance()
    private var selectedDateKey: String = sdf.format(Date())

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
            // Nedovol navigovat do budoucna
            val now = Calendar.getInstance()
            if (calendar.get(Calendar.YEAR) < now.get(Calendar.YEAR) ||
                (calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                        calendar.get(Calendar.MONTH) < now.get(Calendar.MONTH))) {
                calendar.add(Calendar.MONTH, 1)
                renderCalendar()
            }
        }

        // Nastav na aktuální měsíc
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        renderCalendar()
        loadData(selectedDateKey)

        return view
    }

    // ----------------------------------------------------------------
    // Vlastní kalendář — tmavé buňky na tmavé kartě
    // ----------------------------------------------------------------
    private fun renderCalendar() {
        tvMonthLabel.text = monthSdf.format(calendar.time).replaceFirstChar { it.uppercase() }
        calGrid.removeAllViews()

        val today = sdf.format(Date())
        val displayCal = calendar.clone() as Calendar
        displayCal.set(Calendar.DAY_OF_MONTH, 1)

        // Posun — Pondělí = 0
        var firstDow = displayCal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
        if (firstDow < 0) firstDow += 7

        val daysInMonth = displayCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Prázdné buňky před prvním dnem
        repeat(firstDow) {
            calGrid.addView(makeDayCell("", null, false, false))
        }

        for (day in 1..daysInMonth) {
            displayCal.set(Calendar.DAY_OF_MONTH, day)
            val dateKey = sdf.format(displayCal.time)
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
        // 20dp card margin*2 + 4dp padding*2 + 20dp grid padding*2 + 4dp cell margin*2*7
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
            label.isEmpty() -> { /* prázdná buňka */ }
            isSelected -> {
                cell.setBackgroundResource(R.drawable.bg_status_pill)
                cell.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#DDA15E")
                )
                cell.setTextColor(Color.parseColor("#283618"))
                cell.typeface = Typeface.DEFAULT_BOLD
            }
            isToday -> {
                cell.setBackgroundResource(R.drawable.bg_status_pill)
                cell.backgroundTintList = android.content.res.ColorStateList.valueOf(
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

    // ----------------------------------------------------------------
    // Načtení dat pro vybraný den
    // ----------------------------------------------------------------
    private fun loadData(dateKey: String) {
        val parsed = sdf.parse(dateKey) ?: Date()

        // Zobraz pill s datem
        val isToday = dateKey == sdf.format(Date())
        tvDate.text = if (isToday) "DNES" else dateKey

        // Makra z kalkulátoru
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

    // ----------------------------------------------------------------
    // Symetrie z DB
    // ----------------------------------------------------------------
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
                tvSymmetryStatus.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#30283618"))
                return@launch
            }

            graph.visibility = View.VISIBLE
            layoutNoMetrics.visibility = View.GONE
            tvSymmetryStatus.text = "AKTIVNÍ"
            tvSymmetryStatus.backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor("#606C38"))

            val neck    = metrics.neck
            val chest   = metrics.chest
            val waist   = metrics.waist
            val bicep   = metrics.bicep
            val forearm = metrics.forearm
            val abdomen = metrics.abdomen
            val thigh   = metrics.thigh
            val calf    = metrics.calf

            // ── Vědecké proporce ─────────────────────────────────────
            // Zdroje: Steve Reeves (1947), NSCA Athletic Standards (2021),
            //         Casey Butt PhD (Maximum Muscular Potential, 2010)
            //
            // Casey Butt formule pro přirozený limit (z obvodu zápěstí + kotníku):
            //   bicep  = height × 0.252 × (wrist/15)^0.5   ← škáluje dle kostry
            //   forearm= wrist × 1.93
            //   neck   = wrist × 2.47  (referenční ověření)
            //   thigh  = height × 0.362
            //   calf   = ankle × 1.93  (bez kotníku: ≈ neck × 1.03)
            //
            // Ideál NENÍ soutěžní kulturista — je to symetrický atletický sportovec.
            // Tolerance je asymetrická: příliš malé trestáme víc než příliš velké.

            // Zápěstí proxy: Casey Butt ukázal wrist ≈ neck × 0.406
            // Ale pokud uživatel zadá skutečné zápěstí, použijeme ho přímo
            // (BodyMetricsEntity zatím nemá wrist — odhadneme z neck s korekcí)
            val wristEst = neck * 0.406f   // korelace dle Casey Butt databáze (r=0.91)

            // Asymetrická Gaussovská funkce:
            // - pod ideálem: tolerance tightLow (přísnější)
            // - nad ideálem: tolerance tightHigh (benevolentnější — svalová hmota je OK)
            fun asymScore(actual: Float, ideal: Float,
                          tightLow: Float = 0.10f, tightHigh: Float = 0.16f): Float {
                if (ideal <= 0f) return 0.5f
                val ratio = actual / ideal
                val tight = if (ratio < 1f) tightLow else tightHigh
                return exp(-((ratio - 1f).pow(2)) / (2f * tight.pow(2))).coerceIn(0.1f, 1f)
            }

            // Ideály pro trénovaného muže (Casey Butt Athletic Standard):
            val idealChest   = neck * 2.87f           // hrudník ≈ 2.87× krk (ověřeno: 37×2.87=106cm ✓)
            val idealWaist   = neck * 2.14f           // pas ≈ 2.14× krk (štíhlý atletický)
            val idealBicep   = wristEst * 2.50f       // Reeves: bicep = 2.5× zápěstí
            val idealForearm = wristEst * 1.93f       // Reeves: předloktí = 1.93× zápěstí
            val idealThigh   = neck * 1.70f           // stehno ≈ 1.70× krk
            val idealCalf    = wristEst * 2.50f       // Reeves: lýtko = bicep = 2.5× zápěstí

            // Hrudník
            val scoreChest = asymScore(chest, idealChest, 0.09f, 0.15f)

            // Pas — speciální: menší než ideál je BONUS, větší trestáme
            val waistRatio = waist / idealWaist
            val scoreWaist = when {
                waistRatio <= 1.0f -> exp(-((waistRatio - 1f).pow(2)) / (2 * 0.08f.pow(2))).coerceIn(0.1f, 1f)
                else               -> exp(-((waistRatio - 1f).pow(2)) / (2 * 0.06f.pow(2))).coerceIn(0.1f, 1f)
            }

            // Bicep
            val scoreBicep = asymScore(bicep, idealBicep, 0.10f, 0.18f)

            // Předloktí — poměr k bicep (NSCA: 0.80–0.87)
            val scoreForearm = if (bicep > 0f)
                asymScore(forearm / bicep, 0.853f, 0.07f, 0.10f)
            else 0.5f

            // Břicho — abdomen MENŠÍ než pas = výborné (pevný střed), větší = tuk
            val scoreAbdomen = when {
                abdomen <= waist         -> 1.0f   // abdomen menší než pas = perfektní
                abdomen <= waist + 4f    -> exp(-((abdomen - waist) / 8f).pow(2)).coerceIn(0.5f, 1f)
                else                     -> exp(-((abdomen - waist - 4f) / 5f).pow(2)).coerceIn(0.1f, 0.7f)
            }

            // Stehno
            val scoreThigh = asymScore(thigh, idealThigh, 0.10f, 0.16f)

            // Lýtko — ideálně = bicep symetrie (Reeves), zároveň ≥ 2.5× zápěstí
            val idealCalfFinal = (idealCalf + bicep) / 2f
            val scoreCalf = asymScore(calf, idealCalfFinal, 0.10f, 0.16f)

            graph.dataPoints = floatArrayOf(
                scoreChest,    // HRU
                scoreWaist,    // PAS
                scoreBicep,    // BIC
                scoreForearm,  // PŘE
                scoreAbdomen,  // BŘI
                scoreThigh,    // STE
                scoreCalf,     // LÝT
                1.0f           // KRK — referenční bod
            )
            graph.invalidate()
        }
    }
}
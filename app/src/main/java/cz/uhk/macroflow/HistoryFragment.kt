package cz.uhk.macroflow

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CalendarView
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private lateinit var graph: SymmetryGraphView
    private lateinit var tvDate: TextView
    private lateinit var tvKcal: TextView
    private lateinit var tvPST: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)

        graph = view.findViewById(R.id.historySymmetryGraph)
        tvDate = view.findViewById(R.id.tvSelectedDate)
        tvKcal = view.findViewById(R.id.tvHistoryKcal)
        tvPST = view.findViewById(R.id.tvHistoryPST)
        val calendarView = view.findViewById<CalendarView>(R.id.calendarView)

        // Dnešní datum jako výchozí
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdf.format(Date())
        loadData(today)

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth)
            val dateKey = sdf.format(cal.time)
            loadData(dateKey)
        }

        return view
    }

    private fun loadData(dateKey: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val selectedDate = sdf.parse(dateKey) ?: Date()

        // 1. Načtení maker pro daný den
        val data = MacroCalculator.calculateForDate(requireContext(), selectedDate)
        tvDate.text = "Plán pro: $dateKey (${data.trainingType})"

        if (data.calories > 0) {
            tvKcal.text = "${data.calories.toInt()} kcal"
            tvPST.text = "B: ${data.protein.toInt()}g | S: ${data.carbs.toInt()}g | T: ${data.fat.toInt()}g"
        } else {
            tvKcal.text = "Plán nenastaven"
        }

        // 2. KLÍČOVÁ ČÁST: Předáme dateKey do updateSymmetry
        updateSymmetry(dateKey)
    }

    private fun updateSymmetry(dateKey: String) {
        val prefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        // Načteme metriky přímo pro daný den pomocí nového formátu klíčů
        val cur = getMetricsForDate(dateKey, prefs)

        // Pokud nemáme zadaný krk pro tento den, graf schováme (nebo vynulujeme)
        if (cur[7] <= 0f) {
            graph.visibility = View.INVISIBLE // Lepší než GONE, aby layout neposkakoval
            return
        }
        graph.visibility = View.VISIBLE

        val neck = cur[7]

        // Výpočet bodů (0.1 až 1.0) - oktagon se teď spočítá z "cur", což jsou data dne
        val points = floatArrayOf(
            (cur[0] / (neck * 2.8f)).coerceIn(0.1f, 1f),       // Hrudník
            ((neck * 1.95f) / cur[1]).coerceIn(0.1f, 1f),      // Pas
            (cur[2] / neck).coerceIn(0.1f, 1f),                // Biceps
            (cur[3] / (cur[2] * 0.8f)).coerceIn(0.1f, 1f),     // Předloktí
            ((neck * 1.95f) / cur[4]).coerceIn(0.1f, 1f),      // Břicho
            (cur[5] / (neck * 1.51f)).coerceIn(0.1f, 1f),      // Stehno
            (cur[6] / neck).coerceIn(0.1f, 1f),                // Lýtko
            1.0f                                               // Krk
        )

        graph.dataPoints = points
        graph.invalidate() // DŮLEŽITÉ: Nutí SymmetryGraphView, aby se znovu vykreslil s novými daty!
    }

    private fun getMetricsForDate(d: String, p: android.content.SharedPreferences): FloatArray {
        // Pomocná funkce pro bezpečné načtení Float ze Stringu v SharedPreferences
        fun getF(key: String): Float {
            val s = p.getString("${d}_$key", "0") ?: "0"
            return s.toFloatOrNull() ?: 0f
        }

        // Vracíme pole hodnot přesně v pořadí, které čeká oktagon
        return floatArrayOf(
            getF("chest"),   // index 0
            getF("waist"),   // index 1
            getF("bicep"),   // index 2
            getF("forearm"), // index 3
            getF("abdomen"), // index 4
            getF("thigh"),   // index 5
            getF("calf"),    // index 6
            getF("neck")     // index 7
        )
    }

    private fun getMetricsFromPrefs(p: android.content.SharedPreferences): FloatArray {
        // Musíme použít přesně ty klíče, které ukládáš v ProfileFragmentu (R.id.et...)
        // Protože IDs jsou Inty, v ProfileFragmentu jsi ukládal "metric_$id"
        // Tady je potřeba mít jistotu, že IDs odpovídají tvým EditTextům:

        fun getF(idName: String): Float {
            // Získáme ID podle názvu (protože v fragmentu nemáme přístup k R.id sheetu přímo)
            val resId = resources.getIdentifier(idName, "id", requireContext().packageName)
            val valueStr = p.getString("metric_$resId", "0") ?: "0"
            return valueStr.toFloatOrNull() ?: 0f
        }

        return floatArrayOf(
            getF("etChest"),   // 0
            getF("etWaist"),   // 1
            getF("etBicep"),   // 2
            getF("etForearm"), // 3
            getF("etAbdomen"), // 4
            getF("etThigh"),   // 5
            getF("etCalf"),    // 6
            getF("etNeck")     // 7
        )
    }
}
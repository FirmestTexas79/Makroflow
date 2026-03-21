package cz.uhk.macroflow

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CalendarView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private lateinit var graph: SymmetryGraphView
    private lateinit var tvDate: TextView
    private lateinit var tvKcal: TextView
    private lateinit var tvPST: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)

        graph = view.findViewById(R.id.historySymmetryGraph)
        tvDate = view.findViewById(R.id.tvSelectedDate)
        tvKcal = view.findViewById(R.id.tvHistoryKcal)
        tvPST = view.findViewById(R.id.tvHistoryPST)
        val calendarView = view.findViewById<CalendarView>(R.id.calendarView)

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdf.format(Date())
        loadData(today)

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth)
            loadData(sdf.format(cal.time))
        }

        return view
    }

    private fun loadData(dateKey: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val selectedDate = sdf.parse(dateKey) ?: Date()

        // Makra pro vybraný den
        val data = MacroCalculator.calculateForDate(requireContext(), selectedDate)
        tvDate.text = "Plán pro: $dateKey (${data.trainingType})"

        if (data.calories > 0) {
            tvKcal.text = "${data.calories.toInt()} kcal"
            tvPST.text = "B: ${data.protein.toInt()}g | S: ${data.carbs.toInt()}g | T: ${data.fat.toInt()}g"
        } else {
            tvKcal.text = "Plán nenastaven"
        }

        // Symetrie z DB
        updateSymmetry(dateKey)
    }

    private fun updateSymmetry(dateKey: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val metrics = withContext(Dispatchers.IO) {
                db.bodyMetricsDao().getByDateSync(dateKey)
            }

            // Pokud pro daný den neexistují míry (nebo chybí krk), graf skryjeme
            if (metrics == null || metrics.neck <= 0f) {
                graph.visibility = View.INVISIBLE
                return@launch
            }
            graph.visibility = View.VISIBLE

            val neck = metrics.neck

            val points = floatArrayOf(
                (metrics.chest / (neck * 2.8f)).coerceIn(0.1f, 1f),         // Hrudník
                ((neck * 1.95f) / metrics.waist).coerceIn(0.1f, 1f),        // Pas
                (metrics.bicep / neck).coerceIn(0.1f, 1f),                  // Biceps
                (metrics.forearm / (metrics.bicep * 0.8f)).coerceIn(0.1f, 1f), // Předloktí
                ((neck * 1.95f) / metrics.abdomen).coerceIn(0.1f, 1f),      // Břicho
                (metrics.thigh / (neck * 1.51f)).coerceIn(0.1f, 1f),        // Stehno
                (metrics.calf / neck).coerceIn(0.1f, 1f),                   // Lýtko
                1.0f                                                          // Krk (referenční)
            )

            graph.dataPoints = points
            graph.invalidate()
        }
    }
}
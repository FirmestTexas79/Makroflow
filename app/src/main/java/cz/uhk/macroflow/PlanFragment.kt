package cz.uhk.macroflow

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup

class PlanFragment : Fragment() {

    private val daysMap = listOf(
        "Monday" to R.string.day_monday,
        "Tuesday" to R.string.day_tuesday,
        "Wednesday" to R.string.day_wednesday,
        "Thursday" to R.string.day_thursday,
        "Friday" to R.string.day_friday,
        "Saturday" to R.string.day_saturday,
        "Sunday" to R.string.day_sunday
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_plan, container, false)
        view.findViewById<RecyclerView>(R.id.rvTrainingPlan)?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = TrainingPlanAdapter(daysMap)
            setPadding(0, 44, 0, 150) // Padding pro Pixel 10 Pro a spodní menu
            clipToPadding = false
        }
        return view
    }

    inner class TrainingPlanAdapter(private val days: List<Pair<String, Int>>) :
        RecyclerView.Adapter<TrainingPlanAdapter.PlanViewHolder>() {

        private val trainingPrefs by lazy {
            requireContext().getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)
        }

        inner class PlanViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvDay: TextView = view.findViewById(R.id.tvDayName)
            val toggleGroup: MaterialButtonToggleGroup = view.findViewById(R.id.toggleGroup)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_day, parent, false)
            return PlanViewHolder(view)
        }

        override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
            val (englishName, resId) = days[position]
            holder.tvDay.text = getString(resId).take(3).uppercase()
            holder.tvDay.setTextColor(android.graphics.Color.parseColor("#283618"))

            val savedType = trainingPrefs.getString("type_$englishName", "rest")

            // KLÍČOVÉ: Nejdřív smažeme listenery, pak nastavíme check, pak přidáme listenery
            holder.toggleGroup.clearOnButtonCheckedListeners()

            when (savedType) {
                "push" -> holder.toggleGroup.check(R.id.btnPush)
                "pull" -> holder.toggleGroup.check(R.id.btnPull)
                "legs" -> holder.toggleGroup.check(R.id.btnLegs)
                else -> holder.toggleGroup.check(R.id.btnRest)
            }

            holder.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val selectedType = when (checkedId) {
                        R.id.btnPush -> "push"
                        R.id.btnPull -> "pull"
                        R.id.btnLegs -> "legs"
                        else -> "rest"
                    }
                    trainingPrefs.edit().putString("type_$englishName", selectedType).apply()
                    holder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                }
            }
        }

        override fun getItemCount(): Int = days.size
    }
}
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
import com.google.android.material.card.MaterialCardView

class PlanFragment : Fragment() {

    private val daysMap = listOf(
        Triple("Monday",    R.string.day_monday,    "PO"),
        Triple("Tuesday",   R.string.day_tuesday,   "ÚT"),
        Triple("Wednesday", R.string.day_wednesday, "ST"),
        Triple("Thursday",  R.string.day_thursday,  "ČT"),
        Triple("Friday",    R.string.day_friday,    "PÁ"),
        Triple("Saturday",  R.string.day_saturday,  "SO"),
        Triple("Sunday",    R.string.day_sunday,    "NE")
    )

    private val trainingPrefs by lazy {
        requireContext().getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_plan, container, false)

        view.findViewById<RecyclerView>(R.id.rvTrainingPlan)?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = TrainingPlanAdapter()
            isNestedScrollingEnabled = false
            clipToPadding = false
        }

        updateStats(view)
        return view
    }

    // ----------------------------------------------------------------
    // Aktualizace statistických karet nahoře
    // ----------------------------------------------------------------
    private fun updateStats(view: View) {
        var push = 0; var pull = 0; var legs = 0; var rest = 0
        daysMap.forEach { (key, _, _) ->
            when (trainingPrefs.getString("type_$key", "rest")?.lowercase()) {
                "push" -> push++
                "pull" -> pull++
                "legs" -> legs++
                else   -> rest++
            }
        }
        view.findViewById<TextView>(R.id.tvStatPushCount)?.text = push.toString()
        view.findViewById<TextView>(R.id.tvStatPullCount)?.text = pull.toString()
        view.findViewById<TextView>(R.id.tvStatLegsCount)?.text = legs.toString()
        view.findViewById<TextView>(R.id.tvStatRestCount)?.text = rest.toString()
    }

    // ----------------------------------------------------------------
    // Adapter
    // ----------------------------------------------------------------
    inner class TrainingPlanAdapter : RecyclerView.Adapter<TrainingPlanAdapter.PlanViewHolder>() {

        inner class PlanViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView        = view.findViewById(R.id.cardDay)
            val tvDay: TextView               = view.findViewById(R.id.tvDayName)
            val tvDayFull: TextView           = view.findViewById(R.id.tvDayFull)
            val accent: View                  = view.findViewById(R.id.viewAccent)
            val toggleGroup: MaterialButtonToggleGroup = view.findViewById(R.id.toggleGroup)
        }

        override fun getItemCount() = daysMap.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_day, parent, false)
            return PlanViewHolder(view)
        }

        override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
            val (englishName, resId, shortCz) = daysMap[position]

            holder.tvDay.text     = shortCz
            holder.tvDayFull.text = getString(resId)

            val savedType = trainingPrefs.getString("type_$englishName", "rest") ?: "rest"

            // Nejdřív smaž listenery, pak nastav check — zabrání smyčce
            holder.toggleGroup.clearOnButtonCheckedListeners()
            when (savedType) {
                "push" -> holder.toggleGroup.check(R.id.btnPush)
                "pull" -> holder.toggleGroup.check(R.id.btnPull)
                "legs" -> holder.toggleGroup.check(R.id.btnLegs)
                else   -> holder.toggleGroup.check(R.id.btnRest)
            }

            updateCardVisual(holder, savedType)

            holder.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val type = when (checkedId) {
                    R.id.btnPush -> "push"
                    R.id.btnPull -> "pull"
                    R.id.btnLegs -> "legs"
                    else         -> "rest"
                }
                trainingPrefs.edit().putString("type_$englishName", type).apply()
                holder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                updateCardVisual(holder, type)
                // Aktualizuj statistiky — hledej root view fragmentu
                view?.let { updateStats(it) }
            }
        }

        private fun updateCardVisual(vh: PlanViewHolder, type: String) {
            val colorPush  = android.graphics.Color.parseColor("#606C38")
            val colorPull  = android.graphics.Color.parseColor("#283618")
            val colorLegs  = android.graphics.Color.parseColor("#BC6C25")
            val colorRest  = android.graphics.Color.parseColor("#20283618")
            val colorDark  = android.graphics.Color.parseColor("#283618")

            when (type) {
                "push" -> {
                    vh.card.strokeColor = colorPush
                    vh.card.strokeWidth = 4
                    vh.accent.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(colorPush)
                    vh.accent.visibility = View.VISIBLE
                    vh.tvDay.setTextColor(colorPush)
                }
                "pull" -> {
                    vh.card.strokeColor = colorPull
                    vh.card.strokeWidth = 4
                    vh.accent.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(colorPull)
                    vh.accent.visibility = View.VISIBLE
                    vh.tvDay.setTextColor(colorPull)
                }
                "legs" -> {
                    vh.card.strokeColor = colorLegs
                    vh.card.strokeWidth = 4
                    vh.accent.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(colorLegs)
                    vh.accent.visibility = View.VISIBLE
                    vh.tvDay.setTextColor(colorLegs)
                }
                else -> {
                    vh.card.strokeColor = colorRest
                    vh.card.strokeWidth = 2
                    vh.accent.visibility = View.GONE
                    vh.tvDay.setTextColor(colorDark)
                }
            }
        }
    }
}
package cz.uhk.macroflow.training

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import cz.uhk.macroflow.common.AppPreferences
import cz.uhk.macroflow.common.MakroflowNotifications
import cz.uhk.macroflow.common.MakroflowTimePicker
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.dashboard.MacroFlowEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

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

    override fun onResume() {
        super.onResume()
        view?.let { updateStats(it) }
    }

    private fun updateStats(view: View) {
        var push = 0; var pull = 0; var legs = 0; var rest = 0
        daysMap.forEach { (key, _, _) ->
            when (AppPreferences.getTrainingTypeSync(requireContext(), key).lowercase()) {
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTotalSteps = view.findViewById<TextView>(R.id.tvTotalStepsCount)
        val tvFatLabel = view.findViewById<TextView>(R.id.tvFatBurnedLabel)
        val tvEmoji = view.findViewById<TextView>(R.id.tvStepsEmoji)
        val llStepsCounter = view.findViewById<LinearLayout>(R.id.llStepsCounter)

        val db = AppDatabase.getDatabase(requireContext())
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        viewLifecycleOwner.lifecycleScope.launch {
            val profile = withContext(Dispatchers.IO) {
                db.userProfileDao().getProfileSync()
            }
            val stepGoal = profile?.stepGoal ?: 6000
            val userWeight = profile?.weight ?: 83.0

            db.stepsDao().getStepsForDateFlow(todayStr).collect { stepsEntity ->
                val stepsToday = stepsEntity?.count ?: 0
                val isGoalReached = stepsToday >= stepGoal

                tvTotalSteps?.text = "$stepsToday / $stepGoal"

                val burnedCalories = MacroFlowEngine.calculateCaloriesFromSteps(stepsToday, userWeight)
                val fatBurnedGrams = burnedCalories / 9.0

                if (!isGoalReached) {
                    tvFatLabel?.text = String.format(Locale.getDefault(), "🔥 %.1fg TUKU SPÁLENO", fatBurnedGrams)
                    tvEmoji?.text = "👟"
                    tvFatLabel?.setTextColor(Color.parseColor("#BC6C25"))
                } else {
                    tvFatLabel?.text = String.format(Locale.getDefault(), "🎉 %.1fg TUKU! CÍL SPLNĚN", fatBurnedGrams)
                    tvEmoji?.text = "🎉"
                    tvFatLabel?.setTextColor(Color.parseColor("#283618"))
                    llStepsCounter?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#20BC6C25"))
                }
            }
        }
    }

    // --- RECYCLERVIEW ADAPTER ---

    inner class TrainingPlanAdapter : RecyclerView.Adapter<TrainingPlanAdapter.PlanViewHolder>() {

        inner class PlanViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView                  = view.findViewById(R.id.cardDay)
            val tvDay: TextView                         = view.findViewById(R.id.tvDayName)
            val tvDayFull: TextView                     = view.findViewById(R.id.tvDayFull)
            val accent: View                            = view.findViewById(R.id.viewAccent)
            val toggleGroup: MaterialButtonToggleGroup  = view.findViewById(R.id.toggleGroup)
            val tvTimePill: TextView?                   = view.findViewById(R.id.tvTrainingTimePill)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_day, parent, false)
            return PlanViewHolder(view)
        }

        override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
            val (englishName, resId, shortCz) = daysMap[position]
            holder.tvDay.text     = shortCz
            holder.tvDayFull.text = getString(resId)

            val savedType = AppPreferences.getTrainingTypeSync(requireContext(), englishName)
            holder.toggleGroup.clearOnButtonCheckedListeners()
            when (savedType) {
                "push" -> holder.toggleGroup.check(R.id.btnPush)
                "pull" -> holder.toggleGroup.check(R.id.btnPull)
                "legs" -> holder.toggleGroup.check(R.id.btnLegs)
                else   -> holder.toggleGroup.check(R.id.btnRest)
            }
            updateCardVisual(holder, savedType)
            updateTimePill(holder, englishName, savedType)

            holder.tvTimePill?.setOnClickListener { showTimePicker(englishName, holder) }

            holder.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val type = when (checkedId) {
                    R.id.btnPush -> "push"
                    R.id.btnPull -> "pull"
                    R.id.btnLegs -> "legs"
                    else         -> "rest"
                }
                AppPreferences.setTrainingTypeSync(requireContext(), englishName, type)
                holder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                updateCardVisual(holder, type)
                updateTimePill(holder, englishName, type)
                view?.let { updateStats(it) }
            }
        }

        private fun updateTimePill(holder: PlanViewHolder, dayEnglish: String, type: String) {
            val pill = holder.tvTimePill ?: return
            if (type == "rest") { pill.visibility = View.GONE; return }
            pill.visibility = View.VISIBLE
            val savedTime = TrainingTimeManager.getTrainingTime(requireContext(), dayEnglish)
            if (savedTime != null) {
                pill.text = "🕐 $savedTime"
                pill.setTextColor(Color.parseColor("#606C38"))
                (pill.background as? GradientDrawable)?.setColor(Color.parseColor("#15606C38"))
            } else {
                pill.text = "🕐 Nastavit čas"
                pill.setTextColor(Color.parseColor("#80606C38"))
            }
        }

        private fun showTimePicker(dayEnglish: String, holder: PlanViewHolder) {
            val ctx = requireContext()
            val existing = TrainingTimeManager.getTrainingTime(ctx, dayEnglish)
            val initH = existing?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 7
            val initM = existing?.split(":")?.getOrNull(1)?.toIntOrNull() ?: 0

            MakroflowTimePicker.Companion.show(
                parentFragmentManager, initH, initM,
                "Čas tréninku — ${holder.tvDayFull.text}"
            ) { hour, minute ->
                val timeStr = String.format("%02d:%02d", hour, minute)
                TrainingTimeManager.setTrainingTime(ctx, dayEnglish, timeStr)
                updateTimePill(holder, dayEnglish, AppPreferences.getTrainingTypeSync(ctx, dayEnglish))
                holder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                MakroflowNotifications.rescheduleWorkout(ctx)
            }
        }

        private fun updateCardVisual(vh: PlanViewHolder, type: String) {
            val colorPush = Color.parseColor("#606C38"); val colorPull = Color.parseColor("#283618")
            val colorLegs = Color.parseColor("#BC6C25"); val colorRest = Color.parseColor("#20283618")
            when (type) {
                "push" -> {
                    vh.card.strokeColor = colorPush; vh.card.strokeWidth = 4
                    vh.accent.backgroundTintList = ColorStateList.valueOf(colorPush)
                    vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorPush)
                }
                "pull" -> {
                    vh.card.strokeColor = colorPull; vh.card.strokeWidth = 4
                    vh.accent.backgroundTintList = ColorStateList.valueOf(colorPull)
                    vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorPull)
                }
                "legs" -> {
                    vh.card.strokeColor = colorLegs; vh.card.strokeWidth = 4
                    vh.accent.backgroundTintList = ColorStateList.valueOf(colorLegs)
                    vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorLegs)
                }
                else -> {
                    vh.card.strokeColor = colorRest; vh.card.strokeWidth = 2
                    vh.accent.visibility = View.GONE; vh.tvDay.setTextColor(Color.parseColor("#283618"))
                }
            }
        }
        override fun getItemCount() = daysMap.size
    }
}

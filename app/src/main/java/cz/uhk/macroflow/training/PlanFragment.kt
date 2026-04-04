package cz.uhk.macroflow.training

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
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

    private val trainingPrefs by lazy {
        requireContext().getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)
    }

    private var isKardioMode = false

    private val colorPowerBg  = Color.parseColor("#DDE5B6")
    private val colorKardioBg = Color.parseColor("#1A2510")
    private val colorRun      = Color.parseColor("#2E86AB")
    private val colorRope     = Color.parseColor("#7B2D8B")
    private val colorBike     = Color.parseColor("#E76F51")

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isKardioMode = trainingPrefs.getBoolean("is_kardio_mode", false)

        val modeToggle = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleModeGroup)
        modeToggle?.check(if (isKardioMode) R.id.btnModeKardio else R.id.btnModePower)
        applyTheme(view, isKardioMode, animated = false)

        modeToggle?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val toKardio = checkedId == R.id.btnModeKardio
            if (toKardio != isKardioMode) {
                isKardioMode = toKardio
                trainingPrefs.edit().putBoolean("is_kardio_mode", toKardio).apply()
                applyTheme(view, toKardio, animated = true)
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

                val rv = view.findViewById<RecyclerView>(R.id.rvTrainingPlan)
                rv?.animate()?.alpha(0f)?.setDuration(150)?.withEndAction {
                    (rv.adapter as? TrainingPlanAdapter)?.notifyDataSetChanged()
                    updateStats(view)
                    rv.animate().alpha(1f).setDuration(200).start()
                }?.start()
            }
        }

        // Steps counter
        val tvTotalSteps  = view.findViewById<TextView>(R.id.tvTotalStepsCount)
        val tvFatLabel    = view.findViewById<TextView>(R.id.tvFatBurnedLabel)
        val tvEmoji       = view.findViewById<TextView>(R.id.tvStepsEmoji)
        val llStepsCounter = view.findViewById<LinearLayout>(R.id.llStepsCounter)
        val db            = AppDatabase.getDatabase(requireContext())
        val todayStr      = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        viewLifecycleOwner.lifecycleScope.launch {
            val profile    = withContext(Dispatchers.IO) { db.userProfileDao().getProfileSync() }
            val stepGoal   = profile?.stepGoal ?: 6000
            val userWeight = profile?.weight ?: 83.0
            db.stepsDao().getStepsForDateFlow(todayStr).collect { stepsEntity ->
                val stepsToday    = stepsEntity?.count ?: 0
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

    private fun applyTheme(view: View, toKardio: Boolean, animated: Boolean) {
        val targetBg = if (toKardio) colorKardioBg else colorPowerBg
        val root = view.findViewById<View>(R.id.coordinatorPlan) ?: view

        if (animated) {
            val fromBg = if (toKardio) colorPowerBg else colorKardioBg
            ValueAnimator.ofObject(ArgbEvaluator(), fromBg, targetBg).apply {
                duration = 400
                addUpdateListener { root.setBackgroundColor(it.animatedValue as Int) }
                start()
            }
        } else {
            root.setBackgroundColor(targetBg)
        }

        // Stat labels
        view.findViewById<TextView>(R.id.tvStatPushLabel)?.text = if (toKardio) "BĚH"  else "PUSH"
        view.findViewById<TextView>(R.id.tvStatPullLabel)?.text = if (toKardio) "ŠVIH" else "PULL"
        view.findViewById<TextView>(R.id.tvStatLegsLabel)?.text = if (toKardio) "KOLO" else "LEGS"
    }

    private fun updateStats(view: View) {
        var a = 0; var b = 0; var c = 0; var rest = 0
        val prefix = if (isKardioMode) "kardio_type_" else "type_"
        val typeA  = if (isKardioMode) "run"  else "push"
        val typeB  = if (isKardioMode) "rope" else "pull"
        val typeC  = if (isKardioMode) "bike" else "legs"
        daysMap.forEach { (key, _, _) ->
            when (trainingPrefs.getString("$prefix$key", "rest")?.lowercase()) {
                typeA -> a++
                typeB -> b++
                typeC -> c++
                else  -> rest++
            }
        }
        view.findViewById<TextView>(R.id.tvStatPushCount)?.text = a.toString()
        view.findViewById<TextView>(R.id.tvStatPullCount)?.text = b.toString()
        view.findViewById<TextView>(R.id.tvStatLegsCount)?.text = c.toString()
        view.findViewById<TextView>(R.id.tvStatRestCount)?.text = rest.toString()
    }

    // ── ADAPTER ─────────────────────────────────────────────────────────────

    inner class TrainingPlanAdapter : RecyclerView.Adapter<TrainingPlanAdapter.PlanViewHolder>() {

        inner class PlanViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView                 = view.findViewById(R.id.cardDay)
            val tvDay: TextView                        = view.findViewById(R.id.tvDayName)
            val tvDayFull: TextView                    = view.findViewById(R.id.tvDayFull)
            val accent: View                           = view.findViewById(R.id.viewAccent)
            val divider: View                          = view.findViewById(R.id.divider)
            val toggleGroup: MaterialButtonToggleGroup = view.findViewById(R.id.toggleGroup)
            val tvTimePill: TextView?                  = view.findViewById(R.id.tvTrainingTimePill)
            val llKardioExtras: LinearLayout?          = view.findViewById(R.id.llKardioExtras)
            val tvDurationPill: TextView?              = view.findViewById(R.id.tvKardioDurationPill)
            val tvSpeedPill: TextView?                 = view.findViewById(R.id.tvKardioSpeedPill)
            val btnPush: MaterialButton?               = view.findViewById(R.id.btnPush)
            val btnPull: MaterialButton?               = view.findViewById(R.id.btnPull)
            val btnLegs: MaterialButton?               = view.findViewById(R.id.btnLegs)
            val btnRest: MaterialButton?               = view.findViewById(R.id.btnRest)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_day, parent, false)
            return PlanViewHolder(view)
        }

        override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
            val (englishName, resId, shortCz) = daysMap[position]
            holder.tvDay.text     = shortCz
            holder.tvDayFull.text = getString(resId)
            if (isKardioMode) bindKardio(holder, englishName)
            else              bindPower(holder, englishName)
        }

        // ── POWER ────────────────────────────────────────────────────────────

        private fun bindPower(holder: PlanViewHolder, dayEnglish: String) {
            applyCardTheme(holder, isKardio = false)
            holder.btnPush?.text = "PUSH"
            holder.btnPull?.text = "PULL"
            holder.btnLegs?.text = "LEGS"
            holder.llKardioExtras?.visibility = View.GONE

            val savedType = trainingPrefs.getString("type_$dayEnglish", "rest") ?: "rest"
            holder.toggleGroup.clearOnButtonCheckedListeners()
            when (savedType) {
                "push" -> holder.toggleGroup.check(R.id.btnPush)
                "pull" -> holder.toggleGroup.check(R.id.btnPull)
                "legs" -> holder.toggleGroup.check(R.id.btnLegs)
                else   -> holder.toggleGroup.check(R.id.btnRest)
            }
            updatePowerCardVisual(holder, savedType)
            updateTimePill(holder, dayEnglish, savedType, isPower = true)
            holder.tvTimePill?.setOnClickListener { showTimePicker(dayEnglish, holder, isPower = true) }

            holder.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val type = when (checkedId) {
                    R.id.btnPush -> "push"
                    R.id.btnPull -> "pull"
                    R.id.btnLegs -> "legs"
                    else         -> "rest"
                }
                trainingPrefs.edit().putString("type_$dayEnglish", type).apply()
                holder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                updatePowerCardVisual(holder, type)
                updateTimePill(holder, dayEnglish, type, isPower = true)
                view?.let { updateStats(it) }
            }
        }

        // ── KARDIO ───────────────────────────────────────────────────────────

        private fun bindKardio(holder: PlanViewHolder, dayEnglish: String) {
            applyCardTheme(holder, isKardio = true)
            holder.btnPush?.text = "BĚH"
            holder.btnPull?.text = "ŠVIH"
            holder.btnLegs?.text = "KOLO"

            val savedType = trainingPrefs.getString("kardio_type_$dayEnglish", "rest") ?: "rest"
            holder.toggleGroup.clearOnButtonCheckedListeners()
            when (savedType) {
                "run"  -> holder.toggleGroup.check(R.id.btnPush)
                "rope" -> holder.toggleGroup.check(R.id.btnPull)
                "bike" -> holder.toggleGroup.check(R.id.btnLegs)
                else   -> holder.toggleGroup.check(R.id.btnRest)
            }
            updateKardioCardVisual(holder, savedType)
            updateTimePill(holder, dayEnglish, savedType, isPower = false)
            updateKardioPills(holder, dayEnglish, savedType)

            holder.tvTimePill?.setOnClickListener    { showTimePicker(dayEnglish, holder, isPower = false) }
            holder.tvDurationPill?.setOnClickListener { showDurationPicker(dayEnglish, holder) }
            holder.tvSpeedPill?.setOnClickListener    { showSpeedPicker(dayEnglish, holder) }

            holder.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val type = when (checkedId) {
                    R.id.btnPush -> "run"
                    R.id.btnPull -> "rope"
                    R.id.btnLegs -> "bike"
                    else         -> "rest"
                }
                trainingPrefs.edit().putString("kardio_type_$dayEnglish", type).apply()
                holder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                updateKardioCardVisual(holder, type)
                updateTimePill(holder, dayEnglish, type, isPower = false)
                updateKardioPills(holder, dayEnglish, type)
                view?.let { updateStats(it) }
            }
        }

        // ── VIZUÁL ───────────────────────────────────────────────────────────

        private fun applyCardTheme(holder: PlanViewHolder, isKardio: Boolean) {
            if (isKardio) {
                holder.card.setCardBackgroundColor(Color.parseColor("#1E2D14"))
                holder.divider.setBackgroundColor(Color.parseColor("#40DDE5B6"))
                holder.tvDayFull.setTextColor(Color.parseColor("#80DDE5B6"))
                listOf(holder.btnRest, holder.btnPush, holder.btnPull, holder.btnLegs).forEach {
                    it?.setTextColor(Color.parseColor("#DDE5B6"))
                }
            } else {
                holder.card.setCardBackgroundColor(Color.parseColor("#FAFAF5"))
                holder.card.strokeColor = Color.parseColor("#20283618")
                holder.card.strokeWidth = 2
                holder.divider.setBackgroundColor(Color.parseColor("#20283618"))
                holder.tvDayFull.setTextColor(Color.parseColor("#80283618"))
                listOf(holder.btnRest, holder.btnPush, holder.btnPull, holder.btnLegs).forEach {
                    it?.setTextColor(Color.parseColor("#283618"))
                }
            }
        }

        private fun updatePowerCardVisual(vh: PlanViewHolder, type: String) {
            val colorPush = Color.parseColor("#606C38")
            val colorPull = Color.parseColor("#283618")
            val colorLegs = Color.parseColor("#BC6C25")
            when (type) {
                "push" -> { vh.card.strokeColor = colorPush; vh.card.strokeWidth = 4; vh.accent.backgroundTintList = ColorStateList.valueOf(colorPush); vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorPush) }
                "pull" -> { vh.card.strokeColor = colorPull; vh.card.strokeWidth = 4; vh.accent.backgroundTintList = ColorStateList.valueOf(colorPull); vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorPull) }
                "legs" -> { vh.card.strokeColor = colorLegs; vh.card.strokeWidth = 4; vh.accent.backgroundTintList = ColorStateList.valueOf(colorLegs); vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorLegs) }
                else   -> { vh.card.strokeColor = Color.parseColor("#20283618"); vh.card.strokeWidth = 2; vh.accent.visibility = View.GONE; vh.tvDay.setTextColor(Color.parseColor("#283618")) }
            }
        }

        private fun updateKardioCardVisual(vh: PlanViewHolder, type: String) {
            when (type) {
                "run"  -> { vh.card.strokeColor = colorRun;  vh.card.strokeWidth = 4; vh.accent.backgroundTintList = ColorStateList.valueOf(colorRun);  vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorRun) }
                "rope" -> { vh.card.strokeColor = colorRope; vh.card.strokeWidth = 4; vh.accent.backgroundTintList = ColorStateList.valueOf(colorRope); vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorRope) }
                "bike" -> { vh.card.strokeColor = colorBike; vh.card.strokeWidth = 4; vh.accent.backgroundTintList = ColorStateList.valueOf(colorBike); vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorBike) }
                else   -> { vh.card.strokeColor = Color.parseColor("#40DDE5B6"); vh.card.strokeWidth = 2; vh.accent.visibility = View.GONE; vh.tvDay.setTextColor(Color.parseColor("#DDE5B6")) }
            }
        }

        // ── PILLS ────────────────────────────────────────────────────────────

        private fun updateTimePill(holder: PlanViewHolder, dayEnglish: String, type: String, isPower: Boolean) {
            val pill = holder.tvTimePill ?: return
            if (type == "rest") { pill.visibility = View.GONE; return }
            pill.visibility = View.VISIBLE
            val timeKey   = if (isPower) dayEnglish else "kardio_$dayEnglish"
            val savedTime = TrainingTimeManager.getTrainingTime(requireContext(), timeKey)
            val accent    = if (isPower) Color.parseColor("#606C38") else colorRun
            if (savedTime != null) {
                pill.text = "🕐 $savedTime"
                pill.setTextColor(accent)
                (pill.background as? GradientDrawable)?.setColor(Color.argb(30, Color.red(accent), Color.green(accent), Color.blue(accent)))
            } else {
                pill.text = "🕐 Nastavit čas"
                pill.setTextColor(Color.argb(128, Color.red(accent), Color.green(accent), Color.blue(accent)))
            }
        }

        private fun updateKardioPills(holder: PlanViewHolder, dayEnglish: String, type: String) {
            val ll = holder.llKardioExtras ?: return
            if (type == "rest") { ll.visibility = View.GONE; return }
            ll.visibility = View.VISIBLE
            val duration = trainingPrefs.getString("kardio_duration_$dayEnglish", null)?.toIntOrNull() ?: 0
            val speed    = trainingPrefs.getString("kardio_speed_$dayEnglish", null)?.toFloatOrNull() ?: 0f
            holder.tvDurationPill?.text = if (duration > 0) "⏱ ${duration} min" else "⏱ Délka"
            holder.tvSpeedPill?.text    = if (speed > 0f)   "🏃 ${String.format("%.1f", speed)} km/h" else "🏃 Tempo"
        }

        // ── PICKERS ──────────────────────────────────────────────────────────

        private fun showTimePicker(dayEnglish: String, holder: PlanViewHolder, isPower: Boolean) {
            val ctx     = requireContext()
            val timeKey = if (isPower) dayEnglish else "kardio_$dayEnglish"
            val existing = TrainingTimeManager.getTrainingTime(ctx, timeKey)
            val initH   = existing?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 7
            val initM   = existing?.split(":")?.getOrNull(1)?.toIntOrNull() ?: 0
            val label   = if (isPower) "Čas tréninku — ${holder.tvDayFull.text}"
                          else         "Čas kardia — ${holder.tvDayFull.text}"
            MakroflowTimePicker.Companion.show(parentFragmentManager, initH, initM, label) { hour, minute ->
                val timeStr = String.format("%02d:%02d", hour, minute)
                TrainingTimeManager.setTrainingTime(ctx, timeKey, timeStr)
                val type = if (isPower) trainingPrefs.getString("type_$dayEnglish", "rest") ?: "rest"
                           else         trainingPrefs.getString("kardio_type_$dayEnglish", "rest") ?: "rest"
                updateTimePill(holder, dayEnglish, type, isPower)
                holder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                MakroflowNotifications.rescheduleWorkout(ctx)
            }
        }

        private fun showDurationPicker(dayEnglish: String, holder: PlanViewHolder) {
            val ctx     = requireContext()
            val current = trainingPrefs.getString("kardio_duration_$dayEnglish", null)?.toIntOrNull() ?: 0
            val editText = EditText(ctx).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setText(if (current > 0) current.toString() else "")
                hint = "Počet minut"
                setPadding(48, 32, 48, 32)
            }
            AlertDialog.Builder(ctx)
                .setTitle("Délka kardia — ${holder.tvDayFull.text}")
                .setView(editText)
                .setPositiveButton("Uložit") { _, _ ->
                    val minutes = editText.text.toString().toIntOrNull() ?: 0
                    trainingPrefs.edit().putString("kardio_duration_$dayEnglish", minutes.toString()).apply()
                    val type = trainingPrefs.getString("kardio_type_$dayEnglish", "rest") ?: "rest"
                    updateKardioPills(holder, dayEnglish, type)
                    holder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                .setNegativeButton("Zrušit", null)
                .show()
        }

        private fun showSpeedPicker(dayEnglish: String, holder: PlanViewHolder) {
            val ctx     = requireContext()
            val current = trainingPrefs.getString("kardio_speed_$dayEnglish", null)?.toFloatOrNull() ?: 0f
            val editText = EditText(ctx).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(if (current > 0f) String.format("%.1f", current) else "")
                hint = "Průměrné tempo (km/h)"
                setPadding(48, 32, 48, 32)
            }
            AlertDialog.Builder(ctx)
                .setTitle("Tempo — ${holder.tvDayFull.text}")
                .setView(editText)
                .setPositiveButton("Uložit") { _, _ ->
                    val speed = editText.text.toString().replace(",", ".").toFloatOrNull() ?: 0f
                    trainingPrefs.edit().putString("kardio_speed_$dayEnglish", speed.toString()).apply()
                    val type = trainingPrefs.getString("kardio_type_$dayEnglish", "rest") ?: "rest"
                    updateKardioPills(holder, dayEnglish, type)
                    holder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                .setNegativeButton("Zrušit", null)
                .show()
        }

        override fun getItemCount() = daysMap.size
    }
}

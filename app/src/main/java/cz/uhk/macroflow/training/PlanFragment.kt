package cz.uhk.macroflow.training

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
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

    // Barvy textu
    private val colorTextLight = Color.parseColor("#DDE5B6")
    private val colorTextDark  = Color.parseColor("#283618")

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

        // Kroky
        val tvTotalSteps   = view.findViewById<TextView>(R.id.tvTotalStepsCount)
        val tvFatLabel     = view.findViewById<TextView>(R.id.tvFatBurnedLabel)
        val tvEmoji        = view.findViewById<TextView>(R.id.tvStepsEmoji)
        val llStepsCounter = view.findViewById<LinearLayout>(R.id.llStepsCounter)
        val db             = AppDatabase.getDatabase(requireContext())
        val todayStr       = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

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

                if (isKardioMode) {
                    // Tmavý mód — texty světlé
                    tvTotalSteps?.setTextColor(colorTextLight)
                    if (!isGoalReached) {
                        tvFatLabel?.text = String.format(Locale.getDefault(), "🔥 %.1fg TUKU SPÁLENO", fatBurnedGrams)
                        tvFatLabel?.setTextColor(Color.parseColor("#E76F51"))
                        tvEmoji?.text = "👟"
                    } else {
                        tvFatLabel?.text = String.format(Locale.getDefault(), "🎉 %.1fg TUKU! CÍL SPLNĚN", fatBurnedGrams)
                        tvFatLabel?.setTextColor(colorTextLight)
                        tvEmoji?.text = "🎉"
                        llStepsCounter?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#20E76F51"))
                    }
                } else {
                    // Světlý mód — originální barvy
                    tvTotalSteps?.setTextColor(colorTextDark)
                    if (!isGoalReached) {
                        tvFatLabel?.text = String.format(Locale.getDefault(), "🔥 %.1fg TUKU SPÁLENO", fatBurnedGrams)
                        tvFatLabel?.setTextColor(Color.parseColor("#BC6C25"))
                        tvEmoji?.text = "👟"
                    } else {
                        tvFatLabel?.text = String.format(Locale.getDefault(), "🎉 %.1fg TUKU! CÍL SPLNĚN", fatBurnedGrams)
                        tvFatLabel?.setTextColor(colorTextDark)
                        tvEmoji?.text = "🎉"
                        llStepsCounter?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#20BC6C25"))
                    }
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

        val textMain = if (toKardio) colorTextLight else colorTextDark
        val textSub  = if (toKardio) Color.parseColor("#90DDE5B6") else Color.parseColor("#90606C38")

        view.findViewById<TextView>(R.id.tvTitle)?.setTextColor(textMain)
        view.findViewById<TextView>(R.id.tvSubtitle)?.setTextColor(textSub)
        view.findViewById<TextView>(R.id.tvLabelSetupDays)?.setTextColor(textSub)
        view.findViewById<TextView>(R.id.tvTotalStepsCount)?.setTextColor(textMain)

        view.findViewById<LinearLayout>(R.id.llStepsCounter)
            ?.backgroundTintList = ColorStateList.valueOf(
                if (toKardio) Color.parseColor("#20DDE5B6") else Color.parseColor("#0D283618")
            )

        // Stat labely
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

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder =
            PlanViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_day, parent, false))

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
            holder.tvTimePill?.setOnClickListener { showPowerTimePicker(dayEnglish, holder) }

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

            holder.tvTimePill?.setOnClickListener     { showKardioTimePicker(dayEnglish, holder) }
            holder.tvDurationPill?.setOnClickListener { showDurationDialog(dayEnglish, holder) }
            holder.tvSpeedPill?.setOnClickListener    { showSpeedDialog(dayEnglish, holder) }

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
                holder.card.strokeColor = Color.parseColor("#40606C38")
                holder.divider.setBackgroundColor(Color.parseColor("#40DDE5B6"))
                holder.tvDayFull.setTextColor(Color.parseColor("#60DDE5B6"))
                listOf(holder.btnRest, holder.btnPush, holder.btnPull, holder.btnLegs).forEach {
                    it?.setTextColor(colorTextLight)
                }
            } else {
                holder.card.setCardBackgroundColor(Color.parseColor("#FAFAF5"))
                holder.card.strokeColor = Color.parseColor("#20283618")
                holder.card.strokeWidth = 2
                holder.divider.setBackgroundColor(Color.parseColor("#20283618"))
                holder.tvDayFull.setTextColor(Color.parseColor("#80283618"))
                listOf(holder.btnRest, holder.btnPush, holder.btnPull, holder.btnLegs).forEach {
                    it?.setTextColor(colorTextDark)
                }
            }
        }

        private fun updatePowerCardVisual(vh: PlanViewHolder, type: String) {
            val cPush = Color.parseColor("#606C38")
            val cPull = Color.parseColor("#283618")
            val cLegs = Color.parseColor("#BC6C25")
            when (type) {
                "push" -> { vh.card.strokeColor = cPush; vh.card.strokeWidth = 4; vh.accent.backgroundTintList = ColorStateList.valueOf(cPush); vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(cPush) }
                "pull" -> { vh.card.strokeColor = cPull; vh.card.strokeWidth = 4; vh.accent.backgroundTintList = ColorStateList.valueOf(cPull); vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(cPull) }
                "legs" -> { vh.card.strokeColor = cLegs; vh.card.strokeWidth = 4; vh.accent.backgroundTintList = ColorStateList.valueOf(cLegs); vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(cLegs) }
                else   -> { vh.card.strokeColor = Color.parseColor("#20283618"); vh.card.strokeWidth = 2; vh.accent.visibility = View.GONE; vh.tvDay.setTextColor(colorTextDark) }
            }
        }

        private fun updateKardioCardVisual(vh: PlanViewHolder, type: String) {
            when (type) {
                "run"  -> { vh.card.strokeColor = colorRun;  vh.card.strokeWidth = 4; vh.accent.backgroundTintList = ColorStateList.valueOf(colorRun);  vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorRun) }
                "rope" -> { vh.card.strokeColor = colorRope; vh.card.strokeWidth = 4; vh.accent.backgroundTintList = ColorStateList.valueOf(colorRope); vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorRope) }
                "bike" -> { vh.card.strokeColor = colorBike; vh.card.strokeWidth = 4; vh.accent.backgroundTintList = ColorStateList.valueOf(colorBike); vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorBike) }
                else   -> { vh.card.strokeColor = Color.parseColor("#40DDE5B6"); vh.card.strokeWidth = 2; vh.accent.visibility = View.GONE; vh.tvDay.setTextColor(colorTextLight) }
            }
        }

        // ── PILLS ────────────────────────────────────────────────────────────

        private fun updateTimePill(holder: PlanViewHolder, dayEnglish: String, type: String, isPower: Boolean) {
            val pill = holder.tvTimePill ?: return
            if (type == "rest") { pill.visibility = View.GONE; return }
            pill.visibility = View.VISIBLE
            val savedTime = if (isPower) TrainingTimeManager.getTrainingTime(requireContext(), dayEnglish)
                            else         TrainingTimeManager.getKardioTime(requireContext(), dayEnglish)
            val accent = if (isPower) Color.parseColor("#606C38") else colorRun
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

        // ── TIME PICKERS ─────────────────────────────────────────────────────

        private fun showPowerTimePicker(dayEnglish: String, holder: PlanViewHolder) {
            val ctx      = requireContext()
            val existing = TrainingTimeManager.getTrainingTime(ctx, dayEnglish)
            val initH    = existing?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 7
            val initM    = existing?.split(":")?.getOrNull(1)?.toIntOrNull() ?: 0
            MakroflowTimePicker.Companion.show(
                parentFragmentManager, initH, initM,
                "Čas tréninku — ${holder.tvDayFull.text}"
            ) { hour, minute ->
                val timeStr = String.format("%02d:%02d", hour, minute)
                TrainingTimeManager.setTrainingTime(ctx, dayEnglish, timeStr)
                val type = trainingPrefs.getString("type_$dayEnglish", "rest") ?: "rest"
                updateTimePill(holder, dayEnglish, type, isPower = true)
                holder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                MakroflowNotifications.rescheduleWorkout(ctx)
            }
        }

        private fun showKardioTimePicker(dayEnglish: String, holder: PlanViewHolder) {
            val ctx      = requireContext()
            val existing = TrainingTimeManager.getKardioTime(ctx, dayEnglish)
            val initH    = existing?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 7
            val initM    = existing?.split(":")?.getOrNull(1)?.toIntOrNull() ?: 0
            MakroflowTimePicker.Companion.show(
                parentFragmentManager, initH, initM,
                "Čas kardia — ${holder.tvDayFull.text}"
            ) { hour, minute ->
                val timeStr = String.format("%02d:%02d", hour, minute)
                TrainingTimeManager.setKardioTime(ctx, dayEnglish, timeStr)
                val type = trainingPrefs.getString("kardio_type_$dayEnglish", "rest") ?: "rest"
                updateTimePill(holder, dayEnglish, type, isPower = false)
                holder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }

        // ── KARDIO INPUT DIALOGY ─────────────────────────────────────────────

        private fun showDurationDialog(dayEnglish: String, holder: PlanViewHolder) {
            val current = trainingPrefs.getString("kardio_duration_$dayEnglish", null)?.toIntOrNull() ?: 0
            KardioInputDialog.showDuration(parentFragmentManager, holder.tvDayFull.text.toString(), current) { minutes ->
                trainingPrefs.edit().putString("kardio_duration_$dayEnglish", minutes.toString()).apply()
                val type = trainingPrefs.getString("kardio_type_$dayEnglish", "rest") ?: "rest"
                updateKardioPills(holder, dayEnglish, type)
                holder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }

        private fun showSpeedDialog(dayEnglish: String, holder: PlanViewHolder) {
            val current = trainingPrefs.getString("kardio_speed_$dayEnglish", null)?.toFloatOrNull() ?: 0f
            KardioInputDialog.showSpeed(parentFragmentManager, holder.tvDayFull.text.toString(), current) { speed ->
                trainingPrefs.edit().putString("kardio_speed_$dayEnglish", speed.toString()).apply()
                val type = trainingPrefs.getString("kardio_type_$dayEnglish", "rest") ?: "rest"
                updateKardioPills(holder, dayEnglish, type)
                holder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }

        override fun getItemCount() = daysMap.size
    }
}

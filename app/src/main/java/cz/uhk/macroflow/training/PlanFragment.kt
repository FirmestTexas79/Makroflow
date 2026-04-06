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
import cz.uhk.macroflow.common.MainActivity
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

    private val colorPowerBg   = Color.parseColor("#FEFAE0")
    private val colorKardioBg  = Color.parseColor("#1A2510")
    private val colorRun       = Color.parseColor("#2E86AB")
    private val colorRope      = Color.parseColor("#7B2D8B")
    private val colorBike      = Color.parseColor("#E76F51")
    private val colorCream     = Color.parseColor("#FEFAE0")
    private val colorDarkGreen = Color.parseColor("#283618")

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
                (requireActivity() as? MainActivity)?.refreshStickyNotification()
            }
        }

        // Krokoměr a kalorie
        val tvTotalSteps  = view.findViewById<TextView>(R.id.tvTotalStepsCount)
        val tvFatLabel    = view.findViewById<TextView>(R.id.tvFatBurnedLabel)
        val tvEmoji       = view.findViewById<TextView>(R.id.tvStepsEmoji)
        val llStepsCounter = view.findViewById<LinearLayout>(R.id.llStepsCounter)

        val db = AppDatabase.getDatabase(requireContext())
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        viewLifecycleOwner.lifecycleScope.launch {
            val profile    = withContext(Dispatchers.IO) { db.userProfileDao().getProfileSync() }
            val stepGoal   = profile?.stepGoal ?: 6000
            val userWeight = profile?.weight ?: 75.0
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
                    tvFatLabel?.setTextColor(colorDarkGreen)
                    llStepsCounter?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#20BC6C25"))
                }
            }
        }
    }

    private fun applyTheme(view: View, toKardio: Boolean, animated: Boolean) {
        val targetBg = if (toKardio) colorKardioBg else colorPowerBg
        val root = view.findViewById<View>(R.id.coordinatorPlan) ?: view

        // Základní barvy textu podle módu (na pozadí fragmentu)
        val titleTextColor = if (toKardio) colorCream else colorDarkGreen
        val subTitleTextColor = if (toKardio) Color.parseColor("#B0DDE5B6") else Color.parseColor("#80283618")

        // Pomocné barvy pro vnitřky boxů
        val colorTextOnDark = Color.WHITE
        val colorTextOnLight = colorDarkGreen

        // 1. Animace pozadí fragmentu
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

        // 2. Hlavní texty a step counter
        view.findViewById<TextView>(R.id.tvTitle)?.setTextColor(titleTextColor)
        view.findViewById<TextView>(R.id.tvSubtitle)?.setTextColor(subTitleTextColor)
        view.findViewById<TextView>(R.id.tvTotalStepsCount)?.setTextColor(titleTextColor)

        // 3. Logika pro statistiky (barevné čtverce)
        if (toKardio) {
            // --- KARDIO MODE ---
            // Všechny boxy v Kardio mají barevné nebo tmavé pozadí -> bílý text uvnitř

            // PUSH (BĚH) - Modrá
            view.findViewById<MaterialCardView>(R.id.cardStatPush)?.setCardBackgroundColor(colorRun)
            view.findViewById<TextView>(R.id.tvStatPushCount)?.setTextColor(colorTextOnDark)
            view.findViewById<TextView>(R.id.tvStatPushLabel)?.apply {
                text = "BĚH"
                setTextColor(colorTextOnDark)
            }

            // PULL (ŠVIH) - Fialová
            view.findViewById<MaterialCardView>(R.id.cardStatPull)?.setCardBackgroundColor(colorRope)
            view.findViewById<TextView>(R.id.tvStatPullCount)?.setTextColor(colorTextOnDark)
            view.findViewById<TextView>(R.id.tvStatPullLabel)?.apply {
                text = "ŠVIH"
                setTextColor(colorTextOnDark)
            }

            // LEGS (KOLO) - Oranžová
            view.findViewById<MaterialCardView>(R.id.cardStatLegs)?.setCardBackgroundColor(colorBike)
            view.findViewById<TextView>(R.id.tvStatLegsCount)?.setTextColor(colorTextOnDark)
            view.findViewById<TextView>(R.id.tvStatLegsLabel)?.apply {
                text = "KOLO"
                setTextColor(colorTextOnDark)
            }

            // REST - Tmavě zelená (v kardio módu chceme, aby REST box ladil s tmavým tématem)
            view.findViewById<MaterialCardView>(R.id.cardStatRest)?.setCardBackgroundColor(colorDarkGreen)
            view.findViewById<TextView>(R.id.tvStatRestCount)?.setTextColor(colorTextOnDark)
            // V XML u restu label nemá ID a je to přímý potomek LinearLayoutu,
            // tak najdeme druhý TextView v tom layoutu:
            val restLayout = view.findViewById<MaterialCardView>(R.id.cardStatRest)?.getChildAt(0) as? LinearLayout
            (restLayout?.getChildAt(1) as? TextView)?.setTextColor(colorTextOnDark)

        } else {
            // --- POWER MODE ---
            // Obnova původních barev pro Power mód

            // PUSH - Tmavě zelený box
            view.findViewById<MaterialCardView>(R.id.cardStatPush)?.setCardBackgroundColor(colorDarkGreen)
            view.findViewById<TextView>(R.id.tvStatPushCount)?.setTextColor(colorTextOnDark)
            view.findViewById<TextView>(R.id.tvStatPushLabel)?.apply {
                text = "PUSH"
                setTextColor(colorTextOnDark)
            }

            // PULL - Světle hnědý/oranžový box (brand_accent_warm)
            view.findViewById<MaterialCardView>(R.id.cardStatPull)?.setCardBackgroundColor(Color.parseColor("#DDA15E"))
            view.findViewById<TextView>(R.id.tvStatPullCount)?.setTextColor(colorTextOnDark) // na této oranžové je bílá ok
            view.findViewById<TextView>(R.id.tvStatPullLabel)?.apply {
                text = "PULL"
                setTextColor(colorTextOnDark)
            }

            // LEGS - Tmavší hnědá (brand_accent_deep)
            view.findViewById<MaterialCardView>(R.id.cardStatLegs)?.setCardBackgroundColor(Color.parseColor("#BC6C25"))
            view.findViewById<TextView>(R.id.tvStatLegsCount)?.setTextColor(colorTextOnDark)
            view.findViewById<TextView>(R.id.tvStatLegsLabel)?.apply {
                text = "LEGS"
                setTextColor(colorTextOnDark)
            }

            // REST - Krémový box s borderem
            view.findViewById<MaterialCardView>(R.id.cardStatRest)?.setCardBackgroundColor(colorCream)
            view.findViewById<TextView>(R.id.tvStatRestCount)?.setTextColor(colorTextOnLight)
            val restLayout = view.findViewById<MaterialCardView>(R.id.cardStatRest)?.getChildAt(0) as? LinearLayout
            (restLayout?.getChildAt(1) as? TextView)?.setTextColor(colorDarkGreen)
        }

        // 4. Přepínač módů (Toggle Group)
        val btnPower = view.findViewById<MaterialButton>(R.id.btnModePower)
        val btnKardio = view.findViewById<MaterialButton>(R.id.btnModeKardio)

        if (toKardio) {
            btnPower?.setTextColor(Color.parseColor("#80DDE5B6"))
            btnKardio?.setTextColor(colorCream)
        } else {
            btnPower?.setTextColor(colorDarkGreen)
            btnKardio?.setTextColor(Color.parseColor("#80283618"))
        }
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

            val btnDelete: View                        = view.findViewById(R.id.btnDeleteDayData)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_day, parent, false)
            return PlanViewHolder(view)
        }

        override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
            val (englishName, resId, shortCz) = daysMap[position]
            holder.tvDay.text     = shortCz
            holder.tvDayFull.text = getString(resId)

            applyCardTheme(holder, isKardioMode)

            if (isKardioMode) bindKardio(holder, englishName)
            else              bindPower(holder, englishName)
        }

        private fun applyCardTheme(holder: PlanViewHolder, isKardio: Boolean) {
            val bgColor = if (isKardio) "#F7F9F2" else "#FAFAF5"
            val strokeColor = if (isKardio) "#40DDE5B6" else "#20283618"

            holder.card.setCardBackgroundColor(Color.parseColor(bgColor))
            holder.card.strokeColor = Color.parseColor(strokeColor)
            holder.card.strokeWidth = if (isKardio) 3 else 2
            holder.divider.setBackgroundColor(Color.parseColor(if (isKardio) "#15283618" else "#20283618"))
            holder.tvDayFull.setTextColor(Color.parseColor(if (isKardio) "#90283618" else "#80283618"))
        }

        private fun updateToggleGroupColors(vh: PlanViewHolder, selectedType: String, isKardio: Boolean) {
            val buttons = listOf(
                vh.btnPush to if(isKardio) "run" else "push",
                vh.btnPull to if(isKardio) "rope" else "pull",
                vh.btnLegs to if(isKardio) "bike" else "legs",
                vh.btnRest to "rest"
            )

            buttons.forEach { (btn, type) ->
                if (btn != null) {
                    if (selectedType == type) {
                        // Tlačítko JE vybrané

                        // SPECIÁLNÍ LOGIKA PRO "REST" (OFF) V POWER MÓDU
                        if (!isKardio && type == "rest") {
                            btn.setTextColor(colorDarkGreen) // Tmavě zelený text
                            btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT) // Průhledné pozadí
                            btn.strokeWidth = 3 // Výraznější rámeček
                            btn.strokeColor = ColorStateList.valueOf(colorDarkGreen)
                        } else {
                            // Standardní chování pro ostatní vybraná tlačítka (vyplněná barvou)
                            btn.setTextColor(Color.WHITE)
                            val activeColor = when(type) {
                                "run"  -> colorRun
                                "rope" -> colorRope
                                "bike" -> colorBike
                                "push" -> colorDarkGreen
                                "pull" -> Color.parseColor("#DDA15E")
                                "legs" -> Color.parseColor("#BC6C25")
                                else   -> colorDarkGreen // Tohle platí pro vybraný REST v Kardio módu
                            }
                            btn.backgroundTintList = ColorStateList.valueOf(activeColor)
                            btn.strokeWidth = 0
                        }
                    } else {
                        // Tlačítko NENÍ vybrané
                        btn.setTextColor(colorDarkGreen)
                        btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                        btn.strokeWidth = 2
                        btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#20283618"))
                    }
                }
            }
        }

        private fun bindPower(holder: PlanViewHolder, dayEnglish: String) {
            holder.btnPush?.text = "PUSH"
            holder.btnPull?.text = "PULL"
            holder.btnLegs?.text = "LEGS"

            // V Power módu vypneme doplňky - zbude jen tvTrainingTimePill
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
            updateToggleGroupColors(holder, savedType, false)
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

                (requireActivity() as? MainActivity)?.refreshStickyNotification()

                holder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                updatePowerCardVisual(holder, type)
                updateToggleGroupColors(holder, type, false)
                updateTimePill(holder, dayEnglish, type, isPower = true)
                view?.let { updateStats(it) }
            }
        }

        private fun updateDeleteButtonVisibility(holder: PlanViewHolder, dayEnglish: String) {
            val timeKey = "kardio_$dayEnglish"
            val hasTime = TrainingTimeManager.getTrainingTime(requireContext(), timeKey) != null
            val hasDuration = trainingPrefs.getString("kardio_duration_$dayEnglish", null) != null

            // Pokud jsou tam jakákoliv data, ukážeme křížek
            holder.btnDelete.visibility = if (hasTime || hasDuration) View.VISIBLE else View.INVISIBLE
        }

        private fun bindKardio(holder: PlanViewHolder, dayEnglish: String) {
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
            updateToggleGroupColors(holder, savedType, true)
            updateTimePill(holder, dayEnglish, savedType, isPower = false)
            updateKardioPills(holder, dayEnglish, savedType)
            updateDeleteButtonVisibility(holder, dayEnglish)

            holder.btnDelete.setOnClickListener {
                trainingPrefs.edit().apply {
                    remove("kardio_$dayEnglish")
                    remove("kardio_duration_$dayEnglish")
                    remove("kardio_speed_$dayEnglish")
                    remove("kardio_jumps_$dayEnglish")
                }.apply()
                holder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                updateTimePill(holder, dayEnglish, savedType, isPower = false)
                updateKardioPills(holder, dayEnglish, savedType)
                updateDeleteButtonVisibility(holder, dayEnglish)
                view?.let { updateStats(it) }
            }

            holder.tvTimePill?.setOnClickListener { showTimePicker(dayEnglish, holder, isPower = false) }
            holder.tvDurationPill?.setOnClickListener { showKardioPicker(dayEnglish, holder) }
            holder.tvSpeedPill?.setOnClickListener { showKardioPicker(dayEnglish, holder) }

            holder.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val type = when (checkedId) {
                    R.id.btnPush -> "run"
                    R.id.btnPull -> "rope"
                    R.id.btnLegs -> "bike"
                    else         -> "rest"
                }
                trainingPrefs.edit().putString("kardio_type_$dayEnglish", type).apply()

                (requireActivity() as? MainActivity)?.refreshStickyNotification()

                holder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                updateKardioCardVisual(holder, type)
                updateToggleGroupColors(holder, type, true)
                updateTimePill(holder, dayEnglish, type, isPower = false)
                updateKardioPills(holder, dayEnglish, type)
                updateDeleteButtonVisibility(holder, dayEnglish)
                view?.let { updateStats(it) }
            }
        }

        private fun showKardioPicker(dayEnglish: String, holder: PlanViewHolder) {
            val position = holder.adapterPosition
            if (position == RecyclerView.NO_POSITION) return
            val dayCz = getString(daysMap[position].second)

            MakroflowKardioPicker.show(parentFragmentManager, dayEnglish, dayCz) {
                val type = trainingPrefs.getString("kardio_type_$dayEnglish", "rest") ?: "rest"
                updateKardioPills(holder, dayEnglish, type)
                updateDeleteButtonVisibility(holder, dayEnglish) // <--- Důležité
                view?.let { updateStats(it) }
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
                else   -> { vh.card.strokeColor = Color.parseColor("#20283618"); vh.card.strokeWidth = 2; vh.accent.visibility = View.GONE; vh.tvDay.setTextColor(colorDarkGreen) }
            }
        }

        private fun updateKardioCardVisual(vh: PlanViewHolder, type: String) {
            when (type) {
                "run"  -> { vh.card.strokeColor = colorRun;  vh.card.strokeWidth = 5; vh.accent.backgroundTintList = ColorStateList.valueOf(colorRun);  vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorRun) }
                "rope" -> { vh.card.strokeColor = colorRope; vh.card.strokeWidth = 5; vh.accent.backgroundTintList = ColorStateList.valueOf(colorRope); vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorRope) }
                "bike" -> { vh.card.strokeColor = colorBike; vh.card.strokeWidth = 5; vh.accent.backgroundTintList = ColorStateList.valueOf(colorBike); vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorBike) }
                else   -> { vh.card.strokeColor = Color.parseColor("#20283618"); vh.card.strokeWidth = 2; vh.accent.visibility = View.GONE; vh.tvDay.setTextColor(colorDarkGreen) }
            }
        }

        private fun updateTimePill(holder: PlanViewHolder, dayEnglish: String, type: String, isPower: Boolean) {
            val pill = holder.tvTimePill ?: return
            val container = holder.itemView.findViewById<LinearLayout>(R.id.llPillsContainer)

            // Pokud je rest, schováme ÚPLNĚ CELÝ kontejner i s marginy
            if (type == "rest") {
                container?.visibility = View.GONE
                pill.visibility = View.GONE
                holder.llKardioExtras?.visibility = View.GONE
                return
            }

            // Pokud to není rest, kontejner musí být vidět
            container?.visibility = View.VISIBLE
            pill.visibility = View.VISIBLE

            // Zbytek logiky pro Kardio/Power doplňky
            holder.llKardioExtras?.visibility = if (isPower) View.GONE else View.VISIBLE

            val timeKey = if (isPower) dayEnglish else "kardio_$dayEnglish"
            val savedTime = TrainingTimeManager.getTrainingTime(requireContext(), timeKey)
            val accentColor = if (isPower) colorDarkGreen else colorRun

            if (savedTime != null) {
                pill.text = "🕐 $savedTime"
                pill.setTextColor(accentColor)
                pill.backgroundTintList = ColorStateList.valueOf(
                    Color.argb(35, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                )
            } else {
                pill.text = "🕐 ČAS"
                pill.setTextColor(colorDarkGreen)
                pill.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1A283618"))
            }
        }

        private fun updateKardioPills(holder: PlanViewHolder, dayEnglish: String, type: String) {
            val ll = holder.llKardioExtras ?: return
            if (type == "rest") { ll.visibility = View.GONE; return }
            ll.visibility = View.VISIBLE

            val duration = trainingPrefs.getString("kardio_duration_$dayEnglish", null)?.toIntOrNull() ?: 0
            holder.tvDurationPill?.text = if (duration > 0) "⏱ ${duration} min" else "⏱ Délka"

            if (type == "rope") {
                val jumps = trainingPrefs.getString("kardio_jumps_$dayEnglish", null)?.toIntOrNull() ?: 0
                holder.tvSpeedPill?.text = if (jumps > 0) "🔂 $jumps skoků" else "🔂 Přeskoky"
            } else {
                val speed = trainingPrefs.getString("kardio_speed_$dayEnglish", null)?.toFloatOrNull() ?: 0f
                holder.tvSpeedPill?.text = if (speed > 0f) "🏃 ${String.format("%.1f", speed)} km/h" else "🏃 Tempo"
            }
        }

        private fun showTimePicker(dayEnglish: String, holder: PlanViewHolder, isPower: Boolean) {
            val ctx     = requireContext()
            val timeKey = if (isPower) dayEnglish else "kardio_$dayEnglish"
            val existing = TrainingTimeManager.getTrainingTime(ctx, timeKey)
            val initH   = existing?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 7
            val initM   = existing?.split(":")?.getOrNull(1)?.toIntOrNull() ?: 0
            val label   = if (isPower) "Čas tréninku — ${holder.tvDayFull.text}"
            else         "Čas kardia — ${holder.tvDayFull.text}"
            MakroflowTimePicker.show(parentFragmentManager, initH, initM, label) { hour, minute ->
                val timeStr = String.format("%02d:%02d", hour, minute)
                TrainingTimeManager.setTrainingTime(ctx, timeKey, timeStr)

                (requireActivity() as? MainActivity)?.refreshStickyNotification()

                val type = if (isPower) trainingPrefs.getString("type_$dayEnglish", "rest") ?: "rest"
                else          trainingPrefs.getString("kardio_type_$dayEnglish", "rest") ?: "rest"
                updateTimePill(holder, dayEnglish, type, isPower)
                holder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                MakroflowNotifications.rescheduleWorkout(ctx)
            }
        }

        override fun getItemCount() = daysMap.size
    }
}
package cz.uhk.macroflow.training

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
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
import cz.uhk.macroflow.dashboard.MacroCalculator
import cz.uhk.macroflow.dashboard.MacroFlowEngine
import cz.uhk.macroflow.training.atlas.MuscleAtlasSheet
import cz.uhk.macroflow.training.body.BodyMapView
import cz.uhk.macroflow.training.body.Muscle
import cz.uhk.macroflow.training.body.TrainingMuscles
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ProgressBar
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
    private val colorStairs    = Color.parseColor("#2D6A4F") // nová barva pro STAIRS
    private val colorFull      = Color.parseColor("#5C4033") // nová barva pro FULL
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
        view?.let { updateStats(it); renderGymBag(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isKardioMode = trainingPrefs.getBoolean("is_kardio_mode", false)

        val modeToggle = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleModeGroup)
        modeToggle?.check(if (isKardioMode) R.id.btnModeKardio else R.id.btnModePower)
        applyTheme(view, isKardioMode, animated = false)
        setupGymBag(view)
        buildBodyLegend(view)
        updateBodyWeek(view, animate = false)
        setupBodyAtlas(view)

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

        // Krokoměr a kalorie - real-time sledování
        val tvTotalSteps   = view.findViewById<TextView>(R.id.tvTotalStepsCount)
        val tvFatLabel     = view.findViewById<TextView>(R.id.tvFatBurnedLabel)
        val tvEmoji        = view.findViewById<TextView>(R.id.tvStepsEmoji)
        val llStepsCounter = view.findViewById<LinearLayout>(R.id.llStepsCounter)

        val db = AppDatabase.getDatabase(requireContext())
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        viewLifecycleOwner.lifecycleScope.launch {
            val profile    = withContext(Dispatchers.IO) { db.userProfileDao().getProfileSync() }
            val stepGoal   = profile?.stepGoal ?: 6000

            db.stepsDao().getStepsForDateFlow(todayStr).collect { stepsEntity ->
                val stepsToday    = stepsEntity?.count ?: 0
                val isGoalReached = stepsToday >= stepGoal

                tvTotalSteps?.text = "$stepsToday / $stepGoal"
                val burnedCalories = MacroCalculator.walkingKcal(profile, stepsToday)
                // Ekvivalent tukové tkáně (~7,7 kcal/g), ne „čistý tuk 9 kcal/g“ –
                // spálené kalorie nejsou ze 100 % tuk a tkáň obsahuje i vodu
                val fatBurnedGrams = burnedCalories / (cz.uhk.macroflow.energy.MacroPlanner.KCAL_PER_KG_TISSUE / 1000.0)

                if (!isGoalReached) {
                    tvFatLabel?.text = String.format(Locale.getDefault(), "🔥 %.1fg TUKU SPÁLENO", fatBurnedGrams)
                    tvEmoji?.text = "👟"
                    tvFatLabel?.setTextColor(if (isKardioMode) colorCream else Color.parseColor("#BC6C25"))
                } else {
                    tvFatLabel?.text = String.format(Locale.getDefault(), "%.1fg TUKU! CÍL SPLNĚN", fatBurnedGrams)
                    tvEmoji?.text = "🎉"
                    tvFatLabel?.setTextColor(if (isKardioMode) Color.WHITE else colorDarkGreen)

                    val bgAlpha = if (isKardioMode) "#40FFFFFF" else "#20BC6C25"
                    llStepsCounter?.backgroundTintList = ColorStateList.valueOf(Color.parseColor(bgAlpha))
                }
            }
        }
    }

    private fun applyTheme(view: View, toKardio: Boolean, animated: Boolean) {
        val targetBg = if (toKardio) colorKardioBg else colorPowerBg
        val root = view.findViewById<View>(R.id.coordinatorPlan) ?: view

        val titleTextColor    = if (toKardio) colorCream else colorDarkGreen
        val subTitleTextColor = if (toKardio) Color.parseColor("#B0DDE5B6") else Color.parseColor("#80283618")
        val colorTextOnDark   = Color.WHITE

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

        // Přebarvení textu "TUKU SPÁLENO" při přepnutí módu
        val tvFatLabel = view.findViewById<TextView>(R.id.tvFatBurnedLabel)
        val stepsText = view.findViewById<TextView>(R.id.tvTotalStepsCount)?.text.toString()
        val isGoalReached = stepsText.contains("/") &&
                (stepsText.split("/")[0].trim().toIntOrNull() ?: 0) >= (stepsText.split("/")[1].trim().toIntOrNull() ?: 6000)

        if (isGoalReached) {
            tvFatLabel?.setTextColor(if (toKardio) Color.WHITE else colorDarkGreen)
        } else {
            tvFatLabel?.setTextColor(if (toKardio) colorCream else Color.parseColor("#BC6C25"))
        }

        // 3. Logika pro statistiky (barevné čtverce)
        if (toKardio) {
            // FULL/STAIRS blok (nový, první)
            view.findViewById<MaterialCardView>(R.id.cardStatFull)?.setCardBackgroundColor(colorStairs)
            view.findViewById<TextView>(R.id.tvStatFullLabel)?.text = "STAIRS"

            view.findViewById<MaterialCardView>(R.id.cardStatPush)?.setCardBackgroundColor(colorRun)
            view.findViewById<TextView>(R.id.tvStatPushLabel)?.text = "BĚH"

            view.findViewById<MaterialCardView>(R.id.cardStatPull)?.setCardBackgroundColor(colorRope)
            view.findViewById<TextView>(R.id.tvStatPullLabel)?.text = "ŠVIH"

            view.findViewById<MaterialCardView>(R.id.cardStatLegs)?.setCardBackgroundColor(colorBike)
            view.findViewById<TextView>(R.id.tvStatLegsLabel)?.text = "KOLO"

            view.findViewById<MaterialCardView>(R.id.cardStatRest)?.setCardBackgroundColor(colorDarkGreen)

            // Bílé texty pro všechny boxy v kardio
            listOf(
                R.id.tvStatFullCount, R.id.tvStatFullLabel,
                R.id.tvStatPushCount, R.id.tvStatPushLabel,
                R.id.tvStatPullCount, R.id.tvStatPullLabel,
                R.id.tvStatLegsCount, R.id.tvStatLegsLabel,
                R.id.tvStatRestCount
            ).forEach { view.findViewById<TextView>(it)?.setTextColor(colorTextOnDark) }
            val restLayout = view.findViewById<MaterialCardView>(R.id.cardStatRest)?.getChildAt(0) as? LinearLayout
            (restLayout?.getChildAt(1) as? TextView)?.setTextColor(colorTextOnDark)

        } else {
            // FULL/STAIRS blok (nový, první)
            view.findViewById<MaterialCardView>(R.id.cardStatFull)?.setCardBackgroundColor(colorFull)
            view.findViewById<TextView>(R.id.tvStatFullLabel)?.text = "FULL"

            view.findViewById<MaterialCardView>(R.id.cardStatPush)?.setCardBackgroundColor(colorDarkGreen)
            view.findViewById<TextView>(R.id.tvStatPushLabel)?.text = "PUSH"

            view.findViewById<MaterialCardView>(R.id.cardStatPull)?.setCardBackgroundColor(Color.parseColor("#DDA15E"))
            view.findViewById<TextView>(R.id.tvStatPullLabel)?.text = "PULL"

            view.findViewById<MaterialCardView>(R.id.cardStatLegs)?.setCardBackgroundColor(Color.parseColor("#BC6C25"))
            view.findViewById<TextView>(R.id.tvStatLegsLabel)?.text = "LEGS"

            view.findViewById<MaterialCardView>(R.id.cardStatRest)?.setCardBackgroundColor(colorCream)

            // Texty v power módu
            listOf(
                R.id.tvStatFullCount, R.id.tvStatFullLabel,
                R.id.tvStatPushCount, R.id.tvStatPushLabel,
                R.id.tvStatPullCount, R.id.tvStatPullLabel,
                R.id.tvStatLegsCount, R.id.tvStatLegsLabel
            ).forEach { view.findViewById<TextView>(it)?.setTextColor(colorTextOnDark) }
            view.findViewById<TextView>(R.id.tvStatRestCount)?.setTextColor(colorDarkGreen)
            val restLayout = view.findViewById<MaterialCardView>(R.id.cardStatRest)?.getChildAt(0) as? LinearLayout
            (restLayout?.getChildAt(1) as? TextView)?.setTextColor(colorDarkGreen)
        }

        // 4. Přepínač módů
        val btnPower  = view.findViewById<MaterialButton>(R.id.btnModePower)
        val btnKardio = view.findViewById<MaterialButton>(R.id.btnModeKardio)
        if (toKardio) {
            btnPower?.setTextColor(Color.parseColor("#80DDE5B6"))
            btnKardio?.setTextColor(colorCream)
        } else {
            btnPower?.setTextColor(colorDarkGreen)
            btnKardio?.setTextColor(Color.parseColor("#80283618"))
        }
    }

    /** Barva typu tréninku – tlačítka i rozsvícené partie. */
    private fun typeColor(type: String): Int = when (type) {
        "stairs" -> colorStairs
        "full"   -> colorFull
        "run"    -> colorRun
        "rope"   -> colorRope
        "bike"   -> colorBike
        "push"   -> colorDarkGreen
        "pull"   -> Color.parseColor("#DDA15E")
        "legs"   -> Color.parseColor("#BC6C25")
        else     -> colorDarkGreen
    }

    private fun updateStats(view: View) {
        var full = 0; var a = 0; var b = 0; var c = 0; var rest = 0
        val prefix    = if (isKardioMode) "kardio_type_" else "type_"
        val typeFull  = if (isKardioMode) "stairs" else "full"
        val typeA     = if (isKardioMode) "run"    else "push"
        val typeB     = if (isKardioMode) "rope"   else "pull"
        val typeC     = if (isKardioMode) "bike"   else "legs"

        daysMap.forEach { (key, _, _) ->
            when (trainingPrefs.getString("$prefix$key", "rest")?.lowercase()) {
                typeFull -> full++
                typeA    -> a++
                typeB    -> b++
                typeC    -> c++
                else     -> rest++
            }
        }
        view.findViewById<TextView>(R.id.tvStatFullCount)?.text  = full.toString()
        view.findViewById<TextView>(R.id.tvStatPushCount)?.text  = a.toString()
        view.findViewById<TextView>(R.id.tvStatPullCount)?.text  = b.toString()
        view.findViewById<TextView>(R.id.tvStatLegsCount)?.text  = c.toString()
        view.findViewById<TextView>(R.id.tvStatRestCount)?.text  = rest.toString()
        updateBodyWeek(view, animate = true)
    }

    // ── Týden na těle ────────────────────────────────────────────────────────

    private fun weekTypes(): List<String?> {
        val prefix = if (isKardioMode) "kardio_type_" else "type_"
        return daysMap.map { (key, _, _) -> trainingPrefs.getString("$prefix$key", "rest") }
    }

    private fun weekColor() = if (isKardioMode) colorRun else Color.parseColor("#606C38")

    /** Intenzita partie = týdenní frekvence / 2 (2× týdně = plná barva). */
    private fun updateBodyWeek(view: View, animate: Boolean) {
        val body = view.findViewById<BodyMapView>(R.id.bodyWeek) ?: return
        val freq = TrainingMuscles.weeklyFrequency(weekTypes())
        body.setIntensities(freq.mapValues { (it.value / TrainingMuscles.TARGET_PER_WEEK).coerceAtMost(1.0) }, weekColor(), animate)
        val hint = view.findViewById<TextView>(R.id.tvBodyHint)
        hint?.text = when {
            freq.values.all { it == 0.0 } -> "Vyber tréninky na jednotlivé dny a uvidíš, co za týden procvičíš."
            isKardioMode -> "Kardio zatěžuje hlavně nohy. Frekvenci pro růst svalů hlídej v režimu Power."
            else -> {
                val below = TrainingMuscles.belowTarget(freq)
                if (below.isEmpty()) "Každá partie aspoň 2× týdně ✓"
                else "Pod 2× týdně: " + below.joinToString(", ") { it.label } +
                    ". Pro růst svalu se doporučuje každou partii 2× týdně."
            }
        }
        buildBodyLegend(view)
    }

    /** Klepnutí na kartu otevře atlas svalů; klepnutí přímo na sval ho v atlasu rovnou vybere. */
    private fun setupBodyAtlas(view: View) {
        val body = view.findViewById<BodyMapView>(R.id.bodyWeek) ?: return
        val open = { m: Muscle? ->
            MuscleAtlasSheet.show(childFragmentManager, TrainingMuscles.weeklyFrequency(weekTypes()), weekColor(), m)
        }
        view.findViewById<View>(R.id.cardBodyWeek)?.setOnClickListener { open(null) }
        body.setOnClickListener { open(null) }
        body.onMuscleTap = { m ->
            body.select(null, animate = false)   // malá postava výběr nedrží, rozsvítí se až v atlasu
            open(m)
        }
    }

    private fun buildBodyLegend(view: View) {
        val ll = view.findViewById<LinearLayout>(R.id.llBodyLegend) ?: return
        val dp = resources.displayMetrics.density
        ll.removeAllViews()
        val body = view.findViewById<BodyMapView>(R.id.bodyWeek)
        val idle = body?.idleMuscleColor ?: Color.LTGRAY
        listOf("0×" to idle,
            "1×" to androidx.core.graphics.ColorUtils.blendARGB(idle, weekColor(), 0.35f + 0.65f * 0.5f),
            "2×+ týdně" to weekColor()
        ).forEach { (label, c) ->
            ll.addView(View(requireContext()).apply {
                background = GradientDrawable().apply { cornerRadius = 3 * dp; setColor(c) }
                layoutParams = LinearLayout.LayoutParams((12 * dp).toInt(), (12 * dp).toInt()).apply { marginStart = (10 * dp).toInt() }
            })
            ll.addView(TextView(requireContext()).apply {
                text = label; textSize = 11f; setTextColor(Color.parseColor("#99283618"))
                setPadding((4 * dp).toInt(), 0, 0, 0)
            })
        }
    }

    // ── Taška do gymu ────────────────────────────────────────────────────────

    private val bagPrefs by lazy { requireContext().getSharedPreferences("GymBagPrefs", Context.MODE_PRIVATE) }
    private var bagExpanded = true

    private fun todayKey() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun loadBag(): GymBagState {
        val items = GymBag.decodeItems(bagPrefs.getString("items", null))
        val saved = if (items == null) GymBag.defaults(todayKey())
            else GymBagState(items, GymBag.decodeChecked(bagPrefs.getString("checked", null)), bagPrefs.getString("date", "") ?: "")
        return GymBag.forDay(saved, todayKey())
    }

    private fun saveBag(s: GymBagState) {
        bagPrefs.edit()
            .putString("items", GymBag.encodeItems(s.items))
            .putString("checked", GymBag.encodeChecked(s.checked))
            .putString("date", s.date)
            .apply()
    }

    private fun isTrainingToday(): Boolean {
        val day = SimpleDateFormat("EEEE", Locale.ENGLISH).format(Date())
        return trainingPrefs.getString("type_$day", "rest") != "rest" ||
            trainingPrefs.getString("kardio_type_$day", "rest") != "rest"
    }

    private fun setupGymBag(view: View) {
        // Rozbalená jen v tréninkový den, dokud není vše sbalené
        val s = loadBag()
        bagExpanded = isTrainingToday() && !s.allPacked
        view.findViewById<View>(R.id.llBagHeader)?.setOnClickListener {
            bagExpanded = !bagExpanded
            renderGymBag(view)
        }
        view.findViewById<View>(R.id.btnBagAdd)?.setOnClickListener { showAddBagItem(view) }
        view.findViewById<View>(R.id.btnBagReset)?.setOnClickListener {
            val st = GymBag.unpackAll(loadBag()); saveBag(st); renderGymBag(view)
        }
        renderGymBag(view)
    }

    private fun renderGymBag(view: View) {
        val grid = view.findViewById<GridLayout>(R.id.gridBag) ?: return
        val s = loadBag()
        saveBag(s)
        val dp = resources.displayMetrics.density
        val done = s.allPacked

        view.findViewById<TextView>(R.id.tvBagTitle)?.text = if (done) "🎒 VŠE SBALENO ✓" else "🎒 TAŠKA DO GYMU"
        view.findViewById<TextView>(R.id.tvBagCount)?.apply {
            text = "${s.packedCount}/${s.items.size}"
            backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (done) "#606C38" else "#1A283618"))
            setTextColor(if (done) colorCream else colorDarkGreen)
        }
        view.findViewById<TextView>(R.id.tvBagChevron)?.text = if (bagExpanded) "▴" else "▾"
        view.findViewById<ProgressBar>(R.id.pbBag)?.apply {
            max = s.items.size.coerceAtLeast(1); progress = s.packedCount
        }
        view.findViewById<View>(R.id.llBagBody)?.visibility = if (bagExpanded) View.VISIBLE else View.GONE
        if (!bagExpanded) return

        grid.removeAllViews()
        s.items.forEach { item ->
            val packed = s.isPacked(item)
            grid.addView(TextView(requireContext()).apply {
                text = (if (packed) "✓  " else "○  ") + item.label
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                typeface = if (packed) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (packed) colorCream else colorDarkGreen)
                setPadding((12 * dp).toInt(), (9 * dp).toInt(), (10 * dp).toInt(), (9 * dp).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 14 * dp
                    setColor(Color.parseColor(if (packed) "#606C38" else "#0F283618"))
                }
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply {
                    width = 0
                    setMargins((3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt())
                }
                setOnClickListener {
                    val before = loadBag()
                    val after = GymBag.toggle(before, item.id)
                    saveBag(after)
                    performHapticFeedback(if (after.allPacked) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CONTEXT_CLICK)
                    renderGymBag(view)
                }
                setOnLongClickListener {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Odebrat „${item.label}“?")
                        .setMessage("Věc zmizí ze seznamu. Kdykoli ji přidáš zpátky.")
                        .setPositiveButton("Odebrat") { _, _ -> saveBag(GymBag.remove(loadBag(), item.id)); renderGymBag(view) }
                        .setNegativeButton("Nechat", null)
                        .show()
                    true
                }
            })
        }
    }

    private fun showAddBagItem(view: View) {
        val dp = resources.displayMetrics.density
        val input = EditText(requireContext()).apply {
            hint = "např. Opasek, magnézium, šejkr"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(android.text.InputFilter.LengthFilter(GymBag.MAX_LABEL))
        }
        val box = FrameLayout(requireContext()).apply {
            setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Přidat do tašky")
            .setView(box)
            .setPositiveButton("Přidat") { _, _ ->
                val id = bagPrefs.getInt("next_id", 0)
                val before = loadBag()
                val after = GymBag.add(before, input.text.toString(), "u$id")
                if (after !== before) {
                    bagPrefs.edit().putInt("next_id", id + 1).apply()
                    saveBag(after)
                    renderGymBag(view)
                }
            }
            .setNegativeButton("Zrušit", null)
            .show()
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
            val btnFull: MaterialButton?               = view.findViewById(R.id.btnFull)
            val btnPush: MaterialButton?               = view.findViewById(R.id.btnPush)
            val btnPull: MaterialButton?               = view.findViewById(R.id.btnPull)
            val btnLegs: MaterialButton?               = view.findViewById(R.id.btnLegs)
            val btnDelete: View                        = view.findViewById(R.id.btnDeleteDayData)
            val llMuscles: View?                       = view.findViewById(R.id.llDayMuscles)
            val bodyDay: BodyMapView?                  = view.findViewById<BodyMapView>(R.id.bodyDay)?.apply { showCaptions = false }
            val tvMuscles: TextView?                   = view.findViewById(R.id.tvDayMuscles)
        }

        /** Rozsvítí partie zvoleného typu v kartě dne (odpočinek = skryto). */
        private fun updateDayMuscles(holder: PlanViewHolder, type: String, animate: Boolean) {
            val ll = holder.llMuscles ?: return
            val muscles = TrainingMuscles.of(type)
            if (muscles.isEmpty()) { ll.visibility = View.GONE; return }
            ll.visibility = View.VISIBLE
            holder.bodyDay?.setIntensities(muscles, typeColor(type), animate)
            holder.tvMuscles?.text = TrainingMuscles.describe(type)
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
            val bgColor     = if (isKardio) "#F7F9F2" else "#FAFAF5"
            val strokeColor = if (isKardio) "#40DDE5B6" else "#20283618"
            holder.card.setCardBackgroundColor(Color.parseColor(bgColor))
            holder.card.strokeColor = Color.parseColor(strokeColor)
            holder.card.strokeWidth = if (isKardio) 3 else 2
            holder.divider.setBackgroundColor(Color.parseColor(if (isKardio) "#15283618" else "#20283618"))
            holder.tvDayFull.setTextColor(Color.parseColor(if (isKardio) "#90283618" else "#80283618"))
        }

        /**
         * Přebarvení tlačítek toggle skupiny.
         * selectedType == "rest" (nic nevybráno) = všechna tlačítka prázdná / outlined.
         */
        private fun updateToggleGroupColors(vh: PlanViewHolder, selectedType: String, isKardio: Boolean) {
            val buttons = listOf(
                vh.btnFull to if (isKardio) "stairs" else "full",
                vh.btnPush to if (isKardio) "run"    else "push",
                vh.btnPull to if (isKardio) "rope"   else "pull",
                vh.btnLegs to if (isKardio) "bike"   else "legs"
            )
            buttons.forEach { (btn, type) ->
                if (btn != null) {
                    if (selectedType == type) {
                        btn.setTextColor(Color.WHITE)
                        btn.backgroundTintList = ColorStateList.valueOf(typeColor(type))
                        btn.strokeWidth = 0
                    } else {
                        btn.setTextColor(colorDarkGreen)
                        btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                        btn.strokeWidth = 2
                        btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#20283618"))
                    }
                }
            }
        }

        /**
         * POWER bind — tlačítka: FULL | PUSH | PULL | LEGS
         * Nic nevybráno = OFF (ukládáme "rest").
         * Klik na již vybrané tlačítko = odznačit → OFF.
         */
        private fun bindPower(holder: PlanViewHolder, dayEnglish: String) {
            holder.btnFull?.text = "FULL"
            holder.btnPush?.text = "PUSH"
            holder.btnPull?.text = "PULL"
            holder.btnLegs?.text = "LEGS"
            holder.llKardioExtras?.visibility = View.GONE

            val savedType = trainingPrefs.getString("type_$dayEnglish", "rest") ?: "rest"

            // Nastavíme vizuál bez listeneru (clearujeme aby se nespustil)
            holder.toggleGroup.clearOnButtonCheckedListeners()
            refreshPowerCheck(holder, savedType)
            updatePowerCardVisual(holder, savedType)
            updateToggleGroupColors(holder, savedType, false)
            updateTimePill(holder, dayEnglish, savedType, isPower = true)
            updateDayMuscles(holder, savedType, animate = false)

            holder.tvTimePill?.setOnClickListener { showTimePicker(dayEnglish, holder, isPower = true) }

            // Listener na každé tlačítko zvlášť — umožní odznačení (toggle chování)
            listOf(
                holder.btnFull to "full",
                holder.btnPush to "push",
                holder.btnPull to "pull",
                holder.btnLegs to "legs"
            ).forEach { (btn, type) ->
                btn?.setOnClickListener {
                    val current = trainingPrefs.getString("type_$dayEnglish", "rest") ?: "rest"
                    val newType = if (current == type) "rest" else type
                    trainingPrefs.edit().putString("type_$dayEnglish", newType).apply()
                    (requireActivity() as? MainActivity)?.refreshStickyNotification()
                    holder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    refreshPowerCheck(holder, newType)
                    updatePowerCardVisual(holder, newType)
                    updateToggleGroupColors(holder, newType, false)
                    updateTimePill(holder, dayEnglish, newType, isPower = true)
                    updateDayMuscles(holder, newType, animate = true)
                    view?.let { updateStats(it) }
                }
            }
        }

        /**
         * Nastaví vizuální check state toggle group pro power bez spuštění listeneru.
         */
        private fun refreshPowerCheck(holder: PlanViewHolder, type: String) {
            holder.toggleGroup.clearOnButtonCheckedListeners()
            when (type) {
                "full" -> holder.toggleGroup.check(R.id.btnFull)
                "push" -> holder.toggleGroup.check(R.id.btnPush)
                "pull" -> holder.toggleGroup.check(R.id.btnPull)
                "legs" -> holder.toggleGroup.check(R.id.btnLegs)
                else   -> holder.toggleGroup.clearChecked()
            }
        }

        /**
         * KARDIO bind — tlačítka: STAIRS | BĚH | ŠVIH | KOLO
         * Nic nevybráno = OFF (ukládáme "rest").
         * Klik na již vybrané tlačítko = odznačit → OFF.
         */
        private fun bindKardio(holder: PlanViewHolder, dayEnglish: String) {
            holder.btnFull?.text = "STAIRS"
            holder.btnPush?.text = "BĚH"
            holder.btnPull?.text = "ŠVIH"
            holder.btnLegs?.text = "KOLO"

            val savedType = trainingPrefs.getString("kardio_type_$dayEnglish", "rest") ?: "rest"

            holder.toggleGroup.clearOnButtonCheckedListeners()
            refreshKardioCheck(holder, savedType)
            updateKardioCardVisual(holder, savedType)
            updateToggleGroupColors(holder, savedType, true)
            updateTimePill(holder, dayEnglish, savedType, isPower = false)
            updateDayMuscles(holder, savedType, animate = false)
            updateKardioPills(holder, dayEnglish, savedType)
            updateDeleteButtonVisibility(holder, dayEnglish)

            holder.btnDelete.setOnClickListener {
                trainingPrefs.edit().apply {
                    remove("kardio_$dayEnglish")
                    remove("kardio_duration_$dayEnglish")
                    remove("kardio_speed_$dayEnglish")
                    remove("kardio_jumps_$dayEnglish")
                }.apply()
                val currentType = trainingPrefs.getString("kardio_type_$dayEnglish", "rest") ?: "rest"
                updateTimePill(holder, dayEnglish, currentType, isPower = false)
                updateKardioPills(holder, dayEnglish, currentType)
                updateDeleteButtonVisibility(holder, dayEnglish)
                view?.let { updateStats(it) }
            }

            holder.tvTimePill?.setOnClickListener { showTimePicker(dayEnglish, holder, isPower = false) }
            holder.tvDurationPill?.setOnClickListener { showKardioPicker(dayEnglish, holder) }
            holder.tvSpeedPill?.setOnClickListener { showKardioPicker(dayEnglish, holder) }

            // Listener na každé tlačítko zvlášť — umožní odznačení
            listOf(
                holder.btnFull to "stairs",
                holder.btnPush to "run",
                holder.btnPull to "rope",
                holder.btnLegs to "bike"
            ).forEach { (btn, type) ->
                btn?.setOnClickListener {
                    val current = trainingPrefs.getString("kardio_type_$dayEnglish", "rest") ?: "rest"
                    val newType = if (current == type) "rest" else type
                    trainingPrefs.edit().putString("kardio_type_$dayEnglish", newType).apply()
                    (requireActivity() as? MainActivity)?.refreshStickyNotification()
                    holder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    refreshKardioCheck(holder, newType)
                    updateKardioCardVisual(holder, newType)
                    updateToggleGroupColors(holder, newType, true)
                    updateTimePill(holder, dayEnglish, newType, isPower = false)
                    updateDayMuscles(holder, newType, animate = true)
                    updateKardioPills(holder, dayEnglish, newType)
                    updateDeleteButtonVisibility(holder, dayEnglish)
                    view?.let { updateStats(it) }
                }
            }
        }

        /**
         * Nastaví vizuální check state toggle group pro kardio bez spuštění listeneru.
         */
        private fun refreshKardioCheck(holder: PlanViewHolder, type: String) {
            holder.toggleGroup.clearOnButtonCheckedListeners()
            when (type) {
                "stairs" -> holder.toggleGroup.check(R.id.btnFull)
                "run"    -> holder.toggleGroup.check(R.id.btnPush)
                "rope"   -> holder.toggleGroup.check(R.id.btnPull)
                "bike"   -> holder.toggleGroup.check(R.id.btnLegs)
                else     -> holder.toggleGroup.clearChecked()
            }
        }

        private fun updateDeleteButtonVisibility(holder: PlanViewHolder, dayEnglish: String) {
            val hasTime     = TrainingTimeManager.getTrainingTime(requireContext(), "kardio_$dayEnglish") != null
            val hasDuration = trainingPrefs.getString("kardio_duration_$dayEnglish", null) != null
            holder.btnDelete.visibility = if (hasTime || hasDuration) View.VISIBLE else View.INVISIBLE
        }

        private fun showKardioPicker(dayEnglish: String, holder: PlanViewHolder) {
            val dayCz = getString(daysMap[holder.adapterPosition].second)
            MakroflowKardioPicker.show(parentFragmentManager, dayEnglish, dayCz) {
                val currentType = trainingPrefs.getString("kardio_type_$dayEnglish", "rest") ?: "rest"
                updateKardioPills(holder, dayEnglish, currentType)
                updateDeleteButtonVisibility(holder, dayEnglish)
                view?.let { updateStats(it) }
            }
        }

        private fun updatePowerCardVisual(vh: PlanViewHolder, type: String) {
            val colorPush = Color.parseColor("#606C38")
            val colorPull = Color.parseColor("#283618")
            val colorLegs = Color.parseColor("#BC6C25")
            when (type) {
                "full" -> { vh.card.strokeColor = colorFull;  vh.card.strokeWidth = 4; vh.accent.backgroundTintList = ColorStateList.valueOf(colorFull);  vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorFull) }
                "push" -> { vh.card.strokeColor = colorPush;  vh.card.strokeWidth = 4; vh.accent.backgroundTintList = ColorStateList.valueOf(colorPush);  vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorPush) }
                "pull" -> { vh.card.strokeColor = colorPull;  vh.card.strokeWidth = 4; vh.accent.backgroundTintList = ColorStateList.valueOf(colorPull);  vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorPull) }
                "legs" -> { vh.card.strokeColor = colorLegs;  vh.card.strokeWidth = 4; vh.accent.backgroundTintList = ColorStateList.valueOf(colorLegs);  vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorLegs) }
                else   -> { vh.card.strokeColor = Color.parseColor("#20283618"); vh.card.strokeWidth = 2; vh.accent.visibility = View.GONE; vh.tvDay.setTextColor(colorDarkGreen) }
            }
        }

        private fun updateKardioCardVisual(vh: PlanViewHolder, type: String) {
            when (type) {
                "stairs" -> { vh.card.strokeColor = colorStairs; vh.card.strokeWidth = 5; vh.accent.backgroundTintList = ColorStateList.valueOf(colorStairs); vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorStairs) }
                "run"    -> { vh.card.strokeColor = colorRun;    vh.card.strokeWidth = 5; vh.accent.backgroundTintList = ColorStateList.valueOf(colorRun);    vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorRun) }
                "rope"   -> { vh.card.strokeColor = colorRope;   vh.card.strokeWidth = 5; vh.accent.backgroundTintList = ColorStateList.valueOf(colorRope);   vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorRope) }
                "bike"   -> { vh.card.strokeColor = colorBike;   vh.card.strokeWidth = 5; vh.accent.backgroundTintList = ColorStateList.valueOf(colorBike);   vh.accent.visibility = View.VISIBLE; vh.tvDay.setTextColor(colorBike) }
                else     -> { vh.card.strokeColor = Color.parseColor("#20283618"); vh.card.strokeWidth = 2; vh.accent.visibility = View.GONE; vh.tvDay.setTextColor(colorDarkGreen) }
            }
        }

        private fun updateTimePill(holder: PlanViewHolder, dayEnglish: String, type: String, isPower: Boolean) {
            val pill      = holder.tvTimePill ?: return
            val container = holder.itemView.findViewById<LinearLayout>(R.id.llPillsContainer)
            if (type == "rest") {
                container?.visibility = View.GONE
                pill.visibility = View.GONE
                holder.llKardioExtras?.visibility = View.GONE
                return
            }
            container?.visibility = View.VISIBLE
            pill.visibility = View.VISIBLE
            holder.llKardioExtras?.visibility = if (isPower) View.GONE else View.VISIBLE

            val timeKey     = if (isPower) dayEnglish else "kardio_$dayEnglish"
            val savedTime   = TrainingTimeManager.getTrainingTime(requireContext(), timeKey)
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

            when (type) {
                "rope" -> {
                    val jumps = trainingPrefs.getString("kardio_jumps_$dayEnglish", null)?.toIntOrNull() ?: 0
                    holder.tvSpeedPill?.text = if (jumps > 0) "🔂 $jumps skoků" else "🔂 Přeskoky"
                }
                "stairs" -> {
                    // Pro schody zobrazíme pouze délku, speed pill schováme
                    holder.tvSpeedPill?.visibility = View.GONE
                }
                else -> {
                    holder.tvSpeedPill?.visibility = View.VISIBLE
                    val speed = trainingPrefs.getString("kardio_speed_$dayEnglish", null)?.toFloatOrNull() ?: 0f
                    holder.tvSpeedPill?.text = if (speed > 0f) "🏃 ${String.format("%.1f", speed)} km/h" else "🏃 Tempo"
                }
            }
        }

        private fun showTimePicker(dayEnglish: String, holder: PlanViewHolder, isPower: Boolean) {
            val timeKey  = if (isPower) dayEnglish else "kardio_$dayEnglish"
            val existing = TrainingTimeManager.getTrainingTime(requireContext(), timeKey)
            val label    = if (isPower) "Čas tréninku — ${holder.tvDayFull.text}" else "Čas kardia — ${holder.tvDayFull.text}"
            MakroflowTimePicker.show(
                parentFragmentManager,
                existing?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 7,
                existing?.split(":")?.getOrNull(1)?.toIntOrNull() ?: 0,
                label
            ) { h, m ->
                val timeStr = String.format("%02d:%02d", h, m)
                TrainingTimeManager.setTrainingTime(requireContext(), timeKey, timeStr)
                (requireActivity() as? MainActivity)?.refreshStickyNotification()
                val currentType = if (isPower)
                    trainingPrefs.getString("type_$dayEnglish",        "rest") ?: "rest"
                else
                    trainingPrefs.getString("kardio_type_$dayEnglish", "rest") ?: "rest"
                updateTimePill(holder, dayEnglish, currentType, isPower)
                MakroflowNotifications.rescheduleWorkout(requireContext())
            }
        }

        override fun getItemCount() = daysMap.size
    }
}
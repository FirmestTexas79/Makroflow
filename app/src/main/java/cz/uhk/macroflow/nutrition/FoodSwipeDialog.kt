package cz.uhk.macroflow.nutrition

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.*
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.R
import cz.uhk.macroflow.training.TrainingTimeManager
import cz.uhk.macroflow.data.ConsumedSnackEntity
import cz.uhk.macroflow.data.SnackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp

class FoodSwipeDialog : DialogFragment() {

    private var initialX = 0f
    private var snackList = mutableListOf<SnackEntity>()

    private lateinit var currentMealContext: TrainingTimeManager.MealContext

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, s: Bundle?
    ): View? = inflater.inflate(R.layout.layout_food_card, container, false)

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.5f)
            attributes?.windowAnimations = android.R.style.Animation_Dialog
        }
    }

    @SuppressLint("ClickableViewAccessibility", "MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentMealContext = TrainingTimeManager.getMealContext(requireContext())

        val card = view.findViewById<MaterialCardView>(R.id.foodCard)
        card.post {
            card.pivotX = card.width / 2f
            card.pivotY = card.height * 2.0f
        }

        val vibrator = getVibrator()

        updateContextBanner(view, currentMealContext)
        loadRealSnacks(view)

        card.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { initialX = event.rawX; true }
                MotionEvent.ACTION_MOVE -> {
                    val diff = event.rawX - initialX
                    v.translationX = diff
                    v.rotation = diff / 40f
                    if (abs(diff) > 120) {
                        triggerHaptic(vibrator)
                        card.setCardBackgroundColor(
                            Color.parseColor(if (diff > 0) "#606C38" else "#BC6C25")
                        )
                        updateElementsColor(view, "#FEFAE0")
                    } else {
                        resetCardToDefaultState(view)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diff = event.rawX - initialX
                    if (abs(diff) > 450) {
                        if (diff > 0) handleConfirm(view) else handleSkip(view)
                    } else {
                        v.animate().translationX(0f).rotation(0f).setDuration(300)
                            .withEndAction { resetCardToDefaultState(view) }.start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun loadRealSnacks(view: View) {
        lifecycleScope.launch {
            val allSnacks = AppDatabase.getDatabase(requireContext())
                .snackDao().getAllSnacks().first().toMutableList()
            snackList = sortByFuzzyContext(allSnacks, currentMealContext).toMutableList()
            updateUI(view)
        }
    }

    private fun sortByFuzzyContext(
        snacks: List<SnackEntity>,
        ctx: TrainingTimeManager.MealContext
    ): List<SnackEntity> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        return snacks.sortedByDescending { s ->
            val total = s.p + s.s + s.t + 0.01f
            val kcal  = (s.p * 4 + s.s * 4 + s.t * 9)

            val macroScore = when (ctx) {
                TrainingTimeManager.MealContext.PRE_WORKOUT -> {
                    val carbRatio  = (s.s / total).coerceIn(0f, 1f)
                    val fatPenalty = (s.t / total).coerceIn(0f, 1f) * 0.4f
                    carbRatio - fatPenalty
                }
                TrainingTimeManager.MealContext.IMMINENT -> {
                    val carbRatio    = (s.s / total).coerceIn(0f, 1f)
                    val lightBonus   = if (kcal < 400) 0.4f else 0f
                    val heavyPenalty = if (kcal > 600) 0.5f else 0f
                    carbRatio + lightBonus - heavyPenalty
                }
                TrainingTimeManager.MealContext.POST_WORKOUT_EARLY -> {
                    val protRatio = (s.p / total).coerceIn(0f, 1f)
                    val carbBonus = (s.s / total * 0.3f).coerceIn(0f, 0.3f)
                    protRatio + carbBonus
                }
                TrainingTimeManager.MealContext.POST_WORKOUT_LATE -> {
                    val protRatio = (s.p / total).coerceIn(0f, 1f)
                    val carbRatio = (s.s / total).coerceIn(0f, 1f)
                    (protRatio + carbRatio) / 2f
                }
                TrainingTimeManager.MealContext.DURING -> {
                    val lightBonus = if (kcal < 200) 0.8f else 0f
                    val carbRatio  = (s.s / total * 0.3f)
                    lightBonus + carbRatio
                }
                TrainingTimeManager.MealContext.NO_TRAINING,
                TrainingTimeManager.MealContext.LONG_BEFORE,
                TrainingTimeManager.MealContext.LONG_AFTER -> {
                    val fiberBonus = (s.fiber / total).coerceIn(0f, 0.3f)
                    when (hour) {
                        in 6..9   -> (s.p / total) + fiberBonus
                        in 10..13 -> (s.s / total)
                        in 14..17 -> (s.p + s.s) / (total * 2)
                        in 18..21 -> (s.p / total) + fiberBonus
                        else      -> s.p / total
                    }
                }
            }

            val calorieScore = when (ctx) {
                TrainingTimeManager.MealContext.PRE_WORKOUT        -> gaussianScore(kcal, 450f, 150f)
                TrainingTimeManager.MealContext.IMMINENT           -> gaussianScore(kcal, 200f, 100f)
                TrainingTimeManager.MealContext.POST_WORKOUT_EARLY -> gaussianScore(kcal, 550f, 150f)
                else                                               -> gaussianScore(kcal, 600f, 200f)
            }

            (macroScore * 0.6f + calorieScore * 0.4f).toDouble()
        }
    }

    private fun gaussianScore(value: Float, ideal: Float, sigma: Float): Float {
        val diff = value - ideal
        return exp(-(diff * diff) / (2 * sigma * sigma)).toFloat()
    }

    private fun getTimingTag(snack: SnackEntity, ctx: TrainingTimeManager.MealContext): Pair<String, String>? {
        val total     = snack.p + snack.s + snack.t + 0.01f
        val carbRatio = snack.s / total
        val protRatio = snack.p / total
        val kcal      = (snack.p * 4 + snack.s * 4 + snack.t * 9)

        return when (ctx) {
            TrainingTimeManager.MealContext.PRE_WORKOUT ->
                if (carbRatio > 0.5f)       "⚡ Ideální PRE" to "#606C38"
                else if (carbRatio > 0.35f) "✓ Vhodné PRE"  to "#DDA15E"
                else null
            TrainingTimeManager.MealContext.IMMINENT ->
                if (kcal < 300 && carbRatio > 0.4f) "✓ Lehké a rychlé"       to "#606C38"
                else if (kcal > 500)                 "⚠ Těžké před tréninkem" to "#BC6C25"
                else null
            TrainingTimeManager.MealContext.POST_WORKOUT_EARLY ->
                if (protRatio > 0.4f)       "💪 Ideální POST" to "#283618"
                else if (protRatio > 0.25f) "✓ Vhodné POST"  to "#606C38"
                else null
            TrainingTimeManager.MealContext.DURING ->
                if (kcal < 200) "💧 Vhodné při tréninku" to "#606C38"
                else            "⚠ Příliš těžké"         to "#BC6C25"
            else -> null
        }
    }

    private fun updateContextBanner(view: View, ctx: TrainingTimeManager.MealContext) {
        val banner  = view.findViewById<TextView>(R.id.tvFoodContextBanner) ?: return
        val minutes = TrainingTimeManager.minutesToTraining(requireContext())

        val (text, bgColor) = when (ctx) {
            TrainingTimeManager.MealContext.PRE_WORKOUT -> {
                val min = minutes ?: 0
                "⚡ PRE trénink za ${TrainingTimeManager.formatCountdown(min)}" to "#606C38"
            }
            TrainingTimeManager.MealContext.IMMINENT        -> "⏱️ Trénink za chvíli — lehce!"        to "#DDA15E"
            TrainingTimeManager.MealContext.POST_WORKOUT_EARLY -> "💪 POST okno — dej bílkoviny hned!" to "#283618"
            TrainingTimeManager.MealContext.DURING          -> "🏋️ Trénink probíhá — hydratace!"      to "#BC6C25"
            TrainingTimeManager.MealContext.POST_WORKOUT_LATE  -> "🔄 Po tréninku — doplň zásoby"    to "#606C38"
            else -> { banner.visibility = View.GONE; return }
        }
        banner.text = text
        banner.backgroundTintList = ColorStateList.valueOf(Color.parseColor(bgColor))
        banner.visibility = View.VISIBLE
    }

    private fun updateUI(view: View) {
        val current = snackList.firstOrNull() ?: run { dismiss(); return }
        resetCardToDefaultState(view)

        val kcal = ((current.p * 4) + (current.s * 4) + (current.t * 9)).toInt()
        view.findViewById<TextView>(R.id.tvFoodName).text     = current.name
        view.findViewById<TextView>(R.id.tvFoodCalories).text = "$kcal kcal | ${current.energyKj.toInt()} kJ"
        view.findViewById<TextView>(R.id.tvFoodProtein).text  = "${current.p.toInt()}g"
        view.findViewById<TextView>(R.id.tvFoodCarbs).text    = "${current.s.toInt()}g"
        view.findViewById<TextView>(R.id.tvFoodFat).text      = "${current.t.toInt()}g"

        view.findViewById<TextView>(R.id.tvFoodFiber)?.apply {
            text       = "Vláknina: ${"%.1f".format(current.fiber)}g"
            visibility = if (current.fiber > 0.1f) View.VISIBLE else View.GONE
        }

        view.findViewById<ImageView>(R.id.ivFoodImage)
            .setImageResource(getIconForSnack(current))

        val tvTag = view.findViewById<TextView>(R.id.tvFoodTimingTag)
        val tag   = getTimingTag(current, currentMealContext)
        if (tag != null) {
            tvTag?.text = tag.first
            tvTag?.setTextColor(Color.parseColor(tag.second))
            tvTag?.backgroundTintList = ColorStateList.valueOf(Color.parseColor(tag.second + "20"))
            tvTag?.visibility = View.VISIBLE
        } else {
            tvTag?.visibility = View.GONE
        }
    }

    private fun handleConfirm(view: View) {
        if (snackList.isEmpty()) return
        val snack = snackList.removeAt(0)

        lifecycleScope.launch {
            val kcal = ((snack.p * 4) + (snack.s * 4) + (snack.t * 9)).toDouble()
            cz.uhk.macroflow.dashboard.MacroFlowEngine.logSwipedFood(
                context     = requireContext(),
                name        = snack.name,
                p           = snack.p.toDouble(),
                s           = snack.s.toDouble(),
                t           = snack.t.toDouble(),
                cal         = kcal,
                kj          = snack.energyKj.toDouble(),
                fiber       = snack.fiber.toDouble(),
                mealContext = currentMealContext.name
            )
            resetCardPositionAndData(view)
        }
    }

    private fun handleSkip(view: View) {
        if (snackList.isEmpty()) return
        snackList.removeAt(0)
        resetCardPositionAndData(view)
    }

    private fun resetCardPositionAndData(view: View) {
        view.findViewById<MaterialCardView>(R.id.foodCard).apply {
            translationX = 0f; rotation = 0f
        }
        if (snackList.isNotEmpty()) updateUI(view) else dismiss()
    }

    private fun resetCardToDefaultState(view: View) {
        val card        = view.findViewById<MaterialCardView>(R.id.foodCard)
        val viewImageBg = view.findViewById<View>(R.id.viewImageBg)
        val ivFoodImage = view.findViewById<ImageView>(R.id.ivFoodImage)

        val colorCream      = Color.parseColor("#FEFAE0")
        val colorDark       = Color.parseColor("#283618")
        val colorPrimary    = Color.parseColor("#606C38")
        val colorAccentWarm = Color.parseColor("#E9B072")
        val colorAccentDeep = Color.parseColor("#BC6C25")

        card.setCardBackgroundColor(colorCream)
        card.strokeColor = Color.parseColor("#15283618")
        viewImageBg.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#08283618"))
        ivFoodImage.setColorFilter(colorPrimary)

        view.findViewById<TextView>(R.id.tvFoodName).setTextColor(colorDark)
        view.findViewById<TextView>(R.id.tvFoodCalories).setTextColor(colorDark)
        view.findViewById<TextView>(R.id.tvFoodFiber)?.setTextColor(colorDark)
        view.findViewById<TextView>(R.id.tvCalLabel).setTextColor(colorDark)
        view.findViewById<TextView>(R.id.tvCalLabel).alpha = 0.5f

        // ── Makro kontejnery — přístup přes přímé ID kontejnerů ──────
        // V novém LinearLayout jsou kontejnery P/S/T přímo children macroContainer.
        val macroContainer = view.findViewById<LinearLayout>(R.id.macroContainer)
        val containerP = macroContainer.getChildAt(0) as LinearLayout
        val containerS = macroContainer.getChildAt(1) as LinearLayout
        val containerT = macroContainer.getChildAt(2) as LinearLayout

        containerP.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#08283618"))
        view.findViewById<TextView>(R.id.tvFoodProtein).setTextColor(colorPrimary)
        (containerP.getChildAt(0) as TextView).setTextColor(colorPrimary)

        containerS.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#08E9B072"))
        view.findViewById<TextView>(R.id.tvFoodCarbs).setTextColor(colorAccentWarm)
        (containerS.getChildAt(0) as TextView).setTextColor(colorAccentWarm)

        containerT.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#08BC6C25"))
        view.findViewById<TextView>(R.id.tvFoodFat).setTextColor(colorAccentDeep)
        (containerT.getChildAt(0) as TextView).setTextColor(colorAccentDeep)
    }

    private fun updateElementsColor(view: View, colorHex: String) {
        val targetColor      = Color.parseColor(colorHex)
        val whiteTransparent = Color.argb(30, 255, 255, 255)

        view.findViewById<TextView>(R.id.tvFoodName).setTextColor(targetColor)
        view.findViewById<TextView>(R.id.tvFoodCalories).setTextColor(targetColor)
        view.findViewById<TextView>(R.id.tvFoodFiber)?.setTextColor(targetColor)
        view.findViewById<TextView>(R.id.tvCalLabel).setTextColor(targetColor)
        view.findViewById<ImageView>(R.id.ivFoodImage).setColorFilter(targetColor)
        view.findViewById<View>(R.id.viewImageBg).backgroundTintList =
            ColorStateList.valueOf(whiteTransparent)

        // ── Makro kontejnery — přes macroContainer children ──────────
        val macroContainer = view.findViewById<LinearLayout>(R.id.macroContainer)
        val containerP = macroContainer.getChildAt(0) as LinearLayout
        val containerS = macroContainer.getChildAt(1) as LinearLayout
        val containerT = macroContainer.getChildAt(2) as LinearLayout

        view.findViewById<TextView>(R.id.tvFoodProtein).setTextColor(targetColor)
        view.findViewById<TextView>(R.id.tvFoodCarbs).setTextColor(targetColor)
        view.findViewById<TextView>(R.id.tvFoodFat).setTextColor(targetColor)

        (containerP.getChildAt(0) as TextView).setTextColor(targetColor)
        (containerS.getChildAt(0) as TextView).setTextColor(targetColor)
        (containerT.getChildAt(0) as TextView).setTextColor(targetColor)

        containerP.backgroundTintList = ColorStateList.valueOf(whiteTransparent)
        containerS.backgroundTintList = ColorStateList.valueOf(whiteTransparent)
        containerT.backgroundTintList = ColorStateList.valueOf(whiteTransparent)
    }

    private fun getIconForSnack(snack: SnackEntity): Int = when {
        snack.p > snack.s -> R.drawable.ic_macro_protein
        snack.s > snack.p -> R.drawable.ic_macro_carbs
        else              -> R.drawable.ic_macro_fat
    }

    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun triggerHaptic(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }
}
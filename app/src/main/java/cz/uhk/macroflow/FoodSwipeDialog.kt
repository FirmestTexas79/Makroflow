package cz.uhk.macroflow

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.*
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

class FoodSwipeDialog : DialogFragment() {

    private var initialX = 0f
    private var snackList = mutableListOf<SnackEntity>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View? {
        return inflater.inflate(R.layout.layout_food_card, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout((resources.displayMetrics.widthPixels * 0.95).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.5f)
        }
    }

    @SuppressLint("ClickableViewAccessibility", "MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val card = view.findViewById<MaterialCardView>(R.id.foodCard)

        // Pivot nastaven pro efekt kyvadla [cite: 2026-03-01]
        card.post {
            card.pivotX = card.width / 2f
            card.pivotY = card.height * 2.0f
        }

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        loadRealSnacks(view)

        card.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = event.rawX
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = event.rawX - initialX
                    v.translationX = diffX
                    v.rotation = diffX / 40f

                    if (abs(diffX) > 120) {
                        triggerHaptic(vibrator, diffX)

                        // Změna barev na potvrzovací (zelená/hnědá) [cite: 2026-02-19]
                        val activeColor = if (diffX > 0) "#606C38" else "#BC6C25"
                        card.setCardBackgroundColor(Color.parseColor(activeColor))

                        // Všechny texty a ikona na krémovou [cite: 2026-03-04]
                        updateElementsColor(view, "#FEFAE0")
                    } else {
                        // Návrat do neutrálního stavu během pohybu
                        resetCardToDefaultState(view)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = event.rawX - initialX
                    if (abs(diffX) > 450) {
                        if (diffX > 0) handleConfirm(view) else handleSkip(view)
                    } else {
                        // Animovaný návrat domů [cite: 2026-03-01]
                        v.animate().translationX(0f).rotation(0f).setDuration(300).withEndAction {
                            resetCardToDefaultState(view)
                        }.start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    // Centrální funkce pro reset barev (Pozadí + Texty + Ikona) [cite: 2026-02-19]
    private fun resetCardToDefaultState(view: View) {
        val card = view.findViewById<MaterialCardView>(R.id.foodCard)

        // 1. Reset pozadí karty na krémovou z manuálu [cite: 2026-02-19]
        card.setCardBackgroundColor(Color.parseColor("#FEFAE0"))

        // 2. Reset textů na jejich specifické brandové barvy
        view.findViewById<TextView>(R.id.tvFoodProtein).setTextColor(Color.parseColor("#606C38"))
        view.findViewById<TextView>(R.id.tvFoodCarbs).setTextColor(Color.parseColor("#DDA15E"))
        view.findViewById<TextView>(R.id.tvFoodFat).setTextColor(Color.parseColor("#BC6C25"))
        view.findViewById<TextView>(R.id.tvFoodCalories).setTextColor(Color.parseColor("#283618"))
        view.findViewById<TextView>(R.id.tvFoodName).setTextColor(Color.parseColor("#283618"))
        view.findViewById<TextView>(R.id.tvCalLabel).setTextColor(Color.parseColor("#BC6C25"))

        // 3. Reset barvy ikony
        view.findViewById<ImageView>(R.id.ivFoodImage).setColorFilter(Color.parseColor("#DDA15E"))
    }

    // Pomocná funkce pro hromadnou změnu barvy prvků (např. na krémovou při swipu)
    private fun updateElementsColor(view: View, colorHex: String) {
        val color = Color.parseColor(colorHex)
        view.findViewById<TextView>(R.id.tvFoodProtein).setTextColor(color)
        view.findViewById<TextView>(R.id.tvFoodCarbs).setTextColor(color)
        view.findViewById<TextView>(R.id.tvFoodFat).setTextColor(color)
        view.findViewById<TextView>(R.id.tvFoodCalories).setTextColor(color)
        view.findViewById<TextView>(R.id.tvFoodName).setTextColor(color)
        view.findViewById<TextView>(R.id.tvCalLabel).setTextColor(color)
        view.findViewById<ImageView>(R.id.ivFoodImage).setColorFilter(color)
    }

    private fun loadRealSnacks(view: View) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            snackList = db.snackDao().getAllSnacks().first().toMutableList()
            updateUI(view)
        }
    }

    private fun triggerHaptic(vibrator: Vibrator, distance: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }

    private fun handleConfirm(view: View) {
        if (snackList.isEmpty()) return
        val snack = snackList.removeAt(0)
        lifecycleScope.launch {
            val calories = ((snack.p * 4) + (snack.s * 4) + (snack.t * 9)).toDouble()
            MacroFlowEngine.logSwipedFood(requireContext(), snack.name, snack.p.toDouble(), snack.s.toDouble(), snack.t.toDouble(), calories)
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
            translationX = 0f
            rotation = 0f
        }
        // Tady se provede reset barev před načtením nového jídla [cite: 2026-03-01]
        if (snackList.isNotEmpty()) updateUI(view) else dismiss()
    }

    private fun getIconForSnack(snack: SnackEntity): Int {
        return when {
            snack.p > snack.s -> R.drawable.ic_macro_protein
            snack.s > snack.p -> R.drawable.ic_macro_carbs
            else -> R.drawable.ic_macro_fat
        }
    }

    private fun updateUI(view: View) {
        val current = snackList.firstOrNull() ?: run { dismiss(); return }

        // KLÍČOVÝ FIX: Nejdříve vše vynulujeme na výchozí barvy z manuálu [cite: 2026-03-01]
        resetCardToDefaultState(view)

        val calories = ((current.p * 4) + (current.s * 4) + (current.t * 9)).toInt()

        view.findViewById<TextView>(R.id.tvFoodName).text = current.name
        view.findViewById<TextView>(R.id.tvFoodCalories).text = "$calories kcal"
        view.findViewById<TextView>(R.id.tvFoodProtein).text = "${current.p.toInt()}g"
        view.findViewById<TextView>(R.id.tvFoodCarbs).text = "${current.s.toInt()}g"
        view.findViewById<TextView>(R.id.tvFoodFat).text = "${current.t.toInt()}g"

        // Nastavení správné ikony [cite: 2026-03-04]
        view.findViewById<ImageView>(R.id.ivFoodImage).setImageResource(getIconForSnack(current))
    }
}
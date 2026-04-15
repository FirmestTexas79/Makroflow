package cz.uhk.macroflow.nutrition

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.ConsumedSnackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ConsumedFoodSheet : BottomSheetDialogFragment() {

    var onFoodDeleted: (() -> Unit)? = null

    private val db by lazy { AppDatabase.getDatabase(requireContext()) }
    private val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_consumed_food, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        sheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.85f).toInt()
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        loadAndDisplay(view)
    }

    private fun loadAndDisplay(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.sheetFoodList)
        val tvTotalKcal = view.findViewById<TextView>(R.id.tvSheetTotalKcal)
        // Opraveno na tvSheetEmpty podle tvého XML
        val tvEmpty = view.findViewById<TextView>(R.id.tvSheetEmpty)

        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                db.consumedSnackDao().getConsumedByDate(today).first()
            }

            container.removeAllViews()

            if (items.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                tvTotalKcal.text = "0 kcal"
                return@launch
            }

            tvEmpty.visibility = View.GONE

            // Výpočet celkových kalorií (Makra v hlavičce tvé XML teď nemá, tak je nepřiřazujeme)
            val totalKcal = items.sumOf { it.calories }
            tvTotalKcal.text = "$totalKcal kcal"

            items.sortedByDescending { it.time }.forEach { consumed ->
                container.addView(buildFoodRow(consumed, container, view))
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildFoodRow(
        consumed: ConsumedSnackEntity,
        container: LinearLayout,
        rootView: View
    ): View {
        val view = layoutInflater.inflate(R.layout.item_consumed_food, container, false)
        val card = view.findViewById<MaterialCardView>(R.id.cardConsumed)

        view.findViewById<TextView>(R.id.tvItemName).text = consumed.name
        view.findViewById<TextView>(R.id.tvItemTime).text = consumed.time
        view.findViewById<TextView>(R.id.tvItemKcal).text = "${consumed.calories} kcal"
        view.findViewById<TextView>(R.id.tvItemSub).text =
            "B: ${consumed.p.toInt()}g  •  S: ${consumed.s.toInt()}g  •  T: ${consumed.t.toInt()}g"

        var startX = 0f
        val deleteThreshold = -350f
        val baseColor = requireContext().getColor(R.color.brand_cream)
        val deleteColorHint = Color.parseColor("#FFF0F0")

        card.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - startX
                    if (deltaX < 0) {
                        v.translationX = deltaX.coerceAtLeast(-500f)
                        val progress = (Math.abs(deltaX) / Math.abs(deleteThreshold)).coerceIn(0f, 1f)
                        card.setCardBackgroundColor(blendColors(baseColor, deleteColorHint, progress))

                        if (progress > 0.8f) {
                            card.strokeColor = Color.parseColor("#EF5350")
                        } else {
                            card.setStrokeColor(android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.brand_dark_alpha10)))
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (v.translationX < deleteThreshold) {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        animateDelete(card, consumed, container, rootView)
                    } else {
                        v.animate().translationX(0f).setDuration(250).start()
                        card.setCardBackgroundColor(baseColor)
                        card.setStrokeColor(android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.brand_dark_alpha10)))
                    }
                    true
                }
                else -> false
            }
        }
        return view
    }

    private fun animateDelete(card: View, consumed: ConsumedSnackEntity, container: LinearLayout, rootView: View) {
        card.animate()
            .translationX(-card.width.toFloat())
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                lifecycleScope.launch(Dispatchers.IO) {
                    db.consumedSnackDao().deleteConsumedByTimestamp(consumed.timestamp)
                    if (cz.uhk.macroflow.data.FirebaseRepository.isLoggedIn) {
                        try {
                            cz.uhk.macroflow.data.FirebaseRepository.deleteConsumedSnack(consumed.timestamp)
                        } catch (e: Exception) { }
                    }
                    withContext(Dispatchers.Main) {
                        container.removeView(card)
                        onFoodDeleted?.invoke()
                        loadAndDisplay(rootView)
                    }
                }
            }.start()
    }

    private fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val a = (Color.alpha(c1) * inv + Color.alpha(c2) * ratio).toInt()
        val r = (Color.red(c1)   * inv + Color.red(c2)   * ratio).toInt()
        val g = (Color.green(c1) * inv + Color.green(c2) * ratio).toInt()
        val b = (Color.blue(c1)  * inv + Color.blue(c2)  * ratio).toInt()
        return Color.argb(a, r, g, b)
    }
}
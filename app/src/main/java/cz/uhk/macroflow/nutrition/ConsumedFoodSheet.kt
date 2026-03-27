package cz.uhk.macroflow.nutrition

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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

    private val db by lazy { AppDatabase.Companion.getDatabase(requireContext()) }
    private val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_consumed_food, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Roztáhni sheet na 85% výšky
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
        val tvTotal   = view.findViewById<TextView>(R.id.tvSheetTotalKcal)
        val tvEmpty   = view.findViewById<TextView>(R.id.tvSheetEmpty)

        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                db.consumedSnackDao().getConsumedByDate(today).first()
            }

            container.removeAllViews()

            if (items.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                tvTotal.text = "0 kcal"
                return@launch
            }

            tvEmpty.visibility = View.GONE

            val totalKcal = items.sumOf { it.calories }
            val totalP    = items.sumOf { it.p.toDouble() }.toInt()
            val totalS    = items.sumOf { it.s.toDouble() }.toInt()
            val totalT    = items.sumOf { it.t.toDouble() }.toInt()
            tvTotal.text  = "$totalKcal kcal  ·  B:${totalP}g  S:${totalS}g  T:${totalT}g"

            items.forEachIndexed { index, consumed ->
                val row = buildFoodRow(consumed, index, container, view)
                container.addView(row)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildFoodRow(
        consumed: ConsumedSnackEntity,
        index: Int,
        container: LinearLayout,
        rootView: View
    ): View {
        // Barva řádku — střídá se + mění se podle makra dominance
        val rowBg = when {
            consumed.p > consumed.s && consumed.p > consumed.t ->
                Color.parseColor("#0A606C38")  // olivová — protein dominuje
            consumed.s > consumed.t ->
                Color.parseColor("#0ADDA15E")  // zlatá — sacharidy
            else ->
                Color.parseColor("#0ABC6C25")  // oranžová — tuky
        }

        val card = MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 4, 0, 4) }
            setCardBackgroundColor(rowBg)
            radius = 16f
            cardElevation = 0f
            strokeWidth = 0
        }

        val inner = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 14, 16, 14)
        }

        // Barevný dot — indikátor makro dominance
        val dot = View(requireContext()).apply {
            val dotColor = when {
                consumed.p > consumed.s && consumed.p > consumed.t -> Color.parseColor("#606C38")
                consumed.s > consumed.t -> Color.parseColor("#DDA15E")
                else -> Color.parseColor("#BC6C25")
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(dotColor)
            }
            layoutParams = LinearLayout.LayoutParams(10, 10).also {
                it.marginEnd = 12
            }
        }

        // Čas + název
        val leftCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTime = TextView(requireContext()).apply {
            text = consumed.time
            textSize = 10f
            setTextColor(Color.parseColor("#80283618"))
            letterSpacing = 0.05f
        }
        val tvName = TextView(requireContext()).apply {
            text = consumed.name
            textSize = 13f
            setTextColor(Color.parseColor("#283618"))
            typeface = Typeface.DEFAULT_BOLD
        }

        leftCol.addView(tvTime)
        leftCol.addView(tvName)

        // Makra
        val tvMacros = TextView(requireContext()).apply {
            text = "${consumed.calories}\nkcal"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#606C38"))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = 8 }
        }

        inner.addView(dot)
        inner.addView(leftCol)
        inner.addView(tvMacros)
        card.addView(inner)

        // ── SWIPE DOLEVA PRO SMAZÁNÍ ─────────────────────────────────
        var startX = 0f
        var isDragging = false

        card.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - startX
                    if (deltaX < -20) {
                        isDragging = true
                        // Posuň kartu doleva + zčervej
                        val progress = (-deltaX / 300f).coerceIn(0f, 1f)
                        v.translationX = deltaX.coerceAtLeast(-280f)
                        val deleteColor = Color.argb(
                            (progress * 200).toInt(),
                            188, 108, 37  // #BC6C25
                        )
                        card.setCardBackgroundColor(
                            blendColors(rowBg, Color.parseColor("#30BC6C25"), progress)
                        )
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val deltaX = event.rawX - startX
                    if (isDragging && deltaX < -180f) {
                        // Potvrzení smazání — animace ven + vibrace
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        animateDeleteOut(card, consumed, container, rootView)
                    } else {
                        // Snap zpět
                        v.animate().translationX(0f).setDuration(200).start()
                        card.setCardBackgroundColor(rowBg)
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }

        // ── PULZUJÍCÍ ANIMACE barvy (živý feeling) ───────────────────
        startPulseAnimation(card, rowBg, index)

        return card
    }

    private fun animateDeleteOut(
        card: MaterialCardView,
        consumed: ConsumedSnackEntity,
        container: LinearLayout,
        rootView: View
    ) {
        card.animate()
            .translationX(-card.width.toFloat())
            .alpha(0f)
            .setDuration(280)
            .withEndAction {
                lifecycleScope.launch(Dispatchers.IO) {
                    // 1. 🏠 Smažeme lokálně z Room databáze
                    db.consumedSnackDao().deleteConsumedByTimestamp(consumed.timestamp)

                    // 2. ☁️ ✅ NOVÉ: Smažeme z cloudu Firebase!
                    if (cz.uhk.macroflow.data.FirebaseRepository.isLoggedIn) {
                        try {
                            cz.uhk.macroflow.data.FirebaseRepository.deleteConsumedSnack(consumed.timestamp)
                        } catch (e: Exception) {
                            android.util.Log.e("FIREBASE_DELETE", "Nepovedlo se smazat z cloudu: ${e.message}")
                        }
                    }

                    withContext(Dispatchers.Main) {
                        container.removeView(card)
                        onFoodDeleted?.invoke()

                        // Přepočítej celkový součet v UI listu
                        loadAndDisplay(rootView)

                        Toast.makeText(
                            requireContext(),
                            "${consumed.name} odebráno",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }.start()
    }

    /**
     * Jemná pulzující animace — každý řádek má mírně jiné načasování
     * aby to působilo jako "dýchání" — živý seznam
     */
    private fun startPulseAnimation(card: MaterialCardView, baseColor: Int, index: Int) {
        val lighterColor = blendColors(baseColor, Color.parseColor("#08FEFAE0"), 0.5f)
        val animator = ValueAnimator.ofObject(ArgbEvaluator(), baseColor, lighterColor).apply {
            duration = 2400
            startDelay = index * 180L  // kaskádové spuštění
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { anim ->
                card.setCardBackgroundColor(anim.animatedValue as Int)
            }
        }
        animator.start()
        // Uložíme animator aby šel zastavit při dismiss
        card.tag = animator
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Zastav všechny animace
        val container = view?.findViewById<LinearLayout>(R.id.sheetFoodList)
        container?.let {
            for (i in 0 until it.childCount) {
                (it.getChildAt(i) as? MaterialCardView)?.let { card ->
                    (card.tag as? ValueAnimator)?.cancel()
                }
            }
        }
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
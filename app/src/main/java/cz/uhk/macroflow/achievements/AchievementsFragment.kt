package cz.uhk.macroflow.achievements

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AchievementsFragment : Fragment() {

    private val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    // ── Makroflow paleta ─────────────────────────────────────────────
    private val CREAM        = 0xFFFEFAE0.toInt()
    private val DARK         = 0xFF283618.toInt()
    private val PRIMARY      = 0xFF606C38.toInt()
    private val ACCENT_WARM  = 0xFFDDA15E.toInt()
    private val ACCENT_DEEP  = 0xFFBC6C25.toInt()
    private val CARD_BG      = 0xFFF5F0DC.toInt()
    private val DIVIDER      = 0x25283618

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_achievements, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            val unlockedIds = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(requireContext())
                    .achievementDao().getAllUnlocked()
                    .associate { it.id to it.unlockedAt }
            }

            renderUI(view, unlockedIds)

            // Kontrola nových achievementů na pozadí
            val newlyUnlocked = withContext(Dispatchers.IO) {
                AchievementEngine.checkAll(requireContext())
            }

            if (newlyUnlocked.isNotEmpty()) {
                val updatedIds = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(requireContext())
                        .achievementDao().getAllUnlocked()
                        .associate { it.id to it.unlockedAt }
                }
                renderUI(view, updatedIds)
                AchievementUnlockQueue.enqueue(requireActivity(), newlyUnlocked)
            }
        }
    }

    private fun renderUI(view: View, unlockedIds: Map<String, Long>) {
        val total    = AchievementRegistry.all.size
        val unlocked = unlockedIds.size

        view.findViewById<TextView>(R.id.tvAchievementCount)?.apply {
            text = "$unlocked / $total"
            setTextColor(0xFFFEFAE0.toInt())
        }

        view.findViewById<ProgressBar>(R.id.pbAchievementTotal)?.apply {
            max = total; progress = unlocked
        }

        val container = view.findViewById<LinearLayout>(R.id.achievementContainer)
        container?.removeAllViews()

        // Vykreslení kategorií
        AchievementCategory.entries.forEach { category ->
            val items = AchievementRegistry.byCategory(category)
            if (items.isEmpty()) return@forEach
            addCategoryHeader(container, category)
            container?.addView(buildGrid(items, unlockedIds))
        }
    }

    private fun addCategoryHeader(container: LinearLayout?, cat: AchievementCategory) {
        val ctx = requireContext()
        val dp  = resources.displayMetrics.density

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins((20 * dp).toInt(), (28 * dp).toInt(), (20 * dp).toInt(), (6 * dp).toInt()) }
        }

        fun line() = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, (1 * dp).toInt(), 1f)
            setBackgroundColor(DIVIDER)
        }

        val label = TextView(ctx).apply {
            text = "${cat.emoji}  ${cat.labelCs.uppercase()}"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(PRIMARY)
            letterSpacing = 0.14f
            setPadding((20 * dp).toInt(), 0, (20 * dp).toInt(), 0)
        }

        row.addView(line()); row.addView(label); row.addView(line())
        container?.addView(row)
    }

    private fun buildGrid(items: List<AchievementDef>, unlockedIds: Map<String, Long>): GridLayout {
        val dp = resources.displayMetrics.density
        return GridLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, (4 * dp).toInt()) }
            columnCount = 2
            items.forEach { def ->
                val card = buildCard(def, unlockedIds[def.id])
                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
                }
                card.layoutParams = params
                addView(card)
            }
        }
    }

    private fun buildCard(def: AchievementDef, unlockedAt: Long?): MaterialCardView {
        val ctx = requireContext()
        val dp = resources.displayMetrics.density
        val unlocked = unlockedAt != null
        val tierStroke = tierColor(def.tier)

        val card = MaterialCardView(ctx).apply {
            radius = (18 * dp)
            cardElevation = if (unlocked) (3 * dp) else (0f)
            strokeWidth = if (unlocked) (2 * dp).toInt() else (1 * dp).toInt()
            strokeColor = if (unlocked) tierStroke else Color.argb(40, 40, 54, 24)
            setCardBackgroundColor(if (unlocked) CREAM else CARD_BG)
        }

        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt())
        }

        // Ikona a zámek
        val frame = FrameLayout(ctx).apply {
            val medalSize = (96 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(medalSize, medalSize).also { it.gravity = Gravity.CENTER_HORIZONTAL }
        }

        val img = ImageView(ctx).apply {
            val resId = ctx.resources.getIdentifier("achievement_${def.id}", "drawable", ctx.packageName)
            if (resId != 0) {
                setImageResource(resId)
                if (!unlocked) {
                    colorFilter = PorterDuffColorFilter(Color.argb(180, 200, 195, 180), PorterDuff.Mode.SRC_ATOP)
                    alpha = 0.40f
                }
            }
        }
        frame.addView(img)
        if (!unlocked) frame.addView(TextView(ctx).apply { text = "🔒"; textSize = 22f; gravity = Gravity.CENTER })
        inner.addView(frame)

        // Název a Tier
        inner.addView(TextView(ctx).apply {
            text = def.titleCs
            textSize = 12.5f
            setTextColor(if (unlocked) DARK else Color.argb(110, 40, 54, 24))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = 5 }
        })

        inner.addView(TextView(ctx).apply {
            text = if (unlocked) "✓  ${sdf.format(Date(unlockedAt!!))}" else def.descCs
            textSize = 10f
            setTextColor(if (unlocked) adjustAlpha(ACCENT_DEEP, 0.85f) else Color.argb(100, 96, 108, 56))
            gravity = Gravity.CENTER
        })

        card.addView(inner)
        return card
    }

    private fun tierColor(tier: AchievementTier) = when (tier) {
        AchievementTier.BRONZE -> ACCENT_DEEP
        AchievementTier.SILVER -> Color.parseColor("#8A9BB0")
        AchievementTier.GOLD -> ACCENT_WARM
        AchievementTier.DIAMOND -> Color.parseColor("#4A8FA8")
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val a = (255 * factor).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}
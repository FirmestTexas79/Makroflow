package cz.uhk.macroflow

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AchievementsFragment : Fragment() {

    private val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_achievements, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            // Zkontroluj nové achievementy
            val newlyUnlocked = withContext(Dispatchers.IO) {
                AchievementEngine.checkAll(requireContext())
            }

            // Načti všechny odemčené
            val unlockedIds = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(requireContext())
                    .achievementDao().getAllUnlocked()
                    .map { it.id to it.unlockedAt }.toMap()
            }

            // Celkový počet
            val totalCount = AchievementRegistry.all.size
            val unlockedCount = unlockedIds.size

            view.findViewById<TextView>(R.id.tvAchievementCount)?.text =
                "$unlockedCount / $totalCount"
            view.findViewById<TextView>(R.id.tvAchievementSub)?.text =
                when {
                    unlockedCount == 0 -> "Zatím žádný achievement — začni hned!"
                    unlockedCount < 5  -> "Dobrý start, pokračuj dál 💪"
                    unlockedCount < 15 -> "Jdeš správnou cestou 🔥"
                    unlockedCount < 30 -> "Výborně, jsi na půl cesty! ⚡"
                    else               -> "Neuvěřitelné, skoro vše odemčeno! 👑"
                }

            // Progress bar celkový
            val progress = view.findViewById<ProgressBar>(R.id.pbAchievementTotal)
            progress?.max = totalCount
            progress?.progress = unlockedCount

            // Vykresli kategorie
            val container = view.findViewById<LinearLayout>(R.id.achievementContainer)
            container?.removeAllViews()

            AchievementCategory.entries.forEach { category ->
                val items = AchievementRegistry.byCategory(category)
                if (items.isEmpty()) return@forEach

                // Oddělovač kategorie
                addCategoryHeader(container, category)

                // Grid 2×N
                val grid = GridLayout(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.setMargins(0, 0, 0, 8) }
                    columnCount = 2
                }

                items.forEach { def ->
                    val unlockedAt = unlockedIds[def.id]
                    val card = createAchievementCard(def, unlockedAt)
                    val params = GridLayout.LayoutParams().apply {
                        width = 0
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(8, 8, 8, 8)
                    }
                    card.layoutParams = params
                    grid.addView(card)
                }

                container?.addView(grid)
            }
        }
    }

    private fun addCategoryHeader(container: LinearLayout?, category: AchievementCategory) {
        val ctx = requireContext()
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(20, 24, 20, 4) }
        }

        // Levá čára
        val lineLeft = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1).also { it.weight = 1f }
            setBackgroundColor(android.graphics.Color.parseColor("#20283618"))
        }
        // Text
        val tv = TextView(ctx).apply {
            text = "${category.emoji} ${category.labelCs.uppercase()}"
            textSize = 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#606C38"))
            letterSpacing = 0.14f
            setPadding(24, 0, 24, 0)
        }
        // Pravá čára
        val lineRight = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1).also { it.weight = 1f }
            setBackgroundColor(android.graphics.Color.parseColor("#20283618"))
        }

        header.addView(lineLeft)
        header.addView(tv)
        header.addView(lineRight)
        container?.addView(header)
    }

    private fun createAchievementCard(def: AchievementDef, unlockedAt: Long?): MaterialCardView {
        val ctx = requireContext()
        val unlocked = unlockedAt != null

        // Barvy dle tier a stavu
        val tierColor = android.graphics.Color.parseColor(def.tier.color)
        // Odemčená — sytá barva tieru; zamčená — jemná kremová
        val bgColor = if (unlocked) when (def.tier) {
            AchievementTier.BRONZE  -> android.graphics.Color.parseColor("#BC6C25")
            AchievementTier.SILVER  -> android.graphics.Color.parseColor("#7B8FA1")
            AchievementTier.GOLD    -> android.graphics.Color.parseColor("#C8860A")
            AchievementTier.DIAMOND -> android.graphics.Color.parseColor("#2E7D9E")
        } else android.graphics.Color.parseColor("#F0FEFAE0")
        val textColor = if (unlocked) android.graphics.Color.parseColor("#FEFAE0")
        else android.graphics.Color.parseColor("#60283618")

        val card = MaterialCardView(ctx).apply {
            setCardBackgroundColor(bgColor)
            radius = 56f   // výrazně kulatá jako medaile
            cardElevation = if (unlocked) 6f else 0f
            strokeWidth = if (unlocked) 0 else 1
            strokeColor = android.graphics.Color.parseColor("#20283618")
        }

        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(16, 20, 16, 16)
        }

        // Kulatý placeholder — medaile
        val medalView = View(ctx).apply {
            val size = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) * 2
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                if (unlocked) {
                    // Barevný kruh dle tieru
                    setColor(adjustAlpha(tierColor, 0.25f))
                    setStroke(4, tierColor)
                } else {
                    setColor(android.graphics.Color.parseColor("#15283618"))
                    setStroke(2, android.graphics.Color.parseColor("#20283618"))
                }
            }
        }
        // Emoji uvnitř plachceholder kruhu
        val emojiTv = TextView(ctx).apply {
            text = if (unlocked) def.emoji else "🔒"
            textSize = 28f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = -72 }  // překryv s kruhem
        }

        // Název
        val titleTv = TextView(ctx).apply {
            text = def.titleCs
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 8 }
        }

        // Tier badge
        val tierTv = TextView(ctx).apply {
            text = def.tier.labelCs.uppercase()
            textSize = 9f
            setTextColor(if (unlocked) adjustAlphaColor(textColor, 0.7f) else android.graphics.Color.parseColor("#40283618"))
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.08f
        }

        // Datum odemčení nebo popis
        val subTv = TextView(ctx).apply {
            text = if (unlocked) "✓ ${sdf.format(Date(unlockedAt!!))}"
            else def.descCs
            textSize = 9f
            setTextColor(if (unlocked) adjustAlphaColor(textColor, 0.6f)
            else android.graphics.Color.parseColor("#50283618"))
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 2 }
        }

        inner.addView(medalView)
        inner.addView(emojiTv)
        inner.addView(titleTv)
        inner.addView(tierTv)
        inner.addView(subTv)
        card.addView(inner)

        return card
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val a = (android.graphics.Color.alpha(color) * factor).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }
    private fun adjustAlphaColor(color: Int, factor: Float): Int {
        val a = (255 * factor).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}
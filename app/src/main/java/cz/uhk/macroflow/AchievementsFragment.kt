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

    // ── Makroflow paleta ─────────────────────────────────────────────
    private val CREAM        = 0xFFFEFAE0.toInt()
    private val DARK         = 0xFF283618.toInt()
    private val PRIMARY      = 0xFF606C38.toInt()
    private val ACCENT_WARM  = 0xFFDDA15E.toInt()
    private val ACCENT_DEEP  = 0xFFBC6C25.toInt()
    private val CARD_BG      = 0xFFF5F0DC.toInt()
    private val DIVIDER      = 0x25283618
    private val GHOST_PURPLE = android.graphics.Color.parseColor("#6A4C93")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_achievements, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            // 1. Nejdřív načti co je odemčeno
            val unlockedIds = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(requireContext())
                    .achievementDao().getAllUnlocked()
                    .associate { it.id to it.unlockedAt }
            }

            val coinBalance = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(requireContext()).coinDao().getBalance()?.balance ?: 0
            }

            renderUI(view, unlockedIds, coinBalance)

            // 2. checkAll na pozadí
            val newlyUnlocked = withContext(Dispatchers.IO) {
                AchievementEngine.checkAll(requireContext())
            }

            if (newlyUnlocked.isNotEmpty()) {
                val updatedIds = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(requireContext())
                        .achievementDao().getAllUnlocked()
                        .associate { it.id to it.unlockedAt }
                }
                val updatedBalance = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(requireContext()).coinDao().getBalance()?.balance ?: 0
                }
                renderUI(view, updatedIds, updatedBalance)

                AchievementUnlockQueue.enqueue(requireActivity(), newlyUnlocked)
            }
        }
    }

    // ── Render celého UI ─────────────────────────────────────────────
    private fun renderUI(view: View, unlockedIds: Map<String, Long>, coinBalance: Int) {
        val total    = AchievementRegistry.all.size
        val unlocked = unlockedIds.size

        view.findViewById<TextView>(R.id.tvAchievementCount)?.apply {
            text = "$unlocked / $total"
            setTextColor(0xFFFEFAE0.toInt())
        }
        view.findViewById<TextView>(R.id.tvAchievementSub)?.apply {
            text = when {
                unlocked == 0  -> "Zatím žádný achievement — začni hned!"
                unlocked < 5   -> "Dobrý start, pokračuj dál 💪"
                unlocked < 15  -> "Jdeš správnou cestou 🔥"
                unlocked < 30  -> "Výborně, jsi na půl cesty! ⚡"
                else           -> "Neuvěřitelné, skoro vše odemčeno! 👑"
            }
            setTextColor(android.graphics.Color.argb(160, 40, 54, 24))
        }
        view.findViewById<ProgressBar>(R.id.pbAchievementTotal)?.apply {
            max = total; progress = unlocked
        }

        val container = view.findViewById<LinearLayout>(R.id.achievementContainer)
        container?.removeAllViews()

        // 1. Nejprve vykreslíme všechny kategorie achievementů
        AchievementCategory.entries.forEach { category ->
            val items = AchievementRegistry.byCategory(category)
            if (items.isEmpty()) return@forEach
            addCategoryHeader(container, category)
            container?.addView(buildGrid(items, unlockedIds))
        }

        // 2. 🤫 A až ÚPLNĚ DOLE pod vším se zobrazí secret Shop
        container?.addView(buildCoinShopCard(coinBalance))
    }

    // ── Coin balance + Shop tlačítko ─────────────────────────────────
    private fun buildCoinShopCard(coinBalance: Int): MaterialCardView {
        val ctx = requireContext()
        val dp  = resources.displayMetrics.density

        val card = MaterialCardView(ctx).apply {
            radius        = 16f * dp
            cardElevation = 4f * dp
            strokeWidth   = (2 * dp).toInt()
            strokeColor   = ACCENT_WARM
            setCardBackgroundColor(DARK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.setMargins(
                    (12 * dp).toInt(), (4 * dp).toInt(),
                    (12 * dp).toInt(), (20 * dp).toInt()
                )
            }
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
        }

        // Levá část — balance
        val leftCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvLabel = TextView(ctx).apply {
            text      = "💰  Tvoje coiny"
            textSize  = 11f
            setTextColor(android.graphics.Color.argb(180, 254, 250, 224))
        }

        val tvBalance = TextView(ctx).apply {
            text     = "$coinBalance 🪙"
            textSize = 24f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(ACCENT_WARM)
        }

        val tvInfo = TextView(ctx).apply {
            text      = "Za achievementy: 🥉1  🥈3  🥇10  💎50"
            textSize  = 9.5f
            setTextColor(android.graphics.Color.argb(130, 254, 250, 224))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (2 * dp).toInt() }
        }

        leftCol.addView(tvLabel)
        leftCol.addView(tvBalance)
        leftCol.addView(tvInfo)

        // Pravá část — shop tlačítko
        val btnShop = TextView(ctx).apply {
            text      = "🏪\nSHOP"
            textSize  = 12f
            typeface  = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(DARK)
            gravity   = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(ACCENT_WARM)
                cornerRadius = 12f * dp
            }
            setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
            setOnClickListener {
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.nav_host_fragment, cz.uhk.macroflow.pokemon.PokemonShopFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }

        row.addView(leftCol)
        row.addView(btnShop)
        card.addView(row)
        return card
    }

    // ── Nadpis kategorie ─────────────────────────────────────────────
    private fun addCategoryHeader(container: LinearLayout?, cat: AchievementCategory) {
        val ctx = requireContext()
        val dp  = resources.displayMetrics.density

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(
                (20 * dp).toInt(), (28 * dp).toInt(),
                (20 * dp).toInt(), (6  * dp).toInt()
            )}
        }

        fun line() = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, (1 * dp).toInt(), 1f)
            setBackgroundColor(DIVIDER)
        }

        val label = TextView(ctx).apply {
            text        = "${cat.emoji}  ${cat.labelCs.uppercase()}"
            textSize    = 10f
            typeface    = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(PRIMARY)
            letterSpacing = 0.14f
            setPadding((20 * dp).toInt(), 0, (20 * dp).toInt(), 0)
        }

        row.addView(line())
        row.addView(label)
        row.addView(line())
        container?.addView(row)
    }

    // ── Grid 2 sloupce ───────────────────────────────────────────────
    private fun buildGrid(
        items: List<AchievementDef>,
        unlockedIds: Map<String, Long>
    ): android.widget.GridLayout {
        val dp = resources.displayMetrics.density
        return android.widget.GridLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, (4 * dp).toInt()) }
            columnCount = 2
            items.forEach { def ->
                val card   = buildCard(def, unlockedIds[def.id])
                val params = android.widget.GridLayout.LayoutParams().apply {
                    width        = 0
                    columnSpec   = android.widget.GridLayout.spec(
                        android.widget.GridLayout.UNDEFINED, 1f
                    )
                    setMargins(
                        (10 * dp).toInt(), (8 * dp).toInt(),
                        (10 * dp).toInt(), (8 * dp).toInt()
                    )
                }
                card.layoutParams = params
                addView(card)
            }
        }
    }

    // ── Karta achievementu ───────────────────────────────────────────
    private fun buildCard(def: AchievementDef, unlockedAt: Long?): MaterialCardView {
        val ctx      = requireContext()
        val dp       = resources.displayMetrics.density
        val unlocked = unlockedAt != null

        val tierStroke = tierColor(def.tier)

        val card = MaterialCardView(ctx).apply {
            radius          = (18 * dp)
            cardElevation   = if (unlocked) (3 * dp) else (0f)
            strokeWidth     = if (unlocked) (2 * dp).toInt() else (1 * dp).toInt()
            strokeColor     = if (unlocked) tierStroke
            else android.graphics.Color.argb(40, 40, 54, 24)
            setCardBackgroundColor(if (unlocked) CREAM else CARD_BG)
        }

        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = android.view.Gravity.CENTER
            setPadding(
                (10 * dp).toInt(), (14 * dp).toInt(),
                (10 * dp).toInt(), (14 * dp).toInt()
            )
        }

        // ── Medaile obrázek ──────────────────────────────────────────
        val medalSize = (96 * dp).toInt()
        val frame = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(medalSize, medalSize).also {
                it.gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
        }

        val img = android.widget.ImageView(ctx).apply {
            val resId = ctx.resources.getIdentifier(
                "achievement_${def.id}", "drawable", ctx.packageName
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            if (resId != 0) {
                setImageResource(resId)
                if (!unlocked) {
                    colorFilter = android.graphics.PorterDuffColorFilter(
                        android.graphics.Color.argb(180, 200, 195, 180),
                        android.graphics.PorterDuff.Mode.SRC_ATOP
                    )
                    alpha = 0.40f
                }
            }
        }
        frame.addView(img)

        if (!unlocked) {
            frame.addView(TextView(ctx).apply {
                text      = "🔒"
                textSize  = 22f
                gravity   = android.view.Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            })
        }
        inner.addView(frame)

        // ── Coin odměna pill ─────────────────────────────────────────
        if (!unlocked) {
            val coins = AchievementEngine.coinsForTier(def.tier)
            inner.addView(TextView(ctx).apply {
                text      = "+$coins 🪙"
                textSize  = 8f
                setTextColor(ACCENT_DEEP)
                gravity   = android.view.Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 20f * dp
                    setColor(android.graphics.Color.argb(30, 188, 108, 37))
                }
                setPadding((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also {
                    it.topMargin = (4 * dp).toInt()
                    it.gravity   = android.view.Gravity.CENTER_HORIZONTAL
                }
            })
        }

        // ── Tier pill ────────────────────────────────────────────────
        inner.addView(TextView(ctx).apply {
            text      = def.tier.labelCs.uppercase()
            textSize  = 8.5f
            typeface  = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(if (unlocked) tierStroke else android.graphics.Color.argb(100, 40, 54, 24))
            gravity   = android.view.Gravity.CENTER
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.topMargin   = (6 * dp).toInt()
                it.gravity     = android.view.Gravity.CENTER_HORIZONTAL
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 20 * dp
                setStroke(
                    (1 * dp).toInt(),
                    if (unlocked) tierStroke
                    else android.graphics.Color.argb(60, 40, 54, 24)
                )
                setColor(
                    if (unlocked) adjustAlpha(tierStroke, 0.10f)
                    else android.graphics.Color.argb(20, 40, 54, 24)
                )
            }
            setPadding(
                (9 * dp).toInt(), (2 * dp).toInt(),
                (9 * dp).toInt(), (2 * dp).toInt()
            )
        })

        // ── Název ────────────────────────────────────────────────────
        inner.addView(TextView(ctx).apply {
            text      = def.titleCs
            textSize  = 12.5f
            typeface  = android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL
            )
            setTextColor(
                if (unlocked) DARK
                else android.graphics.Color.argb(110, 40, 54, 24)
            )
            gravity   = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (5 * dp).toInt() }
        })

        // ── Datum nebo popis ─────────────────────────────────────────
        inner.addView(TextView(ctx).apply {
            text = if (unlocked) "✓  ${sdf.format(Date(unlockedAt!!))}"
            else def.descCs
            textSize = 10f
            setTextColor(
                if (unlocked) adjustAlpha(ACCENT_DEEP, 0.85f)
                else android.graphics.Color.argb(100, 96, 108, 56)
            )
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (3 * dp).toInt() }
        })

        card.addView(inner)
        return card
    }

    // ── Helpers ──────────────────────────────────────────────────────
    private fun tierColor(tier: AchievementTier) = when (tier) {
        AchievementTier.BRONZE  -> ACCENT_DEEP
        AchievementTier.SILVER  -> android.graphics.Color.parseColor("#8A9BB0")
        AchievementTier.GOLD    -> ACCENT_WARM
        AchievementTier.DIAMOND -> android.graphics.Color.parseColor("#4A8FA8")
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val a = (255 * factor).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}
package cz.uhk.macroflow.pokemon.daily

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import cz.uhk.macroflow.pokemon.balls.Makroball
import cz.uhk.macroflow.pokemon.skills.SkillArt
import cz.uhk.macroflow.pokemon.skills.TreeArt
import cz.uhk.macroflow.pokemon.skills.ui.BevelDrawable
import cz.uhk.macroflow.pokemon.skills.ui.WoodPanelDrawable
import cz.uhk.macroflow.pokemon.skills.ui.WoodUi

/**
 * Stránka Denní úkoly v deníku (docs/adr/0058): dřevěná cedule s odpočtem a třemi kolečky
 * postupu, každý úkol na pergamenu v rámu – barevná dlaždice s pixelovou ikonou podle
 * druhu, odměna v mincovním štítku, pixelový pruh postupu a dřevěné tlačítko Vyzvednout.
 * Vyzvednutý úkol dostane zelené razítko SPLNĚNO.
 */
object DailyPage {

    private val WHITE = Color.parseColor("#FEFAE0")
    private val GOLD = Color.parseColor("#FFD54F")

    /** Barvy dlaždice a pruhu podle skupiny: základ, světlo, stín, výplň pruhu. */
    private fun colors(g: DailyQuests.Group): List<Int> = when (g) {
        DailyQuests.Group.GAME -> listOf("#8E3B2E", "#C25B48", "#5A2219", "#D9705A")
        DailyQuests.Group.FOOD -> listOf("#4E6B2A", "#7A9A44", "#2F4518", "#8FB04F")
        DailyQuests.Group.ACTIVITY -> listOf("#2E5A8A", "#4F82B8", "#1B3A5C", "#5C9AD6")
    }.map { Color.parseColor(it) }

    private fun groupLabel(g: DailyQuests.Group) = when (g) {
        DailyQuests.Group.GAME -> "Makrosvět"
        DailyQuests.Group.FOOD -> "Jídlo"
        DailyQuests.Group.ACTIVITY -> "Pohyb"
    }

    /** Pixelová ikona 12 × 12 podle druhu úkolu. */
    fun icon(kind: DailyQuests.Kind): IntArray = when (kind) {
        DailyQuests.Kind.WIN_TYPE, DailyQuests.Kind.WIN_ANY -> TreeArt.SWORD
        DailyQuests.Kind.CATCH -> Makroball.entries.first().pixels
        DailyQuests.Kind.WIN_IN_BIOME -> TreeArt.TREE
        DailyQuests.Kind.WATER -> TreeArt.DROP
        DailyQuests.Kind.PROTEIN -> TreeArt.MEAT
        DailyQuests.Kind.FIBER -> TreeArt.LEAF
        DailyQuests.Kind.MEALS -> TreeArt.BOWL
        DailyQuests.Kind.STEPS -> TreeArt.BOOT
        DailyQuests.Kind.WORKOUT_SETS -> TreeArt.DUMBBELL
        DailyQuests.Kind.CHECK_IN -> TreeArt.SUN
    }

    fun render(
        box: LinearLayout,
        quests: List<DailyQuests.Quest>,
        facts: DailyQuests.Facts,
        claimed: List<Boolean>,
        onClaim: (DailyQuests.Quest) -> Unit
    ) {
        box.removeAllViews()
        val ui = WoodUi(box.context)
        val u = 2f * ui.dp
        val full = { LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) }

        // ── cedule ────────────────────────────────────────────────────────────
        val plaque = ui.row().apply {
            background = WoodPanelDrawable(2.5f * ui.dp, parchment = false)
            setPadding(ui.px(16f), ui.px(12f), ui.px(16f), ui.px(14f))
        }
        plaque.addView(ui.icon(TreeArt.SUN, TreeArt.SIZE, TreeArt.SIZE, 30f))
        plaque.addView(outlined(ui.text("Denní úkoly", 26f, WHITE)).apply { setPadding(ui.px(8f), 0, 0, ui.px(2f)) },
            ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        // tři kolečka: zlaté = vyzvednuto, zelené = splněno, tmavé = zbývá
        quests.forEachIndexed { i, q ->
            val col = when {
                claimed.getOrElse(i) { false } -> GOLD
                DailyQuests.isDone(q, facts) -> Color.parseColor("#9CC45A")
                else -> Color.parseColor("#3B281F")
            }
            plaque.addView(View(box.context).apply {
                background = BevelDrawable(1.5f * ui.dp, col, Shade.lighten(col), Shade.darken(col), Color.parseColor("#1E140C"))
            }, LinearLayout.LayoutParams(ui.px(16f), ui.px(16f)).apply { marginStart = ui.px(4f) })
        }
        box.addView(plaque, full())

        val now = java.time.LocalDateTime.now()
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
        val left = java.time.Duration.between(now, midnight)
        val dayPassed = 1f - left.seconds / 86400f
        val timer = ui.row().apply {
            gravity = Gravity.CENTER
            background = BevelDrawable(1.5f * ui.dp, Color.parseColor("#EAD6AE"), Color.parseColor("#F8EBCF"), Color.parseColor("#CDB083"), Color.parseColor("#9C7A4E"))
            setPadding(ui.px(10f), ui.px(4f), ui.px(12f), ui.px(5f))
        }
        timer.addView(ui.icon(SkillArt.hourglass(dayPassed), SkillArt.GLASS_W, SkillArt.GLASS_H, 16f))
        timer.addView(ui.text("Nové úkoly za ${left.toHours()} h ${left.toMinutes() % 60} min", 16f, ui.inkSoft).apply {
            setPadding(ui.px(6f), 0, 0, 0)
        })
        box.addView(timer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = ui.px(8f); bottomMargin = ui.px(12f)
        })

        // ── úkoly ─────────────────────────────────────────────────────────────
        quests.forEachIndexed { i, q ->
            val done = DailyQuests.isDone(q, facts)
            val isClaimed = claimed.getOrElse(i) { false }
            val (base, light, dark, bar) = colors(q.kind.group)

            val holder = FrameLayout(box.context).apply { clipChildren = false; clipToPadding = false }
            val card = ui.column().apply {
                background = WoodPanelDrawable(2f * ui.dp)
                setPadding(ui.px(14f), ui.px(13f), ui.px(14f), ui.px(14f))
            }
            holder.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            val top = ui.row().apply { gravity = Gravity.TOP }
            top.addView(FrameLayout(box.context).apply {
                background = BevelDrawable(u, base, light, dark)
                addView(ui.icon(icon(q.kind), TreeArt.SIZE, TreeArt.SIZE, 32f), FrameLayout.LayoutParams(ui.px(34f), ui.px(34f), Gravity.CENTER))
            }, LinearLayout.LayoutParams(ui.px(50f), ui.px(50f)))
            val texts = ui.column().apply { setPadding(ui.px(10f), 0, ui.px(6f), 0) }
            texts.addView(ui.text(groupLabel(q.kind.group).uppercase(), 12f, base))
            texts.addView(ui.text(q.title, 21f))
            texts.addView(ui.text(q.description, 15f, ui.inkSoft).apply { setPadding(0, ui.px(1f), 0, 0) })
            top.addView(texts, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            // odměna
            val reward = ui.row().apply {
                background = BevelDrawable(1.5f * ui.dp, Color.parseColor("#5A3E32"), Color.parseColor("#7A5646"), Color.parseColor("#3B281F"), Color.parseColor("#1E140C"))
                setPadding(ui.px(6f), ui.px(3f), ui.px(8f), ui.px(4f))
            }
            reward.addView(ui.icon(TreeArt.COIN, TreeArt.SIZE, TreeArt.SIZE, 16f))
            reward.addView(outlined(ui.text("+${q.reward}", 17f, GOLD)).apply { setPadding(ui.px(4f), 0, 0, ui.px(1f)) })
            top.addView(reward)
            card.addView(top)

            // pruh postupu
            val frac = DailyQuests.fraction(q, facts)
            val track = FrameLayout(box.context).apply {
                background = BevelDrawable(1.5f * ui.dp, Color.parseColor("#2A1C11"), Color.parseColor("#1A110A"), Color.parseColor("#4F3016"))
            }
            val fill = View(box.context).apply { setBackgroundColor(if (done) Color.parseColor("#7F9148") else bar) }
            val shine = View(box.context).apply { setBackgroundColor(Color.argb(90, 255, 255, 255)) }
            track.addView(fill, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(ui.px(3f), ui.px(3f), ui.px(3f), ui.px(3f)) })
            track.addView(shine, FrameLayout.LayoutParams(0, ui.px(3f)).apply { setMargins(ui.px(3f), ui.px(3f), ui.px(3f), 0) })
            track.addView(outlined(ui.text(DailyQuests.progressText(q, facts), 15f, WHITE, Gravity.CENTER)),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            card.addView(track, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.px(26f)).apply { topMargin = ui.px(10f) })
            track.post {
                val w = ((track.width - ui.px(6f)) * frac).toInt().let { if (frac > 0f) it.coerceAtLeast(ui.px(6f)) else 0 }
                listOf(fill, shine).forEach { v ->
                    v.layoutParams = (v.layoutParams as FrameLayout.LayoutParams).apply { width = w }
                    v.pivotX = 0f; v.scaleX = 0f
                    v.animate().scaleX(1f).setDuration(520).setStartDelay(i * 130L).start()
                }
            }

            // vyzvednutí
            if (done && !isClaimed) {
                val btn = ui.row().apply {
                    gravity = Gravity.CENTER
                    background = WoodPanelDrawable(1.8f * ui.dp, parchment = false)
                    setPadding(0, ui.px(8f), 0, ui.px(10f))
                    isClickable = true
                    contentDescription = "Vyzvednout ${q.reward}"
                    setOnClickListener {
                        isEnabled = false
                        animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).withEndAction {
                            animate().scaleX(1f).scaleY(1f).setDuration(90).withEndAction { onClaim(q) }.start()
                        }.start()
                    }
                }
                btn.addView(ui.icon(TreeArt.COIN, TreeArt.SIZE, TreeArt.SIZE, 20f))
                btn.addView(outlined(ui.text("Vyzvednout +${q.reward}", 20f, WHITE)).apply { setPadding(ui.px(8f), 0, 0, ui.px(2f)) })
                card.addView(btn, full().apply { topMargin = ui.px(10f) })
                // tlačítko jemně dýchá, dokud je vidět
                ObjectAnimator.ofPropertyValuesHolder(btn,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.03f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f)).apply {
                    duration = 600; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
                }.also { anim ->
                    btn.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) { anim.start() }
                        override fun onViewDetachedFromWindow(v: View) { anim.cancel() }
                    })
                }
            }

            // razítko
            if (isClaimed) {
                card.alpha = 0.78f
                val stamp = outlinedStamp(ui, "SPLNĚNO")
                holder.addView(stamp, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.BOTTOM)
                    .apply { marginEnd = ui.px(18f); bottomMargin = ui.px(10f) })
            }
            box.addView(holder, full().apply { bottomMargin = ui.px(10f) })
            holder.alpha = 0f; holder.translationY = ui.px(14f).toFloat()
            holder.animate().alpha(1f).translationY(0f).setStartDelay(60L + i * 90L).setDuration(260).start()
        }

        box.addView(ui.text("Úkoly se mění o půlnoci. Mince z nich utratíš v obchodě ve městě.", 14f, ui.inkSoft, Gravity.CENTER).apply {
            setPadding(ui.px(8f), ui.px(4f), ui.px(8f), 0)
        }, full())
    }

    /** Zelené razítko: dvojitý rámeček, natočené, trochu průsvitné. */
    private fun outlinedStamp(ui: WoodUi, label: String): TextView = ui.text(label, 22f, Color.parseColor("#3E6B2A"), Gravity.CENTER).apply {
        background = android.graphics.drawable.LayerDrawable(arrayOf(
            android.graphics.drawable.GradientDrawable().apply { setStroke(ui.px(3f), Color.parseColor("#3E6B2A")); setColor(Color.argb(40, 156, 196, 90)) },
            android.graphics.drawable.GradientDrawable().apply { setStroke(ui.px(1.5f), Color.parseColor("#3E6B2A")) }
        )).apply { setLayerInset(1, ui.px(5f), ui.px(5f), ui.px(5f), ui.px(5f)) }
        setPadding(ui.px(14f), ui.px(8f), ui.px(14f), ui.px(10f))
        rotation = -9f
        alpha = 0.9f
    }

    private fun outlined(t: TextView): TextView = t.apply { setShadowLayer(3f, 0f, 1.5f, Color.parseColor("#101828")) }
}

/** Zesvětlení a ztmavení barvy pro pixelové hrany. */
internal object Shade {
    fun lighten(c: Int): Int = mix(c, Color.WHITE, 0.35f)
    fun darken(c: Int): Int = mix(c, Color.BLACK, 0.35f)
    private fun mix(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())
}

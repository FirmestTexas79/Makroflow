package cz.uhk.macroflow.pokemon.skills.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * Herní oznámení (docs/adr/0080) místo obyčejného toastu: dřevěná karta nahoře s ikonou v dlaždici,
 * nadpisem pixelovým písmem a u XP s ukazatelem postupu levelu. Víc oznámení jde po sobě.
 */
object GameToast {

    enum class Kind(val icon: String) { XP("⭐"), LEVEL("🎊"), COINS("🪙"), ITEM("🎒"), INFO("💬") }

    private data class Item(val kind: Kind, val title: String, val sub: String?, val progress: Float?)

    private val queue = ArrayDeque<Item>()
    private var showing = false

    fun show(ctx: Context, kind: Kind, title: String, sub: String? = null, progress: Float? = null) {
        val act = ctx.activity() ?: return
        queue.addLast(Item(kind, title, sub, progress))
        if (!showing) next(act)
    }

    private tailrec fun Context.activity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }

    private fun next(act: Activity) {
        val item = queue.removeFirstOrNull() ?: run { showing = false; return }
        val root = act.findViewById<FrameLayout>(android.R.id.content) ?: run { showing = false; return }
        showing = true
        val ui = WoodUi(act)
        val card = ui.row().apply {
            background = WoodPanelDrawable(2f * ui.dp, parchment = false)
            setPadding(ui.px(12f), ui.px(10f), ui.px(16f), ui.px(10f))
            elevation = 400f
        }
        card.addView(ui.text(item.kind.icon, 24f, ui.cream, Gravity.CENTER).apply {
            background = BevelDrawable.slot(1.5f * ui.dp)
        }, LinearLayout.LayoutParams(ui.px(46f), ui.px(46f)))
        val col = ui.column().apply { setPadding(ui.px(12f), 0, 0, 0) }
        col.addView(ui.text(item.title, 22f, ui.cream))
        item.sub?.let { col.addView(ui.text(it, 15f, 0xFFE8CFA0.toInt())) }
        item.progress?.let { p ->
            col.addView(LinearLayout(act).apply {
                background = BevelDrawable.slot(1f * ui.dp)
                setPadding(ui.px(2f), ui.px(2f), ui.px(2f), ui.px(2f))
                addView(View(act).apply { setBackgroundColor(0xFFF2C94A.toInt()) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, p.coerceIn(0.02f, 1f)))
                addView(View(act), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (1f - p).coerceIn(0f, 0.98f)))
            }, LinearLayout.LayoutParams(ui.px(160f), ui.px(10f)).apply { topMargin = ui.px(4f) })
        }
        card.addView(col)
        root.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = ui.px(52f) })
        card.alpha = 0f; card.translationY = -ui.px(40f).toFloat()
        card.animate().alpha(1f).translationY(0f).setDuration(220).withEndAction {
            card.animate().alpha(0f).translationY(-ui.px(30f).toFloat()).setStartDelay(1900).setDuration(220).withEndAction {
                root.removeView(card)
                next(act)
            }.start()
        }.start()
    }
}

package cz.uhk.macroflow.training.log

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import cz.uhk.macroflow.R
import cz.uhk.macroflow.common.MainActivity

/**
 * Bublina po zápisu série (docs/adr/0065, bod 5): „+2 ⚡ · Skok do Makrosvěta“. Na pár sekund,
 * neblokuje další zápis. Je to PopupWindow nad oknem [anchor], aby byla vidět i nad bottom sheetem.
 */
object MakrosvetBubble {
    private const val SET_CAP = 15
    private var current: PopupWindow? = null

    /** [setsToday] = počet dnešních sérií včetně právě zapsané (+2 ⚡ jen do denního stropu). */
    fun show(anchor: View, setsToday: Int) {
        val ctx = anchor.context
        val main = generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<MainActivity>().firstOrNull() ?: return
        val dp = ctx.resources.displayMetrics.density
        fun c(id: Int) = ContextCompat.getColor(ctx, id)
        val black = Typeface.create("sans-serif-black", Typeface.NORMAL)

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((18 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt())
            background = GradientDrawable().apply { cornerRadius = 100 * dp; setColor(c(R.color.brand_dark)) }
            elevation = 10 * dp
            if (setsToday <= SET_CAP) addView(TextView(ctx).apply {
                text = "+2 ⚡"; typeface = black; textSize = 15f; setTextColor(c(R.color.brand_accent_warm))
            })
            addView(TextView(ctx).apply {
                text = "Skok do Makrosvěta"
                typeface = black; textSize = 13f; setTextColor(c(R.color.brand_dark))
                setPadding((16 * dp).toInt(), (9 * dp).toInt(), (16 * dp).toInt(), (9 * dp).toInt())
                background = GradientDrawable().apply { cornerRadius = 100 * dp; setColor(c(R.color.brand_accent_warm)) }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { marginStart = (12 * dp).toInt() }
            })
        }

        current?.dismiss()
        val popup = PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            isOutsideTouchable = false
            elevation = 10 * dp
        }
        content.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            popup.dismiss()
            main.openMakromonBattle()
        }
        current = popup
        anchor.post {
            if (!anchor.isAttachedToWindow) return@post
            popup.showAtLocation(anchor.rootView, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, (56 * dp).toInt())
            content.translationY = -40 * dp; content.alpha = 0f
            content.animate().translationY(0f).alpha(1f).setDuration(260).start()
            content.postDelayed({
                content.animate().translationY(-40 * dp).alpha(0f).setDuration(220).withEndAction {
                    if (popup.isShowing) popup.dismiss()
                }.start()
            }, 5000)
        }
    }
}

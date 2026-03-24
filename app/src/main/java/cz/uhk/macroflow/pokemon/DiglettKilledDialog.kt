package cz.uhk.macroflow.pokemon

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import cz.uhk.macroflow.common.MainActivity

class DiglettKilledDialog : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density

        // Root — tmavé poloprůhledné pozadí
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(0xCC101010.toInt())
            isClickable = true
        }

        // Bílá karta
        val cardBg = GradientDrawable().apply {
            setColor(0xFFF8F4E8.toInt())
            cornerRadius = 20f * dp
        }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER_HORIZONTAL
            background  = cardBg
            setPadding(
                (24 * dp).toInt(), (32 * dp).toInt(),
                (24 * dp).toInt(), (24 * dp).toInt()
            )
            elevation = 16f * dp
        }

        val cardParams = FrameLayout.LayoutParams(
            (280 * dp).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }

        // R.I.P.
        val tvRip = TextView(ctx).apply {
            text      = "R.I.P."
            textSize  = 36f
            setTextColor(0xFF283618.toInt())
            typeface  = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity   = Gravity.CENTER
        }

        // Diglett překlopený
        val ivDiglett = ImageView(ctx).apply {
            val resId = ctx.resources.getIdentifier("diglett_bottom", "drawable", ctx.packageName)
            if (resId != 0) setImageResource(resId)
            scaleType = ImageView.ScaleType.FIT_CENTER
            rotation  = 180f
            alpha     = 0.65f
            layoutParams = LinearLayout.LayoutParams(
                (120 * dp).toInt(), (120 * dp).toInt()
            ).apply {
                gravity     = Gravity.CENTER_HORIZONTAL
                topMargin   = (8 * dp).toInt()
            }
        }

        // X_X oči
        val tvEyes = TextView(ctx).apply {
            text      = "X _ X"
            textSize  = 18f
            setTextColor(0xFF283618.toInt())
            typeface  = Typeface.MONOSPACE
            gravity   = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * dp).toInt() }
        }

        // Zpráva
        val tvMsg = TextView(ctx).apply {
            text      = "Zabil jsi Digletta!\n\nMůžeš ho znovu\nchytit v souboji."
            textSize  = 14f
            setTextColor(0xFF606C38.toInt())
            gravity   = Gravity.CENTER
            setLineSpacing(0f, 1.3f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (16 * dp).toInt() }
        }

        // Tlačítko Pochovat
        val btnBg = GradientDrawable().apply {
            setColor(0xFF283618.toInt())
            cornerRadius = 12f * dp
        }
        val btnClose = TextView(ctx).apply {
            text      = "Pochovat"
            textSize  = 16f
            setTextColor(0xFFF8F4E8.toInt())
            gravity   = Gravity.CENTER
            background = btnBg
            setPadding(
                (24 * dp).toInt(), (12 * dp).toInt(),
                (24 * dp).toInt(), (12 * dp).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (24 * dp).toInt() }
            setOnClickListener { dismiss() }
        }

        card.addView(tvRip)
        card.addView(ivDiglett)
        card.addView(tvEyes)
        card.addView(tvMsg)
        card.addView(btnClose)
        root.addView(card, cardParams)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val card = (view as ViewGroup).getChildAt(0)
        card.translationY = 200f
        card.alpha        = 0f

        val slideUp = ObjectAnimator.ofFloat(card, "translationY", 200f, 0f).apply {
            duration     = 450
            interpolator = OvershootInterpolator(1.4f)
        }
        val fadeIn = ObjectAnimator.ofFloat(card, "alpha", 0f, 1f).apply {
            duration     = 350
            interpolator = DecelerateInterpolator()
        }
        val enterSet = AnimatorSet()
        enterSet.playTogether(slideUp, fadeIn)
        enterSet.startDelay = 100
        enterSet.start()

        // Diglett se kymácí
        val ivDiglett = (card as ViewGroup).getChildAt(1) as? ImageView
        ivDiglett?.let {
            ObjectAnimator.ofFloat(it, "rotation", 180f, 195f, 165f, 180f).apply {
                duration    = 2000
                repeatCount = ValueAnimator.INFINITE
                repeatMode  = ValueAnimator.RESTART
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? MainActivity)?.updatePokemonVisibility()
    }
}
package cz.uhk.macroflow.pokemon

import android.animation.*
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.fragment.app.Fragment
import cz.uhk.macroflow.R
import cz.uhk.macroflow.common.MainActivity
import kotlin.math.*
import kotlin.random.Random

class PokemonBattleFragment : Fragment() {

    private var isClosing = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density

        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ── BATTLE OBSAH ──────────────────────────────────────────────────────
        val battleContent = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            alpha       = 0f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val titleTv = TextView(ctx).apply {
            text      = "★  ENCOUNTER  ★"
            textSize  = 10f
            typeface  = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#A8C8F8"))
            gravity   = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (6 * dp).toInt() }
        }

        val battleView = PokemonBattleView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            onCaught = {
                (requireActivity() as? MainActivity)?.updateMakromonVisibility()
                view?.postDelayed({ safeClose() }, 2000)
            }
        }

        val closeBtn = TextView(ctx).apply {
            text      = "[ CLOSE ]"
            textSize  = 9f
            typeface  = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#686868"))
            gravity   = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (8 * dp).toInt() }
            setOnClickListener { safeClose() }
        }

        battleContent.addView(titleTv)
        battleContent.addView(battleView)
        battleContent.addView(closeBtn)
        root.addView(battleContent)

        // ── INTRO OVERLAY ─────────────────────────────────────────────────────
        val introOverlay = buildIntroOverlay(ctx, dp, battleContent)
        root.addView(introOverlay)

        return root
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTRO OVERLAY
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildIntroOverlay(
        ctx: Context,
        dp: Float,
        battleContent: View
    ): FrameLayout {

        val bushLeftRes  = ctx.resources.getIdentifier("bush",  "drawable", ctx.packageName)
        val bushRightRes = ctx.resources.getIdentifier("bush1", "drawable", ctx.packageName)
        val leafRes      = ctx.resources.getIdentifier("leaf",  "drawable", ctx.packageName)

        val overlay = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        // ── GRADIENT VRSTVA ───────────────────────────────────────────────────
        val gradientView = View(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            alpha = 0f
        }
        overlay.addView(gradientView)

        // ── KŘOVÍ ─────────────────────────────────────────────────────────────
        val bushLeft = ImageView(ctx).apply {
            if (bushLeftRes != 0) setImageResource(bushLeftRes)
            scaleType    = ImageView.ScaleType.FIT_XY
            layoutParams = FrameLayout.LayoutParams(1, 1)
        }
        val bushRight = ImageView(ctx).apply {
            if (bushRightRes != 0) setImageResource(bushRightRes)
            else if (bushLeftRes != 0) setImageResource(bushLeftRes)
            scaleType    = ImageView.ScaleType.FIT_XY
            scaleX       = -1f
            layoutParams = FrameLayout.LayoutParams(1, 1)
        }

        // ── KONTEJNER NA LISTY ────────────────────────────────────────────────
        // DŮLEŽITÉ: leafContainer musí být PŘES křoví (přidán jako poslední)
        val leafContainer = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        overlay.addView(bushLeft)
        overlay.addView(bushRight)
        overlay.addView(leafContainer)

        overlay.post {
            val screenW = overlay.width.toFloat()
            val screenH = overlay.height.toFloat()

            // ── GRADIENT POZADÍ ────────────────────────────────────────────────
            val gradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.argb(255, 25, 55, 15),
                    Color.argb(255, 8,  18,  3),
                )
            ).apply {
                gradientType   = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = screenW * 0.85f
            }
            gradientView.background = gradientDrawable

            // ── ROZMĚRY KŘOVÍ ─────────────────────────────────────────────────
            val bushW = (screenW * 0.80f).toInt()
            val bushH = (screenH * 0.50f).toInt()
            val bushTopMargin = (screenH * 0.42f).toInt()

            (bushLeft.layoutParams as FrameLayout.LayoutParams).apply {
                width      = bushW
                height     = bushH
                gravity    = Gravity.NO_GRAVITY
                leftMargin = -(bushW / 10)
                topMargin  = bushTopMargin
            }
            bushLeft.requestLayout()

            (bushRight.layoutParams as FrameLayout.LayoutParams).apply {
                width      = bushW
                height     = bushH
                gravity    = Gravity.NO_GRAVITY
                leftMargin = (screenW - bushW + bushW / 10).toInt()
                topMargin  = bushTopMargin
            }
            bushRight.requestLayout()

            overlay.requestLayout()

            Handler(Looper.getMainLooper()).postDelayed({
                runIntroSequence(
                    ctx           = ctx,
                    overlay       = overlay,
                    battleContent = battleContent,
                    gradientView  = gradientView,
                    bushLeft      = bushLeft,
                    bushRight     = bushRight,
                    leafContainer = leafContainer,
                    leafRes       = leafRes,
                    screenW       = screenW,
                    screenH       = screenH,
                    bushTopMargin = bushTopMargin.toFloat(),
                    bushH         = bushH.toFloat(),
                    dp            = dp
                )
            }, 150)
        }

        return overlay
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTRO SEKVENCE
    // ─────────────────────────────────────────────────────────────────────────

    private fun runIntroSequence(
        ctx: Context,
        overlay: FrameLayout,
        battleContent: View,
        gradientView: View,
        bushLeft: ImageView,
        bushRight: ImageView,
        leafContainer: FrameLayout,
        leafRes: Int,
        screenW: Float,
        screenH: Float,
        bushTopMargin: Float,
        bushH: Float,
        dp: Float
    ) {
        val handler = Handler(Looper.getMainLooper())

        // ── FÁZE 1: Černá → Gradient ──────────────────────────────────────────
        val fadeInGradient = ObjectAnimator.ofFloat(gradientView, "alpha", 0f, 1f).apply {
            duration = 500
        }

        fadeInGradient.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {

                // ── FÁZE 2: Wiggle křoví ──────────────────────────────────────
                handler.postDelayed({
                    val wiggleSet = AnimatorSet().apply {
                        playTogether(
                            ObjectAnimator.ofFloat(bushLeft, "rotation", 0f, -5f, 4f, -3f, 2f, 0f).apply {
                                duration = 550; interpolator = LinearInterpolator()
                            },
                            ObjectAnimator.ofFloat(bushLeft, "translationY", 0f, -14f * dp, 7f * dp, -4f * dp, 0f).apply {
                                duration = 550; interpolator = AccelerateDecelerateInterpolator()
                            },
                            ObjectAnimator.ofFloat(bushRight, "rotation", 0f, 4f, -5f, 3f, -2f, 0f).apply {
                                duration = 550; interpolator = LinearInterpolator()
                            },
                            ObjectAnimator.ofFloat(bushRight, "translationY", 0f, -10f * dp, 12f * dp, -3f * dp, 0f).apply {
                                duration = 550; interpolator = AccelerateDecelerateInterpolator()
                            }
                        )
                    }

                    wiggleSet.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {

                            // ── FÁZE 3: Křoví odletí + listy ─────────────────
                            val leafOriginX = screenW * 0.50f
                            val leafOriginY = bushTopMargin + bushH * 0.25f

                            spawnLeafParticles(
                                ctx, leafContainer, leafRes,
                                leafOriginX, leafOriginY, screenW, dp
                            )

                            val flySet = AnimatorSet().apply {
                                playTogether(
                                    ObjectAnimator.ofFloat(bushLeft, "translationX",
                                        0f, -(screenW * 0.90f)
                                    ).apply { duration = 420; interpolator = AccelerateInterpolator(2f) },
                                    ObjectAnimator.ofFloat(bushRight, "translationX",
                                        0f, screenW * 0.90f
                                    ).apply { duration = 420; interpolator = AccelerateInterpolator(2f) }
                                )
                            }

                            flySet.addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {

                                    // ── FÁZE 4: Reveal flash ──────────────────
                                    battleContent.alpha = 0f

                                    val flashIn = ObjectAnimator.ofInt(
                                        overlay, "backgroundColor",
                                        Color.TRANSPARENT,
                                        Color.argb(235, 255, 255, 255)
                                    ).apply {
                                        duration = 110; setEvaluator(ArgbEvaluator())
                                    }
                                    val revealBattle = ObjectAnimator.ofFloat(
                                        battleContent, "alpha", 0f, 1f
                                    ).apply { duration = 280 }
                                    val flashOut = ObjectAnimator.ofInt(
                                        overlay, "backgroundColor",
                                        Color.argb(235, 255, 255, 255), Color.TRANSPARENT
                                    ).apply {
                                        duration = 380; setEvaluator(ArgbEvaluator())
                                    }

                                    AnimatorSet().apply {
                                        play(revealBattle).with(flashIn)
                                        play(flashOut).after(flashIn)
                                        addListener(object : AnimatorListenerAdapter() {
                                            override fun onAnimationEnd(a: Animator) {
                                                overlay.visibility = View.GONE
                                            }
                                        })
                                        start()
                                    }
                                }
                            })

                            flySet.start()
                        }
                    })

                    wiggleSet.start()
                }, 150)
            }
        })

        handler.postDelayed({ fadeInGradient.start() }, 350)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LEAF PARTICLES
    // ─────────────────────────────────────────────────────────────────────────

    private fun spawnLeafParticles(
        ctx: Context,
        container: FrameLayout,
        leafRes: Int,
        originX: Float,
        originY: Float,
        screenW: Float,
        dp: Float
    ) {
        val handler = Handler(Looper.getMainLooper())
        repeat(14) { i ->
            handler.postDelayed({
                if (container.isAttachedToWindow) {
                    spawnSingleLeaf(ctx, container, leafRes, originX, originY, screenW, dp)
                }
            }, i * 45L)
        }
    }

    private fun spawnSingleLeaf(
        ctx: Context,
        container: FrameLayout,
        leafRes: Int,
        originX: Float,
        originY: Float,
        screenW: Float,
        dp: Float
    ) {
        val leafSizeDp = 32f + Random.nextFloat() * 20f
        val leafSize   = (leafSizeDp * dp).toInt()

        // ── OPRAVA: pozice přes leftMargin/topMargin, ne x/y ─────────────────
        // leaf.x a leaf.y jsou zkratky za translationX/Y relativně k layout pozici.
        // FrameLayout umísťuje view na (0,0), takže nastavení leaf.x = originX
        // ve skutečnosti nastavovalo translationX – animace ho pak přepsala od 0f
        // a list skončil v rohu. Správně: absolutní pozice = leftMargin + topMargin,
        // translationX/Y pak přidávají relativní pohyb na vrchol té pozice.
        val spawnX = (originX - leafSize / 2f + (Random.nextFloat() - 0.5f) * 60f * dp).toInt()
        val spawnY = (originY - leafSize / 2f + (Random.nextFloat() - 0.5f) * 30f * dp).toInt()

        val leaf = ImageView(ctx).apply {
            if (leafRes != 0) setImageResource(leafRes)
            layoutParams = FrameLayout.LayoutParams(leafSize, leafSize).apply {
                leftMargin = spawnX
                topMargin  = spawnY
            }
            alpha    = 0f
            rotation = Random.nextFloat() * 360f
        }
        container.addView(leaf)

        // Směr letu: primárně nahoru a do stran
        val angleDeg = -90.0 + Random.nextDouble(-70.0, 70.0)
        val angleRad = Math.toRadians(angleDeg)
        val dist     = (120f + Random.nextFloat() * 220f) * dp

        val dx = cos(angleRad).toFloat() * dist
        val dy = sin(angleRad).toFloat() * dist

        val duration = 700L + Random.nextLong(0L, 500L)

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(leaf, "alpha", 0f, 1f, 0.85f, 0f).apply {
                    this.duration = duration
                },
                ObjectAnimator.ofFloat(leaf, "translationX", 0f, dx).apply {
                    this.duration = duration
                    interpolator  = DecelerateInterpolator(1.8f)
                },
                ObjectAnimator.ofFloat(leaf, "translationY", 0f, dy).apply {
                    this.duration = duration
                    interpolator  = DecelerateInterpolator(1.5f)
                },
                ObjectAnimator.ofFloat(
                    leaf, "rotation",
                    leaf.rotation,
                    leaf.rotation + (if (Random.nextBoolean()) 200f else -200f) + Random.nextFloat() * 80f
                ).apply {
                    this.duration = duration
                    interpolator  = LinearInterpolator()
                },
                ObjectAnimator.ofFloat(leaf, "scaleX", 1f, 0.2f).apply { this.duration = duration },
                ObjectAnimator.ofFloat(leaf, "scaleY", 1f, 0.2f).apply { this.duration = duration }
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    container.removeView(leaf)
                }
            })
            start()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun safeClose() {
        if (isClosing) return
        isClosing = true
        if (isAdded && !isRemoving) parentFragmentManager.popBackStack()
    }
}
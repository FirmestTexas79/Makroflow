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

    /** Shiny se losuje tady (ne ve view), aby na něj mohlo reagovat už intro. */
    private var isShiny = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density

        // Tutoriálové křoví je vždy stejné; ladicí přepínač vynutí shiny jen pro jedno setkání
        val gamePrefs = ctx.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        val forced = gamePrefs.contains("FORCE_ENCOUNTER_ID")
        val debugShiny = gamePrefs.getBoolean("DEBUG_FORCE_SHINY", false)
        if (debugShiny) gamePrefs.edit().remove("DEBUG_FORCE_SHINY").apply()
        isShiny = !forced && (debugShiny || cz.uhk.macroflow.pokemon.shiny.ShinyPalette.roll())

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
            text      = if (isShiny) "✦  SHINY ENCOUNTER  ✦" else "★  ENCOUNTER  ★"
            textSize  = 10f
            typeface  = Typeface.MONOSPACE
            setTextColor(Color.parseColor(if (isShiny) "#FFD54F" else "#A8C8F8"))
            gravity   = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (6 * dp).toInt() }
        }

        // V onCreateView fragmentu uprav onCaught takto:
        val battleView = PokemonBattleView(ctx, null, isShiny).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            // V onCreateView fragmentu uprav onCaught:
            onCaught = {
                val prefs = ctx.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

                // Tady je ten trik: pokud v prefs JE force encounter,
                // pošleme "starter_bush", jinak pošleme "wild"
                val isFromQuestBush = prefs.contains("FORCE_ENCOUNTER_ID")
                val source = if (isFromQuestBush) "starter_bush" else "wild"

                // Zavoláme manažer (Ujisti se, že v MakromonMapActivity je questManager public)
                (activity as? MakromonMapActivity)?.let { mapActivity ->
                    mapActivity.questManager.onMakromonCaught(source)
                }

                // Odstraníme příznak až POTÉ, co jsme ho použili
                prefs.edit().remove("FORCE_ENCOUNTER_ID").apply()

                (requireActivity() as? cz.uhk.macroflow.common.MainActivity)?.updateMakromonVisibility()

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

        // ── INTRO podle lokace: Hory mají vlastní (kameny a hory), jinde křoví ──
        val biome = runCatching {
            BiomeType.valueOf(gamePrefs.getString("LAST_BIOME", BiomeType.TOWN.name) ?: BiomeType.TOWN.name)
        }.getOrDefault(BiomeType.TOWN)
        val introOverlay = if (biome == BiomeType.MOUNTAINS) buildMountainIntro(ctx, dp, battleContent)
            else buildIntroOverlay(ctx, dp, battleContent, biome)
        root.addView(introOverlay)

        return root
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ŘÍZENÍ INTRA: přeskočení klepnutím, úklid po zavření
    // ─────────────────────────────────────────────────────────────────────────

    private val introHandler = Handler(Looper.getMainLooper())
    private val introAnimators = mutableListOf<Animator>()
    /** Intro už skončilo nebo bylo přeskočeno – další fáze se nespouštějí. */
    private var introDone = false
    private var revealStarted = false

    private fun <T : Animator> T.tracked(): T { introAnimators += this; return this }

    override fun onDestroyView() {
        // Dřív zpožděná volání a animace dobíhaly i po zavření souboje
        introHandler.removeCallbacksAndMessages(null)
        introAnimators.toList().forEach { it.cancel() }
        introAnimators.clear()
        super.onDestroyView()
    }

    /** Pixel art bez rozmazání při zvětšení. */
    private fun ImageView.crisp() {
        (drawable as? android.graphics.drawable.BitmapDrawable)?.apply { isFilterBitmap = false; setAntiAlias(false) }
    }

    /**
     * Společné odhalení souboje: záblesk (bílý, v Horách písečný, u shiny zlatý) + případně déšť hvězd.
     */
    private fun revealBattle(
        ctx: Context, overlay: FrameLayout, battleContent: View,
        screenW: Float, screenH: Float, dp: Float,
        baseFlash: Int = Color.argb(235, 255, 255, 255)
    ) {
        if (revealStarted) return
        revealStarted = true

        val flashColor = if (isShiny) Color.argb(240, 255, 214, 102) else baseFlash
        if (isShiny) spawnStarBurst(ctx, (overlay.parent as? FrameLayout) ?: overlay, screenW / 2f, screenH * 0.38f, dp)

        // Záblesk jako samostatná vrstva NAVRCHU. Dřív se animovalo pozadí overlaye, jenže to
        // celé zakrýval přechod – záblesk nebyl vidět a intro na konci jen naráz zmizelo.
        val flash = View(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(flashColor)
            alpha = 0f
        }
        overlay.addView(flash)

        val flashIn = ObjectAnimator.ofFloat(flash, "alpha", 0f, 1f).apply { duration = if (isShiny) 180 else 110 }
        flashIn.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) {
                // Na vrcholu záblesku vyměnit scénu za souboj
                overlay.getChildAt(0)?.visibility = View.INVISIBLE
                overlay.setBackgroundColor(Color.TRANSPARENT)
                battleContent.alpha = 1f
            }
        })
        val flashOut = ObjectAnimator.ofFloat(flash, "alpha", 1f, 0f).apply {
            duration = if (isShiny) 650 else 380
            interpolator = DecelerateInterpolator()
        }
        AnimatorSet().apply {
            playSequentially(flashIn, flashOut)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { overlay.visibility = View.GONE }
            })
            tracked()
            start()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HORY: kameny a hory (pixel art z MountainScene)
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildMountainIntro(ctx: Context, dp: Float, battleContent: View): FrameLayout {
        val overlay = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.BLACK)
        }
        val scene = cz.uhk.macroflow.pokemon.encounter.MountainEncounterView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        overlay.addView(scene)
        scene.onReveal = {
            introDone = true
            revealBattle(ctx, overlay, battleContent, overlay.width.toFloat(), overlay.height.toFloat(), dp,
                baseFlash = Color.argb(235, 255, 236, 200))
        }
        overlay.setOnClickListener { if (!introDone) scene.skip() }
        introHandler.postDelayed({ scene.start() }, 120)
        return overlay
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KŘOVÍ (louka, město, voda)
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildIntroOverlay(
        ctx: Context,
        dp: Float,
        battleContent: View,
        biome: BiomeType
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

        // Vše, co má pod zábleskem zmizet, je v jednom kontejneru (revealBattle skryje první dítě)
        val stage = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        overlay.addView(stage)

        val gradientView = View(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            alpha = 0f
        }
        stage.addView(gradientView)

        val bushLeft = ImageView(ctx).apply {
            if (bushLeftRes != 0) setImageResource(bushLeftRes)
            scaleType    = ImageView.ScaleType.FIT_XY
            layoutParams = FrameLayout.LayoutParams(1, 1)
            crisp()
        }
        val bushRight = ImageView(ctx).apply {
            if (bushRightRes != 0) setImageResource(bushRightRes)
            else if (bushLeftRes != 0) setImageResource(bushLeftRes)
            scaleType    = ImageView.ScaleType.FIT_XY
            scaleX       = -1f
            layoutParams = FrameLayout.LayoutParams(1, 1)
            crisp()
        }

        // Listy nad křovím
        val leafContainer = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        stage.addView(bushLeft)
        stage.addView(bushRight)
        stage.addView(leafContainer)

        overlay.post {
            val screenW = overlay.width.toFloat()
            val screenH = overlay.height.toFloat()

            // Barva pozadí podle lokace (voda modrá, jinak les)
            val (inner, outer) = if (biome == BiomeType.WATER)
                Color.argb(255, 18, 52, 92) to Color.argb(255, 4, 12, 30)
            else Color.argb(255, 25, 55, 15) to Color.argb(255, 8, 18, 3)
            gradientView.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(inner, outer)).apply {
                gradientType   = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = screenW * 0.85f
            }

            // Keř je čtvercový pixel art – dřív se roztahoval na 80 % × 50 % obrazovky a deformoval
            val bushSize = (screenW * 0.80f).toInt()
            val bushTopMargin = (screenH * 0.92f).toInt() - bushSize

            (bushLeft.layoutParams as FrameLayout.LayoutParams).apply {
                width = bushSize; height = bushSize
                gravity    = Gravity.NO_GRAVITY
                leftMargin = -(bushSize / 10)
                topMargin  = bushTopMargin
            }
            bushLeft.requestLayout()

            (bushRight.layoutParams as FrameLayout.LayoutParams).apply {
                width = bushSize; height = bushSize
                gravity    = Gravity.NO_GRAVITY
                leftMargin = (screenW - bushSize + bushSize / 10).toInt()
                topMargin  = bushTopMargin
            }
            bushRight.requestLayout()
            overlay.requestLayout()

            // Klepnutím intro přeskočíš: křoví rychle uhne a hned je záblesk
            overlay.setOnClickListener {
                if (introDone) return@setOnClickListener
                introDone = true
                introAnimators.toList().forEach { it.cancel() }
                gradientView.alpha = 1f
                bushLeft.animate().translationX(-screenW).setDuration(160).start()
                bushRight.animate().translationX(screenW).setDuration(160).start()
                revealBattle(ctx, overlay, battleContent, screenW, screenH, dp)
            }

            introHandler.postDelayed({
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
                    bushH         = bushSize.toFloat(),
                    dp            = dp
                )
            }, 120)
        }

        return overlay
    }

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
        if (introDone) return

        // ── FÁZE 1: Černá → Gradient ──
        val fadeInGradient = ObjectAnimator.ofFloat(gradientView, "alpha", 0f, 1f).apply { duration = 400 }.tracked()

        fadeInGradient.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (introDone) return

                // ── FÁZE 2: Wiggle křoví ──
                introHandler.postDelayed({
                    if (introDone) return@postDelayed
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
                    }.tracked()

                    wiggleSet.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (introDone) return

                            // ── FÁZE 3: Křoví odletí + listy ──
                            spawnLeafParticles(ctx, leafContainer, leafRes, screenW * 0.50f, bushTopMargin + bushH * 0.25f, screenW, dp)

                            val flySet = AnimatorSet().apply {
                                playTogether(
                                    ObjectAnimator.ofFloat(bushLeft, "translationX", 0f, -(screenW * 0.90f))
                                        .apply { duration = 420; interpolator = AccelerateInterpolator(2f) },
                                    ObjectAnimator.ofFloat(bushRight, "translationX", 0f, screenW * 0.90f)
                                        .apply { duration = 420; interpolator = AccelerateInterpolator(2f) }
                                )
                            }.tracked()

                            flySet.addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    if (introDone) return
                                    // ── FÁZE 4: záblesk a souboj ──
                                    introDone = true
                                    revealBattle(ctx, overlay, battleContent, screenW, screenH, dp)
                                }
                            })
                            flySet.start()
                        }
                    })
                    wiggleSet.start()
                }, 120)
            }
        })

        introHandler.postDelayed({ if (!introDone) fadeInGradient.start() }, 200)
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
        repeat(14) { i ->
            introHandler.postDelayed({
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
            crisp()
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
    // SHINY: DÉŠŤ HVĚZD PŘI ODHALENÍ
    // ─────────────────────────────────────────────────────────────────────────

    private fun spawnStarBurst(ctx: Context, container: FrameLayout, cx: Float, cy: Float, dp: Float) {
        val colors = intArrayOf(Color.parseColor("#FFD54F"), Color.parseColor("#FFF3C4"), Color.WHITE)
        repeat(26) { i ->
            val star = TextView(ctx).apply {
                text = if (i % 3 == 0) "✦" else "✧"
                setTextColor(colors[i % colors.size])
                textSize = 14f + Random.nextFloat() * 18f
                setShadowLayer(8f * dp, 0f, 0f, Color.parseColor("#FFC107"))
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                    .apply { leftMargin = cx.toInt(); topMargin = cy.toInt() }
                alpha = 0f
            }
            container.addView(star)
            val a = Math.toRadians(i * (360.0 / 26) + Random.nextDouble(-6.0, 6.0))
            val dist = (110f + Random.nextFloat() * 170f) * dp
            val dur = 900L + Random.nextLong(0L, 500L)
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(star, "alpha", 0f, 1f, 1f, 0f).apply { duration = dur },
                    ObjectAnimator.ofFloat(star, "translationX", 0f, cos(a).toFloat() * dist).apply { duration = dur; interpolator = DecelerateInterpolator(2f) },
                    ObjectAnimator.ofFloat(star, "translationY", 0f, sin(a).toFloat() * dist).apply { duration = dur; interpolator = DecelerateInterpolator(2f) },
                    ObjectAnimator.ofFloat(star, "rotation", 0f, if (i % 2 == 0) 270f else -270f).apply { duration = dur },
                    ObjectAnimator.ofFloat(star, "scaleX", 0.3f, 1.3f, 0.6f).apply { duration = dur },
                    ObjectAnimator.ofFloat(star, "scaleY", 0.3f, 1.3f, 0.6f).apply { duration = dur }
                )
                startDelay = (i % 4) * 40L
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) { container.removeView(star) }
                })
                start()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun safeClose() {
        if (isClosing) return
        isClosing = true
        if (isAdded && !isRemoving) parentFragmentManager.popBackStack()
    }
}
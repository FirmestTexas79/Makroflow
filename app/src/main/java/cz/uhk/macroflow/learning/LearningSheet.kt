package cz.uhk.macroflow.learning

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import cz.uhk.macroflow.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Co se aplikace o tobě naučila (Profil). Klidná obrazovka: nahoře letokruhy podle týdnů používání
 * a věta, v kolika oblastech se už aplikace liší od výchozího nastavení; pod tím jeden poznatek na kartu,
 * podrobnosti se rozbalí klepnutím.
 */
class LearningSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "LearningSheet"
        fun show(fm: FragmentManager) {
            if (fm.findFragmentByTag(TAG) != null) return
            LearningSheet().show(fm, TAG)
        }
    }

    private lateinit var col: LinearLayout
    private fun c(id: Int) = ContextCompat.getColor(requireContext(), id)
    private val dark get() = c(R.color.brand_dark)
    private val cream get() = c(R.color.brand_cream)
    private val primary get() = c(R.color.brand_primary)
    private val warm get() = c(R.color.brand_accent_warm)
    private val tint get() = c(R.color.brand_primary_alpha10)
    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20f), dp(18f), dp(20f), dp(32f)) }
        return FrameLayout(ctx).apply {
            setBackgroundResource(R.drawable.bg_sheet_cream)
            addView(NestedScrollView(ctx).apply { addView(col) })
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let { d ->
            d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                sheet.layoutParams = sheet.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
            }
            d.behavior.skipCollapsed = true
            d.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                runCatching { LearningData.load(app).also { LearningData.markOpened(app, LearningData.quick(app).factor) } }
                    .getOrElse { android.util.Log.e("Learning", "load failed", it); Report(0, false, emptyList()) }
            }
            build(report)
        }
    }

    private fun tv(text: String, sp: Float, color: Int, black: Boolean = false, alpha: Float = 1f) = TextView(requireContext()).apply {
        this.text = text; textSize = sp; setTextColor(color); this.alpha = alpha
        if (black) typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }

    private fun round(color: Int, r: Float) = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(color) }

    private fun build(r: Report) {
        col.removeAllViews()
        col.addView(tv("Co se aplikace naučila", 26f, dark, black = true))
        col.addView(tv(if (r.daysUsed > 0) "Den ${r.daysUsed} s Makroflow" else "Začínáme", 13f, primary).apply { setPadding(0, dp(2f), 0, dp(14f)) })

        if (r.unlocked) col.addView(hero(r)) else col.addView(locked(r))

        if (r.items.isNotEmpty()) {
            col.addView(tv(if (r.unlocked) "Co o tobě vím" else "Co už vím", 17f, dark, black = true).apply { setPadding(dp(4f), dp(22f), 0, dp(8f)) })
            r.items.forEach { col.addView(card(it)) }
            col.addView(tv("Tečky ukazují jistotu: jedna je málo dat, tři ověřeno. Oranžový rámeček znamená, že se tvoje aplikace už liší od výchozího nastavení.", 12f, dark, alpha = 0.6f)
                .apply { setPadding(dp(4f), dp(14f), dp(4f), 0) })
        }

        if (r.unlocked) col.addView(MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Sd\u00EDlet shrnut\u00ED"; isAllCaps = false; textSize = 15f; cornerRadius = dp(28f); setTextColor(dark)
            strokeColor = android.content.res.ColorStateList.valueOf(Color.argb(70, 40, 54, 24))
            setOnClickListener { share(r) }
            layoutParams = LinearLayout.LayoutParams(-1, dp(52f)).apply { topMargin = dp(18f) }
        })
        col.addView(MaterialButton(requireContext()).apply {
            text = "Hotovo"; isAllCaps = false; textSize = 16f; cornerRadius = dp(28f)
            backgroundTintList = android.content.res.ColorStateList.valueOf(primary); setTextColor(cream)
            setOnClickListener { dismiss() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(56f)).apply { topMargin = dp(24f) }
        })
    }

    private fun share(r: Report) {
        val app = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                val log = runCatching { cz.uhk.macroflow.loop.LoopData.entries(app) }.getOrDefault(emptyList())
                    .sortedByDescending { it.date }.take(14).map { Triple(it.date, it.text, it.applied) }
                LearningModel.exportText(r, log, LocalDate.now())
            }
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, text)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Makroflow: co se aplikace nau\u010Dila")
            }
            startActivity(android.content.Intent.createChooser(send, null))
        }
    }

    private fun hero(r: Report): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = round(dark, 28f); setPadding(dp(18f), dp(18f), dp(20f), dp(18f))
        addView(GrowthRingsView(requireContext(), r.weeks, cream, warm).apply {
            contentDescription = "Letokruhy: ${r.weeks} týdnů používání"
        }, LinearLayout.LayoutParams(dp(112f), dp(112f)))
        addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16f), 0, 0, 0)
            if (r.items.isEmpty()) addView(tv("Zatím mám málo dat, abych se od výchozího nastavení lišila.", 16f, cream, black = true))
            else {
                addView(tv("Tvoje aplikace se od výchozí liší v ${r.differing} z ${r.items.size} oblastí", 17f, cream, black = true))
                addView(tv("Čím víc zapisuješ, tím víc se přizpůsobí.", 13f, cream, alpha = 0.7f).apply { setPadding(0, dp(6f), 0, 0) })
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun locked(r: Report): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; background = round(dark, 28f); setPadding(dp(20f), dp(18f), dp(20f), dp(20f))
        val left = (LearningModel.UNLOCK_DAYS - r.daysUsed).coerceAtLeast(0)
        addView(tv("Učím se tě poznávat", 20f, cream, black = true))
        addView(tv("Úplné shrnutí se odemkne za ${LearningModel.days(left)}. Do té doby sbírám váhu, jídlo, tréninky a spánek.", 14f, cream, alpha = 0.8f).apply { setPadding(0, dp(6f), 0, dp(14f)) })
        addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            val done = r.daysUsed.coerceIn(0, LearningModel.UNLOCK_DAYS)
            addView(View(requireContext()).apply { background = round(warm, 4f) }, LinearLayout.LayoutParams(0, dp(8f), done.toFloat().coerceAtLeast(0.01f)))
            addView(View(requireContext()).apply { background = round(Color.argb(60, 254, 250, 224), 4f) }, LinearLayout.LayoutParams(0, dp(8f), (LearningModel.UNLOCK_DAYS - done).toFloat().coerceAtLeast(0.01f)))
        })
    }

    private fun card(l: Learning): View {
        val ctx = requireContext()
        val detail = tv(l.detail, 13f, dark, alpha = 0.75f).apply { visibility = View.GONE; setPadding(0, dp(8f), 0, 0) }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            background = GradientDrawable().apply { cornerRadius = dp(20f).toFloat(); setColor(tint); if (l.differs) setStroke(dp(1.5f), warm) }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10f) }
            isClickable = true; isFocusable = true
            setOnClickListener { detail.visibility = if (detail.visibility == View.GONE) View.VISIBLE else View.GONE }
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(tv(l.area.icon, 22f, dark).apply { setPadding(0, 0, dp(12f), 0) })
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(tv(l.area.label, 12f, primary, black = true))
                    addView(tv(l.headline, 16f, dark, black = true).apply { setPadding(0, dp(2f), 0, 0) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(dots(l.dots), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10f) })
            })
            if (l.from != null && l.to != null)
                addView(tv("Výchozí ${l.from}  →  tvoje ${l.to}", 13f, dark, alpha = 0.75f).apply { setPadding(0, dp(8f), 0, 0) })
            addView(detail)
        }
    }

    private fun dots(n: Int) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        contentDescription = "Jistota: $n ze 3"
        repeat(3) { i ->
            addView(View(requireContext()).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (i < n) primary else Color.argb(60, 40, 54, 24)) }
            }, LinearLayout.LayoutParams(dp(8f), dp(8f)).apply { marginStart = dp(3f) })
        }
    }
}

/** Letokruhy: jeden kruh za každý týden používání, poslední (letošní) je oranžový. */
class GrowthRingsView(ctx: Context, private val rings: Int, private val ringColor: Int, private val accent: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f; val cy = height / 2f; val maxR = min(width, height) / 2f - 4 * d
        val n = rings.coerceIn(1, 9)
        for (i in 1..n) {
            val r = maxR * (0.2f + 0.8f * i / n)
            val path = Path()
            for (s in 0..90) {
                val a = (2 * PI * s / 90).toFloat()
                val w = 1f + 0.035f * sin(a * 3 + i * 1.7f) + 0.02f * sin(a * 5 + i * 0.9f)
                val x = cx + r * w * cos(a); val y = cy + r * w * sin(a)
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            p.style = Paint.Style.STROKE
            p.strokeWidth = (if (i == n) 3.5f else 1.6f) * d
            p.color = if (i == n) accent else ringColor
            p.alpha = if (i == n) 255 else 90 + 120 * i / n
            c.drawPath(path, p)
        }
        p.style = Paint.Style.FILL; p.color = accent; p.alpha = 255
        c.drawCircle(cx, cy, maxR * 0.09f, p)
    }
}

/** Slim upozornění na Dashboardu: odemčení po 7 dnech, výrazný posun odhadu výdeje, nedělní shrnutí. */
object LearningPill {
    fun attach(coachCard: View, host: Fragment): () -> Unit {
        val ctx = coachCard.context
        val d = ctx.resources.displayMetrics.density
        val parent = coachCard.parent as? LinearLayout ?: return {}
        val cream = ContextCompat.getColor(ctx, R.color.brand_cream)
        val text = TextView(ctx).apply { setTextColor(cream); textSize = 13.5f }
        val close = TextView(ctx).apply {
            this.text = "✕"; setTextColor(cream); textSize = 15f; gravity = Gravity.CENTER
            minimumWidth = (44 * d).toInt(); minimumHeight = (44 * d).toInt(); contentDescription = "Skrýt"
        }
        val card = MaterialCardView(ctx).apply {
            radius = 20 * d; cardElevation = 0f; setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.brand_dark))
            visibility = View.GONE; isClickable = true; isFocusable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), 0) }
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * d).toInt(), (6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt())
                addView(TextView(ctx).apply { this.text = "🌱"; textSize = 20f; setPadding(0, 0, (12 * d).toInt(), 0) })
                addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(close)
            })
        }
        parent.addView(card, parent.indexOfChild(coachCard) + 1)

        var current: Notice? = null
        var factor: Double? = null
        fun ack() { current?.let { LearningData.acknowledge(ctx, it, factor) }; card.visibility = View.GONE }
        card.setOnClickListener {
            val weekly = current?.key?.startsWith("week:") == true
            ack()
            if (weekly) cz.uhk.macroflow.report.WeeklyReportSheet.show(host.childFragmentManager)
            else LearningSheet.show(host.childFragmentManager)
        }
        close.setOnClickListener { ack() }

        return {
            host.viewLifecycleOwner.lifecycleScope.launch {
                val app = ctx.applicationContext
                val (n, f) = withContext(Dispatchers.IO) {
                    runCatching {
                        val q = LearningData.quick(app)
                        LearningModel.notice(q.daysUsed, q.factor, LearningData.state(app), LocalDate.now()) to q.factor
                    }.getOrElse { android.util.Log.e("Learning", "notice failed", it); null to null }
                }
                current = n; factor = f
                if (n != null) { text.text = n.text; card.visibility = View.VISIBLE } else card.visibility = View.GONE
            }
        }
    }
}

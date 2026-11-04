package cz.uhk.macroflow.training.log

import android.graphics.Color
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
import androidx.core.os.bundleOf
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.WorkoutSetEntity
import cz.uhk.macroflow.training.exercises.ExerciseLibrary
import cz.uhk.macroflow.wearable.ActivityGroups
import cz.uhk.macroflow.wearable.SportGroup
import cz.uhk.macroflow.wearable.WearableActivityEntity
import cz.uhk.macroflow.wearable.WearableHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Výpis po dokončení tréninku: hrdina s délkou, souhrn v jedné kartě, vrchol tréninku, cviky jako karty s pilulkami sérií
 * a data z hodinek. Když hodinky ještě nic neposlaly, aplikace si je po chvíli sama vyžádá a karta se doplní.
 */
class WorkoutSummarySheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "WorkoutSummarySheet"
        /** [date] = den výpisu (yyyy-MM-dd); výchozí je dnešek. [startedAt] a [rewarded] platí jen pro právě dokončený trénink. */
        fun show(fm: FragmentManager, title: String, key: String, startedAt: Long = 0L, rewarded: Boolean = false,
                 date: String = LocalDate.now().toString(), fresh: Boolean = false) {
            if (fm.findFragmentByTag(TAG) != null) return
            WorkoutSummarySheet().apply {
                arguments = bundleOf("title" to title, "key" to key, "start" to startedAt, "rew" to rewarded, "date" to date, "fresh" to fresh)
            }.show(fm, TAG)
        }
    }

    private lateinit var col: LinearLayout
    private var waiting = false
    private val dark get() = ContextCompat.getColor(requireContext(), R.color.brand_dark)
    private val cream get() = ContextCompat.getColor(requireContext(), R.color.brand_cream)
    private val primary get() = ContextCompat.getColor(requireContext(), R.color.brand_primary)
    private val warm get() = ContextCompat.getColor(requireContext(), R.color.brand_accent_warm)
    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private val sand = Color.parseColor("#F1EEDA")
    private val amber = Color.parseColor("#F6E3BC")
    private val mint = Color.parseColor("#DCE9C6")
    private val sky = Color.parseColor("#D6E8F3")
    private val chip = Color.parseColor("#EFEBD6")
    private val chipBest = Color.parseColor("#F3D49B")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20f), dp(14f), dp(20f), dp(32f)) }
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

    private data class Data(val all: List<WorkoutSetEntity>, val watch: List<WearableActivityEntity>, val hasKey: Boolean)

    private suspend fun load(date: String): Data? = withContext(Dispatchers.IO) {
        runCatching {
            val app = requireContext().applicationContext
            val db = AppDatabase.getDatabase(app)
            Data(db.workoutDao().getAllSync(),
                db.wearableDao().getActivitiesSync(date).filter { ActivityGroups.of(it.typeKey) == SportGroup.STRENGTH },
                WearableHub.hasAny(app))
        }.getOrElse { android.util.Log.e("WorkoutSummary", "load failed", it); null }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val date = requireArguments().getString("date") ?: LocalDate.now().toString()
            var data = load(date) ?: run { dismiss(); return@launch }
            // Čerstvý trénink a hodinky ještě nic nemají: zkusíme je sami přesynchronizovat (po půl minutě, nejvýš 8×)
            val wait = data.hasKey && data.watch.isEmpty() && date == LocalDate.now().toString()
            waiting = wait
            build(data)
            if (!wait) return@launch
            repeat(8) { i ->
                if (i > 0) delay(45_000)
                withContext(Dispatchers.IO) { runCatching { WearableHub.run(app, days = 2) } }
                val d = load(date) ?: return@launch
                data = d
                if (d.watch.isNotEmpty()) { waiting = false; build(d); return@launch }
            }
            waiting = false; build(data)
        }
    }

    private fun tv(text: String, sp: Float, color: Int, black: Boolean = false, alpha: Float = 1f) = TextView(requireContext()).apply {
        this.text = text; textSize = sp; setTextColor(color); this.alpha = alpha
        if (black) typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }

    private fun round(color: Int, r: Float) = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(color) }

    private fun card(color: Int, stroke: Boolean = false) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18f), dp(16f), dp(18f), dp(16f))
        background = GradientDrawable().apply {
            cornerRadius = dp(24f).toFloat(); setColor(color)
            if (stroke) setStroke(dp(1f), Color.parseColor("#1F283618"))
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) }
    }

    private fun stat(value: String, label: String) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        addView(tv(value, 22f, dark, black = true)); addView(tv(label, 12f, dark, alpha = 0.65f))
    }

    private fun statRow(vararg s: View) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        s.forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
    }

    private fun heading(s: String) = col.addView(tv(s, 17f, dark, black = true).apply { setPadding(dp(4f), dp(24f), 0, dp(2f)) })

    private fun pill(text: String, bg: Int, bold: Boolean = false) = tv(text, 13f, dark, black = bold).apply {
        background = round(bg, 14f); setPadding(dp(11f), dp(6f), dp(11f), dp(6f))
    }

    private fun e1rm(s: WorkoutSetEntity) = s.weightKg * (1 + s.reps / 30.0)

    private fun build(data: Data) {
        val args = requireArguments()
        val today = args.getString("date")?.let { LocalDate.parse(it) } ?: LocalDate.now()
        val key = args.getString("key")
        val startedAt = args.getLong("start")
        val rewarded = args.getBoolean("rew")
        val mine = data.all.filter { it.date == today.toString() && it.template == key }
            .ifEmpty { data.all.filter { it.date == today.toString() } }
            .sortedBy { it.createdAt }
        col.removeAllViews()

        val minutes = if (mine.isEmpty()) 0 else {
            val start = if (startedAt > 0) startedAt else mine.first().createdAt
            val end = if (startedAt > 0) maxOf(System.currentTimeMillis(), mine.last().createdAt) else mine.last().createdAt
            ((end - start) / 60_000).toInt().coerceAtLeast(0)
        }
        val intensity = mine.groupBy { it.exerciseId }.flatMap { (id, sets) ->
            (ExerciseLibrary.byId(id)?.primary ?: emptySet()).map { m -> m to sets.size / 4.0 }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> (v.sum() + 0.25).coerceAtMost(1.0) }
        val body = cz.uhk.macroflow.training.body.BodyMapView(requireContext()).apply {
            showCaptions = false; sides = listOf(cz.uhk.macroflow.training.body.BodySide.FRONT)
            baseColor = Color.parseColor("#3B4A28"); idleMuscleColor = Color.parseColor("#4A5A33"); outlineColor = cream
            setIntensities(intensity, warm)
            contentDescription = "Zatížené svaly"
        }
        col.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = round(dark, 28f); setPadding(dp(22f), dp(18f), dp(10f), dp(18f))
            addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                addView(tv(if (today == LocalDate.now()) "Trénink hotový" else "Trénink", 13f, warm, black = true))
                addView(tv(if (minutes > 0) "$minutes min" else "–", 48f, cream, black = true).apply { setPadding(0, dp(2f), 0, 0) })
                addView(tv(args.getString("title").orEmpty(), 15f, cream, alpha = 0.85f))
                addView(tv("${today.dayOfMonth}. ${today.monthValue}.", 13f, cream, alpha = 0.6f))
                if (rewarded) addView(tv("+15 ⚡ Energie čeká v Makrosvětu.", 13f, warm).apply { setPadding(0, dp(10f), 0, 0) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(body, LinearLayout.LayoutParams(dp(120f), dp(190f)))
        })

        if (mine.isEmpty()) {
            col.addView(tv("Dnes nejsou zapsané žádné série.", 14f, dark, alpha = 0.7f).apply { setPadding(dp(4f), dp(16f), 0, 0) })
        } else {
            val byEx = mine.groupBy { it.exerciseId }
            val volume = mine.sumOf { it.weightKg * it.reps }
            col.addView(card(sand).apply {
                addView(statRow(stat("${mine.size}", "sérií"), stat("${byEx.size}", "cviků"),
                    stat("${mine.sumOf { it.reps }}", "opakování"), stat(LogSetSheet.kg(volume), "kg celkem")))
            })

            // Z hodinek hned pod souhrnem, ať je vidět, když se doplní
            watchCard(data)

            val before = data.all.filter { it.date < today.toString() }.groupBy { it.exerciseId }
            val records = byEx.mapNotNull { (id, sets) ->
                val prev = before[id]?.maxOfOrNull { e1rm(it) } ?: return@mapNotNull null
                val best = sets.maxByOrNull { e1rm(it) } ?: return@mapNotNull null
                if (e1rm(best) > prev * 1.01) (ExerciseLibrary.byId(id)?.name ?: id) to best else null
            }
            if (records.isNotEmpty() && args.getBoolean("fresh")) {
                cz.uhk.macroflow.learning.LearningData.setPendingPr(requireContext(),
                    records.joinToString(", ") { (name, s) -> "$name (1RM ≈ ${LogSetSheet.kg(e1rm(s))} kg)" })
            }
            val heaviest = mine.maxWithOrNull(compareBy<WorkoutSetEntity>({ it.weightKg }, { it.reps }))
            if (heaviest != null || records.isNotEmpty()) {
                col.addView(card(amber).apply {
                    heaviest?.let {
                        addView(tv("Nejtěžší série", 12.5f, dark, alpha = 0.65f))
                        addView(tv("${LogSetSheet.kg(it.weightKg)} kg × ${it.reps}", 26f, dark, black = true))
                        addView(tv(ExerciseLibrary.byId(it.exerciseId)?.name ?: it.exerciseId, 13.5f, dark, alpha = 0.8f))
                    }
                    if (records.isNotEmpty()) {
                        addView(tv("Nové rekordy", 12.5f, dark, alpha = 0.65f).apply { setPadding(0, dp(14f), 0, dp(6f)) })
                        records.forEach { (name, s) ->
                            addView(pill("★ $name · 1RM ${LogSetSheet.kg(e1rm(s))} kg", mint, bold = true),
                                LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(6f) })
                        }
                    }
                })
            }

            heading("Cviky")
            byEx.forEach { (id, sets) ->
                val best = sets.maxByOrNull { e1rm(it) }
                col.addView(card(Color.parseColor("#FFFDF3"), stroke = true).apply {
                    addView(LinearLayout(requireContext()).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(tv(ExerciseLibrary.byId(id)?.name ?: id, 15f, dark, black = true), LinearLayout.LayoutParams(0, -2, 1f))
                        addView(tv("${sets.size} sérií", 12f, dark, alpha = 0.6f))
                    })
                    addView(ChipGroup(requireContext()).apply {
                        chipSpacingHorizontal = dp(6f); chipSpacingVertical = dp(6f); setPadding(0, dp(10f), 0, 0)
                        sets.forEach { addView(pill("${LogSetSheet.kg(it.weightKg)} × ${it.reps}", if (it === best) chipBest else chip, bold = it === best)) }
                    })
                })
            }
        }
        if (mine.isEmpty()) watchCard(data)

        col.addView(MaterialButton(requireContext()).apply {
            text = "Hotovo"; isAllCaps = false; textSize = 16f; cornerRadius = dp(28f)
            backgroundTintList = android.content.res.ColorStateList.valueOf(primary); setTextColor(cream)
            setOnClickListener { dismiss() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(56f)).apply { topMargin = dp(24f) }
        })
    }

    private fun watchCard(data: Data) {
        if (!data.hasKey && data.watch.isEmpty()) return
        if (data.watch.isEmpty()) {
            col.addView(card(sky).apply {
                addView(tv("Z hodinek", 12.5f, dark, alpha = 0.65f))
                addView(tv(if (waiting) "Čekám na hodinky…" else "Hodinky zatím nic neposlaly", 16f, dark, black = true).apply { setPadding(0, dp(2f), 0, 0) })
                addView(tv(if (waiting) "Zkouším to sám každou chvíli. Až se trénink nahraje, objeví se tady."
                           else "Objeví se při příští synchronizaci, aplikace ji dělá sama.", 13f, dark, alpha = 0.7f).apply { setPadding(0, dp(4f), 0, 0) })
            })
            return
        }
        data.watch.forEach { a ->
            col.addView(card(sky).apply {
                addView(tv("Z hodinek", 12.5f, dark, alpha = 0.65f).apply { setPadding(0, 0, 0, dp(8f)) })
                val cols = mutableListOf(stat("${a.durationSec / 60}", "min"))
                a.kcal?.let { cols += stat("$it", "kcal") }
                a.avgHr?.let { cols += stat("$it", "tep Ø") }
                a.maxHr?.let { cols += stat("$it", "tep max") }
                addView(statRow(*cols.toTypedArray()))
            })
        }
    }
}

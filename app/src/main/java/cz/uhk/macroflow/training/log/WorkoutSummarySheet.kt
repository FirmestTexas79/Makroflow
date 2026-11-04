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
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.WorkoutSetEntity
import cz.uhk.macroflow.training.exercises.ExerciseLibrary
import cz.uhk.macroflow.wearable.ActivityGroups
import cz.uhk.macroflow.wearable.GarminKeyStore
import cz.uhk.macroflow.wearable.SportGroup
import cz.uhk.macroflow.wearable.WearableActivityEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Výpis po dokončení tréninku (podobně jako u hodinek po dosportování): délka, počet sérií a objem,
 * nejtěžší série, nové rekordy, rozpis cviků a – když je Garmin už poslal – data z hodinek.
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
    private val dark get() = ContextCompat.getColor(requireContext(), R.color.brand_dark)
    private val cream get() = ContextCompat.getColor(requireContext(), R.color.brand_cream)
    private val primary get() = ContextCompat.getColor(requireContext(), R.color.brand_primary)
    private val warm get() = ContextCompat.getColor(requireContext(), R.color.brand_accent_warm)
    private val tint get() = ContextCompat.getColor(requireContext(), R.color.brand_primary_alpha10)
    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val date = requireArguments().getString("date") ?: LocalDate.now().toString()
            val data = withContext(Dispatchers.IO) {
                runCatching {
                    val db = AppDatabase.getDatabase(app)
                    Data(db.workoutDao().getAllSync(),
                        db.wearableDao().getActivitiesSync(date).filter { ActivityGroups.of(it.typeKey) == SportGroup.STRENGTH },
                        GarminKeyStore.hasKey(app))
                }.getOrElse { android.util.Log.e("WorkoutSummary", "load failed", it); null }
            }
            if (data == null) { dismiss(); return@launch }
            build(data)
        }
    }

    private fun tv(text: String, sp: Float, color: Int, black: Boolean = false, alpha: Float = 1f) = TextView(requireContext()).apply {
        this.text = text; textSize = sp; setTextColor(color); this.alpha = alpha
        if (black) typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }

    private fun round(color: Int, r: Float) = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(color) }

    private fun tile(value: String, label: String) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; background = round(tint, 20f); setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
        addView(tv(value, 24f, dark, black = true)); addView(tv(label, 12f, dark, alpha = 0.7f))
    }

    private fun row(a: View, b: View) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(a, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5f) })
        addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5f) })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) }
    }

    private fun heading(s: String) = col.addView(tv(s, 17f, dark, black = true).apply { setPadding(dp(4f), dp(24f), 0, dp(6f)) })

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

        // Hrdina: délka tréninku
        val minutes = if (mine.isEmpty()) 0 else {
            val start = if (startedAt > 0) startedAt else mine.first().createdAt
            val end = if (startedAt > 0) maxOf(System.currentTimeMillis(), mine.last().createdAt) else mine.last().createdAt
            ((end - start) / 60_000).toInt().coerceAtLeast(0)
        }
        // Hrdina: vlevo čas a název, vpravo postava s rozsvícenými svaly, které dnes dostaly nejvíc práce
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
            col.addView(row(tile("${mine.size}", "sérií"), tile("${byEx.size}", "cviků")))
            col.addView(row(tile("${mine.sumOf { it.reps }}", "opakování"), tile("${LogSetSheet.kg(volume)} kg", "celkový objem")))

            // Zlatá chvilka: nejtěžší série a nové rekordy podle odhadu 1RM
            val before = data.all.filter { it.date < today.toString() }.groupBy { it.exerciseId }
            val records = byEx.mapNotNull { (id, sets) ->
                val prev = before[id]?.maxOfOrNull { e1rm(it) } ?: return@mapNotNull null
                val best = sets.maxByOrNull { e1rm(it) } ?: return@mapNotNull null
                if (e1rm(best) > prev * 1.01) (ExerciseLibrary.byId(id)?.name ?: id) to best else null
            }
            // Rekord hned po dokončení tréninku se oznámí i na Dashboardu (ne při pozdějším otevření výpisu)
            if (records.isNotEmpty() && args.getBoolean("fresh")) {
                cz.uhk.macroflow.learning.LearningData.setPendingPr(requireContext(),
                    records.joinToString(", ") { (name, s) -> "$name (1RM ≈ ${LogSetSheet.kg(e1rm(s))} kg)" })
            }
            val heaviest = mine.maxWithOrNull(compareBy<WorkoutSetEntity>({ it.weightKg }, { it.reps }))
            if (heaviest != null || records.isNotEmpty()) {
                heading("Vrchol tréninku")
                heaviest?.let { col.addView(tv("Nejtěžší série: ${ExerciseLibrary.byId(it.exerciseId)?.name ?: it.exerciseId}, ${LogSetSheet.kg(it.weightKg)} kg × ${it.reps}", 14f, dark)) }
                records.forEach { (name, s) ->
                    col.addView(tv("★ Nový rekord: $name (1RM ≈ ${LogSetSheet.kg(e1rm(s))} kg)", 14f, primary, black = true).apply { setPadding(0, dp(6f), 0, 0) })
                }
            }

            heading("Cviky")
            byEx.forEach { (id, sets) ->
                col.addView(tv(ExerciseLibrary.byId(id)?.name ?: id, 15f, dark, black = true).apply { setPadding(dp(4f), dp(8f), 0, 0) })
                col.addView(tv(sets.joinToString("  ·  ") { "${LogSetSheet.kg(it.weightKg)} kg × ${it.reps}" }, 13f, dark, alpha = 0.75f).apply { setPadding(dp(4f), dp(2f), 0, dp(4f)) })
            }
        }

        // Hodinky
        if (data.watch.isNotEmpty() || data.hasKey) {
            heading("Z hodinek")
            if (data.watch.isEmpty()) {
                col.addView(tv("Hodinky zatím žádný silový trénink neposlaly. Objeví se po synchronizaci s Garmin Connect.", 13f, dark, alpha = 0.7f))
            } else data.watch.forEach { a ->
                col.addView(tv("${a.durationSec / 60} min" + (a.kcal?.let { " · $it kcal" } ?: ""), 15f, dark, black = true))
                if (a.avgHr != null || a.maxHr != null)
                    col.addView(tv("Tep: Ø ${a.avgHr ?: "–"}, max ${a.maxHr ?: "–"}", 13f, dark, alpha = 0.75f))
            }
        }

        col.addView(MaterialButton(requireContext()).apply {
            text = "Hotovo"; isAllCaps = false; textSize = 16f; cornerRadius = dp(28f)
            backgroundTintList = android.content.res.ColorStateList.valueOf(primary); setTextColor(cream)
            setOnClickListener { dismiss() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(56f)).apply { topMargin = dp(24f) }
        })
    }
}

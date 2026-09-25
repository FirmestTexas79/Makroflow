package cz.uhk.macroflow.nutrition

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import cz.uhk.macroflow.R
import cz.uhk.macroflow.dashboard.MacroFlowEngine
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.data.FoodAIResult
import cz.uhk.macroflow.data.GeminiRepository
import cz.uhk.macroflow.data.SnackEntity
import cz.uhk.macroflow.energy.FoodEnergy
import cz.uhk.macroflow.nutrition.SnackCatalog.Row
import cz.uhk.macroflow.nutrition.SnackCatalog.Timing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Snacky – špajzka potravin (docs/adr/0021).
 *
 *  - seznam jako RecyclerView po skupinách podle zdroje energie (Oblíbené, Bílkoviny, Sacharidy, Tuky),
 *  - „+“ v řádku přidá porci hned (se Zpět), klepnutí = vlastní množství, podržení = nabídka,
 *  - jedno tlačítko „Přidat“ s nabídkou (ručně, čárový kód, fotka, složit jídlo, swipe),
 *  - hlavička ukazuje dnešní zůstatek kalorií a bílkovin.
 * Logika bez Androidu je v [SnackCatalog], zápis do deníku v [FoodLog].
 */
class SnackFragment : Fragment() {

    private val db by lazy { AppDatabase.getDatabase(requireContext()) }

    private lateinit var root: View
    private lateinit var rv: RecyclerView
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var etSearch: EditText
    private lateinit var tvSub: TextView
    private lateinit var emptyView: View
    private val adapter = RowsAdapter()

    private var timing = Timing.ALL
    private var query = ""
    private var snacks: List<SnackEntity> = emptyList()
    private var usage: Map<String, Int> = emptyMap()

    /** Předvyplnění formuláře po návratu z fotoaparátu (AI). */
    private var photoUri: android.net.Uri? = null

    // ── Fotoaparát pro AI odhad ─────────────────────────────────────────────

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
        else toast("Fotoaparát není povolen – povol ho v nastavení telefonu.")
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = photoUri ?: return@registerForActivityResult toast("Nepodařilo se načíst fotku.")
        try {
            val bitmap = requireContext().contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
            if (bitmap != null) analyzeImageWithAi(resize(bitmap, 1024)) else toast("Nepodařilo se načíst fotku.")
        } catch (e: Exception) {
            toast("Chyba při načítání fotky: ${e.message}")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        root = inflater.inflate(R.layout.fragment_snack, container, false)
        rv = root.findViewById(R.id.rvSnacks)
        fab = root.findViewById(R.id.fabAddSnack)
        etSearch = root.findViewById(R.id.etSnackSearch)
        tvSub = root.findViewById(R.id.tvSnackSub)
        emptyView = root.findViewById(R.id.snackEmpty)

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        rv.itemAnimator = null   // filtrování po každém písmenu – bez přeskupovacích animací
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 8 && etSearch.hasFocus()) hideKeyboard()
                if (dy > 8 && fab.isExtended) fab.shrink() else if (dy < -8 && !fab.isExtended) fab.extend()
            }
        })

        val ivClear = root.findViewById<ImageView>(R.id.ivClearSearch)
        ivClear.setOnClickListener { etSearch.setText("") }
        etSearch.setOnEditorActionListener { _, _, _ -> hideKeyboard(); true }
        etSearch.addTextChangedListener { text ->
            query = text?.toString()?.trim().orEmpty()
            ivClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
            render()
        }

        root.findViewById<MaterialButtonToggleGroup>(R.id.toggleSnackTiming).addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            timing = when (id) { R.id.btnPreWorkout -> Timing.PRE; R.id.btnPostWorkout -> Timing.POST; else -> Timing.ALL }
            render(scrollToTop = true)
        }

        fab.setOnClickListener { showAddMenu() }
        root.findViewById<View>(R.id.btnEmptyCreate).setOnClickListener {
            showEditSheet(existing = null, prefill = Prefill(name = query.replaceFirstChar { it.uppercase() }))
        }

        observe()
        return root
    }

    // ── Data ────────────────────────────────────────────────────────────────

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            val dao = db.snackDao()
            if (withContext(Dispatchers.IO) { dao.getAllSnacks().first() }.isEmpty()) {
                withContext(Dispatchers.IO) { SnackSeed.DEFAULTS.forEach { dao.insertSnack(it) } }
            }
            combine(dao.getAllSnacksSmart(System.currentTimeMillis()), dao.getAllUsage()) { s, u ->
                s to u.associate { it.snackName to it.usageCount }
            }.collect { (s, u) ->
                snacks = s; usage = u
                render()
            }
        }
        // Dnešní zůstatek v hlavičce – přepočítá se s každým zápisem
        viewLifecycleOwner.lifecycleScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            db.consumedSnackDao().getConsumedByDate(today).collect { consumed ->
                val ctx = context?.applicationContext ?: return@collect
                val status = withContext(Dispatchers.IO) { MacroFlowEngine.calculateDailyStatusForDate(ctx, Date(), consumed) }
                val left = status.caloriesLeft.roundToInt()
                val protein = status.proteinLeft.roundToInt()
                fun n(v: Int) = String.format(Locale("cs"), "%,d", v).replace('\u00A0', ' ').replace('\u202F', ' ')
                tvSub.text = when {
                    left >= 0 -> "Dnes zbývá ${n(left)} kcal" + if (protein > 0) " · $protein g bílkovin" else " · bílkoviny splněné ✓"
                    else -> "Dnes ${n(abs(left))} kcal nad cílem" + if (protein > 0) " · chybí $protein g bílkovin" else ""
                }
            }
        }
    }

    private fun render(scrollToTop: Boolean = false) {
        val rows = SnackCatalog.rows(snacks, query, timing, usage)
        adapter.submitList(rows) { if (scrollToTop) rv.scrollToPosition(0) }
        val empty = rows.isEmpty() && snacks.isNotEmpty()
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        if (empty) {
            root.findViewById<TextView>(R.id.tvSnackEmpty).text =
                if (query.isNotEmpty()) "„$query“ ve špajzce není." else "V téhle kategorii zatím nic není."
            root.findViewById<TextView>(R.id.btnEmptyCreate).text =
                if (query.isNotEmpty()) "Vytvořit „$query“" else "Vytvořit potravinu"
        }
    }

    // ── Seznam ──────────────────────────────────────────────────────────────

    private inner class RowsAdapter : ListAdapter<Row, RecyclerView.ViewHolder>(object : DiffUtil.ItemCallback<Row>() {
        override fun areItemsTheSame(a: Row, b: Row) = when {
            a is Row.Header && b is Row.Header -> a.group == b.group
            a is Row.Item && b is Row.Item -> a.snack.id == b.snack.id && a.group == b.group
            else -> false
        }
        override fun areContentsTheSame(a: Row, b: Row) = a == b
    }) {
        override fun getItemViewType(position: Int) = if (getItem(position) is Row.Header) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val layout = if (viewType == 0) R.layout.item_snack_header else R.layout.item_snack_row
            return object : RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false)) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = getItem(position)) {
                is Row.Header -> (holder.itemView as TextView).text = "${row.group.title} · ${row.count}"
                is Row.Item -> SnackRowBinder.bind(
                    holder.itemView, row.snack,
                    onClick = { showConsumeSheet(it) },
                    onQuickAdd = { quickAdd(it) },
                    onLongClick = { holder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); showActions(it) }
                )
            }
        }
    }

    // ── Zápis do deníku ─────────────────────────────────────────────────────

    /** „+“ v řádku: přidá výchozí porci hned, s možností Zpět. */
    private fun quickAdd(snack: SnackEntity) {
        val scaled = SnackCatalog.scale(snack, SnackCatalog.portionGrams(snack.weight))
        logAndConfirm(snack.name, scaled)
    }

    private fun logAndConfirm(name: String, scaled: SnackCatalog.Scaled) {
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val ts = FoodLog.add(ctx, name, scaled)
            root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            val label = "${SnackRowBinder.grams(scaled.grams)} g"
            Snackbar.make(root, "$name ($label) · ${scaled.kcal} kcal přidáno", Snackbar.LENGTH_LONG)
                .setAnchorView(fab)
                .setBackgroundTint(ContextCompat.getColor(ctx, R.color.brand_dark))
                .setTextColor(ContextCompat.getColor(ctx, R.color.brand_cream))
                .setActionTextColor(ContextCompat.getColor(ctx, R.color.brand_accent_warm))
                .setAction("ZPĚT") { viewLifecycleOwner.lifecycleScope.launch { FoodLog.remove(ctx, ts) } }
                .show()
        }
    }

    // ── Detail porce ────────────────────────────────────────────────────────

    private fun showConsumeSheet(snack: SnackEntity) {
        val ctx = requireContext()
        val dialog = BottomSheetDialog(ctx)
        val v = layoutInflater.inflate(R.layout.sheet_snack_consume, null)
        dialog.setContentView(v)
        transparentSheet(dialog)

        val base = SnackCatalog.portionGrams(snack.weight)
        val unit = SnackCatalog.unit(snack.weight)
        val group = SnackCatalog.groupOf(snack)
        v.findViewById<TextView>(R.id.tvConsumeGroup).text = group.title
        v.findViewById<TextView>(R.id.tvConsumeName).text = snack.name
        v.findViewById<TextView>(R.id.tvConsumePortion).text =
            "Porce ${SnackRowBinder.grams(base)} $unit · ${SnackCatalog.scale(snack, base).kcal} kcal"
        v.findViewById<TextView>(R.id.tvConsumeUnit).text = unit

        val et = v.findViewById<EditText>(R.id.etConsumeWeight)
        val donut = v.findViewById<MacroDonutView>(R.id.donutConsume).apply {
            trackColor = Color.parseColor("#26FEFAE0"); centerTextColor = ContextCompat.getColor(ctx, R.color.brand_cream)
            ringWidthDp = 8f
        }
        val macroBox = v.findViewById<LinearLayout>(R.id.llConsumeMacros)
        val chips = v.findViewById<ChipGroup>(R.id.chipsPortion)
        val confirm = v.findViewById<TextView>(R.id.btnConsumeConfirm)

        // Řádky maker (název, gramy, pruh vůči dnešku, procenta)
        data class MacroRow(val label: TextView, val bar: IntakeBarView, val pct: TextView)
        val onDark = listOf(Color.parseColor("#B5C47A"), Color.parseColor("#E9B072"), Color.parseColor("#E8955A"), Color.parseColor("#D9D3B0"))
        val names = listOf("Bílkoviny", "Sacharidy", "Tuky", "Vláknina")
        val rows = names.mapIndexed { i, n ->
            val wrap = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, if (i == 0) 0 else dp(7), 0, 0) }
            val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val label = TextView(ctx).apply { textSize = 12.5f; setTextColor(onDark[i]); layoutParams = LinearLayout.LayoutParams(0, -2, 1f); text = n }
            val pct = TextView(ctx).apply { textSize = 11f; setTextColor(Color.parseColor("#B3FEFAE0")) }
            top.addView(label); top.addView(pct)
            val bar = IntakeBarView(ctx).apply { color = onDark[i]; layoutParams = LinearLayout.LayoutParams(-1, dp(6)).apply { topMargin = dp(3) } }
            wrap.addView(top); wrap.addView(bar)
            macroBox.addView(wrap)
            MacroRow(label, bar, pct)
        }

        var target: cz.uhk.macroflow.dashboard.DailyStatus? = null
        fun grams() = et.text.toString().replace(',', '.').toFloatOrNull()?.takeIf { it > 0f }

        fun refresh() {
            val g = grams() ?: 0f
            val s = SnackCatalog.scale(snack, g)
            donut.setMacros(s.p, s.s, s.t)
            donut.centerText = s.kcal.toString(); donut.centerSub = "kcal"
            donut.invalidate()
            val values = listOf(s.p, s.s, s.t, s.fiber)
            val st = target
            val targets = st?.let { listOf(it.target.protein, it.target.carbs, it.target.fat, it.target.fiber) }
            val eaten = st?.let { listOf(it.eatenP, it.eatenS, it.eatenT, it.eatenFiber) }
            rows.forEachIndexed { i, r ->
                r.label.text = "${names[i]}  ${SnackRowBinder.grams(values[i])} g"
                val tgt = targets?.get(i) ?: 0.0
                if (tgt > 0 && eaten != null) {
                    val already = (eaten[i] / tgt).toFloat()
                    val add = (values[i] / tgt).toFloat()
                    r.bar.set(already, add)
                    r.pct.text = "+${(add * 100).roundToInt()} % → ${((already + add) * 100).roundToInt()} %"
                } else { r.bar.set(0f, 0f); r.pct.text = "" }
            }
            confirm.isEnabled = g > 0f
            confirm.alpha = if (g > 0f) 1f else 0.5f
        }

        // Rychlé porce
        SnackCatalog.portionPresets(snack).forEach { p ->
            chips.addView(Chip(ctx).apply {
                text = "${p.label} · ${SnackRowBinder.grams(p.grams)}"
                isCheckable = true
                chipBackgroundColor = ContextCompat.getColorStateList(ctx, R.color.profile_toggle_bg)
                setTextColor(ContextCompat.getColorStateList(ctx, R.color.profile_toggle_text))
                chipStrokeColor = ContextCompat.getColorStateList(ctx, R.color.brand_primary_alpha30)
                chipStrokeWidth = dp(1).toFloat()
                isCheckedIconVisible = false
                tag = p.grams
                setOnClickListener { et.setText(SnackRowBinder.grams(p.grams).replace(',', '.')) }
            })
        }
        fun syncChips() {
            val g = grams()
            for (i in 0 until chips.childCount) {
                val c = chips.getChildAt(i) as Chip
                c.isChecked = g != null && abs((c.tag as Float) - g) < 0.01f
            }
        }

        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { refresh(); syncChips() }
        })
        val step = if (base <= 40f) 5f else 10f
        fun bump(delta: Float) {
            val g = ((grams() ?: 0f) + delta).coerceAtLeast(step)
            et.setText(SnackRowBinder.grams(g).replace(',', '.'))
        }
        v.findViewById<View>(R.id.btnConsumeMinus).setOnClickListener { bump(-step) }
        v.findViewById<View>(R.id.btnConsumePlus).setOnClickListener { bump(step) }

        et.setText(SnackRowBinder.grams(base).replace(',', '.'))

        viewLifecycleOwner.lifecycleScope.launch {
            val app = ctx.applicationContext
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            target = withContext(Dispatchers.IO) {
                MacroFlowEngine.calculateDailyStatusForDate(app, Date(), db.consumedSnackDao().getConsumedByDateSync(today))
            }
            refresh()
        }

        confirm.setOnClickListener {
            val g = grams() ?: return@setOnClickListener
            dialog.dismiss()
            logAndConfirm(snack.name, SnackCatalog.scale(snack, g))
        }
        dialog.show()
    }

    // ── Nabídka po podržení ─────────────────────────────────────────────────

    private fun showActions(snack: SnackEntity) {
        val dialog = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.sheet_snack_actions, null)
        dialog.setContentView(v)
        transparentSheet(dialog)
        v.findViewById<MacroDonutView>(R.id.donutActions).setMacros(snack.p, snack.s, snack.t)
        v.findViewById<TextView>(R.id.tvActionsName).text = snack.name
        v.findViewById<TextView>(R.id.tvActionsMeta).text = SnackRowBinder.meta(snack)
        v.findViewById<TextView>(R.id.actMoveText).text =
            if (snack.isPre) "Přesunout do „Po tréninku“" else "Přesunout do „Před tréninkem“"

        v.findViewById<View>(R.id.actConsume).setOnClickListener { dialog.dismiss(); showConsumeSheet(snack) }
        v.findViewById<View>(R.id.actEdit).setOnClickListener { dialog.dismiss(); showEditSheet(existing = snack, prefill = null) }
        v.findViewById<View>(R.id.actMove).setOnClickListener {
            dialog.dismiss()
            saveSnack(snack.copy(isPre = !snack.isPre), isNew = false)
            toast(if (snack.isPre) "Přesunuto do „Po tréninku“" else "Přesunuto do „Před tréninkem“")
        }
        v.findViewById<View>(R.id.actDelete).setOnClickListener { dialog.dismiss(); deleteWithUndo(snack) }
        dialog.show()
    }

    private fun deleteWithUndo(snack: SnackEntity) {
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.snackDao().deleteSnack(snack)
                runCatching { FirebaseRepository.deleteCustomSnack(snack.id) }
            }
            Snackbar.make(root, "${snack.name} odebráno ze špajzky", Snackbar.LENGTH_LONG)
                .setAnchorView(fab)
                .setBackgroundTint(ContextCompat.getColor(ctx, R.color.brand_dark))
                .setTextColor(ContextCompat.getColor(ctx, R.color.brand_cream))
                .setActionTextColor(ContextCompat.getColor(ctx, R.color.brand_accent_warm))
                .setAction("ZPĚT") { saveSnack(snack, isNew = false) }
                .show()
        }
    }

    // ── Nabídka Přidat ──────────────────────────────────────────────────────

    // ── Zopakovat jídlo (docs/adr/0027) ─────────────────────────────────────

    private fun showRepeat() {
        MealRepeatSheet(this) { label, kcal, timestamps ->
            if (!isAdded || timestamps.isEmpty()) return@MealRepeatSheet
            val ctx = requireContext().applicationContext
            root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            Snackbar.make(root, "$label · $kcal kcal přidáno", Snackbar.LENGTH_LONG)
                .setAnchorView(fab)
                .setBackgroundTint(ContextCompat.getColor(ctx, R.color.brand_dark))
                .setTextColor(ContextCompat.getColor(ctx, R.color.brand_cream))
                .setActionTextColor(ContextCompat.getColor(ctx, R.color.brand_accent_warm))
                .setAction("ZPĚT") { viewLifecycleOwner.lifecycleScope.launch { MealRepeatRepository.undo(ctx, timestamps) } }
                .show()
        }.show()
    }

    private fun showAddMenu() {
        val dialog = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.sheet_snack_add, null)
        dialog.setContentView(v)
        transparentSheet(dialog)
        v.findViewById<View>(R.id.addManual).setOnClickListener { dialog.dismiss(); showEditSheet(null, null) }
        v.findViewById<View>(R.id.addBarcode).setOnClickListener { dialog.dismiss(); scanBarcode { showEditSheet(null, it) } }
        v.findViewById<View>(R.id.addPhoto).setOnClickListener { dialog.dismiss(); startPhoto() }
        v.findViewById<View>(R.id.addMeal).setOnClickListener {
            dialog.dismiss(); MealBuilderSheet(timing == Timing.PRE).show(parentFragmentManager, "MealBuilder")
        }
        v.findViewById<View>(R.id.addSwipe).setOnClickListener { dialog.dismiss(); FoodSwipeDialog().show(parentFragmentManager, "FoodSwipe") }
        v.findViewById<View>(R.id.addRepeat).setOnClickListener { dialog.dismiss(); showRepeat() }
        dialog.show()
    }

    // ── Formulář potraviny ──────────────────────────────────────────────────

    /**
     * Předvyplnění z čárového kódu nebo AI. [perGram] jsou hodnoty na 1 g – změna porce pak
     * makra přepočítá; [labelKjPerGram] = energie z etikety (přesnější než výpočet z maker).
     */
    data class Prefill(
        val name: String = "",
        val grams: Float? = null,
        val p: Float = 0f, val s: Float = 0f, val t: Float = 0f, val fiber: Float = 0f,
        val perGram: FloatArray? = null,
        val labelKjPerGram: Float? = null,
        val isPre: Boolean? = null
    )

    private fun showEditSheet(existing: SnackEntity?, prefill: Prefill?) {
        val ctx = requireContext()
        val dialog = BottomSheetDialog(ctx)
        val v = layoutInflater.inflate(R.layout.sheet_snack_edit, null)
        dialog.setContentView(v)
        transparentSheet(dialog, expanded = true)

        val etName = v.findViewById<TextInputEditText>(R.id.etName)
        val etPortion = v.findViewById<TextInputEditText>(R.id.etPortion)
        val etP = v.findViewById<TextInputEditText>(R.id.etProtein)
        val etS = v.findViewById<TextInputEditText>(R.id.etCarbs)
        val etT = v.findViewById<TextInputEditText>(R.id.etFat)
        val etF = v.findViewById<TextInputEditText>(R.id.etFiber)
        val tvKcal = v.findViewById<TextView>(R.id.tvEditKcal)
        val donut = v.findViewById<MacroDonutView>(R.id.donutEdit)
        val toggle = v.findViewById<MaterialButtonToggleGroup>(R.id.toggleEditTiming)
        val unit = existing?.let { SnackCatalog.unit(it.weight) } ?: "g"
        v.findViewById<TextInputLayout>(R.id.tilPortion).suffixText = unit

        var perGram: FloatArray? = prefill?.perGram
        var labelKjPerGram: Float? = prefill?.labelKjPerGram ?: existing?.takeIf { it.energyKj > 0f }?.let { it.energyKj / SnackCatalog.portionGrams(it.weight) }
        var programmatic = false

        fun num(e: EditText) = e.text.toString().replace(',', '.').toFloatOrNull() ?: 0f
        fun fmt(f: Float) = SnackRowBinder.grams(f).replace(',', '.')
        fun setAll(p: Float, s: Float, t: Float, f: Float) {
            programmatic = true
            etP.setText(fmt(p)); etS.setText(fmt(s)); etT.setText(fmt(t)); etF.setText(fmt(f))
            programmatic = false
        }

        fun energyKj(): Float {
            val g = num(etPortion)
            val label = labelKjPerGram
            return if (label != null && g > 0f) label * g else FoodEnergy.kj(num(etP), num(etS), num(etT), num(etF))
        }

        fun refresh() {
            val p = num(etP); val s = num(etS); val t = num(etT)
            donut.setMacros(p, s, t)
            val kcal = (energyKj() / FoodEnergy.KJ_PER_KCAL).roundToInt()
            val g = num(etPortion)
            tvKcal.text = buildString {
                append("≈ $kcal kcal na porci")
                if (g > 0f) append(" · ${(kcal * 100f / g).roundToInt()} kcal / 100 $unit")
                if (labelKjPerGram != null) append("\nEnergie podle etikety")
            }
        }

        // Makra ručně = už neplatí přepočet z etikety / na gram
        listOf(etP, etS, etT, etF).forEach { e ->
            e.addTextChangedListener { if (!programmatic) { perGram = null; labelKjPerGram = null }; refresh() }
        }
        etPortion.addTextChangedListener {
            val g = num(etPortion)
            perGram?.let { pg -> if (g > 0f) setAll(pg[0] * g, pg[1] * g, pg[2] * g, pg[3] * g) }
            refresh()
        }

        fun apply(pf: Prefill) {
            perGram = pf.perGram
            labelKjPerGram = pf.labelKjPerGram
            if (pf.name.isNotBlank()) etName.setText(pf.name)
            programmatic = true
            pf.grams?.let { etPortion.setText(fmt(it)) }
            programmatic = false
            setAll(pf.p, pf.s, pf.t, pf.fiber)
            pf.isPre?.let { toggle.check(if (it) R.id.btnEditPre else R.id.btnEditPost) }
            refresh()
        }

        if (existing != null) {
            v.findViewById<TextView>(R.id.tvEditTitle).text = "Upravit potravinu"
            v.findViewById<View>(R.id.llEditSources).visibility = View.GONE
            etName.setText(existing.name)
            programmatic = true
            etPortion.setText(fmt(SnackCatalog.portionGrams(existing.weight)))
            programmatic = false
            setAll(existing.p, existing.s, existing.t, existing.fiber)
            toggle.check(if (existing.isPre) R.id.btnEditPre else R.id.btnEditPost)
        } else {
            toggle.check(if (timing == Timing.PRE) R.id.btnEditPre else R.id.btnEditPost)
            prefill?.let { apply(it) }
        }
        refresh()

        v.findViewById<View>(R.id.btnEditBarcode).setOnClickListener { scanBarcode { apply(it) } }
        v.findViewById<View>(R.id.btnEditPhoto).setOnClickListener { dialog.dismiss(); startPhoto() }

        v.findViewById<View>(R.id.btnEditSave).setOnClickListener {
            val name = etName.text.toString().trim()
            val grams = num(etPortion)
            val tilName = v.findViewById<TextInputLayout>(R.id.tilName)
            val tilPortion = v.findViewById<TextInputLayout>(R.id.tilPortion)
            tilName.error = if (name.isEmpty()) "Zadej název" else null
            tilPortion.error = if (grams <= 0f) "Zadej porci" else null
            if (name.isEmpty() || grams <= 0f) return@setOnClickListener
            val entity = SnackEntity(
                id = existing?.id ?: 0,
                name = name,
                weight = "${fmt(grams)}$unit",
                p = num(etP), s = num(etS), t = num(etT), fiber = num(etF),
                isPre = toggle.checkedButtonId == R.id.btnEditPre,
                energyKj = energyKj()
            )
            dialog.dismiss()
            saveSnack(entity, isNew = existing == null)
            if (existing == null) toast("$name je ve špajzce")
        }
        dialog.show()
    }

    /** Uloží potravinu lokálně i do cloudu (nová dostane ID z databáze). */
    private fun saveSnack(snack: SnackEntity, isNew: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = db.snackDao()
            val saved = if (isNew) snack.copy(id = dao.insertSnackGetId(snack).toInt())
                else snack.also { dao.insertSnack(it) }   // REPLACE: úprava i obnovení po „Zpět“
            runCatching { FirebaseRepository.uploadCustomSnack(saved) }
        }
    }

    // ── Čárový kód a fotka ──────────────────────────────────────────────────

    private fun scanBarcode(onFound: (Prefill) -> Unit) {
        GmsBarcodeScanning.getClient(requireContext()).startScan().addOnSuccessListener { barcode ->
            val code = barcode.rawValue ?: return@addOnSuccessListener
            viewLifecycleOwner.lifecycleScope.launch {
                val product = BarcodeProductLookup.lookup(db, code)
                if (product == null) { toast("Produkt $code se nepodařilo dohledat"); return@launch }
                val pg = floatArrayOf(product.proteins100g / 100f, product.carbs100g / 100f, product.fat100g / 100f, product.fiber100g / 100f)
                onFound(Prefill(
                    name = product.name, grams = 100f,
                    p = product.proteins100g, s = product.carbs100g, t = product.fat100g, fiber = product.fiber100g,
                    perGram = pg,
                    labelKjPerGram = product.energyKj100g.takeIf { it > 0f }?.div(100f)
                ))
            }
        }
    }

    private fun startPhoto() {
        when {
            requireContext().checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> launchCamera()
            else -> requestCameraPermission.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        try {
            val file = java.io.File.createTempFile("food_${System.currentTimeMillis()}", ".jpg", requireContext().cacheDir)
            val uri = androidx.core.content.FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            photoUri = uri
            takePhotoLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri))
        } catch (e: Exception) {
            toast("Nepodařilo se spustit kameru: ${e.message}")
        }
    }

    private fun analyzeImageWithAi(bitmap: Bitmap) {
        val progress = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(layoutInflater.inflate(R.layout.layout_ai_progress, null))
            .setCancelable(false).create()
        progress.show()
        viewLifecycleOwner.lifecycleScope.launch {
            val result: FoodAIResult? = GeminiRepository.analyzeFood(bitmap)
            progress.dismiss()
            if (result == null) {
                AlertDialog.Builder(requireContext())
                    .setTitle("AI se zamyslela…")
                    .setMessage("Z fotky se nepodařilo určit nutriční hodnoty. Zkus lepší světlo a vyfotit jídlo zblízka.")
                    .setPositiveButton("Zkusit znovu") { _, _ -> startPhoto() }
                    .setNegativeButton("Zrušit", null)
                    .show()
                return@launch
            }
            val grams = SnackCatalog.portionGrams(result.weight)
            showEditSheet(null, Prefill(
                name = result.name, grams = grams,
                p = result.p, s = result.s, t = result.t, fiber = result.fiber,
                perGram = floatArrayOf(result.p / grams, result.s / grams, result.t / grams, result.fiber / grams),
                labelKjPerGram = null,
                isPre = result.isPre
            ))
        }
    }

    private fun resize(bm: Bitmap, newWidth: Int): Bitmap {
        if (bm.width <= newWidth) return bm
        val scale = newWidth.toFloat() / bm.width
        return Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, Matrix().apply { postScale(scale, scale) }, true)
    }

    // ── Pomocné ─────────────────────────────────────────────────────────────

    /** Panel s vlastním zaobleným pozadím (bez bílého rámečku výchozího sheetu). */
    private fun transparentSheet(dialog: BottomSheetDialog, expanded: Boolean = true) {
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
            if (expanded) { dialog.behavior.skipCollapsed = true; dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED }
        }
    }

    private fun hideKeyboard() {
        etSearch.clearFocus()
        (requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(etSearch.windowToken, 0)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private fun toast(msg: String) { context?.let { Toast.makeText(it, msg, Toast.LENGTH_SHORT).show() } }
}

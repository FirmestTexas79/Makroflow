package cz.uhk.macroflow.nutrition

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.ConsumedSnackEntity
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.data.MealTemplateEntity
import cz.uhk.macroflow.nutrition.MealRepeat.Item
import cz.uhk.macroflow.nutrition.MealRepeat.Kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** Data pro „Zopakovat jídlo“ – převod mezi záznamy a [MealRepeat.Item], zápis a šablony. */
object MealRepeatRepository {

    private fun db(ctx: Context) = AppDatabase.getDatabase(ctx)

    fun toItem(e: ConsumedSnackEntity) = Item(e.name, e.p, e.s, e.t, e.calories, e.energyKj, e.fiber, e.time)

    suspend fun itemsFor(ctx: Context, date: LocalDate): List<Item> = withContext(Dispatchers.IO) {
        db(ctx).consumedSnackDao().getConsumedByDateSync(date.toString()).map(::toItem)
    }

    /** Zapíše položky na dnešek; vrací timestampy (pro „Zpět“). */
    suspend fun log(ctx: Context, items: List<Item>, keepTimes: Boolean): List<Long> = withContext(Dispatchers.IO) {
        val now = LocalTime.now()
        val nowTime = String.format(Locale.US, "%02d:%02d", now.hour, now.minute)
        val base = System.currentTimeMillis()
        MealRepeat.relog(items, nowTime, keepTimes).mapIndexed { i, it ->
            val e = ConsumedSnackEntity(
                timestamp = base + i, date = LocalDate.now().toString(), time = it.time, name = it.name,
                p = it.p, s = it.s, t = it.t, calories = it.calories, energyKj = it.energyKj, fiber = it.fiber
            )
            FoodLog.insert(ctx, e)
            e.timestamp
        }
    }

    suspend fun undo(ctx: Context, timestamps: List<Long>) = timestamps.forEach { FoodLog.remove(ctx, it) }

    suspend fun templates(ctx: Context): List<MealTemplateEntity> = withContext(Dispatchers.IO) {
        db(ctx).mealTemplateDao().allSync().sortedWith(compareByDescending<MealTemplateEntity> { it.lastUsedAt }.thenByDescending { it.createdAt })
    }

    suspend fun saveTemplate(ctx: Context, name: String, kind: Kind, items: List<Item>) = withContext(Dispatchers.IO) {
        val t = MealTemplateEntity(createdAt = System.currentTimeMillis(), name = name, kind = kind.name, items = MealRepeat.encode(items))
        db(ctx).mealTemplateDao().upsert(t)
        runCatching { FirebaseRepository.uploadMealTemplate(t) }
    }

    suspend fun deleteTemplate(ctx: Context, t: MealTemplateEntity) = withContext(Dispatchers.IO) {
        db(ctx).mealTemplateDao().delete(t.createdAt)
        runCatching { FirebaseRepository.deleteMealTemplate(t.createdAt) }
    }

    suspend fun markUsed(ctx: Context, t: MealTemplateEntity) = withContext(Dispatchers.IO) {
        val used = t.copy(lastUsedAt = System.currentTimeMillis())
        db(ctx).mealTemplateDao().upsert(used)
        runCatching { FirebaseRepository.uploadMealTemplate(used) }
    }
}

/**
 * Panel „Zopakovat jídlo“ (docs/adr/0027): uložené šablony a jídla za posledních 7 dní.
 * Klepnutí přidá jídlo (nebo celý den) na dnešek, záložka uloží šablonu.
 * [onLogged] dostane popis a timestampy nových záznamů (Snackbar se „Zpět“).
 */
class MealRepeatSheet(
    private val fragment: Fragment,
    private val onLogged: (label: String, kcal: Int, timestamps: List<Long>) -> Unit
) {
    private val ctx: Context get() = fragment.requireContext()
    private val app: Context get() = ctx.applicationContext
    private lateinit var dialog: BottomSheetDialog
    private lateinit var content: LinearLayout

    fun show() {
        dialog = BottomSheetDialog(ctx)
        val v = LayoutInflater.from(ctx).inflate(R.layout.sheet_meal_repeat, null)
        content = v.findViewById(R.id.llRepeatContent)
        dialog.setContentView(v)
        dialog.setOnShowListener {
            dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.show()
        reload()
    }

    private fun reload() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val templates = MealRepeatRepository.templates(app)
            val today = LocalDate.now()
            val days = (0..7).map { today.minusDays(it.toLong()) }.map { it to MealRepeatRepository.itemsFor(app, it) }
            render(templates, days)
        }
    }

    private fun render(templates: List<MealTemplateEntity>, days: List<Pair<LocalDate, List<Item>>>) {
        content.removeAllViews()
        val inflater = LayoutInflater.from(ctx)

        // ── Šablony ──
        section(inflater, "ŠABLONY", null, null)
        if (templates.isEmpty()) {
            content.addView(TextView(ctx).apply {
                text = "Zatím žádné. Ulož si jídlo nebo celý den záložkou u něj níže – pak ho přidáš jedním klepnutím."
                textSize = 12.5f; alpha = 0.7f; setTextColor(ctx.getColor(R.color.brand_dark))
                setPadding(0, dp(4), 0, dp(4))
            })
        }
        templates.forEach { t ->
            val items = MealRepeat.decode(t.items)
            val isDay = t.kind == Kind.DAY.name
            row(inflater, t.name + if (isDay) "  · celý den" else "", items, onSave = null,
                onClick = { apply(t, items, isDay) },
                onLongClick = { confirmDelete(t) })
        }

        // ── Posledních 7 dní ──
        val nonEmpty = days.filter { it.second.isNotEmpty() }
        if (nonEmpty.isEmpty()) {
            content.addView(TextView(ctx).apply {
                text = "Za posledních 7 dní nic zapsaného."
                textSize = 12.5f; alpha = 0.7f; setTextColor(ctx.getColor(R.color.brand_dark))
                setPadding(0, dp(16), 0, 0)
            })
        }
        nonEmpty.forEach { (date, items) ->
            val isToday = date == LocalDate.now()
            val label = dayLabel(date) + " · ${MealRepeat.totalKcal(items)} kcal"
            section(inflater, label,
                onAddDay = if (isToday) null else { { logItems("${dayLabel(date)} (celý den)", items, keepTimes = true) } },
                onSaveDay = { askName(Kind.DAY, MealRepeat.defaultName(Kind.DAY, null, date.dayOfMonth, date.monthValue), items) })
            MealRepeat.meals(items).forEach { meal ->
                row(inflater, meal.label + if (meal.time.isNotEmpty()) " · ${meal.time}" else "", meal.items,
                    onSave = { askName(Kind.MEAL, MealRepeat.defaultName(Kind.MEAL, meal.label, date.dayOfMonth, date.monthValue), meal.items) },
                    onClick = { logItems(meal.label, meal.items, keepTimes = false) },
                    onLongClick = null)
            }
        }
    }

    private fun section(inflater: LayoutInflater, title: String, onAddDay: (() -> Unit)?, onSaveDay: (() -> Unit)?) {
        val h = inflater.inflate(R.layout.item_repeat_day, content, false)
        h.findViewById<TextView>(R.id.tvRepeatDay).text = title.uppercase(Locale("cs"))
        h.findViewById<View>(R.id.btnRepeatDayAdd).apply {
            visibility = if (onAddDay != null) View.VISIBLE else View.GONE
            setOnClickListener { onAddDay?.invoke() }
        }
        h.findViewById<View>(R.id.btnRepeatDaySave).apply {
            visibility = if (onSaveDay != null) View.VISIBLE else View.GONE
            setOnClickListener { onSaveDay?.invoke() }
        }
        content.addView(h)
    }

    private fun row(
        inflater: LayoutInflater, title: String, items: List<Item>,
        onSave: (() -> Unit)?, onClick: () -> Unit, onLongClick: (() -> Unit)?
    ) {
        val r = inflater.inflate(R.layout.item_repeat_meal, content, false)
        r.findViewById<TextView>(R.id.tvRepeatTitle).text = title
        r.findViewById<TextView>(R.id.tvRepeatItems).text = items.joinToString(", ") { it.name }
        r.findViewById<TextView>(R.id.tvRepeatStats).text = MealRepeat.describe(items)
        r.findViewById<View>(R.id.btnRepeatSave).apply {
            visibility = if (onSave != null) View.VISIBLE else View.GONE
            setOnClickListener { onSave?.invoke() }
        }
        r.setOnClickListener { onClick() }
        onLongClick?.let { l -> r.setOnLongClickListener { l(); true } }
        r.contentDescription = "$title, ${MealRepeat.describe(items)}. Klepnutím přidáš na dnešek."
        content.addView(r)
    }

    private fun logItems(label: String, items: List<Item>, keepTimes: Boolean) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val ts = MealRepeatRepository.log(app, items, keepTimes)
            dialog.dismiss()
            onLogged(label, MealRepeat.totalKcal(items), ts)
        }
    }

    private fun apply(t: MealTemplateEntity, items: List<Item>, isDay: Boolean) {
        if (items.isEmpty()) return
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            MealRepeatRepository.markUsed(app, t)
            val ts = MealRepeatRepository.log(app, items, keepTimes = isDay)
            dialog.dismiss()
            onLogged(t.name, MealRepeat.totalKcal(items), ts)
        }
    }

    private fun askName(kind: Kind, suggestion: String, items: List<Item>) {
        val input = EditText(ctx).apply {
            setText(suggestion); setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
        }
        val box = FrameLayout(ctx).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(input) }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(if (kind == Kind.DAY) "Uložit celý den" else "Uložit jídlo")
            .setMessage(MealRepeat.describe(items))
            .setView(box)
            .setPositiveButton("Uložit") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { suggestion }
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    MealRepeatRepository.saveTemplate(app, name, kind, items)
                    Toast.makeText(app, "Šablona „$name“ uložena", Toast.LENGTH_SHORT).show()
                    reload()
                }
            }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    private fun confirmDelete(t: MealTemplateEntity) {
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Smazat šablonu „${t.name}“?")
            .setMessage("Zapsaná jídla zůstanou, smaže se jen šablona.")
            .setPositiveButton("Smazat") { _, _ ->
                fragment.viewLifecycleOwner.lifecycleScope.launch { MealRepeatRepository.deleteTemplate(app, t); reload() }
            }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    private fun dayLabel(d: LocalDate): String {
        val today = LocalDate.now()
        return when (d) {
            today -> "Dnes"
            today.minusDays(1) -> "Včera"
            today.minusDays(2) -> "Předevčírem"
            else -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("cs")).replaceFirstChar { it.uppercase() } +
                " ${d.dayOfMonth}. ${d.monthValue}."
        }
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
}

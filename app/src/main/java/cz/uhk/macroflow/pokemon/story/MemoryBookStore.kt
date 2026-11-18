package cz.uhk.macroflow.pokemon.story

import android.content.Context
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Stav Pamětní knihy a mizení v Ozvěně (docs/adr/0101); pravidla jsou v [MemoryBook]. */
object MemoryBookStore {
    private const val DAYS = "ozvena_active_days"
    private const val BOOK_DATE = "ozvena_book_date"
    private const val SEEN = "ozvena_gone_seen"
    private const val VESSA_PENDING = "ozvena_vessa_pending"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun hasBook(ctx: Context) = StoryFlags.isSet(ctx, Ozvena.BOOK_KEY)

    /** Den, od kterého se počítají ranní rituály (u starších uložených her den prvního otevření). */
    private fun bookDate(ctx: Context): String =
        prefs(ctx).getString(BOOK_DATE, null) ?: today().also { prefs(ctx).edit().putString(BOOK_DATE, it).apply() }

    fun isGone(ctx: Context, id: String) = StoryFlags.isSet(ctx, MemoryBook.goneKey(id)) ||
        (id == "korinek" && StoryFlags.isSet(ctx, Fenn.END_A_KEY))

    fun isWritten(ctx: Context, id: String) = StoryFlags.isSet(ctx, MemoryBook.writtenKey(id))

    /**
     * Vstup do Ozvěny s knihou: započítá aktivní den, nechá zmizet, koho má, a vrátí scénu prvního
     * zmizení, které hráč ještě neviděl (jinak null). Volat můžeš opakovaně, nic se nezdvojí.
     */
    fun onEnter(ctx: Context): String? {
        if (!hasBook(ctx)) return null
        bookDate(ctx)
        val p = prefs(ctx)
        val days = p.getStringSet(DAYS, emptySet()).orEmpty() + today()
        p.edit().putStringSet(DAYS, days).apply()
        MemoryBook.goneAfter(days.size).forEach { if (!StoryFlags.isSet(ctx, MemoryBook.goneKey(it))) StoryFlags.set(ctx, MemoryBook.goneKey(it)) }
        val seen = p.getStringSet(SEEN, emptySet()).orEmpty()
        val next = MemoryBook.VANISH_ORDER.firstOrNull { StoryFlags.isSet(ctx, MemoryBook.goneKey(it)) && it !in seen } ?: return null
        p.edit().putStringSet(SEEN, seen + next).putString(VESSA_PENDING, next).apply()
        return MemoryBook.SCENE_PREFIX + MemoryBook.TRACES[next]
    }

    /** Vessin komentář k poslednímu zmizení (jednou). */
    fun takeVessaComment(ctx: Context): String? {
        val id = prefs(ctx).getString(VESSA_PENDING, null) ?: return null
        prefs(ctx).edit().remove(VESSA_PENDING).apply()
        return MemoryBook.vessaAfter(isWritten(ctx, id))
    }

    /** Volná jména (čte DB – volat mimo hlavní vlákno). */
    fun freeNames(ctx: Context): Int {
        val since = bookDate(ctx)
        val rituals = AppDatabase.getDatabase(ctx).checkInDao().getAllCheckInsSync()
            .filter { it.date >= since && it.mood != "__demo__" }.map { it.date }.distinct().size
        return MemoryBook.freeNames(rituals, MemoryBook.RESIDENTS.count { isWritten(ctx, it.id) })
    }

    /** Otevře knihu (z Batohu v deníku). */
    fun showBook(root: FrameLayout, owner: LifecycleOwner) {
        val ctx = root.context.applicationContext
        owner.lifecycleScope.launch {
            val free = withContext(Dispatchers.IO) { runCatching { freeNames(ctx) }.getOrDefault(0) }
            WorkshopMenus.show(root, MemoryBook.LABEL,
                if (free > 0) "Volná jména: $free" else "Další jméno po ranním rituálu") { ui, body, close ->
                body.addView(ui.text(MemoryBook.BOOK_HINT, 14f, ui.inkSoft).apply { setPadding(0, 0, 0, ui.px(8f)) })
                MemoryBook.RESIDENTS.forEach { r ->
                    val gone = isGone(ctx, r.id)
                    val memory = if (r.id == "venka") MemoryBook.VESSA_WRITTEN else r.memory
                    when (MemoryBook.line(isWritten(ctx, r.id), gone)) {
                        MemoryBook.Line.OPEN -> body.addView(ui.row().apply {
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            addView(ui.text("${r.name} · ${r.role}", 16f), ui.lp(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                            if (free > 0) addView(ui.button("Zapsat") {
                                StoryFlags.set(ctx, MemoryBook.writtenKey(r.id))
                                close(); showBook(root, owner)
                            })
                        })
                        MemoryBook.Line.WRITTEN -> body.addView(ui.text("✒ ${r.name} · ${r.role}\n$memory", 16f))
                        MemoryBook.Line.WRITTEN_GONE -> body.addView(ui.text("✒ ${r.name} · už tu není\n$memory", 16f, ui.inkSoft).apply {
                            setTypeface(typeface, android.graphics.Typeface.ITALIC)
                        })
                        MemoryBook.Line.BLANK -> body.addView(ui.text("______________________", 16f, ui.inkSoft))
                    }
                    body.addView(ui.spacer(8f))
                }
            }
        }
    }
}

package cz.uhk.macroflow.nutrition.plan

import android.content.Context
import cz.uhk.macroflow.nutrition.MealRepeat

/**
 * Pevné položky dne (docs/adr/0069) – co uživatel jí každý den (proteinový shake …).
 * Plánovač je odečte od cíle, dokud nejsou zapsané. Uložené v prefs formátem šablon [MealRepeat].
 */
object DailyStaples {
    private const val PREFS = "NutritionPrefs"
    private const val KEY = "daily_staples"

    fun all(ctx: Context): List<MealRepeat.Item> =
        MealRepeat.decode(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: "")

    fun add(ctx: Context, item: MealRepeat.Item) = save(ctx, all(ctx).filterNot { it.name.equals(item.name, true) } + item)

    fun remove(ctx: Context, name: String) = save(ctx, all(ctx).filterNot { it.name.equals(name, true) })

    private fun save(ctx: Context, items: List<MealRepeat.Item>) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, MealRepeat.encode(items)).apply()

    /** Je pevná položka dnes už zapsaná? Pozná se podle názvu záznamu v deníku. */
    fun eaten(item: MealRepeat.Item, todayNames: List<String>) = todayNames.any { it.trim().equals(item.name.trim(), true) }
}

package cz.uhk.macroflow.nutrition.plan

import cz.uhk.macroflow.data.SnackEntity
import cz.uhk.macroflow.nutrition.SnackCatalog
import cz.uhk.macroflow.nutrition.FoodCategory
import cz.uhk.macroflow.nutrition.plan.DayPlanner.Kind
import cz.uhk.macroflow.nutrition.plan.DayPlanner.Nutr

/**
 * Jídla ze špajzky pro Ideální den (docs/adr/0069): z nejpoužívanějších potravin
 *  - talíře na oběd a večeři: maso / ryba / luštěniny × příloha z obilovin (+ zelenina) podle kategorií
 *    špajzky (docs/adr/0073), gramy každé složky dopočítá planner,
 *  - samotné potraviny na snídani a svačinu.
 * Potraviny chodí seřazené podle oblíbenosti (SnackDao.getAllSnacksSmart).
 */
object PantryPlates {

    const val PREFIX = "pantry:"
    private const val PROTEINS = 3
    private const val CARBS = 2
    private const val SINGLES = 5
    /** Co se dá sníst samo na snídani nebo svačinu. */
    private val SNACKABLE = setOf(FoodCategory.DAIRY, FoodCategory.FRUIT, FoodCategory.NUTS, FoodCategory.SUPPLEMENTS, FoodCategory.BAKERY)

    /** Potravina a makra její výchozí porce (gramy z popisku, jinak 100 g). */
    data class Item(val snack: SnackEntity, val grams: Float, val nutr: Nutr) {
        val key: String get() = snack.id.toString()
    }

    fun item(s: SnackEntity): Item {
        val g = SnackCatalog.portionGrams(s.weight)
        val v = SnackCatalog.scale(s, g)
        return Item(s, g, Nutr(v.kcal.toDouble(), v.p.toDouble(), v.s.toDouble(), v.t.toDouble()))
    }

    /** @param ranked potraviny od nejoblíbenější */
    fun options(ranked: List<SnackEntity>): List<DayPlanner.Option> {
        val items = ranked.distinctBy { SnackCatalog.normalize(it.name) }.map(::item).filter { it.nutr.kcal > 0 }
        fun top(n: Int, vararg cats: FoodCategory) = items.filter { SnackCatalog.categoryOf(it.snack) in cats }.take(n)
        val veg = top(1, FoodCategory.VEGETABLES)
        val plates = top(PROTEINS, FoodCategory.MEAT, FoodCategory.FISH, FoodCategory.LEGUMES).flatMap { p ->
            top(CARBS, FoodCategory.GRAINS).map { c ->
                val all = listOf(p, c) + veg
                DayPlanner.Option(PREFIX + all.joinToString("|") { it.key }, all.joinToString(" + ") { it.snack.name },
                    setOf(Kind.MAIN), all.map { DayPlanner.Part(it.snack.name, it.nutr, 0.5, 3.0) })
            }
        }
        val singles = items.filter { SnackCatalog.categoryOf(it.snack) in SNACKABLE || isCereal(it) }.take(SINGLES).map {
            DayPlanner.Option(PREFIX + it.key, it.snack.name, if (isCereal(it)) setOf(Kind.BREAKFAST) else setOf(Kind.SNACK, Kind.BREAKFAST),
                listOf(DayPlanner.Part(it.snack.name, it.nutr, 0.5, 2.0)))
        }
        return plates + singles
    }

    /** Snídaňové obiloviny (vločky, müsli…) – samotné jen k snídani. */
    private fun isCereal(i: Item) = SnackCatalog.categoryOf(i.snack) == FoodCategory.GRAINS &&
        Regex("vlock|musli|granol|cornfl|kase|cereal").containsMatchIn(FoodCategory.normalize(i.snack.name))

    /** Potraviny talíře z [optionId] (v pořadí složek). */
    fun parts(optionId: String, ranked: List<SnackEntity>): List<Item> =
        optionId.removePrefix(PREFIX).split('|').mapNotNull { key -> ranked.firstOrNull { it.id.toString() == key }?.let(::item) }
}

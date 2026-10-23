package cz.uhk.macroflow.pokemon.story

/**
 * Kapitola V · OZVĚNA (bible příběhu, docs/adr/0095). Čistá data, pokryto testy.
 *
 * Zatím první dvě fáze: „Dobré ráno, Ozvěno“ (pekař Bohoušek, tři rána se snídaní) a „Jméno na dveřích“
 * (písařka Věnka dá Pamětní knihu). Další fáze (Pět Mistrů, Kalibrace…) se přidají za ně.
 */
object Ozvena {
    const val QUEST_ID = "ozvena_chapter"
    const val BAKER = "Pekař Bohoušek"
    const val SCRIBE = "Písařka Věnka"

    /** Fáze, ve které hráč jde za Věnkou pro Pamětní knihu (index). */
    const val NAME_STAGE = 1
    /** Věnka hráči dala Pamětní knihu. */
    const val BOOK_KEY = "ozvena_memory_book"

    const val BREAKFAST_DAYS = 3
    /** Snídaně = jídlo zapsané mezi 4:00 a 10:30 (stejně jako MealRepeat.Slot.BREAKFAST). */
    private const val FROM_MIN = 4 * 60
    private const val TO_MIN = 10 * 60 + 30

    /** Kolik různých dnů od [sinceDate] (yyyy-MM-dd, včetně) má zapsanou snídani; [meals] = (datum, čas HH:mm). */
    fun breakfastDays(meals: List<Pair<String, String>>, sinceDate: String): Int =
        meals.filter { (date, time) ->
            date >= sinceDate && cz.uhk.macroflow.nutrition.MealRepeat.minutes(time)?.let { it in FROM_MIN until TO_MIN } == true
        }.map { it.first }.distinct().size

    const val GOOD_MORNING =
        "„Dobré ráno, dobré ráno! Čerstvý chléb, jako každé ráno!“\n\n" +
            "Nová tvář? To je dobře. Ráno má mít každý někoho, kdo ho pozdraví. Chodí ke mně na snídani celé město – " +
            "přijď taky. Tři rána, a budeš tu doma.\n\n" +
            "Ráno je ráno, jen když se nasnídáš. To ví každý. Nevím odkud, ale ví."

    const val GOOD_MORNING_HINT =
        "„Dobré ráno, dobré ráno!“ Zapiš si snídani (do 10:30) ve třech různých dnech. Bohoušek si tě pamatuje… nebo aspoň říká, že ano."

    const val NAME_ON_DOOR =
        "„Dobré ráno, dobré ráno! Čerstvý chléb, jako každé ráno!“\n\n" +
            "…Počkej. Třikrát jsi tu byl a třikrát jsem ti řekl úplně totéž, viď? To je zvláštní. " +
            "Mám pocit, že to říkám už hrozně dlouho.\n\n" +
            "Vedle pekárny stojí u dveří prázdný kočárek. Bydleli tam… někdo tam bydlel. Na dveřích bylo jméno, " +
            "teď je tam jen škrábanec. Zajdi za písařkou Věnkou. Ta zapisuje jména, aby nezmizela."

    const val NAME_ON_DOOR_HINT = "Písařka Věnka stojí u svého domu na západ od náměstí – u zdi plné jmen."

    const val MEMORY_BOOK =
        "„Bohoušek tě posílá? Tak už to zase začíná.“\n\n" +
            "Zapsala jsem tě. A tady – Pamětní kniha. Co do ní zapíšeš, to Bezejmenní nesmažou. " +
            "Aspoň zatím to tak vždycky platilo.\n\n" +
            "Víš, kolik jmen je na té zdi? Já už ne. Vím jen, že moje tam není. Jednou ráno jsem ho hledala " +
            "a zeď byla o řádek kratší.\n\n" +
            "Piš si do ní, koho potkáš. Kdyby se jednou ráno někdo neozval, budeš aspoň vědět, že tu byl."
}

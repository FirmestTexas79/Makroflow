package cz.uhk.macroflow.pokemon.story

/**
 * Pamětní kniha a mizení obyvatel Ozvěny (bible příběhu, docs/adr/0101). Čistá data a pravidla, pokryto testy.
 *
 * Varianta B: kniha nechrání postavu, jen vzpomínku. Kdo je zapsaný, zmizí taky, ale v knize po něm zůstane
 * jméno a jedna věta. Kdo zapsaný není, nechá po sobě prázdný řádek.
 *
 * Mizí se po aktivních dnech v Ozvěně (den, kdy hráč s knihou vstoupí do města): každý [EVERY]. den další
 * z [VANISH_ORDER]. Klíčové postavy, Nelle (sad) a Pell se Sabeth nemizí nikdy; Tobben jen koncem A u Fenna.
 */
object MemoryBook {
    /** Obyvatel: [id] = id z OzvenaCity.TOWNSFOLK, [memory] = věta, která po něm v knize zůstane. */
    data class Resident(val id: String, val name: String, val role: String, val memory: String)

    val RESIDENTS = listOf(
        Resident("bohousek", "Brann", "pekař", "Pekl každé ráno tentýž chléb a každého zdravil úplně stejně."),
        Resident("venka", "Vessa", "písařka", "Zapisovala jména, aby nezmizela. Její vlastní na zdi chybělo."),
        Resident("lida", "Nelle", "jablka ze sadu", "Nosila jablka ze Ztichlého sadu a popel měla za pyl."),
        Resident("jarda", "Jarro", "metař", "Každé ráno smetl popel, který se každé ráno vrátil."),
        Resident("korinek", "Tobben", "noviny", "Každý den četl tytéž noviny z úterý třináctého."),
        Resident("corra", "Corra", "pradlena", "Věšela každé ráno totéž prádlo a prala malé košilky, pro které si nikdo nechodil."),
        Resident("oswin", "Oswin", "hodinář", "Nechával všechny hodiny stát na 6:12, protože se bál, co by naměřily."),
        Resident("pell", "Pell", "u kašny", "Hledal sestru, která se schovávala tam, kde ji socha nevidí."),
        Resident("pip", "Pip", "u kašny", "Dokončovala bratrovi věty a schovávala se u kašny, kde ji socha nemohla vidět."),
        Resident("lio", "Lio", "pošťák", "Nosil dopisy bez adres ke dveřím bez jmen."),
        Resident("sabeth", "Sabeth", "zpěvačka", "Zpívala melodii, která tu byla dřív než ona.")
    )

    fun resident(id: String) = RESIDENTS.firstOrNull { it.id == id }

    /** Pořadí mizení (pevné kvůli dramaturgii a testům). */
    val VANISH_ORDER = listOf("corra", "oswin", "pip", "lio", "jarda")
    const val EVERY = 3

    /** Kdo už je pryč po [activeDays] aktivních dnech v Ozvěně. */
    fun goneAfter(activeDays: Int): List<String> = VANISH_ORDER.take((activeDays / EVERY).coerceAtLeast(0))

    /** Volná jména: první dá Vessa, pak jedno za každý den s ranním rituálem od získání knihy. */
    fun freeNames(ritualDaysSinceBook: Int, written: Int): Int = (1 + ritualDaysSinceBook - written).coerceAtLeast(0)

    enum class Line { OPEN, WRITTEN, WRITTEN_GONE, BLANK }

    fun line(written: Boolean, gone: Boolean): Line = when {
        written && gone -> Line.WRITTEN_GONE
        written -> Line.WRITTEN
        gone -> Line.BLANK
        else -> Line.OPEN
    }

    fun goneKey(id: String) = "ozvena_gone_$id"
    fun writtenKey(id: String) = "ozvena_book_$id"

    /** Příznaky příběhu (synchronizují se jako ostatní, StoryProgress). */
    val KEYS: Set<String> = RESIDENTS.flatMap { listOf(goneKey(it.id), writtenKey(it.id)) }.toSet()

    /** Stopa na místě zmizelého (scéna při další návštěvě i klepnutí na jeho místo). */
    val TRACES = mapOf(
        "corra" to "🧺 Na šňůře visí mokré prádlo a kape z něj. Koš stojí poloprázdný, kolíček leží v trávě. Nikdo tu nestojí.",
        "oswin" to "🕰️ Dílna je otevřená a všechny hodiny poprvé tikají. Každé jinak a každé ukazují jiný čas. Hodinář tu není.",
        "pip" to "🐟 Na lavičce u kašny leží jedna mokrá šupinka. Pell sedí kousek vedle a mluví na prázdné místo.",
        "lio" to "✉️ U cesty leží pošťácká brašna. Dopisy se z ní rozsypaly a ani jeden nemá adresu. Na jednom je jen číslo, rozmazané deštěm.",
        "jarda" to "🧹 Koště je opřené o zeď a hromádka popela zůstala nezametená. Vítr ji pomalu rozfoukává po náměstí."
    )

    const val SCENE_PREFIX = "Ozvěna je dnes o něco tišší.\n\n"

    /** Vessa po zmizení: jinak, když hráč jméno zapsal, a jinak, když ne. */
    fun vessaAfter(written: Boolean): String =
        "✍️ Písařka Vessa: „Dneska ráno byla zeď zase o řádek kratší.“\n\n" + if (written)
            "„Ale ty ho máš v knize, viď? Dobře. Někdo si ho bude pamatovat. To je víc, než kolik dostane většina z nás.“"
        else
            "„Podívej se do knihy. Jestli tam po něm zůstal prázdný řádek… to je to nejhorší. Vědět, že tam někdo byl, a nevědět kdo.“"

    const val VESSA_WRITTEN =
        "Zapsal jsi Vessu. Když jí knihu ukážeš, dlouho se dívá na svoje jméno. „Tak takhle se píše,“ řekne nakonec tiše."

    const val BOOK_HINT = "Každý ranní rituál ti dá jedno jméno, které můžeš zapsat. Co je v knize, Bezejmenní nesmažou. Aspoň zatím."

    const val ITEM_ID = "memory_book"
    const val LABEL = "Pamětní kniha"
    const val DESCRIPTION = "Od písařky Vessy. Co do ní zapíšeš, Bezejmenní nesmažou."
}

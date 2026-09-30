package cz.uhk.macroflow.pokemon.story

/**
 * Havíř Vendelín v Dolech (docs/adr/0050). Čistá data, pokryto testy.
 *
 * Poslední havíř ze šichty „Sektoru Hlubiny“. Kahan mu svítí jiskřivkami, na kabátě má
 * vybledlou nášivku S-7. Učí chytat hmyz a pak hráče zkouší ze všech sběrných dovedností
 * světa – pro šichtovní knihu. Kniha je kalibrační protokol Kustodiátu: hráč je Okruh 213
 * a všechny předchozí zápisy jsou psané jeho rukou. Vendelín zapomíná (jako Mydrus) a po
 * skončení questu hráče pokaždé vítá jako nováčka.
 */
object Vendelin {
    const val QUEST_ID = "mines_vendelin"
    const val NAME = "Havíř Vendelín"

    // Uzly v Dolech (MinesMap / gen_mines.py)
    const val NODE = "vendelin"
    const val BOOK_NODE = "sichtovni_kniha"

    /** Kde stojí na mapě (art px Dolů, pata postavy) a kde leží kniha (na bedně). */
    const val X = 52
    const val Y = 448
    const val BOOK_X = 26
    const val BOOK_Y = 489

    /** Fáze s podpisem knihy (index). */
    const val SIGN_STAGE = 5
    /** Příznak: hráč se podepsal do šichtovní knihy. */
    const val BOOK_SIGNED_KEY = "mines_book_signed"

    /** Zkouška: kolik hodin práce v každé dovednosti (fáze AFK_HOURS). */
    const val TEST_HOURS = 3
    /** Dovednosti zkoušky (Skill.id) – kácení, těžba, chytání hmyzu. */
    val TEST_SKILLS = listOf("logging", "mining", "bugcatching")

    /** Odměna za podpis. */
    const val LAMP_ID = "acc_trinket_miner_lamp"

    /** Text knihy – rukopis je pokaždé stejný. [signed] = hráč už se podepsal. */
    fun bookText(signed: Boolean): String = buildString {
        append("ŠICHTOVNÍ KNIHA · SEKTOR HLUBINY\n\n")
        append("Okruh 211 · kácení 3 h · těžba 3 h · síťka 3 h · ZPŮSOBILÝ\n— celý řádek přeškrtnutý —\n\n")
        append("Okruh 212 · kácení 3 h · těžba 3 h · síťka 3 h · ZPŮSOBILÝ\n— přeškrtnuto, na okraji: „zase“ —\n\n")
        if (signed) {
            append("Okruh 213 · kácení 3 h · těžba 3 h · síťka 3 h · ZPŮSOBILÝ\n\n")
            append("Pod tvým podpisem je razítko S-7, ještě vlhké. Když listuješ zpátky, všimneš si, že všechny podpisy " +
                "před tebou jsou psané stejnou rukou.\n\nTvou.")
        } else {
            append("Okruh 213 · ……\n\nŘádek čeká na tvůj podpis. Písmo nad ním ti je nějak povědomé.")
        }
    }

    const val BOOK_CLOSED = "Na bedně leží kniha v okovaných deskách. Vendelín ji hlídá jako oko v hlavě – zatím do ní nesmíš."

    /** Doplněk k textu železných dveří, když už hráč viděl knihu. */
    const val DOOR_HANDWRITING = "\n\nŠkrábance jsou psané stejnou rukou jako Vendelínovy zápisy v šichtovní knize."

    /** Na mapě se ozve, když k němu hráč přijde poprvé (než otevře dialog). */
    const val GREETING = "U lucerny sedí na bedně starý havíř a něco si mumlá do vousů."
}

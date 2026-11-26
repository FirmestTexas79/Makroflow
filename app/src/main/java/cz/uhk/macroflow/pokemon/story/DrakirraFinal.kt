package cz.uhk.macroflow.pokemon.story

/**
 * Poslední souboj s Drakirrou na Prahu – vypálené mýtině dole v Lese pod korunou (docs/adr/0106, bible: finále).
 * Drakirra je zázrakem přetvořená nádoba Kustodiátu a jediná celou dobu stála na hráčově straně. Teď mu zastoupí cestu
 * ke kmeni a tentokrát neodletí: je odhodlaná položit vlastní život, aby do středu kořenů už nepřišla nová skořápka
 * (hráč ani jeho Spirra). Hráč s ní musí bojovat. Souboj: SpecialBattle.DRAKIRRA_FINAL (lv 42, bez řetězů).
 * Po výhře Drakirra leží, dýchá; ze ztracené sondy promluví Sept. Cesta ke kmeni je volná (Kmen a Trpitel – další krok).
 */
object DrakirraFinal {
    const val DEFEATED_KEY = "drakirra_prah_porazena"
    const val AFTER_SEEN_KEY = "drakirra_prah_dohrano"
    val KEYS = setOf(DEFEATED_KEY, AFTER_SEEN_KEY)

    const val TITLE = "Práh"
    const val SUBTITLE = "Kapitola VI · Les pod korunou"

    const val BEFORE = "Drakirra se zvedne. Bez řetězů je větší, než sis ji pamatoval z Arény – zabírá skoro celou mýtinu. " +
        "Les kolem je vypálený do kruhu. Čekala tu dlouho.\n\n" +
        "Nedívá se na tebe. Dívá se na Spirru. Spirra se ti přitiskne k noze – a poprvé se nechce schovat.\n\n" +
        "Dračice sklopí hlavu až k zemi, těsně před tvé boty, a vydechne. Žár ti olízne tvář, ale nespálí. Varování. Nebo prosba.\n\n" +
        "Za ní stoupá větev ke kmeni a nahoře v zelené záři z rány se blýská kopí. Ví, kam jdeš. Ví, kdo tě posílá.\n" +
        "Tu první skořápku už jednou ztratili. Druhou jim nedá."
    const val FIGHT = "🔥 Postavit se jí"
    const val LEAVE = "Ještě ne"
    const val LEAVE_TEXT = "Ustoupíš ke kraji mýtiny. Drakirra si zase lehne přes větev, hlavu na tlapách, oči otevřené. Počká."
    const val NO_TEAM = "Bez Makromona se k ní nepřibližuj."

    const val AFTER = "Drakirra klesne na bok. Křídla se jí rozprostřou přes vypálený kruh jako deka. Dýchá – pomalu, těžce.\n\n" +
        "Spirra k ní přiběhne dřív, než ji stihneš zadržet, a lehne si jí k hlavě. Dračice otevře oko, dlouho se na ni dívá – " +
        "a pak oči zavře. Poprvé klidně.\n\n" +
        "Ztracená sonda v lese zapraská. Z reproduktoru, tiše a přes šum, Septův hlas:\n" +
        "„Test síly nádoby: úspěšný. Podruhé. Subjekt 213, pokračujte ke kmeni. Sestup je schválen.“\n\n" +
        "Necháš ji ležet. Cesta je volná."

    /** Texty míst na Prahu podle stavu. */
    fun dragonText(defeated: Boolean) = if (defeated)
        "🐉 Drakirra leží na boku přes půl mýtiny a pomalu dýchá. Spirra se od ní nechce hnout. Když se přiblížíš, " +
            "dračice pootevře oko – a nechá tě projít."
    else "🐉 Drakirra. Leží přes cestu ke kmeni – přes jedinou větev, po které se dá vylézt nahoru. Oči má otevřené. Vidí.\n" +
        "Dívá se na tebe a pak na Spirru. Mezi zuby jí doutná žár. Tentokrát neodletí."

    fun trunkText(defeated: Boolean) = if (defeated)
        "⬆️ Nejtlustší větev se sklání až k zemi a stoupá vzhůru do tmy – ke kmeni. Ve kůře jsou vyšlapané stupy. Drakirra už cestu nehlídá.\n" +
            "Nahoře čeká Trpitel.\nZATÍM NEPŘÍSTUPNÉ – Kmen se staví."
    else "⬆️ Nejtlustší větev se sklání až k zemi a stoupá vzhůru do tmy – ke kmeni. Přes ni leží Drakirra."
}

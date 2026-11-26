package cz.uhk.macroflow.pokemon.story

/**
 * Poslední souboj s Drakirrou na Prahu – aréně u paty koruny pod trůnem Lumiry (RootThreshold, docs/adr/0106, bible: finále).
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
    const val SUBTITLE = "Kapitola VI · Práh"

    const val BEFORE = "Na ostrově pod trůnem se pohne stín. Drakirra se zvedne – bez řetězů je větší, než sis ji pamatoval z Arény. " +
        "Voda kolem ostrova zasyčí a zapáří se.\n\n" +
        "Nedívá se na tebe. Dívá se na Spirru. Spirra se ti přitiskne k noze – a poprvé se nechce schovat.\n\n" +
        "Dračice sestoupí do mělčiny, sklopí hlavu až k tvým botám a vydechne. Žár ti olízne tvář, ale nespálí. Varování. Nebo prosba.\n\n" +
        "Za ní, za trůnem se sochou ženy v kápi, stoupá větev ke kmeni. Nahoře v bledém světle z rány se blýská kopí. " +
        "Ví, kam jdeš. Ví, kdo tě posílá.\nTu první skořápku už jednou ztratili. Druhou jim nedá."
    const val FIGHT = "🔥 Postavit se jí"
    const val LEAVE = "Ještě ne"
    const val LEAVE_TEXT = "Ustoupíš po deskách zpátky. Drakirra si zase lehne na ostrov pod trůn, hlavu na tlapách, oči otevřené. Počká."
    const val NO_TEAM = "Bez Makromona se k ní nepřibližuj."

    const val AFTER = "Drakirra klesne na bok do mělčiny pod trůnem. Křídla se jí rozprostřou po hladině jako deka. Voda kolem ní syčí a pak utichne. Dýchá – pomalu, těžce.\n\n" +
        "Spirra k ní přiběhne dřív, než ji stihneš zadržet, a lehne si jí k hlavě. Dračice otevře oko, dlouho se na ni dívá – " +
        "a pak oči zavře. Poprvé klidně.\n\n" +
        "Z lesa za branou zapraská ztracená sonda. Tiše a přes šum Septův hlas:\n" +
        "„Test síly nádoby: úspěšný. Podruhé. Subjekt 213, pokračujte ke kmeni. Sestup je schválen.“\n\n" +
        "Necháš ji ležet. Cesta je volná."

    /** Texty míst na Prahu podle stavu. */
    fun dragonText(defeated: Boolean) = if (defeated)
        "🐉 Drakirra leží na boku na ostrově pod trůnem a pomalu dýchá. Spirra se od ní nechce hnout. Když se přiblížíš, " +
            "dračice pootevře oko – a nechá tě projít."
    else "🐉 Drakirra leží na kamenném ostrově uprostřed jezera, pod trůnem, přes jedinou cestu ke kmeni. Oči má otevřené. Vidí.\n" +
        "Dívá se na tebe a pak na Spirru. Mezi zuby jí doutná žár a voda kolem ostrova se pomalu kouří. Tentokrát neodletí."

    fun trunkText(defeated: Boolean) = if (defeated)
        "⬆️ Za trůnem stoupá větev se stupy vzhůru do tmy – ke kmeni. Drakirra už cestu nehlídá.\n" +
            "Nahoře čeká Trpitel.\nZATÍM NEPŘÍSTUPNÉ – Kmen se staví."
    else "⬆️ Za trůnem stoupá větev se stupy vzhůru do tmy – ke kmeni. Cestu k ní hlídá Drakirra."
}

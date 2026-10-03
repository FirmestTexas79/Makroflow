# 0064 – Evoluce: celoobrazovková scéna a volba útoku

**Stav:** návrh (větev `POSTAVA`) · **Datum:** 2026-10-03

## Kontext
Evoluční dialog byl první testovací verze:
* malé tmavé okno se zaoblenými rohy;
* střídání spritu a bílé siluety;
* text ve strojovém písmu.

Nový útok se na volné místo naučil sám, bez ptaní. Při plné sadě se nabízela šedá systémová tlačítka. Okno se pak samo zavřelo. Útok nebyl vidět (síla, typ, efekt) a nešlo odmítnout, aby se ho Makromon vůbec učil.

## Rozhodnutí
* **Scéna** (`EvolutionStageView`) je celá obrazovka kreslená po pixelech, stejně jako přechody mezi lokacemi:
  * noční nebe v pruzích s blikajícími hvězdami a pomalu se točícími paprsky;
  * kruhový podstavec s runami;
  * světlušky, které se během proměny stahují dovnitř.
* **Průběh animace:**
  * Makromon se nejdřív houpe;
  * pak zbělá a jeho silueta se střídá s obrysem nové formy, čím dál rychleji (z 560 ms na 45 ms);
  * záře a paprsky sílí;
  * přijde bílý záblesk a nová forma vyskočí s odskokem, výbuchem světlušek a blikajícími hvězdičkami;
  * nebe se zahřeje do zlata.
  
  Scéna pak dál tiše žije a **nic se samo nezavře**.
* **Dřevěné cedule** jako ve zbytku hry: nahoře „✦ EVOLUCE ✦“ a „Aqulin ➜ ???“ (po odhalení jméno nové formy), dole pergamenový panel. Texty se vypisují po písmenech.
* **Postup v panelu:**
  1. „Co se to děje?“ během proměny;
  2. **Gratulace** a **porovnání statistik** na stejném levelu: životy, útok, obrana a rychlost, pixelový pruh s přírůstkem zeleně, „34 → 41 +7“;
  3. **Nový útok** na kartě: typ v barvě, síla, přesnost, PP a efekt („Uspí soupeře (30 %)“). Tlačítka **Naučit** a **Nenaučit** (odmítnutí se potvrzuje);
  4. při plné sadě **výběr, který útok zapomene**: klepnutím se karta označí červeně a přeškrtne, potvrdí se „Zapomenout X → Y“, nebo jde zpět;
  5. **Hotovo**: přehled útoků s nově naučeným zvýrazněným. Teprve tlačítko Hotovo okno zavře.
* **Útoky:** při evoluci se uloží sada, kterou Makromon uměl. Kdo měl jen výchozí útoky starého druhu, dostane je natrvalo, aby mu je nová forma nepřepsala. Pravidla učení jsou v `MoveLearning` (čistý Kotlin, `MoveLearningTest`).
* **Ukládání** běží mimo dialog, takže se dokončí, i když hráč hned klepne na Hotovo. Ukládá se i do cloudu.
* **Náhled pro ladění:** `--es debug_evo 004:005` na mapě. Spustí evoluci s vymyšleným Makromonem se 4 útoky a nic neuloží.
* Starý layout `dialog_evolution.xml` je smazán. Volající (level-up, Spirra, Makrodex) se nemění.

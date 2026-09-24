# ADR 0002 – Jednotný faktoriální model energetického výdeje a cílů

- **Stav:** přijato (2026-09)
- **Kód:** `app/src/main/java/cz/uhk/macroflow/energy/` (čistý Kotlin, bez Androidu)
- **Testy:** `app/src/test/java/cz/uhk/macroflow/energy/` (30 testů s ručně spočítanými referenčními hodnotami)

## Kontext

Výpočty vznikaly postupně na několika místech a navzájem se rozcházely:

| Problém | Kde byl | Dopad |
|---|---|---|
| TEF se počítal 3× (násobitel aktivity + `dietModifier` 5–12 % + odečet TEF od snědeného) | `MacroCalculator`, `MacroFlowEngine` | povolený příjem ~15–20 % nad výdejem, deficit při dietě mizel |
| Násobitel „Sportovec 1,6 – těžké tréninky“ **a k tomu** přičtené tréninky | `ProfileFragment`, `MacroCalculator` | trénink započten dvakrát |
| Kroky: 0,00045 kcal/kg/krok, až nad 6000, bez délky kroku | `MacroFlowEngine` | ~+24 % proti ACSM, výška ignorována |
| Silový trénink paušálem `kg × 4,5` bez délky | `MacroCalculator` | nezávislé na čase |
| Švihadlo 0,07 kcal/skok | `MacroCalculator` | ~poloviční proti Compendiu |
| Tuk 38 kJ/g, vláknina 0 | 5 souborů v `nutrition/` | EU předpis: 37 kJ/g, vláknina 8 kJ/g |
| Bílkoviny na kg **celkové** váhy | `MacroCalculator` | u vyššího % tuku nadhodnocené |
| Somatotyp ze zápěstí → ±10 % sacharidů | `EliteMetabolicEngine` | bez vědecké opory (zápěstí = velikost kostry) |
| Vláknina 12 g vs 14 g/1000 kcal, v historii navíc jiná čísla podle diety | 3 místa | každá obrazovka jiný cíl |
| Náhled diety (pie chart) s pevnými %, které výpočet vůbec nepoužíval | `DashboardFragment` | UI neodpovídalo číslům |
| „Spálený tuk“ = kcal / 9 | `PlanFragment` | předpoklad 100 % tukové oxidace |

## Rozhodnutí

**TDEE = BMR + NEAT(styl) + chůze + trénink + TEF**, každá složka právě jednou.

| Složka | Vzorec | Zdroj |
|---|---|---|
| BMR | Mifflin-St Jeor; při změřeném % tuku (Elite) Katch-McArdle `370 + 21,6·FFM` | Mifflin et al. 1990; McArdle, Katch & Katch |
| % tuku bez měření | `1,20·BMI + 0,23·věk − 10,8·muž − 5,4` | Deurenberg et al. 1991 |
| NEAT (práce, domácnost) | `BMR · (f − 1)`, f = 1,15 / 1,30 / 1,50 | kalibrováno na PAL ≈ 1,4 pro sedavého s 6000 kroky (FAO/WHO/UNU 2004) |
| Chůze | `kroky · délka kroku · 0,1 ml O₂/kg/m · 5 kcal/l` = 0,5 kcal/kg/km čistého | ACSM rovnice pro chůzi; délka kroku 0,415/0,413 · výška |
| Běh | 0,2 ml O₂/kg/m = 1 kcal/kg/km čistého | ACSM rovnice pro běh |
| Silový trénink | `(MET − 1) · kg · h`; push/pull 3,5, legs/full 5,0; výchozí délka 60 min | Compendium of Physical Activities (Ainsworth 2011), 02054, 02052 |
| Švihadlo | MET 8,8 / 11,8 / 12,3 podle kadence | Compendium 15551–15553 |
| Schody | MET 9,0 | Compendium 02065 |
| TEF | 10 % celkového výdeje | Westerterp 2004 |
| Potraviny | 4/4/9/2 kcal, 17/17/37/8 kJ (B/S/T/vláknina), etiketa má přednost | Nařízení (EU) 1169/2011, příl. XIV |

**Cíl příjmu**
- Dieta: −0,5 % váhy/týden, bulk: +0,25 % váhy/týden, 7700 kcal/kg (Helms 2014; Iraki 2019).
- Deficit max. 25 % výdeje; příjem nikdy pod `max(BMR, 1500 muži / 1200 ženy)` (bezpečnostní pojistka).

**Makra**
- Bílkoviny na kg **netukové hmoty**: 2,3 (udržování/bulk), 2,7 (dieta), +0,4 pro High Protein → horní mez 3,1 (Helms et al. 2014).
- Tuk 25 % energie, nikdy pod 20 % (dolní hranice AMDR, IOM 2005). Low carb: sacharidy ≤ 20 % energie. Keto: 30 g sacharidů.
- Sacharidy = zbytek (energie vlákniny je v cíli započtena zvlášť).
- Vláknina 14 g/1000 kcal, min. 25 g (IOM 2005; EFSA 2010).
- Voda z nápojů 2,0 l muži / 1,6 l ženy + 0,5 l na hodinu tréninku (EFSA 2010: celkem 2,5 / 2,0 l, ~80 % z nápojů).

**Kroky v čase:** uzavřený den = skutečné kroky; dnešek = max(skutečnost, 6000) – cíl ráno neklesá a přes den jen roste; den bez záznamu = 6000.

## Dopad na typický profil (muž, 83 kg, 175 cm, 22 let, sedavá práce, 8000 kroků, den volna)

| | Dřív (efektivně, vč. odečtu TEF) | Teď |
|---|---|---|
| Výdej | ~2 370 + ~250 „TEF bonus“ | 2 592 |
| Cíl – udržování | ~2 620 | 2 592 |
| Cíl – dieta | ~2 250 (deficit jen ~13 %) | 2 135 (−456 kcal = −0,5 % váhy/týden) |
| Voda | 3,3 l | 2,0 l |

## Důsledky a limity

- (+) Jedno místo pravdy – dashboard, historie, achievementy, spawny i PDF report ukazují stejná čísla.
- (+) Každá konstanta má zdroj a test; model je obhajitelný a rozšiřitelný.
- (−) Rovnice mají individuální chybu ±10–15 % (i Mifflin). Řeší ji **fáze B – adaptivní výdej** z váhy a příjmu.
- (−) 7700 kcal/kg je zjednodušení (Hall 2008) – dynamiku opět zpřesní fáze B.
- (−) Délka silového tréninku zatím v UI chybí (čte se volitelný klíč `strength_duration_<Den>`, jinak 60 min).
- Neřešeno v této fázi: predikce váhy v grafu historie (`HistoryFragment.updateBioLogicChart`) stále používá
  heuristiky (TEF podle diety, zápěstí) – nahradí ji predikce z energetické bilance ve fázi B.
  Pole zápěstí v Elite módu už makra neovlivňuje.

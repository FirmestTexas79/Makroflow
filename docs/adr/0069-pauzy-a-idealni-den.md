# 0069 – Pauzy podle cviku a Ideální den

Stav: přijato (2026-10-08)

## Pauzy podle cviku
- `training/exercises/RestTimes` – explicitní pauza pro každý ze 67 cviků v pěti stupních:
  4 min (dřep, mrtvý tah), 3 min (těžké vícekloubové s osou, shyby, bradla), 2 min (vícekloubové na stroji,
  s jednoručkami, kladky, výpady), 1:30 (izolované cviky na velké partie), 1 min (malé svaly, core).
  Opora: Schoenfeld a kol. 2016, ACSM 2009. Neznámý cvik = 2 min. Test hlídá, že nový cvik dostane pauzu.
- Pauza v Makrosvětu (0065, bod 5) se řídí cvikem poslední zapsané série (`WorkoutDao.lastSet`).
- Nastavení „Konec pauzy v Makrosvětu“: výchozí „Podle cviku“, klepnutím pevná délka (přebije cvik).
- Detail cviku ukazuje doporučenou pauzu v podtitulku.

## Predikce dne
- Model výdeje (EnergyModel + MacroPlanner + adaptivní korekce) zůstává. Nově se pro dnešek kroky
  předpovídají na konec dne: `nachozeno + (1 − podíl dne) × obvyklý den`, podíl dne lineárně 6:00 → 22:00,
  obvyklý den = medián posledních 14 dní (méně než 3 dny dat → 6 000). Dřív `max(nachozeno, 6 000)`.

## Ideální den
- `nutrition/plan/DayPlanner` (čistý Kotlin): zbytek = cíl − snědené − nezapsané pevné položky.
  Volná jídla dne (snídaně / oběd / svačina / večeře) = ještě neproběhla a nic se v nich nejedlo.
  Pro každou podmnožinu volných jídel a kombinaci receptů (bez opakování) se porce dopočítají
  omezenými nejmenšími čtverci (souřadnicový sestup, porce 0,5–2,5, pak čtvrtky), skóre =
  vážené relativní odchylky kcal/B/S/T (1 / 1,5 / 0,6 / 0,6) + λ·(porce − 1)² + malá cena za jídlo.
  Vrací 5 nejlepších různých plánů („Jiný návrh“).
- Pevné položky (`DailyStaples`, prefs ve formátu šablon MealRepeat): skládají se ve Složit jídlo
  v režimu „Každý den“, v plánu se zapíšou jedním klepnutím, za snědené se považují podle názvu v deníku.
- Vstup: Přidat jídlo → „Ideální den“. Návrh → Složit jídlo s gramy dopočítanými na cíl.
- Zatím jen z 15 fitness receptů (0068); špajzka a vlastní šablony mohou přibýt jako další `Option`.

# 0023 – Tréninkový deník v atlasu cviků

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-25

## Kontext
Atlas svalů (ADR 0019) vysvětluje cviky, ale nic si nepamatuje. Pro progresivní přetížení –
hlavní hnací sílu růstu síly i svalů – je potřeba vědět, co jsem zvedl minule a co zkusit dnes.
Plán tréninku zná jen typy dnů (PUSH/PULL/LEGS), ne konkrétní série.

## Rozhodnutí
* **Tabulka `workout_sets`** (DB v37, `MIGRATION_36_37`): datum, čas zápisu, id cviku z knihovny,
  váha (0 = vlastní váha, u jednoruček jedna jednoručka), opakování. Synchronizace do Firestore
  `users/{uid}/workout_sets/{createdAt}`, obnova po přihlášení, mazání s účtem.
* **Čistá logika** `training/log/Progression.kt` (testy `ProgressionTest`):
  * odhad 1RM Epleyho vzorcem `w · (1 + r/30)` (1 opakování = váha),
  * pracovní série = série s nejvyšší váhou dne (rozcvičky se nepočítají),
  * **dvojitá progrese**: rozsah opakování podle typu cviku (osa 6–10, jednoručky 8–12,
    kladka/stroj 10–15, vlastní váha 6–15); všechny pracovní série na horní hranici → přidej
    nejmenší přírůstek (osa/kladka 2,5 kg, jednoručky 2 kg) a začni na spodní; v rozsahu → +1
    opakování; pod rozsahem → drž váhu; u vlastní váhy nad rozsahem → těžší varianta/zátěž,
  * osobní rekord = vyšší odhad 1RM než dosavadní maximum (první zápis rekord není),
  * **týdenní objem**: tvrdé série na partii (hlavní sval 1, pomocný 0,5) proti doporučeným
    10–20 sérií týdně pro růst (Schoenfeld, Ogborn & Krieger 2017).
* **UI**: v detailu cviku sekce „Můj deník“ – rekord, návrh na příště s vysvětlením, minulý
  trénink, dnešní série (křížkem smazat) a „Zapsat sérii“ (panel s krokováním váhy a opakování,
  předvyplněný poslední sérií nebo návrhem, živý odhad 1RM a upozornění na rekord).
  V atlasu u partie řádek „Deník: tento týden N sérií“ s hodnocením objemu.

## Důsledky
* Deník je zatím jen u cviků z knihovny (51 cviků); vlastní cviky by vyžadovaly další tabulku.
* Série naměřené kamerou (ADR 0005) se zatím do deníku nepropisují – přirozený další krok.
* Návrh je záměrně jednoduchý a vysvětlitelný; nezohledňuje RIR/RPE ani deload.

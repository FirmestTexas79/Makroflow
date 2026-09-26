# 0042 – Přechody mezi lokacemi

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-26

## Rozhodnutí
* **Vlastní pixelová scéna přechodu** pro každou hlavní lokaci, stejně jako už má vstup do jeskyně (0013). Místo prolnutí se hraje podle cíle:

  | Cíl | Scéna |
  |---|---|
  | Město | zavřou se dřevěné městské brány s kováním a znakem Makroballu přes šev (dovřou se s otřesem), pak se otevřou do teplého světla |
  | Louka | ze země vyroste vysoká tráva, zašpičatělá stébla ve dvou vrstvách s kvítím a motýly; pak ji poryv větru rozhrne od středu |
  | Hvozd | zprava se přižene vichr listí (náhodně překrývané lístky, občas podzimní), ve tmě blikají světlušky, pak listí odletí doleva |
  | Hory | obrazovku zahalí mraky, padá sníh a v mlze prosvitnou zasněžené štíty; pak mlha odtaje |
* **Scéna končí otevřením**: průhledné pixely nechají prosvítat novou mapu, takže mapa se neobjeví prolnutím, ale brány se rozevřou, tráva rozhrne atd. Pod plně zakrytou obrazovkou (`coveredAt`) se vymění mapa.
* **Kód**:
  * Scény jsou čistý Kotlin (`pokemon/transition/LocationTransitions.kt`) a kreslí se v nízkém rozlišení (~120 px na šířku) bez vyhlazení.
  * `LocationTransitionView` je přehrává.
  * Testy hlídají, že každá scéna na začátku nechá mapu vidět, v `coveredAt` zakryje 100 % obrazovky a na konci je celá průhledná.
* Jeskyně si nechávají svůj přechod. Ostatní lokace (voda) zůstávají u prolnutí.

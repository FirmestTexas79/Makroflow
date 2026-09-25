# 0022 – Nastavení aplikace a přeuspořádaná Historie

**Stav:** přijato · **Datum:** 2026-09-25

## Kontext
* Aplikace neměla uživatelské nastavení. Upozornění (ranní rituál, trénink, voda, večerní přehled,
  série) chodila vždy a v pevné časy, zvuk Makrosvěta šel vypnout jen tlačítkem v mapě (hudba
  i efekty najednou) a parťák na spodní liště nešel schovat, i když zakrývá spodní část obrazovek.
* Promo kódy byly jen ve vývojářských nástrojích, které jsou od ADR 0018 skryté v release buildu
  → běžný uživatel kód neměl kde uplatnit.
* Chyby v upozorněních: „série v ohrožení“ počítala všechny check-iny celkem (i s mezerami),
  voda měla cíl napevno 2,5 l místo osobního cíle a první připomínka vody mohla připadnout
  na čas v minulosti (vyskočila hned po spuštění).
* Historie: kalendář na celý měsíc a roční heatmapa zabíraly první dvě obrazovky, biologická
  projekce byla až úplně dole.

## Rozhodnutí
* **`AppSettings`** (SharedPreferences „SettingsPrefs“), výchozí hodnoty = dosavadní chování.
  **`Reminder`** + **`ReminderSchedule`** (čistý Kotlin, `RemindersTest`): typy upozornění, výchozí
  časy, další výskyt denního času, sloty vody 9–21 h po 2 h, délka série končící včerejškem.
* **Plánování**: `scheduleAll` naplánuje zapnutá a zruší vypnutá upozornění; volá se po každé
  změně v Nastavení. Receiver navíc kontroluje nastavení (staré alarmy po vypnutí nic neukážou).
* **Obrazovka Nastavení** (boční menu → Nastavení), ve stylu profilu:
  hlavní vypínač upozornění + každé zvlášť, u denních i čas (MakroflowTimePicker); karta
  s tlačítkem Povolit, když má systém upozornění aplikace vypnutá (Android 13+ dotaz na oprávnění,
  jinak systémové nastavení); hudba a zvukové efekty zvlášť (tlačítko v mapě přepíná obojí);
  parťák na obrazovce; promo kód; soukromí a zdraví; verze; vývojářské nástroje jen v debug.
* **Historie** – shora dolů podle toho, jak se stránka čte:
  1. výběr dne: kalendář ve výchozím stavu jen jako **týden** vybraného dne (šipky po týdnech,
     dopředu nejvýš do aktuálního týdne), „Celý měsíc ▾“ ho rozbalí; volba se pamatuje,
  2. **Váha a trend** – biologická projekce (ADR 0020),
  3. **Jídlo a pohyb** – kalorie a typ dne, makra, vláknina, kroky,
  4. **Tělo** – symetrie,
  5. **Celý rok** – heatmapa aktivity.
  Sekce oddělují stejné nadpisy jako v profilu. `CalendarWeek` (čistý Kotlin, `CalendarWeekTest`).

## Důsledky
* Uživatel si upozornění přizpůsobí místo toho, aby je vypnul celé v systému.
* Trvalé upozornění s kroky (foreground služba) se nevypíná – bez něj by se kroky na pozadí
  nepočítaly; na Androidu 14+ ho jde odsunout gestem.

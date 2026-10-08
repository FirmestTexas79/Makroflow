# 0071 – Pauza v okně se sériemi

Stav: přijato (2026-10-08)

- Ve Výběru tréninku → okno se sériemi (`WorkoutSessionSheet`) je dole připnutá lišta pauzy, vidět i při scrollování.
- Pauza běží od poslední dnes zapsané série; délka podle jejího cviku (`AppSettings.restSecondsFor`, 0069),
  stejná pravidla jako pilulka v Makrosvětu (`QuickWorkout.restLeft`: jen během tréninku, ne po Hotovo, max. 45 min).
- Během pauzy: „PAUZA · <CVIK>“, odpočet, ukazatel průběhu, „+30 s“ a ✕ (ukončit pauzu).
- Po konci: jantarová „PAUZA SKONČILA · Další série ▸“, dvojité zavibrování a pružné naskočení; po 90 s
  nebo klepnutím zmizí. Pauza, která skončila ještě před otevřením okna, nevibruje.

# 0081 – Zprávy o obraně ducha, kód trenéra, kamarádi

Stav: přijato (2026-10-09), větev `MULTI`

- **Zprávy**: po souboji proti cizímu duchovi zapíše vyzyvatel do `arena_results`
  `{attacker, attackerName, defender, won, at=serverTime}`. Majitel ducha je vidí v Aréně
  (posledních 20, řazení v telefonu – bez složeného indexu), nové jsou zvýrazněné
  (`arena_reports_seen`), u každé **ODVETA** = souboj s aktuálním duchem útočníka.
  Pravidla: zapsat smí jen útočník za sebe, číst/mazat jen útočník a obránce, upravit nikdo.
  Bez Cloud Functions → výsledek hlásí klient; podvrh jde jen „proti sobě“ (prohra/výhra
  v cizí zprávě nic neplatí), takže to stačí.
- **Odznak**: tlačítko Arény v Makrosvětě ukáže tečku, když jsou nové zprávy (dotaz při
  otevření mapy, nejvýš jednou za 10 min).
- **Kód trenéra**: 6 znaků odvozených z uid (`Arena.trainerCode`, abeceda z výměn bez 0/O/1/I),
  ukládá se do `arena/{uid}.code` při publikaci ducha. Vyzvání kódem = dotaz `whereEqualTo("code")`.
  Starší duchové kód dostanou, až majitel znovu otevře Arénu.
- **Kamarádi**: úspěšně zadaný kód se uloží lokálně (uid + jméno, max 20); v Aréně řada
  „Kamarádi“ s vyzváním jedním klepnutím, dlouhé podržení = odebrat.
- Při smazání účtu se mažou i zprávy, kde je uživatel obránce nebo útočník.

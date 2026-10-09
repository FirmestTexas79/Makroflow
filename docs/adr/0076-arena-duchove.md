# 0076 – Aréna: duchové hráčů a AI trenéři

Stav: přijato (2026-10-09), větev `MULTI`

Základ pro hru víc hráčů (výměny a souboje mezi trenéry v Kraji 2). Zatím jen asynchronní souboje:
hráč bojuje proti „duchovi“ – uloženému týmu jiného hráče, kterého řídí AI. Stejný mechanismus slouží
AI trenérům (dnes tři v aréně, později trenéři v příběhu).

## Základ (bod 1)

- **Stálé ID Makromona**: `CapturedMakromonEntity.uid` (UUID), migrace 40 → 41 dá stávajícím náhodné.
  Nahrává se do cloudu spolu s útoky (dřív se útoky do cloudu neukládaly). Připravené pro výměny.
- **Statistiky se neposílají.** Trenér nese jen druh, level, jména útoků a shiny (`TrainerMon`).
  Útok, obrana, HP i rychlost se dopočítají z druhu a levelu stejně jako u hráče (`Trainers.toBattle`).
- **Data z cloudu se ověřují** (`Trainers.sanitize`): jen hratelné druhy (ne strážci), level 1…30,
  útoky jen ty, které druh na svém levelu umí (`MovePool.pool`), nejvýš 6 Makromonů, jméno do 16 znaků.
- **Pravidla Firestore** jsou v repu (`firestore.rules`) a nasazují se ručně v konzoli. Data hráče jen
  pro něj, `promo_codes` jen ke čtení, `arena/{uid}` čte každý přihlášený, zapisuje jen vlastník
  s kontrolou tvaru (klíče, délka jména, 1–6 Makromonů, síla ≤ 180).
- Smazání účtu maže i ducha v aréně.

## Aréna (bod 2)

- Menu → Moje sbírka → **Aréna** (`trainer/ArenaFragment`).
- Karta „Tvůj tým“: aktivní parťák + tým (jako v souboji), síla = součet levelů, výhry/prohry.
  Jméno v aréně si hráč nastaví (výchozí křestní jméno z účtu, nikdy e-mail) – vidí ho ostatní.
- Po otevření se tým nahraje jako duch (`arena/{uid}`) a načte se 40 naposledy aktivních duchů;
  ukáže se 6 nejbližších síle hráče. Nepřihlášený hráč hraje jen proti AI trenérům.
- **AI trenéři**: Kája Kardio (o 1 Makromona méně, level −2, běžní), Béďa Benčpres (stejně), Mistr
  Mrtvý tah (o 1 víc, level +2, i vzácní a epičtí). Týmy rostou s hráčem a mění se každý den.
- **Souboj** (`PokemonBattleView` s parametrem `trainer`): úvod „VS“ s pruhy jmen, trenér posílá
  Makromony po jednom, nejde chytat ani utéct, předměty ano. Po každém poraženém Makromonovi XP
  (10 + 3 × level). Prohra nezabíjí postavu (žádný whiteout).
- **AI v souboji** (`TrainerAi`): v 75 % útok s největším očekávaným zraněním (síla × účinnost × přesnost),
  jinak stejná volba jako divocí. Imunní útoky nevybírá.
- **Odměna**: 5 + síla / 2 penízků (max. 40), jen za první výhru nad daným trenérem v den.

## Co zatím ne

- Výměny řeší 0077. Real-time PvP, srovnávání levelů,
  počítadlo obran ducha (zápis do cizího dokumentu by potřeboval další pravidla).

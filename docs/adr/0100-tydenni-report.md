# 0100 – Týdenní report

Stav: přijato (2026-11-05), navazuje na ADR 0022 (upozornění), 0003 (adaptivní výdej) a „Co se aplikace naučila“

- **Co:** obrazovka `WeeklyReportSheet` (Profil → Týdenní report, nedělní kartička na Dashboardu a nedělní upozornění v 19:00).
  Týden pondělí až neděle, šipky mezi týdny (12 týdnů zpět). V neděli je report předběžný, od pondělí hotový.
- **Obsah:** hlavní věta, pět oblastí (Jídlo, Trénink, Spánek, Tělo, Pohyb a voda) se stavem Dobré / V pořádku / Pozor / Bez dat,
  velké číslo a věta u každé, detail po klepnutí, rekordy týdne a nejvýš tři kroky na příští týden (jeden na oblast, zdraví první).
- **Zásady:**
  1. Trefa cíle, ne extrém (`Adherence`): kalorie hluboko pod cílem jsou „Pozor“, ne chvála.
  2. Chybějící data nejsou neúspěch: nezapsaný den se nepočítá, oblast s málo daty je „Bez dat“.
  3. Žádné jedno skóre týdne (tlak a svévolná čísla).
  4. Dnešek se do jídla, kroků a vody nebere, dokud neskončí; do plánu tréninku se počítá jen splněný.
- **Pravidla stavů** (konstanty v `WeeklyReportModel`): jídlo ≥ 3 dny dat, GOOD při ≥ 5 dnech a ≥ 4 dnech kalorií v pásmu, WARN pod 85 % nebo nad 120 % cíle;
  trénink GOOD splněno ≥ plán, WARN pod 60 %; spánek ≥ 4 noci, GOOD Ø ≥ 7 h a dluh < 4 h, WARN Ø < 6 h nebo dluh ≥ 8 h;
  tělo ≥ 3 vážení, hubnutí GOOD −0,25 až −1 % týdně, WARN rychleji než −1 %, nabírání GOOD +0,1 až +0,5 %, WARN nad +0,5 %, udržování WARN nad 1 kg;
  pohyb WARN při Ø krocích pod 70 % cíle, GOOD při cíli a vodě ≥ 5 dní (když se voda zapisuje).
- **Rekordy:** nejlepší odhad 1RM z jedné série týdne (`StrengthModel.setEstimate`) aspoň o 1 kg nad vším dřívějším, cvik s ≥ 2 staršími tréninky.
  Vyhlazený odhad se nepoužívá, protože rekord jednoho týdne schová.
- **Architektura:** `WeeklyReportModel` (čistý Kotlin, testy), `WeeklyReportData` (sběr z databáze, žádná nová tabulka, předchozí týden se přepočítá),
  `WeeklyReportSheet` (UI). Upozornění `Reminder.WEEKLY` jede přes stávající denní alarm a ukáže se jen v neděli (`NotificationReceiver.handleWeekly`).
- **Soukromí:** nic se neposílá ven a neukládá. Sdílený text (`exportText`) neobsahuje jméno, e-mail ani názvy jídel. Data safety se nemění.
- **Mimo rozsah:** sekce Makromoni (XP se po dnech neukládá, chtělo by novou tabulku), porovnání s průměrem čtyř týdnů.

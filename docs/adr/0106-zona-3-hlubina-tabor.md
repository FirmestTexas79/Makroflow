# 0106 – Zóna 3 (Akt III – Hlubina): Stanice 8

Stav: návrh (2026-11-25), první krok implementován. Celý návrh zóny: projekt `lore/akt3-hlubina-navrh.md`.

- Zóna 3 = region pod světem, kde visí Spící. 8 lokací se stupněm pohlcení 1/8 → 8/8: 1 Stanice 8 (hub) → 2 Pozorovatelna B
  → (3 Signální útes – vedlejší | 4 Tábor na svahu) → 5 Svah bezejmenných → (6 Chrám Lumenců | 7 Les pod korunou) → 8 Kmen.
  Střed kořenů (finále) mimo rozsah.
- **Sestup schválen:** klec v Sektoru Hlubiny (−2) se otevře den po prvním výhledu na Spící (Septovo „už brzy“;
  den v GamePrefs `abyss_seen_day`, starší hry bez něj se berou jako včera). Poprvé okno „Sestup schválen“,
  pak klec sjede do tábora (`zona3_tabor`, synchronizovaný příběhový klíč). Debug volný výtah ji pustí hned.
- Pojetí (přepracováno 2026-11-25): dutina pod světem se Spící, kterou se Řád pokusil obsadit; kořeny ho pohlcují
  tím víc, čím blíž ke kmeni. Každá lokace je vlastní mapa ve stylu pater Věže (stěna, podlaha, z-řazené sprity se
  stíny, tůně světla, živé detaily). Díly v `tools/mapgen/hlubina/station_parts.py`.
- **Stanice 8** (`ROOT_CAMP`, `RootCamp.kt`, `gen_station8.py`, 176 × 352): skalní římsa, šachta s klecí, beton
  se značením S-8, ubikace s vysílačkou, nástěnka OBSAZENÍ HLUBINY, pozorovatelna A, sodíkové stožáry, ohňová hlídka
  se spálenými kořeny, terminál lanovky, závora ke Svahu (zatím neschváleno), Hunfreyho křída, ruina Lumenců ve
  skále. Bez setkání. Živé detaily InteriorFxView.ROOT_CAMP (blikající reflektory, popel, doutnání, houby, mlha).
- Dokud zóna 3 nemá druhou lokaci, počítá se v deníku pod zónu 2 (záložka Zóna 3 přibude s ní).
- **Stavebnice zóny** (`tools/mapgen/hlubina/`): `common` (palety), `terrain`, `order`, `lumen`, `roots`, `signs`
  (písmo 5×7 s diakritikou; `plate()` smaltované cedule s nýty a stínem písmen, `painted()` šablona s přestřikem,
  oděrem a stékanci), `scene` (z-buffer po pixelech – kořen nebo předmět blíž se tím za ním nepřekreslí; stíny,
  světlo, export, BFS). Lokace = krátký skript `gen_<lokace>.py`.
- **Pozorovatelna B** (`ROOT_WATCH`, `RootWatch.kt`, `gen_watch_b.py`), pohlcení 2/8 – přepracováno: bunkr Řádu
  zapuštěný do skály a betonová terasa nad propastí se zábradlím (jako balkon), sestup po schodech Lumenců; světlomet
  v půlkruhu pytlů, dálkoměr, agregát, stojan plamenometů, nástěnný graf KONTROLA, polní telefon. Pravidlo zóny:
  každá cedule má nápis a je připevněná (zeď, zábradlí, sloupky – `order.mount_plate`). Setkání DUCH a ZEMĚ lv 28–32,
  intro WatchIntro (světlomet přejede terasu a zastaví se na očích). Závora ze Stanice 8 zatím pouští jen debug
  volný výtah – podmínka přijde s fází
  „Výprava 8“ kapitoly VI. Východy k Lanovce nad roklí a k Táboru na svahu zatím nepřístupné.
- **Tábor na svahu** (`ROOT_SLOPECAMP`, `RootSlopeCamp.kt`, `gen_slope_camp.py`), pohlcení 4/8: fáze 3 obsazení –
  opuštěno; registrační kniha (jména → čísla), polní kuchyně („NEZAPOMEŇTE JÍST“), stany výprav 1, 3, 5, 7 s deníky
  (měření → tep kořenů → zapomínání, „Kdo spí, pamatuje si“ → „Nepočítejte je.“), kořen středem tábora se spolknutými
  stany 2, 4, 6 a prkennou lávkou, sčítací tabule, naviják, kolíky do mlhy, žebra dlouhého domu Lumenců ve skále.
  Hunfrey sem nedošel (držíme jeho „Byl jsem až tady.“ ze Stanice 8). Setkání DUCH lv 30–34, intro SlopeCampIntro.
  Vstup z Pozorovatelny B schody dolů; dolů na Svah zatím nepřístupné.
- **Signální útes** (`ROOT_SIGNAL`, `RootSignal.kt`, `gen_signal.py`), vedlejší lokace místo plánované „Lanovky nad
  roklí“ – přepracováno: vysoká mapa 176 × 560 (kamera jede s hráčem), stezka podél stěny stoupá přes lávku na ostroh;
  vlevo v pozadí, daleko a ve stínu, celá Spící (`hlubina/tree.py`: souvislé větve s plynulým ohybem a pravidlem
  trubek, jemné větvičky do mlhy, vzdušná perspektiva) – původně výhled na celou Spící (kořeny po stropě, kmen s ránou a
  zelenou mlhou, obrácená koruna, kopí a šest světel – Trpitel jen v náznaku), lana lanovky s uvízlou gondolou.
  Signální lampa, bouda signalisty, Kniha signálů, lavička. Vedlejší úkol **Signalista** (`story/Signal.kt`,
  `SIGNAL_QUEST`): Kesh (Lumex) nesl lucernu výpravy 7, jednou ráno promluvil a výprava byla pryč; každou noc
  signalizuje ke kmeni a ve dne rozsvěcí světlomet na Pozorovatelně B. Fáze: dynamo (6 000 kroků za den) → spánek
  (2 noci ≥ 6 h – kdo hlídá, zapomíná) → odpověď (u lampy: z kmene blikne šest zelených světel – nebyla to výprava 7).
  Setkání DUCH a PSYCHO lv 30–34, intro SignalIntro. Grafika zóny: cedule s tloušťkou, leskem a špínou, sloupky
  s hlavicí a objímkami, stany v pohledu 3/4 shora (dvě plochy střechy, štít se vchodem), nová polní kuchyně
  a registrační stolek.
- Živé detaily: nový efekt `falls` (kůra odpadlá od vzdáleného stromu pomalu padá do mlhy) vedle létavců, drolení
  hrany, mlhy, pulzující rány a blikající clony signální lampy.
- **Kořenový most** (`ROOT_BRIDGE`, `RootBridge.kt`, `gen_root_bridge.py`), vedlejší lokace blíž ke kmeni, pohlcení
  6/8. Gondola ze Signálního útesu jede až po odpovědi šesti světel (Kesh přestřihne plombu). Vysoká mapa 176 × 560:
  plošina konečné stanice na kořeni, který se klene přes propast; nahoře celou šířku kmen (kůra jako kost, svislé
  rýhy, zelené světlo v nich, ocelové kotvy s utrženými řetězy). Sklad Operace Pečeť napůl v kůře: nádrže s pěnou,
  potrubí přes hranu dolů k ráně, ventil, lešení přišroubované do kmene. Svatyňka Lumenců: trn s nabodnutou drobnou
  světélkující bytostí, „AŤ NIKDO NENÍ SÁM“. U paty kmene obrys člověka vrostlý do kůry. Inspirace SCP-6666
  (víla na stromě, stavby vrostlé do koruny, sdílené noční můry, spáči dýchající naráz) – nic se nekopíruje.
  Vedlejší úkol **Noční směna** (`story/NightShift.kt`, `NIGHT_SHIFT_QUEST`): technik Orrin z výpravy 8 namlouval
  povinná hlášení o snech a schoval je před Řádem. Fáze: dvě schované pásky (svatyňka, lešení) → utažený ventil
  (2 dny s tréninkem) → hlášení 41: šestioký Orrinovi ve snu řekl, ať pásku nechá pro Okruh 213, „ať se vyspí, než
  sejde až dolů“. Obrys v kůře je Orrin. Setkání PŘÍRODA a PSYCHO lv 32–36, intro BridgeIntro (bytost na trnu
  zhasne, v propasti oči), živé detaily `InteriorFxView.ROOT_BRIDGE`. Portrét magnetofonu `npc_paska.png`.

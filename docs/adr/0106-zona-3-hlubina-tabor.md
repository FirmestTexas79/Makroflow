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
- **Svah bezejmenných** (`ROOT_NAMELESS`, `RootNameless.kt`, `gen_nameless.py`), lokace 5, pohlcení 5/8 – dvě perspektivy:
  vysoká mapa 176 × 560, hráč sestupuje po širokém svahu (80 %, 3/4 shora), vpravo za srázem úzký výhled (20 %) na Spící
  blízko (kmen, rána, kopí a šest světel jen náznakem) a pod korunou v mlze druhý svah, ze kterého se těla rozbíhají
  od stromu na všechny strany. Výtah nebyl jediná cesta ven: vlevo stěna dutiny s puklinami a jeskyněmi (u ústí hromady
  těl hlavou dovnitř, vyškrábané VEN, kořeny ústí zarostly). Těla různá (`order.victim`: plazí se, natažená ruka, na
  zádech, na boku, v dece, sedící; kombinéza Řádu, kabát, bunda, technická; batohy, přilby), všechna míří pryč od stromu;
  devět V7 (Keshova výprava, mezera po nosiči lucerny). Trosky Řádu: kolíky s přetrženým lanem, zlámaný stativ,
  baterka, která pořád svítí, sčítací tabule rovně na sloupcích, NEPOČÍTEJTE JE přes skalní desku na stezce, vodočetná
  lať VODA ve vypité tůňce. Hrůza je v tom, že zapomněli jíst – hra hladovění neodměňuje. Kořeny pijí pramínky.
  Dole schody Lumenců a žebra dlouhého domu (Chrám), kolíky do mlhy (Les) – zatím nepřístupné. Setkání DUCH a ZEMĚ
  lv 32–36, intro NamelessIntro, živé detaily `InteriorFxView.ROOT_NAMELESS`. Opraveno: texty Tábora zobrazovaly
  doslovné „\n“; uzly Kořenového mostu chyběly v seznamu akčních uzlů mapy.
- **Zbývá** (schváleno 2026-11-25): Chrám Lumenců (6/8), Les pod korunou (7/8 – lokace před finálním bohem, souboj
  s Drakirrou), Kmen (8/8 – Trpitel ve Stromu s kopím, volba konce) a nová finální zenová lokace Střed kořenů.
- **Mapa Zóna 3 v deníku** (`gen_zone3.py` → `assets/zone3`, `ZoneOne.ZONE3`): šest lokací sestupuje shora dolů
  v propasti (tma se zelenou září), spoje Stanice 8 → Pozorovatelna B → (Signální útes → gondola → Kořenový most |
  Tábor na svahu → Svah bezejmenných). Teleport a objevování jako v zónách 1 a 2 (příznaky návštěvy zóny 3), test
  `ZoneThreeTest`. Lokace zóny 3 už nespadají pod interiéry zóny 2.
- Strom ve sjezdové cutscéně a na patře −2 kreslí `hlubina/tree.py` (souvislé větve místo rozkouskovaných), mosazná
  deska na pultu je skutečná šikmá deska na sloupku s rytým nápisem, cedule HLUBINA ve skále smaltovaná.
  NEPOČÍTEJTE JE na Svahu je napsané prstem krví (nestejný tah, stékající kapky, otisk dlaně).
- Debug: **Dev keycard** v ladicím menu mapy otevře klec na −2, závoru UZÁVĚRA a gondolu ke Kořenovému mostu bez
  příběhových podmínek.
- **Chrám Lumenců** (`ROOT_TEMPLE`, `RootTemple.kt`, `gen_temple.py`), lokace 6, pohlcení 6/8. Ze Svahu bezejmenných
  po schodech Lumenců (uzel `sb_chram`) do lodi chrámu skoro celého v kořenech: kořeny stropem a zdmi, kolem sloupů,
  zvedají dlažbu, dva sloupy ulomené, kusy dříků na zemi. Na zadní stěně čtyři malby (strom s hvězdou a lidé se světlem
  → král bez obličeje šplhá pro hvězdu → král ji sní a strom se obrací → žena ve stromě pod světem, lidé odcházejí do
  tmy; pod ní „AŤ NÁS NIKDO NENAJDE“, odkaz na studnu Věže). Oltář přání (mísa drobností, jiskry, které na chvíli
  něco připomenou), bezhlavá socha jelena ve sloupu světla z prasklé klenby (kořeny se před ní zastaví), jediný kolík
  Řádu NEINVENTARIZOVAT s razítkem S-0. Bez prozrazení, že Makromoni byli lidé. Setkání PSYCHO a VÍLA lv 34–38,
  intro TempleIntro, živé detaily `InteriorFxView.ROOT_TEMPLE`, díly `lumen.column/column_drum/deer_statue/wish_altar`.
  Na mapě zóny 3 vpravo dole.
- **Les pod korunou** (`ROOT_FOREST`, `RootForest.kt`, `gen_crown_forest.py`), lokace 7, pohlcení 7/8 – poslední před
  Kmenem. Vysoká mapa 176 × 560; vstup ze Svahu bezejmenných (uzel `sb_les`). Obrácený les: holé větve koruny visí shora
  (nahoře mizí ve tmě), kroutí se, dole se rozpadají na záclony větviček, které se dotknou země a svítí. Mlha zapomnění
  z rány ve vrstvách (nejhustší na mýtině ticha), tůňky s odrazy světel, stopy v kruhu, kolík se třemi směrovkami VEN
  (každá jinam), ztracená sonda Řádu (pípá, 4 113 neodeslaných hlášení), značky na kůře, které se rozmazávají (přečíst
  je půjde po spánku – fáze kap. VI), zářezy nehtem. Dole nejtlustší větev skloněná k zemi se stupy – cesta ke Kmeni;
  přes ni leží **Drakirra** (NPC `makromon_19_drakirra`; vidí, neodletí, souboj přijde s kapitolou VI). Setkání PŘÍRODA
  a JED lv 34–38, intro ForestIntro3, živé detaily `InteriorFxView.ROOT_FOREST` (padající kůra, žár u Drakirry).
  Mapa zóny 3 se protáhla dolů (Les pod Svahem).
- **Práh** (`ROOT_THRESHOLD`, `RootThreshold.kt`, `gen_threshold.py`) – samostatná aréna posledního souboje s Drakirrou
  u paty koruny (podle arén Elden Ringu: kořeny stromu, zatopená síň s trůnem). Z Lesa pod korunou branou dvou
  zkřížených větví (`lp_brana`, `lp_prah`; vypálený kruh v lese zrušen). Vysoká mapa 176 × 560: vzadu se hlavní větve
  koruny spouštějí do vody a splétají v bledou stěnu jako žebra katedrály (`tree.py`, tři vrstvy hloubky), uprostřed
  trůn Lumenců se sochou ženy v kápi (Lumira; obličej nevytesaný, ruce sepjaté), za trůnem větev se stupy ke kmeni.
  Shora do středu padá sloup bledého světla z rány, mělké černé jezero zrcadlí stěnu, nad hladinou mlha, padají
  světélkující šupinky kůry. V mělčině klečí kamenní Lumenci čelem k trůnu (někteří bez hlav), dva obří ulomené sloupy,
  procesní cesta z kamenných desek (místy pod vodou) lemovaná sloupy s modrými plameny vede k ostrovu s kruhem znaků
  a obrácenou korunou – tam leží Drakirra. Rám tvoří obří tmavé větve v popředí. Bez divokých setkání, intro
  ThresholdIntro (světlo, chvění hladiny, oči v páře, jiskry), živé detaily `InteriorFxView.ROOT_THRESHOLD`.
  `story/DrakirraFinal.kt`: dialog před soubojem (Drakirra vstane z ostrova, voda syčí, dívá se na Spirru, „Tu první
  skořápku už jednou ztratili. Druhou jim nedá.“), volba Postavit se / Ještě ne; `SpecialBattle.DRAKIRRA_FINAL` (lv 42,
  bez řetězů, nejde chytit ani utéct, souboj na pozadí Prahu). Po výhře příznak `drakirra_prah_porazena`, Drakirra
  leží na boku v mělčině, jednou se ukáže dohra (Spirra si k ní lehne, ze sondy za branou Sept: „Test síly nádoby:
  úspěšný. Podruhé… Sestup je schválen.“). Větev za trůnem pak vede ke Kmeni (Trpitel – další krok).

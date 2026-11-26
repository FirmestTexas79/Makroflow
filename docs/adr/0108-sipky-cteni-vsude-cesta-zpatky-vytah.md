# 0108 – Šipky a čtení všude, sebrání s animací, Cesta zpátky, výtah nad propastí

## Kontext
Šipky východů a prozkoumávání podržením byly jen v Hlubině a ve Věži. U sběratelských předmětů se třpyt rozpadal na bílé
a azurové hvězdy daleko od sebe (vypadalo to jako dva předměty) a sebrání nemělo žádnou zpětnou vazbu – panel vyskočil
hned, jako bychom předmět měli odjakživa. Setkání v Hlubině nebyla na mapě vidět, závora ve Stanici 8 pouštěla jen debug
a železný výtah nad propastí v Ozvěně byl jen cedulka.

## Rozhodnutí
- **Šipky všude** (`cave/ExitMarkers.EXITS`): každá lokace (Město, Louka, Hory, jeskyně, Hvozd, háj, průsmyk, pláň, sad,
  kaňon, Ozvěna, Aréna, vestibul, patra Věže, Hlubina). Grafové mapy na celou obrazovku počítají „art pixel“ jako šířku/160.
  Šedá šipka: Věž bez Pozvánky, výtah nad propastí před výhledem z −2, Hory bez denních kroků, závora S-8, puklina do Středu.
  Test: každá lokace má šipky a každý východ je uzel své mapy.
- **Čtení všude**: popisy míst zón 1 a 2 a vestibulu (průsmyk, doly, háj, pláň, Ozvěna, vestibul, Aréna, kaňon, sad) jsou
  v `inspectExtra` (texty se stavem – Vhled, den, fáze úkolu) a čtou se podržením. Klepnutí jen dojde a poradí.
- **Jeden třpyt = jedno místo**: `InspectGlitterView` kreslí u místa jednu pulzující hvězdičku a dvě jiskry do ±3 px v jedné
  barvě. Místo s čekajícím roztrženým listem třpyt nemá (list má vlastní třpytky). Záře jmenovky sedí na středu plíšku.
- **Sebrání s animací** (`inspect/PickupFx`): záblesk, rozlet pixelů po celých pixelech mapy, předmět povyskočí a zmizí,
  stoupá popisek („🏷️ 3/7“) – panel vyjede až po ~0,6 s. Jmenovky, lucerny, roztržené listy, útržky Hunfreyho mapy, cívky.
- **Oči ve tmě**: u míst setkání v Hlubině se občas rozsvítí pár očí (barva podle lokace) – je vidět, kde něco žije.
- **Závora S-8**: odjistí ji Sept z vysílačky v ubikaci – stačí ji vyslechnout (podržet prst). Do té doby šedá šipka a nápověda.
- **Cesta zpátky** (`story/Lanterns`, vedlejší úkol Keshe po Signalistovi): sedm zhaslých luceren výprav ve Stanici 8,
  Pozorovatelně B, na Kořenovém mostě, v Táboře na svahu, na Svahu bezejmenných, v Chrámu a v Lese. Zapálené zůstanou
  svítit. Pak zpět ke Keshovi – rozsvícená tečkovaná čára od stromu nahoru. Propojuje Keshe, Orrina (lucerna u kotvy)
  a výpravy 1–7. Nic se neodměňuje nespaním; poslední věta posílá spát.
- **Výtah nad propastí** (`story/AbyssLift`): po výhledu na Spící z −2 je kolonka SUBJEKT PŘIPRAVEN vyplněná (213) a výtah
  sjede z náměstí Ozvěny k nové kleci vlevo od balkonu Sektoru Hlubiny (`TowerAbyss.LIFT_NODE`, gen_abyss.py) – níž,
  ke koruně Lumiry. První jízda má scénu. Zpátky stejnou klecí. Zkratka mimo Věž, ne náhrada příběhu.

## Důsledky
Další lokace musí mít záznam v `ExitMarkers.EXITS` (hlídá test). Nové sběratelské předměty volají `collectFx`/`PickupFx`
před panelem. Lucerny jsou příběhové klíče (`StoryProgress`), synchronizují se jako ostatní.

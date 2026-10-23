# 0096 – Ztichlý sad: Zahradník, který sázel jména (část 1) a cutscény přes kód

Stav: přijato (2026-10-23), větev `MULTI`, Akt II

Od kapitoly V mají questy úroveň „Fable / Crimson Desert“: příběh rozložený do dne a noci, volby s následky,
cutscény. Tohle je první taková linka, zatím **prolog až fáze II** (`story/Fenn.kt`, quest `orchard_fenn`).

- **Teplý košík** (ve dne): klepnutí na chalupu → seznam 12 stromů se škrtnutými jmény, poslední rozmazané „F…n“
  (vypravěč = portrét chalupy `npc_orchard_hut.png` z `gen_orchard.py`). Cíl: zeptat se babičky Nelle v Ozvěně.
- **Kdo sbírá jablka**: Nelle (Flori) – jablka leží každé ráno v košíku „od nikoho“, v noci tam někdo zalévá.
  Cíl: vrátit se do sadu po setmění (19:00–5:00, stejné okno jako noční mapa).
- **Cutscéna „Světlo mezi stromy“** se přehraje při prvním nočním příchodu: noční mapa sadu, světlušky, modrý plamínek
  dává stromům dobrou noc jmény, která se nedají dočíst (▒ se přesýpá), a u chalupy se z něj stane plamen na hlavě
  zahradníka **Fenna** (Kendl). Debug menu: „🕯 Cutscéna: Světlo mezi stromy“ a „🕯 Reset questu Fenna“.
- **Jména stromů**: tři stopy – studna (v noci, Ilse), úly (ve dne, Odo), jezírko (v noci, Maren). Bez Pamětní knihy
  od Vessy se jméno nedá zapsat. U každé stopy **volba**: říct Fennovi celou pravdu, nebo jen jméno
  (`<stopa>_truth`; počet pravd rozhodne ve finále). Úly a jezírko mají ve správnou denní dobu přednost před setkáním.
- Fenn v noci stojí u chalupy (uzel `fenn`) s modrou září; ve dne je tam jen konev.
- **Nové obecné prvky**: `CutsceneView` (kamera nad pixelovou mapou, herci, černé pruhy, titulky, prolínání,
  Přeskočit), požadavek `FLAGS_COUNT` (kolik příznaků z výčtu), `QuestProgression.reopenIfExtended` – hotový quest
  s novými fázemi se při načtení znovu otevře (pokračování příběhu), `placeMakromonNpc` pro Makromony na mapě.
- Další část: Co ví Nelle (volba pravda/fasáda), Zalévání (denní cíl vody 3 dny), Noc bez měsíce (cutscéna
  Bezejmenných, konec A – Fenn zůstane, zmizí Tobben; konec B – Fenn odejde do Staré jabloně; skrytý C).
  Pamětník je odměnou hlavní linky – za ním hráče pošlou, až splní její hlavní část.

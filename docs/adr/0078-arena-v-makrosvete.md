# 0078 – Aréna v Makrosvětě

Stav: přijato (2026-10-09), větev `MULTI`

- Aréna (0076) a výměny (0077) se přesunuly z menu aplikace do Makrosvěta: tlačítko se zkříženými
  meči na mapě (sloupec vpravo nahoře, mezi zvukem a nápovědou), dostupné ze všech lokací.
- **Gladiátorská aréna** (`ArenaTheme.COLOSSEUM`): písek s uhlazenými skvrnami, val soupeře, podium
  z pískovce s mramorovou římsou a rudými prapory, brána s mříží, šest stupňů hlediště s diváky
  (barevné postavičky), vnější zeď se dvěma patry oblouků, ohniště. Slouží jako pozadí všech
  soubojů s trenéry (duchové i AI).
- **Obrazovka Arény** (`trainer/ArenaFragment` + `ArenaStageView`): nahoře stejná 3D aréna přes
  celou šířku, na písku stojí čelem k sobě tvůj parťák (zrcadlený) a vedoucí Makromon soupeře,
  oba lehce pohupují. Klepnutí na kartu soupeře ho postaví na písek. Dole dřevěný panel jako ostatní
  menu Makrosvěta (WoodUi, karty WorkshopMenus): tvůj tým, výměna, trenéři arény, duchové hráčů.
  Souboj otevře na místě arény, po něm se vrátíš zpět.
- Jméno v aréně se mění v dřevěném dialogu. Náhled arény do PNG: `ColosseumPreviewTest`
  (`app/build/arena-preview/colosseum.png`).

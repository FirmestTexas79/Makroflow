# 0070 – Přechod do Makrosvěta

Stav: přijato (2026-10-08)

Krátký přechod (~1,9 s) místo prostého prolnutí, stejná scéna jako průvod na webu. `pokemon/PortalTransitionView`, Canvas:
- aplikace (`MainActivity.openMakromonBattle`, 1,3 s): mřížka 9 sloupců pixelových dlaždic se od středu
  kosočtvercem poskládá do noční scény (nebe, tráva `portal_grass`, keře `portal_bush_a/b` z webu),
  naskočí MAKROSVĚT (Jersey 15) s jantarovou linkou, keře zašustí a vyskočí z nich 5 náhodných makromonů,
  kteří s poskakováním přeběhnou obrazovku (prach od tlapek, lístky z keře). Směr pohledu spritu
  podle seznamů FACES_LEFT / FACES_RIGHT z webu; sprity se dekódují mimo hlavní vlákno (inSampleSize 2).
- mapa (extra `portal`): stejná scéna bez makromonů chvíli drží a od středu se rozpadne (0,48 s).
  Overlay během přechodu chytá dotyky.
- Dlouhá verze pro první vstup do Makrosvěta je odložená; přidá se jako další režim stejného view.

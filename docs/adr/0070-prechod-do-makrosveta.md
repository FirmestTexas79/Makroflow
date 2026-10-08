# 0070 – Přechod do Makrosvěta

Stav: přijato (2026-10-08)

Krátký přechod (~3,3 s) místo prostého prolnutí, stejná scéna jako průvod na webu. `pokemon/PortalTransitionView`, Canvas:
- aplikace (`MainActivity.openMakromonBattle`, 2,45 s): mřížka 9 sloupců pixelových dlaždic se od středu
  kosočtvercem poskládá do noční scény (nebe, tráva `portal_grass`, keře `portal_bush_a/b` z webu),
  naskočí MAKROSVĚT (Jersey 15) s jantarovou linkou, keře zašustí a vyskočí z nich 4 náhodní makromoni,
  kteří s poskakováním přeběhnou obrazovku (prach od tlapek, lístky z keře). Směr pohledu spritu
  podle seznamů FACES_LEFT / FACES_RIGHT z webu; sprity se dekódují mimo hlavní vlákno (inSampleSize 2).
- mapa (extra `portal`): stejná scéna bez makromonů chvíli drží a od středu se rozpadne (0,8 s).
- Sova (Johnsova) a Drakirra přeletí oblohou (vlnovka, mávání křídly), Gudwin sedí v trávě a spí
  (dýchá, stoupají z něj Z) – i v odkrývací polovině.
  Overlay během přechodu chytá dotyky.
- Odchod (každý `finish()` mapy – ✕, zpět, „Zpět na trénink“): nad mapou se složí stejná scéna s nápisem
  MAKROFLOW (0,44 s), přeletí sova a 3 makromoni přiběhnou od okrajů a schovají se do keřů (lístky);
  celkem 1,75 s. Aplikace v onResume scénu rozpustí (`PortalTransitionView.pendingExitReveal`).
- Dlouhá verze pro první vstup do Makrosvěta je odložená; přidá se jako další režim stejného view.

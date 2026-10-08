# 0070 – Přechod do Makrosvěta

Stav: přijato (2026-10-08)

Krátký přechod (~1,2 s) místo prostého prolnutí. `pokemon/PortalTransitionView`, Canvas, bez assetů:
- aplikace (`MainActivity.openMakromonBattle`): mřížka 9 sloupců pixelových dlaždic (šachovnice
  #283618 / #222E14) se poskládá od středu kosočtvercem (480 ms), naskočí nápis MAKROSVĚT (Jersey 15)
  s jantarovou linkou, pak se bez systémové animace spustí mapa,
- mapa (extra `portal`): stejná mřížka chvíli drží (160 ms), nápis vybledne a dlaždice se od středu
  rozpadnou (620 ms). Overlay během přechodu chytá dotyky.
- Dlouhá verze pro první vstup do Makrosvěta je odložená; přidá se jako další režim stejného view.

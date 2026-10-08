# 0073 – Kategorie ve špajzce

Stav: přijato (2026-10-08)

- 15 kategorií (`nutrition/FoodCategory`): maso a uzeniny, ryby, mléčné a vejce, luštěniny a tofu,
  obiloviny a přílohy, pečivo, zelenina, ovoce, ořechy a semínka, tuky a oleje, doplňky a proteiny,
  hotová jídla, sladké a pochutiny, nápoje, ostatní. Každá má emoji a barvu.
- **Zařazení samo podle názvu**, bez volby uživatele a bez sloupce v DB (počítá se při zobrazení,
  s cache): slova bez diakritiky, pravidlo = slovo začíná kmenem („=slovo“ celé slovo, „dvě slova“
  spojení). Nejdřív výjimky (rybíz není ryba, arašídové máslo jsou ořechy, kokosový olej tuk,
  bramburky sladké, houskové knedlíky příloha, syrová rýže není sýr), pak kategorie od nejjednoznačnějších.
  Nic nesedí → s mililitry nápoj, jinak Ostatní. Test hlídá výchozí špajzku (nejvýš 2 bez kategorie).
- Formulář potraviny ukazuje pod názvem živě „Zařadí se do: 🍎 Ovoce“.
- GUI: pod hledáním vodorovné dlaždice (Vše + kategorie s počty, vybraná v barvě kategorie, pruží
  a dojede do záběru); seznam po oddílech s ikonou, počtem a barevnou linkou; řádek má ikonu kategorie
  uvnitř prstence maker. Oblíbené jen ve „Vše“ bez hledání. Detail porce má štítek kategorie.
- Ideální den (0069): talíře = maso/ryby/luštěniny × obiloviny (+ zelenina), samotné potraviny jen
  z mléčných, ovoce, ořechů, doplňků a pečiva.

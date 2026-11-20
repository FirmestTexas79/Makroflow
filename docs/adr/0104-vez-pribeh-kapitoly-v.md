# 0104 – Věž Řádu Váhy: příběhový průchod patry až do Sektoru Hlubiny

Stav: návrh (2026-11-20), čeká na schválení v bibli příběhu.

## Kontext
Patra 2–13 Věže byla hotová jen jako mapy (debug výtah). Bible kapitoly V uvádí po Kalibraci: Spis 212 → … →
Subjekt připraven, s vyvrcholením pod světem nad obrácenou korunou Lumiry (Spící).

## Rozhodnutí
- Kapitola V (quest `ozvena_chapter`) pokračuje 16 fázemi za Kalibrací (`TowerStory`, `QuestRegistry.TOWER_STAGES`);
  hotové hráče znovu otevře `QuestProgression.reopenIfExtended`.
- Každé patro schválí „měření“: skutečný návyk nebo souboje na patře + příběhová stopa. Spánek je jádro
  (Spis 212 – 3 noci ≥ 3 h, Dormitoř – 3 noci ≥ 6 h, Mrazírna – Hunfreyho útěk). Nikdy váha ani tvar těla.
- Nové typy fází: `SLEEP_NIGHTS` (noci od začátku fáze, targetId = hodiny), `TRAINING_DAYS` (dny s tréninkem
  od začátku fáze, stejné pravidlo jako Pět Mistrů – `MastersStore.trainingDays`).
- Výtah pouští jen schválená patra (`TowerStory.approved`): patro N od fáze FIRST+N−1, −1 po složení Hunfreyho
  mapy (5 útržků na patrech 4, 6, 7, 9, 13), −2 po dokončení kapitoly (jeden vyvážený den – HIT_TARGET kcal).
- Linka Hunfreyho: Spis 212 → křída pod vahami Měřírny → útržky mapy → prázdná komora 212 (útěk) → Registr nádob
  („ZTRACEN, PATRO −1“) → skrýš na −1 (sešit, 43 čárek, maska 212) → −2.
- Odhalení po patrech: kontrolní výsadby (patra = opsaný svět), krystaly „OBJEKT 019“, ohořelé stránky o Spící,
  studna přání („AŤ NÁS NIKDO NENAJDE“ – Lumenci, bez prozrazení, že Makromoni byli lidé), pára ZAPOMNĚNÍ voní
  Brannovým chlebem (smyčka Ozvěny).
- Nová patra: −1 Hunfreyho skrýš (`gen_hideout.py`, `TowerHideout`), −2 Sektor Hlubiny (`gen_abyss.py`,
  `TowerAbyss`) – lávka nad propastí, Lumira hlavou dolů, rudé nitě stanic, kopí v kmeni, klec „SESTUP ZATÍM
  NESCHVÁLEN“ (háček na Akt III).

- **Objekt 019** (fáze za Registrem nádob): v Tréninkovém koutě Arény souboj se spoutanou Drakirrou
  (`SpecialBattle.OBJEKT_019`, lv 30, řetězy: útok ×0,55, rychlost ×0,5, nejde chytit ani utéct). Po výhře v noci
  vyhoří Kancelář Řádu – `tower_office_burned.png` (`gen_office_burned.py`, stejné rozložení), vlastní texty
  (`TowerOffice.BURNED_TEXTS`) a živé detaily `InteriorFxView.OFFICE_BURNED`.

## Mimo rozsah (další krok)
Cutscéna sjezdu na −2. Hunfrey v HUD je hráčův vlastní Makromon (druh 042), jeho „zmizení“ se nedělá.

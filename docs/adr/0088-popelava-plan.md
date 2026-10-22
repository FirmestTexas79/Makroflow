# 0088 – Popelavá pláň: první lokace aktu II

Stav: přijato (2026-10-22), větev `MULTI`, issue #26

- Nový biom `ASH_PLAIN` (`cave/AshPlain.kt`, mapa 160 × 448, kamera jezdí svisle jako v průsmyku).
  Uzly a hrany jsou zdroj pravdy v `AshPlain.kt` i v `tools/mapgen/gen_ashplain.py`.
- **Grafika ve stylu kapesních Pokémonů, skládaná po dílech** (2026-10-22, na přání po první verzi):
  `ashplain/tiles.py` = dlaždice 16 × 16 (popelavá tráva, cesta se zaoblenými rohy, vysoká suchá tráva,
  římsy, skála), `ashplain/objects.py` = samostatné sprity s obrysem a stínováním (popelavé a ohořelé stromy,
  zřícený dům, brána starého města s věžemi a erbem Řádu Váhy, cedule, lucerny, ohniště, strašák, mezník, kameny, keře;
  **Brána světů se nekreslí znovu, vyřízne se z hotové `sky_pass.png`** a dostane otevřený závoj a Srdce v lůžku),
  `gen_ashplain.py` = mřížka 10 × 28 buněk, objekty seřazené podle paty, mapa chůze z mřížky a kolizí objektů.
  Náhledy dílů: `ashplain/parts_tiles.png`, `parts_objects.png`.
- Vstup: po vložení Srdce Hvozdu má tabule Brány světů tlačítko **Projít branou** → hráč vyjde nahoře
  z Brány a sestupuje dolů k bráně Ozvěny (celý akt jde shora dolů). Zpět klepnutím na Bránu (`vstup_z_brany`).
- Vysoká tráva / obilí jsou nepravidelné porosty z trsů (ne čtverce dlaždic).
- Pět míst setkání (suchá tráva pod Branou, spálený háj, ohniště, sutiny, obilí se strašákem), divocí na lv 12–18 (nad doly 8–14).
  **Dokud nejsou nakreslení noví Makromoni (048+), spawnuje pláň horské druhy** (`wildBiome = MOUNTAINS`),
  hudba, intro souboje i aréna jsou horské.
- Cedule, mezník (s Vhledem ≥ 3 prozradí „S-0“) a zavřená vrata Ozvěny (otevře #27).
- Příznak `ash_plain_visited` je ve `StoryProgress.STORY_KEYS`.
- Mimo rozsah: fáze 1 kapitoly V (Sestup s Hunfreyem) přijde s questovou linkou v #28, zóna aktu II v deníku později.
- Závoj v Bráně světů se na pláni točí stejně jako v průsmyku (`SkyPassArt.veil`, střed `AshPlain.GATE_X/Y`);
  v obrázku mapy je uvnitř prstence jen tmavé nitro. Oblasti klepnutí u okrajů mapy jsou větší, aby šly
  trefit vedle ovládacích prvků (ukazatel energie vlevo, tlačítka vpravo).

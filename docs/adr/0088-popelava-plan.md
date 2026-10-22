# 0088 – Popelavá pláň: první lokace aktu II

Stav: přijato (2026-10-22), větev `MULTI`, issue #26

- Nový biom `ASH_PLAIN` (`cave/AshPlain.kt`, mapa 160 × 440 z `tools/mapgen/gen_ashplain.py`, kamera jezdí svisle
  jako v průsmyku). Uzly a hrany jsou zdroj pravdy v obou souborech.
- Vstup: po vložení Srdce Hvozdu má tabule Brány světů tlačítko **Projít branou** → pláň u zbořeného oblouku.
  Zpět se jde klepnutím na oblouk (`vstup_z_brany`) k Bráně v průsmyku.
- Pět míst setkání (duny, spálený háj, ohniště, sutiny, pole), divocí na lv 12–18 (nad doly 8–14).
  **Dokud nejsou nakreslení noví Makromoni (048+), spawnuje pláň horské druhy** (`wildBiome = MOUNTAINS`),
  hudba, intro souboje i aréna jsou horské.
- Cedule, mezník (s Vhledem ≥ 3 prozradí „S-0“) a zavřená vrata Ozvěny (otevře #27).
- Příznak `ash_plain_visited` je ve `StoryProgress.STORY_KEYS`.
- Mimo rozsah: fáze 1 kapitoly V (Sestup s Hunfreyem) přijde s questovou linkou v #28, zóna aktu II v deníku později.

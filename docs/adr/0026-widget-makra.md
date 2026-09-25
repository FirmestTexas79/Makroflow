# 0026 – Widget Makra 2×2 na plochu

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-25

## Kontext
Samuel chtěl mít přehled o dnešním jídle bez otevírání aplikace: widget 2×2, uprostřed logo,
po obvodu tři části pro bílkoviny, sacharidy a tuky a mezi dvěma spodními kolik kcal snědl nebo zbývá.

## Rozhodnutí
* **Vzhled**: prstenec ze tří oblouků – sacharidy vlevo, bílkoviny nahoře, tuky vpravo – se spodní
  mezerou 84° pro kalorie. Oblouk se plní podílem snědeného z denního cíle (levý a pravý zdola
  nahoru, horní zleva). Uvnitř u každého oblouku písmeno (B/S/T) a gramy. Barvy jako koláč maker
  na obrazovce Snacky, světlý i tmavý motiv podle systému.
* **Kalorie**: klepnutím na spodní část se přepíná *zbývá* (výchozí) ↔ *snědeno z cíle*;
  zvlášť pro každý widget. V režimu „zbývá“ se překročení ukazuje „+150 kcal navíc“ a u makra
  „+12“. Bez spočítaných cílů jen snědeno. Klepnutí jinam otevře aplikaci.
* **Technika**: RemoteViews neumí vlastní View, proto se kreslí do bitmapy (`MacroWidgetRenderer`,
  rozměry relativní ke straně, takže funguje i po zvětšení). Co ukázat počítá čistý
  `MacroWidgetModel` (úhly, podíly, texty, popis pro čtečku obrazovky) – pokryto testy.
  Data: dnešní `consumed_snacks` a cíle z `MacroCalculator` (stejné jako v aplikaci).
* **Obnova**: po zápisu/smazání jídla (`FoodLog`), při odchodu z aplikace (`MainActivity.onStop`
  – pokryje i check-in, profil, plán), nepřesný alarm po půlnoci a systémově každých 30 min
  (kroky mění výdej).
* **Debug náhled**: `WidgetPreviewActivity` (jen debug build) vykreslí ukázkové stavy a uloží PNG;
  s `--ez pin true` nabídne připnutí widgetu na plochu. Obrázek do výběru widgetů je z něj.

## Ověření
Testy modelu (8). Na emulátoru Pixel 9 Pro: připnutí widgetu, vykreslení s výchozím profilem,
přepínání snědeno/zbývá klepnutím (změní se popis pro čtečku). Na Samuelově telefonu zatím ne.

## Důsledky
* Bitmapa se posílá při každé obnově (~150–600 px, desítky kB) – u widgetu v pořádku.
* Změna cílů během dne (kroky) se projeví nejpozději za 30 min nebo při odchodu z aplikace.

# ADR 0005 – Sledování činky kamerou

- **Stav:** přijato (2026-09)
- **Kód:** `training/analysis/` (čistý Kotlin: PlateTracker, SetDetector, RepAnalyzer, RepRating),
  UI `training/TrainerFragment`, `SetSummaryViews`, DB `barbell_sets` / `barbell_reps` (v36), PDF stránka v `ReportGenerator`

## Proč původní verze „blbla“
1. ML Kit object detector ve výchozím nastavení vrací **jen jeden** nejvýraznější objekt. To byl často člověk, takže kotouč chyběl.
2. Filtr poměru stran 0,90–1,15 vyřadil kotouč natočený o pár stupňů (elipsa).
3. Bral se vždy **největší** kulatý objekt ve snímku, bez zámku a bez omezení skoku. Sledování proto přeskakovalo na jiné předměty.
4. Analýza běžela na hlavním vlákně. Směr pohybu se určoval z rozdílu dvou snímků, takže reagoval na každý šum.
5. Data se nijak nezpracovávala: nebyly repy, fáze ani ukládání.

## Rozhodnutí
- **Detekce:** víc objektů najednou (`enableMultipleObjects`), tolerance poměru stran 0,7–1,4.
- **Sledování:** zámek na `trackingId` nebo ťuknutím. Kandidáti se hledají jen v okně kolem predikované polohy
  (2,5× poloměr, rozšiřuje se při výpadku) a s podobnou velikostí (±60 %). α-β filtr polohy, při výpadku do 0,4 s
  se kotouč dopočítá predikcí.
- **Měřítko:** cm/px = průměr kotouče / (2 · medián poloměru). Standardní kotouč má 45 cm, hodnotu jde změnit.
- **Opakování:** klouzavý průměr ±70 ms. Body obratu hledá zig-zag algoritmus s hysterezí ½ minimálního rozsahu cviku.
  Opakování tvoří trojice obratů: nahoře → dole → nahoře u dřepu a benche, dole → nahoře → dole u mrtvého tahu a tlaků.
- **Fáze** se určují z rychlosti (> 8 cm/s = pohyb), ne z polohy. U plynulého rozjezdu by polohový práh uřízl až čtvrtinu fáze.
- **Metriky repu:** počáteční bod, bod obratu a konečný bod (cm), rozsah, spouštění, výdrž, zvedání, celková doba bez odpočinku,
  průměrná a maximální rychlost zvedání (m/s), odchylka dráhy do strany (cm), kvalita sledování.
- **Série:** průměrný a **vážený** rozsah (váha = kvalita sledování repu), variabilita rozsahu (CV), ztráta rychlosti.
  Ztráta rychlosti se počítá mezi nejrychlejším a posledním repem (González-Badillo & Sánchez-Medina 2010).
- **Barvy:** srovnání uvnitř série. Každý sloupec má svoji škálu, červená = výrazně horší:
  rozsah ±15 %, spouštění ±50 %, zvedání +60 %, rychlost −40 %, dráha nad limit cviku.
- **Režim AKTIVNÍ:** série začne svislým pohybem > 8 cm za 1,5 s (přibalí se 3 s před ním) a skončí 4 s klidu
  nebo 6 s bez kotouče. Obrazovka nezhasíná. **VYPNUTO:** sledování běží, ale nic se neukládá.

## Validace
Syntetické série (šum detekce 3 px, 30 fps, vypadlé snímky, drift do strany). Počty repů sedí,
rozsah vychází ±2 cm a doby fází ±0,15 s. Testy jsou v `RepAnalyzerTest` a `PlateTrackerTest`.
**Na reálném videu to zatím ověřené není.** Parametry (prahy, okna) je potřeba doladit na skutečných sériích.

## Limity
- Kamera musí být **bokem** (kolmo na osu činky), jinak perspektiva zkresluje rozsah i měřítko.
- Obecný detektor ML Kit není trénovaný na kotouče. Při slabém kontrastu nebo zakrytí kotouče ho může ztratit.
  Dalším krokem by byl vlastní model (TFLite) natrénovaný na kotouče.
- Průměrná rychlost je z pohybu kotouče, ne těžiště činky. U benche a dřepu je to v praxi zanedbatelné.

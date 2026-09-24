# ADR 0007 – Heatmapa aktivity v historii

- **Stav:** přijato (2026-09)
- **Kód:** `history/Heatmap.kt` (čistý Kotlin, testy `HeatmapTest`), `HeatmapRepository`, `ActivityHeatmapView`, karta ve `fragment_history.xml`

## Kontext
Uživatel chtěl přehled po dnech ve stylu GitHubu (contribution graph). Denní kroky, výdej i plnění cílů
už aplikace počítá. Chyběl jen přehled delšího období na jeden pohled.

## Rozhodnutí
- **Mřížka:** 53 týdnů (sloupce Po–Ne), vpravo nejnovější, posun tahem do stran. Klepnutí na den otevře detail
  dne v historii a kalendář přeskočí na jeho měsíc. Budoucí dny se nekreslí.
  Dny před prvním záznamem v aplikaci se zobrazí jako „bez dat“.
- **Kroky – pevné hranice** 4 000 / 7 000 / 10 000. Hranice mají zdravotní význam: podle metaanalýzy
  Paluch et al. 2022 (Lancet Public Health) klesá u dospělých do 60 let riziko úmrtí zhruba do 8–10 tisíc kroků
  denně a pak se ustálí. Série se počítá od 7 000.
- **Aktivní výdej – relativní kvartily** vlastních dní (jako GitHub). Počítá se chůze z *naměřených* kroků
  (bez předpokladu 6 000 kroků pro den bez záznamu) plus trénink. Celkový výdej by heatmapa byla skoro jednobarevná,
  protože z velké části jde o bazální metabolismus. Trénink se zatím bere z plánu podle dne v týdnu, a pod heatmapou to stojí.
- **Cíle jídla** = počet pásem z `Adherence` (kalorie, B, S, T), která den trefil (0–4). Počítají se jen uzavřené dny.
  Je to stejné pravidlo jako u questů a achievementů (ADR 0004): odměňuje se trefa, ne extrém.
- Energie a cíle jdou přes `MacroCalculator.calculateForDate`, tedy stejný model jako dashboard. Dražší metriky se počítají
  na pozadí jednou za otevření obrazovky a pak se drží v paměti.

## Omezení
- Aktivní výdej z plánu neodpovídá skutečně odcvičeným dnům. Až bude délka tréninku odvozená z uložených sérií
  (sledování činky), přejde se na ni.

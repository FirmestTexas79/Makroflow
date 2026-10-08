# 0068 – Fitness recepty a nové Složit jídlo

**Stav:** přijato · **Datum:** 2026-10-08

## Kontext
Plán ze 7. 10.: „top fitness jídla“ jako inspirace a rychlý zápis. Okno Složit jídlo bylo
staré (podtržená pole, nejasný přepínač G/PORCE, bez souhrnu maker).

## Rozhodnutí
* `nutrition/recipes/FitnessRecipes.kt` – 15 trendových jídel (TikTok/Instagram 2025–2026):
  název + známý trend, chod, porce, čas, suroviny v gramech s hodnotami na 100 g (tabulkové, USDA),
  alergeny podle EU 14, postup. Fotky z Unsplash (Unsplash License) v `res/drawable-nodpi/recipe_<id>.jpg`,
  720×540, autor u každé fotky v detailu.
* Vstup: velká karta „Fitness recepty“ nahoře v nabídce Přidat jídlo → `RecipesSheet` (filtr chodu,
  karty s fotkou) → detail (porce ±, kcal/B/S/T na zvolené porce, alergeny, suroviny, postup).
* „Přidat do tabulky“ nezapisuje rovnou: otevře **Složit jídlo** s předvyplněnými surovinami
  pro zvolený počet porcí, uživatel je upraví a teprve pak zapíše.
* Složit jídlo nově: velký název, karty surovin s krokováním po 10 g a makry, „Kolik sníš“
  (celé / ½ / ⅓ / ¼), tmavý souhrn s prstencem maker, tlačítko dole.

## Poznámky
Výživové hodnoty jsou orientační (značkové produkty se liší). Test hlídá alergeny z EU 14,
konzistenci kcal s makry, přepočet porcí a existenci fotek.

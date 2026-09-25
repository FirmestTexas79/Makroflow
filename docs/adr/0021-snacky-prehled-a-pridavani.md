# 0021 – Snacky: přehledný seznam, jedno tlačítko Přidat, nové panely

**Stav:** přijato · **Datum:** 2026-09-25

## Kontext
Obrazovka Snacky byla nejstarší část aplikace a při revizi na telefonu vyšlo najevo:
* **Čtyři plovoucí tlačítka** (Swipe jídlo, Složit jídlo, fotoaparát, Přidat) roztroušená přes
  seznam, se systémovými ikonami Androidu; zakrývala položky.
* **Položky**: dvě podobné bubliny (kcal, gramy), drobná makra, pruh s délkou podle globálního
  maxima (nic neříkal). Rozdělení „bílkoviny vs. sacharidy“ podle `p > s` v gramech – ořechy
  a avokádo tak padaly mezi sacharidy, brokolice mezi bílkoviny.
* **Mazání** jen skrytým gestem (podržet 0,8 s a táhnout doleva přes celoobrazovkový překryv).
* **Dialog porce**: makra byla editovatelná pole, ale úpravy se při uložení ignorovaly
  (přepočítalo se z gramů). Seznam ukazoval kcal z etikety, dialog a uložený záznam kcal
  z maker → u stejné potraviny jiná čísla (Palačinky 274 vs. 278 kcal).
* **Formulář nové potraviny**: pole „B/S/T“ bez jednotek, energie vždy z maker i u naskenované
  etikety, výběr před/po tréninku přepínačem.
* **Výkon**: všech ~190 potravin se inflatovalo do LinearLayoutu při každém písmenu hledání.
* **Synchronizace**: zápisy ze Snacků a ze Složit jídlo se neposílaly do cloudu (swipe ano),
  nové vlastní potraviny se nahrály až při plné synchronizaci.

## Rozhodnutí
* **Čistá logika** `nutrition/SnackCatalog.kt` (testy `SnackCatalogTest`):
  skupiny podle zdroje energie (4/4/9 kcal/g) + zvlášť „Zelenina a lehké“ (< 60 kcal/100 g);
  hledání bez diakritiky a nezávislé na pořadí slov; oddíl Oblíbené (max. 5 podle počtu použití,
  jen bez hledání); rychlé porce (½, 1, 2 porce, 100 g); přepočet porce s energií z etikety.
* **Seznam** jako `RecyclerView` + `ListAdapter`/DiffUtil. Řádek: prstenec s podílem energie
  z B/S/T, název, porce a makra v barvách, kcal vpravo a tlačítko **„+“ = přidat porci hned**
  se Snackbarem **ZPĚT**. Klepnutí = vlastní množství, podržení = nabídka.
* **Hlavička** se při scrollu schová a při návratu vyjede (AppBarLayout); místo obecného textu
  ukazuje **dnešní zůstatek kcal a bílkovin**. Filtr Vše / Před / Po tréninkem (dřív jen dvě
  možnosti a část potravin nešlo vidět najednou).
* **Jedno tlačítko „Přidat“** (zmenší se při scrollu) otevře nabídku: ručně, čárový kód, vyfotit
  (AI), složit jídlo, swipe výběr.
* **Panel porce**: rychlé porce, krokování ±10 g (±5 g u malých porcí), tmavá karta s kcal,
  prstencem a pruhy „už snědeno dnes + tahle porce“ vůči cílům (nad 100 % varovná barva).
* **Formulář potraviny** (nová i úprava): popsaná pole s jednotkami, živý výpočet kcal
  (i na 100 g), energie z etikety u čárového kódu, dokud uživatel makra ručně nezmění.
* **Nabídka po podržení**: vlastní množství, upravit, přesunout před/po tréninkem, smazat se ZPĚT.
* **`FoodLog`** – jediná cesta do deníku (lokálně + cloud + oblíbenost), používá ho i Složit jídlo.
  Vlastní potraviny se nahrají hned po uložení, smazání se promítne i do cloudu.
* Ikony v linkovém stylu aplikace (ADR 0018); `SnackSeed` = výchozí obsah špajzky mimo fragment.

## Důsledky
* Nejčastější akce (sníst obvyklou porci) je jedno klepnutí místo tří, s možností vrátit.
* Čísla kcal jsou konzistentní napříč seznamem, dialogem, deníkem i Složit jídlo.
* Pole `mealContext` zůstává „NO_TRAINING“ – filtr před/po tréninku je kategorie potraviny,
  ne čas snězení (ten řeší `TrainingTimeManager`).

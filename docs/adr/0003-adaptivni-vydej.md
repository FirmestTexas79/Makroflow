# ADR 0003 – Adaptivní výdej z energetické bilance (fáze B)

- **Stav:** přijato (2026-09)
- **Kód:** `energy/WeightTrend.kt`, `energy/AdaptiveExpenditure.kt` (čistý Kotlin), adaptér `dashboard/AdaptiveTdeeRepository.kt`
- **Testy:** `AdaptiveExpenditureTest` (syntetická data + Monte Carlo), validační skript `tools/validation/AdaptiveMonteCarlo.kt`

## Kontext

Model z fáze A stojí na populačních rovnicích. I nejlepší z nich (Mifflin-St Jeor) se u jednotlivce
mýlí typicky o ±10 %, tedy ±200–300 kcal/den (Frankenfield et al. 2005). Aplikace přitom má data,
ze kterých jde skutečný výdej konkrétního člověka zjistit: denní vážení (ranní check-in) a zapsaný příjem.

Původní analytika hmotnosti (`BioLogicEngine`) měla metodické chyby:
- EMA a regrese počítaly s **pořadím záznamů**, ne se dny, takže mezery ve vážení zkreslovaly sklon,
- „modifikátor vitality“ násobil trend podle nálady, bez fyziologického základu,
- stín predikce byl složený z ručních konstant (TEF podle diety, somatotyp ze zápěstí).

## Rozhodnutí

### 1. Trend hmotnosti: Kalmanův filtr + RTS vyhlazení
Model lokálního lineárního trendu, stav `[hmotnost, tempo]`, krok = 1 den:
- σ_měření = 0,7 kg (denní výkyvy vody, glykogenu a obsahu střev),
- šum úrovně 0,05 kg/den, šum tempa 0,004 kg/den².

Dny bez vážení = jen predikce. Pro vyhodnocení okna se používá **Rauch–Tung–Striebel smoother**
(odhad v každém dni ze všech měření). Filtr dává rozptyl, takže predikce v grafu má skutečný
**95% interval** místo ručně nastaveného stínu.

### 2. Pozorovaný výdej z bilance (okno 28 dní)
```
výdej_poz = průměrný příjem (zapsané dny) − průměrné tempo trendu [kg/den] · 7700 kcal/kg
```
- Zapsaný den = příjem ≥ 50 % modelového výdeje (nedopsané dny by výdej podhodnotily).
- Dnešek se nepočítá (ještě není dojedený).
- Minimum dat: 7 vážení, 10 zapsaných dní, rozpětí 14 dní. Jinak se model nemění (k = 1).

### 3. Spojení s modelem (bayesovsky)
Model = prior se σ = 12 % výdeje. Pozorování má rozptyl z denní variability příjmu,
5% systematické chyby zápisu a nejistoty tempa z vyhlazení:
```
w = σ²_model / (σ²_model + σ²_poz)      výsledek = model + w · (poz − model)
k = výsledek / model,  omezeno na 0,80–1,20
```
Korekční faktor `k` násobí výdej z modelu, takže denní rozdíly (kroky, trénink) zůstávají.
`k` se promítne do cíle kalorií i maker. Model navíc bere **vyhlazenou hmotnost z trendu**
místo posledního (zašuměného) vážení.

### 4. Uložení
Tabulka `adaptive_tdee` (DB v35, `MIGRATION_34_35`) se denním snapshotem odhadu.
Historie odhadů umožňuje zpětně vyhodnotit rychlost a stabilitu konvergence.
Data jsou odvozená, a proto se nesynchronizují, na jiném zařízení se přepočítají.
Přepočet běží po každém check-inu. Pro historické dny se používá jen odhad platný k danému dni.

## Validace (Monte Carlo, 1000 simulovaných lidí na scénář)

Skutečný výdej 2200–3400 kcal, rovnice se mýlí o σ = 10 %, příjem ±500 kcal od výdeje (dieta i bulk),
šum zápisu σ = 250 kcal/den, váha šumí σ = 0,7 kg, okno 28 dní:

| Vážení | Průměrná chyba – rovnice | Průměrná chyba – adaptivní | Do ±150 kcal – rovnice | Do ±150 kcal – adaptivní |
|---|---|---|---|---|
| denně | 223 kcal | **100 kcal** | 42 % | **78 %** |
| ob den | 219 kcal | **122 kcal** | 43 % | **67 %** |
| každý 3. den | 226 kcal | **129 kcal** | 40 % | **64 %** |

Informační limit: i s ideálním estimátorem má tempo z 28 dní vážení s šumem 0,7 kg
nejistotu ≈ 0,016 kg/den ≈ 125 kcal/den. Proto je bayesovské spojení s modelem lepší než čistá bilance.

## Limity

- 7700 kcal/kg je zjednodušení. Při velkých změnách složení těla (např. rekompozice) neplatí přesně (Hall 2008).
- Systematicky nedopsané jídlo (ne celé dny) filtr nepozná. Výdej pak vyjde podhodnocený.
  Omezení ±20 % a 5% nejistota zápisu dopad tlumí.
- Výkyvy vody v prvních dnech diety (glykogen) mohou krátkodobě nadhodnotit tempo. Proto je okno 28 dní.
- Parametry filtru jsou nastavené z literatury a simulací, ne z dat reálných uživatelů. Až bude víc
  anonymizovaných dat, doporučuje se je doladit (maximální věrohodnost).

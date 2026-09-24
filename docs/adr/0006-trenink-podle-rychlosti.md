# ADR 0006 – Trénink podle rychlosti (VBT): profil zátěž–rychlost, e1RM, autoregulace

- **Stav:** přijato (2026-09)
- **Kód:** `training/analysis/LoadVelocity.kt` (čistý Kotlin: LoadVelocity, VelocityLoss, Autoregulation),
  `Lift.mvt`, `BarbellMapper.profileFor`, UI `TrainerFragment` + `SetSummaryViews`, PDF v `ReportGenerator`.
  Bez změny schématu DB, stačí `barbell_sets.loadKg` a `bestMcv` (v36).

## Kontext
Od ADR 0005 měříme průměrnou rychlost zvedání (MCV) každého repu. Aplikace ji ale jen zobrazovala.
Rychlost při dané zátěži přitom přímo odráží aktuální výkonnost. Díky tomu jde maximum odhadnout
bez testu na 1RM a zátěž i délku série řídit podle dnešní formy.

## Rozhodnutí
- **Profil zátěž–rychlost:** lineární regrese v = a + b·zátěž. Každá série se zadanou zátěží přispěje
  jedním bodem, a to **nejrychlejším repem** (standard pro profilování). Do profilu nejde série s kvalitou sledování pod 0,6.
- **Podmínky přímky:** aspoň 2 různé zátěže vzdálené ≥ 20 % té těžší, rozdíl rychlostí ≥ 0,10 m/s, sklon < 0
  a R² ≥ 0,6. Menší rozestupy zaniknou v šumu měření (±0,03–0,05 m/s).
- **e1RM:** zátěž, při které přímka klesne na minimální rychlost cviku (MVT). Použité populační hodnoty:
  dřep 0,30 · bench 0,17 · tlaky nad hlavu 0,19 · mrtvý tah 0,15 m/s. Odhad nesmí být nižší než nejtěžší zvednutá zátěž.
- **Jedna zátěž za den:** vezme se medián sklonu ze spolehlivých profilů posledních 6 týdnů a přímka se posune
  přes dnešní body. Denní forma mění hlavně výšku přímky, sklon je u jednotlivce poměrně stálý.
- **Spolehlivost:**

  | Úroveň | Podmínky |
  |---|---|
  | vysoká | ≥ 3 zátěže, R² ≥ 0,9 a nejtěžší série ≥ 70 % odhadu |
  | střední | R² ≥ 0,8 a buď ≥ 70 % odhadu, nebo ≥ 3 zátěže; při sklonu z historie ≥ 75 % odhadu |
  | nízká | ostatní případy |

  PDF i UI úroveň vždy uvádějí.
- **Cíle a ztráta rychlosti (VL):**

  | Cíl | Zátěž (% e1RM) | STOP při VL |
  |---|---|---|
  | Síla | 80–85 % | 20 % |
  | Objem | 67–75 % | 30 % |
  | Výbušnost | 50–60 % | 10 % |
  | Bez limitu | – | – |

  Doporučená zátěž je střed pásma zaokrouhlený na 2,5 kg.
- **Živé STOP:** během série se VL počítá dvakrát za sekundu. Poslední rep se nezapočítá, dokud jeho rozsah
  nedosáhne 90 % mediánu, protože analyzátor uzavírá rozjetý rep podle dosud nejvyššího bodu a falešně nízká
  rychlost by jinak STOP spustila předčasně. Signál je jednorázový: červený panel a krátký tón,
  protože telefon leží u činky a displej nemusí být vidět.
- **Zátěž se nepřepisuje sama.** Doporučení se převezme tlačítkem v souhrnu. Automaticky přepsané pole by se
  uložilo k další sérii, i kdyby uživatel kotouče nepřeložil, a tím by poškodilo profil.

## Důsledky a omezení
- MVT se mezi lidmi liší (±0,03–0,05 m/s). Individuální MVT by šlo zjistit ze skutečného 1RM pokusu. To je rozšíření na později.
- Odhad 1RM z profilu je u dřepu v literatuře méně přesný než u benche. Proto se uvádí jako odhad se spolehlivostí,
  ne jako naměřené maximum.
- Přesnost celé cesty závisí na přesnosti MCV z kamery. Ta se ověří validací proti referenci (plánováno do diplomové práce).

## Zdroje
- González-Badillo & Sánchez-Medina (2010), *Movement velocity as a measure of loading intensity in resistance training*, IJSM.
- Sánchez-Medina & González-Badillo (2011), *Velocity loss as an indicator of neuromuscular fatigue during resistance training*, MSSE.
- Pareja-Blanco et al. (2017), *Effects of velocity loss during resistance training on athletic performance, strength gains and muscle adaptations*, SJMSS.
- Banyard, Nosaka & Haff (2017), *Reliability and validity of the load–velocity relationship to predict the 1RM back squat*, JSCR.
- Weakley et al. (2021), *Velocity-based training: from theory to application*, SCJ.

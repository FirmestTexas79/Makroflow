# ADR 0012 – Stavové efekty a lékárnička

- **Stav:** přijato (2026-09)
- **Kód:** `pokemon/status/Status.kt` a `MedItem.kt` (čistý Kotlin, testy `StatusTest`), útoky v `BattleEngine`,
  tahy a efekty v `PokemonBattleView`, obchod a inventář.

## Pravidla (podle Pokémonů Gen 5+, zjednodušená pro souboj 1 na 1)

**Hlavní stav** může mít Makromon jen jeden. Zmatení a omráčení jdou navíc.

| Stav | Efekt | Trvání | Chycení |
|---|---|---|---|
| Spánek (SLP) | nemůže útočit | 1–3 tahy, pak se probudí a v tom tahu už jedná | ×2,5 |
| Paralýza (PAR) | 25 % šance, že tah vynechá; rychlost ½ (útěk) | trvalá | ×1,5 |
| Otrava (PSN) | 1/8 max. HP na konci kola | trvalá | ×1,5 |
| Popálení (BRN) | 1/16 max. HP na konci kola; útok ½ | trvalá | ×1,5 |
| Zmatení (CNF) | 33 % šance, že zasáhne sám sebe (bez typu, síla 40) | 2–5 tahů | – |
| Omráčení | tah propadne | jen v kole, kdy útočník jednal dřív | – |

Omráčení tedy umí jen hráč, protože v tomhle souboji hraje hráč vždycky první.

**Imunity:** ohnivý Makromon se nepopálí, jedovatý se neotráví, elektrický se neparalyzuje.

**Kontrola před tahem:** omráčení → spánek → paralýza → zmatení.

**Kolo:** hráč → soupeř → zranění na konci kola (nejdřív hráč, pak soupeř). Každá hláška čeká na ťuknutí.
Stav platí jen během souboje, po souboji se neukládá.

## Útoky

| Útok | Efekt a šance |
|---|---|
| SLEEP POWDER, HYPNOSIS | spánek 100 % |
| LICK, DRAGONBREATH | paralýza 30 % |
| THUNDER SHOCK, THUNDERBOLT | paralýza 10 % |
| ohnivé útoky | popálení 10 % |
| POISON STING, SLUDGE BOMB | otrava 30 % |
| WATER PULSE | zmatení 20 % |
| BITE | omráčení 30 % |
| HYPER FANG | omráčení 10 % |

- **HEX** má dvojnásobnou sílu proti cíli se stavem.
- **Nové čistě stavové útoky:**

  | Útok | Efekt | Přesnost | Kdo ho umí |
  |---|---|---|---|
  | TOXIC | otrava | 90 % | Mydrus místo SMOKESCREEN (ten nic nedělal) |
  | CONFUSE RAY | zmatení | 100 % | Soulu |
  | WILL-O-WISP | popálení | 85 % | Shadirra |

  SLEEP POWDER dostal Mycit.
- **Soupeř** nepoužije čistě stavový útok, když by nic neudělal (cíl už stav má).

## Lékárnička
- **Předměty:**

  | Předmět | Léčí | Cena |
  |---|---|---|
  | Kofein | spánek | 30 mincí |
  | Elektrolyt | paralýzu | 30 mincí |
  | Aktivní uhlí | otravu | 30 mincí |
  | Aloe gel | popáleniny | 30 mincí |
  | Studená sprcha | zmatení | 25 mincí |
  | Multivitamín | cokoli | 80 mincí |

- **Použití:** menu ITEM → LÉKÁRNIČKA. Použití stojí tah. Bez účinku se předmět nespotřebuje a tah nepropadne.
  Ikonky jsou pixel art 12 × 12 ze stejné mřížky jako Makrobally (obchod, inventář, menu souboje).

## Zobrazení
- **Štítek v rámečku HP:** SLP šedý, PAR žlutý, PSN fialový, BRN oranžový, CNF růžový.
- **Efekty nad Makromonem** při způsobení stavu a pak každých ~1,7 s, dokud stav trvá:

  | Stav | Efekt |
  |---|---|
  | spánek | „Z“ |
  | paralýza | jiskry |
  | otrava | bublinky |
  | popálení | plamínky |
  | zmatení | kroužící hvězdičky |

  Vyléčení ukáže zelené křížky.

## Opravené chyby
- **Snížení statistik na špatné straně:** GROWL, LEER, TAIL WHIP apod. snižovaly obranu hráče místo soupeře
  (`enemyDefMod` se násobil obranou hráče) a útok hráče snížení nebral v úvahu. Teď má každá strana své modifikátory.
- **Stavové útoky soupeře:** vypsaly „STAT FELL“, ale nic neudělaly.

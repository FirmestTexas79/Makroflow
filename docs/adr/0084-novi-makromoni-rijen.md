# 0084 – Noví Makromoni (Mysnor, Hunfrey, Kendl, Gemak, Krystalos, Magmor) – kontrola a animace

Stav: přijato (2026-10-14), větev `MULTI`

## Kontrola dat
- **Statistiky** jsou „základní“ (jako u Pokémonů, přepočet na level v `initializeStatsForLevel`).
  Kendl měl 30/18/25/12 (součet 85 – slabší než běžný Spirra) → 40/45/50/30 (165, jako Hunfrey).
  Magmor 60/55/70/25 → 58/55/65/25 (203), aby jednostupňový RARE nebyl silnější než EPIC Mydrus.
  Ostatní sedí: Gemak (160) ≈ Mysnic (159), Krystalos (260) ≈ Mysnor (241) + podmínka 10 check-inů.
- **Podpisový útok** (první v seznamu, divoký ho má vždy) musí mít typ druhu:
  Kendl NIGHT SHADE (duch), Gemak CRYSTAL SHARD (nově ZEMĚ), Magmor EMBER, Psychirra PSYCHIC.
- **Vývoj**: Gemak → Krystalos na levelu 10 (z registru druhů). Číslo 046 zůstává volné
  (třeba pro předvývoj Magmora).
- Starší dluh (neřešeno): duchové Soulu/Phantil a pár dalších mají statistiky v „malé“ stupnici.

## Učení útoků (`MakromonGrowthManager`)
- Psychirra 14 CONFUSE RAY, 17 DAZZL.GLEAM, 22 SHADOW BALL, 28 MIND SPIRAL (psycho 110, zmatení 30 %)
- Hunfrey 4 LEER, 7 QUICK ATTACK, 10 SLASH, 13 PSYCHIC, 17 PLAY ROUGH, 21 HYPNOSIS, 25 MASTER PLAN
- Kendl 4 SMOKESCREEN, 6 HEX, 9 SHADOW BALL, 13 FLAMETHROWER, 17 WAX SHELL (+obrana), 22 BLUE INFERNO (popálení 30 %)
- Gemak 3 MUD-SLAP, 5 BITE, 7 CRYSTAL ROLL, 9 DIG; Krystalos 12 CRUNCH, 15 CRYSTAL ROLL, 18 EARTHQUAKE, 24 GEM STORM
- Magmor 3 SMOKESCREEN, 5 MUD-SLAP, 8 FIRE FANG, 11 DIG, 15 HEAT WAVE, 19 EARTHQUAKE, 25 ERUPTION
- Mysnor měl učení už dřív (ADR 0063).

## Animace na liště (`StandardWanderer`)
| Druh | Chůze | Klid | Přes tlačítko | Klepnutí |
|---|---|---|---|---|
| Mysnic / Mysnor | cupitá / dupe | Mysnor dýchá pod krunýřem | přeleze (Mysnor těžce, tlačítko se otřese) | schová se pod krunýř, prach |
| Psychirra | levituje | vznáší se, kolem obíhá spirála jisker | přepluje | psychická vlna (2 prstence) |
| Hunfrey | klidný krok | občas si prohlédne kartu (otočí se nad hlavou) | vyleze nahoru a rozhlédne se | hodí kartu, rozprskne se v jiskry |
| Kendl | poskakuje jako svíčka | plamínek se chvěje, stoupá modrý dým | projde skrz | vyšlehne sloup modrého ohně |
| Gemak | batolí se | krystaly jiskří | stočí se do klubka a přejede | schová se do krunýře, jiskry |
| Krystalos | dupe | dýchá, krystaly jiskří | těžce přeleze | otřese se a vystřelí střepy |
| Magmor | dupe | sopka na zádech dýmá, občas jiskra | těžce přeleze | erupce: kouř a déšť jisker |

## Doplněno 2026-10-21: Tynafi, Tynafior, Johnsova, Grifben, Lumivix
- **Typ v souboji**: Tynafi a Tynafior neměli `type` a první útok byl TACKLE → v souboji byli NORMÁLNÍ.
  Teď OHEŇ (FIRE FANG / FLAMETHROWER první). Johnsova byla podle SAND ATTACK zemní → BLESK s novým
  podpisovým THUNDER SHOCK. Grifben má první THUNDERBOLT, Lumivix CHARM.
- **Učení útoků**: Tynafi 3 EMBER, 5 BITE, 7 SMOKESCREEN · Tynafior 10 SLASH, 13 CRUNCH, 16 FIRE BLAST,
  22 WILDFIRE MANE (popálení 30 %) · Johnsova 3 GUST, 5 QUICK ATTACK, 7 HYPNOSIS, 9 WING ATTACK ·
  Grifben 12 FURY ATTACK, 15 STATIC FEATHERS (+útok), 18 STORM DIVE, 24 THUNDERSTORM (paralýza 30 %) ·
  Lumivix 4 BABY-DOLL, 6 QUICK ATTACK, 10 DAZZL.GLEAM, 14 PLAY ROUGH, 18 MOONBLAST, 24 CRYSTAL WINGS.
- **Lišta**: Tynafi/Tynafior plamínky v srsti, skok s jiskrami, klepnutí = plamen · Johnsova/Grifben
  natáčí hlavu, jiskry v peří, přelet, klepnutí = výboj (Grifben větší a čechrá si peří) ·
  Lumivix poskakuje a jiskří, přeplachtí, klepnutí = víla jiskry.
- Statistiky těchto pěti jsou stále v „malé“ stupnici – řeší samostatný úkol vyvážení.

# 0090 – Ozvěnový kaňon

Stav: přijato (2026-10-22), větev `MULTI`, issue #29

- Biom `ECHO_CANYON` (`cave/EchoCanyon.kt`, mapa 160 × 384 z `tools/mapgen/gen_canyon.py`). Vchod z Popelavé
  pláně odbočkou vpravo (`ke_kanonu`), v kaňonu hráč přijde **zleva** a sestupuje dnem dolů.
- Grafika stejným postupem (ADR 0088): dno a plošiny jsou dlaždice, **skály podle výškové mřížky** – plošina
  s rudou horninou, pod ní skalní čelo se slojemi a puklinami a stín na dně (`canyon/canyon_parts.py`).
  Objekty: visutý most přes průrvu, hnízdo s vejci, jeskyňka, obří kosti, kameny, suché keře, ohořelé stromy.
- Ozvěnová stěna = čelo stolové hory dole, vyryté dlaně a čárky jmen. Klepnutí: ozvěna vrátí podle dne
  jinou větu (jméno Hunfrey, „Subjekt připraven“, počítání okruhů). Kosti mají razítko Kustodiátu a číslo 019.
- Živá vrstva `CanyonFxView`: vítr nese prach kaňonem, z průrvy stoupá teplý vzduch.
- Místa setkání: hnízdo, jeskyňka, římsa, vyschlé koryto. Divocí lv 14–20 (nejtěžší v aktu II zatím),
  druhy zatím horské (Kaňoňák a další přijdou s nakreslenými sprity).

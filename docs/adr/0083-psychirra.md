# 0083 – Psychirra (7. cesta vývoje Spirry) a sprite Lumexe

Stav: přijato (2026-10-09), větev `MULTI`

- **Psychirra** (#041, typ PSYCHO): Spirra se v ni vyvine po **14 ranních check-inech** ve dnech,
  kdy byla aktivním parťákem (od levelu 12, platí pravidlo „první splněná cesta vyhrává“, ADR 0055).
  Check-in = záznam v `check_ins` pro daný den (váha, spánek, energie, nálada) – odměňuje pravidelnost,
  ne extrémy. Ve volné přírodě vzácně v noci (EPIC, všude). Útoky: TACKLE, PSYCHIC, HYPNOSIS.
- `SpirraEvolution.Day.checkIn`; uložené dny mají 9. pole. Starší záznamy (8 polí) se zahodí
  a den se přepočítá z databáze, takže se započítají i check-iny z minulosti.
- **Lumex** (#011) dostal vlastní sprite `makromon_11_lumex.png` (data druhu beze změny).

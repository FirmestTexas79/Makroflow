# 0072 – Dlouhý úvod do Makrosvěta

Stav: přijato (2026-10-08)

Při prvním vstupu do Makrosvěta (po krátkém zakrytí z 0070) se místo krátkého odkrytí přehraje
~28 s průlet kamery nad našimi mapami (`pokemon/IntroFlyoverView`, Canvas, žádná videa ani nové assety):

| čas (s) | záběr |
|---|---|
| 0–4,4 | Město, Gudwin poskočí: „Vítej, poutníku!“ · „Daleko za tvým telefonem leží Makrosvět.“ |
| 4,4–8,6 | Průlet mraky na louku, poutník kácí strom (třísky) · „Na louce se poctivě pracuje…“ |
| 8,6–11 | Keř zašustí, vyskočí Spirra a uteče (lístky) · „…a v každém keři se může něco skrývat.“ |
| 11–16,4 | Hory → ponoření do štoly → Starý důl, poutník kope (jiskry, světlo lucerny) → zpět ven |
| 16,4–22 | Socha Krále Mlsáka s „!“, dialog s jeho podobiznou (animovaná GIF od API 28) |
| 22–25 | Průlet na Nebeský průsmyk · „A za průsmykem čeká nový kraj…“ |
| 25–27,6 | Nápis MAKROMON + „Tvoje dobrodružství začíná“, pak rozpad po dlaždicích do mapy |

- Mapy se skládají pod sebe na šířku obrazovky (průsmyk, hory, louka, město), švy zakrývají pixelové mraky.
  Kamera = klíčové záběry (střed, zoom) s plynulým přechodem; mezi světy (důl) se přepíná ve tmě.
- Postava ze Sunnyside (assets/hero axe_w, mining_e), 1 px spritu = W/205 (W/150 v dole).
- „Přeskočit >“ vpravo nahoře spustí rozpad hned. Přehraje se jednou (GamePrefs `intro_seen`,
  nastaví se už při startu). Debug: `--ez debug_intro true` na MakromonMapActivity.
- Náhled mimo telefon: `tools/intro/render_preview.py <složka> <fps>` (věrný přepis časování v Pythonu).

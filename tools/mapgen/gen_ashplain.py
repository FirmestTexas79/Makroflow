"""
Použití:  python tools/mapgen/gen_ashplain.py   (vyžaduje Pillow + numpy)
Výstup:   app/src/main/res/drawable-nodpi/ash_plain.png   (1 px = 1 art pixel)
          app/src/main/assets/walk/ash_plain.txt           (mapa chůze)
          tools/mapgen/ash_plain_debug.png                 (4x, s uzly a překážkami)
          tools/mapgen/ashplain/parts_tiles.png, parts_objects.png (náhled dílů)

Popelavá pláň (Akt II, docs/adr/0088) ve stylu kapesních Pokémonů, skládaná po vrstvách:
  1. pozadí z dlaždic 16 × 16 (ashplain/tiles.py): popelavá tráva, cesta, vysoká tráva, římsy, skála,
  2. objekty jako samostatné sprity (ashplain/objects.py): stromy, ohořelé stromy, dům, strážnice…,
  3. složení tady: mřížka 10 × 28 buněk, objekty seřazené podle paty (co je níž, je vpředu).
Hráč vyjde nahoře z Brány světů (převzaté z sky_pass.png) a sestupuje dolů k bráně starého města Ozvěny.

NODES / EDGES jsou ZDROJ PRAVDY i pro AshPlain.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import walkmask  # noqa: E402
sys.path.insert(0, os.path.join(HERE, "ashplain"))
import tiles, objects, pix  # noqa: E402
from tiles import T  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
RES = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
WALK = os.path.join(ROOT, "app", "src", "main", "assets", "walk")
COLS, ROWS = 10, 28
AW, AH = COLS * T, ROWS * T
CELL = 2
rng = np.random.default_rng(88)

NODES = {
    "vstup_z_brany": (80, 78),
    "n1": (80, 112), "cedule": (52, 100), "n2": (48, 112), "duny": (112, 160),
    "n3": (48, 192), "k_sadu": (10, 192), "ohniste": (40, 236), "n4": (96, 192), "ke_kanonu": (150, 192), "hajek": (124, 244),
    "n5": (96, 272), "meznik": (112, 300), "n6": (64, 272), "sutiny": (36, 326), "pole": (124, 326),
    "n7": (64, 336), "brana_mesta": (80, 432),
}
EDGES = [
    ("vstup_z_brany", "n1"), ("n1", "cedule"), ("n1", "n2"), ("n1", "duny"), ("n2", "n3"),
    ("n3", "ohniste"), ("n3", "k_sadu"), ("n3", "n4"), ("n4", "hajek"), ("n4", "ke_kanonu"), ("n4", "n5"), ("n5", "meznik"), ("n5", "n6"),
    ("n6", "sutiny"), ("n6", "n7"), ("n7", "pole"), ("n7", "brana_mesta"),
]

# ── 1) podklad: tráva, cesta, římsy, skála nahoře ──
path = np.zeros((ROWS, COLS), bool)
for (q0, q1, r0, r1) in ((4, 5, 4, 6), (2, 5, 6, 7), (2, 3, 7, 11), (0, 9, 11, 12), (5, 6, 12, 16),
                         (3, 6, 16, 17), (3, 4, 17, 20), (3, 5, 20, 21), (4, 5, 21, 27)):
    path[r0:r1 + 1, q0:q1 + 1] = True
LEDGES = [(6, 6, 8), (15, 1, 2), (17, 7, 8)]          # (řádek, od sloupce, do sloupce)

img = np.zeros((AH, AW, 4), np.uint8)
block = np.zeros((AH, AW), bool)

for r in range(ROWS):
    for q in range(COLS):
        v = rng.choice([0, 0, 0, 1, 2])
        img[r * T:(r + 1) * T, q * T:(q + 1) * T] = tiles.ground(int(v)).a
tiles.draw_path(img, path)
# stará dlažba před branou města
apron = lambda x, y: (60 <= x <= 99 and 352 <= y < 448) or ((x - 80) / 30) ** 2 + ((y - 372) / 14) ** 2 <= 1
tiles.draw_cobbles(img, apron, (40, 352, 120, 448))
# vysoká suchá tráva / obilí – nepravidelné porosty
PATCHES = [(126, 148, 22, 26, 1), (134, 262, 16, 30, 2), (126, 326, 24, 16, 3)]
for (cx, cy, rx, ry, sd) in PATCHES:
    tiles.draw_tall_grass(img, tiles.grass_patch(cx, cy, rx, ry, sd), (cx - rx - 4, cy - ry - 4, cx + rx + 5, cy + ry + 5), sd)
for (r, q0, q1) in LEDGES:
    block |= tiles.draw_ledge(img, r, q0, q1)

# skalní stěna nahoře za Branou světů (řádky 0–2), hrana s popelem
rw = tiles.rock_wall()
for r in (0, 1, 2):
    for q in range(COLS):
        img[r * T:(r + 1) * T, q * T:(q + 1) * T] = rw.a
for x in range(AW):
    lip = 46 + (x * 7) % 3
    for y in range(lip, 48):
        img[y, x] = tiles.R[5] if y == lip else tiles.R[4]
    img[48, x, :3] = (img[48, x, :3] * 0.7).astype(np.uint8)
block[:48, :] = True

# ── 2) objekty: (sprite, x paty – střed, y paty, kolize (šířka, výška) nebo None, stín) ──
O = objects
placed = []


def place(spr, cx, by, foot=None, shade=True):
    placed.append((spr, cx, by, foot, shade))


gate = O.world_gate()
place(gate, 80, 72, None, False)
city = O.city_gate()
place(city, 80, 448, None, False)
place(O.sign(), 52, 90, (6, 3))
place(O.milestone(), 112, 292, (10, 4))
place(O.scarecrow(), 128, 322, (4, 3))
place(O.campfire(), 40, 226, (14, 8))
place(O.ruined_house(), 24, 316, (46, 22), False)
for (x, y, lit) in ((100, 92, True), (72, 176, True), (32, 176, False), (100, 316, True)):
    place(O.lamp(lit), x, y, (4, 3))
for i, (x, y) in enumerate(((118, 236), (142, 252), (120, 290))):
    place(O.dead_tree(10 + i, mushrooms=i != 1), x, y, (6, 4))
for (x, y, big) in ((20, 230, False), (62, 236, True), (100, 126, False), (140, 112, True), (40, 344, False)):
    place(O.rock(big), x, y, (10 if big else 8, 4))
for (x, y) in ((60, 210), (100, 236), (24, 130), (104, 344)):
    place(O.bush(int(x + y)), x, y, (10, 4))
# hranice z popelavých stromů (sloupce 0 a 9) od skály dolů k hradbám
for i, y in enumerate(range(64, 360, 18)):
    if not 268 <= y <= 330 and not 176 <= y <= 214:             # dům a odbočka do sadu
        place(O.tree(i), 6 + (i % 2) * 3, y, (12, 6))
    if not 176 <= y <= 214:                                     # odbočka do kaňonu
        place(O.tree(i + 50), 154 - (i % 2) * 3, y, (12, 6))
for (x, y) in ((22, 64), (138, 64), (22, 150)):
    place(O.tree(int(x * 3 + y)), x, y, (12, 6))

for (spr, cx, by, foot, shade) in sorted(placed, key=lambda p: p[2]):
    x0, y0 = int(cx - spr.w // 2), int(by - spr.h)
    if shade:
        pix.shadow(img, cx, by - 1, spr.w * 0.38, 3)
    pix.paste(img, spr, x0, y0)
    if foot:                                            # silueta, ne jen pata (walkmask.py, ADR 0094)
        walkmask.silhouette(block, spr.a[:, :, 3], int(cx - spr.w // 2), int(by - spr.h))

# kolize Brány světů: prstenec a menhiry (schody pod ní jsou průchozí)
gm = gate.a[:, :, 3] > 0
gm[52:, :] = False
block[4:72, 36:124] |= gm
# kolize hradeb: vše neprůhledné ve spritu města kromě průjezdu
cm = city.a[:, :, 3] > 0
cm[:, 68:93] = False
block[348:448, :] |= cm

# ── 3) mapa chůze ──
block[48:380, :T] = True; block[48:380, -T:] = True   # stromová hranice
block[178:206, :T] = False                             # odbočka do Ztichlého sadu (vlevo)
block[178:206, -T:] = False                            # odbočka do Ozvěnového kaňonu (vpravo)
walk = ~block

os.makedirs(RES, exist_ok=True)
Image.fromarray(img[:, :, :3]).save(os.path.join(RES, "ash_plain.png"))

gw, gh = AW // CELL, AH // CELL
rows = []
for gy in range(gh):
    rows.append("".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(gw)))
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
os.makedirs(WALK, exist_ok=True)
with open(os.path.join(WALK, "ash_plain.txt"), "w") as f:
    f.write(f"# mapa chůze ash_plain: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_ashplain.py)\n")
    f.write(f"{AW} {AH} {CELL}\n")
    f.write("\n".join(rows) + "\n")

dbg = Image.fromarray(img[:, :, :3]).resize((AW * 4, AH * 4), Image.NEAREST).convert("RGBA")
ov = Image.new("RGBA", dbg.size, (0, 0, 0, 0))
d = ImageDraw.Draw(ov)
for gy, row in enumerate(rows):
    for gx, ch in enumerate(row):
        if ch == "#":
            d.rectangle([gx * CELL * 4, gy * CELL * 4, (gx + 1) * CELL * 4 - 1, (gy + 1) * CELL * 4 - 1], fill=(255, 0, 0, 40))
for (a, b) in EDGES:
    (x0, y0), (x1, y1) = NODES[a], NODES[b]
    d.line([x0 * 4, y0 * 4, x1 * 4, y1 * 4], fill=(255, 255, 0, 200), width=2)
for n, (x, y) in NODES.items():
    d.ellipse([x * 4 - 6, y * 4 - 6, x * 4 + 6, y * 4 + 6], fill=(0, 120, 255, 220))
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "ash_plain_debug.png"))
pix.sheet(tiles.parts(), scale=6).save(os.path.join(HERE, "ashplain", "parts_tiles.png"))
pix.sheet(objects.parts(), scale=4, cols=6).save(os.path.join(HERE, "ashplain", "parts_objects.png"))
print("ok", AW, AH)

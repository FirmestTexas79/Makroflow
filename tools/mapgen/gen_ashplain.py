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
Hráč přichází zdola zbořeným obloukem (druhá strana Brány světů) a jde nahoru ke strážnici Ozvěny.

NODES / EDGES jsou ZDROJ PRAVDY i pro AshPlain.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
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
    "brana_mesta": (80, 72),
    "n8": (80, 112), "cedule": (56, 88), "meznik": (120, 104), "n7": (48, 112),
    "n6": (48, 192), "ohniste": (40, 232), "n5": (96, 192), "pole": (104, 168),
    "n4": (96, 272), "hajek": (120, 248), "n3": (64, 272),
    "n2": (64, 352), "sutiny": (40, 344), "n1": (80, 352), "duny": (120, 360),
    "vstup_z_brany": (80, 416),
}
EDGES = [
    ("brana_mesta", "n8"), ("n8", "cedule"), ("n8", "meznik"), ("n8", "n7"), ("n7", "n6"),
    ("n6", "ohniste"), ("n6", "n5"), ("n5", "pole"), ("n5", "n4"), ("n4", "hajek"), ("n4", "n3"),
    ("n3", "n2"), ("n2", "sutiny"), ("n2", "n1"), ("n1", "duny"), ("n1", "vstup_z_brany"),
]

# ── 1) mřížka: cesta, vysoká tráva, římsy ──
path = np.zeros((ROWS, COLS), bool)
for (q0, q1, r0, r1) in ((4, 5, 4, 6), (2, 5, 6, 7), (2, 3, 7, 11), (2, 6, 11, 12), (5, 6, 12, 16),
                         (3, 6, 16, 17), (3, 4, 17, 21), (3, 5, 21, 22), (4, 5, 22, 27)):
    path[r0:r1 + 1, q0:q1 + 1] = True
grass = np.zeros((ROWS, COLS), bool)
for (q0, q1, r0, r1) in ((6, 8, 8, 11), (7, 8, 14, 17), (6, 8, 21, 24)):
    grass[r0:r1 + 1, q0:q1 + 1] = True
LEDGES = [(7, 6, 8), (15, 1, 2), (20, 6, 8)]          # (řádek, od sloupce, do sloupce)

img = np.zeros((AH, AW, 4), np.uint8)
block = np.zeros((AH, AW), bool)

for r in range(ROWS):
    for q in range(COLS):
        v = rng.choice([0, 0, 0, 1, 2])
        img[r * T:(r + 1) * T, q * T:(q + 1) * T] = tiles.ground(int(v)).a
tiles.draw_path(img, path)
tg = tiles.tall_grass()
for r, q in zip(*np.nonzero(grass)):
    img[r * T:(r + 1) * T, q * T:(q + 1) * T] = tg.a
for (r, q0, q1) in LEDGES:
    block |= tiles.draw_ledge(img, r, q0, q1)

# skalní stěna dole kolem oblouku (řádky 26–27 mimo sloupce 3–6)
rw = tiles.rock_wall()
for r in (26, 27):
    for q in list(range(0, 3)) + list(range(7, 10)):
        img[r * T:(r + 1) * T, q * T:(q + 1) * T] = rw.a
        block[r * T:(r + 1) * T, q * T:(q + 1) * T] = True
for x in list(range(0, 48)) + list(range(112, 160)):          # světlá hrana nahoře
    img[26 * T, x] = tiles.R[5]; img[26 * T + 1, x] = tiles.R[4]

# za hradbami: souvislé červené střechy města (horní okraj mapy)
for y in range(0, 26):
    for x in range(AW):
        row = y % 5
        img[y, x, :3] = (0x8A, 0x30, 0x28) if row == 4 else (0xB4, 0x4A, 0x3A) if (x + (y // 5) * 4) % 9 else (0x9A, 0x3A, 0x30)
        if row == 0:
            img[y, x, :3] = (0xD0, 0x68, 0x50)

# ── 2) objekty: (sprite, x paty – střed, y paty, kolize (šířka, výška) nebo None, stín) ──
O = objects
placed = []


def place(spr, cx, by, foot=None, shade=True):
    placed.append((spr, cx, by, foot, shade))


place(O.city_wall(48), 24, 64, (48, 56), False)
place(O.city_wall(48), 136, 64, (48, 56), False)
place(O.gatehouse(), 80, 64, (64, 64), False)
place(O.world_arch(), 80, 448, None, False)
place(O.sign(), 56, 80, (6, 3))
place(O.milestone(), 120, 96, (10, 4))
place(O.scarecrow(), 120, 150, (4, 3))
place(O.campfire(), 40, 222, (14, 8))
place(O.ruined_house(), 24, 336, (46, 22), False)
for (x, y, lit) in ((104, 92, True), (24, 156, False), (72, 176, True), (104, 318, True), (56, 404, True)):
    place(O.lamp(lit), x, y, (4, 3))
for i, (x, y) in enumerate(((118, 236), (140, 262), (116, 290))):
    place(O.dead_tree(10 + i, mushrooms=i != 1), x, y, (6, 4))
for (x, y, big) in ((20, 214, True), (60, 232, False), (138, 128, False), (100, 120, True), (24, 392, False), (138, 392, True)):
    place(O.rock(big), x, y, (10 if big else 8, 4))
for (x, y) in ((20, 244), (60, 210), (136, 178), (102, 330), (22, 102)):
    place(O.bush(int(x + y)), x, y, (10, 4))
# hranice z popelavých stromů (sloupce 0 a 9), uvnitř druhá řada, kde je místo
for i, y in enumerate(range(92, 420, 18)):
    if not 280 <= y <= 340:                                     # tam stojí dům
        place(O.tree(i), 6 + (i % 2) * 3, y, (12, 6))
    place(O.tree(i + 50), 154 - (i % 2) * 3, y, (12, 6))
for (x, y) in ((22, 128), (138, 220), (22, 300), (140, 416), (20, 420)):
    place(O.tree(int(x * 3 + y)), x, y, (12, 6))

for (spr, cx, by, foot, shade) in sorted(placed, key=lambda p: p[2]):
    x0, y0 = int(cx - spr.w // 2), int(by - spr.h)
    if shade:
        pix.shadow(img, cx, by - 1, spr.w * 0.38, 3)
    pix.paste(img, spr, x0, y0)
    if foot:
        fw, fh = foot
        block[max(0, by - fh):by, max(0, int(cx - fw // 2)):min(AW, int(cx + (fw + 1) // 2))] = True

# zelený svit Brány světů nad plotnou oblouku
for y in range(396, 448):
    for x in range(56, 105):
        d = ((x - 80) / 24) ** 2 + ((y - 424) / 26) ** 2
        if d < 1:
            k = (1 - d) ** 2 * 0.32
            img[y, x, :3] = (img[y, x, :3] * (1 - k) + np.array([126, 240, 160]) * k).astype(np.uint8)

# ── 3) mapa chůze ──
block[:64, :] = True                                  # hradby a strážnice
block[64:, :T] = True; block[64:, -T:] = True         # stromová hranice
block[396:450, 48:66] = True; block[396:450, 98:112] = True   # pilíře oblouku
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

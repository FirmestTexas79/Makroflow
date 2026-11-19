"""
Použití:  python tools/mapgen/gen_archive.py
Výstup:   app/src/main/res/drawable-nodpi/tower_archive.png, app/src/main/assets/walk/tower_archive.txt,
          tools/mapgen/tower_archive_debug.png

Archiv okruhů – 1. patro Věže Řádu Váhy (Akt II, fáze Spis 212), interiér 176 × 336 v pohledu 3/4 shora.
Noc: místnost je tmavá, světlo dávají jen zelené bankéřské lampy (teplé kruhy), kabina výtahu a měsíc
v oknech (studené kužely přes parkety). Motiv spánku: hodiny na 6:12, lehátko noční směny, které nikdo
nepoužil, a Spis 212 na čtecím pultu – nejsvětlejší místo v patře.
  1. podlaha: parkety stromeček, modrý koberec od výtahu ke studovně,
  2. stíny podlahy (vržené, kontaktní, u stěn),
  3. zadní stěna: vestavěné police, okna do noci, mramorové pilastry, výtah s ukazatelem na 1, hodiny, žebřík,
  4. objekty podle hloubky (tower/archive_parts.py),
  5. noc: ztmavení celé scény mimo světla lamp, světelné kruhy lamp, měsíční kužely, prach, viněta.
NODES / EDGES jsou ZDROJ PRAVDY i pro TowerArchive.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import walkmask  # noqa: E402
import tower_parts as P, light as L, figures as F, archive_parts as A  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 336
WALL_H = 84
SIDE = 10
NODES = {
    "archiv_vytah": (88, 98), "archiv_stred": (88, 206), "archiv_katalog": (34, 138), "archiv_vozik": (142, 136),
    "archiv_regal_l": (40, 198), "archiv_regal_p": (136, 198), "archiv_studovna": (88, 266), "archiv_globus": (146, 254),
    "spis_212": (84, 318), "archiv_lehatko": (138, 272), "archiv_regal_s": (36, 316), "archiv_hodiny": (40, 98),
}
EDGES = [("archiv_vytah", "archiv_stred"), ("archiv_vytah", "archiv_hodiny"), ("archiv_stred", "archiv_katalog"),
         ("archiv_stred", "archiv_vozik"), ("archiv_stred", "archiv_regal_l"), ("archiv_stred", "archiv_regal_p"),
         ("archiv_stred", "archiv_studovna"), ("archiv_studovna", "spis_212"), ("archiv_studovna", "archiv_lehatko"),
         ("archiv_studovna", "archiv_regal_s"), ("archiv_studovna", "archiv_globus")]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.zeros((AH, AW), bool)
floor_px = np.zeros((AH, AW), bool); floor_px[WALL_H:, SIDE:AW - SIDE] = True
BAL_Y = AH - 14                                                           # zábradlí galerie dole

# ── 1. podlaha ───────────────────────────────────────────────────────────────
A.parquet(img, 0, WALL_H, AW, AH)
P.carpet(img, 79, 98, WALL_H + 2, 222)

# ── objekty ──────────────────────────────────────────────────────────────────
placed = []
def place(spr, cx, by, z=None, collide=True, pad=2, shadow=True, foot=None):
    o = dict(spr=spr, x=int(cx - spr.w // 2), y=int(by - spr.h), z=by if z is None else z, shadow=shadow)
    placed.append(o)
    if collide:
        if foot:
            fw, fh = foot
            block[by - fh:by, int(cx - fw / 2):int(cx + fw / 2)] = True
        else:
            walkmask.silhouette(block, spr.a[:, :, 3], o["x"], o["y"], pad)
    return o

place(A.card_catalog(), 34, 134, foot=(32, 14))
place(A.book_cart(), 142, 128, foot=(26, 10))
shelf_l = place(A.bookshelf(50, 48, seed=190), 40, 186, foot=(54, 14))
shelf_r = place(A.bookshelf(50, 48, seed=200), 136, 186, foot=(54, 14))
table = place(A.reading_table(64), 88, 256, foot=(64, 22))
lect = place(A.lectern(), 84, 306, foot=(26, 10))
bed = place(A.daybed(), 138, 308, foot=(48, 18))
shelf_s = place(A.bookshelf(46, 44, seed=212), 36, 308, foot=(50, 14))
place(A.globe(), 146, 240, foot=(18, 8))
place(A.box_stack(1), 26, 238, foot=(20, 8)); place(A.box_stack(2), 114, 124, foot=(20, 8))
place(A.step_stool(), 72, 190, foot=(16, 8))
place(A.nightstand(), 106, 308, foot=(14, 8))
place(A.map_roll(), 64, 306, collide=False)                               # Hunfreyho tubus opřený o regál
A.loose_papers(img, [(62, 260), (100, 263), (114, 259), (52, 268), (76, 262)], np.random.default_rng(5))

# ── 2. stíny podlahy ─────────────────────────────────────────────────────────
sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=16, k=0.95)
L.side_wall_shadow(sh, SIDE, AW - SIDE, depth=12, k=0.8, y0=WALL_H)
for o in placed:
    s = o["spr"]
    L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], 0.50, 0.28, 0.8)
    L.contact_shadow(sh, o["x"] + s.w / 2, o["y"] + s.h - 1, s.w / 2 + 1, 2.4, 1.0)
sh *= floor_px
L.darken(img, sh, 0.55, dither=False, steps=4)

# ── 3. zadní a boční stěny ───────────────────────────────────────────────────
wall = A.wall_shelves(AW, WALL_H)
img[:WALL_H] = wall.a
block[:WALL_H + 3, :] = True

def wall_put(spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < AH and 0 <= x + xx < AW:
                img[y + yy, x + xx] = spr.a[yy, xx]

WIN = [(22, 16, 20, 56), (154, 16, 20, 56)]
for i, (wx, wy, ww, wh) in enumerate(WIN):
    for yy in range(wy - 2, wy + wh + 1):                                 # výklenek okna v policích
        for xx in range(wx - ww // 2 - 2, wx + ww // 2 + 2):
            img[yy, xx] = P.MARB[1] if xx < wx else P.MARB[0]
    wall_put(A.night_window(ww, wh, seed=wx, moon=(i == 0)), wx - ww // 2, wy)
for px0 in (56, 110):
    wall_put(P.pilaster(WALL_H - 6), px0, 5)
for yy in range(5, WALL_H):                                               # mramor za výtahem
    for xx in range(66, 110):
        img[yy, xx] = P.MARB[2] if (yy // 8 + (xx // 16)) % 2 else P.MARB[3]
        if yy % 8 == 0 or xx % 16 == 0:
            img[yy, xx] = P.MARB[1]
elev = F.elevator3d(40, 58)
EX0, EY0 = 88 - 20, WALL_H + 2 - 58
wall_put(elev, EX0, EY0)
dial = F.floor_dial3d(13, at=1)
wall_put(dial, 88 - dial.w // 2, 4)
wall_put(A.wall_clock(7, pendulum=False), 34, 22)      # kyvadlo: InteriorFxView (pivot 42,39)
wall_put(A.ladder(WALL_H - 10), 126, 8)
L.reflect(img, (EX0 + 4, EY0 + 8, EX0 + 36), WALL_H + 3, 12, k=0.16, floor_mask=floor_px)

for y in range(WALL_H - 6, AH):                                           # boční stěny (tmavý ořech, vnitřní čelo)
    for x in range(SIDE):
        for (X, inner) in ((x, x), (AW - 1 - x, x)):
            left = X < AW // 2
            if inner < 3:
                col = A.WALNUT[4] if inner == 1 else A.WALNUT[3] if inner == 2 else A.WALNUT[1]
            else:
                t = (inner - 3) / (SIDE - 3)
                col = A.WALNUT[2].copy() if left else A.WALNUT[1].copy()
                k = (0.15 + 0.4 * t) if left else (0.35 + 0.4 * t)
                if (y // 12) % 2 == 0 and inner == SIDE - 1:
                    col = A.WALNUT[0].copy()
                col[:3] = (col[:3] * (1 - k) + np.array([8, 10, 22]) * k).astype(np.uint8)
            img[y, X] = col
            block[y, X] = True

# ── 4. objekty podle hloubky ─────────────────────────────────────────────────
for o in sorted(placed, key=lambda o: o["z"]):
    s = o["spr"]
    for yy in range(s.h):
        for xx in range(s.w):
            if s.a[yy, xx, 3] and 0 <= o["y"] + yy < AH and 0 <= o["x"] + xx < AW:
                img[o["y"] + yy, o["x"] + xx] = s.a[yy, xx]

bal = A.balustrade(AW - 2 * SIDE, AH - BAL_Y)
wall_put(bal, SIDE, BAL_Y)
block[BAL_Y - 2:, :] = True

# ── 5. noc: tma, lampy, měsíc ────────────────────────────────────────────────
lamps = [(table["x"] + 12, table["y"] + 12, 26, 16, 1.0), (table["x"] + 64 - 14, table["y"] + 12, 26, 16, 1.0),
         (lect["x"] + 25, lect["y"] + 6, 22, 14, 0.9),
         (88, WALL_H + 2, 26, 14, 0.8)]                                  # kabina výtahu
def ring_pool(mask, cx, cy, rx, ry, k=1.0):
    """Světelný kruh lampy v pixelových prstencích (ne měkký mrak): 1.0 / 0.6 / 0.3, hranice šachovnicí."""
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if not (0 <= x < AW and 0 <= y < AH):
                continue
            d = ((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2
            d = d ** 0.5 + (0.04 if (x + y) % 2 else -0.04)
            v = 1.0 if d < 0.42 else 0.6 if d < 0.72 else 0.3 if d < 1 else 0
            mask[y, x] = max(mask[y, x], round(v * k, 1))

pool = np.zeros((AH, AW))
for (lx, ly, rx, ry, k) in lamps:
    ring_pool(pool, lx, ly, rx, ry, k)
ring_pool(pool, 84, lect["y"] + 16, 32, 26, 0.8)                           # záře kolem spisu
moon = np.zeros((AH, AW))
wx, wy, ww, wh = WIN[0]
L.light_shaft(moon, (wx - ww // 2 + 2, WALL_H), (wx + ww // 2 - 2, WALL_H), 0.55, 170)
moon *= floor_px

dark = np.clip(1.0 - pool, 0, 1)
dark[:WALL_H][(img[:WALL_H, :, 2] > img[:WALL_H, :, 0] + 30) & (img[:WALL_H, :, 2] > 60)] *= 0.25   # okna nechat
L.darken(img, dark, 0.46, (6, 9, 24), dither=True, steps=10)
L.lighten(img, pool, 0.22, (255, 214, 140), steps=10)
L.lighten(img, moon, 0.20, (150, 176, 255), steps=8)
rng = np.random.default_rng(212)                                          # prach v kuželech světla
for _ in range(90):
    x, y = int(rng.integers(SIDE, AW - SIDE)), int(rng.integers(WALL_H + 2, BAL_Y))
    if pool[y, x] > 0.45:
        img[y, x, :3] = (255, 236, 190) if rng.random() < 0.6 else (220, 190, 140)
    elif moon[y, x] > 0.4:
        img[y, x, :3] = (190, 205, 255)

vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.55, AW * 0.62, AH * 0.6, k=0.5)
L.darken(img, vg, 0.8, (4, 6, 16), dither=True, steps=10)
L.tone(img, (120, 140, 210), (200, 170, 130), k=0.07)

# ── výstupy ──────────────────────────────────────────────────────────────────
Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_archive.png"))
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
os.makedirs(os.path.join(ROOT, "app", "src", "main", "assets", "walk"), exist_ok=True)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "tower_archive.txt"), "w") as f:
    f.write(f"# mapa chůze tower_archive: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_archive.py)\n{AW} {AH} {CELL}\n")
    f.write("\n".join(rows) + "\n")
dbg = Image.fromarray(img[:, :, :3]).resize((AW * 3, AH * 3), Image.NEAREST).convert("RGBA")
ov = Image.new("RGBA", dbg.size, (0, 0, 0, 0)); dr = ImageDraw.Draw(ov)
for gy, row in enumerate(rows):
    for gx, ch in enumerate(row):
        if ch == "#":
            dr.rectangle([gx * 6, gy * 6, gx * 6 + 5, gy * 6 + 5], fill=(255, 0, 0, 45))
for (a, b) in EDGES:
    dr.line([NODES[a][0] * 3, NODES[a][1] * 3, NODES[b][0] * 3, NODES[b][1] * 3], fill=(255, 255, 0, 200), width=2)
for n, (x, y) in NODES.items():
    dr.ellipse([x * 3 - 5, y * 3 - 5, x * 3 + 5, y * 3 + 5], fill=(0, 120, 255, 230))
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "tower_archive_debug.png"))
print("ok", AW, AH)
print("fx lamps", [(l[0], l[1], l[2], l[3]) for l in lamps[:3]], "table", (table["x"], table["y"]), "lect", (lect["x"], lect["y"]))

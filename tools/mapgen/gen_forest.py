"""
Použití:  python tools/mapgen/gen_forest.py
Výstup:   app/src/main/res/drawable-nodpi/tower_forest.png, app/src/main/assets/walk/tower_forest.txt,
          tools/mapgen/tower_forest_debug.png

Zarostlé patro – 4. patro Věže Řádu Váhy (patro listových Makromonů), 176 × 352 v pohledu 3/4 shora.
Les prorostl úřadem: kořeny zvedly dlaždice, kartotéky obrostl břečťan, ze stěny visí liány, výtah je
zelený od mechu. Velké stromy, jeden s dutinou, kapradí (setkání), padlý kmen se slimákovou cestičkou,
houby. Šero, světlo jen v kuželech mezi korunami.
Živé detaily (pohyblivé paprsky, padající listí, oči v dutině, slimák, prach): InteriorFxView.FOREST.
NODES / EDGES jsou ZDROJ PRAVDY i pro TowerForest.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import walkmask  # noqa: E402
import tower_parts as P, light as L, figures as F, archive_parts as A, measure_parts as M, forest_parts as G  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 352
WALL_H = 90
SIDE = 10
NODES = {
    "les_vytah": (88, 98), "les_stred": (88, 172), "les_krovi_1": (66, 222), "les_krovi_2": (112, 280),
    "les_krovi_3": (70, 336), "les_dutina": (130, 226), "les_kmen": (100, 318), "les_kartoteka": (52, 146),
    "les_houby": (106, 182), "les_cedulka": (144, 300),
}
EDGES = [("les_vytah", "les_stred"), ("les_vytah", "les_kartoteka"), ("les_stred", "les_houby"),
         ("les_stred", "les_krovi_1"), ("les_stred", "les_dutina"), ("les_dutina", "les_krovi_2"),
         ("les_krovi_1", "les_krovi_2"), ("les_krovi_2", "les_kmen"), ("les_kmen", "les_cedulka"),
         ("les_kmen", "les_krovi_3")]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.zeros((AH, AW), bool)
BAL_Y = AH - 12
floor_px = np.zeros((AH, AW), bool); floor_px[WALL_H:BAL_Y, SIDE:AW - SIDE] = True

G.forest_floor(img, 0, WALL_H, AW, AH)

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

t1 = place(G.big_tree(1), 22, 196, foot=(30, 12))
t2 = place(G.big_tree(2, hollow=True), 154, 216, foot=(30, 12))
t3 = place(G.big_tree(3), 24, 306, foot=(30, 12))
t4 = place(G.big_tree(4), 156, 334, foot=(30, 12))
for o in (t1, t2, t3, t4):                                                # koruny: nepodcházet (hráč by byl nad nimi)
    s = o["spr"]
    for yy in range(46):
        for xx in range(s.w):
            X, Y = o["x"] + xx, o["y"] + yy
            if s.a[yy, xx, 3] and 0 <= X < AW and 0 <= Y < AH:
                block[Y, X] = True
place(G.overgrown_cabinet(F.filing_cabinet(1)), 52, 134, foot=(24, 10))
place(G.overgrown_cabinet(F.filing_cabinet(3)), 124, 134, foot=(24, 10))
f1 = place(G.fern(1), 66, 212, collide=False)
f2 = place(G.fern(2), 112, 270, collide=False)
f3 = place(G.fern(3), 70, 326, collide=False)
log = place(G.fallen_log(50), 100, 306, foot=(48, 10))
place(G.snail_sign(), 140, 296, foot=(4, 3))
import plain_parts as Q  # noqa: E402
for i, (x, y) in enumerate(((30, 150), (146, 154), (82, 196), (140, 270), (36, 262), (60, 248), (120, 228), (96, 340),
                            (150, 296), (34, 340), (112, 150), (70, 292), (126, 316))):     # drobné kapradí a trsy
    place(G.fern(20 + i, 18, 11) if i % 2 else Q.tall_grass(14, 10, 30 + i), x, y, collide=False, shadow=False)
for i, (x, y) in enumerate(((106, 176), (44, 226), (132, 252), (52, 290), (90, 236))):
    place(G.mushrooms(i), x, y, foot=(6, 3))

# ── šero a skvrny světla mezi korunami ──────────────────────────────────────
dark = np.ones((AH, AW)); dark[:WALL_H] = 0
spots = np.zeros((AH, AW))
rng = np.random.default_rng(4)
for _ in range(7):                                                         # sluneční skvrny (pevné, pixelové)
    cx, cy = rng.uniform(40, 140), rng.uniform(WALL_H + 20, BAL_Y - 10)
    L.contact_shadow(spots, cx, cy, rng.uniform(6, 12), rng.uniform(3, 6), 1.0)
spots = (spots > 0.35).astype(float) * 0.9
L.darken(img, dark * floor_px * (1 - spots), 0.28, (8, 20, 12), dither=False, steps=4)
L.lighten(img, spots * floor_px, 0.14, (255, 236, 170), steps=4)

sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=12, k=0.8)
L.side_wall_shadow(sh, SIDE, AW - SIDE, depth=10, k=0.7, y0=WALL_H)
for o in placed:
    if not o["shadow"]:
        continue
    s = o["spr"]
    L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], 0.50, 0.28, 0.8)
    L.contact_shadow(sh, o["x"] + s.w / 2, o["y"] + s.h - 1, s.w / 2 + 1, 2.4, 1.0)
sh *= floor_px
L.darken(img, sh, 0.5, (6, 14, 8), dither=False, steps=4)

# ── zadní stěna: kachle obrostlé břečťanem ──────────────────────────────────
img[:WALL_H] = M.tiled_wall(AW, WALL_H).a
block[:WALL_H + 3, :] = True

def wall_put(spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < AH and 0 <= x + xx < AW:
                img[y + yy, x + xx] = spr.a[yy, xx]

for (wx, wy, ww, wh) in ((22, 14, 20, 50), (154, 14, 20, 50)):
    win = P.window(ww, wh, seed=wx + 4)
    m = win.a[:, :, 3] > 0
    win.a[m, :3] = (win.a[m, :3] * 0.55 + np.array([40, 90, 50]) * 0.45).astype(np.uint8)    # zelené od mechu
    wall_put(win, wx - ww // 2, wy)
for yy in range(4, WALL_H):
    for xx in range(64, 113):
        if xx < 68 or xx > 108 or yy < 30:
            img[yy, xx] = P.MARB[4] if xx in (64, 65) or yy == 4 else P.MARB[1] if xx in (111, 112) else P.MARB[3]
elev = F.elevator3d(40, 58)
EX0, EY0 = 88 - 20, WALL_H + 2 - 58
wall_put(elev, EX0, EY0)
wall_put(F.floor_dial3d(13, at=4), 88 - 15, 6)
from pix import Spr  # noqa: E402
wall = G.ivy_wall(Spr(AW, WALL_H))
wa = wall.a
m = wa[:, :, 3] > 0
img[:WALL_H][m] = wa[m]

for y in range(WALL_H - 6, AH):                                           # boční stěny: kachle pod mechem
    for x in range(SIDE):
        for (X, inner) in ((x, x), (AW - 1 - x, x)):
            left = X < AW // 2
            h = G._h(X, y, 5)
            col = G.MOSS[[1, 2, 2, 3, 1][h % 5] if inner < 7 else 0]
            if inner >= 7:
                col = G.LEAF[0]
            if not left:
                col = (col * 0.75).astype(np.uint8); col[3] = 255
            img[y, X] = col
            block[y, X] = True

for o in sorted(placed, key=lambda o: o["z"]):
    s = o["spr"]
    for yy in range(s.h):
        for xx in range(s.w):
            if s.a[yy, xx, 3] and 0 <= o["y"] + yy < AH and 0 <= o["x"] + xx < AW:
                img[o["y"] + yy, o["x"] + xx] = s.a[yy, xx]

wall_put(A.balustrade(AW - 2 * SIDE, AH - BAL_Y), SIDE, BAL_Y)
block[BAL_Y - 2:, :] = True

vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.55, AW * 0.6, AH * 0.6, k=0.55)
L.darken(img, vg, 0.8, (4, 12, 6), dither=True, steps=10)
L.tone(img, (110, 160, 120), (120, 150, 100), k=0.05)

Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_forest.png"))
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "tower_forest.txt"), "w") as f:
    f.write(f"# mapa chůze tower_forest: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_forest.py)\n{AW} {AH} {CELL}\n")
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
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "tower_forest_debug.png"))
print("ok trees", [(o["x"], o["y"]) for o in (t1, t2, t3, t4)], "ferns", [(o["x"], o["y"]) for o in (f1, f2, f3)], "log", (log["x"], log["y"]))

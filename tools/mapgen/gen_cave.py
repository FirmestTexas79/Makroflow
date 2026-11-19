"""
Použití:  python tools/mapgen/gen_cave.py
Výstup:   app/src/main/res/drawable-nodpi/tower_cave.png, app/src/main/assets/walk/tower_cave.txt,
          tools/mapgen/tower_cave_debug.png

Krystalová jeskyně – 6. patro Věže Řádu Váhy (psychičtí a električtí Makromoni), 176 × 352 v pohledu 3/4 shora.
Tma, světlo dávají jen krystaly (tyrkysové a fialové kruhy), lucerna a kabina výtahu. Krápníky, podzemní
jezírko, bedny s úlomky, měřicí přístroj s kabelem ke krystalu, důlní vozík.
Živé detaily (pulzující krystaly, jiskřičky, výboj mezi krystaly, kapky do louží, netopýr, světlušky
na stropě, lucerna): InteriorFxView.CAVE. NODES / EDGES jsou ZDROJ PRAVDY i pro TowerCave.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import walkmask  # noqa: E402
import tower_parts as P, light as L, figures as F, archive_parts as A, cave_parts as V  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 352
WALL_H = 88
SIDE = 10
NODES = {
    "jes_vytah": (88, 98), "jes_stred": (88, 160), "jes_krystal_1": (42, 206), "jes_krystal_2": (138, 226),
    "jes_krystal_3": (54, 314), "jes_pristroj": (98, 210), "jes_jezirko": (126, 270), "jes_vozik": (64, 262),
    "jes_bedny": (132, 146), "jes_lucerna": (72, 176),
}
EDGES = [("jes_vytah", "jes_stred"), ("jes_stred", "jes_bedny"), ("jes_stred", "jes_lucerna"),
         ("jes_lucerna", "jes_krystal_1"), ("jes_stred", "jes_pristroj"), ("jes_pristroj", "jes_krystal_2"),
         ("jes_krystal_1", "jes_vozik"), ("jes_vozik", "jes_jezirko"), ("jes_vozik", "jes_krystal_3")]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.zeros((AH, AW), bool)
BAL_Y = AH - 12
floor_px = np.zeros((AH, AW), bool); floor_px[WALL_H:BAL_Y, SIDE:AW - SIDE] = True

V.cave_floor(img, 0, WALL_H, AW, AH)
V.pool(img, 136, 302, 28, 15)
for y in range(AH):
    for x in range(AW):
        if ((x - 136) / 30) ** 2 + ((y - 302) / 17) ** 2 < 1:
            block[y, x] = True

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

k1 = place(V.crystal_cluster(1), 40, 196, foot=(22, 8))
k2 = place(V.crystal_cluster(2, violet=True), 140, 216, foot=(22, 8))
k3 = place(V.crystal_cluster(3, 40, 46), 52, 304, foot=(26, 8))
for i, (x, y, h) in enumerate(((24, 140, 24), (154, 172, 30), (112, 258, 26), (26, 250, 28), (96, 336, 18), (156, 250, 20))):
    place(V.stalagmite(i, h), x, y, foot=(8, 4))
for i, (x, y, vi) in enumerate(((96, 300, False), (158, 276, True), (22, 222, True), (120, 166, False), (88, 244, True), (152, 330, False))):
    place(V.crystal_cluster(20 + i, 16, 18, violet=vi), x, y, foot=(10, 4))      # drobné krystaly
place(V.crate(0), 130, 136, foot=(22, 8)); place(V.crate(1), 152, 140, foot=(22, 8))
app_ = place(V.apparatus(), 100, 200, foot=(26, 8))
cart = place(V.mine_cart(), 64, 252, foot=(30, 8))
lan = place(V.lantern_pole(), 72, 166, foot=(4, 3))
# kabel od přístroje ke krystalu (leží na zemi)
for t in np.linspace(0, 1, 60):
    x = 114 + (128 - 114) * t; y = 194 + (212 - 194) * t + 3 * np.sin(t * 6)
    img[int(y), int(x)] = V.CROCK[0]; img[int(y) - 1, int(x)] = V.IRON_[3] if int(x) % 3 == 0 else V.CROCK[3]

# ── světlo: tma, kruhy krystalů, lucerna ────────────────────────────────────
def ring_pool(mask, cx, cy, rx, ry, k=1.0):
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if 0 <= x < AW and 0 <= y < AH:
                d = (((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2) ** 0.5 + (0.04 if (x + y) % 2 else -0.04)
                v = 1.0 if d < 0.4 else 0.6 if d < 0.7 else 0.3 if d < 1 else 0
                mask[y, x] = max(mask[y, x], round(v * k, 1))

cyan = np.zeros((AH, AW)); violet = np.zeros((AH, AW)); warm = np.zeros((AH, AW))
ring_pool(cyan, 40, 186, 30, 20); ring_pool(cyan, 52, 292, 34, 22)
ring_pool(violet, 140, 206, 30, 20)
for (x, y, vi) in ((96, 296, False), (158, 272, True), (22, 218, True), (120, 162, False), (88, 240, True), (152, 326, False)):
    ring_pool(violet if vi else cyan, x, y, 12, 8, 0.6)
ring_pool(warm, lan["x"] + 11, lan["y"] + 12, 24, 15); ring_pool(warm, 88, WALL_H + 3, 24, 12, 0.8)
light = np.maximum(np.maximum(cyan, violet), warm)
sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=12, k=0.8)
L.side_wall_shadow(sh, SIDE, AW - SIDE, depth=10, k=0.7, y0=WALL_H)
for o in placed:
    s = o["spr"]
    L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], 0.50, 0.28, 0.8)
    L.contact_shadow(sh, o["x"] + s.w / 2, o["y"] + s.h - 1, s.w / 2 + 1, 2.4, 1.0)
sh *= floor_px
L.darken(img, sh, 0.5, (4, 2, 8), dither=False, steps=4)

img[:WALL_H] = V.cave_wall(AW, WALL_H).a
block[:WALL_H + 3, :] = True

def wall_put(spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < AH and 0 <= x + xx < AW:
                img[y + yy, x + xx] = spr.a[yy, xx]

for yy in range(22, WALL_H):                                              # rám výtahu + železné nosníky
    for xx in range(62, 115):
        if xx < 68 or xx > 108 or yy < 30:
            img[yy, xx] = P.MARB[4] if xx in (64, 65) or yy == 22 else P.MARB[1] if xx in (111, 112) else P.MARB[3]
            if xx in (62, 63, 113, 114):
                img[yy, xx] = F.IRON_[3] if xx in (62, 113) else F.IRON_[1]
for xx in range(58, 119):
    img[20, xx] = F.IRON_[4]; img[21, xx] = F.IRON_[2]
elev = F.elevator3d(40, 58)
EX0, EY0 = 88 - 20, WALL_H + 2 - 58
wall_put(elev, EX0, EY0)
wall_put(F.floor_dial3d(10, at=6), 88 - 12, 2)

for y in range(WALL_H - 6, AH):                                           # boční stěny: tmavá skála
    for x in range(SIDE):
        for (X, inner) in ((x, x), (AW - 1 - x, x)):
            h = V._h(X // 3, y // 4, 7)
            col = V.CROCK[[2, 3, 2, 1][h % 4]].copy() if inner < 7 else V.CROCK[0].copy()
            if X > AW // 2:
                col[:3] = (col[:3] * 0.7).astype(np.uint8)
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

# noc jeskyně: všechno ztmavit mimo světla, pak obarvit kruhy
dark = np.clip(1.0 - light, 0, 1); dark[:WALL_H] *= 0.6
L.darken(img, dark, 0.5, (4, 3, 10), dither=True, steps=10)
L.lighten(img, cyan, 0.24, (90, 230, 240), steps=10)
L.lighten(img, violet, 0.24, (180, 120, 250), steps=10)
L.lighten(img, warm, 0.22, (255, 200, 120), steps=10)
L.reflect(img, (EX0 + 4, EY0 + 8, EX0 + 36), WALL_H + 3, 10, k=0.14, floor_mask=floor_px)
vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.55, AW * 0.6, AH * 0.6, k=0.5)
L.darken(img, vg, 0.8, (2, 2, 8), dither=True, steps=10)

Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_cave.png"))
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "tower_cave.txt"), "w") as f:
    f.write(f"# mapa chůze tower_cave: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_cave.py)\n{AW} {AH} {CELL}\n")
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
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "tower_cave_debug.png"))
print("ok", "crystals", [(o["x"], o["y"], o["spr"].w, o["spr"].h) for o in (k1, k2, k3)], "app", (app_["x"], app_["y"]), "lantern", (lan["x"], lan["y"]))

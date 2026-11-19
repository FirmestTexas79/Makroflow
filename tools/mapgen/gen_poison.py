"""
Použití:  python tools/mapgen/gen_poison.py
Výstup:   app/src/main/res/drawable-nodpi/tower_poison.png, app/src/main/assets/walk/tower_poison.txt,
          tools/mapgen/tower_poison_debug.png

Jedová laboratoř – 12. patro Věže Řádu Váhy (typ JED), 176 × 352 v pohledu 3/4 shora. Zarostlý skleník, kde Řád
vaří spánek a zapomnění: zamlžené sklo v železných rámech, za ním tlačící se liány, police s jedy, cedule
POZOR – JED, plynové masky (jedna chybí). Mechem prorostlé břidlicové dlaždice, louže jedu (setkání), pracovní stůl
s dávkami SPÁNEK, obří masožravka, dvě kádě s bublající kaší, měděný destilační přístroj s baňkou ZAPOMNĚNÍ,
záhony rulíku, bolehlavu a fialových hub se jmenovkami.
Živé detaily (bubliny v kádích, zelená mlha, spory, kapky z hadu, sklapnutí masožravky): InteriorFxView.POISON.
NODES / EDGES jsou ZDROJ PRAVDY i pro TowerPoison.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import walkmask  # noqa: E402
import light as L, figures as F, archive_parts as A, poison_parts as P  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 352
WALL_H = 96
SIDE = 10
PUDDLES = [(88, 176, 13, 6), (52, 292, 12, 6), (126, 300, 13, 6)]
NODES = {
    "jed_vytah": (88, 104), "jed_masky": (146, 106), "jed_stred": (88, 158), "jed_stul": (44, 170),
    "jed_past": (138, 176), "jed_louze_1": (88, 178), "jed_kad": (34, 244), "jed_kotel": (100, 268),
    "jed_louze_2": (52, 292), "jed_louze_3": (126, 300), "jed_zahon": (88, 318),
}
EDGES = [("jed_vytah", "jed_stred"), ("jed_vytah", "jed_masky"), ("jed_stred", "jed_stul"),
         ("jed_stred", "jed_past"), ("jed_stred", "jed_louze_1"), ("jed_louze_1", "jed_kad"),
         ("jed_louze_1", "jed_kotel"), ("jed_kad", "jed_louze_2"), ("jed_kotel", "jed_louze_3"),
         ("jed_louze_2", "jed_zahon"), ("jed_louze_3", "jed_zahon")]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.zeros((AH, AW), bool)
BAL_Y = AH - 12
floor_px = np.zeros((AH, AW), bool); floor_px[WALL_H:BAL_Y, SIDE:AW - SIDE] = True

P.lab_floor(img, SIDE, WALL_H, AW - SIDE, AH)
for i, (x, y, rx, ry) in enumerate(PUDDLES):
    P.toxic_puddle(img, x, y, rx, ry, seed=i)

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

bench = place(P.workbench(), 42, 158, foot=(56, 10))
trap = place(P.flytrap(), 146, 166, foot=(22, 8))
vat_l = place(P.vat(1), 32, 232, foot=(34, 12))
vat_r = place(P.vat(2), 150, 248, foot=(34, 12))
alem = place(P.alembic(), 90, 258, foot=(62, 16))
place(P.poison_bed(1), 40, 336, foot=(46, 12))
place(P.poison_bed(2), 136, 336, foot=(46, 12))
place(P.drum(1), 156, 296, foot=(16, 8))
place(P.drum(2), 20, 282, foot=(16, 8))
place(P.drum(3), 30, 286, foot=(16, 8))

# ── světlo: tma, nemocně zelená záře z kádí, louží a baňky ────────────────────
def ring_pool(mask, cx, cy, rx, ry, k=1.0):
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if 0 <= x < AW and 0 <= y < AH:
                d = (((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2) ** 0.5 + (0.04 if (x + y) % 2 else -0.04)
                v = 1.0 if d < 0.4 else 0.6 if d < 0.7 else 0.3 if d < 1 else 0
                mask[y, x] = max(mask[y, x], round(v * k, 1))
green = np.zeros((AH, AW))
for o in (vat_l, vat_r):
    ring_pool(green, o["x"] + 20, o["y"] + 10, 26, 16, 0.9)
for (x, y, rx, ry) in PUDDLES:
    ring_pool(green, x, y, rx + 10, ry + 6, 0.7)
ring_pool(green, alem["x"] + 40, alem["y"] + 60, 18, 10, 0.8)
ring_pool(green, alem["x"] + 20, alem["y"] + 62, 14, 8, 0.6)
ring_pool(green, 88, WALL_H + 3, 22, 11, 0.4)
violet = np.zeros((AH, AW))
for bx in (40, 136):
    ring_pool(violet, bx, 320, 28, 12, 0.8)
sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=14, k=0.8)
L.side_wall_shadow(sh, SIDE, AW - SIDE, depth=10, k=0.6, y0=WALL_H)
for o in placed:
    s = o["spr"]
    L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], 0.4, 0.25, 0.65)
    L.contact_shadow(sh, o["x"] + s.w / 2, o["y"] + s.h - 1, s.w / 2 + 1, 2.4, 1.0)
sh *= floor_px
L.darken(img, sh, 0.5, (2, 8, 4), dither=False, steps=4)

img[:WALL_H] = P.greenhouse_wall(AW, WALL_H).a
block[:WALL_H + 3, :] = True

def wall_put(spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < AH and 0 <= x + xx < AW:
                img[y + yy, x + xx] = spr.a[yy, xx]

wall_put(P.bottle_shelf(44), 12, 36)
wall_put(P.skull_sign(), 124, 30)
wall_put(P.gas_masks(3, missing=1), 140, 64)
for yy in range(22, WALL_H):                                              # rám výtahu: rezavé železo s mechem
    for xx in range(62, 115):
        if xx < 68 or xx > 108 or yy < 30:
            col = P.IRON_[3] if xx in (62, 63) or yy == 22 else P.IRON_[0] if xx in (113, 114) else P.IRON_[1]
            if P._h(xx, yy, 3) % 7 == 0:
                col = P.MOSSG[2] if P._h(xx, yy, 4) % 2 else P.c(0x6A3A1E)
            img[yy, xx] = col
wall_put(F.elevator3d(40, 58), 88 - 20, WALL_H + 2 - 58)
wall_put(F.floor_dial3d(10, at=12), 88 - 12, 2)

for y in range(WALL_H - 6, AH):                                           # boční stěny: cihly s mechem
    for x in range(SIDE):
        for (X, inner) in ((x, x), (AW - 1 - x, x)):
            col = (P.BRICKM[3] if inner < 2 else P.BRICKM[2] if (y // 4 + inner // 4) % 2 else P.BRICKM[1]).copy()
            if P._h(X // 2, y // 2, 8) % 4 == 0:
                col = P.MOSSG[2].copy()
            if X > AW // 2:
                col[:3] = (col[:3] * 0.72).astype(np.uint8)
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

dark = np.clip(1.0 - np.maximum(green, violet * 0.8), 0, 1); dark[:WALL_H] *= 0.6
L.darken(img, dark, 0.5, (4, 10, 8), dither=True, steps=10)
L.lighten(img, green, 0.24, (120, 255, 90), steps=10)
L.lighten(img, violet, 0.2, (200, 120, 255), steps=8)
vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.55, AW * 0.6, AH * 0.6, k=0.5)
L.darken(img, vg, 0.8, (2, 6, 4), dither=True, steps=10)

Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_poison.png"))
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "tower_poison.txt"), "w") as f:
    f.write(f"# mapa chůze tower_poison: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_poison.py)\n{AW} {AH} {CELL}\n")
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
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "tower_poison_debug.png"))
print("ok bench", (bench["x"], bench["y"]), "trap", (trap["x"], trap["y"]), "vats", (vat_l["x"], vat_l["y"]), (vat_r["x"], vat_r["y"]), "alem", (alem["x"], alem["y"]))

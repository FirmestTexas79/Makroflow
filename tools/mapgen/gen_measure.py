"""
Použití:  python tools/mapgen/gen_measure.py
Výstup:   app/src/main/res/drawable-nodpi/tower_measure.png, app/src/main/assets/walk/tower_measure.txt,
          tools/mapgen/tower_measure_debug.png

Měřírna – 2. patro Věže Řádu Váhy, interiér 176 × 320 v pohledu 3/4 shora. Den, studené světlo z oken
na zelenošedé kachle a linoleum. Uprostřed velké váhy, na kterých se váží spisy (ne těla), dvě měřicí
kabiny se závěsem, řady prázdných očíslovaných židlí, stojan se závažími, stůl úředníka s metronomem,
stanice potrubní pošty a graf „spánek okruhů“, který klesá pod červenou čáru normy.
Živé detaily (kyvadlo, metronom, kapsle, kontrolka OBSAZENO): InteriorFxView.MEASURE.
NODES / EDGES jsou ZDROJ PRAVDY i pro TowerMeasure.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import walkmask  # noqa: E402
import tower_parts as P, light as L, figures as F, archive_parts as A, measure_parts as M  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 320
WALL_H = 88
SIDE = 10
NODES = {
    "meri_vytah": (88, 100), "meri_stred": (88, 140), "meri_kabina_l": (30, 160), "meri_kabina_p": (146, 160),
    "meri_vahy": (88, 232), "meri_zidle": (40, 298), "meri_zavazi": (144, 262), "meri_stul": (100, 300),
    "meri_graf": (48, 100), "meri_posta": (130, 100), "meri_hodiny": (148, 212), "meri_kartoteka": (34, 232),
}
EDGES = [("meri_vytah", "meri_stred"), ("meri_vytah", "meri_graf"), ("meri_vytah", "meri_posta"),
         ("meri_stred", "meri_kabina_l"), ("meri_stred", "meri_kabina_p"), ("meri_stred", "meri_vahy"),
         ("meri_vahy", "meri_zidle"), ("meri_vahy", "meri_zavazi"), ("meri_vahy", "meri_stul"), ("meri_vahy", "meri_hodiny"), ("meri_vahy", "meri_kartoteka")]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.zeros((AH, AW), bool)
BAL_Y = AH - 12
floor_px = np.zeros((AH, AW), bool); floor_px[WALL_H:BAL_Y, SIDE:AW - SIDE] = True

M.linoleum(img, 0, WALL_H, AW, AH)
P.carpet(img, 79, 98, WALL_H + 2, 160)

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

place(M.booth(1), 30, 150, foot=(36, 16)); place(M.booth(2), 146, 150, foot=(36, 16))
scale = place(M.balance_scale(), 88, 222, foot=(36, 14))
for row, by in enumerate((258, 284)):
    for x in (22, 38, 54):
        place(M.chair(row * 3 + x), x, by, foot=(12, 6))
place(M.weights_rack(), 144, 250, foot=(42, 12))
desk = place(M.clerk_desk(), 136, 296, foot=(54, 16))
place(F.filing_cabinet(1), 22, 226, foot=(24, 10)); place(F.filing_cabinet(3), 46, 226, foot=(24, 10))
glass = place(M.hourglass(), 148, 204, foot=(20, 8))
place(P.dead_plant(), 60, 106, foot=(10, 6)); place(P.dead_plant(), 116, 106, foot=(10, 6))

# ── světlo: kužely z oken, studený den ──────────────────────────────────────
lit = np.zeros((AH, AW))
WIN = [(22, 16, 20, 50), (154, 16, 20, 50)]
for (wx, wy, ww, wh) in WIN:
    L.light_shaft(lit, (wx - ww // 2 + 1, WALL_H), (wx + ww // 2 - 1, WALL_H), 0.46 if wx < 88 else -0.34, 150)
lit *= floor_px
L.lighten(img, lit, 0.30, (236, 244, 250))

sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=16, k=0.95)
L.side_wall_shadow(sh, SIDE, AW - SIDE, depth=12, k=0.8, y0=WALL_H)
for o in placed:
    s = o["spr"]
    L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], 0.50, 0.28, 0.8)
    L.contact_shadow(sh, o["x"] + s.w / 2, o["y"] + s.h - 1, s.w / 2 + 1, 2.4, 1.0)
sh *= floor_px
L.darken(img, sh, 0.5, (14, 22, 26), dither=False, steps=4)

# ── zadní stěna ──────────────────────────────────────────────────────────────
img[:WALL_H] = M.tiled_wall(AW, WALL_H).a
block[:WALL_H + 3, :] = True

def wall_put(spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < AH and 0 <= x + xx < AW:
                img[y + yy, x + xx] = spr.a[yy, xx]

for (wx, wy, ww, wh) in WIN:
    wall_put(P.window(ww, wh, seed=wx + 2), wx - ww // 2, wy)
for yy in range(4, WALL_H):                                               # mramorový rám výtahu
    for xx in range(64, 113):
        if xx < 68 or xx > 108 or yy < 30:
            img[yy, xx] = M.MARB[4] if xx in (64, 65) or yy == 4 else M.MARB[1] if xx in (111, 112) else M.MARB[3]
elev = F.elevator3d(40, 58)
EX0, EY0 = 88 - 20, WALL_H + 2 - 58
wall_put(elev, EX0, EY0)
dial = F.floor_dial3d(13, at=2)
wall_put(dial, 88 - dial.w // 2, 6)
wall_put(M.wall_chart(), 33, 24)
wall_put(M.tube_station(), 118, WALL_H - 40)
wall_put(A.wall_clock(6, pendulum=False), 124, 6)                         # kyvadlo: InteriorFxView (pivot 131,22)
L.reflect(img, (EX0 + 4, EY0 + 8, EX0 + 36), WALL_H + 3, 12, k=0.14, floor_mask=floor_px)

for y in range(WALL_H - 6, AH):                                           # boční stěny z kachlí
    for x in range(SIDE):
        for (X, inner) in ((x, x), (AW - 1 - x, x)):
            left = X < AW // 2
            if inner < 3:
                col = M.TILE[4] if inner == 1 else M.TILE[3] if inner == 2 else M.TILE[1]
            else:
                t = (inner - 3) / (SIDE - 3)
                col = (M.TILE[2] if left else M.TILE[1]).copy()
                k = (0.15 + 0.4 * t) if left else (0.35 + 0.4 * t)
                if (y // 6) % 2 == 0 and inner == SIDE - 1:
                    col = M.TILE[0].copy()
                col[:3] = (col[:3] * (1 - k) + np.array([14, 22, 26]) * k).astype(np.uint8)
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

rng = np.random.default_rng(22)                                           # prach v kuželech světla
for _ in range(60):
    x, y = int(rng.integers(SIDE, AW - SIDE)), int(rng.integers(WALL_H + 2, BAL_Y))
    if lit[y, x] > 0.35:
        img[y, x, :3] = (246, 250, 252)
vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.55, AW * 0.62, AH * 0.62, k=0.45)
L.darken(img, vg, 0.8, (10, 16, 20), dither=True, steps=10)
L.tone(img, (150, 190, 190), (200, 200, 180), k=0.08)

Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_measure.png"))
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "tower_measure.txt"), "w") as f:
    f.write(f"# mapa chůze tower_measure: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_measure.py)\n{AW} {AH} {CELL}\n")
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
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "tower_measure_debug.png"))
print("ok", AW, AH, "desk", (desk["x"], desk["y"]), "scale", (scale["x"], scale["y"]))

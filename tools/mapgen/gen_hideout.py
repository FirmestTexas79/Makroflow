"""
Použití:  python tools/mapgen/gen_hideout.py
Výstup:   app/src/main/res/drawable-nodpi/tower_hideout.png, app/src/main/assets/walk/tower_hideout.txt,
          tools/mapgen/tower_hideout_debug.png

Patro −1 Věže Řádu Váhy – Hunfreyho skrýš (176 × 352, pohled 3/4 shora). Technické podlaží, které na panelu
výtahu nemá tlačítko: holý beton, svazek zrezivělých trubek, louže kondenzátu. Hunfrey (Okruh 212) se tu skrýval
po útěku z Mrazírny. Rozházené polní lehátko (jediná postel ve Věži, ve které někdo ležel), bedna jako stůl
se svíčkami a sešitem (poslední zápis), mapa kořenů na zdi s rudými nitěmi od stanic 0–7 k jednomu místu dole,
čárky křídou za probdělé noci, maska „VYDÁNO – SUBJEKT 212“ na háčku, ukradené spisy, zapečetěný poklop dolů.
Bez setkání (příběhové patro). Živé detaily: InteriorFxView.HIDEOUT. NODES / EDGES jsou ZDROJ PRAVDY i pro
TowerHideout.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import walkmask  # noqa: E402
import light as L, figures as F, archive_parts as A, cave_parts as CV, hideout_parts as P  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 352
WALL_H = 96
SIDE = 10
NODES = {
    "minus_vytah": (88, 104), "minus_stred": (88, 168), "minus_mapa": (40, 104), "minus_carky": (142, 104),
    "minus_maska": (158, 112), "minus_lehatko": (50, 190), "minus_sesit": (86, 200), "minus_spisy": (140, 214),
    "minus_poklop": (88, 270),
}
EDGES = [("minus_vytah", "minus_stred"), ("minus_vytah", "minus_mapa"), ("minus_vytah", "minus_carky"),
         ("minus_carky", "minus_maska"), ("minus_stred", "minus_lehatko"), ("minus_stred", "minus_sesit"),
         ("minus_stred", "minus_spisy"), ("minus_sesit", "minus_poklop"), ("minus_spisy", "minus_poklop"),
         ("minus_lehatko", "minus_poklop")]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.zeros((AH, AW), bool)
BAL_Y = AH - 12
floor_px = np.zeros((AH, AW), bool); floor_px[WALL_H:BAL_Y, SIDE:AW - SIDE] = True

P.concrete_floor(img, SIDE, WALL_H, AW - SIDE, AH)
for (x, y, rx, ry, sd) in ((24, 122, 9, 4, 1), (150, 300, 11, 5, 2), (60, 318, 7, 3, 3), (134, 150, 5, 2, 4)):
    P.puddle(img, x, y, rx, ry, sd)
A.loose_papers(img, [(64, 206), (72, 222), (80, 238), (96, 230), (110, 244), (120, 222), (70, 250)], np.random.default_rng(7))
for i in range(14):                                                           # šipka křídou k poklopu
    img[236 + i, 88] = P.c(0xC8C4B8) if i % 3 else P.CONC[3]
for d in range(5):
    img[248 - d, 88 - d] = P.c(0xC8C4B8); img[248 - d, 88 + d] = P.c(0xC8C4B8)
for a in range(0, 360, 8):                                                    # kruh křídou kolem poklopu
    import math
    x = int(88 + math.cos(math.radians(a)) * 26); y = int(280 + math.sin(math.radians(a)) * 15)
    if a % 24:
        img[y, x] = P.c(0xB8B4A8)

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

hatch = place(P.sealed_hatch(16), 88, 292, collide=False, shadow=False)
block[278:288, 78:98] = True                                                  # na plombu se nestoupá
cot = place(P.camp_cot(), 44, 190, pad=1)
place(P.bucket(), 26, 118, pad=1)
place(P.bowl(), 66, 204, collide=False)
table = place(P.crate_table(), 86, 194, pad=1)
place(P.candle_on_floor(), 66, 190, collide=False)
place(P.candle_on_floor(), 106, 186, collide=False)
place(A.map_roll(), 104, 196, pad=1)
place(A.box_stack(2), 142, 206, pad=1)
place(A.box_stack(5), 158, 222, pad=1)
place(A.box_stack(8), 130, 228, pad=1)
lamp = place(CV.lantern_pole(), 122, 286, foot=(6, 4))
place(P.candle_on_floor(), 56, 300, collide=False)

# ── světlo: tma, teplé tůně svíček a lucerny, studený pruh od výtahu ─────────
def ring_pool(mask, cx, cy, rx, ry, k=1.0):
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if 0 <= x < AW and 0 <= y < AH:
                d = (((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2) ** 0.5 + (0.04 if (x + y) % 2 else -0.04)
                v = 1.0 if d < 0.4 else 0.6 if d < 0.7 else 0.3 if d < 1 else 0
                mask[y, x] = max(mask[y, x], round(v * k, 1))
warm = np.zeros((AH, AW))
ring_pool(warm, 80, 188, 34, 22, 1.0)
ring_pool(warm, 66, 186, 12, 8, 0.8); ring_pool(warm, 106, 182, 12, 8, 0.8)
ring_pool(warm, 120, 270, 34, 20, 0.9)
ring_pool(warm, 88, 282, 26, 15, 0.5)
ring_pool(warm, 56, 296, 12, 7, 0.6)
ring_pool(warm, 40, 70, 30, 26, 0.35)                                         # svíčka osvětluje mapu
cold = np.zeros((AH, AW))
L.light_shaft(cold, (72, WALL_H), (104, WALL_H), 0.0, 70)
cold *= floor_px
sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=16, k=0.9)
L.side_wall_shadow(sh, SIDE, AW - SIDE, depth=12, k=0.7, y0=WALL_H)
for o in placed:
    s = o["spr"]
    if not o["shadow"]:
        continue
    L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], 0.42, 0.26, 0.65)
    L.contact_shadow(sh, o["x"] + s.w / 2, o["y"] + s.h - 1, s.w / 2 + 1, 2.4, 1.0)
sh *= floor_px
L.darken(img, sh, 0.55, (4, 4, 6), dither=False, steps=4)

wall = P.service_wall(AW, WALL_H)
img[:WALL_H] = wall.a
block[:WALL_H + 3, :] = True

def wall_put(spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < AH and 0 <= x + xx < AW:
                img[y + yy, x + xx] = spr.a[yy, xx]

wall_put(P.root_map(48, 40), 14, 24)
tal = P.Spr(52, 30); P.tally_marks(tal, 2, 2, 9)
wall_put(tal, 120, 26)
wall_put(P.mask_hook(), 152, 58)
for yy in range(24, WALL_H):                                                  # hrubý ocelový rám služebního výtahu
    for xx in range(62, 115):
        if xx < 68 or xx > 108 or yy < 30:
            col = F.STEEL[3] if xx in (62, 63) or yy == 24 else F.STEEL[1] if xx in (113, 114) else F.STEEL[2]
            if (xx + yy) % 9 == 0 and yy > 30:
                col = P.c(0x5A3A22)                                           # rez
            img[yy, xx] = col
wall_put(F.elevator3d(40, 58), 88 - 20, WALL_H + 2 - 58)
wall_put(F.floor_dial3d(10, at=-1), 88 - 12, 2)                               # ručička pod nulou

for y in range(WALL_H - 6, AH):                                               # boční stěny: beton
    for x in range(SIDE):
        for (X, inner) in ((x, x), (AW - 1 - x, x)):
            col = (P.CONC[4] if inner < 2 else P.CONC[3] if (y // 14) % 2 else P.CONC[2]).copy()
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

for y in range(BAL_Y, AH):                                                    # dole stěna místo balustrády
    for x in range(AW):
        img[y, x] = P.CONC[1] if y > BAL_Y + 1 else P.CONC[3]
block[BAL_Y - 2:, :] = True

dark = np.clip(1.0 - np.maximum(warm, cold * 0.6), 0, 1); dark[:WALL_H] *= 0.6
L.darken(img, dark, 0.5, (4, 4, 8), dither=True, steps=10)
L.lighten(img, warm, 0.24, (255, 190, 110), steps=10)
L.lighten(img, cold, 0.12, (170, 190, 220), steps=8)
vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.52, AW * 0.58, AH * 0.56, k=0.6)
L.darken(img, vg, 0.85, (2, 2, 4), dither=True, steps=10)

Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_hideout.png"))
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "tower_hideout.txt"), "w") as f:
    f.write(f"# mapa chůze tower_hideout: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_hideout.py)\n{AW} {AH} {CELL}\n")
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
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "tower_hideout_debug.png"))
print("ok cot", (cot["x"], cot["y"]), "table", (table["x"], table["y"]), "hatch", (hatch["x"], hatch["y"]), "lamp", (lamp["x"], lamp["y"]))

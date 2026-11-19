"""
Použití:  python tools/mapgen/gen_dorm.py
Výstup:   app/src/main/res/drawable-nodpi/tower_dorm.png, app/src/main/assets/walk/tower_dorm.txt,
          tools/mapgen/tower_dorm_debug.png

Dormitoř – 8. patro Věže Řádu Váhy (patro duchů), 176 × 352 v pohledu 3/4 shora. Věčná noc: dlouhá ložnice
„kalibrovaných“, osm železných postelí (pod většinou přikrývek spáč, který se neprobudí), svíčky na nočních
stolcích, vysoká okna s průsvitnými záclonami a měsícem, cedule TICHO – PROBÍHÁ SPÁNEK, hodiny na 6:12,
stolek noční sestry s knihou spánku, paraván se stínem postavy, studená mlha (setkání).
Živé detaily (duchové, Zzz nad postelemi, svíčky, mlha, kyvadlo, měsíční prach): InteriorFxView.DORM.
NODES / EDGES jsou ZDROJ PRAVDY i pro TowerDorm.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import walkmask  # noqa: E402
import tower_parts as P, light as L, figures as F, archive_parts as A, ghost_parts as Gh  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 352
WALL_H = 96
SIDE = 10
NODES = {
    "duch_vytah": (88, 104), "duch_stred": (88, 206), "duch_mlha_1": (30, 292), "duch_mlha_2": (146, 288),
    "duch_mlha_3": (88, 300), "duch_postele_l": (42, 200), "duch_postele_p": (134, 200), "duch_stul": (112, 330),
    "duch_paravan": (62, 330), "duch_okno": (36, 104),
}
EDGES = [("duch_vytah", "duch_stred"), ("duch_vytah", "duch_okno"), ("duch_stred", "duch_postele_l"),
         ("duch_stred", "duch_postele_p"), ("duch_stred", "duch_mlha_3"), ("duch_mlha_3", "duch_mlha_1"),
         ("duch_mlha_3", "duch_mlha_2"), ("duch_mlha_3", "duch_stul"), ("duch_mlha_3", "duch_paravan")]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.zeros((AH, AW), bool)
BAL_Y = AH - 12
floor_px = np.zeros((AH, AW), bool); floor_px[WALL_H:BAL_Y, SIDE:AW - SIDE] = True

Gh.floorboards(img, 0, WALL_H, AW, AH)

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

beds, candles = [], []
for row, by in enumerate((180, 252)):
    for j, x in enumerate((24, 60, 116, 152)):
        beds.append(place(Gh.iron_bed(row * 4 + j, sleeper=(row * 4 + j) not in (2, 5)), x, by, foot=(26, 34)))
    for x in (42, 134):
        candles.append(place(Gh.candle_stand(), x, by - 26, foot=(12, 6)))
desk = place(Gh.nurse_desk(), 120, 324, foot=(40, 14))
place(Gh.screen(), 40, 326, foot=(40, 6))

# ── noc: tma, svíčky, měsíční světlo z oken ─────────────────────────────────
def ring_pool(mask, cx, cy, rx, ry, k=1.0):
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if 0 <= x < AW and 0 <= y < AH:
                d = (((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2) ** 0.5 + (0.04 if (x + y) % 2 else -0.04)
                v = 1.0 if d < 0.4 else 0.6 if d < 0.7 else 0.3 if d < 1 else 0
                mask[y, x] = max(mask[y, x], round(v * k, 1))
warm = np.zeros((AH, AW))
for o in candles:
    ring_pool(warm, o["x"] + 5, o["y"] + 6, 20, 13, 0.9)
ring_pool(warm, desk["x"] + 30, desk["y"] + 8, 24, 15, 1.0)
ring_pool(warm, 88, WALL_H + 3, 22, 11, 0.7)
moon = np.zeros((AH, AW))
for wx in (24, 152):
    L.light_shaft(moon, (wx - 9, WALL_H), (wx + 9, WALL_H), 0.42 if wx < 88 else -0.42, 150)
moon *= floor_px
sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=14, k=0.9)
L.side_wall_shadow(sh, SIDE, AW - SIDE, depth=10, k=0.7, y0=WALL_H)
for o in placed:
    s = o["spr"]
    L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], 0.50, 0.28, 0.75)
    L.contact_shadow(sh, o["x"] + s.w / 2, o["y"] + s.h - 1, s.w / 2 + 1, 2.4, 1.0)
sh *= floor_px
L.darken(img, sh, 0.5, (4, 4, 12), dither=False, steps=4)

img[:WALL_H] = Gh.dorm_wall(AW, WALL_H).a
block[:WALL_H + 3, :] = True

def wall_put(spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < AH and 0 <= x + xx < AW:
                img[y + yy, x + xx] = spr.a[yy, xx]

for i, wx in enumerate((24, 152)):
    wall_put(Gh.curtained_window(24, 60, moon=(i == 0)), wx - 12, 12)
for yy in range(22, WALL_H):                                              # rám výtahu: tmavé dřevo + mosaz
    for xx in range(62, 115):
        if xx < 68 or xx > 108 or yy < 30:
            img[yy, xx] = Gh.WAINS[3] if xx in (62, 63) or yy == 22 else Gh.WAINS[0] if xx in (113, 114) else Gh.WAINS[2]
elev = F.elevator3d(40, 58)
EX0, EY0 = 88 - 20, WALL_H + 2 - 58
wall_put(elev, EX0, EY0)
wall_put(F.floor_dial3d(10, at=8), 88 - 12, 0)
wall_put(Gh.silence_sign(), 118, 30)
wall_put(A.wall_clock(7, pendulum=False), 42, 24)                       # kyvadlo: InteriorFxView (pivot 50,42)

for y in range(WALL_H - 6, AH):                                           # boční stěny: obklad
    for x in range(SIDE):
        for (X, inner) in ((x, x), (AW - 1 - x, x)):
            col = (Gh.WAINS[2] if inner < 3 else Gh.WAINS[1] if (y // 22) % 2 else Gh.WAINS[0]).copy()
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

dark = np.clip(1.0 - np.maximum(warm, moon * 0.6), 0, 1); dark[:WALL_H] *= 0.7
L.darken(img, dark, 0.45, (6, 6, 18), dither=True, steps=10)
L.lighten(img, warm, 0.20, (255, 200, 130), steps=10)
L.lighten(img, moon, 0.18, (150, 170, 255), steps=8)
vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.55, AW * 0.6, AH * 0.6, k=0.5)
L.darken(img, vg, 0.8, (2, 2, 10), dither=True, steps=10)

Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_dorm.png"))
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "tower_dorm.txt"), "w") as f:
    f.write(f"# mapa chůze tower_dorm: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_dorm.py)\n{AW} {AH} {CELL}\n")
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
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "tower_dorm_debug.png"))
print("ok candles", [(o["x"], o["y"]) for o in candles], "desk", (desk["x"], desk["y"]), "beds", [(o["x"], o["y"]) for o in beds])

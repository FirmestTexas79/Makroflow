"""
Použití:  python tools/mapgen/gen_water.py
Výstup:   app/src/main/res/drawable-nodpi/tower_water.png, app/src/main/assets/walk/tower_water.txt,
          tools/mapgen/tower_water_debug.png

Zatopené patro – 7. patro Věže Řádu Váhy (vodní Makromoni), 176 × 352 v pohledu 3/4 shora. Kancelář zaplavila
voda: zadní stěna je obří akvárium, po hladině vedou dřevěné lávky na kůlech ke dvěma plošinám, pod vodou
utopené stoly a židle, lekníny, rákos, loďka, záchranný kruh, ventil.
Živé detaily (ryby v akváriu, bubliny, kruhy na hladině, odlesky, plovoucí papír, žába): InteriorFxView.WATER.
NODES / EDGES jsou ZDROJ PRAVDY i pro TowerWater.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import walkmask  # noqa: E402
import tower_parts as P, light as L, figures as F, archive_parts as A, measure_parts as M, water_parts as Wp  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 352
WALL_H = 96
SIDE = 10
NODES = {
    "zat_vytah": (88, 104), "zat_stred": (88, 194), "zat_voda_1": (32, 216), "zat_voda_2": (150, 300),
    "zat_voda_3": (88, 326), "zat_kruh": (24, 184), "zat_lodka": (126, 300), "zat_ventil": (66, 196),
    "zat_rakos": (156, 262),
}
EDGES = [("zat_vytah", "zat_stred"), ("zat_stred", "zat_ventil"), ("zat_ventil", "zat_kruh"), ("zat_kruh", "zat_voda_1"),
         ("zat_stred", "zat_voda_3"), ("zat_voda_3", "zat_lodka"), ("zat_lodka", "zat_voda_2"), ("zat_voda_2", "zat_rakos")]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
BAL_Y = AH - 12
deck = np.zeros((AH, AW), bool)                                           # lávky a plošiny (chodí se jen po nich)
deck[WALL_H:BAL_Y, 78:99] = True                                          # hlavní lávka od výtahu
deck[186:203, SIDE:79] = True                                             # odbočka vlevo
deck[166:226, SIDE:56] = True                                             # levá plošina
deck[276:293, 98:AW - SIDE] = True                                        # odbočka vpravo
deck[256:312, 122:AW - SIDE] = True                                       # pravá plošina
deck[WALL_H:WALL_H + 14, 60:117] = True                                   # nástupiště u výtahu
wmask = np.zeros((AH, AW), bool); wmask[WALL_H:BAL_Y, SIDE:AW - SIDE] = True
wmask &= ~deck
block = ~deck.copy()

img[WALL_H:, :] = Wp.WATER[1]
Wp.water(img, wmask, WALL_H)

# utopené věci pod hladinou (kreslí se do vody, ztmavené)
def sink(spr, cx, by):
    Wp.tint_under(spr, 0.55)
    x0, y0 = int(cx - spr.w // 2), int(by - spr.h)
    for yy in range(spr.h):
        for xx in range(spr.w):
            X, Y = x0 + xx, y0 + yy
            if spr.a[yy, xx, 3] and 0 <= X < AW and 0 <= Y < AH and wmask[Y, X]:
                img[Y, X] = spr.a[yy, xx]
sink(M.clerk_desk(), 40, 300); sink(M.chair(1), 62, 262); sink(M.chair(2), 24, 250); sink(M.chair(4), 140, 160)
sink(F.filing_cabinet(2), 148, 220); sink(M.chair(5), 116, 220)
for i, (x, y) in enumerate(((34, 140), (60, 330), (124, 130), (150, 236), (112, 330))):    # utopené listy papíru
    for dx in range(5):
        for dy in range(3):
            if wmask[y + dy, x + dx]:
                img[y + dy, x + dx, :3] = (np.array([200, 210, 200]) * 0.5 + img[y + dy, x + dx, :3] * 0.5).astype(np.uint8)

# lávky: prkna napříč, přední hrana a kůly do vody
for y in range(WALL_H, BAL_Y):
    for x in range(AW):
        if not deck[y, x]:
            continue
        vertical = 78 <= x < 99 and not (166 <= y < 226 and x < 79) and not (256 <= y < 312 and x >= 98)
        plank = (y // 4) if vertical else (x // 4)
        col = Wp.WOOD[3] if plank % 2 else Wp.WOOD[2]
        if (y % 4 == 0 if vertical else x % 4 == 0):
            col = Wp.WOOD[1]
        if Wp._h(x, y) % 157 == 0:
            col = Wp.WOOD[4]
        img[y, x] = col
for y in range(WALL_H, BAL_Y):                                             # hrany lávek nad vodou
    for x in range(SIDE, AW - SIDE):
        if deck[y, x] and y + 1 < AH and wmask[y + 1, x]:
            img[y + 1, x] = Wp.WOOD[1]
            if y + 2 < AH and wmask[y + 2, x]:
                img[y + 2, x] = Wp.WOOD[0]
        if deck[y, x] and x + 1 < AW and wmask[y, x + 1]:
            img[y, x + 1] = Wp.WOOD[1]
        if deck[y, x] and x - 1 >= 0 and wmask[y, x - 1]:
            img[y, x - 1] = Wp.WOOD[4]
for (x, y) in ((77, 140), (99, 140), (77, 250), (99, 250), (77, 300), (99, 300), (40, 227), (12, 227), (130, 313), (160, 313), (60, 204)):
    for k in range(4):                                                      # kůly s kroužkem vody
        if 0 <= y + k < AH:
            img[y + k, x] = Wp.WOOD[2]; img[y + k, x + 1] = Wp.WOOD[0]
    img[y + 4, x - 1] = Wp.WATER[5]; img[y + 4, x + 2] = Wp.WATER[5]

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

for i, (x, y, r) in enumerate(((40, 250, 7), (56, 276, 6), (118, 150, 8), (142, 196, 6), (24, 292, 6), (110, 240, 8),
                               (62, 318, 7), (36, 152, 6), (152, 150, 7), (126, 214, 6))):
    place(Wp.lily_pad(i, r), x, y, collide=False, shadow=False, z=0)
for i, (x, y) in enumerate(((16, 150), (160, 196), (16, 330), (160, 240), (108, 120))):
    place(Wp.reeds(i), x, y, collide=False)
boat = place(Wp.rowboat(), 140, 334, collide=False)
place(Wp.life_ring(), 22, 178, foot=(4, 3))
place(Wp.pipe_valve(), 66, 236, collide=False)

sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=10, k=0.7)
L.side_wall_shadow(sh, SIDE, AW - SIDE, depth=10, k=0.7, y0=WALL_H)
for o in placed:
    if not o["shadow"]:
        continue
    s = o["spr"]
    L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], 0.50, 0.28, 0.6)
sh[:, :] *= 1
L.darken(img, sh * (np.arange(AH)[:, None] >= WALL_H), 0.45, (4, 14, 20), dither=False, steps=4)

EX0, EY0 = 88 - 20, WALL_H + 2 - 58
img[:WALL_H] = Wp.aquarium_wall(AW, WALL_H, EX0, EX0 + 40).a

def wall_put(spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < AH and 0 <= x + xx < AW:
                img[y + yy, x + xx] = spr.a[yy, xx]

for yy in range(20, WALL_H):                                              # mramorový rám výtahu
    for xx in range(62, 115):
        if xx < 68 or xx > 108 or yy < 30:
            img[yy, xx] = P.MARB[4] if xx in (62, 63) or yy == 20 else P.MARB[1] if xx in (113, 114) else P.MARB[3]
elev = F.elevator3d(40, 58)
wall_put(elev, EX0, EY0)
wall_put(F.floor_dial3d(10, at=7), 88 - 12, 0)

for y in range(WALL_H - 6, AH):                                           # boční stěny: kachle, mokré
    for x in range(SIDE):
        for (X, inner) in ((x, x), (AW - 1 - x, x)):
            lx, ly = X % 8, y % 6
            col = (M.TILE[2] if not (lx == 7 or ly == 5) else M.TILE[0]).copy() if inner < 7 else M.TILE[0].copy()
            k = 0.35 if X < AW // 2 else 0.55
            col[:3] = (col[:3] * (1 - k) + np.array([10, 40, 50]) * k).astype(np.uint8)
            img[y, X] = col
            block[y, X] = True
block[:WALL_H + 3, :] = True
block[WALL_H + 3:WALL_H + 14, 60:117] = False

for o in sorted(placed, key=lambda o: o["z"]):
    s = o["spr"]
    for yy in range(s.h):
        for xx in range(s.w):
            if s.a[yy, xx, 3] and 0 <= o["y"] + yy < AH and 0 <= o["x"] + xx < AW:
                img[o["y"] + yy, o["x"] + xx] = s.a[yy, xx]

wall_put(A.balustrade(AW - 2 * SIDE, AH - BAL_Y), SIDE, BAL_Y)
block[BAL_Y - 2:, :] = True

L.reflect(img, (EX0 + 4, EY0 + 8, EX0 + 36), WALL_H + 14, 10, k=0.18, floor_mask=wmask)
vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.55, AW * 0.62, AH * 0.6, k=0.45)
L.darken(img, vg, 0.75, (2, 10, 16), dither=True, steps=10)
L.tone(img, (120, 180, 200), (140, 170, 160), k=0.05)

Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_water.png"))
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "tower_water.txt"), "w") as f:
    f.write(f"# mapa chůze tower_water: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_water.py)\n{AW} {AH} {CELL}\n")
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
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "tower_water_debug.png"))
print("ok", "boat", (boat["x"], boat["y"]))

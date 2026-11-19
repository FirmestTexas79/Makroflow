"""
Použití:  python tools/mapgen/gen_fire.py
Výstup:   app/src/main/res/drawable-nodpi/tower_fire.png, app/src/main/assets/walk/tower_fire.txt,
          tools/mapgen/tower_fire_debug.png

Spalovna spisů – 10. patro Věže Řádu Váhy (ohnivé patro), 176 × 352 v pohledu 3/4 shora. Kotelna, kam padají spisy,
které Řád „uzavřel“. Stěna: začouzené cihly, pás tmavě zelených kachlí, dvě průzorová okénka, za kterými hoří,
parní potrubí s manometrem, mosazná násypka se skluzem. Podlaha z pálených dlaždic, od výtahu pás slzičkového plechu.
Uprostřed kulatá litinová spalovací pec s kouřovodem do stropu, z otevřeného ústí svítí oheň; kolem hromady uhlí,
pult topiče s knihou spálení, převázané balíky spisů, vozík se složkami. Popelnice a vyhrabaný popel = setkání (OHEŇ).
Živé detaily: InteriorFxView.FIRE. NODES / EDGES jsou ZDROJ PRAVDY i pro TowerFire.kt (MAP).
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import walkmask  # noqa: E402
import light as L, figures as F, archive_parts as A, fire_parts as P1, fire2 as P  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 352
WALL_H = 96
SIDE = 10
FURN_CX, FURN_BY = 88, 292
NODES = {
    "ohen_vytah": (88, 104), "ohen_stred": (88, 152), "ohen_ventil": (30, 106), "ohen_vozik": (140, 158),
    "ohen_stul": (40, 196), "ohen_uhli": (36, 254), "ohen_pec": (88, 306),
    "ohen_popel_1": (28, 312), "ohen_popel_2": (148, 312), "ohen_popel_3": (110, 326),
}
EDGES = [("ohen_vytah", "ohen_stred"), ("ohen_vytah", "ohen_ventil"), ("ohen_vytah", "ohen_vozik"),
         ("ohen_stred", "ohen_stul"), ("ohen_stred", "ohen_vozik"), ("ohen_stul", "ohen_uhli"),
         ("ohen_uhli", "ohen_popel_1"), ("ohen_vozik", "ohen_popel_2"), ("ohen_popel_1", "ohen_pec"),
         ("ohen_popel_2", "ohen_pec"), ("ohen_pec", "ohen_popel_3")]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.zeros((AH, AW), bool)
BAL_Y = AH - 12
floor_px = np.zeros((AH, AW), bool); floor_px[WALL_H:BAL_Y, SIDE:AW - SIDE] = True

P.tile_floor(img, SIDE, WALL_H, AW - SIDE, AH, runway=(76, 101), runway_y1=146)
GRATE = (74, 146, 103, 158)                                               # mříž na konci plechu, pod ní žhne
for y in range(GRATE[1], GRATE[3]):
    for x in range(GRATE[0], GRATE[2]):
        e = min(x - GRATE[0], GRATE[2] - 1 - x, y - GRATE[1], GRATE[3] - 1 - y)
        if e == 0:
            img[y, x] = P.CAST[3] if (y == GRATE[1] or x == GRATE[0]) else P.CAST[0]
        elif (x - GRATE[0]) % 3 == 0:
            img[y, x] = P.CAST[1]
        else:
            t = (y - GRATE[1]) / (GRATE[3] - GRATE[1])
            img[y, x] = P.FLAME[2] if t > 0.4 and (x + y) % 2 else P.FLAME[1] if t > 0.2 else P.HOT[3]

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

inc, info = P.incinerator(flue=60)
furn = dict(spr=inc, x=FURN_CX - info["cx"], y=FURN_BY - inc.h, z=FURN_BY, shadow=True)
placed.append(furn)
block[FURN_BY - 22:FURN_BY, FURN_CX - 33:FURN_CX + 34] = True
block[FURN_BY - inc.h + 4:FURN_BY - 22, FURN_CX - 9:FURN_CX + 10] = True        # kouřovod a kotel: hráč nesmí za ně
place(P.ash_spill(40, 14), 104, 336, collide=False, shadow=False)
place(P.folder_trolley(), 152, 140, foot=(36, 10))
place(P.stoker_desk(), 36, 184, foot=(34, 12))
place(P.folder_bundles(), 142, 206, foot=(30, 8))
place(P1.coal_pile(1, w=40, h=22, shovel=True), 34, 244, foot=(36, 10))
place(P1.coal_pile(4, w=36, h=20), 148, 250, foot=(32, 8))
place(P.ash_bin(1), 28, 334, foot=(22, 10))
place(P.ash_bin(3), 150, 334, foot=(22, 10))

# ── světlo: tma, žár z ústí pece a z průzorů ────────────────────────────────
mouth = (FURN_CX, FURN_BY - 14)
warm = np.zeros((AH, AW))
yy, xx = np.mgrid[0:AH, 0:AW]
d = np.hypot((xx - mouth[0]) / 1.0, (yy - mouth[1]) / 0.75)
cone = np.clip(1 - d / 96, 0, 1) * (0.55 + 0.45 * np.clip((yy - mouth[1] + 30) / 60, 0, 1))
warm = np.maximum(warm, np.round(cone * 6) / 6)
for (px_, py_) in ((44, 52), (132, 52)):
    pass
ld = np.hypot(xx - 48, (yy - 170) * 1.5)
warm = np.maximum(warm, np.round(np.clip(1 - ld / 22, 0, 1) * 0.6 * 5) / 5)
gd = np.hypot(xx - 88, (yy - 152) * 1.8)
warm = np.maximum(warm, np.round(np.clip(1 - gd / 30, 0, 1) * 0.7 * 5) / 5)
for (px_, py_) in ((44, 52), (132, 52)):
    dd = np.hypot(xx - px_, (yy - (WALL_H + 6)) * 1.6)
    warm = np.maximum(warm, np.round(np.clip(1 - dd / 26, 0, 1) * 0.6 * 5) / 5 * (yy > WALL_H))
sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=14, k=0.8)
L.side_wall_shadow(sh, SIDE, AW - SIDE, depth=10, k=0.6, y0=WALL_H)
for o in placed:
    if not o["shadow"]:
        continue
    s = o["spr"]
    ocx, oby = o["x"] + s.w / 2, o["y"] + s.h
    if o is not furn:                                                     # stín od pece (paprsek z ústí ven)
        vx, vy = ocx - mouth[0], oby - mouth[1]
        n = max(1.0, math.hypot(vx, vy))
        L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], 0.55 * vx / n, 0.45 * vy / n, 0.75)
    L.contact_shadow(sh, ocx, oby - 1, s.w / 2 + 2, 3, 1.0)
sh *= floor_px
L.darken(img, sh, 0.55, (8, 3, 3), dither=False, steps=4)

img[:WALL_H] = P.boiler_wall(AW, WALL_H, ports=((44, 52, 7), (132, 52, 7))).a
block[:WALL_H + 3, :] = True

def wall_put(spr, x, y):
    for yy_ in range(spr.h):
        for xx_ in range(spr.w):
            if spr.a[yy_, xx_, 3] and 0 <= y + yy_ < AH and 0 <= x + xx_ < AW:
                img[y + yy_, x + xx_] = spr.a[yy_, xx_]

wall_put(P1.steam_pipe(64), 19, 30)
wall_put(P.chute2(54), 144, 36)
for yy_ in range(22, WALL_H):                                             # rám výtahu: litina s nýty
    for xx_ in range(62, 115):
        if xx_ < 68 or xx_ > 108 or yy_ < 30:
            col = P.CAST[4] if xx_ in (62, 63) or yy_ == 22 else P.CAST[0] if xx_ in (113, 114) else P.CAST[2]
            if (xx_ in (65, 111) and yy_ % 6 == 0) or (yy_ == 26 and xx_ % 6 == 0):
                col = P.CAST[5]
            img[yy_, xx_] = col
elev = F.elevator3d(40, 58)
wall_put(elev, 88 - 20, WALL_H + 2 - 58)
wall_put(F.floor_dial3d(10, at=10), 88 - 12, 4)

for y in range(WALL_H - 6, AH):                                           # boční stěny: kachle a cihly
    for x in range(SIDE):
        for (X, inner) in ((x, x), (AW - 1 - x, x)):
            col = (P.CAST[3] if inner < 2 else P.GLAZE[2] if (y // 6 + inner // 4) % 2 else P.GLAZE[1]).copy()
            if (y % 6) == 5:
                col = P.GLAZE[0].copy()
            if X > AW // 2:
                col[:3] = (col[:3] * 0.72).astype(np.uint8)
            img[y, X] = col
            block[y, X] = True

for o in sorted(placed, key=lambda o: o["z"]):
    s = o["spr"]
    for yy_ in range(s.h):
        for xx_ in range(s.w):
            if s.a[yy_, xx_, 3] and 0 <= o["y"] + yy_ < AH and 0 <= o["x"] + xx_ < AW:
                img[o["y"] + yy_, o["x"] + xx_] = s.a[yy_, xx_]

wall_put(A.balustrade(AW - 2 * SIDE, AH - BAL_Y), SIDE, BAL_Y)
block[BAL_Y - 2:, :] = True

dark = np.clip(1.0 - warm, 0, 1); dark[:WALL_H] *= 0.75
L.darken(img, dark, 0.62, (12, 5, 5), dither=True, steps=10)
L.lighten(img, warm, 0.26, (255, 130, 50), steps=10)
vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.62, AW * 0.62, AH * 0.62, k=0.55)
L.darken(img, vg, 0.8, (5, 2, 2), dither=True, steps=10)

Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_fire.png"))
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "tower_fire.txt"), "w") as f:
    f.write(f"# mapa chůze tower_fire: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_fire.py)\n{AW} {AH} {CELL}\n")
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
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "tower_fire_debug.png"))
print("ok furnace", (furn["x"], furn["y"]), info)

"""
Použití:  python tools/mapgen/gen_fire.py
Výstup:   app/src/main/res/drawable-nodpi/tower_fire.png, app/src/main/assets/walk/tower_fire.txt,
          tools/mapgen/tower_fire_debug.png

Spalovna spisů – 10. patro Věže Řádu Váhy (ohnivé patro), 176 × 352 v pohledu 3/4 shora. Sem padají spisy, které
Řád „uzavřel“. Začouzená cihlová stěna, skluz na spisy s vozíkem plným složek, parní potrubí s manometrem,
litinové podlahové desky s mřížemi nad žárem, hromady uhlí (u jedné lopata), kupy popela (setkání, typ OHEŇ)
a velká pec s otevřenými dvířky, nejjasnější místo patra.
Živé detaily (plameny v peci, jiskry, hořící stránky, pára z ventilu, složka sjíždějící skluzem, žár mříží):
InteriorFxView.FIRE. NODES / EDGES jsou ZDROJ PRAVDY i pro TowerFire.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import walkmask  # noqa: E402
import light as L, figures as F, archive_parts as A, fire_parts as P  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 352
WALL_H = 96
SIDE = 10
NODES = {
    "ohen_vytah": (88, 104), "ohen_stred": (88, 196), "ohen_pec": (88, 320), "ohen_vozik": (138, 150),
    "ohen_ventil": (30, 106), "ohen_uhli": (40, 238), "ohen_popel_1": (34, 292), "ohen_popel_2": (142, 292),
    "ohen_popel_3": (100, 212),
}
EDGES = [("ohen_vytah", "ohen_stred"), ("ohen_vytah", "ohen_ventil"), ("ohen_vytah", "ohen_vozik"),
         ("ohen_stred", "ohen_uhli"), ("ohen_stred", "ohen_popel_3"), ("ohen_popel_3", "ohen_popel_2"),
         ("ohen_uhli", "ohen_popel_1"), ("ohen_popel_1", "ohen_pec"), ("ohen_popel_2", "ohen_pec"),
         ("ohen_popel_3", "ohen_pec")]
GRATES = [(74, 148, 102, 160), (60, 306, 116, 314)]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.zeros((AH, AW), bool)
BAL_Y = AH - 12
floor_px = np.zeros((AH, AW), bool); floor_px[WALL_H:BAL_Y, SIDE:AW - SIDE] = True

P.iron_floor(img, SIDE, WALL_H, AW - SIDE, AH, GRATES)

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

furn = place(P.furnace(), 92, 302, foot=(78, 26))
cart = place(P.file_cart(), 150, 136, foot=(34, 10))
place(P.coal_pile(1, w=40, h=22, shovel=True), 40, 228, foot=(36, 10))
place(P.coal_pile(4, w=36, h=20), 140, 216, foot=(32, 8))
for i, (x, by) in enumerate(((34, 300), (142, 300), (100, 218))):
    place(P.ash_heap(seed=i + 1), x, by, collide=False, shadow=False)

# ── žár: tma, oranžové světlo z pece a mříží ──────────────────────────────────
def ring_pool(mask, cx, cy, rx, ry, k=1.0):
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if 0 <= x < AW and 0 <= y < AH:
                d = (((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2) ** 0.5 + (0.04 if (x + y) % 2 else -0.04)
                v = 1.0 if d < 0.4 else 0.6 if d < 0.7 else 0.3 if d < 1 else 0
                mask[y, x] = max(mask[y, x], round(v * k, 1))
warm = np.zeros((AH, AW))
ring_pool(warm, 88, 290, 70, 46, 1.0)
for (gx0, gy0, gx1, gy1) in GRATES:
    ring_pool(warm, (gx0 + gx1) / 2, (gy0 + gy1) / 2, (gx1 - gx0) * 0.8, 14, 0.7)
ring_pool(warm, 88, WALL_H + 3, 22, 11, 0.5)
for (x, y) in ((34, 296), (142, 296), (100, 214)):
    ring_pool(warm, x, y, 16, 8, 0.4)
sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=14, k=0.8)
L.side_wall_shadow(sh, SIDE, AW - SIDE, depth=10, k=0.6, y0=WALL_H)
for o in placed:
    if not o["shadow"]:
        continue
    s = o["spr"]
    L.contact_shadow(sh, o["x"] + s.w / 2, o["y"] + s.h - 1, s.w / 2 + 2, 3, 1.0)
sh *= floor_px
L.darken(img, sh, 0.5, (8, 4, 4), dither=False, steps=4)

img[:WALL_H] = P.soot_wall(AW, WALL_H).a
block[:WALL_H + 3, :] = True

def wall_put(spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < AH and 0 <= x + xx < AW:
                img[y + yy, x + xx] = spr.a[yy, xx]

wall_put(P.steam_pipe(64), 19, 30)
wall_put(P.chute(46), 136, 44)
for yy in range(22, WALL_H):                                              # rám výtahu: začouzená litina
    for xx in range(62, 115):
        if xx < 68 or xx > 108 or yy < 30:
            img[yy, xx] = F.IRON_[3] if xx in (62, 63) or yy == 22 else F.IRON_[0] if xx in (113, 114) else F.IRON_[1]
elev = F.elevator3d(40, 58)
wall_put(elev, 88 - 20, WALL_H + 2 - 58)
wall_put(F.floor_dial3d(10, at=10), 88 - 12, 0)

for y in range(WALL_H - 6, AH):                                           # boční stěny: začouzené cihly
    for x in range(SIDE):
        for (X, inner) in ((x, x), (AW - 1 - x, x)):
            col = (P.BRICK[3] if inner < 3 else P.BRICK[2] if (y // 4 + inner // 4) % 2 else P.BRICK[1]).copy()
            if X > AW // 2:
                col[:3] = (col[:3] * 0.75).astype(np.uint8)
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

dark = np.clip(1.0 - warm, 0, 1); dark[:WALL_H] *= 0.8
L.darken(img, dark, 0.42, (14, 6, 6), dither=True, steps=10)
L.lighten(img, warm, 0.24, (255, 140, 60), steps=10)
vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.6, AW * 0.6, AH * 0.6, k=0.5)
L.darken(img, vg, 0.75, (6, 2, 2), dither=True, steps=10)

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
print("ok furnace", (furn["x"], furn["y"]), "cart", (cart["x"], cart["y"]))

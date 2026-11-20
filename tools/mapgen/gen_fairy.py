"""
Použití:  python tools/mapgen/gen_fairy.py
Výstup:   app/src/main/res/drawable-nodpi/tower_fairy.png, app/src/main/assets/walk/tower_fairy.txt,
          tools/mapgen/tower_fairy_debug.png

Duhová zahrada – 11. patro Věže Řádu Váhy (patro víl, typ VÍLA), 176 × 352 v pohledu 3/4 shora. Pohádka, až
příliš dokonalá: malované pastelové nebe s duhou, která vpravo dopadá do hrnce zlata, živý plot s růžemi,
trávník plný jetele (čtyřlístky v pravidelných rozestupech), pastelové nášlapné kameny, kruhy muchomůrek
(setkání), muchomůrkové domky víl, studna přání, skleněný zvon s obřím čtyřlístkem, sklenice s chycenými pixies.
Živé detaily (pixies, třpyt duhy, motýli, okvětní lístky, světlušky, záře sklenic): InteriorFxView.FAIRY.
NODES / EDGES jsou ZDROJ PRAVDY i pro TowerFairy.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import walkmask  # noqa: E402
import light as L, figures as F, archive_parts as A, fairy_parts as P  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 352
WALL_H = 96
SIDE = 10
NODES = {
    "vila_vytah": (88, 104), "vila_stred": (88, 166), "vila_zvon": (30, 152), "vila_hrnec": (138, 138),
    "vila_kruh_1": (44, 190), "vila_domek": (138, 210), "vila_sklenice": (38, 262), "vila_kruh_2": (130, 258),
    "vila_kruh_3": (46, 316), "vila_studna": (112, 322),
}
EDGES = [("vila_vytah", "vila_stred"), ("vila_vytah", "vila_hrnec"), ("vila_stred", "vila_zvon"),
         ("vila_stred", "vila_kruh_1"), ("vila_stred", "vila_domek"), ("vila_kruh_1", "vila_sklenice"),
         ("vila_domek", "vila_kruh_2"), ("vila_sklenice", "vila_kruh_3"), ("vila_kruh_2", "vila_studna"),
         ("vila_kruh_3", "vila_studna")]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.zeros((AH, AW), bool)
BAL_Y = AH - 12
floor_px = np.zeros((AH, AW), bool); floor_px[WALL_H:BAL_Y, SIDE:AW - SIDE] = True

P.fairy_lawn(img, SIDE, WALL_H, AW - SIDE, AH)
rng = np.random.default_rng(7)
path = [(88, 104), (84, 116), (80, 128), (82, 140), (88, 152), (92, 166), (90, 180), (84, 194), (80, 208), (82, 222),
        (88, 236), (94, 250), (96, 264), (92, 278), (88, 292), (90, 306), (98, 318)]
P.stone_path(img, path, rng)

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

rings = []
for i, (x, by) in enumerate(((44, 200), (130, 268), (46, 326))):
    spr, (rcx, rcy, rr) = P.fairy_ring(seed=i + 1, r=15)
    o = place(spr, x, by, collide=False, shadow=False)
    rings.append((o["x"] + rcx, o["y"] + rcy, rr))
place(P.clover_cloche(), 26, 142, pad=1)
pot = place(P.pot_of_gold(), 150, 126, pad=1)
house = place(P.fairy_house(0, True), 142, 200, pad=1)
place(P.fairy_house(3, False), 156, 236, pad=1)
jars = place(P.pixie_jars(), 38, 252, pad=1)
well = place(P.wishing_well(), 140, 330, pad=1)
place(P.rose_bush(1), 18, 214, foot=(14, 6))
place(P.rose_bush(2, col=[P.c(0x8A6A1E), P.c(0xD0A030), P.c(0xFFD860), P.c(0xFFF0A8)]), 116, 118, foot=(16, 6))
place(P.foxgloves(1), 160, 300, foot=(12, 6))
place(P.foxgloves(2), 18, 296, foot=(12, 6))
place(P.rose_bush(3, col=[P.c(0x6A4AA0), P.c(0x9A78D8), P.c(0xC4A8F4), P.c(0xE8DCFF)]), 72, 300, foot=(14, 6))

# ── světlo: jasný pohádkový den, měkké paprsky shora, záře hrnce a sklenic ─────
def ring_pool(mask, cx, cy, rx, ry, k=1.0):
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if 0 <= x < AW and 0 <= y < AH:
                d = (((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2) ** 0.5 + (0.04 if (x + y) % 2 else -0.04)
                v = 1.0 if d < 0.4 else 0.6 if d < 0.7 else 0.3 if d < 1 else 0
                mask[y, x] = max(mask[y, x], round(v * k, 1))
glow = np.zeros((AH, AW))
ring_pool(glow, pot["x"] + 16, pot["y"] + 14, 22, 12, 0.8)
ring_pool(glow, jars["x"] + 24, jars["y"] + 14, 26, 12, 0.7)
for (rcx, rcy, rr) in rings:
    ring_pool(glow, rcx, rcy, rr + 4, rr * 0.6 + 3, 0.5)
sun = np.zeros((AH, AW))
for x0 in (30, 96, 140):
    L.light_shaft(sun, (x0, WALL_H), (x0 + 16, WALL_H), 0.22, 230)
sun *= floor_px
sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=10, k=0.5)
L.side_wall_shadow(sh, SIDE, AW - SIDE, depth=8, k=0.4, y0=WALL_H)
for o in placed:
    if not o["shadow"]:
        continue
    s = o["spr"]
    L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], 0.45, 0.25, 0.6)
    L.contact_shadow(sh, o["x"] + s.w / 2, o["y"] + s.h - 1, s.w / 2 + 1, 2.4, 1.0)
sh *= floor_px
L.darken(img, sh, 0.35, (20, 40, 60), dither=False, steps=4)

img[:WALL_H] = P.sky_wall(AW, WALL_H, arch=(18, 158, 92, 72)).a
block[:WALL_H + 3, :] = True

def wall_put(spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < AH and 0 <= x + xx < AW:
                img[y + yy, x + xx] = spr.a[yy, xx]

for yy in range(22, WALL_H):                                              # rám výtahu: bílá treláž s růžemi
    for xx in range(62, 115):
        if xx < 68 or xx > 108 or yy < 30:
            col = P.STEM[3] if (xx + yy) % 4 in (0, 1) else P.STEM[1]
            if xx in (113, 114):
                col = P.STEM[0]
            if P._h(xx, yy, 3) % 11 == 0:
                col = P.ROSE[2] if P._h(xx, yy, 4) % 2 else P.LAWN[3]
            img[yy, xx] = col
wall_put(F.elevator3d(40, 58), 88 - 20, WALL_H + 2 - 58)
wall_put(F.floor_dial3d(10, at=11), 88 - 12, 2)

for y in range(WALL_H - 6, AH):                                           # boční stěny: živý plot
    for x in range(SIDE):
        for (X, inner) in ((x, x), (AW - 1 - x, x)):
            k = P._h(X // 2, y // 2, 6) % 4
            col = [P.LAWN[1], P.LAWN[2], P.LAWN[3], P.LAWN[2]][k].copy()
            if inner == SIDE - 1:
                col = P.LAWN[4].copy()
            if P._h(X, y, 7) % 37 == 0:
                col = P.ROSE[2].copy()
            if X > AW // 2:
                col[:3] = (col[:3] * 0.8).astype(np.uint8)
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

L.lighten(img, sun, 0.14, (255, 240, 200), steps=8)
L.lighten(img, glow, 0.18, (255, 230, 150), steps=8)
vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.55, AW * 0.65, AH * 0.65, k=0.4)
L.darken(img, vg, 0.5, (40, 30, 80), dither=True, steps=10)

Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_fairy.png"))
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "tower_fairy.txt"), "w") as f:
    f.write(f"# mapa chůze tower_fairy: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_fairy.py)\n{AW} {AH} {CELL}\n")
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
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "tower_fairy_debug.png"))
print("ok rings", [(round(a), round(b)) for a, b, _ in rings], "pot", (pot["x"], pot["y"]), "jars", (jars["x"], jars["y"]), "house", (house["x"], house["y"]), "well", (well["x"], well["y"]))

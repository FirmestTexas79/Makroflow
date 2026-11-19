"""
Použití:  python tools/mapgen/gen_office.py
Výstup:   app/src/main/res/drawable-nodpi/tower_office.png, app/src/main/assets/walk/tower_office.txt,
          tools/mapgen/tower_office_debug.png

Kancelář Řádu – 11. patro, vrchol Věže Řádu Váhy, observatoř, 176 × 352 v pohledu 3/4 shora. Vnitřek kopule
se zlatými souhvězdími a otevřenou štěrbinou na noční nebe (v hvězdách chybí jedna). Dalekohled míří do štěrbiny,
orloj, Foucaultovo kyvadlo nad mosazným kruhem s čísly okruhů 198–213, velké váhy (znak Řádu), mapa Makrosvěta
se stanicemi 0–7, stůl Řádu s Registrem nádob a prázdným křeslem. Ukazatel výtahu je za koncem stupnice.
Bez setkání (příběhové patro). Později ho spálí Drakirra (Objekt 019).
Živé detaily: InteriorFxView.OFFICE. NODES / EDGES jsou ZDROJ PRAVDY i pro TowerOffice.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import walkmask  # noqa: E402
import light as L, figures as F, archive_parts as A, office_parts as P  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 352
WALL_H = 96
SIDE = 10
RING = (88, 236, 30)
NODES = {
    "kanc_vytah": (88, 104), "kanc_stred": (88, 176), "kanc_mapa": (144, 106), "kanc_dalekohled": (52, 200),
    "kanc_orloj": (128, 200), "kanc_globus": (126, 262), "kanc_kyvadlo": (88, 270), "kanc_vahy": (34, 330), "kanc_stul": (84, 330),
}
EDGES = [("kanc_vytah", "kanc_stred"), ("kanc_vytah", "kanc_mapa"), ("kanc_stred", "kanc_dalekohled"),
         ("kanc_stred", "kanc_orloj"), ("kanc_stred", "kanc_kyvadlo"), ("kanc_kyvadlo", "kanc_vahy"),
         ("kanc_kyvadlo", "kanc_stul"), ("kanc_kyvadlo", "kanc_globus")]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.zeros((AH, AW), bool)
BAL_Y = AH - 12
floor_px = np.zeros((AH, AW), bool); floor_px[WALL_H:BAL_Y, SIDE:AW - SIDE] = True

P.marble_floor(img, SIDE, WALL_H, AW - SIDE, AH, ring=RING, carpet=(78, 99, WALL_H, RING[1] - 20))

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

tel = place(P.telescope(), 38, 186, foot=(36, 12))
orr = place(P.orrery_base(), 142, 186, foot=(28, 10))
scl = place(P.great_scales(), 34, 316, foot=(22, 10))
desk = place(P.order_desk(), 128, 330, foot=(62, 18))
place(A.globe(), 150, 268, foot=(18, 8))
block[RING[1] - 3:RING[1] + 3, RING[0] - 3:RING[0] + 3] = True                    # pod kyvadlem se nestojí

# ── světlo: noc, hvězdné světlo ze štěrbiny, lampa na stole, záře orloje ─────────
def ring_pool(mask, cx, cy, rx, ry, k=1.0):
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if 0 <= x < AW and 0 <= y < AH:
                d = (((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2) ** 0.5 + (0.04 if (x + y) % 2 else -0.04)
                v = 1.0 if d < 0.4 else 0.6 if d < 0.7 else 0.3 if d < 1 else 0
                mask[y, x] = max(mask[y, x], round(v * k, 1))
warm = np.zeros((AH, AW))
ring_pool(warm, desk["x"] + 53, desk["y"] + 20, 28, 16, 1.0)
ring_pool(warm, 88, WALL_H + 3, 22, 11, 0.5)
ring_pool(warm, orr["x"] + 20, orr["y"] + 18, 16, 10, 0.5)
star = np.zeros((AH, AW))
L.light_shaft(star, (16, WALL_H), (44, WALL_H), 0.3, 200)
ring_pool(star, RING[0], RING[1], RING[2] + 8, RING[2] * 0.6 + 6, 0.6)
star *= floor_px
sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=14, k=0.8)
L.side_wall_shadow(sh, SIDE, AW - SIDE, depth=10, k=0.6, y0=WALL_H)
for o in placed:
    s = o["spr"]
    L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], 0.45, 0.28, 0.7)
    L.contact_shadow(sh, o["x"] + s.w / 2, o["y"] + s.h - 1, s.w / 2 + 1, 2.4, 1.0)
sh *= floor_px
L.darken(img, sh, 0.5, (4, 6, 14), dither=False, steps=4)

img[:WALL_H] = P.dome_wall(AW, WALL_H, slit=(18, 42)).a
block[:WALL_H + 3, :] = True

def wall_put(spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < AH and 0 <= x + xx < AW:
                img[y + yy, x + xx] = spr.a[yy, xx]

wall_put(P.station_map(40, 36), 124, 16)
for yy in range(22, WALL_H):                                              # rám výtahu: eben a zlato
    for xx in range(62, 115):
        if xx < 68 or xx > 108 or yy < 30:
            col = P.GOLDL[2] if xx in (62, 63) or yy == 22 else P.EBONY[0] if xx in (113, 114) else P.EBONY[2]
            if yy == 26 or xx in (65, 111):
                col = P.GOLDL[1]
            img[yy, xx] = col
wall_put(F.elevator3d(40, 58), 88 - 20, WALL_H + 2 - 58)
wall_put(F.floor_dial3d(10, at=11), 88 - 12, 2)                          # ručička za koncem stupnice

for y in range(WALL_H - 6, AH):                                           # boční stěny: eben se zlatou lištou
    for x in range(SIDE):
        for (X, inner) in ((x, x), (AW - 1 - x, x)):
            col = (P.GOLDL[1] if inner == 2 else P.EBONY[3] if inner < 2 else P.EBONY[2] if (y // 24) % 2 else P.EBONY[1]).copy()
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

dark = np.clip(1.0 - np.maximum(warm, star * 0.7), 0, 1); dark[:WALL_H] *= 0.6
L.darken(img, dark, 0.5, (4, 6, 18), dither=True, steps=10)
L.lighten(img, warm, 0.2, (255, 210, 140), steps=10)
L.lighten(img, star, 0.16, (150, 170, 255), steps=8)
vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.55, AW * 0.6, AH * 0.6, k=0.5)
L.darken(img, vg, 0.8, (2, 3, 10), dither=True, steps=10)

Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_office.png"))
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "tower_office.txt"), "w") as f:
    f.write(f"# mapa chůze tower_office: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_office.py)\n{AW} {AH} {CELL}\n")
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
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "tower_office_debug.png"))
print("ok tel", (tel["x"], tel["y"]), "orr", (orr["x"], orr["y"]), "scl", (scl["x"], scl["y"]), "desk", (desk["x"], desk["y"]))

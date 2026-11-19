"""
Použití:  python tools/mapgen/gen_office.py
Výstup:   app/src/main/res/drawable-nodpi/tower_office.png, app/src/main/assets/walk/tower_office.txt,
          tools/mapgen/tower_office_debug.png

Kancelář Řádu – 11. patro, vrchol Věže Řádu Váhy (observatoř), 176 × 352 v pohledu 3/4 shora. Malovaná kopule se
zlatými hvězdami, velké klenuté okno s Měsícem a závěsy (v souhvězdí chybí hvězda), mapa Makrosvěta se stanicemi
0–7 a rudými nitěmi. Parketa z mahagonu ve vzoru rybí kosti. Pódium se zábradlím a velkým refraktorem, velký orloj
na lvích tlapách, glóbus, kartotéka, pult s hvězdným atlasem, vykládaný znak Řádu s kolíky Foucaultova kyvadla,
stůl Řádu s prázdným trůnem a Registrem nádob na koberci, monumentální váhy (popel je těžší než krystal).
Bez setkání (příběhové patro). Později ho spálí Drakirra (Objekt 019). Ukazatel výtahu je za koncem stupnice.
Živé detaily: InteriorFxView.OFFICE. NODES / EDGES jsou ZDROJ PRAVDY i pro TowerOffice.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import walkmask  # noqa: E402
import light as L, figures as F, archive_parts as A, office_parts as P1, office2 as P  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 352
WALL_H = 96
SIDE = 10
EMB = (88, 232, 28)
NODES = {
    "kanc_vytah": (88, 104), "kanc_stred": (88, 186), "kanc_mapa": (146, 106), "kanc_dalekohled": (46, 206),
    "kanc_orloj": (140, 190), "kanc_globus": (118, 214), "kanc_kyvadlo": (88, 262), "kanc_katalog": (30, 274),
    "kanc_pult": (148, 260), "kanc_stul": (60, 258), "kanc_vahy": (124, 262),
}
EDGES = [("kanc_vytah", "kanc_stred"), ("kanc_vytah", "kanc_mapa"), ("kanc_stred", "kanc_dalekohled"),
         ("kanc_stred", "kanc_orloj"), ("kanc_stred", "kanc_globus"), ("kanc_stred", "kanc_kyvadlo"),
         ("kanc_kyvadlo", "kanc_katalog"), ("kanc_kyvadlo", "kanc_stul"), ("kanc_kyvadlo", "kanc_vahy"),
         ("kanc_vahy", "kanc_pult")]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.zeros((AH, AW), bool)
BAL_Y = AH - 12
floor_px = np.zeros((AH, AW), bool); floor_px[WALL_H:BAL_Y, SIDE:AW - SIDE] = True

P.parquet_floor(img, SIDE, WALL_H, AW - SIDE, AH)
P.runner(img, 79, 98, WALL_H, EMB[1] - 18)                                 # běhoun od výtahu
P.ornate_rug(img, 14, 286, 112, 336)                                       # koberec pod stolem
P.floor_emblem(img, *EMB)
A.loose_papers(img, [(34, 252), (44, 258), (104, 300), (110, 292), (70, 276)], np.random.default_rng(4))

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

dais = place(P.telescope_dais(), 44, 198, foot=(62, 34))
orr = place(P.great_orrery(), 140, 180, foot=(30, 14))
glb = place(A.globe(), 118, 204, foot=(14, 8))
cat = place(A.card_catalog(), 26, 262, foot=(28, 12))
lect = place(A.lectern(), 150, 250, foot=(22, 10))
desk = place(P.grand_desk(), 60, 336, foot=(92, 30))
scl = place(P.monument_scales(), 136, 336, foot=(30, 16))
block[EMB[1] - 3:EMB[1] + 3, EMB[0] - 3:EMB[0] + 3] = True                       # pod kyvadlem se nestojí

# ── světlo: noc, Měsíc oknem, svíčky na stole, záře orloje ────────────────────
def ring_pool(mask, cx, cy, rx, ry, k=1.0):
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if 0 <= x < AW and 0 <= y < AH:
                d = (((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2) ** 0.5 + (0.04 if (x + y) % 2 else -0.04)
                v = 1.0 if d < 0.4 else 0.6 if d < 0.7 else 0.3 if d < 1 else 0
                mask[y, x] = max(mask[y, x], round(v * k, 1))
warm = np.zeros((AH, AW))
ring_pool(warm, desk["x"] + 10, desk["y"] + 24, 30, 20, 1.0)
ring_pool(warm, 88, WALL_H + 3, 22, 11, 0.5)
ring_pool(warm, orr["x"] + 28, orr["y"] + 26, 20, 12, 0.6)
ring_pool(warm, lect["x"] + 15, lect["y"] + 10, 16, 10, 0.5)
moon = np.zeros((AH, AW))
L.light_shaft(moon, (12, WALL_H), (44, WALL_H), 0.42, 190)
ring_pool(moon, EMB[0], EMB[1], EMB[2] + 6, EMB[2] * 0.6 + 4, 0.5)
moon *= floor_px
sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=14, k=0.8)
L.side_wall_shadow(sh, SIDE, AW - SIDE, depth=10, k=0.6, y0=WALL_H)
for o in placed:
    s = o["spr"]
    L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], 0.42, 0.26, 0.65)
    L.contact_shadow(sh, o["x"] + s.w / 2, o["y"] + s.h - 1, s.w / 2 + 1, 2.4, 1.0)
sh *= floor_px
L.darken(img, sh, 0.5, (6, 4, 10), dither=False, steps=4)

img[:WALL_H] = P.dome_wall2(AW, WALL_H).a
block[:WALL_H + 3, :] = True

def wall_put(spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < AH and 0 <= x + xx < AW:
                img[y + yy, x + xx] = spr.a[yy, xx]

wall_put(P.grand_window(30, 64), 6, 12)
wall_put(P1.station_map(38, 34), 126, 14)
for yy in range(22, WALL_H):                                              # rám výtahu: mahagon a zlato
    for xx in range(62, 115):
        if xx < 68 or xx > 108 or yy < 30:
            col = P.GOLD[3] if xx in (62, 63) or yy == 22 else P.WOODR[0] if xx in (113, 114) else P.WOODR[3]
            if yy in (25, 26) or xx in (65, 111):
                col = P.GOLD[2]
            img[yy, xx] = col
wall_put(F.elevator3d(40, 58), 88 - 20, WALL_H + 2 - 58)
wall_put(F.floor_dial3d(10, at=13), 88 - 12, 2)                          # ručička za koncem stupnice

for y in range(WALL_H - 6, AH):                                           # boční stěny: mahagon se zlatou lištou
    for x in range(SIDE):
        for (X, inner) in ((x, x), (AW - 1 - x, x)):
            col = (P.GOLD[2] if inner == 2 else P.WOODR[4] if inner < 2 else P.WOODR[3] if (y // 24) % 2 else P.WOODR[2]).copy()
            if (y % 24) == 0 and inner > 2:
                col = P.WOODR[1].copy()
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

dark = np.clip(1.0 - np.maximum(warm, moon * 0.75), 0, 1); dark[:WALL_H] *= 0.55
L.darken(img, dark, 0.5, (6, 6, 18), dither=True, steps=10)
L.lighten(img, warm, 0.22, (255, 200, 120), steps=10)
L.lighten(img, moon, 0.2, (160, 180, 255), steps=8)
vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.55, AW * 0.6, AH * 0.6, k=0.5)
L.darken(img, vg, 0.8, (3, 3, 10), dither=True, steps=10)

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
print("ok dais", (dais["x"], dais["y"]), "orr", (orr["x"], orr["y"]), "desk", (desk["x"], desk["y"]), "scl", (scl["x"], scl["y"]), "lect", (lect["x"], lect["y"]), "glb", (glb["x"], glb["y"]))

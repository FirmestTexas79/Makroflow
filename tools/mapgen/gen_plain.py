"""
Použití:  python tools/mapgen/gen_plain.py
Výstup:   app/src/main/res/drawable-nodpi/tower_plain.png, app/src/main/assets/walk/tower_plain.txt,
          tools/mapgen/tower_plain_debug.png

Kalibrační louka – 3. patro Věže Řádu Váhy (patro normálních Makromonů), 176 × 352 v pohledu 3/4 shora.
Umělá louka uvnitř věže pod velkou lampou místo slunce: namalované nebe na zadní stěně (se spárami a
odlupující se malbou), tráva rozdělená provázky a kolíky na měřicí čtverce, vysoké trávy (setkání),
strašák v úřednickém plášti, prázdná pozorovatelna, postřikovače, balík sena, boční živé ploty.
Živé detaily (tráva ve větru, motýli, postřikovače, zablikání „slunce“): InteriorFxView.PLAIN.
NODES / EDGES jsou ZDROJ PRAVDY i pro TowerPlain.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import walkmask  # noqa: E402
import tower_parts as P, light as L, figures as F, archive_parts as A, plain_parts as Q  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 352
WALL_H = 96
SIDE = 10
NODES = {
    "plan_vytah": (88, 104), "plan_stred": (88, 180), "plan_trava_1": (36, 206), "plan_posed": (146, 212),
    "plan_strasak": (42, 272), "plan_trava_2": (140, 278), "plan_trava_3": (38, 332), "plan_seno": (142, 334),
    "plan_kolik": (60, 160), "plan_malba": (150, 104), "plan_lavicka": (34, 138), "plan_stromek": (130, 140),
}
EDGES = [("plan_vytah", "plan_stred"), ("plan_vytah", "plan_malba"), ("plan_stred", "plan_kolik"),
         ("plan_stred", "plan_trava_1"), ("plan_stred", "plan_posed"), ("plan_stred", "plan_strasak"),
         ("plan_stred", "plan_trava_2"), ("plan_strasak", "plan_trava_3"), ("plan_trava_2", "plan_seno"), ("plan_stred", "plan_lavicka"), ("plan_stred", "plan_stromek")]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.zeros((AH, AW), bool)
BAL_Y = AH - 12
floor_px = np.zeros((AH, AW), bool); floor_px[WALL_H:BAL_Y, SIDE:AW - SIDE] = True

Q.meadow(img, 0, WALL_H, AW, AH)
# měřicí síť: provázky (světlá linka + stín pod ní)
STR = Q.c(0xE8E2CE); STR_SH = Q.GRASS[1]
for x in (52, 124):
    for y in range(WALL_H + 10, BAL_Y - 6):
        img[y, x] = STR; img[y, x + 1] = STR_SH
for y in (150, 232, 302):
    for x in range(SIDE + 2, AW - SIDE - 2):
        img[y, x] = STR; img[y + 1, x] = STR_SH

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

for i, y in enumerate(range(112, BAL_Y - 4, 11)):                        # pěšinka z šlapáků
    place(Q.stepping_stone(i), 88 + (2 if i % 2 else -2), y, collide=False, shadow=False, z=0)
for i, (x, y) in enumerate(((52, 150), (124, 150), (52, 232), (124, 232), (52, 302), (124, 302))):
    place(Q.stake(i), x + 1, y + 2, foot=(4, 3))
place(Q.observer_chair(), 146, 200, foot=(26, 10))
g1 = place(Q.tall_grass(44, 26, 1), 36, 198, collide=False)
place(Q.scarecrow(), 42, 262, foot=(8, 6))
g2 = place(Q.tall_grass(44, 26, 2), 140, 270, collide=False)
g3 = place(Q.tall_grass(44, 26, 3), 38, 324, collide=False)
place(F.bench3d(30, F.OAK), 34, 128, foot=(30, 6)); place(Q.potted_tree(), 146, 136, foot=(14, 8))
place(Q.hay_bale(), 142, 322, foot=(28, 12))
sp1 = place(Q.sprinkler(), 70, 172, foot=(4, 3)); sp2 = place(Q.sprinkler(), 108, 296, foot=(4, 3))
for (x, y, s) in ((112, 140, 4), (22, 250, 5), (160, 236, 6), (74, 318, 7), (100, 214, 8)):      # drobné trsy
    place(Q.tall_grass(14, 10, s), x, y, collide=False, shadow=False)

# ── světlo: lampa místo slunce, tvrdé stíny ─────────────────────────────────
sun = np.zeros((AH, AW)); L.contact_shadow(sun, 88, 210, 96, 130, 1.0); sun *= floor_px
L.lighten(img, sun, 0.16, (255, 240, 200), steps=10)
sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=12, k=0.8)
L.side_wall_shadow(sh, SIDE, AW - SIDE, depth=10, k=0.7, y0=WALL_H)
for o in placed:
    if not o["shadow"]:
        continue
    s = o["spr"]
    L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], 0.50, 0.28, 0.8)
    L.contact_shadow(sh, o["x"] + s.w / 2, o["y"] + s.h - 1, s.w / 2 + 1, 2.4, 1.0)
sh *= floor_px
L.darken(img, sh, 0.5, (14, 30, 18), dither=False, steps=4)

# ── zadní stěna: namalované nebe ────────────────────────────────────────────
img[:WALL_H] = Q.sky_mural(AW, WALL_H).a
block[:WALL_H + 3, :] = True

def wall_put(spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < AH and 0 <= x + xx < AW:
                img[y + yy, x + xx] = spr.a[yy, xx]

for yy in range(26, WALL_H):                                              # mramorový rám výtahu v „nebi“
    for xx in range(64, 113):
        if xx < 68 or xx > 108 or yy < 34:
            img[yy, xx] = P.MARB[4] if xx in (64, 65) or yy == 26 else P.MARB[1] if xx in (111, 112) else P.MARB[3]
elev = F.elevator3d(40, 58)
EX0, EY0 = 88 - 20, WALL_H + 2 - 58
wall_put(elev, EX0, EY0)
dial = F.floor_dial3d(11, at=3)
wall_put(dial, 88 - dial.w // 2, 6)

for y in range(WALL_H - 6, AH):                                           # boční živé ploty
    for x in range(SIDE):
        for (X, inner) in ((x, x), (AW - 1 - x, x)):
            left = X < AW // 2
            h = Q._h(X, y, 9)
            k = [1, 2, 3, 2, 1][h % 5] if inner < 7 else 0
            col = Q.GRASS[min(4, k + (1 if left else 0))]
            if inner == SIDE - 1:
                col = Q.GRASS[0]
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

vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.55, AW * 0.66, AH * 0.62, k=0.4)
L.darken(img, vg, 0.7, (10, 20, 14), dither=True, steps=10)
L.tone(img, (170, 200, 230), (230, 220, 170), k=0.06)

Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_plain.png"))
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "tower_plain.txt"), "w") as f:
    f.write(f"# mapa chůze tower_plain: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_plain.py)\n{AW} {AH} {CELL}\n")
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
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "tower_plain_debug.png"))
print("ok", AW, AH, "grass", [(g["x"], g["y"]) for g in (g1, g2, g3)], "sprinklers", [(s["x"], s["y"]) for s in (sp1, sp2)])

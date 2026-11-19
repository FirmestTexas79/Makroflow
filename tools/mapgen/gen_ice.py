"""
Použití:  python tools/mapgen/gen_ice.py
Výstup:   app/src/main/res/drawable-nodpi/tower_ice.png, app/src/main/assets/walk/tower_ice.txt,
          tools/mapgen/tower_ice_debug.png

Mrazírna – 9. patro Věže Řádu Váhy (ledové patro), 176 × 352 v pohledu 3/4 shora. Patro zamrzlo: zadní stěna pod
jinovatkou s rampouchy, rozbité okno (za ním polární záře, dovnitř sněží a navátý sníh leží pod ním), ledová podlaha
se závějemi u stěn. Hlavně hibernační komory: šest kapslí s „kalibrovanými“, kteří spí pod námrazou – spánek bez konce.
Zamrzlé kartotéky, blok ledu s uvězněným tvorem, teploměr. Závěje = setkání (Glacirra).
Živé detaily (sněžení, poryvy z okna, polární záře, kontrolky komor, třpyt ledu, zajíc): InteriorFxView.ICE.
NODES / EDGES jsou ZDROJ PRAVDY i pro TowerIce.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import walkmask  # noqa: E402
import light as L, figures as F, archive_parts as A, ice_parts as I  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 352
WALL_H = 96
SIDE = 10
NODES = {
    "led_vytah": (88, 104), "led_stred": (88, 214), "led_komory_l": (46, 206), "led_komory_p": (130, 206),
    "led_zavej_1": (34, 292), "led_zavej_2": (142, 280), "led_zavej_3": (88, 300), "led_okno": (36, 108),
    "led_teplomer": (146, 106), "led_blok": (136, 332), "led_skrine": (88, 266),
}
EDGES = [("led_vytah", "led_stred"), ("led_vytah", "led_okno"), ("led_vytah", "led_teplomer"),
         ("led_stred", "led_komory_l"), ("led_stred", "led_komory_p"), ("led_stred", "led_zavej_3"),
         ("led_zavej_3", "led_zavej_1"), ("led_zavej_3", "led_zavej_2"), ("led_zavej_3", "led_blok"),
         ("led_stred", "led_skrine")]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.zeros((AH, AW), bool)
BAL_Y = AH - 12
floor_px = np.zeros((AH, AW), bool); floor_px[WALL_H:BAL_Y, SIDE:AW - SIDE] = True

I.ice_floor(img, SIDE, WALL_H, AW - SIDE, AH)
I.drift_edges(img, SIDE, WALL_H, AW - SIDE, AH, side_w=9)

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

pods = []
for i, (x, by) in enumerate(((28, 182), (64, 182), (112, 182), (148, 182), (28, 256), (148, 256))):
    pods.append(place(I.cryo_pod(seed=i, occupied=(i != 3)), x, by, foot=(24, 14)))
place(I.snow_pile(34, 12), 30, WALL_H + 14, collide=False, shadow=False)
for i, (x, by) in enumerate(((34, 300), (142, 288), (88, 310))):
    place(I.snowdrift(seed=i + 2), x, by, collide=False, shadow=False)
place(I.frosted(F.filing_cabinet(1), seed=3), 76, 256, foot=(22, 10))
place(I.frosted(F.filing_cabinet(2), seed=5), 100, 256, foot=(22, 10))
blk = place(I.ice_block(), 136, 326, foot=(34, 14))

# ── chlad: modravá tma, studené světlo z okna, mdlé kontrolky komor ─────────
def ring_pool(mask, cx, cy, rx, ry, k=1.0):
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if 0 <= x < AW and 0 <= y < AH:
                d = (((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2) ** 0.5 + (0.04 if (x + y) % 2 else -0.04)
                v = 1.0 if d < 0.4 else 0.6 if d < 0.7 else 0.3 if d < 1 else 0
                mask[y, x] = max(mask[y, x], round(v * k, 1))
cold = np.zeros((AH, AW))
L.light_shaft(cold, (22, WALL_H), (46, WALL_H), 0.35, 170)
cold *= floor_px
glow = np.zeros((AH, AW))
for o in pods:
    ring_pool(glow, o["x"] + 14, o["y"] + 50, 18, 9, 0.8)
ring_pool(glow, 88, WALL_H + 3, 22, 11, 0.6)
ring_pool(glow, blk["x"] + 18, blk["y"] + 30, 22, 12, 0.5)
sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=12, k=0.7)
L.side_wall_shadow(sh, SIDE, AW - SIDE, depth=8, k=0.5, y0=WALL_H)
for o in placed:
    if not o["shadow"]:
        continue
    s = o["spr"]
    L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], -0.45, 0.28, 0.7)
    L.contact_shadow(sh, o["x"] + s.w / 2, o["y"] + s.h - 1, s.w / 2 + 1, 2.4, 1.0)
sh *= floor_px
L.darken(img, sh, 0.45, (10, 18, 40), dither=False, steps=4)

img[:WALL_H] = I.frost_wall(AW, WALL_H).a
block[:WALL_H + 3, :] = True

def wall_put(spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < AH and 0 <= x + xx < AW:
                img[y + yy, x + xx] = spr.a[yy, xx]

wall_put(I.broken_window(24, 52), 24, 14)
for yy in range(22, WALL_H):                                              # rám výtahu: ojíněný kov
    for xx in range(62, 115):
        if xx < 68 or xx > 108 or yy < 30:
            col = I.METAL[3] if xx in (62, 63) or yy == 22 else I.METAL[0] if xx in (113, 114) else I.METAL[2]
            if yy < 25 or ((xx * 7 + yy * 13) % 29 == 0 and yy < 40):
                col = I.SNOW[3]
            img[yy, xx] = col
elev = F.elevator3d(40, 58)
wall_put(elev, 88 - 20, WALL_H + 2 - 58)
wall_put(F.floor_dial3d(10, at=9), 88 - 12, 0)
wall_put(I.thermometer(), 141, 30)

for y in range(WALL_H - 6, AH):                                           # boční stěny: ojíněné kachle
    for x in range(SIDE):
        for (X, inner) in ((x, x), (AW - 1 - x, x)):
            col = (I.SNOW[3] if inner < 3 else I.FROSTT[2] if (y // 12) % 2 else I.FROSTT[1]).copy()
            if X > AW // 2:
                col[:3] = (col[:3] * 0.78).astype(np.uint8)
            img[y, X] = col
            block[y, X] = True

for o in sorted(placed, key=lambda o: o["z"]):
    s = o["spr"]
    for yy in range(s.h):
        for xx in range(s.w):
            if s.a[yy, xx, 3] and 0 <= o["y"] + yy < AH and 0 <= o["x"] + xx < AW:
                img[o["y"] + yy, o["x"] + xx] = s.a[yy, xx]

wall_put(I.frosted(A.balustrade(AW - 2 * SIDE, AH - BAL_Y), seed=7, k=0.2), SIDE, BAL_Y)
block[BAL_Y - 2:, :] = True

dark = np.clip(1.0 - np.maximum(glow, cold * 0.8), 0, 1); dark[:WALL_H] *= 0.6
L.darken(img, dark, 0.32, (8, 14, 40), dither=True, steps=10)
L.lighten(img, glow, 0.16, (120, 230, 200), steps=8)
L.lighten(img, cold, 0.20, (170, 210, 255), steps=8)
vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.55, AW * 0.6, AH * 0.6, k=0.5)
L.darken(img, vg, 0.7, (4, 8, 24), dither=True, steps=10)

Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_ice.png"))
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "tower_ice.txt"), "w") as f:
    f.write(f"# mapa chůze tower_ice: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_ice.py)\n{AW} {AH} {CELL}\n")
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
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "tower_ice_debug.png"))
print("ok pods", [(o["x"], o["y"]) for o in pods], "blk", (blk["x"], blk["y"]))

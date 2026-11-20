"""
Použití:  python tools/mapgen/gen_office_burned.py   (po gen_office.py)
Výstup:   app/src/main/res/drawable-nodpi/tower_office_burned.png

Kancelář Řádu po Objektu 019 (docs/adr/0104): Drakirra se v noci vyrvala z řetězů a horní patro Věže vyhořelo.
Stejné rozložení jako tower_office.png (mapa chůze se nemění) – jen oheň: zuhelnatělá parketa, saze táhnoucí se
od okna (kudy vlétl plamen), propadlá kopule s nocí a hvězdami v dírách, popel, doutnající uhlíky.
"""
import math, os, sys
import numpy as np
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "tower"))
from hideout_parts import _vn, _h  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
SRC = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_office.png")
img = np.array(Image.open(SRC).convert("RGB")).astype(float)
H, W = img.shape[:2]
WALL_H = 96
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0

# 1) celkově: vybledlé, zakouřené, teplé hnědé tónování
gray = img.mean(axis=2, keepdims=True)
img = img * 0.6 + gray * 0.4
img = img * np.array([0.8, 0.68, 0.6])

for y in range(H):
    for x in range(W):
        # 2) saze: pás od okna vlevo nahoře (kudy vlétl plamen) přes místnost
        d_streak = abs((y - 30) * 0.55 - (x - 20) * 0.9) / 34
        n = _vn(x, y, 3, 9) * 0.65 + _vn(x, y, 5, 3) * 0.35
        soot = max(0.0, 1 - d_streak) * 0.75 + (n - 0.6) * 0.6
        if soot > BAYER[y % 4, x % 4] * 0.8 + 0.2:
            k = min(1.0, soot)
            img[y, x] = img[y, x] * (1 - 0.75 * k) + np.array([14, 11, 10]) * 0.75 * k
        # 3) zuhelnatělá parketa: tmavé ostrůvky s popraskanou žhavou spárou
        if y > WALL_H + 2 and _vn(x, y, 11, 16) > 0.74:
            img[y, x] = np.array([18, 14, 12]) if (x + y) % 5 else np.array([30, 24, 20])
            if _h(x, y, 13) % 97 == 0:
                img[y, x] = np.array([210, 80, 30])                          # uhlík
        # 4) popel
        if _h(x, y, 17) % 71 == 0:
            img[y, x] = img[y, x] * 0.4 + np.array([150, 144, 136]) * 0.6

# 5) propadlá kopule: díry s nocí a hvězdami, ohořelé okraje
for (hx, hy, r) in ((58, 20, 13), (122, 34, 9), (96, 10, 7)):
    for y in range(int(hy - r) - 3, int(hy + r) + 4):
        for x in range(int(hx - r * 1.4) - 3, int(hx + r * 1.4) + 4):
            if not (0 <= x < W and 0 <= y < WALL_H):
                continue
            d = math.hypot((x - hx) / 1.4, y - hy) / r + (_vn(x, y, 21, 3) - 0.5) * 0.5
            if d < 1:
                img[y, x] = np.array([8, 12, 30]) if _h(x, y, 2) % 37 else np.array([230, 232, 255])
            elif d < 1.35:
                img[y, x] = np.array([24, 16, 12]) if d > 1.15 else np.array([150, 60, 24])   # ohořelý okraj, žhne

# 6) doutnající záře u paty stěn a pod trůnem
glow_pts = [(30, 300, 26), (140, 140, 18), (60, 330, 22), (150, 300, 16)]
for (gx, gy, r) in glow_pts:
    for y in range(gy - r, gy + r):
        for x in range(gx - r, gx + r):
            if 0 <= x < W and 0 <= y < H:
                d = math.hypot(x - gx, (y - gy) * 1.3) / r
                if d < 1:
                    a = round((1 - d) * 3) / 3 * 0.32
                    img[y, x] = img[y, x] * (1 - a) + np.array([255, 110, 40]) * a

Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_office_burned.png"))
print("ok")

"""Portrét do dialogu úkolu „Kořeny, které pijí“ (story/SlopeWater): vodočetná lať Řádu v tůňce mezi kořeny.
36 × 34 px pixel art, ×8 → app/src/main/res/drawable-nodpi/npc_lat.png (stejně jako npc_paska)."""
import os
import numpy as np
from PIL import Image

W, H, S = 36, 34, 8
img = np.zeros((H, W, 4), np.uint8)
rng = np.random.default_rng(3)
rock = np.array([46, 44, 50]); rock2 = np.array([58, 55, 60])
for y in range(H):                                   # skála pozadí
    for x in range(W):
        c = rock2 if (x * 7 + y * 3 + rng.integers(0, 3)) % 9 == 0 else rock
        img[y, x, :3] = c; img[y, x, 3] = 255
for (x0, sl) in ((3, 0.35), (30, -0.3), (12, 0.15)):  # kořeny shora
    for y in range(0, 22):
        x = int(x0 + y * sl)
        for dx in (0, 1):
            if 0 <= x + dx < W:
                img[y, x + dx, :3] = (196, 184, 160) if dx == 0 else (150, 138, 116)
water_y = 24
img[water_y:, :, :3] = (34, 70, 96)                   # tůňka
img[water_y, :, :3] = (110, 160, 190)
for x in range(2, W - 2, 5):
    img[water_y + 3 + (x % 3), x:x + 2, :3] = (60, 100, 130)
img[water_y - 3, 2:34, :3] = (84, 110, 120)          # mokrá čára o dlaň výš
for y in range(3, H - 2):                             # lať: bílé a rudé dílky
    for x in (16, 17, 18, 19):
        band = (y // 3) % 2
        img[y, x, :3] = (232, 228, 214) if band == 0 else (190, 52, 46)
    img[y, 15, :3] = (30, 28, 30); img[y, 20, :3] = (30, 28, 30)
    if y % 3 == 0:
        img[y, 19, :3] = (30, 28, 30)
img[2, 15:21, :3] = (30, 28, 30)
img[water_y + 1:, 15:21, :3] = img[water_y + 1:, 15:21, :3] // 2 + np.array([17, 35, 48])  # pod vodou
for x, y in ((22, 12), (23, 14), (22, 16)):           # tužka na lati
    img[y, x - 4, :3] = (60, 60, 64)
img[30, 26:29, :3] = (200, 200, 190); img[31, 27, :3] = (230, 230, 220)  # odlesk kovu v kalu
out = Image.fromarray(img, "RGBA").resize((W * S, H * S), Image.NEAREST)
dst = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res", "drawable-nodpi", "npc_lat.png")
out.save(dst)
print("ok", os.path.abspath(dst))

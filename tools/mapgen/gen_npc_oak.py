"""Portrét Starého dubu pro dialogy (docs/adr/0045).

Prastarý dub s tváří v kůře: jantarové oči v sucích, těžké obočí z kůry, mechový vous
a prasklina na hrudi, ve které září Srdce Hvozdu. Pixel art 64×64, uložený zvětšený 8×
bez vyhlazení (dialog ho zmenšuje na 110 dp).
Výstup: app/src/main/res/drawable-nodpi/npc_stary_dub.png
"""
import math, os
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable-nodpi", "npc_stary_dub.png")
N = 64
rng = np.random.default_rng(7)
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0 - 0.5


def rgb(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], dtype=float)


def mix(a, b, t):
    return a + (b - a) * max(0.0, min(1.0, t))


img = np.zeros((N, N, 4))


def put(x, y, c, a=255):
    if 0 <= x < N and 0 <= y < N:
        img[y, x, :3] = c
        img[y, x, 3] = a


# ── koruna (za kmenem): shluky listů ────────────────────────────────────────
leaf_d, leaf_m, leaf_l, leaf_hi = rgb(0x1F4A22), rgb(0x2F6E30), rgb(0x4E9A3E), rgb(0x8CCB5A)
blobs = [(12, 12, 11), (26, 7, 11), (40, 7, 11), (53, 13, 10), (6, 24, 8), (58, 25, 7), (20, 18, 10), (44, 18, 10), (32, 14, 12)]
for y in range(N):
    for x in range(N):
        hit = None
        for (bx, by, r) in blobs:
            d = math.hypot(x - bx, (y - by) * 1.15)
            if d <= r and (hit is None or by > hit[1] or (by == hit[1] and d / r < hit[3])):
                hit = (bx, by, r, d / r)                      # nižší shluk leží před vyšším
        if hit is None:
            continue
        bx, by, r, t = hit
        # každý shluk má vlastní světlo zleva shora → koruna se čte jako chomáče listí
        lx, ly = (x - bx) / r, (y - by) / r
        light = 0.62 - (lx * 0.35 + ly * 0.55) + BAYER[y % 4, x % 4] * 0.35
        c = mix(leaf_d, leaf_m, light)
        if light > 0.7:
            c = mix(leaf_m, leaf_l, (light - 0.7) * 2.5)
        if light > 0.95 and (x * 7 + y * 3) % 5 < 2:
            c = leaf_hi
        if t > 0.9:
            c = leaf_d
        put(x, y, c)

# ── kmen ────────────────────────────────────────────────────────────────────
bark_d, bark_m, bark_l, bark_hi = rgb(0x2A1A10), rgb(0x4A3020), rgb(0x6E4A30), rgb(0x94704C)


def trunk_half(y):
    """Poloviční šířka kmene v řádku y (rozšiřuje se ke kořenům)."""
    base = 17 + (y - 20) * 0.08
    if y > 50:
        base += (y - 50) ** 1.6 * 0.45
    return base


for y in range(18, N):
    hw = trunk_half(y)
    for x in range(N):
        dx = x - 32 + math.sin(y * 0.21) * 1.2
        if abs(dx) > hw:
            continue
        u = dx / hw                                    # -1..1 přes kmen
        shade = 0.55 - u * 0.35                        # světlo zleva
        groove = math.sin(dx * 0.9 + math.sin(y * 0.17) * 2.2)   # svislé rýhy kůry
        c = mix(bark_d, bark_m, shade + groove * 0.18 + BAYER[y % 4, x % 4] * 0.25)
        if groove > 0.75 and shade > 0.45:
            c = mix(c, bark_l, 0.7)
        if groove < -0.8:
            c = bark_d
        if abs(u) > 0.93:
            c = rgb(0x1A100A)
        put(x, y, c)

# kořeny do stran dole
for side in (-1, 1):
    for k in range(14):
        y = 56 + k // 3
        for w in range(3):
            x = 32 + side * (22 + k) + (w * side)
            put(x, min(N - 1, y + w // 2), mix(bark_d, bark_m, 0.4 - k * 0.02))

# ── tvář ────────────────────────────────────────────────────────────────────
# obočí z těžké kůry
for side in (-1, 1):
    for i in range(11):
        x = 32 + side * (3 + i)
        y = 26 + int(abs(i - 3) * 0.4) - (1 if i < 3 else 0)
        for t in range(4):
            put(x, y + t, bark_hi if t == 0 else (bark_l if t == 1 else (bark_m if t == 2 else rgb(0x140A06))))
# oči: suky s jantarovou září
amber_d, amber, amber_l, glow = rgb(0x7A3A08), rgb(0xE08A18), rgb(0xFFC850), rgb(0xFFF4B0)
for side in (-1, 1):
    ex, ey = 32 + side * 8, 33
    for y in range(ey - 4, ey + 5):
        for x in range(ex - 5, ex + 6):
            d = math.hypot((x - ex) / 1.25, y - ey)
            if d <= 4.2:
                if d > 3.3:
                    put(x, y, rgb(0x140A06))              # tmavý okraj suku
                elif d > 2.2:
                    put(x, y, amber_d)
                elif d > 1.1:
                    put(x, y, amber)
                else:
                    put(x, y, amber_l)
    put(ex - 1 * side, ey - 1, glow)                      # odlesk
# nos – vystouplý kus kůry
for y in range(35, 42):
    for x in range(30, 35):
        c = bark_l if x < 32 else bark_m
        if y == 41 or x == 34:
            c = bark_d
        put(x, y, c)
put(31, 36, bark_hi); put(31, 37, bark_hi)
# ústa – zvlněná prasklina
for x in range(24, 41):
    y = 45 + int(round(math.sin((x - 24) * 0.45) * 0.8))
    put(x, y, rgb(0x100804)); put(x, y + 1, bark_d)
# mechový vous
moss_d, moss, moss_l = rgb(0x3A5A1E), rgb(0x5E8A2A), rgb(0x9CC24A)
for x in range(22, 43):
    length = 6 + int(3 * math.sin((x - 22) * 0.5) + 2 * math.sin(x * 1.7))
    for k in range(length):
        y = 47 + k
        t = k / max(1, length)
        c = mix(moss_l, moss_d, t + BAYER[y % 4, x % 4] * 0.4)
        if (x + k) % 4 == 0:
            c = mix(c, moss, 0.5)
        put(x, y, c)
# prasklina na hrudi se Srdcem Hvozdu (pod vousem vykukuje záře)
for y in range(52, 62):
    for x in range(26, 39):
        d = math.hypot((x - 32) / 1.1, (y - 57) / 1.5)
        if d < 4.6:
            put(x, y, rgb(0x120904))
        if d < 3.2:
            put(x, y, mix(amber, glow, 1 - d / 3.2))
# mech a lišejník na kmeni
for (mx, my) in [(18, 40), (46, 38), (20, 52), (45, 55), (16, 30)]:
    for i in range(9):
        x = mx + int(rng.integers(-3, 4)); y = my + int(rng.integers(-2, 3))
        if img[y, x, 3] > 0:
            put(x, y, moss if i % 3 else moss_l)
# žaludy v koruně
for (ax, ay) in [(15, 20), (48, 22), (27, 24)]:
    put(ax, ay, rgb(0x6A4420)); put(ax, ay + 1, rgb(0xB88A40)); put(ax + 1, ay + 1, rgb(0x9A7030))

# ── obrys: tmavý lem kolem celé postavy ─────────────────────────────────────
alpha = img[:, :, 3] > 0
edge = np.zeros_like(alpha)
for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
    edge |= np.roll(np.roll(alpha, dy, 0), dx, 1) & ~alpha
out = img.copy()
out[edge, :3] = rgb(0x0E0A06)
out[edge, 3] = 255

im = Image.fromarray(out.clip(0, 255).astype(np.uint8), "RGBA").resize((N * 8, N * 8), Image.NEAREST)
im.save(OUT)
print("ok", OUT)

"""Obrázky k Zapomenutému háji (docs/adr/0046).

* npc_elderan.png – duch Elderana, posledního Strážce brány: stařec z rodu Mycitů
  s obrovským svítícím parožím, vousem z mechu a světla, průsvitný a chladně tyrkysový.
* mural_1..3.png – obrazy vytesané do kamenů kruhu (svítící rýhy v kameni):
  1) Draci stavějí Bránu světů, 2) druidi zapečeťují bránu Srdcem a drak usíná na hoře,
  3) Pán popela za branou a strážce, který se vpíjí do kořenů dubu.
Výstup: app/src/main/res/drawable-nodpi/
"""
import math, os
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable-nodpi")
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0 - 0.5


def rgb(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], dtype=float)


def mix(a, b, t):
    return a + (b - a) * max(0.0, min(1.0, t))


def hash01(k, s=0):
    v = (k * 2654435761 + s * 97531) & 0xFFFFFFFF
    v = ((v ^ (v >> 15)) * 2246822519) & 0xFFFFFFFF
    return ((v ^ (v >> 13)) & 0xFFFF) / 65535.0


# ═════════════════════════════════════════════════════════════════════════════
# ELDERAN – portrét 64×64
# ═════════════════════════════════════════════════════════════════════════════
N = 64
img = np.zeros((N, N, 4))


def put(x, y, c, a=255):
    if 0 <= x < N and 0 <= y < N:
        img[y, x, :3] = c
        img[y, x, 3] = max(img[y, x, 3], a)


g_d, g_m, g_l, g_w = rgb(0x1E5A66), rgb(0x3E9AA6), rgb(0x8EE0E0), rgb(0xE8FFFF)
antler = rgb(0x9FF6E8)

# paroží – rozvětvené čáry nahoru do stran
def branch(x, y, ang, length, depth):
    for k in range(int(length)):
        px = x + math.cos(ang) * k; py = y + math.sin(ang) * k
        put(int(px), int(py), antler, 235)
        put(int(px) + 1, int(py), mix(antler, g_m, 0.5), 170)
    if depth > 0:
        ex, ey = x + math.cos(ang) * length, y + math.sin(ang) * length
        branch(ex, ey, ang - 0.55, length * 0.62, depth - 1)
        branch(ex, ey, ang + 0.45, length * 0.55, depth - 1)


for side in (-1, 1):
    base = -math.pi / 2 - side * 0.62
    branch(32 + side * 6, 18, base, 13, 3)

# kápě a roucho (trojúhelník rozšiřující se dolů), průsvitné, dole mizí
for y in range(30, N):
    hw = 11 + (y - 30) * 0.45
    for x in range(N):
        u = (x - 32) / hw
        if abs(u) > 1:
            continue
        t = (y - 30) / (N - 30)
        light = 0.55 - u * 0.35 + BAYER[y % 4, x % 4] * 0.25
        c = mix(g_d, g_m, light)
        fold = math.sin((x - 32) * 0.7 + y * 0.05)
        if fold > 0.85:
            c = mix(c, g_l, 0.35)
        if abs(u) > 0.9:
            c = mix(g_d, rgb(0x0A2228), 0.5)
        alpha = int(235 * (1 - max(0, t - 0.55) * 1.9))          # roucho se rozplývá
        if alpha > 20 and hash01(x * 17 + y * 5, 3) > max(0, t - 0.7) * 1.8:
            put(x, y, c, alpha)

# kápě kolem hlavy (za hlavou, tmavší)
for y in range(16, 40):
    for x in range(18, 47):
        if math.hypot((x - 32) / 14, (y - 30) / 14) < 1:
            put(x, y, mix(g_d, g_m, 0.35 - (x - 32) * 0.02 + BAYER[y % 4, x % 4] * 0.2), 230)
# hlava myšího starce: kulatá, velké uši
def disc(cx, cy, r, fn):
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            d = math.hypot(x - cx, y - cy)
            if d <= r:
                fn(x, y, d / r)


for side in (-1, 1):
    disc(32 + side * 11, 19, 6, lambda x, y, t: put(x, y, mix(g_l, g_m, t) if t < 0.85 else g_d, 225))
    disc(32 + side * 11, 19, 3.5, lambda x, y, t: put(x, y, mix(rgb(0xB8F0F0), g_l, t), 200))
disc(32, 27, 9.5, lambda x, y, t: put(x, y, mix(g_w, g_l, t * 1.1) if t < 0.88 else g_d, 240))
# kápě přes temeno
for y in range(17, 25):
    for x in range(22, 43):
        if math.hypot((x - 32) / 10.5, (y - 25) / 8) < 1 and y < 22:
            put(x, y, mix(g_d, g_m, 0.5 - (x - 32) * 0.03), 245)
# oči – bílomodrá záře, přivřené (moudrý stařec)
for side in (-1, 1):
    ex = 32 + side * 4
    for dx in (-2, -1, 0, 1, 2):
        put(ex + dx, 27, rgb(0x2A6A76), 255); put(ex + dx, 28, rgb(0x5AAAB4), 255)
    for dx in (-1, 0, 1):
        put(ex + dx, 27, rgb(0xFFFFFF), 255)
    put(ex, 26, rgb(0x7AF0FF), 255)
    put(ex - side * 2, 25, g_d, 255)      # obočí
    put(ex - side, 25, g_d, 255)
# čenich a vousy
put(32, 30, rgb(0x2A6A76), 255); put(31, 31, g_m, 255); put(33, 31, g_m, 255)
for side in (-1, 1):
    for k in range(7):
        put(32 + side * (3 + k), 31 + (k // 3), rgb(0xD8FFFF), 150)
# vous z mechu a světla
for x in range(25, 40):
    ln = 10 + int(5 * math.sin((x - 25) * 0.4)) + int(2 * math.sin(x * 1.9))
    for k in range(ln):
        t = k / ln
        c = mix(g_w, rgb(0x5ADCA8), t + BAYER[(33 + k) % 4, x % 4] * 0.3)
        put(x, 33 + k, c, int(240 * (1 - t * 0.7)))
# hůl s krystalem (vpravo)
for y in range(26, N):
    put(49, y, rgb(0x5A8A8A), int(220 * (1 - (y - 26) / 45)))
for (dx, dy) in [(0, -2), (-1, -1), (0, -1), (1, -1), (-1, 0), (0, 0), (1, 0), (0, 1)]:
    put(49 + dx, 25 + dy, rgb(0xA8FFF0), 255)
# jemná záře kolem celé postavy
alpha = img[:, :, 3].copy()
halo = np.zeros_like(alpha)
for dy in range(-3, 4):
    for dx in range(-3, 4):
        if dx * dx + dy * dy <= 9:
            halo = np.maximum(halo, np.roll(np.roll(alpha, dy, 0), dx, 1) * (1 - math.hypot(dx, dy) / 4.2))
glow_mask = (alpha == 0) & (halo > 0)
img[glow_mask, :3] = rgb(0x7AF0E8)
img[glow_mask, 3] = halo[glow_mask] * 0.35
Image.fromarray(img.clip(0, 255).astype(np.uint8), "RGBA").resize((N * 8, N * 8), Image.NEAREST).save(
    os.path.join(RES, "npc_elderan.png"))

# ═════════════════════════════════════════════════════════════════════════════
# VYTESANÉ OBRAZY – 72×48, kámen se svítícími rýhami
# ═════════════════════════════════════════════════════════════════════════════
MW, MH = 72, 48
rune = rgb(0x6AF0E0)
ember = rgb(0xFF8A3A)


def stone_canvas(seed):
    m = np.zeros((MH, MW, 3))
    for y in range(MH):
        for x in range(MW):
            n = hash01(x // 3 * 91 + y // 3 * 17, seed) * 0.5 + hash01(x * 7 + y * 13, seed + 1) * 0.5
            c = mix(rgb(0x2E3C48), rgb(0x566A7C), 0.35 + n * 0.4 + BAYER[y % 4, x % 4] * 0.2)
            if x in (0, MW - 1) or y in (0, MH - 1):
                c = rgb(0x121A22)
            elif x in (1, MW - 2) or y in (1, MH - 2):
                c = rgb(0x6E8496)
            m[y, x] = c
    # mech v rozích
    for (cx, cy) in ((2, 2), (MW - 3, MH - 3), (MW - 4, 3)):
        for y in range(MH):
            for x in range(MW):
                if math.hypot(x - cx, y - cy) < 5 and hash01(x * 5 + y, seed + 3) < 0.6:
                    m[y, x] = mix(rgb(0x1A4436), rgb(0x3E7A56), hash01(x + y * 3, seed))
    return m


class Carver:
    def __init__(self, m):
        self.m = m

    def dot(self, x, y, c=rune):
        x, y = int(round(x)), int(round(y))
        if 2 <= x < MW - 2 and 2 <= y < MH - 2:
            self.m[y, x] = c
            # tmavá rýha pod světlem (hloubka)
            if y + 1 < MH - 2:
                self.m[y + 1, x] = mix(self.m[y + 1, x], rgb(0x101820), 0.6)

    def line(self, x0, y0, x1, y1, c=rune):
        n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for k in range(n + 1):
            t = k / max(1, n)
            self.dot(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, c)

    def ring(self, cx, cy, r, c=rune, a0=0.0, a1=2 * math.pi):
        n = int(r * 7)
        for k in range(n + 1):
            a = a0 + (a1 - a0) * k / n
            self.dot(cx + math.cos(a) * r, cy + math.sin(a) * r, c)

    def dragon(self, cx, cy, s=1.0, c=rune, asleep=False):
        if asleep:
            # stočený drak (spirála) s hlavou na ocase
            for k in range(60):
                a = k * 0.19; r = 2 + k * 0.13 * s
                self.dot(cx + math.cos(a) * r, cy + math.sin(a) * r * 0.6, c)
            self.line(cx + 8 * s, cy - 1, cx + 11 * s, cy - 3, c)
            return
        # letící drak: tělo, křídla, ocas
        self.line(cx - 8 * s, cy, cx + 6 * s, cy - 1, c)
        self.line(cx + 6 * s, cy - 1, cx + 9 * s, cy - 3, c)          # krk a hlava
        self.dot(cx + 10 * s, cy - 3, c)
        self.line(cx - 1 * s, cy, cx - 5 * s, cy - 7 * s, c)          # křídlo
        self.line(cx - 5 * s, cy - 7 * s, cx + 3 * s, cy - 2, c)
        self.line(cx + 1 * s, cy, cx - 2 * s, cy - 9 * s, c)
        self.line(cx - 2 * s, cy - 9 * s, cx + 5 * s, cy - 2, c)
        self.line(cx - 8 * s, cy, cx - 12 * s, cy + 3, c)             # ocas

    def druid(self, cx, by, c=rune):
        self.line(cx, by, cx, by - 7, c)                              # tělo
        self.line(cx - 2, by, cx + 2, by, c)
        self.dot(cx, by - 8, c)                                       # hlava
        self.line(cx - 1, by - 9, cx - 3, by - 12, c)                 # paroží
        self.line(cx + 1, by - 9, cx + 3, by - 12, c)
        self.dot(cx - 4, by - 11, c); self.dot(cx + 4, by - 11, c)

    def seed(self, cx, cy, c=rgb(0xFFC850)):
        for (dx, dy) in [(0, -2), (-1, -1), (0, -1), (1, -1), (-1, 0), (0, 0), (1, 0), (0, 1)]:
            self.dot(cx + dx, cy + dy, c)


def save(m, name):
    Image.fromarray(m.clip(0, 255).astype(np.uint8), "RGB").resize((MW * 6, MH * 6), Image.NEAREST).save(
        os.path.join(RES, name))


# 1) Draci staví Bránu světů: dva světy (vlevo stromy, vpravo hory s ohněm), mezi nimi prstenec, nad ním drak
m = stone_canvas(11); cv = Carver(m)
cv.ring(36, 28, 9)
cv.ring(36, 28, 6, rgb(0x9AD8E8))
for x in (10, 16, 22):                                                # stromy Hvozdu
    cv.line(x, 40, x, 32); cv.line(x - 3, 34, x, 28); cv.line(x + 3, 34, x, 28)
cv.line(52, 40, 58, 30); cv.line(58, 30, 64, 40)                       # hora za branou
cv.line(56, 29, 57, 25, ember); cv.line(59, 29, 58, 24, ember)         # oheň
cv.line(4, 41, 68, 41)                                                 # zem
cv.dragon(34, 12, 1.1)
save(m, "mural_1.png")

# 2) Pečeť: druidi drží Srdce nad branou, přes bránu kříž, drak spí na hoře se dvěma krystaly
m = stone_canvas(23); cv = Carver(m)
cv.ring(22, 24, 8)
cv.line(16, 18, 28, 30); cv.line(28, 18, 16, 30)                       # zapečetěná brána
cv.druid(8, 40); cv.druid(36, 40)
cv.seed(22, 10)
cv.line(10, 30, 20, 12, rgb(0x9AD8E8)); cv.line(34, 30, 24, 12, rgb(0x9AD8E8))   # paprsky ke Srdci
cv.line(44, 41, 56, 22); cv.line(56, 22, 68, 41)                       # hora
cv.dragon(55, 25, 0.9, asleep=True)
cv.dot(50, 33, rgb(0x7AB8FF)); cv.dot(51, 32, rgb(0x7AB8FF))           # modrý krystal
cv.dot(61, 33, rgb(0xFF6A6A)); cv.dot(60, 32, rgb(0xFF6A6A))           # červený krystal
cv.line(4, 41, 68, 41)
save(m, "mural_2.png")

# 3) Pán popela: koruna z uhlíků a oči za branou, popel padá na stromy, strážce se vpíjí do kořenů dubu
m = stone_canvas(37); cv = Carver(m)
cv.ring(54, 20, 10)
for (dx, dy) in [(-4, -5), (-2, -7), (0, -5), (2, -7), (4, -5)]:      # koruna
    cv.dot(54 + dx, 20 + dy, ember)
cv.line(50, 16, 58, 16, ember)
cv.dot(51, 20, rgb(0xFFE070)); cv.dot(57, 20, rgb(0xFFE070))           # oči
for k in range(14):                                                    # popel padá doleva dolů
    cv.dot(44 - k * 2.4 + (k % 3), 18 + k * 1.3 + (k % 2) * 2, rgb(0xB06A4A))
cv.line(18, 41, 18, 24); cv.line(12, 30, 18, 24); cv.line(24, 30, 18, 24); cv.line(10, 26, 26, 26)   # dub
for side in (-1, 1):                                                   # kořeny
    cv.line(18, 41, 18 + side * 10, 45)
cv.druid(30, 40)
for k in range(5):                                                     # strážce se rozplývá do kořenů
    cv.dot(27 - k * 1.5, 41 + (k % 2), rgb(0x9AD8E8))
cv.line(4, 41, 68, 41)
save(m, "mural_3.png")
print("ok")

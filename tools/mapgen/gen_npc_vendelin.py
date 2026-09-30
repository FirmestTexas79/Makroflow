"""Havíř Vendelín (docs/adr/0050) – portrét 64×64, uložený 8× (512×512) bez vyhlazení.

Starý havíř z Dolů: kožená přilba s lampičkou, šedý rozcuchaný vous, obličej od sazí, jedno oko
zakalené. Vlnený kabát s koženou zástěrou a vybledlou nášivkou S-7, v ruce zvedá kahan, ve
kterém místo oleje svítí jiskřivky.
Výstup: app/src/main/res/drawable-nodpi/npc_vendelin.png
"""
import math, os
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable-nodpi")
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0 - 0.5
N = 64
ys, xs = np.mgrid[0:N, 0:N].astype(float)


def rgb(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], dtype=float)


img = np.zeros((N, N, 4))
solid = np.zeros((N, N), bool)            # co dostane obrys


def paint(mask, base, light=None, dark=None, lx=-1.0, ly=-1.0, cx=None, cy=None, solid_=True):
    """Vybarví masku s jednoduchým stínováním (světlo zleva shora) a ditheringem."""
    m = mask
    if cx is None:
        yy, xx = np.nonzero(m)
        cx, cy = xx.mean(), yy.mean()
    sh = ((xs - cx) * lx + (ys - cy) * ly) / 8.0 + BAYER[ys.astype(int) % 4, xs.astype(int) % 4] * 0.6
    for y, x in zip(*np.nonzero(m)):
        c = base
        if light is not None and sh[y, x] > 0.55: c = light
        elif dark is not None and sh[y, x] < -0.55: c = dark
        img[y, x, :3] = c; img[y, x, 3] = 255
    if solid_: solid[m] = True


def ellipse(cx, cy, rx, ry):
    return ((xs - cx) / rx) ** 2 + ((ys - cy) / ry) ** 2 <= 1


def put(x, y, c, a=255):
    if 0 <= x < N and 0 <= y < N:
        img[y, x, :3] = c; img[y, x, 3] = a; solid[y, x] = a > 200


# ── barvy ──
COAT, COAT_L, COAT_D = rgb(0x3A4660), rgb(0x56668A), rgb(0x242C40)
APRON, APRON_L, APRON_D = rgb(0x6E4424), rgb(0x94603A), rgb(0x4A2A14)
SKIN, SKIN_L, SKIN_D = rgb(0xB88A6A), rgb(0xD8AA86), rgb(0x7E5A44)
SOOT = rgb(0x4A3A34)
BEARD, BEARD_L, BEARD_D = rgb(0xA8A8B0), rgb(0xE0E0E6), rgb(0x6E6E78)
HELM, HELM_L, HELM_D = rgb(0x5A3C24), rgb(0x8A5E36), rgb(0x3A2414)
BRASS, BRASS_L, BRASS_D = rgb(0xC8943A), rgb(0xF2D07A), rgb(0x7E5820)
GLOW, GLOW2, HOT = rgb(0xFFE27A), rgb(0xFFA83A), rgb(0xFFFFF0)
OUT = rgb(0x140E0C)

# ── záře kahanu (pod vším, poloprůhledná) ──
LX, LY = 11, 20
d = np.hypot(xs - LX, ys - LY)
for y in range(N):
    for x in range(N):
        a = max(0.0, 1 - d[y, x] / 17.0) ** 1.6
        q = round((a + BAYER[y % 4, x % 4] * 0.18) * 4) / 4
        if q > 0:
            img[y, x, :3] = GLOW2 * 0.6 + GLOW * 0.4
            img[y, x, 3] = int(110 * q)

# ── kabát (lichoběžník), zástěra, opasek ──
coat = (ys >= 35) & (np.abs(xs - 31) <= 9 + (ys - 35) * 0.42)
paint(coat, COAT, COAT_L, COAT_D, cx=31, cy=46)
apron = (ys >= 41) & (np.abs(xs - 31) <= 5 + (ys - 41) * 0.2)
paint(apron, APRON, APRON_L, APRON_D, cx=31, cy=52)
for x in range(20, 43):
    if coat[45, x]: put(x, 45, APRON_D)
put(31, 45, BRASS_L); put(30, 45, BRASS)
# kapsa na zástěře s kladívkem
for x in range(28, 35): put(x, 52, APRON_D)
for y in range(48, 52): put(33, y, rgb(0x8C8C94))
# nášivka S-7 na hrudi (vybledlá)
for y in range(37, 41):
    for x in range(36, 41):
        put(x, y, rgb(0x8A4A44) if (x + y) % 5 else rgb(0x6A3A36))
put(37, 38, rgb(0xD8C8B8)); put(39, 38, rgb(0xD8C8B8)); put(39, 39, rgb(0xD8C8B8)); put(37, 39, rgb(0xB8A898))

# ── zvednutá pravá paže s kahanem ──
arm_pts = [(19, 37), (16, 33), (14, 29), (13, 26)]
for (ax, ay), (bx, by) in zip(arm_pts, arm_pts[1:]):
    for k in range(6):
        t = k / 5
        x, y = ax + (bx - ax) * t, ay + (by - ay) * t
        m = ellipse(x, y, 2.6, 2.6)
        paint(m, COAT, COAT_L, COAT_D, cx=15, cy=31)
paint(ellipse(12.5, 25, 2.2, 2.0), SKIN, SKIN_L, SKIN_D)                   # pěst
# kahan: ucho, víčko, sklo s jiskřivkami, dno
for x in range(9, 14): put(x, 12, BRASS_D)
put(8, 13, BRASS_D); put(14, 13, BRASS_D)
for x in range(7, 16): put(x, 14, BRASS_L)
for x in range(6, 17): put(x, 15, BRASS)
for y in range(16, 24):
    for x in range(6, 17):
        edge = x in (6, 16) or (x == 11 and y % 2 == 0)
        put(x, y, BRASS_D if edge else rgb(0x5A3818))
for (fx, fy) in ((8, 17), (13, 19), (9, 21), (12, 22)):
    put(fx, fy, GLOW); put(fx + 1, fy, GLOW2)
put(14, 17, HOT)
for x in range(6, 17): put(x, 24, BRASS)
for x in range(7, 16): put(x, 25, BRASS_D)

# ── hlava: obličej, vous, přilba ──
face = ellipse(31, 21, 8, 7)
paint(face, SKIN, SKIN_L, SKIN_D)
# saze na tvářích
for (x, y) in ((25, 22), (26, 23), (36, 21), (37, 22), (35, 23), (28, 17)):
    put(x, y, SOOT)
# obočí huňaté, oči (levé zakalené)
for x in range(26, 30): put(x, 18, BEARD_D)
for x in range(33, 37): put(x, 18, BEARD_D)
put(27, 19, OUT); put(28, 19, rgb(0x2A1A10))
put(34, 19, rgb(0xC8D4DC)); put(35, 19, rgb(0x9AAAB8))                   # zakalené oko
put(31, 21, SKIN_D); put(31, 22, rgb(0xB0645A)); put(32, 22, rgb(0x9A5048))  # nos
# vous: velký rozcuchaný
beard = ellipse(31, 29, 10, 9) & (ys >= 23)
beard |= ellipse(31, 36, 6, 5)
paint(beard, BEARD, BEARD_L, BEARD_D, cx=31, cy=30)
r = np.random.default_rng(7)
for _ in range(26):                                                          # prameny
    x = int(r.integers(23, 40)); y = int(r.integers(25, 40))
    if beard[y, x]: put(x, y, BEARD_D if r.random() < 0.6 else BEARD_L)
for x in range(28, 35): put(x, 25, BEARD_L)                                  # knír
put(30, 26, rgb(0x5A3A30)); put(31, 26, rgb(0x5A3A30)); put(32, 26, rgb(0x5A3A30))   # ústa pod knírem
# přilba: kopule + krempa + lampička
helm = ellipse(31, 14, 11, 7.5) & (ys <= 14)
paint(helm, HELM, HELM_L, HELM_D, cx=31, cy=10)
for x in range(18, 45): put(x, 15, HELM_D); put(x, 14, HELM)
for x in range(21, 42):
    if (x * 7) % 5 == 0: put(x, 12, HELM_L)
for y in range(7, 12):
    for x in range(28, 35):
        put(x, y, BRASS if not (x in (28, 34) or y in (7, 11)) else BRASS_D)
put(30, 9, GLOW); put(31, 9, HOT); put(32, 9, GLOW); put(31, 10, GLOW2)     # lampička svítí

# ── obrys ──
sol = solid.copy()
for y in range(N):
    for x in range(N):
        if sol[y, x]: continue
        if any(0 <= x + dx < N and 0 <= y + dy < N and sol[y + dy, x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            img[y, x, :3] = OUT; img[y, x, 3] = 255

out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").resize((N * 8, N * 8), Image.NEAREST)
os.makedirs(RES, exist_ok=True)
out.save(os.path.join(RES, "npc_vendelin.png"), optimize=True)
print("npc_vendelin.png", out.size)

"""
Spalovna spisů – 10. patro Věže Řádu Váhy: palety, hromada uhlí s lopatou a parní potrubí s manometrem
(schválené díly). Ostatní díly patra jsou ve fire2.py. Ve 3/4, objemově, obrys selout.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
from pix import Spr, c  # noqa: E402
from figures import sphere, selout, blobs, BRASS, PAPER, INK, STEEL, RED, IRON_, shade_px  # noqa: E402

SOOT = [c(0x0C0A0C), c(0x181416), c(0x241E20), c(0x322A2A), c(0x443836)]
EMBER = [c(0x4A1006), c(0x8A220A), c(0xD24E10), c(0xF48A22), c(0xFCD058), c(0xFFF2C0)]
COAL = [c(0x060608), c(0x0E0E14), c(0x18181F), c(0x262632), c(0x6A7088)]


def _h(x, y, s=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (s * 83492791)) & 0xFFFF


def coal_pile(seed=0, w=34, h=20, shovel=False):
    """Hromada uhlí: hrudky jako malé koule s lesklou hranou, rozsypané kousky kolem, volitelně lopata."""
    s = Spr(w, h + (14 if shovel else 0))
    oy = 14 if shovel else 0
    rng = np.random.default_rng(seed)
    sx = w * 0.64
    if shovel:                                                                      # list lopaty (zapíchnutý, zbytek zakryje uhlí)
        for y in range(oy + 1, oy + 12):
            for x in range(int(sx + 1), int(sx + 8)):
                s.px(x, y, STEEL[3] if x < sx + 3 else STEEL[2] if x < sx + 6 else STEEL[1])
    lumps = []
    for i in range(70):                                                             # kupka: výš uprostřed
        x = w / 2 + rng.uniform(-1, 1) * (w / 2 - 3)
        top = h - 3 - (1 - (abs(x - w / 2) / (w / 2)) ** 1.6) * (h - 6)
        y = rng.uniform(top, h - 3)
        lumps.append((x, y + oy, rng.uniform(1.8, 3.0)))
    lumps.sort(key=lambda l: l[1])
    for (x, y, r) in lumps:
        sphere(s, x, y, r, r * 0.85, COAL[:4], amb=0.35)
        if rng.random() < 0.5:
            s.px(x - r * 0.4, y - r * 0.5, COAL[4])                                   # lesk hrany
    for _ in range(10):
        x = int(rng.integers(1, w - 1)); s.px(x, h - 1 + oy, COAL[1]); s.px(x + 1, h - 1 + oy, COAL[3])
    if shovel:                                                                      # násada 2 px + rukojeť do T
        for k in range(15):
            X = sx + 4 - k * 0.3; Y = oy + 1 - k
            s.px(X, Y, c(0x9A7A46)); s.px(X + 1, Y, c(0x6A4A26))
        for x in range(int(sx - 1), int(sx + 5)):
            s.px(x, oy - 14, c(0x9A7A46)); s.px(x, oy - 13, c(0x6A4A26))
    return selout(s, 0.55, 0.85)


def steam_pipe(h=64):
    """Svislé parní potrubí na stěně s ventilovým kolem a manometrem (ručička v červeném poli)."""
    s = Spr(22, h)
    for y in range(h):
        for x in range(8, 14):
            s.px(x, y, IRON_[4] if x == 8 else IRON_[3] if x < 11 else IRON_[1])
        if y % 12 == 0:
            for x in range(7, 15):
                s.px(x, y, IRON_[2]); s.px(x, y + 1, IRON_[0])
    gy = 14
    for y in range(gy - 7, gy + 8):                                                     # manometr
        for x in range(4, 19):
            d = math.hypot(x + .5 - 11, y + .5 - gy)
            if d <= 7:
                s.px(x, y, BRASS[2] if d > 5.6 else PAPER[3])
    for a in range(0, 8):
        ang = math.pi * (0.85 + a * 0.18)
        s.px(11 + math.cos(ang) * 4.4, gy + math.sin(ang) * 4.4, INK if a < 6 else RED[2])
    s.line(11, gy, 11 + math.cos(math.pi * 2.05) * 4, gy + math.sin(math.pi * 2.05) * 4, RED[3])
    vy = 40
    for y in range(vy - 6, vy + 7):                                                     # ventilové kolo
        for x in range(4, 19):
            d = math.hypot(x + .5 - 11, (y + .5 - vy) * 1.2)
            if 4.8 <= d <= 6.6 or (d < 6 and (abs(x - 11) < 1 or abs(y - vy) < 1)):
                s.px(x, y, RED[2] if x < 11 else RED[1])
    return selout(s, 0.55, 0.85)

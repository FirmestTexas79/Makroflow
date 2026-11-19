"""
Zatopené patro – 7. patro Věže Řádu Váhy, patro vodních Makromonů. Kancelář, kterou zaplavila voda:
zadní stěna je obří akvárium za sklem, podlaha je hladina, po které vedou dřevěné lávky na kůlech,
pod vodou jsou vidět utopené stoly a židle, na hladině lekníny a plovoucí listy papíru, u lávky
přivázaná loďka, záchranný kruh. Ve 3/4, objemově, obrys selout.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
from pix import Spr, c  # noqa: E402
from figures import sphere, selout, blobs, _pick, BRASS, PAPER, INK, STEEL, RED, IRON_, shade_px  # noqa: E402

WATER = [c(0x08202A), c(0x0E3240), c(0x164858), c(0x206272), c(0x2E7E8C), c(0x5AAAB4)]
TANK = [c(0x0A2E44), c(0x124A66), c(0x1C6688), c(0x2A86A8), c(0x58AECC)]
SAND = [c(0x8A7A56), c(0xB0A070), c(0xCCBC8A)]
WOOD = [c(0x2E1C10), c(0x4E3220), c(0x6E4A30), c(0x8E6440), c(0xAE8256)]
PAD = [c(0x1E4A22), c(0x2E6A30), c(0x4A8A3E), c(0x76B052)]
WEED = [c(0x163A22), c(0x24582E), c(0x3A7A3C)]


def _h(x, y, s=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (s * 83492791)) & 0xFFFF


def water(img, mask, y0):
    """Hladina: hloubka podle vzdálenosti od kraje (u lávek a břehů světlejší), jemné vodorovné vlnky,
    odlesky světla; kreslí se jen tam, kde je mask True."""
    H, W = mask.shape
    dist = np.zeros(mask.shape)                                                     # vzdálenost od kraje (eroze, max 12)
    cur = mask.copy()
    for k in range(12):
        dist += cur
        cur = cur & np.roll(cur, 1, 0) & np.roll(cur, -1, 0) & np.roll(cur, 1, 1) & np.roll(cur, -1, 1)
    for y in range(y0, H):
        for x in range(W):
            if not mask[y, x]:
                continue
            d = dist[y, x]
            k = 4 if d < 2 else 3 if d < 5 else 2 if d < 12 else 1
            wave = math.sin(x * 0.45 + y * 0.9) + math.sin(x * 0.17 - y * 0.6)
            if wave > 1.5:
                k = min(5, k + 1)
            col = WATER[k]
            if _h(x, y) % 211 == 0 and d > 3:
                col = WATER[5]
            img[y, x] = col


def tint_under(spr, k=0.55):
    """Ztopí sprite pod vodu: přebarví do modrozelené a ztmaví, spodek víc (hloubka)."""
    a = spr.a
    m = a[:, :, 3] > 0
    for y in range(spr.h):
        t = k + (1 - k) * 0.4 * (y / max(1, spr.h - 1))
        row = m[y]
        a[y, row, :3] = (a[y, row, :3] * (1 - t) + np.array([14, 60, 74]) * t).astype(np.uint8)
    return spr


def aquarium_wall(W, H, ex0, ex1):
    """Zadní stěna = obří akvárium: nahoře kachlový pás, pak sklo v mosazných rámech, za ním voda s
    přechodem, písek, kameny, chaluhy, světelné paprsky shora a šikmé odlesky na skle. Mezi [ex0, ex1] je výtah."""
    s = Spr(W, H)
    rng = np.random.default_rng(9)
    top, bot = 10, H - 6
    for y in range(H):
        for x in range(W):
            if y < top:
                lx, ly = x % 8, y % 6
                col = c(0x8A9A94) if (lx == 7 or ly == 5) else c(0xA8B8B0)
            elif y >= bot:
                col = c(0x5C6A66) if y > bot else BRASS[2]
            else:
                t = (y - top) / (bot - top)
                col = TANK[4 if t < 0.12 else 3 if t < 0.35 else 2 if t < 0.65 else 1]
            s.px(x, y, col)
    for x in range(W):                                                              # písek a kameny
        sh = bot - 6 + int(2 * math.sin(x * 0.2) + math.sin(x * 0.07))
        for y in range(sh, bot):
            s.px(x, y, SAND[2] if y == sh else SAND[1] if y < bot - 2 else SAND[0])
    for _ in range(9):
        cx = rng.uniform(4, W - 4); r = rng.uniform(2, 4)
        sphere(s, cx, bot - 5, r, r * 0.7, [c(0x2A3438), c(0x4A565A), c(0x6E7A7E), c(0x96A2A4)], amb=0.3)
    for _ in range(16):                                                             # chaluhy
        x = rng.uniform(2, W - 2); L = rng.uniform(10, 34)
        for k in range(int(L)):
            xx = x + 1.5 * math.sin(k * 0.35 + x)
            s.px(int(xx), bot - 6 - k, WEED[2] if k % 3 else WEED[1])
            if k % 4 == 0:
                s.px(int(xx) + 1, bot - 6 - k, WEED[1])
    for i in range(4):                                                              # paprsky shora
        x0 = 14 + i * 44
        for y in range(top, bot - 8):
            for w in range(3):
                X = int(x0 + (y - top) * 0.25 + w)
                if 0 <= X < W and (X + y) % 2 == 0:
                    col = s.a[y, X, :3].astype(int) + 20
                    s.a[y, X, :3] = np.clip(col, 0, 255)
    for X in list(range(0, W, 44)) + [W - 2]:                                       # mosazné rámy
        for y in range(top, bot):
            for k in range(2):
                if 0 <= X + k < W:
                    s.px(X + k, y, BRASS[3] if k == 0 else BRASS[1])
    for x in range(W):
        s.px(x, top, BRASS[4]); s.px(x, top + 1, BRASS[2])
    for y in range(top + 4, bot - 10):                                              # odlesky na skle
        for x0 in (10, 54, 98, 142):
            X = x0 + (y - top) // 3
            if (y // 3) % 7 < 3 and X < W:
                s.px(X, y, TANK[4])
    return s


def lily_pad(seed=0, r=5):
    """Leknín: zelený list (elipsa ve 3/4) se zářezem k středu, žilky, okraj světlejší; občas kvítek."""
    rng = np.random.default_rng(seed)
    w, h = r * 2 + 3, r + 4
    s = Spr(w, h)
    cx, cy = w / 2, h / 2
    notch = rng.uniform(0, 2 * math.pi)
    for y in range(h):
        for x in range(w):
            dx, dy = (x + .5 - cx) / r, (y + .5 - cy) / (r * 0.55)
            d = dx * dx + dy * dy
            if d > 1:
                continue
            a = math.atan2(dy, dx)
            if abs((a - notch + math.pi) % (2 * math.pi) - math.pi) < 0.3:
                continue
            col = PAD[3] if d > 0.7 and dy < 0 else PAD[2] if dy < 0.2 else PAD[1]
            if int(a * 4) % 3 == 0 and d > 0.15 and d < 0.8:
                col = PAD[1]
            s.px(x, y, col)
    if rng.random() < 0.4:
        sphere(s, cx + 1, cy - 1, 1.8, 1.4, [c(0xC86A8A), c(0xE89AB4), c(0xFAD4E2), c(0xFFFFFF)], amb=0.4)
    return selout(s, 0.6, 0.88)


def reeds(seed=0):
    """Rákos u kraje: svislá stébla s pár listy, nahoře hnědé palice orobince."""
    s = Spr(14, 22)
    rng = np.random.default_rng(seed)
    for i in range(6):
        x = 2 + i * 2 + int(rng.integers(0, 2)); top = int(rng.integers(0, 8))
        for y in range(top, 22):
            s.px(x, y, WEED[2] if y < 14 else WEED[1])
        if rng.random() < 0.6:
            for y in range(top, top + 4):
                s.px(x, y, c(0x5A3A1E)); s.px(x + 1, y, c(0x3A2412))
    return selout(s, 0.6, 0.88)


def rowboat():
    """Loďka ve 3/4: dřevěný trup s žebry, sedačka, veslo přes okraj, provaz k lávce."""
    W, H = 36, 18
    s = Spr(W, H)
    for y in range(H):
        for x in range(W):
            dx, dy = (x + .5 - W / 2) / (W / 2), (y + .5 - H / 2) / (H / 2)
            if dx * dx + dy * dy * 0.9 > 1:
                continue
            inner = dx * dx * 1.25 + dy * dy * 1.6 < 1
            col = WOOD[2] if inner else WOOD[4] if dy < 0 else WOOD[1]
            if inner and int(x) % 6 == 0:
                col = WOOD[1]                                                       # žebra
            s.px(x, y, col)
    for x in range(14, 22):
        s.px(x, 8, WOOD[4]); s.px(x, 9, WOOD[3])
    for k in range(18):
        s.px(6 + k, 4 + k // 4, WOOD[3])
    for x in range(W - 4, W):
        s.px(x, 9 + (x - W + 4) // 2, c(0xC4A060))
    return selout(s, 0.55, 0.85)


def life_ring():
    """Záchranný kruh na kůlu: červenobílý prstenec (objemový), lano kolem dokola."""
    s = Spr(16, 24)
    for y in range(8, 24):
        s.px(7, y, WOOD[3]); s.px(8, y, WOOD[1])
    for y in range(0, 15):
        for x in range(0, 16):
            d = math.hypot(x + .5 - 8, (y + .5 - 7) * 1.1)
            if 4 <= d <= 7:
                ang = math.atan2(y - 7, x - 8)
                red = int((ang + math.pi) / (math.pi / 2)) % 2 == 0
                lit = (x < 8 and y < 7)
                s.px(x, y, (c(0xE04A3A) if lit else c(0xA82A22)) if red else (c(0xFAF6EE) if lit else c(0xC8C4BA)))
    return selout(s, 0.55, 0.85)


def pipe_valve():
    """Trubka trčící z vody s ventilovým kolem: železo ve 3/4, rezavé skvrny, kolo s paprsky."""
    s = Spr(18, 24)
    for y in range(8, 24):
        for x in range(6, 12):
            s.px(x, y, IRON_[4] if x == 6 else IRON_[3] if x < 9 else IRON_[1])
        if y % 5 == 0:
            s.px(9, y, c(0x8A4A22))
    for y in range(0, 12):
        for x in range(0, 18):
            d = math.hypot(x + .5 - 9, (y + .5 - 6) * 1.3)
            if 5.5 <= d <= 7.5 or (d < 7 and (abs(x - 9) < 1 or abs(y - 6) < 1)):
                s.px(x, y, RED[2] if x < 9 else RED[1])
    sphere(s, 9.5, 6, 1.6, 1.4, IRON_, amb=0.35)
    return selout(s, 0.55, 0.85)

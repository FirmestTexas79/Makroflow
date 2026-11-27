"""
Ikony Výhně 20 × 20 (docs/adr/0118) ve stylu předloh: objemové stínování z mapy vzdálenosti (světlo vlevo nahoře),
barevné rampy s posunem odstínu (stíny do fialova, světla do žluta), jasný odlesk, tmavý barevný obrys podle souseda.
Každá ikona = vrstvy (maska + materiál), kreslené po sobě.
"""
import math
import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

N = 20
LIGHT = np.array([-0.62, -0.72])


def hexc(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], np.float32)


def ramp(*hexes):
    return [hexc(h) for h in hexes]


# rampy: tma → světlo (5–6 odstínů)
LEATHER = ramp(0x2A1418, 0x4E2A22, 0x7A4630, 0xA66A42, 0xC99360, 0xEFC890)
WOOD = ramp(0x24141A, 0x48281E, 0x70442A, 0x9A6436, 0xC08A4E, 0xE4B878)
IRON = ramp(0x161420, 0x2C2A3A, 0x4A4A5C, 0x72788A, 0xA4ACBA, 0xE2E8F0)
BRASS = ramp(0x2E1A14, 0x6A4220, 0xA86E2C, 0xD8A23E, 0xF2D26A, 0xFFF4B8)
GOLD = ramp(0x3A1E14, 0x7A4818, 0xC07E1E, 0xEDB230, 0xFFE070, 0xFFFBD0)
SILVER = ramp(0x1E2030, 0x3E4458, 0x6C7488, 0xA2AABC, 0xD4DAE4, 0xFFFFFF)
COAL = ramp(0x0C0A10, 0x1C1822, 0x2E2834, 0x46404C, 0x6A6270, 0x9A92A0)
FIRE = ramp(0x5A0E10, 0xA82418, 0xE0561E, 0xFF9A2A, 0xFFD254, 0xFFF8C8)
EMBER = ramp(0x3A0A0C, 0x7A1A10, 0xC4401A, 0xF07A22, 0xFFB44A, 0xFFE6A0)
PAPER = ramp(0x3A2A22, 0x7A624A, 0xB49A72, 0xDCC89A, 0xF2E6C0, 0xFFFBEA)
ASHR = ramp(0x2A2830, 0x4C4A54, 0x74727C, 0x9C9AA2, 0xC6C4CA, 0xEEEDF0)
RED = ramp(0x2E0A18, 0x6A1424, 0xA82432, 0xDC4448, 0xFF8476, 0xFFD4C8)
GREEN = ramp(0x0E2418, 0x1E4626, 0x2E7434, 0x4EA444, 0x8ED45C, 0xD8F6A0)
EARTH = ramp(0x24160E, 0x4A2E1A, 0x7A5030, 0xA87A4A, 0xCCA470, 0xEED8A8)
VOLT = ramp(0x3A2A06, 0x7A5A0A, 0xC0981A, 0xF0D030, 0xFFF070, 0xFFFFD8)
STEEL_BLUE = ramp(0x101830, 0x22305A, 0x34508A, 0x5478B8, 0x86A8DC, 0xD0E4FF)
NIGHT = ramp(0x2A2440, 0x5A5070, 0x9A90A0, 0xD6CCA8, 0xF2E8B8, 0xFFFBE6)
WHITE = ramp(0x6A6060, 0x9A9090, 0xC8C0BC, 0xE8E2DC, 0xFAF6F0, 0xFFFFFF)


class Icon:
    def __init__(self):
        self.rgb = np.zeros((N, N, 3), np.float32)
        self.a = np.zeros((N, N), bool)
        self.layer = np.full((N, N), -1, int)
        self.ramps = []

    def mask(self, draw_fn):
        im = Image.new("L", (N * 4, N * 4), 0)
        draw_fn(ImageDraw.Draw(im), 4)
        return np.array(im.resize((N, N), Image.BOX)) > 110

    def fill(self, m, rmp, mode="round", bias=0.0, spec=True, flat=None):
        """Vyplní masku materiálem. mode round = objem z mapy vzdálenosti, flat = svislý přechod, const = jeden odstín."""
        if not m.any():
            return m
        d = ndimage.distance_transform_edt(np.pad(m, 1))[1:-1, 1:-1]
        dm = max(d.max(), 1)
        h = 1 - (1 - d / dm) ** 2
        gy, gx = np.gradient(h)
        lam = -(gx * LIGHT[0] + gy * LIGHT[1]) * 2.6
        if mode == "round":
            s = 0.42 + lam + h * 0.22 + bias
        elif mode == "flat":
            yy = np.arange(N)[:, None] * np.ones((1, N))
            ys = yy[m]
            s = 0.75 - (yy - ys.min()) / max(1, ys.max() - ys.min()) * 0.4 + lam * 0.5 + bias
        else:
            s = np.full((N, N), 0.55 + bias)
        n = len(rmp) - (1 if spec else 0)
        idx = np.clip((s * n).astype(int), 0, n - 1)
        li = len(self.ramps); self.ramps.append(rmp)
        for y, x in zip(*np.nonzero(m)):
            self.rgb[y, x] = rmp[idx[y, x]]
            self.layer[y, x] = li
        self.a |= m
        if spec:
            cand = np.where(m, s, -9)
            y, x = np.unravel_index(np.argmax(cand), cand.shape)
            self.rgb[y, x] = rmp[-1]
        return m

    def px(self, x, y, col):
        if 0 <= x < N and 0 <= y < N:
            self.rgb[y, x] = col if not isinstance(col, int) else hexc(col); self.a[y, x] = True

    def done(self):
        out = np.zeros((N, N, 4), np.float32)
        out[:, :, :3] = self.rgb; out[:, :, 3] = self.a * 255
        a = self.a
        # vnější obrys: tmavší barva sousedního pixelu, do fialova
        for y in range(N):
            for x in range(N):
                if a[y, x]:
                    continue
                nb = [(y + dy, x + dx) for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)) if 0 <= y + dy < N and 0 <= x + dx < N and a[y + dy, x + dx]]
                if nb:
                    col = np.mean([self.rgb[p] for p in nb], axis=0)
                    out[y, x, :3] = np.clip(col * 0.22 + np.array([14, 8, 18]), 0, 255)
                    out[y, x, 3] = 255
        # vnitřní švy mezi vrstvami (tmavší hrana na straně dál od světla)
        L = self.layer
        for y in range(1, N):
            for x in range(1, N):
                if a[y, x] and L[y, x] >= 0 and (L[y - 1, x] not in (-1, L[y, x]) or L[y, x - 1] not in (-1, L[y, x])):
                    out[y, x, :3] *= 0.78
        return out


def poly(pts):
    return lambda d, s: d.polygon([(x * s, y * s) for x, y in pts], fill=255)


def ell(x0, y0, x1, y1):
    return lambda d, s: d.ellipse([x0 * s, y0 * s, x1 * s, y1 * s], fill=255)


def rect(x0, y0, x1, y1):
    return lambda d, s: d.rectangle([x0 * s, y0 * s, x1 * s, y1 * s], fill=255)


def union(*fns):
    def f(d, s):
        for g in fns:
            g(d, s)
    return f


# ── jednotlivé ikony ────────────────────────────────────────────────────────
def bellows():
    i = Icon()
    i.fill(i.mask(poly([(0.5, 2.5), (2, 1.5), (6, 6), (4.5, 7)])), WOOD)                       # rukojeti
    i.fill(i.mask(poly([(0.5, 17.5), (2, 18.5), (6, 14), (4.5, 13)])), WOOD)
    i.fill(i.mask(poly([(15, 8.6), (19.6, 9.4), (19.6, 10.6), (15, 11.4)])), BRASS)             # tryska
    i.fill(i.mask(poly([(4, 4.5), (11, 3.5), (16.5, 8.2), (16.5, 9.6), (3.5, 9.6), (2.8, 7)])), WOOD, bias=0.12)   # horní deska
    i.fill(i.mask(poly([(3.5, 10.4), (16.5, 10.4), (16.5, 11.8), (11, 16.5), (4, 15.5), (2.8, 13)])), WOOD, bias=-0.12)
    for x in range(3, 17):                                                                     # kožený harmonikový pás
        for y in (9, 10):
            if 2 <= x <= 16:
                i.px(x, y, 0xA66A42 if (x + y) % 2 == 0 else 0x4E2A22)
    i.fill(i.mask(ell(8, 5, 11, 8)), BRASS, bias=0.2)                                         # mosazný štít
    i.fill(i.mask(ell(8, 12, 11, 15)), BRASS, bias=0.0)
    return i.done()


def spark():
    i = Icon()
    i.fill(i.mask(poly([(10, 0.5), (12, 8), (19.5, 10), (12, 12), (10, 19.5), (8, 12), (0.5, 10), (8, 8)])), FIRE, bias=0.25)
    i.fill(i.mask(ell(7, 7, 13, 13)), WHITE, bias=0.3)
    for (x, y) in ((3, 3), (16, 4), (4, 16), (16, 15)):
        i.px(x, y, 0xFFE070)
    return i.done()


def tag():
    i = Icon()
    i.fill(i.mask(poly([(7, 2), (18, 2), (18, 13), (11, 19.5), (1, 9.5)])), PAPER, mode="flat")
    i.fill(i.mask(ell(13, 4, 16, 7)), WOOD, spec=False)                                       # dírka
    for k in range(6):                                                                         # %
        i.px(7 + k, 14 - k, 0x8E2222)
    i.px(7, 9, 0x8E2222); i.px(8, 9, 0x8E2222); i.px(11, 13, 0x8E2222); i.px(12, 13, 0x8E2222)
    for (x, y) in ((16, 4), (17, 3), (18, 2), (19, 1)):
        i.px(x, y, 0xC8A060)                                                                   # provázek
    return i.done()


def flame_shape(cx, base, h, w):
    pts = []
    for k in range(13):
        t = k / 12
        hw = w * math.sin(t * math.pi) ** 0.8 * (1 - t * 0.45)
        pts.append((cx - hw + math.sin(t * 6) * 0.4, base - t * h))
    for k in range(12, -1, -1):
        t = k / 12
        hw = w * math.sin(t * math.pi) ** 0.8 * (1 - t * 0.45)
        pts.append((cx + hw + math.sin(t * 5 + 1) * 0.5, base - t * h))
    pts[12] = (cx + 0.8, base - h - 1)
    return poly(pts)


def flame_into(i, cx, base, h, w):
    i.fill(i.mask(flame_shape(cx, base, h, w)), FIRE, bias=0.1)
    i.fill(i.mask(flame_shape(cx, base - 0.5, h * 0.6, w * 0.55)), ramp(0xFF9A2A, 0xFFC040, 0xFFE070, 0xFFF4B0, 0xFFFFF0), bias=0.2)


def auto_fire():
    i = Icon()
    i.fill(i.mask(poly([(2, 15), (17, 13), (18, 16), (3, 18)])), WOOD)
    i.fill(i.mask(poly([(2, 13), (17, 16), (16, 19), (1, 16)])), WOOD, bias=-0.12)
    flame_into(i, 10, 15.5, 15, 7.5)
    return i.done()


def brazier():
    i = Icon()
    flame_into(i, 6.5, 11, 11, 4.8)
    flame_into(i, 13.5, 11, 10, 4.4)
    i.fill(i.mask(poly([(1.5, 10), (18.5, 10), (16, 15), (4, 15)])), IRON)
    i.fill(i.mask(union(rect(5, 15, 6.5, 19), rect(13.5, 15, 15, 19), rect(4, 18, 16, 19.2))), IRON, bias=-0.1)
    for x in range(4, 16):
        if i.a[10, x]:
            i.rgb[10, x] = hexc(0xFFB44A)
    return i.done()


def ingot_up():
    i = Icon()
    i.fill(i.mask(poly([(1.5, 13), (5, 9), (17, 9), (14, 13)])), EMBER, bias=0.2)
    i.fill(i.mask(poly([(1.5, 13), (14, 13), (14, 17), (1.5, 17)])), EMBER, bias=-0.05)
    i.fill(i.mask(poly([(14, 13), (17, 9), (17, 13), (14, 17)])), EMBER, bias=-0.25)
    i.fill(i.mask(poly([(12, 1), (17.5, 6), (15, 6), (15, 8.5), (9, 8.5), (9, 6), (6.5, 6)])), GOLD, bias=0.15)
    return i.done()


def ash_bucket():
    i = Icon()
    i.fill(i.mask(poly([(2.5, 7), (17.5, 7), (15.5, 19), (4.5, 19)])), IRON)
    i.fill(i.mask(ell(2.5, 5, 17.5, 9.5)), ASHR, bias=0.1)
    for (x, y) in ((7, 6), (11, 7), (13, 6), (9, 7)):
        i.px(x, y, 0xFF8A2A)
    i.px(12, 5, 0xFFD254)
    for x in range(3, 18):                                                                     # ucho
        y = int(round(4 - 3 * math.sin((x - 3) / 14 * math.pi)))
        i.px(x, y, 0x72788A)
    return i.done()


def boot():
    i = Icon()
    i.fill(i.mask(poly([(5, 2), (12, 2), (12.5, 11), (18.5, 13), (18.5, 17), (4, 17), (4.5, 10)])), LEATHER)
    i.fill(i.mask(rect(3.5, 17, 19, 19)), WOOD, bias=-0.2)
    i.fill(i.mask(rect(4.5, 1.5, 12.5, 3.5)), LEATHER, bias=0.15)
    for y in (6, 8, 10):
        i.px(9, y, 0xEFC890); i.px(10, y, 0xEFC890)
    return i.done()


def moon():
    i = Icon()
    m = i.mask(ell(2, 3, 17, 18)) & ~i.mask(ell(7, 0.5, 21, 14))
    i.fill(m, NIGHT, bias=0.15)
    for (x, y, c) in ((15, 3, 0xFFF4B8), (18, 7, 0xD6CCA8), (13, 9, 0xFFF4B8)):
        i.px(x, y, c)
    i.px(15, 2, 0xFFF4B8); i.px(15, 4, 0xFFF4B8); i.px(14, 3, 0xFFF4B8); i.px(16, 3, 0xFFF4B8)
    return i.done()


def coal(rmp, cracks):
    i = Icon()
    i.fill(i.mask(poly([(3, 9), (6, 4), (12, 2.5), (17, 6), (18.5, 12), (15, 17.5), (7, 18), (2, 14)])), rmp)
    i.fill(i.mask(poly([(6, 4), (12, 2.5), (13, 7), (8, 9)])), rmp, bias=0.25, spec=False)     # horní ploška
    if cracks:
        for (x, y) in ((8, 11), (9, 12), (10, 12), (11, 13), (12, 13), (13, 12), (10, 14), (7, 15)):
            i.px(x, y, 0xFF8A2A)
        i.px(10, 12, 0xFFD254)
    return i.done()


def shield(field, emblem):
    i = Icon()
    shape = poly([(2, 2), (18, 2), (18, 9), (15, 15), (10, 19.5), (5, 15), (2, 9)])
    i.fill(i.mask(shape), IRON)
    inner = poly([(4, 4), (16, 4), (16, 9), (13.5, 14), (10, 17), (6.5, 14), (4, 9)])
    i.fill(i.mask(inner), field, bias=0.05)
    emblem(i)
    return i.done()


def em_cross(i):
    i.fill(i.mask(union(rect(9, 5, 11, 15), rect(6, 8, 14, 10))), GOLD, bias=0.1)


def em_leaf(i):
    i.fill(i.mask(poly([(6, 14), (8, 8), (13, 5), (14, 6), (12, 12)])), GREEN, bias=0.2)
    for k in range(6):
        i.px(7 + k, 13 - k, 0x1E4626)


def em_rock(i):
    i.fill(i.mask(poly([(5.5, 13), (7, 9), (10, 7), (13, 8), (14.5, 12), (12, 15), (8, 15)])), EARTH, bias=0.1)


def em_bolt(i):
    i.fill(i.mask(poly([(11, 4.5), (7, 10.5), (10, 10.5), (8.5, 15.5), (13.5, 8.5), (10.5, 8.5), (12.5, 4.5)])), VOLT, bias=0.25)


def em_flame(i):
    flame_into(i, 10, 15.5, 11, 4.6)


def heart():
    i = Icon()
    m = i.mask(union(ell(2, 3, 11, 12), ell(9, 3, 18, 12), poly([(2.6, 9), (17.4, 9), (10, 18.5)])))
    i.fill(m, RED)
    return i.done()


def net(rmp):
    i = Icon()
    for k in range(9):                                                                         # násada
        i.px(2 + k // 2 + (k % 2), 18 - k, 0x9A6436 if k % 3 else 0x70442A)
    ring = i.mask(ell(6, 1.5, 18.5, 12.5)) & ~i.mask(ell(7.4, 2.9, 17.1, 11.1))
    inner = i.mask(ell(7.4, 2.9, 17.1, 11.1))
    for y, x in zip(*np.nonzero(inner)):
        if (x + y) % 3 == 0 or (x - y) % 3 == 0:
            i.px(x, y, 0xE8E2DC)
    i.fill(ring, rmp, bias=0.05)
    return i.done()


def temper():
    i = Icon()
    i.fill(i.mask(poly([(1, 11), (19, 11), (17, 14), (3, 14)])), IRON, bias=0.1)               # kovadlina
    i.fill(i.mask(rect(6, 14, 14, 17)), IRON, bias=-0.1)
    i.fill(i.mask(rect(4, 17, 16, 19)), IRON, bias=-0.2)
    i.fill(i.mask(poly([(10, 9), (17, 2), (18.5, 3.5), (11.5, 10.5)])), WOOD)                  # kladivo
    i.fill(i.mask(poly([(13, 1), (19, 1), (19, 5), (13, 5)])), IRON, bias=0.15)
    i.fill(i.mask(rect(5, 9, 11, 11)), EMBER, bias=0.2)                                        # žhavý kus
    return i.done()


def artifact_heart():
    i = Icon()
    i.fill(i.mask(ell(1.5, 1.5, 18.5, 18.5)), GOLD)
    i.fill(i.mask(ell(4.5, 4.5, 15.5, 15.5)), EMBER, bias=0.2)
    i.fill(i.mask(ell(7, 7, 12, 12)), ramp(0xFFB44A, 0xFFD254, 0xFFF0A0, 0xFFFFF0, 0xFFFFFF), bias=0.3)
    for (x, y) in ((10, 0), (10, 19), (0, 10), (19, 10)):
        i.px(x, y, 0xFFE070)
    return i.done()


def artifact_anvil():
    i = Icon()
    i.fill(i.mask(poly([(0.5, 6.5), (5, 5), (19, 5), (19, 9), (15, 9.8), (5, 9.8), (2.5, 8.5)])), GOLD, bias=0.12)   # roh + plocha
    i.fill(i.mask(poly([(7, 9.8), (14, 9.8), (12.5, 13.5), (8.5, 13.5)])), GOLD, bias=-0.05)                     # pas
    i.fill(i.mask(poly([(5, 13.5), (16, 13.5), (18, 17.5), (3, 17.5)])), GOLD, bias=-0.15)                       # pata
    for (x, y) in ((3, 2), (16, 1), (18, 3), (2, 1)):
        i.px(x, y, 0xFFFBD0)
    return i.done()


def artifact_hammer():
    i = Icon()
    i.fill(i.mask(poly([(2.5, 17), (4, 18.5), (13, 9.5), (11.5, 8)])), WOOD)                   # násada šikmo
    i.fill(i.mask(poly([(8, 5), (13, 0.5), (19.5, 7), (15, 11.5)])), GOLD, bias=0.08)           # hlava
    i.fill(i.mask(poly([(13, 0.5), (15, 0.5), (19.5, 5), (19.5, 7)])), GOLD, bias=0.3)
    i.fill(i.mask(ell(12, 4.5, 15.5, 8)), EMBER, bias=0.3)                                     # žhnoucí runa
    return i.done()


ICONS = [
    ("up_bellows", bellows), ("up_spark", spark), ("up_discount", tag), ("up_auto", auto_fire),
    ("up_hearth", brazier), ("up_mult", ingot_up), ("up_ash", ash_bucket), ("up_steps", boot), ("up_sleep", moon),
    ("coal", lambda: coal(COAL, True)), ("coal_silver", lambda: coal(SILVER, False)), ("coal_gold", lambda: coal(GOLD, False)),
    ("p_def", lambda: shield(STEEL_BLUE, em_cross)), ("p_grass", lambda: shield(ramp(0x1A2A1A, 0x2E3E2A, 0x44583E, 0x5E7454, 0x80966E, 0xB0C49A), em_leaf)),
    ("p_net", lambda: net(BRASS)), ("p_temper", temper),
    ("p_ground", lambda: shield(ramp(0x2A1E16, 0x44342A, 0x5E4A3C, 0x7A6452, 0x9C8670, 0xC6B49C), em_rock)),
    ("p_electric", lambda: shield(STEEL_BLUE, em_bolt)), ("p_heart", heart),
    ("p_fire", lambda: shield(ramp(0x2A0E12, 0x4A1A1C, 0x6A2A26, 0x8E3E30, 0xB0603E, 0xD88E5E), em_flame)),
    ("p_master", lambda: net(GOLD)),
    ("art_heart", artifact_heart), ("art_anvil", artifact_anvil), ("art_hammer", artifact_hammer),
]


def atlas():
    return np.concatenate([f() for _, f in ICONS], axis=1)

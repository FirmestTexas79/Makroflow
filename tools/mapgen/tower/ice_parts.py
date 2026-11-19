"""
Mrazírna – 9. patro Věže Řádu Váhy, patro ledových Makromonů. Patro zamrzlo: kachle pod jinovatkou, rampouchy,
okno s rozbitou tabulí, kterým dovnitř sněží (za ním polární záře), led na podlaze, závěje (setkání).
Hlavně hibernační komory: prosklené kapsle, ve kterých pod námrazou spí „kalibrovaní“ – spánek, který nemá konec.
Zamrzlé kartotéky, blok ledu s tvarem uvězněného tvora, teploměr na stěně. Ve 3/4, objemově, obrys selout.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
from pix import Spr, c  # noqa: E402
from figures import sphere, selout, blobs, _pick, BRASS, PAPER, INK, STEEL, RED, IRON_, shade_px  # noqa: E402

ICE = [c(0x3A5A72), c(0x5A82A0), c(0x82AAC8), c(0xAED0E6), c(0xD6ECF8), c(0xF4FBFF)]
SNOW = [c(0x8AA0B8), c(0xAEC0D4), c(0xCCDAE8), c(0xE6EEF6), c(0xFAFCFF)]
FROSTT = [c(0x6A7E8C), c(0x8EA2B0), c(0xB0C2CE), c(0xCCDAE2)]                  # kachle pod jinovatkou
AURORA = [c(0x1E8A6A), c(0x3AD09A), c(0x9AF0C8), c(0x8A5AD0)]
NIGHT = [c(0x060A18), c(0x0C1430), c(0x142048)]
METAL = [c(0x2A3240), c(0x48525E), c(0x6E7A88), c(0x9AA6B4), c(0xC8D2DC)]


def _h(x, y, s=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (s * 83492791)) & 0xFFFF


def ice_floor(img, x0, y0, x1, y1):
    """Zamrzlá podlaha: kachle 16 px pod vrstvou ledu (spáry prosvítají tlumeně), led má mraky tónů (hodnotový šum),
    některé kachle krátký šikmý odlesk v levém horním rohu, praskliny ve tvaru pavučin, vzácný poprašek sněhu."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx, ly = (x - x0) % 16, (y - y0) % 16
            tx, ty = (x - x0) // 16, (y - y0) // 16
            n = _vnoise(x, y, 22, 5) * 0.8 + _vnoise(x, y, 6, 6) * 0.2
            col = ICE[3] if n > 0.56 + 0.04 * ((x + y) % 2) else ICE[2]
            if (lx == 15 or ly == 15) and (x + y) % 3:
                col = ICE[1] if col is not ICE[1] else ICE[0]
            th = _h(tx, ty, 3)
            if th % 3 == 0 and 2 <= lx + ly <= 9 and abs(lx - ly) <= 1 + (lx + ly) // 4 and lx - ly + (lx + ly) % 2 < 2:
                col = ICE[4] if lx + ly < 7 else ICE[3]                                  # krátký odlesk
            if _h(x, y) % 401 == 0:
                col = SNOW[4]
            img[y, x] = col
    rng = np.random.default_rng(9)
    for _ in range(7):                                                              # praskliny
        cx, cy = rng.uniform(x0 + 10, x1 - 10), rng.uniform(y0 + 10, y1 - 10)
        for k in range(5):
            a = rng.uniform(0, 2 * math.pi); L = rng.uniform(4, 10)
            for t in range(int(L)):
                X, Y = int(cx + math.cos(a) * t), int(cy + math.sin(a) * t * 0.7)
                if x0 <= X < x1 and y0 <= Y < y1:
                    img[Y, X] = ICE[0] if t < L * 0.5 else ICE[1]
                    if 0 <= Y - 1 and t < L * 0.5:
                        img[Y - 1, X] = ICE[4]                                      # světlá hrana praskliny


def drift_edges(img, x0, y0, x1, y1, side_w=14):
    """Sněhové závěje podél stěn: vlnitý okraj, světlý vršek, modravý stín na spodní straně."""
    for y in range(y0, y1):
        for (xa, d) in ((x0, 1), (x1 - 1, -1)):
            w = side_w * (0.55 + 0.45 * math.sin(y * 0.05 + xa) ** 2) + 2 * math.sin(y * 0.3)
            for k in range(int(w)):
                x = xa + d * k
                t = k / max(1, w)
                img[y, x] = SNOW[4] if t > 0.75 else SNOW[3] if t > 0.4 else SNOW[2]
            img[y, xa + d * int(w)] = SNOW[1]


def _vnoise(x, y, scale, seed=0):
    """Hladký hodnotový šum (bilineární interpolace náhodných hodnot v mřížce [scale])."""
    gx, gy = x / scale, y / scale
    x0, y0 = int(math.floor(gx)), int(math.floor(gy))
    fx, fy = gx - x0, gy - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    v = lambda a, b: (_h(a, b, seed) % 1000) / 1000
    top = v(x0, y0) * (1 - fx) + v(x0 + 1, y0) * fx
    bot = v(x0, y0 + 1) * (1 - fx) + v(x0 + 1, y0 + 1) * fx
    return top * (1 - fy) + bot * fy


def frost_wall(W, H):
    """Zadní stěna pod jinovatkou: kachle přetažené námrazou (světlejší nahoře), ledové stalaktity – rampouchy
    z římsy, zmrzlé stékance."""
    s = Spr(W, H)
    rng = np.random.default_rng(4)
    for y in range(H):
        for x in range(W):
            lx, ly = x % 8, y % 6
            col = FROSTT[2] if not (lx == 7 or ly == 5) else FROSTT[1]
            fr = _vnoise(x, y, 14, 1) * 0.6 + _vnoise(x, y, 5, 2) * 0.4
            cover = 0.75 - 0.45 * (1 - y / H)                                       # nahoře víc námrazy
            if fr > cover:
                col = SNOW[3] if fr > cover + 0.08 else FROSTT[3]                    # jinovatka s měkkým okrajem
            if y < 4:
                col = SNOW[4] if y < 2 else SNOW[2]
            s.px(x, y, col)
    for _ in range(40):                                                             # rampouchy z římsy
        x = rng.uniform(1, W - 1); L = rng.uniform(3, 14); w = rng.uniform(0.8, 1.8)
        for y in range(4, 4 + int(L)):
            hw = w * (1 - (y - 4) / L)
            for xx in range(int(x - hw), int(x + hw) + 1):
                s.px(xx, y, ICE[5] if xx < x else ICE[3])
        s.px(int(x), 4 + int(L), ICE[4])
    for x in range(W):                                                              # sníh u paty
        hh = 4 + int(2 * math.sin(x * 0.2) + 1.5 * math.sin(x * 0.07))
        for y in range(H - hh, H):
            s.px(x, y, SNOW[4] if y < H - hh + 2 else SNOW[3])
    return s


def broken_window(w=24, h=52):
    """Okno s rozbitou tabulí: rám s námrazou, za sklem noc s polární září (zelené a fialové pruhy),
    v jedné tabuli díra se střepy, na parapetu navátý sníh."""
    s = Spr(w, h)
    for y in range(h):
        for x in range(w):
            if y < w / 2 and math.hypot(x + .5 - w / 2, y + .5 - w / 2) > w / 2:
                continue
            s.px(x, y, METAL[3] if x < 2 or y < 2 else METAL[2])
    for y in range(2, h - 4):
        for x in range(2, w - 2):
            if y < w / 2 and math.hypot(x + .5 - w / 2, y + .5 - w / 2) > w / 2 - 2:
                continue
            col = NIGHT[0] if y < h * 0.3 else NIGHT[1] if y < h * 0.6 else NIGHT[2]
            band = math.sin(x * 0.4 + y * 0.12) * 4 + 12 + y * 0.1                  # polární záře
            if abs(y - band) < 2.5 and y < h * 0.55:
                col = AURORA[1] if abs(y - band) < 1 else AURORA[0]
            if abs(y - band - 7) < 1.5 and y < h * 0.6:
                col = AURORA[3]
            s.px(x, y, col)
    s.vline(w // 2, 3, h - 5, METAL[1])
    for y in (int(h * 0.42), int(h * 0.7)):
        s.hline(2, w - 3, y, METAL[1])
    for y in range(int(h * 0.42) + 1, int(h * 0.7)):                                # rozbitá tabule (vpravo dole)
        for x in range(w // 2 + 1, w - 2):
            d = math.hypot(x - (w * 0.75), y - h * 0.56)
            if d < 3.5 + math.sin(math.atan2(y - h * 0.56, x - w * 0.75) * 5) * 1.2:
                s.px(x, y, NIGHT[1])
            elif d < 5:
                s.px(x, y, ICE[4] if (x + y) % 2 else ICE[2])                       # střepy
    for y in range(2, h - 4):                                                       # námraza v rozích tabulí
        for x in range(2, w - 2):
            if s.opaque(x, y) and (min(x - 2, w - 3 - x) < 2 or y > h - 9) and (x + y) % 2 == 0:
                s.px(x, y, ICE[4])
    for x in range(w):
        s.px(x, h - 4, SNOW[4]); s.px(x, h - 3, SNOW[3]); s.px(x, h - 2, METAL[2]); s.px(x, h - 1, METAL[0])
    return selout(s, 0.55, 0.85)


def cryo_pod(seed=0, occupied=True):
    """Hibernační komora ve 3/4: kovový podstavec s kontrolkou, prosklená kapsle (zakulacený vršek), sklo pod
    námrazou, za ním mlhavá silueta spáče, hadice do podstavce, na čele štítek s číslem okruhu."""
    W, H = 26, 52
    s = Spr(W + 2, H)
    rng = np.random.default_rng(seed)
    for y in range(H - 12, H):                                                      # podstavec
        for x in range(1, W - 1):
            s.px(x, y, METAL[3] if y == H - 12 else METAL[2] if x < W - 4 else METAL[1])
        for x in range(W - 1, W + 2):
            if y - (H - 12) >= x - (W - 1):
                s.px(x, y, METAL[0])
    for x in range(8, 18):
        s.px(x, H - 8, PAPER[3]); s.px(x, H - 7, PAPER[2])
    s.px(10, H - 8, INK); s.px(12, H - 8, INK); s.px(15, H - 7, INK)
    s.px(W - 5, H - 9, c(0x5AE0A0) if occupied else c(0xE0503A))
    for y in range(2, H - 12):                                                      # kapsle (sklo)
        t = (y - 2) / (H - 14)
        hw = (W / 2 - 3) * (math.sqrt(max(0.0, 1 - ((1 - min(1.0, t * 3.5)) ** 2))) if t < 0.3 else 1.0)
        for x in range(int(W / 2 - hw), int(W / 2 + hw) + 1):
            nx = (x - W / 2) / max(1, hw)
            col = ICE[3] if nx < -0.4 else ICE[2] if nx < 0.5 else ICE[1]
            s.px(x, y, col)
    if occupied:                                                                    # mlhavá silueta spáče
        for y in range(9, H - 14):
            for x in range(6, W - 6):
                d1 = ((x - W / 2) / 3.2) ** 2 + ((y - 13) / 3.4) ** 2
                d2 = ((x - W / 2) / 5.2) ** 2 + ((y - 27) / 11) ** 2
                if (d1 < 1 or d2 < 1) and (x + y) % 2 == 0:
                    shade_px(s, x, y, 0.72)
                elif d1 < 1 or d2 < 1:
                    shade_px(s, x, y, 0.82)
    for _ in range(70):                                                             # námraza na skle
        x, y = int(rng.integers(3, W - 3)), int(rng.integers(3, H - 13))
        if s.opaque(x, y) and (y > H - 24 or x < 7 or x > W - 8 or rng.random() < 0.25):
            s.px(x, y, ICE[5] if rng.random() < 0.5 else ICE[4])
    for y in range(6, H - 18):                                                      # odlesk
        s.px(7, y, ICE[5])
    for x in range(3, W - 3):
        s.px(x, H - 13, METAL[4]); s.px(x, H - 12, METAL[2])                         # obruč
    for k in range(10):                                                             # hadice
        s.px(W - 2 + k // 5, H - 22 + k, METAL[1])
    return selout(s, 0.5, 0.82)


def snowdrift(seed=0, w=40, h=22):
    """Závěj (místo setkání): kupa sněhu z několika boulí, světlo zleva shora, modravý stín pod převisem
    a vpravo dole, rozfoukaný okraj do stran, třpytky."""
    s = Spr(w, h)
    rng = np.random.default_rng(seed)
    bumps = [(w * 0.5, h - 9, 11), (w * 0.28 + rng.uniform(-2, 2), h - 7, 8), (w * 0.72 + rng.uniform(-2, 2), h - 6, 7.5),
             (w * 0.42 + rng.uniform(-2, 2), h - 12, 6)]
    blobs(s, bumps, SNOW, amb=0.25)
    for y in range(h - 5, h):                                                       # rozfoukaný okraj
        k = (y - (h - 5)) / 5
        for x in range(int(2 + (1 - k) * 4), int(w - 2 - (1 - k) * 4)):
            if not s.opaque(x, y):
                s.px(x, y, SNOW[2] if (x + y) % 2 else SNOW[1])
    for x in range(w):                                                              # stín u paty
        for y in range(h - 2, h):
            if s.opaque(x, y) and x > w * 0.35:
                s.px(x, y, SNOW[0] if y == h - 1 else SNOW[1])
    for _ in range(12):
        x, y = int(rng.integers(3, w - 3)), int(rng.integers(3, h - 6))
        if s.opaque(x, y) and s.opaque(x, y - 1):
            s.px(x, y, c(0xFFFFFF))
    return selout(s, 0.62, 0.9)


def ice_block():
    """Blok ledu ve 3/4 (horní plocha, čelo, bok), uvnitř tmavý obrys uvězněného tvora (čtyři nohy, ocas, hlava),
    praskliny a bubliny v ledu."""
    W, H = 34, 34
    s = Spr(W + 4, H)
    for y in range(0, 8):
        for x in range(W):
            s.px(x, y, ICE[4] if y < 2 else ICE[3])
    for y in range(8, H):
        for x in range(W):
            s.px(x, y, ICE[2] if x > 2 else ICE[3])
        for x in range(W, W + 4):
            if y - 8 >= (x - W) - 4:
                s.px(x, y, ICE[1])
    for y in range(12, 30):                                                         # tvor uvnitř
        for x in range(4, 30):
            body = ((x - 16) / 8) ** 2 + ((y - 20) / 4.5) ** 2 < 1
            head = ((x - 25) / 3.5) ** 2 + ((y - 16) / 3.5) ** 2 < 1
            legs = y > 22 and any(abs(x - lx) < 1.2 for lx in (10, 13, 19, 22)) and y < 28
            tail = abs(y - (18 - (8 - x) * 0.6)) < 1 and 3 < x < 9
            if body or head or legs or tail:
                shade_px(s, x, y, 0.55)
    s.px(26, 15, c(0xF4F070))                                                       # oko (svítí)
    for _ in range(14):
        x, y = int(_h(_, 1) % (W - 4)) + 2, int(_h(_, 2) % (H - 11)) + 9
        s.px(x, y, ICE[4])
    for k in range(9):
        s.px(6 + k, 10 + k // 2, ICE[5])
    return selout(s, 0.55, 0.85)


def thermometer():
    """Nástěnný teploměr: smaltovaná deska, skleněná trubička, rtuť dole u nuly, stupnice s rysky."""
    s = Spr(10, 30)
    for y in range(30):
        for x in range(10):
            e = min(x, y, 9 - x, 29 - y)
            s.px(x, y, c(0xE8E2D2) if e > 0 else c(0x8A8678))
    for y in range(3, 25):
        s.px(4, y, ICE[4]); s.px(5, y, ICE[3])
    sphere(s, 4.5, 25, 2, 2, [RED[0], RED[1], RED[2], RED[3]], amb=0.4)
    for y in range(21, 25):
        s.px(4, y, RED[3]); s.px(5, y, RED[2])
    for y in range(4, 24, 3):
        s.px(7, y, INK)
    return s


def frosted(spr, seed=0, k=0.35):
    """Námraza na hotovém předmětu: sníh na horních hranách, jinovatka (světlé tečky) a krátké rampouchy
    pod vodorovnými hranami. Vrací novou kopii."""
    s = Spr(spr.w, spr.h + 4)
    s.a[:spr.h] = spr.a
    rng = np.random.default_rng(seed)
    op = s.a[:, :, 3] > 0
    for y in range(s.h):
        for x in range(s.w):
            if not op[y, x]:
                continue
            top = y == 0 or not op[y - 1, x]
            if top:
                s.px(x, y, SNOW[4])
                if y + 1 < s.h and op[y + 1, x]:
                    s.px(x, y + 1, SNOW[3] if (x + y) % 2 else SNOW[2])
            elif rng.random() < k * 0.25:
                s.px(x, y, ICE[4] if rng.random() < 0.6 else ICE[5])
            bottom = y + 1 >= s.h or not op[y + 1, x]
            if bottom and y < spr.h - 3 and rng.random() < 0.3:
                for d in range(1, int(rng.integers(2, 5))):
                    if y + d < s.h:
                        s.px(x, y + d, ICE[4] if d == 1 else ICE[3])
    return s


def snow_pile(w=30, h=12, seed=1):
    """Navátý sníh pod rozbitým oknem: nízký jazyk sněhu, který se táhne od stěny do místnosti."""
    s = Spr(w, h)
    rng = np.random.default_rng(seed)
    for x in range(w):
        top = int(h * (0.15 + 0.7 * (x / w) ** 1.4) + rng.uniform(-1, 1))
        for y in range(top, h):
            t = (y - top) / max(1, h - top)
            s.px(x, y, SNOW[4] if t < 0.3 else SNOW[3] if t < 0.7 else SNOW[2])
    return selout(s, 0.7, 0.92)

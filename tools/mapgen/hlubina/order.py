"""Zóna 3 – stavby a technika Řádu Váhy (obsazení Hlubiny)."""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "ashplain")); sys.path.insert(0, os.path.join(HERE, "..", "tower")); sys.path.insert(0, HERE)
from common import *  # noqa: E402,F401,F403
from common import _shade, _pick, _h, _vn  # noqa: E402,F401
from signs import plate, painted, tiny  # noqa: E402


def shaft_lift(w=46, h=110):
    """Ocelová konstrukce přístupové šachty, která sjíždí ze stropu: příhradové sloupy, výztuhy do X, dole
    přistávací rám klece s odemčeným rudým zámkem, žluto-černé pruhy a lampa nad vstupem."""
    s = Spr(w, h)
    for y in range(h):
        for x in (0, 1, 2, w - 3, w - 2, w - 1):                          # sloupy
            s.px(x, y, STEEL[[1, 3, 2][x % 3] if x < 3 else [2, 1, 0][(x - (w - 3)) % 3]])
    for y0 in range(0, h - 46, 14):                                       # výztuhy do X
        s.line(3, y0, w - 4, y0 + 14, STEEL[1]); s.line(w - 4, y0, 3, y0 + 14, STEEL[1])
        s.hline(3, w - 4, y0, STEEL[2])
    cy0 = h - 46                                                          # klec
    for y in range(cy0, h - 4):
        for x in range(4, w - 4):
            inside = 6 < x < w - 7 and cy0 + 4 < y < h - 6
            if inside:
                s.px(x, y, c(0x0C0E12) if (x - 6) % 5 else STEEL[2])
            else:
                s.px(x, y, STEEL[3] if y == cy0 else STEEL[2])
    for x in range(10, w - 10):                                           # otevřená dvířka
        for y in range(cy0 + 6, h - 6):
            if x > w // 2 + 2:
                s.px(x, y, c(0x1A1E26) if (x % 3) else STEEL[1])
    for y in range(h - 4, h):                                             # práh se žlutočernými pruhy
        for x in range(w):
            s.px(x, y, YELLOW[2] if ((x + y) // 3) % 2 else c(0x14141A))
    s.rect(9, cy0 + 20, 11, cy0 + 22, RED[3])                             # rudý zámek – odemčený, visí
    s.px(10, cy0 + 19, STEEL[3]); s.px(12, cy0 + 18, STEEL[3])
    s.rect(w // 2 - 3, cy0 - 4, w // 2 + 3, cy0 - 2, STEEL[2])            # lampa nad vstupem
    s.rect(w // 2 - 2, cy0 - 1, w // 2 + 2, cy0, SODIUM)
    return selout(s, 0.55, 0.85)


# ── budovy a technika Řádu ──────────────────────────────────────────────────
def barrack(w=56, h=40, seed=0):
    """Plechová ubikace (kontejner) ve 3/4: zvlněný plech, dveře s pečetí vah, okno se světlem (někdo nechal
    lampu), levý roh zespodu prolamuje bledý kořen (navazuje na kořen v mapě)."""
    s = Spr(w, h)
    roof_h = 9
    for y in range(roof_h):                                               # střecha shora
        for x in range(1, w - 1):
            _shade(s, x, y, 0.62 - y * 0.02 + (0.06 if x % 4 == 0 else 0), OLIVE)
    for y in range(roof_h, h):                                            # čelo – zvlněný plech
        for x in range(1, w - 1):
            v = 0.42 + 0.14 * math.cos(x * 1.6) - (y - roof_h) * 0.006
            if (_h(x, y, seed) % 31 == 0) or (y > h - 8 and (x + y) % 5 == 0):
                v -= 0.2                                                  # rez a špína dole
            _shade(s, x, y, v, OLIVE)
        s.px(1, y, OLIVE[1]); s.px(w - 2, y, OLIVE[0])
    s.hline(1, w - 2, roof_h, OLIVE[0])
    dx0 = 8                                                               # dveře
    for y in range(roof_h + 6, h - 1):
        for x in range(dx0, dx0 + 12):
            s.px(x, y, OLIVE[3] if x in (dx0, dx0 + 11) or y == roof_h + 6 else OLIVE[2])
    s.px(dx0 + 9, roof_h + 18, BRASS[3])                                  # klika
    for k in range(5):                                                    # pečeť vah na dveřích
        s.px(dx0 + 4 + k, roof_h + 11, PAPER[2])
    s.px(dx0 + 6, roof_h + 10, PAPER[2]); s.px(dx0 + 4, roof_h + 12, PAPER[2]); s.px(dx0 + 8, roof_h + 12, PAPER[2])
    for y in range(roof_h + 7, roof_h + 17):                              # okno se světlem
        for x in range(28, 44):
            edge = y in (roof_h + 7, roof_h + 16) or x in (28, 43) or x == 36
            s.px(x, y, OLIVE[4] if edge else (SODIUM if (y - roof_h) < 12 else c(0xC8904A)))
    pl = plate("S-8", c(0xE8E2CC), c(0x2A3424), seed=2, pad=2)
    for yy in range(pl.h):
        for xx in range(pl.w):
            s.px(28 + xx, roof_h + 19 + yy, pl.a[yy, xx])
    for y in range(h - 14, h):                                            # kořen prolamuje levý roh zespodu
        for x in range(1, 15):
            d = (14 - x) - (h - y) * 1.2
            if 0 < d < 6:
                _shade(s, x, y, 0.75 - abs(d - 3) * 0.15, BARK)
    return selout(s, 0.5, 0.8)


def radio_mast(h=44):
    """Anténní stožár s kotevními lany a blikající červenou lampičkou nahoře."""
    s = Spr(24, h)
    for y in range(2, h):
        s.px(12, y, STEEL[3] if y % 6 else STEEL[2]); s.px(13, y, STEEL[1])
    for y in range(4, h, 6):
        s.px(11, y, STEEL[2]); s.px(14, y, STEEL[2])
    s.line(12, 6, 1, h - 1, STEEL[1]); s.line(13, 6, 23, h - 1, STEEL[1])
    s.rect(11, 0, 13, 1, RED[4])
    return s


def obs_tower(w=40, h=104, seed=0):
    """Pozorovatelna: příhradová ocelová věž s žebříkem, nahoře plechová kabina s průzorem a reflektorem
    namířeným dolů do tmy; kolem jedné nohy se ovíjí kořen."""
    s = Spr(w, h)
    cab_h = 26
    lx0, lx1, rx0, rx1 = 4, 12, w - 13, w - 5                             # nohy se k vrchu sbíhají
    for y in range(cab_h, h):
        t = (y - cab_h) / (h - cab_h)
        xl = int(lx1 - (lx1 - lx0) * t); xr = int(rx0 + (rx1 - rx0) * t)
        s.px(xl, y, STEEL[3]); s.px(xl + 1, y, STEEL[1]); s.px(xr, y, STEEL[2]); s.px(xr + 1, y, STEEL[0])
        if (y - cab_h) % 16 == 0:
            s.hline(xl, xr, y, STEEL[2])
    for y0 in range(cab_h, h - 16, 16):
        t0, t1 = (y0 - cab_h) / (h - cab_h), (y0 + 16 - cab_h) / (h - cab_h)
        a, b = int(lx1 - (lx1 - lx0) * t0), int(rx0 + (rx1 - rx0) * t0)
        a2, b2 = int(lx1 - (lx1 - lx0) * t1), int(rx0 + (rx1 - rx0) * t1)
        s.line(a, y0, b2, y0 + 16, STEEL[1]); s.line(b, y0, a2, y0 + 16, STEEL[1])
    for y in range(cab_h, h):                                             # žebřík uprostřed
        s.px(w // 2 - 3, y, STEEL[2]); s.px(w // 2 + 2, y, STEEL[2])
        if y % 4 == 0:
            s.hline(w // 2 - 3, w // 2 + 2, y, STEEL[3])
    for y in range(0, cab_h):                                             # kabina
        for x in range(2, w - 2):
            if y < 5:
                v = 0.65 if y < 2 else 0.4                                # střecha s přesahem
            else:
                v = 0.45 + 0.12 * math.cos(x * 1.6)
                if 9 <= y <= 15 and 6 <= x <= w - 7:
                    v = -1                                                # průzor
            if v < 0:
                s.px(x, y, c(0x10141C) if (x + y) % 7 else c(0x2A3446))
            else:
                _shade(s, x, y, v, OLIVE)
    pl = plate("A", c(0x1A1A1A), c(0xD8B030), seed=4, pad=2, rivets=False)
    for yy in range(pl.h):
        for xx in range(pl.w):
            s.px(w // 2 - pl.w // 2 + xx, 16 + yy, pl.a[yy, xx])
    s.rect(w - 4, 8, w - 1, 12, STEEL[3]); s.rect(w - 2, 13, w - 1, 14, SODIUM)   # reflektor
    for y in range(h - 30, h):                                            # kořen kolem levé nohy
        t = (y - cab_h) / (h - cab_h)
        xl = int(lx1 - (lx1 - lx0) * t)
        for k in range(-2, 4):
            if (y + k * 3) % 9 < 5:
                _shade(s, xl + k, y, 0.7 - abs(k) * 0.1, BARK)
    return selout(s, 0.5, 0.82)


def flood_mast(h=56):
    """Stožár se dvěma sodíkovými reflektory a kabelem ke generátoru."""
    s = Spr(20, h)
    for y in range(8, h):
        s.px(9, y, STEEL[3]); s.px(10, y, STEEL[1])
    s.hline(2, 17, 8, STEEL[2])
    for (x0,) in ((1,), (12,)):
        s.rect(x0, 2, x0 + 6, 7, STEEL[2]); s.rect(x0 + 1, 6, x0 + 5, 7, SODIUM)
    s.rect(6, h - 3, 13, h - 1, CONC[3])
    return selout(s, 0.55, 0.85)


def sandbags(w=40, rows=2):
    """Zídka z pytlů s pískem (polštářky ve vazbě)."""
    s = Spr(w, 6 * rows + 3)
    for r in range(rows):
        off = 0 if r % 2 else 5
        for bx in range(-off, w, 10):
            sphere(s, bx + 5, s.h - 4 - r * 6, 5.2, 3.6, SAND, amb=0.25)
    return selout(s, 0.45, 0.75)


def barrier(w=64):
    """Závora: žlutočerné rameno na betonových patkách a nad ním plechová cedule UZÁVĚRA s rudým lemem."""
    pl = plate("UZÁVĚRA", c(0xB8261E), c(0xE6E0CC), seed=9, pad=3, border=c(0xB8261E))
    H = pl.h + 18
    s = Spr(w, H)
    for x in range(4, w - 4):                                              # rameno
        for y in range(pl.h + 4, pl.h + 7):
            s.px(x, y, YELLOW[2] if (x // 4) % 2 else c(0x16161C))
        s.px(x, pl.h + 7, c(0x0A0A0E))
    for (x0,) in ((0,), (w - 9,)):                                         # patky
        for y in range(pl.h + 6, H):
            for x in range(x0, x0 + 9):
                s.px(x, y, CONC[4] if y == pl.h + 6 else CONC[3] if x < x0 + 6 else CONC[1])
    for y in range(pl.h, pl.h + 5):                                        # nosníky cedule
        s.px(w // 2 - 12, y, STEEL[2]); s.px(w // 2 + 12, y, STEEL[2])
    for yy in range(pl.h):
        for xx in range(pl.w):
            s.px(w // 2 - pl.w // 2 + xx, yy, pl.a[yy, xx])
    return selout(s, 0.5, 0.85)


def fuel_drums(n=3):
    """Sudy ohňové hlídky (rudé, s bílým pruhem) a tlakové lahve."""
    s = Spr(10 * n + 2, 16)
    for i in range(n):
        x0 = 1 + i * 10
        cylinder(s, x0, x0 + 8, 3, 15, RED[1:], amb=0.25)
        sphere(s, x0 + 4, 3, 4.4, 1.8, RED[2:], amb=0.4)
        s.hline(x0, x0 + 8, 9, c(0xD8D2BE))
    return selout(s, 0.5, 0.8)


def burnt_stump(w=18, h=22, seed=0):
    """Ohořelý pahýl kořene, který hlídka spálila: černé dřevo s rozpraskaným uhlím a žhavými spárami."""
    s = Spr(w, h)
    for y in range(h):
        for x in range(w):
            hw = (w / 2 - 1) * (0.6 + 0.4 * (y / h)) + (_vn(x, y, seed, 3) - 0.5) * 3
            if abs(x - w / 2) < hw and y > (_h(x, seed, 2) % 6):
                v = 0.5 - (x - w / 2) / w * 0.6 + (_vn(x * 2, y, seed + 1, 2) - 0.5) * 0.4
                _shade(s, x, y, v, CHAR)
                if _h(x, y, seed + 3) % 23 == 0:
                    s.px(x, y, c(0xE06020))
    return selout(s, 0.6, 0.9)


def notice_board():
    """Nástěnka na kůlech: rozpis výprav, přeškrtnuté řádky, čerstvý list nahoře."""
    s = Spr(30, 30)
    W = [c(0x24160C), c(0x3A2414), c(0x54361E), c(0x6E4A2A)]
    s.rect(0, 0, 29, 19, W[1]); s.rect(1, 1, 28, 18, W[2])
    for (x0, y0, w, h) in ((3, 3, 7, 12), (12, 2, 7, 9), (21, 3, 6, 13), (12, 12, 8, 5)):
        s.rect(x0, y0, x0 + w, y0 + h, PAPER[2])
        for yy in range(y0 + 2, y0 + h, 2):
            s.hline(x0 + 1, x0 + w - 1, yy, PAPER[0])
    for yy in range(5, 14, 2):
        s.hline(3, 10, yy, RED[3])                                        # NEVRÁCENA – přeškrtnuto
    s.rect(3, 20, 4, 29, W[1]); s.rect(25, 20, 26, 29, W[1])
    return selout(s, 0.5, 0.8)


def cable_station(w=50, h=36):
    """Spodní terminál lanovky: betonová rampa, velké vratné kolo, dvě lana mizí přes okraj do tmy, u rampy
    visí kabina gondoly s rozbitým okénkem. Cedule LANOVKA > KMEN."""
    s = Spr(w, h)
    for y in range(12, h):
        for x in range(w):
            _shade(s, x, y, 0.5 - (y - 12) * 0.01 + (0.1 if y == 12 else 0), CONC)
    cx, cy, r = 16, 10, 9                                                 # vratné kolo
    for a in range(0, 360, 3):
        x = cx + math.cos(math.radians(a)) * r; y = cy + math.sin(math.radians(a)) * r * 0.45
        s.px(x, y, STEEL[3])
    for a in range(0, 360, 45):
        s.line(cx, cy, cx + math.cos(math.radians(a)) * r, cy + math.sin(math.radians(a)) * r * 0.45, STEEL[1])
    s.rect(cx - 1, cy, cx + 1, 18, STEEL[2])
    for x in range(w // 2, w):                                            # gondola
        for y in range(16, 32):
            gx = x - w // 2
            if 2 < gx < w // 2 - 2:
                v = 0.5 + 0.1 * math.cos(gx * 0.8)
                if 19 < y < 25 and 5 < gx < w // 2 - 5:
                    v = -1
                s.px(x, y, (c(0x0C0E14) if (x * y) % 5 else c(0x3A4456)) if v < 0 else _pick(YELLOW, v, x, y))
    s.rect(w // 2 + w // 4 - 1, 8, w // 2 + w // 4, 16, STEEL[2])
    return selout(s, 0.5, 0.8)


def army_crate(seed=0, w=18, h=16):
    """Vojenská bedna Řádu (olivové prkna, kování, nastříkané S-8 a pečeť)."""
    s = Spr(w, h)
    top = 5
    for y in range(h):
        for x in range(w):
            if y < top:
                v = 0.62 if (x // 6) % 2 else 0.55
            else:
                v = 0.42 - (y - top) * 0.01 + (0.06 if (y - top) % 4 == 0 else 0)
            if x in (0, w - 1) or y in (top,):
                v = 0.22
            _shade(s, x, y, v, OLIVE)
    for y in range(top + 1, h - 1):
        s.px(3, y, RUST[2]); s.px(w - 4, y, RUST[2])
    tiny(s, 5, top + 4, "S8", c(0xCFC8B0))
    return selout(s, 0.5, 0.8)



# ── Pozorovatelna B (pohlcení 2/8) ──────────────────────────────────────────
def lean(s, tilt):
    """Nakloní sprite: každý řádek posune o tilt·(výška − y) px (pata stojí, vrchol uhýbá)."""
    out = Spr(s.w + int(abs(tilt) * s.h) + 2, s.h)
    for y in range(s.h):
        dx = int(round(tilt * (s.h - 1 - y))) + (int(abs(tilt) * s.h) + 1 if tilt < 0 else 0)
        for x in range(s.w):
            if s.a[y, x, 3]:
                out.a[y, x + dx] = s.a[y, x]
    return out


def leaning_tower(w=40, h=112):
    """Pozorovatelna B: stejná věž jako A, ale nakloněná, reflektor rozbitý, okny kabiny prorůstají kořeny
    a po nohách šplhají; žlutý štítek B."""
    s = obs_tower(w, h)
    for y in range(9, 16):                                                # rozbitý průzor + kořeny ven
        for x in range(6, w - 6):
            if (x * 7 + y * 3) % 11 < 3:
                s.px(x, y, c(0x1A1C22))
    for (x0, dirx) in ((8, -1), (w - 10, 1)):
        for k in range(14):
            for t in range(3):
                _shade(s, x0 + dirx * k // 2 + t, 12 + k, 0.7 - t * 0.15, BARK)
    for y in range(30, h):                                                # popínavé kořeny po obou nohách
        for (cx, ph) in ((10 - (y - 30) * 6 // (h - 30), 0), (w - 11 + (y - 30) * 6 // (h - 30), 2)):
            k = int(2.5 * math.sin(y / 5 + ph))
            for t in range(-1, 2):
                _shade(s, cx + k + t, y, 0.72 - abs(t) * 0.2, BARK)
    pl = plate("B", c(0x1A1A1A), c(0xD8B030), seed=11, pad=2, rivets=False)
    for yy in range(pl.h):
        for xx in range(pl.w):
            s.px(w // 2 - pl.w // 2 + xx, 16 + yy, pl.a[yy, xx])
    s.rect(w - 4, 8, w - 1, 12, STEEL[1]); s.px(w - 2, 13, c(0x2A2A2A))        # rozbitý reflektor
    return lean(selout(s, 0.5, 0.82), 0.09)


def fence(w, h=22, seed=0, gap=None):
    """Plot z drátěného pletiva na sloupcích (pletivo průsvitné – kosočtverce), nahoře ostnatý drát.
    gap = (x0, x1) úsek, který kořen zmáčkl k zemi."""
    s = Spr(w, h)
    for x in range(0, w, 14):
        for y in range(2, h):
            s.px(x, y, STEEL[3]); s.px(x + 1, y, STEEL[1])
    for y in range(4, h - 1):
        for x in range(w):
            if gap and gap[0] <= x <= gap[1]:
                continue
            if (x + y) % 6 == 0 or (x - y) % 6 == 0:
                s.px(x, y, STEEL[2] if (x + y) % 12 else STEEL[3])
    for x in range(w):
        if not (gap and gap[0] <= x <= gap[1]):
            s.px(x, 2, STEEL[2]); s.px(x, 3 + (x % 3 == 0), STEEL[1])
    if gap:                                                                # zmáčknuté pletivo u země
        for x in range(gap[0], gap[1]):
            for y in range(h - 5, h):
                if (x + y) % 4 == 0:
                    s.px(x, y, STEEL[1])
    return s


def gen_shed(seed=0):
    """Bouda s generátorem: malý plechový kontejner, vedle naftový agregát s výfukem a kabely, střechou
    prorůstá kořen."""
    s = Spr(52, 34)
    for y in range(8):
        for x in range(1, 33):
            _shade(s, x, y, 0.6 - y * 0.02, OLIVE)
    for y in range(8, 34):
        for x in range(1, 33):
            _shade(s, x, y, 0.4 + 0.12 * math.cos(x * 1.6) - (0.15 if (_h(x, y, seed) % 23 == 0) else 0), OLIVE)
    for y in range(13, 33):
        for x in range(5, 14):
            s.px(x, y, OLIVE[3] if x in (5, 13) or y == 13 else OLIVE[1])
    for y in range(16, 30):                                                # agregát
        for x in range(34, 51):
            _shade(s, x, y, 0.55 - (y - 16) * 0.015 + (0.12 if y == 16 else 0), YELLOW)
    for x in range(36, 49, 3):
        for y in range(19, 27):
            s.px(x, y, YELLOW[0])
    for y in range(6, 16):
        s.px(47, y, STEEL[1]); s.px(48, y, STEEL[2])                        # výfuk
    for y in range(0, 12):                                                 # kořen přes střechu
        for k in range(-2, 3):
            _shade(s, 18 + y // 2 + k, y, 0.7 - abs(k) * 0.12, BARK)
    tiny(s, 15, 20, "ZDROJ", c(0xD8D2BE))
    return selout(s, 0.5, 0.8)


def flamer_rack():
    """Stojan ohňové hlídky: dva plamenomety (rudé tlakové lahve se zátěží), hadice, opuštěné."""
    s = Spr(26, 26)
    for x in range(1, 25):
        s.px(x, 4, STEEL[3]); s.px(x, 20, STEEL[2])
    for x in (2, 23):
        for y in range(4, 26):
            s.px(x, y, STEEL[2])
    for (x0,) in ((5,), (14,)):
        cylinder(s, x0, x0 + 6, 6, 19, RED[1:], amb=0.25)
        sphere(s, x0 + 3, 6, 3.4, 1.5, RED[2:], amb=0.4)
        s.rect(x0 + 2, 3, x0 + 4, 5, STEEL[3])
    s.line(8, 19, 12, 25, c(0x1A1A1A)); s.line(17, 19, 22, 25, c(0x1A1A1A))   # hadice
    return selout(s, 0.5, 0.8)


def helmet():
    s = Spr(10, 7)
    sphere(s, 5, 5, 4.6, 3.8, OLIVE[1:], amb=0.25)
    s.hline(0, 9, 6, OLIVE[0])
    return selout(s, 0.5, 0.8)


def check_post():
    """Sloupek KONTROLA KOŘENŮ: cedule a pod ní tabulka s čárkami po dnech (každý den kořen blíž)."""
    pl = plate("KONTROLA", c(0xE8E2CC), c(0x1E2A44), seed=13, pad=2)
    s = Spr(pl.w, pl.h + 26)
    s.a[:pl.h] = pl.a
    for y in range(pl.h, pl.h + 26):
        s.px(pl.w // 2, y, STEEL[3]); s.px(pl.w // 2 + 1, y, STEEL[1])
    for y in range(pl.h + 2, pl.h + 13):                                   # tabulka s čárkami
        for x in range(4, pl.w - 4):
            s.px(x, y, PAPER[2] if (y - pl.h) % 3 else PAPER[1])
    for i in range(14):
        s.px(6 + i * 3, pl.h + 4 + (i % 3) * 3, RED[3])
    return selout(s, 0.5, 0.85)


def cable_pylon(h=70):
    """Příhradový stožár lanovky (k rokli): vršek s kladkami, lana odcházejí vpravo."""
    s = Spr(28, h)
    for y in range(10, h):
        t = (y - 10) / (h - 10)
        a, b = int(11 - 8 * t), int(16 + 8 * t)
        s.px(a, y, STEEL[3]); s.px(b, y, STEEL[1])
        if (y - 10) % 10 == 0:
            s.hline(a, b, y, STEEL[2])
    for y0 in range(10, h - 10, 10):
        t0, t1 = (y0 - 10) / (h - 10), (y0 - 0) / (h - 10)
        s.line(int(11 - 8 * t0), y0, int(16 + 8 * t1), y0 + 10, STEEL[1])
    s.rect(2, 6, 25, 9, STEEL[2]); s.hline(2, 25, 6, STEEL[3])
    for x in (5, 22):
        sphere(s, x, 5, 2.2, 2.2, STEEL[1:], amb=0.3)
    return selout(s, 0.5, 0.85)


# ── Pozorovatelna B – bunkr ve skále (verze 2) ──────────────────────────────
def mount_plate(pl, posts=0, post_col=None):
    """Cedule na sloupcích (posts = výška sloupků). Bez sloupků je to cedule na zeď – vrací se i se stínem pod sebou."""
    if posts <= 0:
        s = Spr(pl.w + 1, pl.h + 1)
        for y in range(pl.h):                                              # vržený stín na zeď (o 1 px vpravo dolů)
            for x in range(pl.w):
                if pl.a[y, x, 3]:
                    s.px(x + 1, y + 1, c(0x08080C))
        for y in range(pl.h):
            for x in range(pl.w):
                if pl.a[y, x, 3]:
                    s.px(x, y, pl.a[y, x])
        return s
    s = Spr(pl.w, pl.h + posts)
    s.a[:pl.h] = pl.a
    col = post_col or STEEL
    for y in range(pl.h, pl.h + posts):
        for x0 in (3, pl.w - 5):
            s.px(x0, y, col[3]); s.px(x0 + 1, y, col[1])
    return s


def bunker_facade(W, H, x0, door_x, seed=0):
    """Betonové čelo bunkru zapuštěné do skály (vrací masku a Spr přes celou šířku stěny): panely s bedněním,
    světlejší hrana nahoře, rezavé stékance od kotev, ocelové dveře s pečetí a dvě střílny se světlem."""
    s = Spr(W, H)
    top = 26
    for y in range(top, H):
        for x in range(x0, W):
            v = 0.5 + (_vn(x, y, seed + 41, 5) - 0.5) * 0.18 - (y - top) * 0.002
            if (y - top) % 18 == 0 or (x - x0) % 30 == 0:
                v = 0.25                                                  # spáry bednění
            elif (y - top) % 18 == 1:
                v += 0.1
            if y < top + 3:
                v = 0.72 - (y - top) * 0.12                              # horní hrana (atika)
            if (x - x0) % 30 in (14, 15) and (y - top) % 18 in (6, 7):
                v = 0.15                                                  # kotvy
            s.px(x, y, _pick(CONC, max(0.0, min(1.0, v)), x, y))
            if (x - x0) % 30 == 15 and 8 < (y - top) % 18 < 8 + _h(x, y // 18, seed) % 9:
                s.px(x, y, RUST[2])                                       # rez od kotev
    for y in range(top, H):                                               # boční hrana do skály
        s.px(x0, y, CONC[0]); s.px(x0 + 1, y, CONC[5])
    dw, dh = 22, 34                                                       # dveře
    for y in range(H - dh, H):
        for x in range(door_x, door_x + dw):
            edge = x in (door_x, door_x + dw - 1) or y == H - dh
            v = 0.62 if edge else 0.42 + 0.06 * ((x - door_x) // 4 % 2)
            if x == door_x + dw // 2:
                v = 0.2
            s.px(x, y, _pick(STEEL, v, x, y))
    for (dx, dy) in ((0, 0), (1, 0), (2, 0), (3, 0), (4, 0), (1, -1), (3, -1), (2, -2), (0, 1), (4, 1)):
        s.px(door_x + dw // 2 - 2 + dx, H - dh + 9 + dy, BRASS[3])          # pečeť vah
    for k in range(3):
        s.px(door_x + dw - 5, H - 16 + k, BRASS[2])                         # klika
    for (sx0, sy0) in ((door_x - 36, H - 26), (door_x + dw + 16, H - 26)):  # střílny se světlem
        for y in range(sy0, sy0 + 5):
            for x in range(sx0, sx0 + 20):
                s.px(x, y, SODIUM if y > sy0 and 0 < x - sx0 < 19 else CONC[0])
        for x in range(sx0 - 1, sx0 + 21):
            s.px(x, sy0 - 1, CONC[5]); s.px(x, sy0 + 5, CONC[1])
    return s


def railing(w, h=12, bend=None, gaps=()):
    """Zábradlí na okraji terasy: sloupky po 12 px, madlo a středová tyč; bend = (x0, x1) úsek ohnutý kořenem
    dolů; gaps = úseky bez zábradlí (průchody)."""
    s = Spr(w, h + 4)
    def gap(x):
        return any(a <= x <= b for (a, b) in gaps)
    for x in range(w):
        if gap(x):
            continue
        dip = 0
        if bend and bend[0] <= x <= bend[1]:
            q = (x - bend[0]) / max(1, bend[1] - bend[0])
            dip = int(math.sin(q * math.pi) * 6)
        s.px(x, 1 + dip, STEEL[4]); s.px(x, 2 + dip, STEEL[2])            # madlo
        s.px(x, 7 + dip // 2, STEEL[2])                                   # středová tyč
        if x % 12 == 0:
            for y in range(1 + dip, h + 2):
                s.px(x, y, STEEL[3]); s.px(x + 1, y, STEEL[1])
    return s


def searchlight():
    """Velký světlomet na otočném podstavci: buben s mřížkou přes čočku, třmen, točna; natočený dolů ke kmeni."""
    s = Spr(36, 38)
    for y in range(28, 38):                                               # točna
        for x in range(4, 32):
            if ((x - 18) / 14) ** 2 + ((y - 33) / 5) ** 2 < 1:
                s.px(x, y, _pick(STEEL, 0.55 - (y - 28) * 0.03, x, y))
    for y in range(14, 30):                                               # třmen
        for x in (6, 7, 28, 29):
            s.px(x, y, STEEL[3] if x in (6, 28) else STEEL[1])
    cylinder(s, 8, 27, 6, 24, [c(0x1C2228), c(0x34404A), c(0x52626E), c(0x7A8C98), c(0xA8B8C2)], amb=0.25)
    for y in range(10, 22):                                               # čočka (tmavá, rozbitá mřížka)
        for x in range(11, 25):
            if ((x - 18) / 7) ** 2 + ((y - 16) / 6) ** 2 < 1:
                s.px(x, y, c(0xFFE6A8) if (x + y) % 5 == 0 else c(0x6A6040))
                if x % 3 == 0:
                    s.px(x, y, STEEL[1])
    s.rect(12, 2, 23, 5, STEEL[2]); s.hline(12, 23, 2, STEEL[4])           # chladič nahoře
    return selout(s, 0.5, 0.85)


def rangefinder():
    """Dálkoměr na trojnožce: dlouhá trubice se dvěma okuláry, štítek s měřítkem."""
    s = Spr(30, 26)
    for (x0, x1) in ((15, 5), (15, 25), (15, 15)):
        s.line(x0, 12, x1, 25, STEEL[2])
    for y in range(6, 11):
        for x in range(1, 29):
            s.px(x, y, _pick(OLIVE, 0.6 - abs(y - 8) * 0.12, x, y))
    for x in (1, 28):
        sphere(s, x, 8, 2.6, 2.8, STEEL[1:], amb=0.3)
    s.rect(13, 3, 17, 6, OLIVE[2]); s.rect(14, 4, 16, 5, c(0x4A80C0))
    return selout(s, 0.5, 0.85)


def sandbag_ring(w=60, h=30):
    """Půlkruh z pytlů kolem stanoviště (otevřený dozadu)."""
    s = Spr(w, h)
    for i in range(14):
        a = math.pi * (0.05 + 0.9 * i / 13)
        x = w / 2 + math.cos(a) * (w / 2 - 5); y = h - 8 - (1 - math.sin(a)) * 0 - math.sin(a) * 0 + (h - 14) * (1 - math.sin(a)) * 0
        y = 8 + math.sin(a) * (h - 16)
        for lvl in range(2):
            sphere(s, x, y + 4 - lvl * 4, 5, 3.4, SAND, amb=0.25)
    return selout(s, 0.45, 0.75)


def field_phone():
    """Polní telefon na zdi: olivová skříňka, sluchátko na vidlici, kroucená šňůra."""
    s = Spr(12, 16)
    for y in range(2, 14):
        for x in range(1, 11):
            s.px(x, y, _pick(OLIVE, 0.55 - (y - 2) * 0.02, x, y))
    s.rect(2, 0, 9, 2, c(0x1A1A1A)); s.px(1, 1, c(0x1A1A1A)); s.px(10, 1, c(0x1A1A1A))
    for y in range(4, 15):
        s.px(5 + (y % 2), y + 1, c(0x1A1A1A)) if y > 12 else None
    s.rect(4, 6, 7, 8, c(0xD8D2BE))
    return selout(s, 0.5, 0.85)


def wall_chart(title="KONTROLA"):
    """Nástěnný graf kontrol kořenů: smaltovaný titulek nahoře, pod ním papír s křivkou, která klesá k nule."""
    pl = plate(title, c(0xE8E2CC), c(0x1E2A44), seed=13, pad=2)
    W, H = pl.w, pl.h + 22
    s = Spr(W, H)
    s.a[:pl.h] = pl.a
    for y in range(pl.h, H):
        for x in range(2, W - 2):
            s.px(x, y, PAPER[2] if (x % 6) else PAPER[1])
    pts = [(4 + i * (W - 10) // 9, pl.h + 3 + int(i * i * 0.2)) for i in range(10)]
    for (a, b) in zip(pts, pts[1:]):
        s.line(a[0], a[1], b[0], b[1], RED[3])
    s.hline(3, W - 4, H - 3, c(0x3A3428))
    return selout(s, 0.55, 0.9)

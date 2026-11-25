"""
Díly pro zónu 3 (Akt III – Hlubina), docs/adr/0106. Řád Váhy se pokusil Hlubinu obsadit: beton, plechové
ubikace, pozorovatelny, reflektory se sodíkovým světlem, ohňové hlídky, které pálí kořeny, lanovka ke kmeni.
Kořeny Spící to všechno pomalu pohlcují – čím blíž ke kmeni, tím víc. Pod tím vším starší vrstva: ruiny Lumenců
vytesané do skály, které kořeny stahují do země.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "ashplain")); sys.path.insert(0, os.path.join(HERE, "..", "tower"))
from pix import Spr, c  # noqa: E402
from figures import sphere, cylinder, selout, _pick, BRASS, STEEL, RED, BAYER  # noqa: E402
from hideout_parts import _h, _vn  # noqa: E402

ROCK = [c(0x0E0E12), c(0x17171D), c(0x212129), c(0x2C2C36), c(0x3A3A46), c(0x4C4C5A)]
CONC = [c(0x1C1E20), c(0x282B2E), c(0x34383C), c(0x42474C), c(0x52585E), c(0x666D74)]
OLIVE = [c(0x161A12), c(0x232A1C), c(0x323C28), c(0x445236), c(0x5A6A48), c(0x748660)]
RUST = [c(0x24140C), c(0x42261A), c(0x623A24), c(0x86522E)]
BARK = [c(0x24221E), c(0x3E3A34), c(0x5E584E), c(0x827A6C), c(0xA49C8A), c(0xC8C0AC), c(0xE4DECC)]
CHAR = [c(0x0C0A08), c(0x1A1612), c(0x2A241E), c(0x3C342A)]
SAND = [c(0x2E2618), c(0x4A3E28), c(0x6A5C3C), c(0x8C7C54), c(0xAC9C70)]
PAPER = [c(0x8A7E62), c(0xB4A882), c(0xD8CCA4), c(0xF0E6C4)]
SODIUM = c(0xFFC870)
GLYPH = c(0x7EC0FF)
YELLOW = [c(0x5A4410), c(0x9C7A1E), c(0xD8AC30), c(0xF4D060)]


def _shade(s, x, y, v, ramp):
    s.px(x, y, _pick(ramp, max(0.0, min(1.0, v)), x, y))


# ── skalní stěna dutiny s ruinou Lumenců a výstupem šachty ──────────────────
def cavern_wall(W, H):
    """Svislá čedičová stěna (vrstvy, praskliny), nahoře ztrácející se ve tmě stropu."""
    s = Spr(W, H)
    for y in range(H):
        for x in range(W):
            strata = math.sin(y / 5.5 + _vn(x, y, 3, 18) * 3) * 0.12
            col = _vn(x * 0.6, y * 2.4, 4, 7)
            v = 0.42 + strata + (col - 0.5) * 0.3 - (1 - y / H) * 0.25
            if abs(_vn(x * 1.3, y * 0.5, 6, 9) - 0.5) < 0.03:
                v = 0.06                                                  # svislé pukliny
            if y > H - 6:
                v -= 0.18 * (y - (H - 6)) / 6                             # pata stěny ve stínu
            _shade(s, x, y, v, ROCK)
    return s


def ruin_facade(w=54, h=70):
    """Fasáda Lumenců vytesaná do skály: lomený oblouk vchodu (zasypaný), dva štíhlé sloupy, římsa s řadou
    drobných svítících znaků (hvězdy a vlny), nad obloukem vytesaná obrácená koruna. Starší než Řád."""
    s = Spr(w, h)
    STONE = [c(0x1A1C24), c(0x2E3240), c(0x444A5A), c(0x5E6676), c(0x7E8696), c(0xA4ACBA)]
    cx = w // 2
    for y in range(h):
        for x in range(w):
            v = 0.5 + (_vn(x, y, 31, 4) - 0.5) * 0.25
            if y < 8:
                v = 0.62 if y % 4 else 0.3                               # římsa
            if y > 18 and abs(x - cx) < 14:                               # oblouk (lomený)
                top = 18 + (abs(x - cx) / 14) ** 2 * 10
                if y > top:
                    v = 0.0                                               # tma za vchodem
                elif y > top - 2:
                    v = 0.75
            for px_ in (5, w - 9):                                        # sloupy
                if px_ <= x < px_ + 4 and y >= 8:
                    v = 0.4 + (x - px_) * 0.12
            _shade(s, x, y, v, STONE)
    for i, x in enumerate(range(4, w - 4, 4)):                            # znaky na římse
        if i % 2:
            s.px(x, 3, GLYPH); s.px(x + 1, 4, GLYPH); s.px(x - 1, 4, GLYPH); s.px(x, 5, GLYPH)
        else:
            s.px(x, 4, GLYPH); s.px(x + 1, 3, GLYPH); s.px(x + 2, 4, GLYPH)
    for k in range(9):                                                    # obrácená koruna nad obloukem
        s.px(cx - 4 + k, 11, GLYPH)
        if k % 2 == 0:
            s.px(cx - 4 + k, 12, GLYPH); s.px(cx - 4 + k, 13, c(0x4A80C0))
    s.px(cx, 10, GLYPH); s.px(cx, 9, GLYPH); s.px(cx, 8, c(0x4A80C0))
    for (x, y) in ((cx - 6, h - 6), (cx + 3, h - 9), (cx - 1, h - 4)):    # sutina ve vchodu
        sphere(s, x, y, 3, 2, STONE, amb=0.25)
    return selout(s, 0.5, 0.8)


def stencil(s, x0, y0, text, col, scale=1):
    """Nastříkaný text 3×5 (velká písmena, číslice, mezera, tečka, pomlčka)."""
    font = {
        "S": ["###", "#..", "###", "..#", "###"], "T": ["###", ".#.", ".#.", ".#.", ".#."], "A": [".#.", "#.#", "###", "#.#", "#.#"],
        "N": ["#.#", "###", "###", "#.#", "#.#"], "I": ["###", ".#.", ".#.", ".#.", "###"], "C": ["###", "#..", "#..", "#..", "###"],
        "E": ["###", "#..", "##.", "#..", "###"], "8": ["###", "#.#", "###", "#.#", "###"], "H": ["#.#", "#.#", "###", "#.#", "#.#"],
        "L": ["#..", "#..", "#..", "#..", "###"], "U": ["#.#", "#.#", "#.#", "#.#", "###"], "B": ["##.", "#.#", "##.", "#.#", "##."],
        "Z": ["###", "..#", ".#.", "#..", "###"], "V": ["#.#", "#.#", "#.#", "#.#", ".#."], "R": ["##.", "#.#", "##.", "#.#", "#.#"],
        "O": ["###", "#.#", "#.#", "#.#", "###"], "K": ["#.#", "##.", "#..", "##.", "#.#"], "M": ["#.#", "###", "###", "#.#", "#.#"],
        "P": ["##.", "#.#", "##.", "#..", "#.."], "D": ["##.", "#.#", "#.#", "#.#", "##."], "J": ["..#", "..#", "..#", "#.#", "###"],
        "2": ["###", "..#", "###", "#..", "###"], "1": [".#.", "##.", ".#.", ".#.", "###"], "0": ["###", "#.#", "#.#", "#.#", "###"],
        "·": ["...", "...", ".#.", "...", "..."], "-": ["...", "...", "###", "...", "..."], " ": ["...", "...", "...", "...", "..."],
        ">": ["#..", ".#.", "..#", ".#.", "#.."], "!": [".#.", ".#.", ".#.", "...", ".#."],
    }
    x = x0
    for ch in text:
        g = font.get(ch, font[" "])
        for dy, row in enumerate(g):
            for dx, b in enumerate(row):
                if b == "#":
                    for sy in range(scale):
                        for sx in range(scale):
                            s.px(x + dx * scale + sx, y0 + dy * scale + sy, col)
        x += 4 * scale
    return x


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
    """Plechová ubikace (kontejner) ve 3/4: zvlněný plech, střecha s anténou, dveře s pečetí vah, okno se
    světlem (někdo nechal lampu), zespodu z rohu prolamuje plech bledý kořen."""
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
    stencil(s, 30, roof_h + 20, "S-8", c(0xD8D2BE))
    for y in range(h - 12, h):                                            # kořen prolamuje roh
        for x in range(w - 14, w - 1):
            d = (x - (w - 14)) - (h - y) * 1.2
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
    stencil(s, w // 2 - 2, 18, "A", c(0xD8D2BE))
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


def barrier(w=52):
    """Závora s žlutočerným ramenem a cedulí UZÁVĚRA na betonových patkách."""
    s = Spr(w, 26)
    for x in range(2, w - 2):
        for y in range(10, 13):
            s.px(x, y, YELLOW[2] if (x // 4) % 2 else c(0x16161C))
    for (x0, y0) in ((0, 12), (w - 8, 12)):
        s.rect(x0, y0, x0 + 7, y0 + 13, CONC[3]); s.hline(x0, x0 + 7, y0, CONC[5])
    s.rect(w // 2 - 15, 0, w // 2 + 15, 8, c(0xE8E2CC)); s.hline(w // 2 - 15, w // 2 + 15, 8, c(0x8A8470))
    stencil(s, w // 2 - 14, 2, "UZAVERA", RED[2])
    return selout(s, 0.5, 0.8)


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


# ── kořeny a kreslení přímo do mapy ─────────────────────────────────────────
def root_path(img, depth, pts, w0, w1, AW, AH, block=None, lift=0.0, d=0.5):
    """Kořen podél křivky bodů (šířka w0→w1) ve 3/4: válcové stínování, praskliny v kůře, stín pod ním.
    Kreslí přímo do img; block = kořen je překážka. lift = kořen vystupuje nad zem (víc stínu)."""
    n = len(pts)
    for i in range(n - 1):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        t = i / (n - 1)
        w = (w0 + (w1 - w0) * t) * (1 + 0.18 * math.sin(t * 23 + w0) + 0.1 * math.sin(t * 61))   # hrboly
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        nx, ny = -dy / ln, dx / ln
        steps = max(1, int(ln * 1.6))
        for s_ in range(steps):
            cx, cy = x0 + dx * s_ / steps, y0 + dy * s_ / steps
            for k in np.linspace(-w / 2, w / 2, max(2, int(w * 2.2))):
                X, Y = int(round(cx + nx * k)), int(round(cy + ny * k))
                if not (0 <= X < AW and 0 <= Y < AH):
                    continue
                if depth[Y, X] and depth[Y, X] < d - 0.01:
                    continue
                u = k / (w / 2 + 0.01)
                v = 0.52 - 0.38 * u * (1 if nx >= 0 else -1) + 0.08 * math.cos(u * math.pi)
                v += (_vn(X * 1.4, Y * 1.4, 11, 3) - 0.5) * 0.3                  # kůra
                if abs(_vn(cx * 0.9 + k * 2, cy * 0.9, 12, 4) - 0.5) < 0.04:
                    v -= 0.3                                                  # podélné rýhy
                if abs(u) > 0.84:
                    v = 0.08
                if _h(int(cx * 0.6), int(cy * 0.6), 7) % 9 == 0 and abs(u) < 0.5:
                    v -= 0.3
                v = min(v, 0.82)
                img[Y, X] = _pick(BARK, max(0.0, min(1.0, v)), X, Y)
                depth[Y, X] = d
                if block is not None:
                    block[Y, X] = True
            # stín pod kořenem (vpravo dole)
            for k in range(1, int(2 + lift * 3)):
                X, Y = int(round(cx + w / 2 * 0.3 + k)), int(round(cy + w / 2 + k))
                if 0 <= X < AW and 0 <= Y < AH and not depth[Y, X]:
                    img[Y, X, :3] = (img[Y, X, :3] * 0.55).astype(np.uint8)


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
    stencil(s, 5, top + 4, "S8", c(0xCFC8B0))
    return selout(s, 0.5, 0.8)

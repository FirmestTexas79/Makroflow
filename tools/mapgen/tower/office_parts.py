"""
Kancelář Řádu – 11. patro, vrchol Věže Řádu Váhy: observatoř. Nikdo tu nesedí, a přesto se tu pracuje.
Kopule s otevřenou štěrbinou na hvězdy (chybí v nich jedna – ta, kterou snědl Hladomír), mosazný dalekohled,
orloj (model nebe), Foucaultovo kyvadlo nad mosazným kruhem s čísly okruhů, velké váhy – znak Řádu, mapa
Makrosvěta se stanicemi 0–7 a rudými nitěmi, stůl Řádu s Registrem nádob, který se dopisuje sám.
Ve 3/4, objemově, obrys selout.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
from pix import Spr, c  # noqa: E402
from figures import sphere, cylinder, selout, _pick, BRASS, PAPER, INK, STEEL, RED, IRON_, shade_px  # noqa: E402

NAVY = [c(0x070A16), c(0x0E1428), c(0x18203E), c(0x243058), c(0x364678), c(0x5468A0)]
GOLDL = [c(0x6A4A18), c(0x9C7428), c(0xCCA040), c(0xF0CC6C)]
EBONY = [c(0x100A0A), c(0x1C1212), c(0x2A1C1A), c(0x3C2A24), c(0x56403A)]
MARBLE = [c(0x101218), c(0x181C26), c(0x222836), c(0x2E3646), c(0x404A5E), c(0x5E6A82)]
CARPET = [c(0x2A0A10), c(0x44121A), c(0x5E1C24), c(0x7A2A30), c(0x9A3E40)]
LEATHERG = [c(0x0E2418), c(0x163626), c(0x204A34), c(0x2E6046)]
SKY = [c(0x020308), c(0x060A18), c(0x0C1430), c(0x162248)]


def _h(x, y, s=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (s * 83492791)) & 0xFFFF


def marble_floor(img, x0, y0, x1, y1, ring=None, carpet=None):
    """Tmavý mramor 16 × 16 s žilkováním (šikmé světlé nitky), lesklé spáry. [ring] = (cx, cy, r): mosazný
    kruh s ryskami a čísly okruhů 198–213 (tečky), pod Foucaultovým kyvadlem. [carpet] = (x0, x1, y0, y1)
    vínový běhoun se zlatým lemem."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx, ly = (x - x0) % 16, (y - y0) % 16
            tx, ty = (x - x0) // 16, (y - y0) // 16
            k = 2 + (tx + ty) % 2
            col = MARBLE[k]
            ph = _h(tx, ty, 1) % 7
            v = math.sin(x * 0.28 + y * 0.12 + ph + 1.6 * math.sin(y * 0.21 + x * 0.05 + ph))
            if abs(v) < 0.06 and _h(tx, ty, 2) % 3:
                col = MARBLE[k + 2]                                                    # žilka
            elif abs(v) < 0.16 and (x + y) % 2 and _h(tx, ty, 2) % 3:
                col = MARBLE[k + 1]
            if lx == 0 or ly == 0:
                col = MARBLE[1]
            if lx == 1 and ly == 1:
                col = MARBLE[5]
            img[y, x] = col
    if carpet:
        cx0, cx1, cy0, cy1 = carpet
        for y in range(cy0, cy1):
            for x in range(cx0, cx1):
                e = min(x - cx0, cx1 - 1 - x)
                col = CARPET[0] if e == 0 else GOLDL[2] if e == 1 else CARPET[1] if e == 2 else CARPET[2]
                if e > 3 and (y // 4 + (x - cx0) // 4) % 4 == 0 and (x + y) % 2:
                    col = CARPET[3]
                img[y, x] = col
    if ring:
        rcx, rcy, r = ring
        for y in range(int(rcy - r * 0.6) - 3, int(rcy + r * 0.6) + 4):
            for x in range(int(rcx - r) - 3, int(rcx + r) + 4):
                q = math.hypot(x + .5 - rcx, (y + .5 - rcy) / 0.6)
                if r - 2 <= q <= r + 1:
                    ang = math.atan2((y - rcy) / 0.6, x - rcx)
                    img[y, x] = GOLDL[3] if q > r and ang < 0 else GOLDL[2] if q > r - 1 else GOLDL[1]
                elif q < r - 2:
                    img[y, x] = (img[y, x] * 0.8).astype(np.uint8) if (x + y) % 2 else img[y, x]
        for i in range(16):                                                          # rysky okruhů 198–213
            a = i / 16 * 2 * math.pi - math.pi / 2
            for t in (r - 4, r - 5):
                X, Y = int(rcx + math.cos(a) * t), int(rcy + math.sin(a) * t * 0.6)
                img[Y, X] = GOLDL[3] if i == 15 else GOLDL[2]
            if i == 15:                                                              # 213 – červená
                X, Y = int(rcx + math.cos(a) * (r - 7)), int(rcy + math.sin(a) * (r - 7) * 0.6)
                img[Y, X] = RED[3]
        for k in range(-4, 5):                                                       # střed: mosazná růžice
            img[int(rcy), int(rcx + k)] = GOLDL[2]
            img[int(rcy + k * 0.6), int(rcx)] = GOLDL[2]


def dome_wall(W, H, slit=(16, 44), mapbox=None):
    """Zadní stěna = vnitřek kopule: tmavě modrá omítka se zlatými souhvězdími, mosazná žebra sbíhající nahoru,
    dole obklad z ebenu. [slit] = svislá štěrbina kopule otevřená do noci (hvězdy, v jednom místě chybí hvězda –
    prázdný kroužek)."""
    s = Spr(W, H)
    pan = H - 26
    for y in range(H):
        for x in range(W):
            if y >= pan:
                ly = y - pan
                col = EBONY[3] if ly == 0 else EBONY[2] if (x % 22) not in (0, 21) else EBONY[0]
                if ly in (4, 20) and (x % 22) not in (0, 21):
                    col = EBONY[1]
                if ly == 1:
                    col = GOLDL[1]
            else:
                col = NAVY[2] if (_h(x // 3, y // 3, 4) % 5) else NAVY[1]
                if _h(x, y, 8) % 211 == 0:
                    col = GOLDL[2]                                                    # zlaté tečky (hvězdy na omítce)
            s.px(x, y, col)
    for rx in (52, 124):                                                             # mosazná žebra kopule
        for y in range(0, pan):
            xx = int(rx + (88 - rx) * (1 - y / pan) * 0.25)
            s.px(xx, y, GOLDL[2]); s.px(xx + 1, y, GOLDL[0])
    consts = [((70, 40), (76, 34), (82, 38), (80, 46)), ((100, 30), (108, 26), (114, 32)),
              ((140, 50), (148, 46), (152, 54), (160, 50))]
    for pts in consts:                                                               # souhvězdí (zlaté linky)
        for a, b in zip(pts, pts[1:]):
            for t in range(0, 11):
                X = a[0] + (b[0] - a[0]) * t / 10; Y = a[1] + (b[1] - a[1]) * t / 10
                if t % 2 == 0:
                    s.px(X, Y, GOLDL[1])
        for p in pts:
            s.px(p[0], p[1], GOLDL[3])
    x0, x1 = slit                                                                    # štěrbina kopule
    for y in range(0, pan - 2):
        for x in range(x0 - 3, x1 + 4):
            if x < x0 or x > x1:
                s.px(x, y, GOLDL[2] if x in (x0 - 3, x1 + 1) else GOLDL[1] if x in (x0 - 2, x1 + 2) else GOLDL[0])
            else:
                t = y / pan
                col = SKY[0] if t < 0.3 else SKY[1] if t < 0.6 else SKY[2]
                hh = _h(x, y, 21) % 97
                if hh == 0:
                    col = c(0xFFFFFF)
                elif hh < 3:
                    col = c(0x8A9AC8)
                s.px(x, y, col)
    mx, my = (x0 + x1) // 2 + 2, 22                                                  # chybějící hvězda
    for a in range(12):
        ang = a / 12 * 2 * math.pi
        s.px(mx + math.cos(ang) * 3, my + math.sin(ang) * 3, c(0x3A2A40))
    for x in range(x0 - 3, x1 + 4):                                                  # parapet štěrbiny
        s.px(x, pan - 2, GOLDL[3]); s.px(x, pan - 1, GOLDL[1])
    for x in range(W):                                                               # římsa
        s.px(x, 0, GOLDL[2]); s.px(x, 1, GOLDL[0])
    return s


def telescope():
    """Velký refraktor: mosazný tubus s tmavými prstenci míří šikmo vzhůru doleva (ke štěrbině kopule),
    hledáček, okulár dole vpravo, paralaktická hlavice a dřevěná trojnožka s rozpěrou."""
    W, H = 52, 62
    s = Spr(W, H)
    hx, hy = 30, 36                                                                  # hlavice
    for (fx, fy) in ((14, 61), (30, 61), (44, 60)):                                  # nohy trojnožky
        s.line(hx, hy + 2, fx, fy, EBONY[3], 2)
        s.line(hx + 1, hy + 2, fx + 1, fy, EBONY[1])
    for x in range(18, 41):
        s.px(x, 52, BRASS[2]); s.px(x, 53, BRASS[0])
    ax, ay = -0.78, -0.62                                                            # osa tubusu
    nx, ny = -ay, ax
    L0, L1 = -12, 34
    for t10 in range(L0 * 10, L1 * 10):
        t = t10 / 10
        rad = 3.2 + (t - L0) / (L1 - L0) * 1.6
        for o10 in range(int(-rad * 10), int(rad * 10) + 1):
            o = o10 / 10
            X, Y = hx + ax * t + nx * o, hy + ay * t + ny * o
            v = 0.25 + 0.75 * max(0.0, -o / rad * 0.7 + 0.5)
            ramp = BRASS if not (abs(t - 6) < 0.8 or abs(t - 20) < 0.8 or t > L1 - 2) else [EBONY[0], EBONY[1], EBONY[2], EBONY[3], EBONY[4]]
            s.px(round(X), round(Y), _pick(ramp, min(1, v), int(X), int(Y)))
    for t10 in range(20, 220):                                                       # hledáček
        t = t10 / 10
        for o in (-5.6, -5.0):
            X, Y = hx + ax * t + nx * o, hy + ay * t + ny * o
            s.px(round(X), round(Y), BRASS[3] if o < -5.3 else BRASS[1])
    ex, ey = hx - ax * 13, hy - ay * 13                                              # okulár
    sphere(s, ex, ey, 2.2, 2.2, [EBONY[1], EBONY[2], EBONY[3], EBONY[4]], amb=0.3)
    sphere(s, hx, hy, 4.5, 4, BRASS, amb=0.3)                                        # hlavice
    s.px(hx - 1, hy - 2, BRASS[4])
    return selout(s, 0.5, 0.82)


def orrery_base():
    """Orloj (model nebe): ebenový podstavec na lvích tlapách, mosazné prstence drah ve 3/4, uprostřed zlaté
    slunce. Planety obíhají v InteriorFxView (orrery)."""
    W, H = 40, 40
    s = Spr(W, H)
    cx, cy = 20, 18
    for y in range(28, H):                                                           # podstavec
        hw = 6 + (y - 28) * 0.6
        for x in range(int(cx - hw), int(cx + hw) + 1):
            s.px(x, y, EBONY[4] if x < cx - hw + 2 else EBONY[2] if x < cx + hw - 2 else EBONY[1])
    for x in range(cx - 13, cx + 14):
        s.px(x, H - 1, EBONY[0])
    s.vline(cx, cy + 2, 28, BRASS[2]); s.vline(cx + 1, cy + 2, 28, BRASS[0])
    for r in (8, 13, 18):                                                            # dráhy
        for a10 in range(0, 630):
            a = a10 / 100
            X, Y = cx + math.cos(a) * r, cy + math.sin(a) * r * 0.42
            if Y > cy - 0.5 or a10 % 3 == 0:
                s.px(round(X), round(Y), BRASS[3] if math.cos(a) < 0 else BRASS[1])
    sphere(s, cx, cy, 3.4, 3.4, [GOLDL[0], GOLDL[1], GOLDL[2], GOLDL[3], c(0xFFF4C0)], amb=0.5)
    return selout(s, 0.55, 0.85)


def great_scales():
    """Velké váhy – znak Řádu: mramorový podstavec, mosazný sloup s koulí, vahadlo s řetízky a dvěma miskami;
    na levé hrst popela, na pravé modrý lumenský krystal. Vahadlo je mírně nakloněné."""
    W, H = 42, 50
    s = Spr(W, H)
    cx = 21
    for y in range(H - 10, H):                                                       # podstavec
        for x in range(cx - 10, cx + 11):
            s.px(x, y, MARBLE[5] if y == H - 10 else MARBLE[4] if x < cx + 6 else MARBLE[3])
    cylinder(s, cx - 2, cx + 2, 10, H - 10, BRASS, amb=0.3)
    sphere(s, cx, 9, 3, 3, BRASS, amb=0.35)
    tilt = 0.12
    for x in range(2, W - 2):                                                        # vahadlo
        y = 11 + (x - cx) * tilt
        s.px(x, y, BRASS[3]); s.px(x, y + 1, BRASS[1])
    for side, (px_, ) in ((-1, (4,)), (1, (W - 5,))):
        top = 11 + (px_ - cx) * tilt
        bot = top + 16
        s.line(px_, top + 1, px_ - 5, bot, BRASS[1]); s.line(px_, top + 1, px_ + 5, bot, BRASS[1])
        for x in range(px_ - 6, px_ + 7):                                            # miska
            d = abs(x - px_) / 6
            s.px(x, bot, BRASS[4] if d < 0.6 else BRASS[2]); s.px(x, bot + 1, BRASS[1])
            if d < 0.8:
                s.px(x, bot + 2, BRASS[0])
        if side < 0:
            for x in range(px_ - 3, px_ + 4):
                s.px(x, bot - 1, c(0x8E8A84) if (x % 2) else c(0x6E6A66))
            s.px(px_, bot - 2, c(0xB2ADA6))
        else:
            for y in range(int(bot - 6), int(bot)):
                hw = (y - (bot - 6)) * 0.5
                for x in range(int(px_ - hw), int(px_ + hw) + 1):
                    s.px(x, y, c(0x9AE6FF) if x < px_ else c(0x4A9AD0))
    return selout(s, 0.5, 0.82)


def order_desk():
    """Stůl Řádu: masivní ebenový stůl ve 3/4 se zelenou koženou vložkou, na něm Registr nádob (otevřená
    kniha), kalamář s brkem, razítko, mosazná lampa se zeleným stínidlem; za stolem prázdné vysoké křeslo."""
    W, H = 62, 48
    s = Spr(W + 6, H)
    ch_x = W // 2                                                                    # křeslo (za stolem)
    for y in range(0, 22):
        for x in range(ch_x - 9, ch_x + 10):
            e = min(x - (ch_x - 9), ch_x + 9 - x, y)
            col = CARPET[3] if e == 0 else CARPET[2] if (x + y) % 6 else CARPET[1]
            if e > 1 and (x - ch_x) % 6 == 0 and y % 5 == 2:
                col = GOLDL[2]                                                        # čalounické knoflíky
            s.px(x, y, col)
    top, th = 18, 14
    for y in range(top, top + th):                                                   # deska
        for x in range(0, W):
            e = min(x, W - 1 - x, y - top, top + th - 1 - y)
            col = EBONY[4] if e == 0 and y == top else EBONY[3] if e < 3 else LEATHERG[2] if (x + y) % 9 else LEATHERG[1]
            s.px(x, y, col)
    for y in range(top + th, H - 1):                                                 # čelo a nohy
        for x in range(0, W):
            col = EBONY[2] if y == top + th else EBONY[1]
            if 6 < x < W - 7 and y > top + th + 2 and y < H - 6:
                col = EBONY[2] if (x // 12) % 2 else EBONY[1]                            # šuplíky
                if x % 12 == 6 and y == top + th + 6:
                    col = GOLDL[3]
            s.px(x, y, col)
        for x in range(W, W + 6):
            if y - top - th >= x - W - 6:
                s.px(x, y, EBONY[0])
    for y in range(top + 2, top + 10):                                               # Registr nádob
        for x in range(18, 42):
            col = PAPER[3] if x != 30 else PAPER[0]
            if (y - top) % 2 == 0 and x not in (29, 30, 31) and (x % 4):
                col = INK
            s.px(x, y, col)
    for x in range(18, 42):
        s.px(x, top + 10, c(0x5A1414))
    s.px(9, top + 3, INK); s.px(10, top + 3, INK); s.px(9, top + 2, c(0x2A2A34))      # kalamář
    s.line(10, top + 2, 14, top - 4, PAPER[3]); s.px(14, top - 5, PAPER[2])           # brk
    for y in range(top + 3, top + 7):                                                # razítko
        s.px(45, y, EBONY[3]); s.px(46, y, EBONY[3])
    s.px(44, top + 7, RED[2]); s.px(47, top + 7, RED[2])
    lx = 53                                                                          # lampa
    s.vline(lx, top - 6, top + 4, BRASS[2])
    for y in range(top - 10, top - 5):
        hw = 2 + (y - (top - 10))
        for x in range(lx - hw, lx + hw + 1):
            s.px(x, y, LEATHERG[3] if x < lx else LEATHERG[2])
    for x in range(lx - 4, lx + 5):
        s.px(x, top + 4, BRASS[3])
    return selout(s, 0.5, 0.82)


def station_map(w=44, h=40):
    """Mapa Makrosvěta na stěně: pergamen v rámu, obrysy krajů, osm připínáčků (stanice 0–7), od každého
    vede rudá nit dolů přes okraj rámu."""
    s = Spr(w, h + 18)
    rng = np.random.default_rng(5)
    for y in range(h):
        for x in range(w):
            e = min(x, y, w - 1 - x, h - 1 - y)
            col = EBONY[3] if e == 0 else EBONY[2] if e < 2 else PAPER[2] if (x + y * 3) % 11 else PAPER[1]
            s.px(x, y, col)
    for k in range(5):                                                               # obrysy krajů
        cx_, cy_ = rng.uniform(8, w - 8), rng.uniform(8, h - 8)
        for a10 in range(0, 63):
            a = a10 / 10
            r = 5 + math.sin(a * 3 + k) * 2
            s.px(cx_ + math.cos(a) * r, cy_ + math.sin(a) * r * 0.8, PAPER[0])
    pins = [(8, 8), (20, 6), (34, 9), (12, 20), (28, 18), (38, 26), (16, 31), (26, 33)]
    for i, (px_, py_) in enumerate(pins):
        s.line(px_, py_ + 1, px_ + (i % 3 - 1), h + 6 + (i * 5) % 12, RED[1])         # rudá nit dolů přes okraj
        s.px(px_, py_, RED[4] if i else c(0xF0F0F0)); s.px(px_ + 1, py_, RED[2])
    return selout(s, 0.5, 0.82)

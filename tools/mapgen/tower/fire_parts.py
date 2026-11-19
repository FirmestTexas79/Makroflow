"""
Spalovna spisů – 10. patro Věže Řádu Váhy, ohnivé patro. Sem padají spisy z archivu, když je Řád „uzavře“.
Začouzené cihly, litinové podlahové desky s mřížemi, nad kterými se tetelí žár, velká pec s otevřenými dvířky,
skluz na spisy, vozík plný složek, hromady uhlí s lopatou, kupy popela (setkání), parní potrubí s manometrem.
Ve 3/4, objemově, obrys selout.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
from pix import Spr, c  # noqa: E402
from figures import sphere, selout, blobs, BRASS, PAPER, INK, STEEL, RED, IRON_, shade_px  # noqa: E402

BRICK = [c(0x1A0C0A), c(0x2C1410), c(0x421E16), c(0x5A2A1E), c(0x743A28)]
SOOT = [c(0x0C0A0C), c(0x181416), c(0x241E20), c(0x322A2A), c(0x443836)]
EMBER = [c(0x4A1006), c(0x8A220A), c(0xD24E10), c(0xF48A22), c(0xFCD058), c(0xFFF2C0)]
PLATE = [c(0x16141A), c(0x22202A), c(0x302C36), c(0x403A44), c(0x585058)]
ASH = [c(0x3A3634), c(0x58524E), c(0x7A726C), c(0x9C948C), c(0xBEB6AC)]
COAL = [c(0x060608), c(0x0E0E14), c(0x18181F), c(0x262632), c(0x6A7088)]
FOLDER = [c(0x6A4A22), c(0x8E6A34), c(0xB28A48), c(0xD0AC64)]


def _h(x, y, s=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (s * 83492791)) & 0xFFFF


def iron_floor(img, x0, y0, x1, y1, grates=()):
    """Litinové podlahové desky 22 × 16 px s nýty v rozích a protiskluzovým vzorem (šikmé rýhy), saze ve spárách,
    tu a tam rezavá skvrna. [grates] = obdélníky mříží, pod kterými žhne (tmavé pruhy + oranžová mezi nimi)."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx, ly = (x - x0) % 22, (y - y0) % 16
            tx, ty = (x - x0) // 22, (y - y0) // 16
            k = 2 if (tx + ty) % 2 else 3
            col = PLATE[k]
            if (lx + ly * 2) % 6 == 0 and 2 < lx < 20 and 2 < ly < 14:
                col = PLATE[k - 1]                                                      # rýhy
            if lx == 0 or ly == 0:
                col = SOOT[1]
            elif lx == 1 or ly == 1:
                col = PLATE[4] if k == 3 else PLATE[3]
            if (lx, ly) in ((3, 3), (18, 3), (3, 12), (18, 12)):
                col = PLATE[4]
            if (lx, ly) in ((4, 4), (19, 4), (4, 13), (19, 13)):
                col = SOOT[1]
            if _h(tx, ty, 2) % 7 == 0 and ((lx - 11) ** 2 + (ly - 8) ** 2) < 9 + _h(x, y) % 6:
                col = c(0x5A2E1A) if (x + y) % 2 else c(0x6E3A20)                      # rez
            img[y, x] = col
    for (gx0, gy0, gx1, gy1) in grates:
        for y in range(gy0, gy1):
            for x in range(gx0, gx1):
                e = min(x - gx0, gx1 - 1 - x, y - gy0, gy1 - 1 - y)
                if e == 0:
                    img[y, x] = IRON_[3] if y == gy0 or x == gx0 else IRON_[0]
                elif (x - gx0) % 3 == 0:
                    img[y, x] = IRON_[1]
                else:
                    t = (y - gy0) / max(1, gy1 - gy0)
                    img[y, x] = EMBER[3] if t > 0.4 and (x + y) % 2 else EMBER[2] if t > 0.2 else EMBER[1]


def soot_wall(W, H):
    """Zadní stěna: cihly 8 × 4 ve vazbě, nahoře černé od sazí (přechod rozbitý šumem), litinový pás s nýty,
    dole u paty rudý odlesk žáru."""
    s = Spr(W, H)
    for y in range(H):
        for x in range(W):
            row = y // 4
            off = 4 if row % 2 else 0
            lx, ly = (x + off) % 8, y % 4
            b = _h((x + off) // 8, row, 1) % 3
            col = BRICK[2 + (b == 0)] if not (lx == 0 or ly == 3) else BRICK[1]
            if _h((x + off) // 8, row, 4) % 9 == 0 and lx and ly != 3:
                col = BRICK[4]
            soot = 1 - y / (H * 0.75) + ((_h(x // 3, y // 3, 7) % 100) / 100 - 0.5) * 0.35
            if soot > 0.55:
                col = SOOT[2] if soot < 0.8 else SOOT[1]
                if (lx == 0 or ly == 3):
                    col = SOOT[0]
            if y > H - 10 and (x + y) % 3 == 0 and col is not BRICK[1]:
                col = c(0x8A3A20)
            s.px(x, y, col)
    for y in (6, 7, 8, 9):
        for x in range(W):
            s.px(x, y, IRON_[3] if y == 6 else IRON_[2] if y < 9 else IRON_[0])
    for x in range(4, W, 12):
        s.px(x, 7, IRON_[4]); s.px(x, 8, IRON_[1])
    return s


def furnace(W=72, H=78):
    """Velká pec ve 3/4: cihlové těleso (horní plocha, čelo, tmavší bok vpravo), litinový rám s obloukem, dvířka
    otevřená doleva, uvnitř žhavé lože a plameny (oranžová → bílá ve středu), nad ústím začouzený plech, na čele
    mosazný štítek, dole popelník. Plameny oživuje InteriorFxView.FIRE (flames)."""
    s = Spr(W + 8, H)
    top = 12
    for y in range(H):
        for x in range(W):
            if y < top:                                                                 # horní plocha (cihly)
                lx, ly = (x + (4 if (y // 3) % 2 else 0)) % 10, y % 3
                col = BRICK[4] if not (lx == 0 or ly == 2) else BRICK[2]
                if y < 1 or x < 1:
                    col = SOOT[3]
            else:
                row = (y - top) // 4; off = 4 if row % 2 else 0
                lx, ly = (x + off) % 8, (y - top) % 4
                col = BRICK[3] if not (lx == 0 or ly == 3) else BRICK[1]
                if _h((x + off) // 8, row, 3) % 4 == 0 and lx and ly != 3:
                    col = BRICK[2]
                if x < 3:
                    col = BRICK[4] if ly != 3 else BRICK[2]
            s.px(x, y, col)
        for x in range(W, W + 8):                                                       # bok
            if y - top >= -(x - W) and y >= x - W:
                s.px(x, y, BRICK[1] if (y // 4) % 2 else BRICK[0])
    cx, mouth_top, mouth_bot, hw = W // 2, top + 14, H - 16, 18
    for y in range(mouth_top - 4, mouth_bot + 4):                                       # litinový rám
        for x in range(cx - hw - 4, cx + hw + 5):
            ay = y - (mouth_top + hw)
            inside_arch = y >= mouth_top + hw or math.hypot(x - cx, ay) <= hw + 4
            if inside_arch:
                s.px(x, y, IRON_[2] if x < cx - hw - 2 or y < mouth_top - 2 else IRON_[1])
    for y in range(mouth_top, mouth_bot):                                               # ústí s ohněm
        for x in range(cx - hw, cx + hw + 1):
            ay = y - (mouth_top + hw)
            if y < mouth_top + hw and math.hypot(x - cx, ay) > hw:
                continue
            t = (y - mouth_top) / (mouth_bot - mouth_top)
            d = abs(x - cx) / hw
            v = (1 - d * 0.8) * (0.4 + t * 0.8) + (_h(x, y, 9) % 100) / 400
            fl = math.sin(x * 0.9) * 0.15 + math.sin(x * 0.37 + 1) * 0.2
            if t < 0.35 + fl * 0.5:
                v *= 0.45                                                               # nahoře tma komory
            s.px(x, y, EMBER[min(5, max(0, int(v * 5.2)))])
    for y in range(mouth_bot - 5, mouth_bot):                                           # žhavé lože
        for x in range(cx - hw + 1, cx + hw):
            s.px(x, y, EMBER[4] if (x + y) % 3 else EMBER[5] if _h(x, y) % 5 == 0 else EMBER[3])
    for y in range(mouth_top - 2, mouth_bot + 2):                                       # dvířka otevřená doleva
        for x in range(cx - hw - 14, cx - hw - 4):
            k = (cx - hw - 4 - x) / 10
            if mouth_top + k * 4 <= y <= mouth_bot - k * 4:
                e = x == cx - hw - 14 or y in (int(mouth_top + k * 4), int(mouth_bot - k * 4))
                s.px(x, y, IRON_[3] if e else IRON_[1] if x < cx - hw - 9 else IRON_[2])   # vnitřní strana dvířek
    for y in (mouth_top + 6, mouth_bot - 8):                                            # panty a nýty
        s.px(cx - hw - 4, y, IRON_[4]); s.px(cx - hw - 5, y, IRON_[0])
    for y in range(mouth_top + 14, mouth_top + 20):                                     # klika
        s.px(cx - hw - 12, y, BRASS[2])
    for x in range(cx - hw - 2, cx + hw + 3):                                           # popelník
        for y in range(mouth_bot + 5, mouth_bot + 11):
            s.px(x, y, IRON_[1] if y > mouth_bot + 6 else IRON_[3])
        if (x - cx) % 4 == 0:
            s.px(x, mouth_bot + 8, EMBER[2])
    for x in range(cx - 8, cx + 9):                                                     # štítek
        s.px(x, top + 4, BRASS[3]); s.px(x, top + 5, BRASS[2]); s.px(x, top + 6, BRASS[1])
    for x in range(cx - 6, cx + 7, 2):
        s.px(x, top + 5, INK)
    for y in range(0, top + 1):                                                         # kouřovod (litina) vzadu na stropě pece
        for x in range(cx - 6, cx + 7):
            d = (x - cx) / 6
            s.px(x, y, IRON_[3] if d < -0.4 else IRON_[2] if d < 0.4 else IRON_[1])
    for x in range(cx - 7, cx + 8):
        s.px(x, top - 1, IRON_[0]); s.px(x, 0, IRON_[4] if x < cx else IRON_[3])
    for y in range(top, mouth_top - 4):                                                 # saze nad ústím
        for x in range(cx - hw, cx + hw):
            if (_h(x, y, 5) % 100) / 100 < 0.55 + (y - top) / (mouth_top - top) * 0.4:
                shade_px(s, x, y, 0.55)
    return selout(s, 0.5, 0.8)


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


def ash_heap(seed=0, w=36, h=18):
    """Kupa popela (místo setkání): měkké šedé boule, uvnitř tu a tam doutná uhlík, navrchu ohořelý cár papíru."""
    s = Spr(w, h)
    rng = np.random.default_rng(seed)
    bumps = [(w * 0.5, h - 7, 9), (w * 0.3, h - 5, 7), (w * 0.7, h - 5, 6.5), (w * 0.45, h - 10, 5)]
    blobs(s, bumps, ASH, amb=0.3)
    for y in range(h - 3, h):
        for x in range(3, w - 3):
            if not s.opaque(x, y):
                s.px(x, y, ASH[1] if (x + y) % 2 else ASH[0])
    for _ in range(6):
        x, y = int(rng.integers(5, w - 5)), int(rng.integers(h - 9, h - 2))
        if s.opaque(x, y):
            s.px(x, y, EMBER[3]); s.px(x + 1, y, EMBER[2])
    px0, py0 = int(w * 0.55), 4                                                         # ohořelý cár
    for y in range(py0, py0 + 4):
        for x in range(px0, px0 + 6):
            if s.opaque(x, y + 2) or y > py0:
                edge = x == px0 or x == px0 + 5 or y == py0
                s.px(x, y, SOOT[2] if edge else PAPER[2])
    s.px(px0 + 2, py0 + 2, INK); s.px(px0 + 3, py0 + 2, INK)
    return selout(s, 0.6, 0.9)


def file_cart():
    """Železný vozík se spisy (složky na sobě, některé ohořelé na rozích), dvě kolečka, madlo."""
    W, H = 34, 30
    s = Spr(W + 4, H)
    for y in range(12, H - 5):                                                          # korba
        for x in range(1, W - 1):
            s.px(x, y, IRON_[3] if y == 12 else IRON_[2] if x > 2 else IRON_[3])
        for x in range(W - 1, W + 3):
            if y - 12 >= x - (W - 1):
                s.px(x, y, IRON_[1])
    rng = np.random.default_rng(3)
    for i in range(7):                                                                  # složky
        y0 = 12 - i * 2 + int(rng.integers(0, 2))
        x0 = 3 + int(rng.integers(-2, 3))
        for y in range(y0 - 2, y0 + 1):
            for x in range(x0, x0 + 26):
                col = FOLDER[2] if y == y0 - 2 else FOLDER[1]
                if (x - x0 > 20 and i % 3 == 0) or (x - x0 < 3 and i % 4 == 1):
                    col = SOOT[2] if (x + y) % 2 else EMBER[1]                           # ohořelý roh
                s.px(x, y, col)
        s.px(x0 + 6, y0 - 1, PAPER[3]); s.px(x0 + 7, y0 - 1, PAPER[3]); s.px(x0 + 8, y0 - 1, RED[2])
    for x in range(W + 1, W + 4):                                                       # madlo
        s.px(x, 10, IRON_[3])
    s.vline(W + 3, 10, 16, IRON_[2])
    for cx in (7, W - 7):
        sphere(s, cx, H - 4, 3.2, 3.2, IRON_, amb=0.3)
        s.px(cx, H - 4, IRON_[4])
    return selout(s, 0.5, 0.82)


def chute(h=46):
    """Skluz na spisy ze stěny: nýtovaný plechový žlab šikmo dolů, nahoře klapka s nápisem (čárky)."""
    W = 22
    s = Spr(W, h)
    for y in range(h):
        x0 = int(2 + y * 0.18)
        for x in range(x0, x0 + 12):
            e = x - x0
            s.px(x, y, STEEL[3] if e < 2 else STEEL[2] if e < 9 else STEEL[1])
        if y % 7 == 0:
            s.px(x0 + 1, y, STEEL[4]); s.px(x0 + 10, y, STEEL[0])
    for y in range(0, 9):
        for x in range(0, 16):
            s.px(x, y, IRON_[2] if y > 1 else IRON_[3])
    for x in range(3, 13, 2):
        s.px(x, 4, PAPER[3])
    return selout(s, 0.5, 0.82)


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

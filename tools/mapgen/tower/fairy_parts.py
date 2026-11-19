"""
Duhová zahrada – 11. patro Věže Řádu Váhy, patro víl (typ VÍLA). Pohádka, až příliš dokonalá: malované pastelové
nebe s duhou, která na pravé straně dopadá do hrnce zlata, trávník plný jetele (čtyřlístky v pravidelných
řádcích), kruhy muchomůrek (setkání), muchomůrkové domky víl, studna přání („přání se evidují“), skleněný zvon
s obřím čtyřlístkem (VZOREK ŠTĚSTÍ 213), sklenice s chycenými pixies, růžové keře a náprstníky. Ve 3/4, selout.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
from pix import Spr, c  # noqa: E402
from figures import sphere, cylinder, selout, blobs, _pick, BRASS, PAPER, INK, STEEL, RED, IRON_, shade_px  # noqa: E402

LAWN = [c(0x1E5A3A), c(0x2A7446), c(0x3A9052), c(0x52AC60), c(0x74C66E), c(0xA2DE88)]
CLOVER = [c(0x1A6A3A), c(0x2A8A46), c(0x46AA58), c(0x7AD07A)]
PASTEL = [c(0xF6B8D0), c(0xFFE08A), c(0xB8D8FF), c(0xD8B8F8), c(0xFFFFFF), c(0xFFC8A0)]
SKYP = [c(0x9AB8F0), c(0xB6C8F6), c(0xD2CCF4), c(0xEED0EC), c(0xFFDCE0), c(0xFFE8D8)]
CLOUDP = [c(0xD8D4EE), c(0xEEEAF8), c(0xFFFFFF)]
RAINBOW = [c(0xF2585A), c(0xFF9A48), c(0xFFDC5A), c(0x6ACC6A), c(0x58A8F0), c(0x6A6AD8), c(0xB06AD8)]
CAPR = [c(0x6A0E1A), c(0xA81E2A), c(0xD83A3A), c(0xF06A5A), c(0xFFB0A0)]
STEM = [c(0x9A8E80), c(0xC8BCAC), c(0xE8DFD0), c(0xFAF6EC)]
STONEP = [c(0x6A6488), c(0x8E88AC), c(0xB2ACCC), c(0xD4D0E8), c(0xF0EEFA)]
WOODP = [c(0x4A2E26), c(0x6E4436), c(0x925E48), c(0xB27E62)]
GOLDC = [c(0x7A5410), c(0xB0801C), c(0xE0B030), c(0xFCDC5A), c(0xFFF4B0)]
ROSE = [c(0x8A1E4A), c(0xC03A6A), c(0xE86A92), c(0xFFA8C4)]
GLASS = [c(0x8AB0C8), c(0xB8D6E6), c(0xE2F2FA), c(0xFFFFFF)]


def _h(x, y, s=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (s * 83492791)) & 0xFFFF


def _clover(img, x, y, four=False, col=None):
    """Jetelový lístek 3 (nebo 4) srdíčka kolem středu, 1 px na lístek + tmavší střed."""
    ring = [(0, -1), (1, 0), (-1, 0)] + ([(0, 1)] if four else [])
    for dx, dy in ring:
        if 0 <= y + dy < img.shape[0] and 0 <= x + dx < img.shape[1]:
            img[y + dy, x + dx] = col if col is not None else CLOVER[3]
    img[y, x] = CLOVER[1]


def fairy_lawn(img, x0, y0, x1, y1):
    """Svěží pastelový trávník: drobné chomáčky trávy ve čtyřech tónech, husté trsy jetele (každý 9. lístek je
    čtyřlístek, vždy ve stejné vzdálenosti – až podezřele pravidelně), sedmikrásky, pomněnky a žluté kvítky."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            h = _h(x // 2 + (y // 2) % 2, y // 2)
            k = h % 11
            col = LAWN[3] if k < 7 else LAWN[2] if k < 9 else LAWN[4]
            if (x + (y // 3) * 2) % 7 == 0 and _h(x, y) % 4 == 0:
                col = LAWN[4]                                                        # stéblo
            n = math.sin(x * 0.09 + y * 0.05) + math.sin(x * 0.04 - y * 0.08)
            if n > 1.2 and col is LAWN[3]:
                col = LAWN[4]
            elif n < -1.3 and col is LAWN[3]:
                col = LAWN[2]
            img[y, x] = col
    for y in range(y0 + 3, y1 - 3, 6):
        for x in range(x0 + 3 + (y // 6) % 2 * 3, x1 - 3, 6):
            h = _h(x, y, 3)
            if h % 4 == 0:
                four = ((x // 6) + (y // 6) * 3) % 9 == 0
                _clover(img, x, y, four, CLOVER[3] if not four else c(0x9AF09A))
            elif h % 21 == 1:
                img[y, x] = PASTEL[4]; img[y, x + 1] = PASTEL[4]; img[y + 1, x] = PASTEL[4]; img[y, x - 1] = PASTEL[4]
                img[y - 1, x] = PASTEL[4]; img[y, x] = PASTEL[1]                         # sedmikráska
            elif h % 29 == 2:
                img[y, x] = PASTEL[2]; img[y + 1, x + 1] = PASTEL[2]                     # pomněnka
            elif h % 31 == 3:
                img[y, x] = PASTEL[1]


def stone_path(img, pts, rng):
    """Pastelové nášlapné kameny po klikaté cestě (oválky se světlou horní hranou a tmavým stínem dole)."""
    for (x, y) in pts:
        rx, ry = rng.uniform(4.5, 6.5), rng.uniform(2.6, 3.4)
        for yy in range(int(y - ry) - 1, int(y + ry) + 3):
            for xx in range(int(x - rx) - 1, int(x + rx) + 2):
                d = ((xx + .5 - x) / rx) ** 2 + ((yy + .5 - y) / ry) ** 2
                if d <= 1:
                    v = 0.5 - (yy - y) / ry * 0.35 - (xx - x) / rx * 0.15
                    img[yy, xx] = STONEP[2] if v < 0.45 else STONEP[3] if v < 0.8 else STONEP[4]
                    if d > 0.75 and yy > y:
                        img[yy, xx] = STONEP[1]
                elif d <= 1.5 and yy > y + ry * 0.5:
                    img[yy, xx] = (img[yy, xx] * 0.7).astype(np.uint8)


def sky_wall(W, H, arch=(18, 158, 92, 70)):
    """Zadní stěna = malované pastelové nebe: přechod modrá → levandulová → růžová → broskvová, nadýchané mraky,
    jiskřičky; přes celou stěnu duha (sedm pásů s jemným světlým okrajem), jejíž pravá noha dopadá na trávník.
    Dole živý plot s růžemi."""
    s = Spr(W, H)
    hedge = H - 16
    for y in range(H):
        for x in range(W):
            t = y / hedge
            idx = min(5, int(t * 6))
            col = SKYP[idx]
            if (t * 6) % 1 > 0.75 and (x + y) % 2:
                col = SKYP[min(5, idx + 1)]
            s.px(x, y, col)
    for (cx, cy, sc) in ((30, 20, 1.0), (128, 14, 1.3), (70, 40, 0.8), (150, 52, 0.9), (16, 58, 0.7)):
        for (dx, dy, r) in ((-8, 2, 6), (0, 0, 8), (9, 1, 6.5), (4, 4, 6), (-4, 5, 5)):
            rr = r * sc
            for y in range(int(cy + dy * sc - rr), int(cy + dy * sc + rr) + 1):
                for x in range(int(cx + dx * sc - rr), int(cx + dx * sc + rr) + 1):
                    d = math.hypot(x + .5 - cx - dx * sc, (y + .5 - cy - dy * sc) * 1.3)
                    if d <= rr:
                        col = CLOUDP[2] if (y < cy + dy * sc - rr * 0.2) else CLOUDP[1] if d < rr * 0.8 else CLOUDP[0]
                        s.px(x, y, col)
    ax0, ax1, ay, ar = arch                                                         # duha
    acx = (ax0 + ax1) / 2
    for y in range(0, hedge):
        for x in range(W):
            d = math.hypot((x + .5 - acx), (y + .5 - ay) * 1.15)
            band = int((ar - d) / 3)
            if 0 <= band < 7 and y < ay:
                col = RAINBOW[band]
                if (ar - d) % 3 < 0.6:
                    col = c(0xFFFFFF) if (x + y) % 3 == 0 else col
                s.px(x, y, col)
    for _ in range(60):                                                             # jiskřičky
        x, y = _h(_, 1) % W, _h(_, 2) % hedge
        s.px(x, y, c(0xFFFFFF))
    for y in range(hedge, H):                                                       # živý plot s růžemi
        for x in range(W):
            k = (_h(x // 2, y // 2, 9) % 4)
            col = [LAWN[1], LAWN[2], LAWN[3], LAWN[1]][k]
            top = hedge + int(2 * math.sin(x * 0.4) + 1.5 * math.sin(x * 0.13))
            if y < top:
                continue
            if y == top:
                col = LAWN[4]
            s.px(x, y, col)
    for i in range(22):
        x, y = 4 + i * 8 + _h(i, 4) % 4, hedge + 4 + _h(i, 5) % 7
        rc = [ROSE[2], PASTEL[0], PASTEL[4], PASTEL[1]][i % 4]
        s.px(x, y, rc); s.px(x + 1, y, rc); s.px(x, y + 1, ROSE[1] if rc is ROSE[2] else rc); s.px(x + 1, y + 1, rc)
    return s


def toadstool(s, cx, by, r, h, spots=True):
    """Muchomůrka do sprite [s]: bílá noha se sukénkou, kulatý červený klobouk s bílými puntíky."""
    cylinder(s, int(cx - r * 0.35), int(cx + r * 0.35), int(by - h), by, STEM, amb=0.4)
    for x in range(int(cx - r * 0.5), int(cx + r * 0.5) + 1):
        s.px(x, int(by - h * 0.55), STEM[3])
    sphere(s, cx, by - h, r, r * 0.62, CAPR, amb=0.3, mask=lambda x, y: y <= by - h + r * 0.2)
    for x in range(int(cx - r), int(cx + r) + 1):
        s.px(x, int(by - h + r * 0.2), CAPR[1])
    if spots:
        for (dx, dy) in ((-0.5, -0.3), (0.1, -0.5), (0.55, -0.15), (-0.15, 0.0), (0.35, 0.1)):
            X, Y = cx + dx * r, by - h + dy * r * 0.6
            s.px(X, Y, c(0xFFFFFF))
            if r > 4:
                s.px(X + 1, Y, c(0xF0E8E0))


def fairy_ring(seed=0, r=16):
    """Kruh muchomůrek (místo setkání): dvanáct hub různé velikosti po elipse, uvnitř tráva o tón světlejší
    a pár zářících spor."""
    W, H = int(r * 2 + 12), int(r * 1.2 + 18)
    s = Spr(W, H)
    rng = np.random.default_rng(seed)
    cx, cy = W / 2, H - r * 0.6 - 6
    items = []
    for i in range(12):
        a = i / 12 * 2 * math.pi + rng.uniform(-0.1, 0.1)
        items.append((cx + math.cos(a) * r, cy + math.sin(a) * r * 0.6, rng.uniform(2.2, 4.0)))
    items.sort(key=lambda t: t[1])
    for (x, y, rr) in items:
        toadstool(s, x, int(y + 3), rr, rr * 1.2 + 2)
    return selout(s, 0.55, 0.88), (cx, cy, r)


def fairy_house(seed=0, big=True):
    """Muchomůrkový domek víl: tlustá bílá noha s kulatými dvířky (mosazná klika), okénko s teplým světlem a
    květináčkem, obří puntíkatý klobouk s komínkem, u dveří lucernička a schůdek z kamínku."""
    W, H = (36, 46) if big else (26, 34)
    s = Spr(W, H)
    cx = W / 2
    r = W / 2 - 1
    sr = r * 0.55
    sy0, sy1 = int(H * 0.38), H - 2
    cylinder(s, int(cx - sr), int(cx + sr), sy0, sy1, STEM, amb=0.35)
    for x in range(int(cx - sr), int(cx + sr) + 1):                                  # spodek nohy
        s.px(x, sy1, STEM[0]); s.px(x, sy1 + 1, LAWN[1])
    dw, dh = int(sr * 0.7), int((sy1 - sy0) * 0.55)                                  # dvířka
    dx0 = int(cx - dw / 2)
    for y in range(sy1 - dh, sy1):
        for x in range(dx0, dx0 + dw):
            if y < sy1 - dh + dw / 2 and math.hypot(x + .5 - cx, y + .5 - (sy1 - dh + dw / 2)) > dw / 2:
                continue
            col = WOODP[2] if (x - dx0) % 3 else WOODP[1]
            s.px(x, y, col)
    s.px(dx0 + dw - 2, sy1 - dh // 2, GOLDC[3])
    wx, wy = int(cx + sr * 0.45), sy0 + 5                                            # okénko
    for y in range(wy - 2, wy + 3):
        for x in range(wx - 2, wx + 3):
            d = math.hypot(x - wx, y - wy)
            if d <= 2.6:
                s.px(x, y, c(0xFFE8A0) if d < 1.8 else WOODP[1])
    s.px(wx, wy, WOODP[2])
    sphere(s, cx, sy0, r, r * 0.66, CAPR, amb=0.3, mask=lambda x, y: y <= sy0 + 2)
    for x in range(int(cx - r), int(cx + r) + 1):
        s.px(x, sy0 + 2, CAPR[0]); s.px(x, sy0 + 3, (STEM[0] if abs(x - cx) < sr else (0, 0, 0, 0)))
    rng = np.random.default_rng(seed)
    for _ in range(7):
        a = rng.uniform(math.pi * 1.05, math.pi * 1.95); d = rng.uniform(0.25, 0.85)
        X, Y = cx + math.cos(a) * r * d, sy0 + math.sin(a) * r * 0.66 * d
        s.px(X, Y, c(0xFFFFFF)); s.px(X + 1, Y, c(0xFFFFFF)); s.px(X, Y + 1, c(0xF0E4E0))
    chx = int(cx + r * 0.45)                                                         # komínek
    for y in range(int(sy0 - r * 0.6) - 4, int(sy0 - r * 0.45)):
        s.px(chx, y, STONEP[2]); s.px(chx + 1, y, STONEP[1])
    s.px(int(cx - sr - 2), sy1 - 6, GOLDC[3]); s.px(int(cx - sr - 2), sy1 - 5, c(0xFFF0A0))    # lucernička
    return selout(s, 0.5, 0.85)


def wishing_well():
    """Studna přání: kruhová roubená kamenná obruba (pastelové kameny), uvnitř tma s hladinou a mincemi,
    dvě dřevěné vzpěry se šindelovou stříškou, rumpál s lanem a vědrem; na sloupku cedulka."""
    W, H = 40, 52
    s = Spr(W, H)
    cx, cy, r = 20, 38, 15
    for y in range(cy - 7, cy + 13):                                                 # obruba
        for x in range(cx - r, cx + r + 1):
            dx = (x + .5 - cx) / r
            if abs(dx) > 1:
                continue
            top = cy - 6 * math.sqrt(1 - dx * dx); bot = cy + 6 * math.sqrt(1 - dx * dx)
            if top <= y <= bot:
                q = math.hypot(dx, (y - cy) / 6)
                if q < 0.78:
                    col = c(0x101830) if y < cy + 1 else c(0x2A4060)                   # voda
                    if _h(x, y, 5) % 9 == 0:
                        col = GOLDC[3]                                                # mince
                else:
                    col = STONEP[4] if y < cy else STONEP[3]
                s.px(x, y, col)
            elif bot < y <= bot + 7:
                row = int(y - bot); off = 3 if row // 3 % 2 else 0
                col = STONEP[2] if (x + off) % 6 and row % 3 != 2 else STONEP[0]
                if dx > 0.55:
                    col = STONEP[1] if col is STONEP[2] else STONEP[0]
                s.px(x, y, col)
    for px_ in (cx - r + 2, cx + r - 2):                                             # vzpěry
        for y in range(10, cy - 2):
            s.px(px_, y, WOODP[3] if px_ < cx else WOODP[1]); s.px(px_ + 1, y, WOODP[1])
    for y in range(0, 12):                                                           # stříška
        hw = 4 + y * 1.6
        for x in range(int(cx - hw), int(cx + hw) + 1):
            col = CAPR[2] if (x + y // 2) % 4 else CAPR[1]
            if y > 9:
                col = CAPR[0]
            if x < cx:
                col = CAPR[3] if (x + y // 2) % 4 else CAPR[2]
            s.px(x, y, col)
    for x in range(cx - r + 3, cx + r - 1):                                          # rumpál
        s.px(x, 18, WOODP[2])
    s.vline(cx + 3, 19, 28, c(0xD8C8A0))
    for y in range(28, 32):
        for x in range(cx + 1, cx + 6):
            s.px(x, y, WOODP[2] if x < cx + 5 else WOODP[1])
    for y in range(22, 26):                                                          # cedulka
        for x in range(cx - r - 3, cx - r + 5):
            s.px(x, y, PAPER[3] if y < 25 else PAPER[1])
    for x in range(cx - r - 2, cx - r + 4, 2):
        s.px(x, 23, INK)
    return selout(s, 0.5, 0.85)


def pot_of_gold():
    """Hrnec zlata na konci duhy: černý litinový kotlík na třech nožkách, přetékající mincemi, třpyt."""
    W, H = 32, 28
    s = Spr(W, H)
    cx, cy = 16, 17
    sphere(s, cx, cy, 12, 9, IRON_, amb=0.25, mask=lambda x, y: y >= cy - 4)
    for x in range(cx - 12, cx + 13):                                                # lem
        s.px(x, cy - 4, IRON_[3]); s.px(x, cy - 3, IRON_[1])
    blobs(s, [(cx, cy - 6, 9), (cx - 5, cy - 5, 5), (cx + 6, cy - 5, 5), (cx - 1, cy - 9, 5)], GOLDC, amb=0.4)
    rng = np.random.default_rng(2)
    for _ in range(22):                                                              # mince
        x, y = int(rng.integers(cx - 10, cx + 11)), int(rng.integers(cy - 13, cy - 3))
        if s.opaque(x, y):
            s.px(x, y, GOLDC[4] if rng.random() < 0.4 else GOLDC[1])
    for (x, y) in ((cx + 12, H - 3), (cx + 14, H - 2), (cx - 13, H - 2)):            # rozsypané
        s.px(x, y, GOLDC[3]); s.px(x + 1, y, GOLDC[2])
    for fx in (cx - 8, cx, cx + 8):
        s.px(fx, H - 2, IRON_[1]); s.px(fx, H - 1, IRON_[0])
    return selout(s, 0.5, 0.85)


def clover_cloche():
    """Skleněný zvon na stolečku: pod sklem obří čtyřlístek v květináči, na skle odlesky; na stolečku cedulka."""
    W, H = 30, 48
    s = Spr(W, H)
    cx = 15
    for y in range(34, H):                                                           # stoleček
        for x in range(4, 26):
            col = WOODP[3] if y == 34 else WOODP[2] if y < 37 else (0, 0, 0, 0)
            if y >= 37 and x in (6, 7, 22, 23):
                col = WOODP[1]
            if col is not None and (len(col) == 4 and col[3] if hasattr(col, '__len__') else True):
                s.px(x, y, col)
    for y in range(28, 34):                                                          # květináč
        hw = 4 - (y - 28) * 0.3
        for x in range(int(cx - hw), int(cx + hw) + 1):
            s.px(x, y, c(0xC86A48) if x < cx + 1 else c(0x9A4A34))
    s.vline(cx, 18, 28, CLOVER[1])
    for (dx, dy) in ((0, -5), (5, 0), (-5, 0), (0, 5)):                              # čtyřlístek
        for yy in range(-3, 4):
            for xx in range(-3, 4):
                if (abs(xx) + abs(yy)) <= 3 and not (xx == 0 and yy == -3):
                    X, Y = cx + dx * 0.9 + xx, 16 + dy * 0.6 + yy * 0.7
                    s.px(X, Y, CLOVER[3] if xx < 0 and yy < 0 else CLOVER[2] if xx < 1 else CLOVER[1])
    s.px(cx, 16, CLOVER[0])
    for y in range(2, 34):                                                           # zvon
        hw = 11 if y > 12 else 11 * math.sqrt(max(0, 1 - ((12 - y) / 11) ** 2))
        for x in (int(cx - hw), int(cx + hw)):
            s.px(x, y, GLASS[2] if x < cx else GLASS[0])
        if y == int(cx * 0) or y < 4:
            pass
    for x in range(cx - 2, cx + 3):
        s.px(x, 1, GLASS[3])
    sphere(s, cx, 1, 1.6, 1.6, GLASS, amb=0.5)
    for y in range(8, 30):                                                           # odlesk
        if y % 4 != 0:
            s.px(cx - 7, y, GLASS[3])
    for x in range(cx - 11, cx + 12):
        s.px(x, 33, GLASS[1])
    for x in range(cx - 4, cx + 5):                                                  # cedulka
        s.px(x, 39, PAPER[3]); s.px(x, 40, PAPER[2])
    for x in range(cx - 3, cx + 4, 2):
        s.px(x, 39, INK)
    return selout(s, 0.55, 0.88)


def pixie_jars():
    """Polička se sklenicemi: dřevěný stolek, na něm pět zavařovacích sklenic různé výšky s látkovým víčkem
    a provázkem, v každé svítí chycená pixie (barevná záře, jen tečka – oživuje InteriorFxView)."""
    W, H = 46, 34
    s = Spr(W + 4, H)
    for y in range(18, H):
        for x in range(0, W):
            col = WOODP[3] if y == 18 else WOODP[2] if y < 21 else (0, 0, 0, 0)
            if y >= 21 and x in (2, 3, W - 4, W - 3):
                col = WOODP[1]
            if y < 21 or x in (2, 3, W - 4, W - 3):
                s.px(x, y, col)
        for x in range(W, W + 4):
            if 18 <= y < 21 and y - 18 >= x - W:
                s.px(x, y, WOODP[0])
    cols = [c(0xFFB0E0), c(0xB0E8FF), c(0xFFF0A0), c(0xC8B0FF), c(0xB0FFC8)]
    for i, (x0, h) in enumerate(((3, 12), (12, 15), (21, 11), (29, 14), (37, 12))):
        for y in range(18 - h, 18):
            for x in range(x0, x0 + 7):
                e = min(x - x0, x0 + 6 - x)
                col = GLASS[2] if e == 0 and x == x0 else GLASS[0] if e == 0 else _mix(GLASS[1], cols[i], 0.35)
                if y == 18 - h:
                    col = PASTEL[[0, 2, 1, 3, 5][i]]
                s.px(x, y, col)
        for x in range(x0 - 1, x0 + 8):
            s.px(x, 18 - h - 1, PASTEL[[0, 2, 1, 3, 5][i]])
        s.px(x0 + 3, 18 - h + 1, c(0xD8C8A0))
        s.px(x0 + 1, 18 - h + 3, GLASS[3])
    return selout(s, 0.55, 0.88)


def _mix(a, b, t):
    out = a.copy(); out[:3] = (a[:3] * (1 - t) + b[:3] * t).astype(np.uint8); return out


def rose_bush(seed=0, w=28, h=24, col=None):
    """Růžový keř: kulatý trs listí (koule) s květy v pastelové barvě."""
    s = Spr(w, h)
    rng = np.random.default_rng(seed)
    blobs(s, [(w / 2, h - 9, 9), (w * 0.3, h - 7, 6.5), (w * 0.7, h - 7, 6.5), (w * 0.45, h - 13, 6)], LAWN[:5], amb=0.25)
    rc = col or ROSE
    for _ in range(9):
        x, y = int(rng.integers(3, w - 3)), int(rng.integers(3, h - 4))
        if s.opaque(x, y) and s.opaque(x + 1, y + 1):
            s.px(x, y, rc[3]); s.px(x + 1, y, rc[2]); s.px(x, y + 1, rc[2]); s.px(x + 1, y + 1, rc[1])
    return selout(s, 0.55, 0.88)


def foxgloves(seed=0):
    """Trs náprstníků: vysoké stonky se zvonkovitými květy (fialové, růžové) odspodu nahoru menšími."""
    W, H = 22, 34
    s = Spr(W, H)
    rng = np.random.default_rng(seed)
    for k in range(4):
        x = 4 + k * 4 + int(rng.integers(-1, 2))
        top = int(rng.integers(2, 10))
        s.vline(x, top, H - 3, LAWN[1])
        fc = [c(0xC870D8), c(0xF08AB8), c(0xB890F0)][k % 3]
        for y in range(top + 2, H - 10, 3):
            s.px(x - 1, y, fc); s.px(x + 1, y, fc); s.px(x - 1, y + 1, _mix(fc, c(0x000000), 0.3))
    for y in range(H - 6, H):
        for x in range(1, W - 1):
            if abs(x - W / 2) < (W / 2 - 1) * (y - (H - 6)) / 6 + 2:
                s.px(x, y, LAWN[2] if (x + y) % 3 else LAWN[3])
    return selout(s, 0.55, 0.88)

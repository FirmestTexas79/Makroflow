"""
Krystalová jeskyně – 6. patro Věže Řádu Váhy, patro psychických a elektrických Makromonů. Věž tu stojí
v jeskyni: tmavá skála, krápníky, podzemní jezírko, a hlavně krystaly, které samy svítí (tyrkysové a
fialové). Kustodiát je těží a měří: bedny s úlomky, měřicí přístroj s kabely připojenými ke krystalu,
vozík na kolejích, lucerna na tyči. Ve 3/4, objemově, obrys selout.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
from pix import Spr, c  # noqa: E402
from figures import sphere, selout, blobs, _pick, BRASS, PAPER, INK, STEEL, RED, IRON_, shade_px  # noqa: E402

CROCK = [c(0x0E0C12), c(0x1C1822), c(0x2C2634), c(0x403848), c(0x58505E), c(0x766C78)]
CYAN = [c(0x0A3A44), c(0x16707E), c(0x2EB0BC), c(0x7AE8EC), c(0xE0FFFF)]
VIOLET = [c(0x2A1044), c(0x4E2280), c(0x8048C0), c(0xB88CEC), c(0xF0E0FF)]
WATER = [c(0x060C14), c(0x0C1A26), c(0x16303E), c(0x24505E)]
WOOD = [c(0x2A1A10), c(0x4A2E1A), c(0x6A4426), c(0x8C5E36)]


def _h(x, y, s=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (s * 83492791)) & 0xFFFF


def cave_floor(img, x0, y0, x1, y1):
    """Jeskynní dno: tmavá vlhká skála v hrbolech (dva tóny podle šumu), loužičky, drobné krystalky."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            n = math.sin(x * 0.13 + y * 0.07) + math.sin(x * 0.05 - y * 0.12 + 1.3) + 0.6 * math.sin((x + y) * 0.21)
            k = 2 if n > 0.6 else 1 if n > -0.8 else 1
            col = CROCK[k + (1 if _h(x, y) % 9 == 0 else 0)]
            if n > 1.6:
                col = CROCK[3]                                                      # hřbítek (světlejší)
            img[y, x] = col
    rng = np.random.default_rng(66)
    for _ in range(26):                                                             # loužičky s odleskem
        cx, cy = rng.uniform(x0 + 6, x1 - 6), rng.uniform(y0 + 6, y1 - 6)
        rx, ry = rng.uniform(2, 5), rng.uniform(1, 2.2)
        for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
            for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
                if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 < 1:
                    img[y, x] = WATER[2]
        img[int(cy), int(cx - rx / 2)] = WATER[3]
    for _ in range(40):                                                             # drobné krystalky v zemi
        x, y = int(rng.integers(x0 + 1, x1 - 1)), int(rng.integers(y0 + 1, y1 - 2))
        ramp = CYAN if rng.random() < 0.6 else VIOLET
        img[y, x] = ramp[3]; img[y + 1, x] = ramp[1]


def cave_wall(W, H):
    """Zadní stěna jeskyně: lámaná skála z plošek (Voronoi), každá ploška má tón podle natočení ke světlu,
    hrany tmavé; nahoru tmavne do stropu, ze kterého visí krápníky."""
    s = Spr(W, H)
    rng = np.random.default_rng(12)
    n = 70
    px_ = rng.uniform(0, W, n); py_ = rng.uniform(0, H, n)
    nrm = rng.uniform(-1, 1, (n, 2))
    yy, xx = np.mgrid[0:H, 0:W]
    d = (xx[None] - px_[:, None, None]) ** 2 + ((yy[None] - py_[:, None, None]) * 1.2) ** 2
    own = np.argmin(d, axis=0)
    for y in range(H):
        for x in range(W):
            o = own[y, x]
            v = 0.5 - nrm[o, 0] * 0.35 - nrm[o, 1] * 0.3
            v *= 0.45 + 0.55 * (y / H)                                              # nahoře tma (strop)
            k = max(0, min(5, int(v * 6)))
            edge = (x + 1 < W and own[y, x + 1] != o) or (y + 1 < H and own[y + 1, x] != o)
            s.px(x, y, CROCK[max(0, k - 2)] if edge else CROCK[k])
    for _ in range(26):                                                             # krápníky
        x = rng.uniform(2, W - 2); L = rng.uniform(6, 22); w = rng.uniform(1.5, 3.5)
        for y in range(int(L)):
            hw = w * (1 - y / L)
            for xx_ in range(int(x - hw), int(x + hw) + 1):
                s.px(xx_, y, CROCK[4] if xx_ < x else CROCK[2])
        s.px(int(x), int(L), CROCK[5])
    return s


def crystal_cluster(seed=0, w=34, h=40, violet=False):
    """Trs svítících krystalů: šestiboké hranoly různé délky trčí ze skalní paty šikmo vzhůru. Každý pixel se
    zařadí do hranolu (osa u, příčně v): levá ploška světlá, pravá tmavá, uprostřed zářivé jádro, špička bílá."""
    s = Spr(w, h)
    rng = np.random.default_rng(seed)
    R = VIOLET if violet else CYAN
    shards = []
    for i in range(7):
        a = -math.pi / 2 + rng.uniform(-0.75, 0.75)
        L = rng.uniform(h * 0.45, h * 0.85) * (1.0 if abs(a + math.pi / 2) < 0.35 else 0.75)
        bx = w / 2 + rng.uniform(-w * 0.2, w * 0.2)
        shards.append((a, L, bx, rng.uniform(2.6, 4.0)))
    shards.sort(key=lambda sh: -sh[1])                                              # dlouhé vzadu, krátké vpředu
    base_y = h - 6
    for (a, L, bx, wd) in shards:
        dx, dy = math.cos(a), math.sin(a)
        for y in range(h):
            for x in range(w):
                rx, ry = x + .5 - bx, y + .5 - base_y
                u = rx * dx + ry * dy
                v = -rx * dy + ry * dx
                if not (0 <= u <= L):
                    continue
                t = u / L
                half = wd if t < 0.72 else wd * (1 - t) / 0.28                      # špička
                if abs(v) > half:
                    continue
                side = v / max(0.4, half)
                tone = 3 if side < -0.35 else 2 if side < 0.35 else 1
                if abs(side) < 0.25 and 0.15 < t < 0.85:
                    tone = 4 if (int(u) % 6) < 2 else 3
                if t > 0.9:
                    tone = 4
                s.px(x, y, R[tone])
    for y in range(h - 8, h):                                                       # skalní pata
        for x in range(int(w * 0.18), int(w * 0.82)):
            d = ((x - w / 2) / (w * 0.32)) ** 2 + ((y - (h - 3)) / 4.5) ** 2
            if d < 1:
                s.px(x, y, CROCK[4] if y < h - 5 else CROCK[3] if x < w / 2 else CROCK[2])
    return selout(s, 0.55, 0.85)


def stalagmite(seed=0, h=30):
    """Krápník ze země: kužel z vrstev (světlejší kroužky), mokrý lesk vlevo, široká pata."""
    w = 14
    s = Spr(w, h)
    for y in range(h):
        t = y / h
        hw = 1 + 5.5 * t ** 1.3
        for x in range(int(w / 2 - hw), int(w / 2 + hw) + 1):
            nx = (x - w / 2) / max(1, hw)
            k = 4 if nx < -0.5 else 3 if nx < 0 else 2 if nx < 0.6 else 1
            if (y + seed) % 6 == 0:
                k = min(5, k + 1)
            s.px(x, y, CROCK[k])
    s.px(w // 2 - 1, 3, CROCK[5])
    return selout(s, 0.55, 0.85)


def crate(n=0):
    """Dřevěná bedna ve 3/4 (víko shora, čelo s prkny a kováním), z pootevřeného víka svítí úlomky krystalů,
    na čele štítek s razítkem."""
    W, H = 22, 20
    s = Spr(W + 3, H)
    for y in range(0, 6):
        for x in range(W):
            s.px(x, y, WOOD[3] if y < 2 else WOOD[2])
    for x in range(4, W - 4):                                                       # úlomky v bedně
        if x % 3:
            s.px(x, 2, CYAN[3] if x % 2 else VIOLET[3]); s.px(x, 3, CYAN[2] if x % 2 else VIOLET[2])
    for y in range(6, H):
        for x in range(W):
            s.px(x, y, WOOD[2] if (y - 6) % 5 else WOOD[1])
        s.px(0, y, WOOD[3]); s.px(W - 1, y, WOOD[0])
    for x in range(W, W + 3):
        for y in range(0, H):
            if y >= x - W:
                s.px(x, y, WOOD[0])
    for y in (7, H - 2):
        s.hline(1, W - 2, y, IRON_[2])
    for x in range(7, 15):
        s.px(x, 12, PAPER[3]); s.px(x, 13, PAPER[2])
    s.px(9, 12, RED[3]); s.px(10, 12, RED[3]); s.px(12, 13, INK)
    return selout(s, 0.5, 0.82)


def apparatus():
    """Měřicí přístroj Řádu: mosazná skříňka na nožkách, kruhový ukazatel, dvě elektronky nahoře,
    knoflíky; ze strany vede kabel (k nejbližšímu krystalu se kreslí na mapě)."""
    W, H = 26, 32
    s = Spr(W + 3, H)
    for y in range(8, 26):
        for x in range(W):
            s.px(x, y, BRASS[2] if y > 10 else BRASS[3])
        s.px(0, y, BRASS[4]); s.px(W - 1, y, BRASS[0])
    for x in range(W, W + 3):
        for y in range(8, 26):
            if y - 8 >= x - W:
                s.px(x, y, BRASS[0])
    for y in range(12, 22):                                                         # ciferník
        for x in range(3, 13):
            d = math.hypot(x - 7.5, y - 17)
            if d < 4.6:
                s.px(x, y, PAPER[3] if d < 3.8 else IRON_[2])
    s.line(7.5, 17, 10, 14, RED[3])
    for (x, col) in ((17, CYAN), (21, VIOLET)):                                     # elektronky
        for y in range(1, 8):
            for xx in range(x - 1, x + 2):
                s.px(xx, y, col[2] if xx == x else col[1])
        s.px(x, 3, col[4])
    for x in (16, 19, 22):
        sphere(s, x + .5, 18, 1.3, 1.3, IRON_, amb=0.35)
    for lx in (2, W - 4):
        for y in range(26, H):
            s.px(lx, y, IRON_[3]); s.px(lx + 1, y, IRON_[1])
    return selout(s, 0.5, 0.82)


def mine_cart():
    """Důlní vozík ve 3/4 na kousku kolejí: korba se rozšiřuje nahoru, vidíme do ní shora (vnitřní okraj, tmavé
    vnitřní stěny a navršené svítící úlomky), čelo s nýtovanými pásy, pravý bok ve stínu, kola na kolejích,
    pražce v perspektivě, na čele mosazný štítek s číslem."""
    W, H = 38, 32
    s = Spr(W, H)
    RAIL_Y = H - 4
    for i, x in enumerate(range(1, W - 1, 6)):                                      # pražce
        for y in range(RAIL_Y - 2, RAIL_Y + 3):
            for xx in range(x, x + 4):
                s.px(xx, y, WOOD[3] if y == RAIL_Y - 2 else WOOD[2] if y < RAIL_Y + 2 else WOOD[1])
    for x in range(W):                                                              # dvě kolejnice
        for ry in (RAIL_Y - 2, RAIL_Y + 1):
            s.px(x, ry, IRON_[4]); s.px(x, ry + 1, IRON_[2])
    top, body_bot = 6, RAIL_Y - 6
    def span(y):                                                                    # korba: nahoře širší
        t = (y - top) / (body_bot - top)
        return int(4 + t * 3), int(W - 6 - t * 3)
    for y in range(top, body_bot + 1):                                              # čelo korby
        x0, x1 = span(y)
        for x in range(x0, x1 + 1):
            col = IRON_[3] if y < top + 3 else IRON_[2]
            if x in (x0, x0 + 1):
                col = IRON_[4]
            s.px(x, y, col)
        for x in range(x1 + 1, x1 + 4):                                             # pravý bok (hloubka)
            if y - top >= x - x1 - 1:
                s.px(x, y, IRON_[1] if x == x1 + 1 else IRON_[0])
    for yb in (top + 4, body_bot - 3):                                              # nýtované pásy
        x0, x1 = span(yb)
        for x in range(x0, x1 + 1):
            s.px(x, yb, IRON_[1]); s.px(x, yb - 1, IRON_[4])
            if (x - x0) % 5 == 2:
                s.px(x, yb - 1, c(0xD8DCE6))
    for x in range(W // 2 - 4, W // 2 + 3):                                         # štítek
        s.px(x, top + 7, BRASS[3]); s.px(x, top + 8, BRASS[2]); s.px(x, top + 9, BRASS[1])
    s.px(W // 2 - 2, top + 8, INK); s.px(W // 2, top + 8, INK)
    for y in range(0, top + 1):                                                     # otvor shora (vnitřek + okraj)
        x0, x1 = 3, W - 5
        inset = (top - y) // 2
        for x in range(x0 + inset, x1 - inset + 1):
            s.px(x, y, IRON_[1])
    for x in range(3, W - 4):
        s.px(x, top, IRON_[4]); s.px(x, top - 1, IRON_[3])                          # přední hrana okraje
    for x in range(6, W - 7):                                                       # navršený náklad
        hump = 3.5 * math.sin((x - 6) / (W - 13) * math.pi)
        for y in range(int(top - 1 - hump), top - 1):
            ramp = CYAN if ((x // 3 + y) % 3) else VIOLET
            s.px(x, y, ramp[3] if y < top - 2 - hump / 2 else ramp[2])
        if x % 4 == 1:
            s.px(x, int(top - 1 - hump), (CYAN if x % 8 == 1 else VIOLET)[4])
    for wx in (9, W - 12):                                                          # kola na kolejích
        sphere(s, wx + .5, RAIL_Y - 3, 3.4, 3.4, IRON_, amb=0.3)
        s.px(wx, RAIL_Y - 3, IRON_[0]); s.px(wx + 1, RAIL_Y - 4, IRON_[4])
    return selout(s, 0.5, 0.82)


def lantern_pole():
    """Lucerna na tyči: dřevěná tyč, háček, lucerna s teplým plamínkem (záři kreslí mapa a InteriorFxView)."""
    s = Spr(14, 44)
    for y in range(4, 44):
        s.px(6, y, WOOD[3]); s.px(7, y, WOOD[1])
    for x in range(6, 12):
        s.px(x, 4, IRON_[3])
    for y in range(6, 16):                                                          # lucerna
        for x in range(8, 14):
            s.px(x, y, IRON_[2] if x in (8, 13) or y in (6, 15) else c(0xFFD890) if 9 < y < 14 else c(0xF0A040))
    s.px(10, 11, c(0xFFFFE0)); s.px(11, 11, c(0xFFF2C0))
    for x in range(5, 10):
        s.px(x, 43, CROCK[1])
    return selout(s, 0.5, 0.82)


def pool(img, cx, cy, rx, ry):
    """Podzemní jezírko (voda, ne díra): hladina odráží světlo krystalů – tyrkysový přechod od břehu ke středu,
    světlé pruhy odrazů a odlesky, mělčina u kraje průhledná na kamenité dno, kolem mokré oblé kameny."""
    rng = np.random.default_rng(int(cx * 7 + cy))
    POOLW = [c(0x081E28), c(0x0C2C38), c(0x123C4A), c(0x1A5262), c(0x3A8A98)]
    for y in range(int(cy - ry) - 4, int(cy + ry) + 5):
        for x in range(int(cx - rx) - 4, int(cx + rx) + 5):
            d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
            if d < 1:
                k = 3 if d > 0.75 else 2 if d > 0.4 else 1                          # u břehu mělčina
                t = (y - (cy - ry)) / (2 * ry)
                if t < 0.25 and d < 0.8:
                    k = min(4, k + 1)                                               # horní okraj odráží stěnu
                col = POOLW[k]
                if d > 0.8 and _h(x, y, 4) % 9 == 0:
                    col = CROCK[2]                                                  # kamínky dna v mělčině
                if int(y) in (int(cy - ry * 0.45), int(cy + ry * 0.1)) and abs(x - cx) < rx * (0.5 if y < cy else 0.3) and _h(x, 0, 5) % 4:
                    col = POOLW[4]                                                  # odraz krystalu (pruh)
                img[y, x] = col
    for i in range(11):                                                             # mokré kameny kolem
        a = i / 18 * 2 * math.pi + rng.uniform(-0.1, 0.1)
        sx, sy = cx + math.cos(a) * (rx + 0.5), cy + math.sin(a) * (ry + 0.5)
        r = rng.uniform(2.0, 3.4)
        for y in range(int(sy - r), int(sy + r) + 1):
            for x in range(int(sx - r), int(sx + r) + 1):
                dd = ((x - sx) / r) ** 2 + ((y - sy) / (r * 0.75)) ** 2
                if dd < 1:
                    img[y, x] = CROCK[4] if (x < sx and y < sy) else CROCK[3] if dd < 0.5 else CROCK[2]

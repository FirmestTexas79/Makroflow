"""
Archiv okruhů – 1. patro Věže Řádu Váhy (Akt II, fáze Spis 212). Noční směna: tma, zelené bankéřské lampy,
studené měsíční světlo z oken, nekonečné police. Hlavní motiv patra je spánek – lehátko noční směny,
které nikdo nepoužil, hodiny stojící na 6:12, spis kartografa, který „přestal spát“.

Všechny modely jsou ve 3/4 pohledu shora (horní plocha + čelo, pravý bok ve stínu), stínované objemově
(světlo shora zleva, tóny s ditherem) a s obrysem „selout“ (tmavší vlastní barva, ne černá čára).
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
from pix import Spr, c  # noqa: E402
from figures import (sphere, cylinder, selout, _pick, _grain, BRASS, WALNUT, PAPER, INK, STEEL, RED, GOLD,  # noqa: E402
                     NAVY, IRON_, GREEN_, shade_px)

# hřbety knih: (světlý, střední, tmavý)
BOOKS = [
    [c(0xA0403A), c(0x7A2A26), c(0x4E1816)],      # vínová
    [c(0x4A6290), c(0x2E426A), c(0x1A2842)],      # modrá
    [c(0x4E7A50), c(0x325636), c(0x1C3420)],      # zelená
    [c(0xC09A52), c(0x8E6E34), c(0x5A4420)],      # okrová
    [c(0x8A8E98), c(0x5E626C), c(0x3A3E46)],      # šedá
    [c(0x8C5C3A), c(0x664028), c(0x402818)],      # hnědá kůže
    [c(0x3E3A44), c(0x28262E), c(0x16141A)],      # černá
    [c(0x5E8A8C), c(0x3E6264), c(0x243C3E)],      # vybledlá tyrkysová
]
BOX = [c(0x4E4030), c(0x76644A), c(0x9A8664), c(0xB8A47E)]   # archivní krabice
WOOL = [c(0x2A3040), c(0x40485C), c(0x5C667C), c(0x8088A0), c(0xA8B0C4)]   # vlněná deka
LINEN = [c(0x7E8496), c(0xA8AEBC), c(0xCCD0DA), c(0xE8EAF0)]
NIGHT = [c(0x060914), c(0x0C1226), c(0x142040), c(0x22325C), c(0x3A4E80)]
MOON = [c(0x9AA4C0), c(0xC8D0E4), c(0xEEF2FA)]
LAMP_ON = [c(0xFFF2C0), c(0xFFDC80)]


def _wood(s, x, y, base, wd=WALNUT, seed=0.0):
    col = wd[base]
    if _grain(x, y, seed):
        col = wd[max(0, base - 1)]
    s.px(x, y, col)


def books_row(s, x0, x1, ybot, hmax, rng, boxes=0.07, gaps=0.08):
    """Řada knih stojících na polici: hřbet = světlý levý sloupec, střed, tmavý pravý; zlaté proužky,
    občas mezera, nakloněná kniha, ležící stoh nebo archivní krabice se štítkem a otvorem na prst."""
    x = x0
    while x <= x1:
        r = rng.random()
        if r < gaps and x > x0 + 2:                                              # mezera (+ nakloněná kniha)
            gw = int(rng.integers(2, 4))
            if rng.random() < 0.6 and x + gw <= x1:
                ramp = BOOKS[int(rng.integers(len(BOOKS)))]
                h = hmax - 1
                for i in range(h):                                               # nakloněná o ~1 px na 3 řádky
                    yy = ybot - i
                    xx = x + min(gw, i // 3)
                    s.px(xx, yy, ramp[0]); s.px(xx + 1, yy, ramp[2])
            x += gw + 1
            continue
        if r < gaps + boxes and x + 6 <= x1:                                       # archivní krabice
            bw, bh = 6, min(hmax, 7)
            for yy in range(ybot - bh + 1, ybot + 1):
                for xx in range(x, x + bw):
                    e = xx - x
                    s.px(xx, yy, BOX[3] if e == 0 else BOX[1] if e == bw - 1 else BOX[2])
            for xx in range(x + 1, x + bw - 1):                                  # štítek a otvor
                s.px(xx, ybot - bh + 2, PAPER[3])
            s.px(x + 2, ybot - bh + 3, INK); s.px(x + 3, ybot - bh + 3, INK)
            s.px(x + 2, ybot - 1, BOX[0]); s.px(x + 3, ybot - 1, BOX[0])
            x += bw + 1
            continue
        if r < gaps + boxes + 0.05 and x + 7 <= x1:                               # ležící stoh
            n = int(rng.integers(2, 4))
            for k in range(n):
                ramp = BOOKS[int(rng.integers(len(BOOKS)))]
                yy = ybot - k * 2
                for xx in range(x, x + 7 - (k % 2)):
                    s.px(xx, yy, ramp[1]); s.px(xx, yy - 1, ramp[0])
                s.px(x + 6 - (k % 2), yy, ramp[2])
            x += 8
            continue
        bw = int(rng.choice([1, 2, 2, 3, 3, 3]))
        h = hmax - int(rng.integers(0, 3))
        ramp = BOOKS[int(rng.integers(len(BOOKS)))]
        band = rng.random() < 0.45
        for yy in range(ybot - h + 1, ybot + 1):
            for k in range(bw):
                col = ramp[0] if (k == 0 and bw > 1) else ramp[2] if k == bw - 1 and bw > 1 else ramp[1]
                if band and bw > 1 and (yy == ybot - h + 2 or yy == ybot - 1):
                    col = GOLD[2] if k < bw - 1 else GOLD[1]
                s.px(x + k, yy, col)
        s.px(x + bw - 1, ybot - h + 1, ramp[2])
        x += bw


def bookshelf(w=50, h=48, seed=0, label=True):
    """Volně stojící regál ve 3/4: horní plocha (hloubka 6), rám se sloupky, 4 police s knihami
    (vnitřek přihrádky tmavý, horní řádky ve stínu police nad nimi), pravý bok, sokl, mosazný štítek okruhu."""
    s = Spr(w + 4, h)
    rng = np.random.default_rng(seed)
    TOP = 6
    for y in range(TOP):                                                          # horní plocha
        for x in range(w):
            _wood(s, x, y, 4 if y < 2 else 3, seed=seed * 0.7)
        s.px(0, y, WALNUT[5])
    s.hline(0, w - 1, 0, WALNUT[5])
    s.hline(0, w - 1, TOP - 1, WALNUT[5])                                         # přední hrana
    for x in range(w, w + 4):                                                     # pravý bok (hloubka)
        for y in range(h):
            if y >= (x - w) and y < h - (0 if x == w else 1):
                s.px(x, y, WALNUT[1] if x == w else WALNUT[0])
    for y in range(TOP, h):                                                       # čelo: sloupky a sokl
        for x in (0, 1):
            s.px(x, y, WALNUT[4] if x == 0 else WALNUT[3])
        for x in (w - 2, w - 1):
            s.px(x, y, WALNUT[2] if x == w - 2 else WALNUT[1])
    PL = h - 4
    for y in range(PL, h):
        for x in range(w):
            s.px(x, y, WALNUT[3] if y == PL else WALNUT[2] if y < h - 1 else WALNUT[1])
    nsh = 4
    cell = (PL - TOP) // nsh
    for i in range(nsh):
        y0 = TOP + i * cell
        y1 = y0 + cell - 2                                                        # vnitřek přihrádky
        for y in range(y0, y1 + 1):
            for x in range(2, w - 2):
                s.px(x, y, WALNUT[0] if (y - y0) < 2 or x > w - 5 else WALNUT[1])
        books_row(s, 3, w - 4, y1, cell - 3, rng)
        for x in range(2, w - 2):                                                 # stín police nad knihami
            if s.a[y0, x, 3]:
                shade_px(s, x, y0, 0.55)
            shade_px(s, x, y0 + 1, 0.72)
        for x in range(2, w - 2):                                                 # police (deska)
            s.px(x, y1 + 1, WALNUT[4]); s.px(x, y1 + 2, WALNUT[2])
    if label:                                                                     # mosazný štítek okruhu
        lx = w // 2 - 5
        for x in range(lx, lx + 10):
            s.px(x, 1, BRASS[4] if x == lx else BRASS[3]); s.px(x, 2, BRASS[2]); s.px(x, 3, BRASS[1])
        for x in range(lx + 2, lx + 8, 2):
            s.px(x, 2, BRASS[0])
    return selout(s, 0.5, 0.82)


def wall_shelves(w, h, seed=5):
    """Vestavěné police zadní stěny: římsa, sloupky po 24 px, šest pater knih, tma mezi nimi."""
    s = Spr(w, h)
    rng = np.random.default_rng(seed)
    for y in range(h):
        for x in range(w):
            s.px(x, y, WALNUT[0])
    for y in range(0, 5):                                                         # římsa
        for x in range(w):
            s.px(x, y, WALNUT[5] if y == 0 else WALNUT[4] if y < 3 else WALNUT[2] if y == 3 else WALNUT[1])
    for x in range(0, w, 3):
        s.px(x, 3, WALNUT[0])
    shelf_y = list(range(16, h - 4, 12))
    for y in shelf_y:
        for x in range(w):
            s.px(x, y, WALNUT[4]); s.px(x, y + 1, WALNUT[2])
    prev = 5
    for y in shelf_y:
        books_row(s, 1, w - 2, y - 1, min(10, y - prev - 2), rng, boxes=0.18)
        for x in range(w):
            shade_px(s, x, prev + 1, 0.55)
        prev = y + 1
    for x0 in range(0, w, 24):                                                    # sloupky
        for y in range(5, h):
            s.px(x0, y, WALNUT[4]); s.px(x0 + 1, y, WALNUT[3]); s.px(x0 + 2, y, WALNUT[1])
    for y in range(h - 4, h):                                                     # sokl
        for x in range(w):
            s.px(x, y, WALNUT[3] if y == h - 4 else WALNUT[2] if y < h - 1 else WALNUT[0])
    return s


def ladder(h=60):
    """Posuvný knihovní žebřík: dvě bočnice, příčle, nahoře mosazné háky na kolejnici, dole kolečka."""
    s = Spr(12, h)
    for y in range(2, h - 2):
        off = (h - y) // 14                                                       # mírně se rozšiřuje dolů
        for x in (2 - off + 2, 9 + off - 1):
            s.px(x, y, OAK_L[3]); s.px(x + 1, y, OAK_L[1])
    for y in range(6, h - 4, 6):
        off = (h - y) // 14
        for x in range(4 - off + 1, 9 + off - 1):
            s.px(x, y, OAK_L[4]); s.px(x, y + 1, OAK_L[1])
    for x in (3, 9):
        s.px(x, 0, BRASS[3]); s.px(x, 1, BRASS[2]); s.px(x + 1, 1, BRASS[1])
    for x in (0, 10):
        sphere(s, x + 1, h - 2, 1.4, 1.4, IRON_, amb=0.3)
    return selout(s, 0.5, 0.8)


OAK_L = [c(0x3A2414), c(0x5C3A20), c(0x825630), c(0xA87444), c(0xCC9A60)]


def wall_clock(r=7, hh=6, mm=12, pendulum=True):
    """Nástěnné hodiny: mosazný rám (okosený), krémový ciferník, ručičky na 6:12, kyvadlo dole."""
    s = Spr(r * 2 + 3, r * 2 + 12)
    cx, cy = r + 1, r + 1
    for y in range(r * 2 + 3):
        for x in range(r * 2 + 3):
            d = math.hypot(x + .5 - cx - .5, y + .5 - cy - .5)
            if d <= r + 1:
                if d > r - 1:
                    ang = math.atan2(cy - y, x - cx)
                    lt = math.cos(ang - 2.3)
                    s.px(x, y, BRASS[4] if lt > 0.5 else BRASS[3] if lt > -0.2 else BRASS[1])
                else:
                    s.px(x, y, PAPER[3] if d < r - 2.5 else PAPER[2])
    for i in range(12):
        a = i / 12 * 2 * math.pi
        s.px(round(cx + .5 + math.sin(a) * (r - 2)), round(cy + .5 - math.cos(a) * (r - 2)), PAPER[0] if i % 3 else INK)
    am = mm / 60 * 2 * math.pi
    ah = (hh % 12 + mm / 60) / 12 * 2 * math.pi
    s.line(cx + .5, cy + .5, cx + .5 + math.sin(am) * (r - 2), cy + .5 - math.cos(am) * (r - 2), INK)
    s.line(cx + .5, cy + .5, cx + .5 + math.sin(ah) * (r - 3.5), cy + .5 - math.cos(ah) * (r - 3.5), INK)
    s.px(cx, cy, RED[3])
    for y in range(r * 2 + 3, r * 2 + 12):                                       # skříňka s kyvadlem
        for x in range(cx - 3, cx + 4):
            s.px(x, y, WALNUT[3] if x == cx - 3 else WALNUT[1] if x == cx + 3 else WALNUT[0])
    if pendulum:                                                              # ve hře kyvadlo kreslí InteriorFxView
        s.vline(cx, r * 2 + 3, r * 2 + 9, BRASS[2])
        sphere(s, cx + .5, r * 2 + 10, 1.6, 1.6, BRASS, amb=0.35)
    return selout(s, 0.5, 0.8)


def night_window(w=20, h=52, seed=3, moon=True):
    """Vysoké okno do noci: mosazný rám s obloukem, tmavomodré nebe s přechodem, hvězdy, srpek měsíce,
    padající popel, který v měsíčním světle září; parapet."""
    s = Spr(w, h)
    rng = np.random.default_rng(seed)
    rad = w / 2
    for y in range(h):
        for x in range(w):
            if y < rad and math.hypot(x + .5 - rad, y + .5 - rad) > rad:
                continue
            s.px(x, y, BRASS[2] if x > 1 else BRASS[3])
    for y in range(2, h - 3):
        for x in range(2, w - 2):
            if y < rad and math.hypot(x + .5 - rad, y + .5 - rad) > rad - 2:
                continue
            t = y / h
            s.px(x, y, _pick(NIGHT, 0.85 - t * 0.7, x, y))
    for _ in range(10):                                                           # hvězdy
        x, y = int(rng.integers(3, w - 3)), int(rng.integers(4, h // 2))
        if s.opaque(x, y):
            s.px(x, y, MOON[1] if rng.random() < 0.7 else MOON[2])
    if moon:
        mx, my = w * 0.62, h * 0.24
        for y in range(int(my - 4), int(my + 5)):
            for x in range(int(mx - 4), int(mx + 5)):
                d1 = math.hypot(x + .5 - mx, y + .5 - my)
                d2 = math.hypot(x + .5 - mx - 1.8, y + .5 - my + 1.0)
                if d1 <= 3.6 and d2 > 3.0:
                    s.px(x, y, MOON[2] if d1 < 2.6 else MOON[1])
                elif d1 <= 4.4 and s.opaque(x, y) and d2 > 3.0:
                    s.px(x, y, NIGHT[4])                                          # halo
    for _ in range(12):                                                           # popel
        x, y = int(rng.integers(3, w - 3)), int(rng.integers(h // 3, h - 5))
        if s.opaque(x, y):
            s.px(x, y, MOON[0])
    s.vline(w // 2, 3, h - 4, BRASS[1])
    for y in (int(h * 0.42), int(h * 0.72)):
        s.hline(2, w - 3, y, BRASS[1])
    for x in range(w):                                                            # parapet
        s.px(x, h - 3, c(0xE6E9EF)); s.px(x, h - 2, c(0xA6ADBB)); s.px(x, h - 1, c(0x4E5464))
    return selout(s, 0.5, 0.8)


def banker_lamp(s, x, y, on=True):
    """Bankéřská lampa (9 × 7) na desce: mosazná noha, zelené stínidlo – válec ležící, s odleskem."""
    for i in range(9):                                                            # stínidlo
        for j in range(3):
            v = 1 - j / 3
            nx = (i + .5) / 9 * 2 - 1
            lt = 0.3 + 0.7 * max(0.0, -nx * 0.5 + 0.6) * v
            s.px(x + i, y + j, _pick(GREEN_, min(1.0, lt), x + i, y + j))
    s.px(x + 2, y, GREEN_[3]); s.px(x + 3, y, GREEN_[3])
    for i in range(1, 8):
        s.px(x + i, y + 3, LAMP_ON[0] if on else GREEN_[0])                      # spodek stínidla září
    s.vline(x + 4, y + 4, y + 5, BRASS[2]); s.px(x + 5, y + 5, BRASS[1])
    for i in range(2, 7):
        s.px(x + i, y + 6, BRASS[3] if i < 4 else BRASS[1])


def reading_table(w=64):
    """Dlouhý čtecí stůl ve 3/4: za ním tři židle (opěradla), deska s hloubkou a lesklým pruhem, dvě lampy,
    otevřená kniha, štosy spisů, kalamář, lupa, rozházené listy; čelo s lištou, nohy."""
    H = 40
    s = Spr(w, H)
    TOP0, TOPH = 10, 14
    for cxh in (w // 4, w // 2, 3 * w // 4):                                     # opěradla židlí za stolem
        for y in range(0, TOP0 + 2):
            for x in range(cxh - 5, cxh + 6):
                e = min(x - (cxh - 5), cxh + 5 - x)
                if y < 3:
                    s.px(x, y, OAK_L[4] if y == 0 else OAK_L[3])
                elif e <= 1:
                    s.px(x, y, OAK_L[3] if x < cxh else OAK_L[1])
                elif y in (6,):
                    s.px(x, y, OAK_L[2])
        s.hline(cxh - 4, cxh + 4, 2, OAK_L[1])
    for y in range(TOP0, TOP0 + TOPH):                                            # deska
        for x in range(w):
            t = (y - TOP0) / TOPH
            base = 4 if t < 0.25 else 3
            _wood(s, x, y, base, seed=2.2)
            for lx_ in (12, w - 14):                                              # odlesk lamp v laku
                if abs(x - lx_) < 5 - (y - TOP0) * 0.25 and 5 <= y - TOP0 <= 11 and (x + y) % 2 == 0:
                    s.px(x, y, WALNUT[5])
    s.hline(0, w - 1, TOP0, WALNUT[5])
    for y in range(TOP0 + TOPH, TOP0 + TOPH + 5):                                 # čelo s lištou
        for x in range(w):
            s.px(x, y, WALNUT[5] if y == TOP0 + TOPH else WALNUT[2] if y < TOP0 + TOPH + 4 else WALNUT[1])
        if y == TOP0 + TOPH + 2:
            for x in range(4, w - 4, 10):
                s.px(x, y, BRASS[2])
    for lx in (2, w - 5):                                                         # nohy (soustružené)
        for y in range(TOP0 + TOPH + 5, H):
            s.px(lx, y, WALNUT[4]); s.px(lx + 1, y, WALNUT[3]); s.px(lx + 2, y, WALNUT[1])
        s.px(lx, H - 6, WALNUT[5]); s.px(lx + 2, H - 6, WALNUT[0])
    # předměty na desce
    banker_lamp(s, 8, TOP0 - 2)
    banker_lamp(s, w - 18, TOP0 - 2)
    bx, by = w // 2 - 7, TOP0 + 4                                                 # otevřená kniha
    for x in range(bx, bx + 14):
        for y in range(by, by + 7):
            mid = abs(x - (bx + 7) + .5)
            s.px(x, y, PAPER[3] if mid > 1.5 else PAPER[1])
            if 1 <= y - by <= 5 and 2 < mid < 6 and (y - by) % 2 == 1:
                s.px(x, y, PAPER[0])
        s.px(x, by + 7, BOOKS[0][2])
    for k, (sx, sy) in enumerate(((w - 30, TOP0 + 2), (16, TOP0 + 6))):          # štosy spisů
        for j in range(4 - k):
            for x in range(sx, sx + 9):
                s.px(x, sy + 3 - j, PAPER[2] if j % 2 else BOX[2])
            s.px(sx + 8, sy + 3 - j, PAPER[0])
        s.hline(sx, sx + 8, sy + 4, PAPER[0])
    sphere(s, w - 9, TOP0 + 9, 1.6, 1.4, IRON_, amb=0.3)                          # kalamář
    s.px(w - 9, TOP0 + 7, PAPER[3]); s.px(w - 8, TOP0 + 6, PAPER[3])             # brk
    for i in range(4):                                                            # lupa
        s.px(10 + i, TOP0 + 11, BRASS[2])
    for (dx, dy) in ((14, -1), (15, -1), (16, 0), (16, 1), (15, 2), (14, 2), (13, 1), (13, 0)):
        s.px(dx, TOP0 + 10 + dy, BRASS[3])
    s.px(14, TOP0 + 10, c(0xF4F8FF))
    for (px_, py_) in ((w - 24, TOP0 + 10), (w // 2 + 9, TOP0 + 9)):              # volné listy
        for x in range(px_, px_ + 6):
            for y in range(py_, py_ + 3):
                s.px(x + (y - py_), y, PAPER[3] if y == py_ else PAPER[2])
    return selout(s, 0.5, 0.82)


def lectern():
    """Čtecí pult se Spisem 212: dubový podstavec, šikmá deska, na ní otevřený tmavomodrý spis s červeným razítkem
    a začerněnými řádky ███, mosazná lampička s kloboučkem; spis je nejsvětlejší věc v místnosti."""
    s = Spr(30, 40)
    for y in range(20, 36):                                                       # podstavec (sloup)
        for x in range(11, 19):
            s.px(x, y, WALNUT[4] if x < 13 else WALNUT[3] if x < 16 else WALNUT[1])
    for y in range(34, 40):                                                       # patka
        for x in range(6, 24):
            s.px(x, y, WALNUT[4] if y == 34 else WALNUT[3] if x < 15 else WALNUT[2])
        s.px(6, y, WALNUT[5]); s.px(23, y, WALNUT[0])
    for y in range(8, 22):                                                        # šikmá deska (lichoběžník)
        inset = (21 - y) // 4
        for x in range(1 + inset, 29 - inset):
            s.px(x, y, WALNUT[3])
        s.px(1 + inset, y, WALNUT[5]); s.px(28 - inset, y, WALNUT[1])
    for x in range(1, 29):
        s.px(x, 22, WALNUT[5]); s.px(x, 23, WALNUT[2]); s.px(x, 24, WALNUT[1])
    s.hline(4, 25, 21, BRASS[2])                                                  # lišta na papír
    for y in range(9, 21):                                                        # otevřený spis
        inset = (21 - y) // 4
        for x in range(3 + inset, 27 - inset):
            mid = x - 15
            if mid in (-1, 0):
                s.px(x, y, NAVY[2])
                continue
            s.px(x, y, PAPER[3] if abs(mid) > 2 else PAPER[2])
    for y in range(11, 20, 2):                                                    # řádky + ███
        inset = (21 - y) // 4
        for x in range(5 + inset, 13):
            s.px(x, y, INK if (y == 13 or y == 17) else PAPER[0])
        for x in range(17, 25 - inset):
            s.px(x, y, INK if y in (11, 15, 19) and x < 23 - inset else PAPER[0])
    for x in range(18, 23):                                                       # razítko 212
        for y in (16, 17, 18):
            if (x in (18, 22)) or y in (16, 18):
                s.px(x, y, RED[3])
    s.px(20, 17, RED[2])
    s.vline(26, 2, 9, BRASS[2]); s.px(27, 2, BRASS[1])                            # lampička
    for x in range(22, 29):
        s.px(x, 1, GREEN_[2] if x < 26 else GREEN_[1]); s.px(x, 2, GREEN_[1])
    for x in range(23, 28):
        s.px(x, 3, LAMP_ON[0])
    return selout(s, 0.5, 0.82)


def card_catalog():
    """Lístkový katalog 30 × 36: horní plocha, čelo z 5 × 6 šuplíčků (štítek + mosazný úchyt), jeden vytažený,
    soustružené nohy, pravý bok."""
    W, H = 28, 36
    s = Spr(W + 3, H)
    for y in range(4):
        for x in range(W):
            _wood(s, x, y, 4 if y < 2 else 3, OAK_L, 1.1)
    s.hline(0, W - 1, 0, OAK_L[4])
    for x in range(W, W + 3):
        for y in range(H - 6):
            if y >= x - W:
                s.px(x, y, OAK_L[1] if x == W else OAK_L[0])
    for y in range(4, H - 6):
        for x in range(W):
            s.px(x, y, OAK_L[2])
    for r in range(6):
        for col in range(5):
            x0, y0 = 1 + col * 5 + (col > 0) * 0, 5 + r * 4
            for x in range(x0, x0 + 5):
                s.px(x, y0, OAK_L[4] if x < x0 + 4 else OAK_L[1])
                s.px(x, y0 + 3, OAK_L[0])
            s.px(x0, y0 + 1, OAK_L[4]); s.px(x0, y0 + 2, OAK_L[4])
            s.px(x0 + 1, y0 + 1, PAPER[3]); s.px(x0 + 2, y0 + 1, PAPER[2])
            s.px(x0 + 2, y0 + 2, BRASS[3]); s.px(x0 + 3, y0 + 2, BRASS[1])
    for x in range(11, 16):                                                       # vytažený šuplíček
        for y in range(18, 23):
            s.px(x, y, OAK_L[3] if y == 18 else PAPER[3] if y < 21 else OAK_L[1])
    s.px(12, 19, PAPER[0]); s.px(14, 19, PAPER[0])
    for y in range(H - 6, H):
        for lx in (1, W - 4):
            s.px(lx, y, OAK_L[3]); s.px(lx + 1, y, OAK_L[2]); s.px(lx + 2, y, OAK_L[0])
    s.hline(0, W - 1, H - 6, OAK_L[1])
    return selout(s, 0.5, 0.82)


def daybed():
    """Lehátko noční směny: kovový rám s čely (mosazné koule), matrace, polštář, pečlivě složená deka.
    Nikdy nepoužité – deka je složená do ostrých hran a na polštáři není důlek."""
    W, H = 48, 30
    s = Spr(W, H)
    for y in range(4, 20):                                                        # matrace shora
        for x in range(4, W - 4):
            t = (y - 4) / 16
            s.px(x, y, LINEN[3] if t < 0.2 else LINEN[2])
    for x in range(4, W - 4):
        s.px(x, 20, LINEN[1]); s.px(x, 21, LINEN[0])                              # čelo matrace
        s.px(x, 22, IRON_[3]); s.px(x, 23, IRON_[1])                              # rám
    for y in range(5, 15):                                                        # polštář (bez důlku)
        for x in range(5, 19):
            e = min(x - 5, 18 - x, y - 5, 14 - y)
            if e == 0 and (x in (5, 18)) and (y in (5, 14)):
                continue
            s.px(x, y, LINEN[3] if (e >= 2 and y < 10) else LINEN[2] if e >= 1 else LINEN[1])
    s.hline(6, 17, 15, LINEN[0])
    for y in range(4, 20):                                                        # matrace o tón tmavší
        for x in range(4, 22):
            if not (5 <= x <= 18 and 5 <= y <= 15):
                s.px(x, y, LINEN[2] if y > 5 else LINEN[3])
    for y in range(7, 21):                                                        # složená deka (pravá část)
        for x in range(22, 42):
            fold = (x - 22) // 7
            v = 0.75 - (x - 22) % 7 * 0.06 - fold * 0.05
            if y < 9:
                v += 0.15
            s.px(x, y, _pick(WOOL, max(0.0, min(1.0, v)), x, y))
        s.px(29, y, WOOL[1]); s.px(36, y, WOOL[1])
    for x in range(22, 42):
        s.px(x, 7, WOOL[4]); s.px(x, 21, WOOL[0])
        if x % 4 == 0:
            s.px(x, 13, WOOL[1])                                                  # proužek
    for (ex, top) in ((1, 0), (W - 4, 6)):                                        # čela postele
        for y in range(top, 26):
            s.px(ex, y, IRON_[3]); s.px(ex + 1, y, IRON_[2]); s.px(ex + 2, y, IRON_[0])
        sphere(s, ex + 1.5, top + 1, 2, 2, BRASS, amb=0.35)
    for y in range(1, 15, 3):
        for x in range(3, 4):
            pass
    for y in range(3, 18, 4):                                                     # příčky hlavového čela
        s.hline(1, 3, y, IRON_[2])
    for lx in (5, W - 7):
        for y in range(24, H):
            s.px(lx, y, IRON_[2]); s.px(lx + 1, y, IRON_[0])
    return selout(s, 0.5, 0.82)


def book_cart():
    """Vozík na spisy: dvě police s krabicemi a svazky, mosazné madlo, kolečka."""
    W, H = 26, 28
    s = Spr(W, H)
    rng = np.random.default_rng(4)
    for y in range(0, 4):
        for x in range(W - 2):
            _wood(s, x, y, 4 if y < 2 else 3, OAK_L, 0.3)
    for (y0, y1) in ((4, 12), (14, 22)):
        for y in range(y0, y1):
            for x in range(1, W - 3):
                s.px(x, y, OAK_L[0])
        books_row(s, 2, W - 5, y1 - 1, y1 - y0 - 1, rng, boxes=0.3, gaps=0.1)
        for x in range(W - 2):
            s.px(x, y1, OAK_L[4]); s.px(x, y1 + 1, OAK_L[2])
    for y in range(0, 24):
        s.px(0, y, OAK_L[3]); s.px(W - 3, y, OAK_L[1])
    for y in range(2, 14):                                                        # madlo
        s.px(W - 1, y, BRASS[3] if y > 2 else BRASS[4])
    s.hline(W - 3, W - 1, 2, BRASS[3])
    for x in (3, W - 6):
        sphere(s, x, 25, 2.2, 2.2, IRON_, amb=0.3)
    return selout(s, 0.5, 0.82)


def balustrade(w, h=14):
    """Zábradlí galerie nad atriem: mosazné madlo, soustružené kuželky, pod ním propast do tmy."""
    s = Spr(w, h)
    for y in range(h):
        for x in range(w):
            s.px(x, y, NIGHT[0])
    for x in range(w):
        s.px(x, 0, BRASS[4]); s.px(x, 1, BRASS[3]); s.px(x, 2, BRASS[1])
        s.px(x, h - 4, WALNUT[3]); s.px(x, h - 3, WALNUT[2])
    for x0 in range(2, w - 2, 6):
        for y in range(3, h - 4):
            t = (y - 3) / (h - 7)
            wdt = 1 if 0.3 < t < 0.6 else 2
            for k in range(-wdt, wdt + 1):
                s.px(x0 + k, y, BRASS[3] if k < 0 else BRASS[2] if k == 0 else BRASS[0])
    return s


def parquet(img, x0, y0, x1, y1):
    """Parkety stromeček (rybí kost): pásy po 12 px, prkénka šikmo střídavě, spáry jen o tón tmavší,
    tóny ořechu podle prkénka (klidná plocha, ať nepřehluší nábytek)."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            band = (x - x0) // 12
            lx = (x - x0) % 12
            off = lx if band % 2 == 0 else 11 - lx
            k = (y + off) // 4
            seam = (y + off) % 4 == 0
            h = (band * 7919 + k * 104729) % 7
            base = [WALNUT[2], WALNUT[3], WALNUT[2], WALNUT[3], WALNUT[3], WALNUT[2], WALNUT[4]][h]
            col = WALNUT[1] if (seam or lx == 0) else base
            if not seam and lx == 1 and h == 6:
                col = WALNUT[5]
            img[y, x] = col


SEA = [c(0x10223A), c(0x1C3A5C), c(0x2C5A82), c(0x4A80A8), c(0x7AAAC8)]
LAND = [c(0x4A3A1E), c(0x7A6232), c(0xA88A4C), c(0xD0B474), c(0xEEDCA4)]


def globe():
    """Glóbus kartografa: koule s mořem a pevninami (stínovaná jako objem), mosazný poledník, dřevěná noha.
    Na jednom místě je přilepený papírek – Hunfreyho značka."""
    s = Spr(26, 36)
    cx, cy, r = 12.5, 12.5, 10
    for y in range(2, 24):
        for x in range(2, 24):
            dx, dy = (x + .5 - cx) / r, (y + .5 - cy) / r
            d2 = dx * dx + dy * dy
            if d2 > 1:
                continue
            nz = math.sqrt(1 - d2)
            lon = math.atan2(dx, nz) * 2.2 + 0.6
            lat = dy * 2.4
            land = math.sin(lon * 1.7) * math.cos(lat * 1.3) + 0.6 * math.sin(lon * 3.1 + lat * 2.2) > 0.55
            n = np.array([dx, dy, nz])
            v = 0.2 + 0.8 * max(0.0, float(n @ np.array([-0.55, -0.72, 0.45]) / 1.0))
            s.px(x, y, _pick(LAND if land else SEA, min(1.0, v), x, y))
    s.px(9, 8, PAPER[3]); s.px(10, 8, PAPER[3]); s.px(10, 9, RED[3])           # značka
    s.px(8, 6, c(0xFFFFFF)); s.px(9, 5, c(0xEEF4FF))                          # odlesk
    for i in range(30):                                                       # poledník (šikmý prstenec)
        a = i / 30 * 2 * math.pi
        x = cx + math.cos(a) * (r + 1.5) * 0.35 + math.sin(a) * 0.0
        y = cy + math.sin(a) * (r + 1.5)
        if math.cos(a) > -0.2 or True:
            s.px(round(x + (y - cy) * 0.25), round(y), BRASS[3] if math.sin(a) < 0 else BRASS[1])
    for y in range(24, 31):                                                   # noha
        s.px(12, y, WALNUT[4]); s.px(13, y, WALNUT[2])
    for x in range(5, 21):
        s.px(x, 31, WALNUT[4] if x < 12 else WALNUT[2]); s.px(x, 32, WALNUT[1])
    for lx in (5, 19):
        for y in range(33, 36):
            s.px(lx, y, WALNUT[3]); s.px(lx + 1, y, WALNUT[0])
    return selout(s, 0.5, 0.82)


def box_stack(seed=0):
    """Stoh archivních krabic na zemi (3/4: víko shora, čelo se štítkem, bok ve stínu), horní pootevřená."""
    s = Spr(22, 30)
    rng = np.random.default_rng(seed)
    y = 30
    for i in range(3):
        bw, bh = 16 - i % 2 * 2, 8
        x0 = 1 + int(rng.integers(0, 3))
        top = y - bh - 3
        for yy in range(top, top + 3):                                        # víko
            for xx in range(x0, x0 + bw):
                s.px(xx, yy, BOX[3] if yy == top else BOX[2])
        for yy in range(top + 3, y):                                          # čelo
            for xx in range(x0, x0 + bw):
                s.px(xx, yy, BOX[2] if xx > x0 else BOX[3])
            for xx in range(x0 + bw, x0 + bw + 3):
                if yy - top >= xx - x0 - bw:
                    s.px(xx, yy, BOX[1] if xx == x0 + bw else BOX[0])
        for xx in range(x0 + 4, x0 + 11):                                     # štítek
            s.px(xx, top + 5, PAPER[3]); s.px(xx, top + 6, PAPER[2])
        s.px(x0 + 5, top + 5, INK); s.px(x0 + 7, top + 5, INK); s.px(x0 + 8, top + 5, INK)
        y = top
    for xx in range(4, 12):                                                   # vyčnívající listy
        s.px(xx, y + 1, PAPER[3])
    return selout(s, 0.5, 0.82)


def step_stool():
    """Knihovní stupínek: dva schody (horní plocha + čelo), boky ve stínu."""
    s = Spr(16, 16)
    for (x0, x1, top) in ((0, 13, 8), (0, 7, 1)):
        for y in range(top, 16):
            for x in range(x0, x1 + 1):
                if y < top + 3:
                    s.px(x, y, OAK_L[4] if y == top else OAK_L[3])
                else:
                    s.px(x, y, OAK_L[2] if x < x1 else OAK_L[1])
    for y in range(9, 16):
        s.px(14, y, OAK_L[0]); s.px(15, y, OAK_L[0] if y > 10 else OAK_L[1])
    return selout(s, 0.5, 0.82)


def nightstand():
    """Noční stolek u lehátka: šuplík, budík na 6:12, sklenice vody s odleskem."""
    s = Spr(16, 26)
    for y in range(8, 11):
        for x in range(14):
            s.px(x, y, WALNUT[4] if y == 8 else WALNUT[3])
    for y in range(11, 24):
        for x in range(14):
            s.px(x, y, WALNUT[2] if x else WALNUT[4])
        for x in (14, 15):
            if y - 8 >= x - 14:
                s.px(x, y, WALNUT[1] if x == 14 else WALNUT[0])
    for x in range(2, 12):
        s.px(x, 13, WALNUT[1]); s.px(x, 18, WALNUT[4])
    s.px(6, 15, BRASS[3]); s.px(7, 15, BRASS[1])
    for lx in (1, 11):
        s.px(lx, 24, WALNUT[2]); s.px(lx, 25, WALNUT[1])
    sphere(s, 4.5, 5.5, 3.2, 3.2, BRASS, amb=0.35)                             # budík
    s.px(3, 2, BRASS[3]); s.px(6, 2, BRASS[3])
    for (x, y) in ((4, 5), (5, 5), (4, 6), (5, 6)):
        s.px(x, y, PAPER[3])
    s.px(4, 6, INK); s.px(5, 5, INK)
    for y in range(3, 9):                                                     # sklenice vody
        for x in (9, 10, 11):
            s.px(x, y, c(0xB8D0E8) if x == 9 else c(0x7A98BC) if y > 4 else c(0xDCEAF6))
    s.px(9, 4, c(0xFFFFFF))
    return selout(s, 0.5, 0.82)


def map_roll():
    """Opřený tubus s mapou: kožený válec, mosazná víčka, vyčnívající zažloutlý papír."""
    s = Spr(8, 22)
    cylinder(s, 1, 5, 4, 21, LEATHER_, amb=0.25)
    for x in range(1, 6):
        s.px(x, 4, BRASS[3] if x < 4 else BRASS[1]); s.px(x, 20, BRASS[2])
    for y in range(0, 4):
        s.px(3, y, PAPER[3]); s.px(4, y, PAPER[1])
    return selout(s, 0.5, 0.82)


LEATHER_ = [c(0x24140C), c(0x3E2416), c(0x5E3A24), c(0x845436), c(0xA87048)]


def loose_papers(img, pts, rng):
    """Rozházené listy na podlaze (3–5 px, některé zmačkané) – stopa od studovny k pultu se spisem."""
    for (x, y) in pts:
        w, h = int(rng.integers(3, 6)), int(rng.integers(2, 4))
        sk = int(rng.integers(-1, 2))
        for j in range(h):
            for i in range(w):
                X, Y = x + i + j * sk, y + j
                img[Y, X, :3] = (PAPER[3] if j == 0 else PAPER[2])[:3]
            img[y + h, x + j * sk + w - 1, :3] = PAPER[0][:3]

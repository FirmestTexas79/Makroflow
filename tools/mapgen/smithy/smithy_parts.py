"""
Díly Staré kovárny v Horách (region 1, docs/adr/0118). Pohled 3/4 shora, světlo shora zleva, objemy stínované
po tónech s ditherem, obrys selout (tmavší verze vlastní barvy). Předloha: nábytek kovárny od Samuela – výheň se
zděným komínovým pláštěm a žhavým uhlím, kožený měch na rámu, kovadlina na pařezu, brus, stojan se zbraněmi,
regály s lahvičkami, ponk, hromady uhlí a rudy, sudy a bedny.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "ashplain")); sys.path.insert(0, os.path.join(HERE, "..", "tower"))
from pix import Spr, c  # noqa: E402
from figures import sphere, cylinder, selout, _pick, shade_px  # noqa: E402

STONE = [c(0x1E1A1E), c(0x34303A), c(0x4C4752), c(0x666070), c(0x847E8C), c(0xA49EAA)]
BRICK = [c(0x2A1210), c(0x4E2018), c(0x763222), c(0x9A4A2E), c(0xBC6A42), c(0xD88E5E)]
WOOD = [c(0x1C100C), c(0x34200F), c(0x52341A), c(0x6E4A26), c(0x8E6436), c(0xB08450)]
DWOOD = [c(0x140C0A), c(0x24160F), c(0x382416), c(0x4C3220), c(0x62432C)]
IRON = [c(0x0E0E14), c(0x1C1E26), c(0x30333E), c(0x4A4E5C), c(0x6E7484), c(0xA0A8B8), c(0xE0E6EE)]
LEATHER = [c(0x24120E), c(0x42221A), c(0x643828), c(0x8A5236), c(0xAE7248), c(0xD09A68)]
EMBER = [c(0x3A0A08), c(0x781A0E), c(0xC03A12), c(0xF06A1A), c(0xFFA83A), c(0xFFE08A), c(0xFFFBE0)]
COALR = [c(0x08080C), c(0x15141C), c(0x25232E), c(0x3A3744), c(0x55515E)]
WATER = [c(0x0C1622), c(0x14263A), c(0x1E3A56), c(0x2E5678), c(0x5A86A8)]
BRASS = [c(0x3A2410), c(0x6A4A1C), c(0xA07428), c(0xD0A040), c(0xF0D070), c(0xFFF2B0)]
GLASS = {"red": [c(0x3A0C14), c(0x7A1A24), c(0xC0303A), c(0xF07070)], "blue": [c(0x0C1A3A), c(0x1A3A7A), c(0x3A6AC0), c(0x90C0F8)],
         "green": [c(0x0C2A14), c(0x1A5A28), c(0x38A048), c(0x9AE8A0)], "amber": [c(0x3A2208), c(0x7A4A10), c(0xC08020), c(0xF8D070)]}
CLOTH = [c(0x2A1A14), c(0x4A3022), c(0x6E4C34), c(0x92704C)]
ORE = [c(0x1E1A22), c(0x3A3440), c(0x5C5464), c(0x7E7686)]
COPPER = [c(0x4A1E10), c(0x8A4420), c(0xC07040), c(0xF0A878)]
WALLST = [c(0x161012), c(0x2A2022), c(0x3C3030), c(0x52423E), c(0x68564E), c(0x806C60)]


def _h(x, y, s=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (s * 83492791)) & 0xFFFF


# ── zdi a podlaha ───────────────────────────────────────────────────────────
def stone_wall(W, H, soot_x=None):
    """Zadní zeď z lomového kamene (nepravidelné kvádry, malta), nahoře trám a krokve, u výhně sazemi zčernalá."""
    s = Spr(W, H)
    rows, y = [], 10
    while y < H:
        h = 9 + _h(y, 1) % 5
        rows.append((y, min(h, H - y))); y += h
    for ri, (y0, h) in enumerate(rows):
        x = -(_h(ri, 7) % 14)
        while x < W:
            w = 12 + _h(x, ri, 3) % 12
            tone = 2 + (_h(x, ri, 9) % 2) + (1 if _h(x, ri, 4) % 7 == 0 else 0)
            for yy in range(y0, y0 + h):
                for xx in range(max(0, x), min(W, x + w)):
                    e_top, e_left = yy - y0, xx - x
                    e_bot, e_right = y0 + h - 1 - yy, x + w - 1 - xx
                    if min(e_top, e_left, e_bot, e_right) == 0:
                        col = WALLST[0]
                    elif e_top == 1 or e_left == 1:
                        col = WALLST[min(5, tone + 1)]
                    elif e_bot == 1 or e_right == 1:
                        col = WALLST[max(1, tone - 1)]
                    else:
                        col = WALLST[tone] if _h(xx, yy, 5) % 9 else WALLST[tone - 1]
                    s.px(xx, yy, col)
            x += w
    for x in range(W):                                                     # trám nahoře
        for y in range(10):
            col = WOOD[4] if y == 1 else WOOD[3] if y < 4 else WOOD[2] if y < 8 else WOOD[1]
            if (x + _h(x // 7, y, 2) % 3) % 23 == 0:
                col = WOOD[1]
            s.px(x, y, col)
    for x0 in (0, W - 8):                                                  # sloupy v rozích
        for y in range(H):
            for x in range(x0, x0 + 8):
                k = x - x0
                s.px(x, y, WOOD[1] if k in (0, 7) else WOOD[4] if k == 1 else WOOD[3] if k < 5 else WOOD[2])
    if soot_x is not None:                                                 # saze nad výhní
        for y in range(H):
            for x in range(W):
                d = max(0.0, 1 - abs(x - soot_x) / 34) * (1 - y / H * 0.5)
                if d > 0 and s.a[y, x, 3]:
                    k = 1 - 0.55 * d
                    s.a[y, x, :3] = (s.a[y, x, :3] * k).astype(np.uint8)
    return s


def floor(img, x0, y0, x1, y1):
    """Udusaná hlína s kamennými deskami u výhně, rozsypané uhlí a okuje."""
    EARTH = [c(0x2A2018), c(0x3A2C20), c(0x4A3828), c(0x5A4632)]
    for y in range(y0, y1):
        for x in range(x0, x1):
            n = _h(x // 2, y // 2, 11) % 16
            col = EARTH[2] if n > 5 else EARTH[1] if n > 1 else EARTH[3]
            if _h(x, y, 4) % 53 == 0:
                col = COALR[1]
            if _h(x, y, 8) % 97 == 0:
                col = EARTH[0]
            img[y, x] = col


def flagstones(img, x0, y0, x1, y1):
    for y in range(y0, y1):
        for x in range(x0, x1):
            row = (y - y0) // 10
            bx = (x - x0 + (row % 2) * 7) // 14
            seam = (y - y0) % 10 == 0 or (x - x0 + (row % 2) * 7) % 14 == 0
            tone = 2 + _h(bx, row, 3) % 2
            img[y, x] = STONE[0] if seam else STONE[tone] if _h(x, y, 6) % 11 else STONE[tone - 1]


# ── výheň ───────────────────────────────────────────────────────────────────
def forge(W=60, H=100):
    """Zděná výheň: kamenný stůl s cihlovým okrajem a žhavým uhlím, nad ním zděný plášť (digestoř) až ke stropu."""
    s = Spr(W, H)
    base_top = H - 34                                                       # horní plocha stolu
    # plášť / komín: lichoběžník zužující se nahoru, cihly, spodní hrana železný pás
    for y in range(0, base_top - 8):
        t = y / (base_top - 8)
        hw = 9 + (W / 2 - 10) * t ** 1.6
        for x in range(int(W / 2 - hw), int(W / 2 + hw) + 1):
            row = y // 5; off = (row % 2) * 4
            u = (x + off) // 8
            seam = y % 5 == 0 or (x + off) % 8 == 0
            nx = (x - W / 2) / max(hw, 1)
            sh = 0.75 - nx * 0.35
            col = BRICK[0] if seam else BRICK[max(1, min(5, int(sh * 5) + (_h(u, row) % 2)))]
            if y > base_top - 22:                                           # saze dole na plášti
                col = c(0x241414) if not seam else BRICK[0]
            s.px(x, y, col)
    for y in range(base_top - 10, base_top - 6):                           # železný lem pláště s nýty
        for x in range(2, W - 2):
            k = y - (base_top - 10)
            s.px(x, y, IRON[5] if k == 0 else IRON[4] if k == 1 else IRON[2])
        for x in range(5, W - 4, 8):
            s.px(x, base_top - 9, IRON[6])
    for y in range(base_top - 6, base_top):                                # temný prostor pod pláštěm se zářením
        for x in range(4, W - 4):
            t = (y - (base_top - 6)) / 6
            s.px(x, y, EMBER[1] if t > 0.5 and _h(x, y) % 3 else c(0x1A0A08))
    # stůl výhně: horní plocha (cihlový okraj + uhlí), přední stěna z kamene
    for y in range(base_top, base_top + 12):
        for x in range(1, W - 1):
            edge = x < 6 or x > W - 7 or y < base_top + 3 or y > base_top + 9
            if edge:
                s.px(x, y, BRICK[4] if y == base_top else BRICK[3] if (x + y) % 6 else BRICK[1])
            else:
                d = math.hypot((x - W / 2) / (W / 2 - 6), (y - base_top - 6) / 4)
                v = max(0.0, 1 - d)
                if _h(x, y, 2) % 4 == 0:
                    col = COALR[2]
                else:
                    col = EMBER[min(6, int(v * 7))] if v > 0.15 else COALR[_h(x, y) % 3 + 1]
                s.px(x, y, col)
    for y in range(base_top + 12, H):
        for x in range(1, W - 1):
            row = (y - base_top - 12) // 7; off = (row % 2) * 6
            seam = (y - base_top - 12) % 7 == 0 or (x + off) % 12 == 0
            sh = 0.85 - (x / W) * 0.45
            col = STONE[0] if seam else STONE[max(1, min(5, int(sh * 5)))]
            s.px(x, y, col)
    for x in range(W // 2 - 7, W // 2 + 8):                                # popelník v čele
        for y in range(H - 12, H - 3):
            s.px(x, y, c(0x120A08) if y > H - 11 else STONE[1])
        if _h(x, 0) % 3 == 0:
            s.px(x, H - 4, EMBER[2])
    return selout(s, 0.55, 0.8)


def bellows(W=36, H=30):
    """Velký kovářský měch na kozlíku (předloha): spodní deska, kožený klín v záhybech, otevřená horní deska
    šikmo nahoru doprava s madlem, železná tryska vlevo míří do výhně."""
    s = Spr(W, H)
    for (x0) in (8, W - 9):                                                # kozlík
        for y in range(19, H):
            s.px(x0, y, WOOD[4]); s.px(x0 + 1, y, WOOD[2])
    for x in range(5, W - 4):
        s.px(x, H - 4, WOOD[3]); s.px(x, H - 3, WOOD[1])
    nose_x, nose_y = 5, 16

    def top_y(x):                                                          # horní deska: od trysky šikmo nahoru
        return nose_y - (x - nose_x) * 0.36

    for x in range(nose_x, W - 2):                                         # kožený klín
        yt = int(top_y(x)) + 2
        for y in range(yt, 18):
            t = (x - nose_x) / (W - 2 - nose_x)
            fold = int((x - nose_x) / 4) % 2
            col = LEATHER[4] if fold == 0 else LEATHER[2]
            if y > 16:
                col = LEATHER[1]
            if x == nose_x or (fold == 0 and (x - nose_x) % 4 == 0):
                col = LEATHER[1]
            s.px(x, y, col)
    for x in range(nose_x, W - 1):                                         # horní deska (silná, se světlou hranou)
        y = int(top_y(x))
        s.px(x, y, WOOD[5]); s.px(x, y + 1, WOOD[4]); s.px(x, y + 2, WOOD[2])
    for x in range(nose_x, W - 2):                                         # spodní deska
        s.px(x, 18, WOOD[4]); s.px(x, 19, WOOD[2])
    for y in range(int(top_y(W - 2)) - 6, int(top_y(W - 2)) + 1):          # madlo
        s.px(W - 3, y, WOOD[4]); s.px(W - 2, y, WOOD[2])
    for x in range(0, nose_x + 1):                                         # tryska
        s.px(x, 16, IRON[5]); s.px(x, 17, IRON[3]); s.px(x, 18, IRON[1])
    for (x, y) in ((16, int(top_y(16)) + 1), (24, int(top_y(24)) + 1)):     # mosazné nýty
        s.px(x, y, BRASS[4])
    return selout(s, 0.5, 0.8)


def anvil_stump(W=30, H=30):
    """Kovadlina (roh, plocha s odleskem, pas, pata) na dubovém pařezu s letokruhy."""
    s = Spr(W, H)
    # pařez: válec + horní elipsa s letokruhy
    cylinder(s, 7, W - 8, 16, H - 2, WOOD, amb=0.25)
    for y in range(13, 19):
        for x in range(7, W - 7):
            if ((x + .5 - W / 2) / (W / 2 - 7)) ** 2 + ((y + .5 - 16) / 3) ** 2 <= 1:
                r = math.hypot((x - W / 2) / 2.2, y - 16)
                s.px(x, y, WOOD[5] if int(r) % 2 else WOOD[4])
    # kovadlina
    for x in range(1, W - 1):                                              # plocha + roh vlevo
        if x < 7:
            y0 = 7 + (7 - x) // 3
            for y in range(y0, 10):
                s.px(x, y, IRON[4] if y == y0 else IRON[3])
        else:
            for y in range(5, 10):
                s.px(x, y, IRON[6] if y == 5 and x < 18 else IRON[5] if y == 5 else IRON[4] if y < 8 else IRON[3])
    for x in range(10, W - 9):                                              # pas
        for y in range(10, 13):
            s.px(x, y, IRON[3] if x < 14 else IRON[2])
    for x in range(8, W - 6):                                              # pata
        for y in range(13, 16):
            s.px(x, y, IRON[4] if y == 13 else IRON[2] if x < W - 12 else IRON[1])
    s.px(W - 5, 6, IRON[1]); s.px(W - 6, 6, IRON[1])                       # otvor pro trn
    for k in range(6):                                                     # kladivo opřené o kovadlinu
        s.px(W - 4 - k // 2, 9 + k, WOOD[4])
    s.rect(W - 4, 7, W - 2, 9, IRON[5])
    return selout(s, 0.5, 0.82)


def quench_tub(W=24, H=18):
    """Kalicí káď: půlka sudu s obručemi, tmavá voda s odleskem a párou."""
    s = Spr(W, H)
    cylinder(s, 1, W - 2, 5, H - 1, DWOOD, amb=0.3)
    for y in (8, 14):
        for x in range(1, W - 1):
            s.px(x, y, IRON[3] if x < W // 2 else IRON[2])
    for y in range(1, 7):
        for x in range(1, W - 1):
            if ((x + .5 - W / 2) / (W / 2 - 1)) ** 2 + ((y + .5 - 4) / 3) ** 2 <= 1:
                edge = ((x + .5 - W / 2) / (W / 2 - 2.5)) ** 2 + ((y + .5 - 4) / 2) ** 2 > 1
                s.px(x, y, DWOOD[4] if edge else WATER[2] if (x + y) % 5 else WATER[3])
    s.px(8, 3, WATER[4]); s.px(9, 3, WATER[4])
    return selout(s, 0.5, 0.82)


def grindstone(W=26, H=28):
    s = Spr(W, H)
    for (x0, x1) in ((3, 5), (W - 6, W - 4)):
        for y in range(10, H):
            for x in range(x0, x1 + 1):
                s.px(x, y, WOOD[3] if x == x0 else WOOD[2])
    for x in range(2, W - 2):
        s.px(x, H - 4, WOOD[3]); s.px(x, H - 3, WOOD[1])
    for y in range(1, 22):                                                  # kámen (kotouč z boku: elipsa)
        for x in range(7, W - 7):
            dx, dy = (x + .5 - W / 2) / (W / 2 - 7), (y + .5 - 11) / 10
            if dx * dx + dy * dy <= 1:
                n = np.array([dx, dy, math.sqrt(max(0, 1 - dx * dx - dy * dy))])
                v = 0.3 + 0.7 * max(0.0, float(n @ np.array([-0.5, -0.7, 0.5])))
                s.px(x, y, _pick(STONE, v, x, y))
    s.rect(W // 2 - 1, 10, W // 2, 12, IRON[5])                             # osa
    for k in range(6):                                                     # klika
        s.px(W - 4 + (k > 3), 11 + k, IRON[4])
    s.rect(W - 4, 16, W - 2, 17, WOOD[4])
    return selout(s, 0.5, 0.82)


def weapon_rack(W=42, H=34):
    """Dřevěný stojan se zbraněmi: meče, sekera, kopí (předloha)."""
    s = Spr(W, H)
    for (x0) in (2, W - 5):
        for y in range(4, H):
            s.px(x0, y, WOOD[4]); s.px(x0 + 1, y, WOOD[3]); s.px(x0 + 2, y, WOOD[2])
    for yb in (6, H - 8):
        for x in range(1, W - 1):
            s.px(x, yb, WOOD[5]); s.px(x, yb + 1, WOOD[3]); s.px(x, yb + 2, WOOD[1])
    blades = [(9, "sword"), (16, "axe"), (23, "sword"), (30, "spear"), (36, "sword")]
    for x, kind in blades:
        top = 0 if kind == "spear" else 3
        for y in range(top, H - 4):
            if kind == "sword":
                if y < 20:
                    s.px(x, y, IRON[6] if y < 3 else IRON[5]); s.px(x + 1, y, IRON[3])
                elif y == 20:
                    s.rect(x - 2, y, x + 3, y, BRASS[3])
                else:
                    s.px(x, y, LEATHER[3]); s.px(x + 1, y, LEATHER[1])
            elif kind == "axe":
                s.px(x, y, WOOD[4]); s.px(x + 1, y, WOOD[2])
                if 4 <= y <= 11:
                    w = 4 - abs(y - 7) // 2
                    s.rect(x + 2, y, x + 2 + w, y, IRON[5] if y < 7 else IRON[4])
            else:
                s.px(x, y, WOOD[4]); s.px(x + 1, y, WOOD[2])
                if y < 5:
                    s.rect(x - (2 - y // 2) + 1, y, x + (2 - y // 2), y, IRON[5])
    return selout(s, 0.5, 0.82)


def potion_shelf(W=34, H=40):
    """Regál s lahvičkami, sklenicemi a svitky (předloha)."""
    s = Spr(W, H)
    for y in range(H):
        for x in (0, 1, W - 2, W - 1):
            s.px(x, y, WOOD[3] if x in (0, W - 2) else WOOD[2])
    for x in range(W):
        for y in range(2, H, 1):
            pass
    shelves = (10, 22, 34)
    for yb in shelves:
        for x in range(W):
            s.px(x, yb, WOOD[5]); s.px(x, yb + 1, WOOD[3]); s.px(x, yb + 2, WOOD[1])
    for x in range(2, W - 2):                                             # záda
        for y in range(0, H - 3):
            if s.a[y, x, 3] == 0:
                s.px(x, y, DWOOD[2] if (x // 6) % 2 else DWOOD[1])
    items = [(4, 10, "red"), (9, 10, "blue"), (14, 10, "jar"), (21, 10, "green"), (26, 10, "amber"),
             (4, 22, "jar"), (10, 22, "scroll"), (18, 22, "red"), (23, 22, "green"), (28, 22, "blue"),
             (5, 34, "amber"), (12, 34, "jar"), (20, 34, "scroll"), (27, 34, "red")]
    for x, yb, kind in items:
        if kind in GLASS:
            g = GLASS[kind]
            for y in range(yb - 6, yb):
                w = 1 if y < yb - 4 else 2
                for dx in range(-w + 1, w + 1):
                    s.px(x + dx, y, g[3] if dx == -w + 1 and y > yb - 4 else g[2] if dx <= 0 else g[1])
            s.px(x, yb - 7, LEATHER[3])
        elif kind == "jar":
            for y in range(yb - 5, yb):
                for dx in range(-2, 3):
                    s.px(x + dx, y, CLOTH[3] if y == yb - 5 else c(0x8C9AA0) if dx < 0 else c(0x5E6A70))
        else:
            for dx in range(-3, 3):
                s.px(x + dx, yb - 2, c(0xE8DCB8)); s.px(x + dx, yb - 1, c(0xB8A880))
            s.px(x - 3, yb - 2, c(0x9A3A2A))
    return selout(s, 0.5, 0.82)


def workbench(W=50, H=24):
    """Ponk: deska s hloubkou, na ní kus kůže, kladívka, kleště, rozdělaná čepel; dole bedna s nářadím."""
    s = Spr(W, H)
    for y in range(0, 8):                                                   # horní plocha
        for x in range(W):
            s.px(x, y, WOOD[5] if y == 0 else WOOD[4] if (x + _h(x // 9, y)) % 13 else WOOD[3])
    for x in range(W):
        s.px(x, 8, WOOD[2]); s.px(x, 9, WOOD[1])
    for x0 in (2, W - 5):
        for y in range(10, H):
            s.px(x0, y, WOOD[3]); s.px(x0 + 1, y, WOOD[2]); s.px(x0 + 2, y, WOOD[1])
    for y in range(14, H - 1):                                              # bedna pod ponkem
        for x in range(16, 34):
            s.px(x, y, DWOOD[3] if y == 14 else DWOOD[2] if (x - 16) % 6 else DWOOD[1])
    for y in range(2, 7):                                                   # kůže
        for x in range(4, 16):
            if (x + y) % 9:
                s.px(x, y, LEATHER[3] if y < 4 else LEATHER[2])
    for x in range(20, 34):                                                 # čepel
        s.px(x, 3, IRON[6] if x < 26 else IRON[5]); s.px(x, 4, IRON[3])
    s.rect(34, 3, 36, 4, BRASS[3])
    for k in range(5):                                                      # kleště
        s.px(39 + k, 2 + k // 2, IRON[4]); s.px(39 + k, 5 - k // 2, IRON[3])
    s.rect(44, 4, 47, 5, IRON[5]); s.px(43, 4, WOOD[4]); s.px(42, 4, WOOD[4])   # kladívko
    return selout(s, 0.5, 0.82)


def heap(W, H, ramp, seed, chunks=None):
    """Hromada (uhlí / ruda): kupka z kulatých kusů stínovaných zvlášť."""
    s = Spr(W, H)
    r = np.random.default_rng(seed)
    pts = []
    for _ in range(W * H // 10):
        x = r.uniform(3, W - 3)
        top = H - (H - 2) * math.sin(math.pi * (x / W)) ** 0.8
        y = r.uniform(top + 1, H - 2)
        pts.append((x, y, r.uniform(1.6, 2.8)))
    for (x, y, rr) in sorted(pts, key=lambda p: p[1]):
        sphere(s, x, y, rr, rr * 0.85, chunks or ramp, amb=0.15)
    return selout(s, 0.5, 0.8)


def ingots(W=18, H=10):
    s = Spr(W, H)
    for (x0, y0, ramp) in ((1, 4, COPPER), (8, 4, COPPER), (4, 1, IRON[2:])):
        for y in range(y0, y0 + 4):
            for x in range(x0, x0 + 8):
                s.px(x, y, ramp[-1] if y == y0 else ramp[-2] if x < x0 + 6 else ramp[1])
    return selout(s, 0.55, 0.85)


def barrel(W=14, H=18):
    s = Spr(W, H)
    cylinder(s, 0, W - 1, 3, H - 1, WOOD, amb=0.3)
    for y in (6, H - 4):
        for x in range(W):
            s.px(x, y, IRON[3] if x < W // 2 else IRON[2])
    for y in range(0, 5):
        for x in range(W):
            if ((x + .5 - W / 2) / (W / 2)) ** 2 + ((y + .5 - 3) / 2.5) ** 2 <= 1:
                s.px(x, y, WOOD[4] if (x + y) % 4 else WOOD[3])
    return selout(s, 0.5, 0.82)


def crate(W=16, H=16):
    s = Spr(W, H)
    for y in range(H):
        for x in range(W):
            top = y < 5
            col = WOOD[5] if top and y == 0 else WOOD[4] if top else WOOD[3] if (x // 4) % 2 else WOOD[2]
            if not top and (x in (0, W - 1) or y in (5, H - 1)):
                col = WOOD[1]
            s.px(x, y, col)
    for k in range(W - 2):                                                 # vzpěra
        s.px(1 + k, H - 2 - int(k * (H - 8) / (W - 2)), WOOD[4])
    return selout(s, 0.5, 0.82)


def log_pile(W=30, H=16):
    s = Spr(W, H)
    for row, (n, y) in enumerate(((4, 10), (3, 5), (2, 0))):
        for k in range(n):
            x = 2 + k * 7 + row * 3.5
            for yy in range(y, y + 6):
                for xx in range(int(x), int(x) + 6):
                    d = math.hypot(xx + .5 - (x + 3), yy + .5 - (y + 3))
                    if d <= 3:
                        s.px(xx, yy, WOOD[5] if d < 1.2 else WOOD[4] if d < 2.2 else WOOD[2])
    return selout(s, 0.5, 0.82)


# ── na zeď ──────────────────────────────────────────────────────────────────
def window(W=22, H=28):
    s = Spr(W, H)
    for y in range(H):
        for x in range(W):
            frame = x < 2 or x > W - 3 or y < 2 or y > H - 3 or x == W // 2 or y == H // 2
            if frame:
                s.px(x, y, WOOD[4] if (x < 2 or y < 2) else WOOD[2])
            else:
                t = y / H
                s.px(x, y, c(0xBFD8EC) if t < 0.3 else c(0x8EB4D0) if t < 0.6 else c(0x7A9A6A))
    for x in range(2, W - 2):                                               # parapet
        s.px(x, H - 1, WOOD[5])
    return s


def tool_rack(W=40, H=24):
    """Dřevěná lišta s háky: kleště, kladiva, podkovy, pilník (na zeď)."""
    s = Spr(W, H)
    for x in range(W):
        s.px(x, 2, WOOD[5]); s.px(x, 3, WOOD[3]); s.px(x, 4, WOOD[1])
    tools = [(4, "tongs"), (11, "hammer"), (17, "shoe"), (23, "tongs"), (30, "hammer"), (36, "shoe")]
    for x, kind in tools:
        s.px(x, 5, IRON[5])
        if kind == "tongs":
            for y in range(6, 22):
                s.px(x - (y > 16) * (y - 16) // 2, y, IRON[4]); s.px(x + 1 + (y > 16) * (y - 16) // 2, y, IRON[2])
        elif kind == "hammer":
            for y in range(6, 18):
                s.px(x, y, WOOD[4]); s.px(x + 1, y, WOOD[2])
            s.rect(x - 2, 17, x + 3, 20, IRON[4]); s.rect(x - 2, 17, x + 3, 17, IRON[5])
        else:
            for a in np.linspace(0.15, math.pi - 0.15, 14):
                s.px(round(x + math.cos(a) * 3), round(11 - math.sin(a) * -4), IRON[5])
    return selout(s, 0.55, 0.85)


def shield_axes(W=26, H=24):
    """Štít se zkříženými sekerami na zdi (předloha)."""
    s = Spr(W, H)
    for k in range(22):
        s.px(2 + k, 1 + k, WOOD[4]); s.px(23 - k, 1 + k, WOOD[3])
    for (x0, d) in ((2, 1), (20, -1)):
        for y in range(0, 6):
            w = 3 - abs(y - 3)
            s.rect(x0 + (0 if d > 0 else -w), y, x0 + (w if d > 0 else 0), y, IRON[5])
    for y in range(4, 22):
        w = 8 if y < 14 else 8 - (y - 14)
        for x in range(13 - w, 13 + w):
            edge = x in (13 - w, 13 + w - 1) or y == 4
            s.px(x, y, IRON[4] if edge else (c(0x2E4A7A) if x < 13 else c(0x22365C)))
    s.rect(12, 8, 13, 17, BRASS[4]); s.rect(9, 11, 16, 12, BRASS[3])
    return selout(s, 0.55, 0.85)


def sign_hammer(W=22, H=14):
    """Cedule KOVÁRNA nad dveřmi uvnitř – deska s kladivem a kovadlinou."""
    s = Spr(W, H)
    for y in range(H):
        for x in range(W):
            s.px(x, y, WOOD[1] if x in (0, W - 1) or y in (0, H - 1) else WOOD[3] if y % 4 else WOOD[2])
    s.rect(6, 8, 15, 9, IRON[5]); s.rect(9, 10, 12, 11, IRON[3]); s.rect(4, 7, 6, 8, IRON[4])
    s.rect(12, 3, 16, 5, IRON[5]); s.px(11, 6, WOOD[5]); s.px(10, 7, WOOD[5])
    return s

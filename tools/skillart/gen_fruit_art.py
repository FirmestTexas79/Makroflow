"""
Sadařství (region 2) – pixel art ovoce, česáků, Sadařova setu a ovoce na stromech v sadu.
Výstup: app/src/main/java/cz/uhk/macroflow/pokemon/skills/FruitArt.kt (ARGB IntArray jako ostatní *Art.kt)
Náhled:  python tools/skillart/gen_fruit_art.py --preview out.png

Ovoce se kreslí z tvaru (maska) a normál koule/elipsy: světlo zleva shora → index barevné rampy (5–6 odstínů),
tmavý okraj vpravo dole, odlesk, stopka a lístek; obrys dělá Kotlin (Px.outline) jako u ostatních ikon.
"""
import math, os, sys
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..")
OUT = os.path.join(ROOT, "app", "src", "main", "java", "cz", "uhk", "macroflow", "pokemon", "skills", "FruitArt.kt")
OUTLINE = (0x1E, 0x14, 0x0C)

L = np.array([-0.62, -0.70, 0.36]); L /= np.linalg.norm(L)

# ── rampy (tmavá → světlá) ──
APPLE = [(0x5A, 0x0E, 0x16), (0x96, 0x16, 0x22), (0xC8, 0x26, 0x2C), (0xE8, 0x48, 0x3A), (0xF8, 0x7E, 0x5C), (0xFF, 0xD6, 0xC0)]
PEAR = [(0x4A, 0x58, 0x10), (0x7A, 0x92, 0x1A), (0xA8, 0xC0, 0x2C), (0xD0, 0xDE, 0x52), (0xEC, 0xF2, 0x8E), (0xFF, 0xFC, 0xDA)]
ORANGE = [(0x7A, 0x30, 0x06), (0xBA, 0x52, 0x0C), (0xE6, 0x76, 0x14), (0xFA, 0x9C, 0x2C), (0xFF, 0xC4, 0x66), (0xFF, 0xEC, 0xC4)]
BANANA = [(0x7A, 0x5A, 0x0E), (0xB8, 0x8C, 0x16), (0xE2, 0xBA, 0x22), (0xF6, 0xDA, 0x48), (0xFF, 0xF0, 0x8E), (0xFF, 0xFC, 0xDC)]
LEAF = [(0x1E, 0x5A, 0x1E), (0x2E, 0x7A, 0x2A), (0x4E, 0xA4, 0x3A), (0x8A, 0xD0, 0x5A)]
STEM = [(0x3E, 0x26, 0x12), (0x6A, 0x44, 0x22)]
BLUSH = (0xD8, 0x6A, 0x30)
UNRIPE = [(0x3A, 0x5E, 0x1A), (0x5A, 0x8A, 0x24), (0x86, 0xB4, 0x38), (0xB8, 0xDA, 0x6A)]


def blank(w, h):
    return np.zeros((h, w, 4), np.uint8)


def put(img, x, y, c):
    if 0 <= y < img.shape[0] and 0 <= x < img.shape[1]:
        img[y, x, :3] = c; img[y, x, 3] = 255


def shade_body(img, inside, normal, ramp, rim=0.22, spec=None):
    """inside(x, y) → bool, normal(x, y) → (nx, ny, nz); lambert + tmavý okraj vpravo dole."""
    h, w = img.shape[:2]
    m = np.zeros((h, w), bool)
    for y in range(h):
        for x in range(w):
            if inside(x + 0.5, y + 0.5): m[y, x] = True
    for y in range(h):
        for x in range(w):
            if not m[y, x]: continue
            n = np.array(normal(x + 0.5, y + 0.5)); n /= np.linalg.norm(n)
            lam = max(0.0, float(n @ L))
            v = 0.30 + 0.78 * lam
            # okraj vpravo dole o stupeň tmavší (objem), sousedi mimo tvar
            edge = (x + 1 >= w or not m[y, x + 1]) or (y + 1 >= h or not m[y + 1, x])
            if edge: v -= rim
            idx = int(np.clip(round(v * (len(ramp) - 2)), 0, len(ramp) - 2))
            put(img, x, y, ramp[idx])
    if spec:
        for (x, y, k) in spec:
            if 0 <= y < h and 0 <= x < w and m[y, x]: put(img, x, y, ramp[k])
    return m


def sphere_normal(cx, cy, rx, ry):
    def f(x, y):
        dx, dy = (x - cx) / rx, (y - cy) / ry
        return (dx, dy, math.sqrt(max(0.05, 1 - dx * dx - dy * dy)))
    return f


# ════════════════════════════════════════════════════════════════════════════
def apple(S=12):
    """Jablko: dva laloky nahoře (důlek u stopky), stopka, lístek vpravo, odlesk vlevo nahoře."""
    img = blank(S, S)
    k = S / 12.0
    cx, cy, r = 5.6 * k, 7.2 * k, 3.9 * k
    def inside(x, y):
        dx, dy = x - cx, y - cy
        rr = r * (1 - 0.10 * max(0.0, -dy / r) * math.exp(-(dx / (1.6 * k)) ** 2))   # důlek nahoře
        rr *= (1 - 0.06 * max(0.0, dy / r))                                             # lehce užší spodek
        return dx * dx / 1.04 + dy * dy <= rr * rr
    m = shade_body(img, inside, sphere_normal(cx - 0.6 * k, cy - 0.8 * k, r * 1.05, r), APPLE,
                   spec=[(int(3 * k), int(4 * k), 5), (int(3 * k), int(5 * k), 4), (int(4 * k), int(4 * k), 4)])
    # žíhání slupky: svislé světlejší proužky na osvětlené straně
    for y in range(S):
        for x in range(S):
            if m[y, x] and x in (int(4 * k), int(6 * k)) and 5 * k < y < 9 * k and img[y, x, 0] < 0xF0:
                c = img[y, x, :3].astype(int); put(img, x, y, tuple(np.clip(c + np.array([20, 10, 6]), 0, 255)))
    # důlek u stopky
    put(img, int(cx), int(cy - r + 1), APPLE[0])
    # stopka a lístek
    sx = int(round(cx)); put(img, sx, int(cy - r), STEM[1]); put(img, sx + 1, int(cy - r) - 1, STEM[0])
    for (x, y, c) in ((sx + 2, int(cy - r) - 1, LEAF[2]), (sx + 3, int(cy - r) - 1, LEAF[3]), (sx + 2, int(cy - r), LEAF[1]),
                      (sx + 3, int(cy - r) - 2, LEAF[2]), (sx + 4, int(cy - r) - 2, LEAF[1])):
        put(img, x, y, c)
    return img


def pear(S=12):
    """Hruška: úzký krk, široké bříško, rezavé tečky, červené líčko na osvětlené straně, stopka."""
    img = blank(S, S)
    k = S / 12.0
    def inside(x, y):
        b = ((x - 5.8 * k) / (3.7 * k)) ** 2 + ((y - 7.6 * k) / (3.1 * k)) ** 2 <= 1
        t = ((x - 5.6 * k) / (1.9 * k)) ** 2 + ((y - 4.9 * k) / (2.0 * k)) ** 2 <= 1
        mid = abs(x - 5.7 * k) <= (1.7 + (y - 4.6 * k) / (3.0 * k) * 1.9) * k and 4.6 * k <= y <= 7.6 * k
        return b or t or mid
    def normal(x, y):
        if y > 6.0 * k:
            return sphere_normal(5.2 * k, 7.0 * k, 4.2 * k, 3.9 * k)(x, y)
        return sphere_normal(5.1 * k, 3.4 * k, 2.6 * k, 3.0 * k)(x, y)
    m = shade_body(img, inside, normal, PEAR, spec=[(int(3 * k), int(6 * k), 5), (int(4 * k), int(3 * k), 4), (int(3 * k), int(7 * k), 4)])
    for (x, y) in ((7, 8), (5, 9), (8, 6), (6, 5)):                                     # rezavé tečky
        xx, yy = int(x * k), int(y * k)
        if m[yy, xx]: put(img, xx, yy, (0x9A, 0x7A, 0x22))
    for (x, y) in ((3, 8), (3, 9), (4, 9)):                                             # líčko
        xx, yy = int(x * k), int(y * k)
        if m[yy, xx]: put(img, xx, yy, BLUSH)
    top = min(y for y in range(S) if m[y].any())
    sx = int(round(5.6 * k))
    put(img, sx, top - 1, STEM[1]); put(img, sx + 1, top - 2, STEM[0])
    put(img, sx - 1, top - 1, LEAF[2]); put(img, sx - 2, top - 1, LEAF[3]); put(img, sx - 2, top - 2, LEAF[1])
    return img


def orange(S=12):
    """Pomeranč: koule s porézní kůrou (tečky), lístek s žilkou a stopka, odlesk."""
    img = blank(S, S)
    k = S / 12.0
    cx, cy, r = 5.6 * k, 7.1 * k, 3.95 * k
    m = shade_body(img, lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r, sphere_normal(cx - 0.4 * k, cy - 0.6 * k, r, r), ORANGE,
                   spec=[(int(3 * k), int(4 * k), 5), (int(4 * k), int(4 * k), 4), (int(3 * k), int(5 * k), 4)])
    for y in range(S):                                                                   # póry kůry
        for x in range(S):
            if m[y, x] and (x * 7 + y * 5) % 9 == 0 and 3 * k < x < 9 * k:
                c = img[y, x, :3].astype(int); put(img, x, y, tuple(np.clip(c - 26, 0, 255)))
    top = int(cy - r)
    put(img, int(cx), top + 1, ORANGE[0])                                                # úponek stopky
    put(img, int(cx), top, STEM[1])
    for (dx, dy, c) in ((1, -1, LEAF[1]), (2, -1, LEAF[2]), (3, -1, LEAF[3]), (2, -2, LEAF[2]), (3, -2, LEAF[2]), (4, -2, LEAF[1]), (1, 0, LEAF[0])):
        put(img, int(cx) + dx, top + dy, c)
    return img


BANANA_ROWS = [
    "............",
    "..........t.",
    ".........ts.",
    "........eds.",
    "........edb.",
    ".......eecb.",
    "......eedcb.",
    "....feedccb.",
    "..ffeedcbb..",
    ".keedccbba..",
    ".kbbbaaa....",
    "............",
]


def banana(S=12):
    """Banán (ručně): srpek zahnutý vzhůru, světlo na vnitřní (horní) straně, tmavá spodní hrana, hnědé konce."""
    img = blank(S, S)
    pal = {"a": BANANA[0], "b": BANANA[1], "c": BANANA[2], "d": BANANA[3], "e": BANANA[4], "f": BANANA[5],
           "k": STEM[0], "s": STEM[0], "t": STEM[1]}
    for y, row in enumerate(BANANA_ROWS):
        for x, ch in enumerate(row):
            if ch in pal: put(img, x, y, pal[ch])
    put(img, 6, 8, (0x8A, 0x60, 0x1A))                                                   # hnědá tečka
    return img



# ── Česák na ovoce 16 × 16: násada, drátěný koš s prsty, uvnitř jablko ──
METALS = {
    "old": ((0xC8, 0xC0, 0xB0), (0x8C, 0x84, 0x78), (0x5A, 0x52, 0x4A)),
    "copper": ((0xF2, 0xB0, 0x7A), (0xC8, 0x74, 0x3C), (0x7E, 0x42, 0x20)),
    "silver": ((0xF8, 0xFB, 0xFF), (0xC4, 0xCC, 0xD8), (0x78, 0x82, 0x8E)),
    "gold": ((0xFF, 0xF2, 0xA8), (0xE8, 0xB4, 0x2E), (0x9A, 0x6A, 0x10)),
}
WOODS = {"old": ((0xA8, 0x7A, 0x48), (0x6C, 0x44, 0x20)), "copper": ((0xC4, 0x8A, 0x4A), (0x6C, 0x44, 0x20)),
         "silver": ((0xE4, 0xDE, 0xCE), (0x8E, 0x86, 0x76)), "gold": ((0xC0, 0x64, 0x3A), (0x6E, 0x2E, 0x18))}
BAGS = {"old": ((0xC4, 0xA0, 0x70), (0x8A, 0x6A, 0x40)), "copper": ((0x7E, 0xB0, 0x5A), (0x4A, 0x7A, 0x34)),
        "silver": ((0x8E, 0xC8, 0xE0), (0x4E, 0x88, 0xA8)), "gold": ((0xE8, 0x5A, 0x6A), (0x9A, 0x26, 0x3A))}
PICKER_ROWS = [
    "................",
    "..........l.l.l.",
    ".........lMlMlMl",
    ".........M.....D",
    ".........MbBrRbD",
    ".........MbrRrbD",
    "..........MbbbD.",
    "...........DDD..",
    "..........wv....",
    ".........wv.....",
    "........wv......",
    ".......wv.......",
    "......wv........",
    ".....wv.........",
    "....wv..........",
    "...wv...........",
]


def picker(tier):
    img = blank(16, 16)
    ml, mm, md = METALS[tier]; wl, wd = WOODS[tier]; bl, bd = BAGS[tier]
    pal = {"l": ml, "M": mm, "D": md, "w": wl, "v": wd, "b": bd, "B": bl, "r": APPLE[2], "R": APPLE[4]}
    for y, row in enumerate(PICKER_ROWS):
        for x, ch in enumerate(row):
            if ch in pal: put(img, x, y, pal[ch])
    if tier == "old":                                                                    # rez a díra v pytli
        put(img, 11, 2, (0x9C, 0x5A, 0x2E)); put(img, 9, 4, (0x9C, 0x5A, 0x2E)); put(img, 12, 5, (0x5A, 0x3E, 0x22))
    return img


# ── Sadařův set 16 × 16 ──
SET_PAL = {
    # slaměný klobouk
    "H": (0xF0, 0xD4, 0x7A), "h": (0xC8, 0xA0, 0x48), "j": (0x96, 0x72, 0x2E), "r": (0xC8, 0x26, 0x2C), "R": (0xF0, 0x5A, 0x48),
    "f": (0xFF, 0xE0, 0xEA), "y": (0xFF, 0xD8, 0x4A),
    # zástěra
    "G": (0x6E, 0xB4, 0x4A), "g": (0x3E, 0x7E, 0x2E), "q": (0x2A, 0x58, 0x22), "s": (0xD8, 0xC8, 0xA0), "S": (0xA8, 0x94, 0x6A),
    "p": (0xD0, 0xDE, 0x52), "P": (0x8E, 0xA8, 0x1E),
    # kalhoty
    "L": (0xD8, 0xC4, 0x9A), "l": (0xA6, 0x8E, 0x62), "m": (0x74, 0x60, 0x40), "o": (0xF2, 0x8C, 0x1E), "O": (0xB8, 0x52, 0x0C),
    "b": (0x8A, 0x5E, 0x30),
    # holínky
    "U": (0x5E, 0xA0, 0x46), "u": (0x34, 0x6E, 0x2C), "v": (0x22, 0x4A, 0x1E), "Y": (0xF6, 0xDA, 0x48), "z": (0x2A, 0x24, 0x20),
}
SET_ROWS = {
    "hat": [
        "................",
        "......jjjj......",
        ".....jHHHHh.....",
        "....jHHhHHHh....",
        "....jHHHHhHh....",
        "....rRRRRRRrf...",
        "....rrrrrrrfyf..",
        "..jjhHHHHHHhfj..",
        ".jHHHhHHHhHHHHh.",
        "jHHhHHHHHHHhHHHh",
        ".jhhhhhhhhhhhhh.",
        "................",
    ],
    "apron": [
        "...s........s...",
        "....s......s....",
        ".....GGGGGGg....",
        ".....GpPGGGg....",
        ".....GPpGGGg....",
        "..sSSgGGGGGgSSs.",
        "....gGGGGGGGg...",
        "....gGGGGGGGgq..",
        "...gGGqqqqqGGg..",
        "...gGGqGGGqGGg..",
        "...gGGqqqqqGGgq.",
        "...gGGGGGGGGGgq.",
        "...qqqqqqqqqqqq.",
    ],
    "pants": [
        "...bbbbbbbbbb...",
        "...lLLLLLLLLm...",
        "...lLLLLLLLLm...",
        "...lLooLLLLLm...",
        "...lLOoLLlLLm...",
        "...lLLLLl.lLLm..",
        "...lLLLm..lLLm..",
        "...lLLLm..lLoOm.",
        "..lLLLLm..lLOoLm",
        "..lLLLm....lLLLm",
        "..mmmmm....mmmmm",
    ],
    "boots": [
        "....YYYY..YYYY..",
        "....UUUu..UUUu..",
        "....UUuu..UUuu..",
        "....UUUu..UUUu..",
        "....UUuu..UUuu..",
        "...UUUUu.UUUUu..",
        "..UUUUUuUUUUUu..",
        "..uuuuuuuuuuuu..",
        "..zzzzzz.zzzzzz.",
    ],
}


def set_piece(name):
    img = blank(16, 16)
    rows = SET_ROWS[name]; off = (16 - len(rows)) // 2 + 1
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in SET_PAL: put(img, x, y + off, SET_PAL[ch])
    return img


# ── Ovoce na stromech v sadu (překryv mapy, art px): pupen → malé zelené → nalévá se → zralé ──
TINY = {
    "apple": [
        ["....", ".ff.", ".fy.", "...."],
        ["....", ".AB.", ".BC.", "...."],
        [".s..", "ABD.", "BCD.", ".D.."],
        [".s...", ".eE..", "eEEd.", "dEdd.", ".dd.."],
    ],
}


def tiny(kind, stage):
    """Malé ovoce na strom: 4–5 px, stadium 0 květ, 1 malé zelené, 2 nalévá se, 3 zralé."""
    ramp = {"apple": APPLE, "pear": PEAR, "orange": ORANGE, "banana": BANANA}[kind]
    img = blank(6, 7)
    if stage == 0:                                                                       # květ / pupen
        if kind == "banana":
            for (x, y, c) in ((2, 0, STEM[1]), (2, 1, (0x8A, 0x2A, 0x5A)), (2, 2, (0xB8, 0x4A, 0x7A)), (3, 2, (0x8A, 0x2A, 0x5A)), (2, 3, (0x6A, 0x1E, 0x46))):
                put(img, x, y, c)
        else:
            petal = (0xFF, 0xF4, 0xF6) if kind != "orange" else (0xFF, 0xFF, 0xF0)
            for (x, y) in ((2, 1), (1, 2), (3, 2), (2, 3)): put(img, x, y, petal)
            put(img, 2, 2, (0xFF, 0xD8, 0x4A)); put(img, 1, 1, (0xF4, 0xB8, 0xC8) if kind != "orange" else petal)
        return img
    if kind == "banana":                                                                 # trs banánů: 3 prsty dolů
        col = UNRIPE if stage == 1 else ([UNRIPE[1], UNRIPE[2], BANANA[2], BANANA[3]] if stage == 2 else BANANA[1:5])
        put(img, 2, 0, STEM[1]); put(img, 3, 0, STEM[0])
        n = 2 + stage
        for i in range(3):
            x = 1 + i
            for y in range(1, 1 + n):
                put(img, x + (1 if y == n and i == 2 else 0), y, col[3 if i == 0 else 2 if i == 1 else 1] if y < n else col[0])
        return img
    # ručně: číslice = index rampy (světlo vlevo nahoře), s stopka, g lístek
    grids = {
        "apple": {1: [".s", "32", "21"], 2: [".s.", "432", "321", ".1."], 3: ["..sg.", ".443.", "44321", "33211", ".211."]},
        "orange": {1: [".s", "32", "21"], 2: [".sg", "432", "321", ".1."], 3: ["..sg.", ".443.", "44322", "34221", ".221."]},
        "pear": {1: ["s.", "3.", "32", "21"], 2: [".s.", ".3.", "432", "321", ".1."], 3: ["..s..", "..4..", ".443.", ".432.", "44321", "33211", ".211."]},
    }[kind][stage]
    rampx = UNRIPE if stage == 1 else (ramp if stage == 3 else [UNRIPE[0], UNRIPE[2], ramp[2], ramp[3], ramp[4]])
    ox = (6 - len(grids[0])) // 2
    for y, row in enumerate(grids):
        for x, ch in enumerate(row):
            if ch == "s": put(img, ox + x, y, STEM[1])
            elif ch == "g": put(img, ox + x, y, LEAF[2])
            elif ch.isdigit(): put(img, ox + x, y, rampx[min(int(ch), len(rampx) - 1)])
    return img


# ════════════════════════════════════════════════════════════════════════════
def outline(img):
    out = img.copy()
    h, w = img.shape[:2]
    for y in range(h):
        for x in range(w):
            if img[y, x, 3]: continue
            if any(0 <= x + dx < w and 0 <= y + dy < h and img[y + dy, x + dx, 3] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y, x, :3] = OUTLINE; out[y, x, 3] = 255
    return out


def kotlin_grid(name, img):
    """Ikona jako řádky znaků + paleta (Kotlin dekóduje do ARGB)."""
    h, w = img.shape[:2]
    cols = []
    rows = []
    for y in range(h):
        r = ""
        for x in range(w):
            if img[y, x, 3] == 0: r += "."; continue
            c = tuple(int(v) for v in img[y, x, :3])
            if c not in cols: cols.append(c)
            r += "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"[cols.index(c)]
        rows.append(r)
    pal = ", ".join("0xFF%02X%02X%02X" % c for c in cols)
    body = ",\n            ".join('"%s"' % r for r in rows)
    return f"    private val {name} = Grid({w}, {h}, longArrayOf({pal}),\n        listOf(\n            {body}))\n"


def write_kotlin():
    grids = []
    icons = {"APPLE": apple(), "PEAR": pear(), "ORANGE": orange(), "BANANA": banana(), "SKILL": apple(16)}
    for k_, v in icons.items(): grids.append(kotlin_grid(k_, v))
    for t in ("old", "copper", "silver", "gold"): grids.append(kotlin_grid("PICKER_" + t.upper(), picker(t)))
    for n in SET_ROWS: grids.append(kotlin_grid("SET_" + n.upper(), set_piece(n)))
    for kind in ("apple", "pear", "orange", "banana"):
        for st in range(4): grids.append(kotlin_grid(f"TINY_{kind.upper()}_{st}", tiny(kind, st)))
    src = """package cz.uhk.macroflow.pokemon.skills

// VYGENEROVÁNO: tools/skillart/gen_fruit_art.py – needitovat ručně, upravit generátor a spustit znovu.

/**
 * Pixel art Sadařství (region 2): ovoce 12 × 12, ikona dovednosti a česáky 16 × 16, Sadařův set 16 × 16
 * a malé ovoce na stromy v sadu (6 × 7, čtyři stadia růstu). ARGB, 0 = průhledné; obrys jako u ostatních ikon.
 */
object FruitArt {
    private class Grid(val w: Int, val h: Int, val pal: LongArray, val rows: List<String>)

    private const val KEYS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private fun decode(g: Grid, outline: Boolean = true): IntArray {
        val p = SkillArt.Px(g.w, g.h)
        g.rows.forEachIndexed { y, r -> r.forEachIndexed { x, ch -> val i = KEYS.indexOf(ch); if (i >= 0) p[x, y] = g.pal[i].toInt() } }
        if (outline) p.outline(SkillArt.OUTLINE)
        return p.data
    }

""" + "\n".join(grids) + """
    fun fruit(r: Resource): IntArray? = when (r) {
        Resource.FRUIT_APPLE -> decode(APPLE)
        Resource.FRUIT_PEAR -> decode(PEAR)
        Resource.FRUIT_ORANGE -> decode(ORANGE)
        Resource.FRUIT_BANANA -> decode(BANANA)
        else -> null
    }

    fun skillIcon(): IntArray = decode(SKILL)

    fun picker(g: Gear): IntArray? = when (g) {
        Gear.OLD_PICKER -> decode(PICKER_OLD)
        Gear.COPPER_PICKER -> decode(PICKER_COPPER)
        Gear.SILVER_PICKER -> decode(PICKER_SILVER)
        Gear.GOLD_PICKER -> decode(PICKER_GOLD)
        Gear.ORCHARD_HAT -> decode(SET_HAT)
        Gear.ORCHARD_APRON -> decode(SET_APRON)
        Gear.ORCHARD_PANTS -> decode(SET_PANTS)
        Gear.ORCHARD_BOOTS -> decode(SET_BOOTS)
        else -> null
    }

    fun oldPicker(): IntArray = decode(PICKER_OLD)

    const val TINY_W = 6
    const val TINY_H = 7

    /** Ovoce na stromě: [stage] 0 květ, 1 malé zelené, 2 nalévá se, 3 zralé. */
    fun tiny(r: Resource, stage: Int): IntArray {
        val s = stage.coerceIn(0, 3)
        val g = when (r) {
            Resource.FRUIT_PEAR -> listOf(TINY_PEAR_0, TINY_PEAR_1, TINY_PEAR_2, TINY_PEAR_3)
            Resource.FRUIT_ORANGE -> listOf(TINY_ORANGE_0, TINY_ORANGE_1, TINY_ORANGE_2, TINY_ORANGE_3)
            Resource.FRUIT_BANANA -> listOf(TINY_BANANA_0, TINY_BANANA_1, TINY_BANANA_2, TINY_BANANA_3)
            else -> listOf(TINY_APPLE_0, TINY_APPLE_1, TINY_APPLE_2, TINY_APPLE_3)
        }[s]
        return decode(g, outline = s > 0)
    }
}
"""
    open(OUT, "w", encoding="utf-8").write(src)
    print("ok", OUT)


def preview(path):
    from PIL import Image
    icons = [apple(), pear(), orange(), banana(), apple(16)] + [picker(t) for t in ("old", "copper", "silver", "gold")] + \
        [set_piece(n) for n in SET_ROWS] + [tiny(k, st) for k in ("apple", "pear", "orange", "banana") for st in range(4)]
    W = sum(i.shape[1] for i in icons) + 4 * len(icons)
    sheet = np.zeros((16, W, 4), np.uint8); x = 0
    for i in icons:
        o = outline(i); sheet[:o.shape[0], x:x + o.shape[1]] = o; x += o.shape[1] + 4
    im = Image.fromarray(sheet, "RGBA")
    bg = Image.new("RGBA", im.size, (0x3A, 0x2A, 0x1E, 255)); bg.alpha_composite(im)
    bg.resize((im.width * 14, im.height * 14), Image.NEAREST).save(path)


if __name__ == "__main__":
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
    else:
        write_kotlin()

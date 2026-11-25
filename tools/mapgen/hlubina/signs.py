"""
Zóna 3 – písmo a nápisy. Písmo 5×7 s českou diakritikou (háček, čárka, kroužek ve dvou řádcích nad písmenem),
buňka 6 × 9. Dva druhy nápisů:
 - plate(): smaltovaná/plechová cedule – lem s plastickou hranou, nýty v rozích, písmena se stínem, opotřebení,
   rezavé stékance od nýtů;
 - painted(): nastříkaná šablona přímo na skálu/beton – barva s přestřikem kolem písmen, místy odřená až na podklad,
   stékance barvy dolů.
Drobné štítky (bedny, kabina) dál tiny() 3×5.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "ashplain")); sys.path.insert(0, os.path.join(HERE, "..", "tower")); sys.path.insert(0, HERE)
from common import *  # noqa: E402,F401,F403
from common import _h, _vn  # noqa: E402

GLYPHS = {'A': ['.###.', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'], 'B': ['####.', '#...#', '#...#', '####.', '#...#', '#...#', '####.'], 'C': ['.###.', '#...#', '#....', '#....', '#....', '#...#', '.###.'], 'D': ['####.', '#...#', '#...#', '#...#', '#...#', '#...#', '####.'], 'E': ['#####', '#....', '#....', '####.', '#....', '#....', '#####'], 'F': ['#####', '#....', '#....', '####.', '#....', '#....', '#....'], 'G': ['.###.', '#...#', '#....', '#.###', '#...#', '#...#', '.###.'], 'H': ['#...#', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'], 'I': ['.###.', '..#..', '..#..', '..#..', '..#..', '..#..', '.###.'], 'J': ['..###', '...#.', '...#.', '...#.', '#..#.', '#..#.', '.##..'], 'K': ['#...#', '#..#.', '#.#..', '##...', '#.#..', '#..#.', '#...#'], 'L': ['#....', '#....', '#....', '#....', '#....', '#....', '#####'], 'M': ['#...#', '##.##', '#.#.#', '#.#.#', '#...#', '#...#', '#...#'], 'N': ['#...#', '##..#', '#.#.#', '#..##', '#...#', '#...#', '#...#'], 'O': ['.###.', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'], 'P': ['####.', '#...#', '#...#', '####.', '#....', '#....', '#....'], 'R': ['####.', '#...#', '#...#', '####.', '#.#..', '#..#.', '#...#'], 'S': ['.####', '#....', '#....', '.###.', '....#', '....#', '####.'], 'T': ['#####', '..#..', '..#..', '..#..', '..#..', '..#..', '..#..'], 'U': ['#...#', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'], 'V': ['#...#', '#...#', '#...#', '#...#', '#...#', '.#.#.', '..#..'], 'Y': ['#...#', '#...#', '.#.#.', '..#..', '..#..', '..#..', '..#..'], 'Z': ['#####', '....#', '...#.', '..#..', '.#...', '#....', '#####'], '0': ['.###.', '#...#', '#..##', '#.#.#', '##..#', '#...#', '.###.'], '1': ['..#..', '.##..', '..#..', '..#..', '..#..', '..#..', '.###.'], '2': ['.###.', '#...#', '....#', '...#.', '..#..', '.#...', '#####'], '3': ['####.', '....#', '....#', '.###.', '....#', '....#', '####.'], '4': ['...#.', '..##.', '.#.#.', '#..#.', '#####', '...#.', '...#.'], '5': ['#####', '#....', '####.', '....#', '....#', '#...#', '.###.'], '6': ['.###.', '#....', '#....', '####.', '#...#', '#...#', '.###.'], '7': ['#####', '....#', '...#.', '..#..', '.#...', '.#...', '.#...'], '8': ['.###.', '#...#', '#...#', '.###.', '#...#', '#...#', '.###.'], '9': ['.###.', '#...#', '#...#', '.####', '....#', '....#', '.###.'], '-': ['.....', '.....', '.....', '####.', '.....', '.....', '.....'], '·': ['.....', '.....', '.....', '..#..', '.....', '.....', '.....'], '.': ['.....', '.....', '.....', '.....', '.....', '.....', '..#..'], '!': ['..#..', '..#..', '..#..', '..#..', '..#..', '.....', '..#..'], ':': ['.....', '..#..', '.....', '.....', '.....', '..#..', '.....'], '>': ['.....', '..#..', '...#.', '#####', '...#.', '..#..', '.....'], ' ': ['.....', '.....', '.....', '.....', '.....', '.....', '.....']}
ACCENTED = {'Á': ('A', 'acute'), 'É': ('E', 'acute'), 'Í': ('I', 'acute'), 'Ý': ('Y', 'acute'), 'Ú': ('U', 'acute'), 'Č': ('C', 'caron'), 'Ě': ('E', 'caron'), 'Ř': ('R', 'caron'), 'Š': ('S', 'caron'), 'Ž': ('Z', 'caron'), 'Ň': ('N', 'caron'), 'Ď': ('D', 'caron'), 'Ť': ('T', 'caron'), 'Ů': ('U', 'ring')}
MARKS = {'acute': ['...#.', '..#..'], 'caron': ['.#.#.', '..#..'], 'ring': ['..#..', '.#.#.']}
CELL_W, CELL_H = 6, 9


def glyph(ch):
    """9 řádků × 5 sloupců: 2 řádky na diakritiku + 7 písmeno."""
    ch = ch.upper()
    if ch in ACCENTED:
        base, mark = ACCENTED[ch]
        return MARKS[mark] + GLYPHS[base]
    return [".....", "....."] + GLYPHS.get(ch, GLYPHS[" "])


def text_width(text, scale=1):
    return (len(text) * CELL_W - 1) * scale


def text_mask(text, scale=1):
    """Bool maska textu (výška 9·scale)."""
    m = np.zeros((CELL_H * scale, max(1, text_width(text, scale))), bool)
    for i, ch in enumerate(text):
        for dy, row in enumerate(glyph(ch)):
            for dx, b in enumerate(row):
                if b == "#":
                    m[dy * scale:(dy + 1) * scale, (i * CELL_W + dx) * scale:(i * CELL_W + dx + 1) * scale] = True
    return m


def _mix(a, b, k):
    return np.clip(np.asarray(a[:3], float) * (1 - k) + np.asarray(b[:3], float) * k, 0, 255).astype(np.uint8)


def plate(text, fg, bg, scale=1, seed=0, pad=4, rivets=True, wear=0.08, border=None):
    """Plechová cedule s textem. fg/bg = barvy (c()), border = barva vnitřního lemu (např. červená u UZÁVĚRY)."""
    m = text_mask(text, scale)
    has_acc = any(ch.upper() in ACCENTED for ch in text)
    top_cut = 0 if has_acc else 2 * scale                                  # bez diakritiky se horní řádky ořežou
    m = m[top_cut:]
    W, H = m.shape[1] + 2 * pad + 2, m.shape[0] + 2 * pad
    s = Spr(W, H)
    bgf = np.asarray(bg[:3], float)
    for y in range(H):
        for x in range(W):
            k = 1.08 - 0.22 * y / H + (_vn(x, y, seed + 3, 3) - 0.5) * 0.12      # svislý přechod + zrno plechu
            col = np.clip(bgf * k, 0, 255).astype(np.uint8)
            if y == 0 or x == 0:
                col = _mix(col, (255, 255, 255), 0.25)                         # osvětlená hrana
            if y == H - 1 or x == W - 1:
                col = _mix(col, (0, 0, 0), 0.55)                               # stinná hrana
            s.px(x, y, np.append(col, 255))
    if border is not None:
        for x in range(2, W - 2):
            s.px(x, 2, border); s.px(x, H - 3, border)
        for y in range(2, H - 2):
            s.px(2, y, border); s.px(W - 3, y, border)
    ox, oy = pad + 1, pad
    dark = _mix(bg, (0, 0, 0), 0.6)
    for y in range(m.shape[0]):                                             # stín písmen (o 1 px vpravo dolů)
        for x in range(m.shape[1]):
            if m[y, x] and not (y + 1 < m.shape[0] and x + 1 < m.shape[1] and m[y + 1, x + 1]):
                s.px(ox + x + 1, oy + y + 1, np.append(dark, 255))
    for y in range(m.shape[0]):
        for x in range(m.shape[1]):
            if m[y, x]:
                col = fg
                if _vn(x * 2, y * 2, seed + 7, 3) < wear:                       # odřená barva
                    col = np.append(_mix(fg, bg, 0.6), 255)
                elif y == 0 or not m[y - 1, x]:
                    col = np.append(_mix(fg, (255, 255, 255), 0.25), 255)        # horní hrana písmene světlejší
                s.px(ox + x, oy + y, col)
    if rivets:
        for (x, y) in ((1, 1), (W - 3, 1), (1, H - 3), (W - 3, H - 3)):
            s.px(x, y, c(0xD8DCE4)); s.px(x + 1, y + 1, c(0x30343C)); s.px(x + 1, y, c(0x9098A6)); s.px(x, y + 1, c(0x9098A6))
            for k in range(1, 2 + _h(x, y, seed) % 4):                         # rezavý stékanec od nýtu
                if y + 1 + k < H - 1:
                    s.px(x + 1, y + 1 + k, np.append(_mix(s.a[y + 1 + k, x + 1], (110, 60, 30), 0.55 - k * 0.1), 255))
    return s


def painted(target, x0, y0, text, paint, scale=1, seed=0, wear=0.12, drips=True, spray=True):
    """Nastříká šablonou text na pole RGBA (mapa nebo Spr.a) – přestřik kolem písmen, odřené místa, stékance."""
    a = target.a if hasattr(target, "a") else target
    H, W = a.shape[:2]
    m = text_mask(text, scale)
    if not any(ch.upper() in ACCENTED for ch in text):
        m = m[2 * scale:]
    pf = np.asarray(paint[:3], float)
    if spray:                                                              # přestřik: řídký závoj barvy kolem písmen
        for y in range(-2, m.shape[0] + 2):
            for x in range(-2, m.shape[1] + 2):
                X, Y = x0 + x, y0 + y
                if not (0 <= X < W and 0 <= Y < H):
                    continue
                near = m[max(0, y - 1):y + 2, max(0, x - 1):x + 2].any() if 0 <= y + 1 and 0 <= x + 1 else False
                if near and not (0 <= y < m.shape[0] and 0 <= x < m.shape[1] and m[y, x]) and _h(X, Y, seed + 11) % 3 == 0:
                    a[Y, X, :3] = _mix(a[Y, X], pf, 0.28)
    for y in range(m.shape[0]):
        for x in range(m.shape[1]):
            X, Y = x0 + x, y0 + y
            if not m[y, x] or not (0 <= X < W and 0 <= Y < H):
                continue
            w = _vn(X * 1.5, Y * 1.5, seed + 5, 3)
            k = 0.95 if w > wear else 0.4                                       # odřené až na podklad
            a[Y, X, :3] = _mix(a[Y, X], pf, k)
    if drips:
        for x in range(m.shape[1]):
            col_ys = [y for y in range(m.shape[0]) if m[y, x]]
            if col_ys and _h(x0 + x, y0, seed + 13) % 5 == 0:
                yb = col_ys[-1]
                for k in range(1, 2 + _h(x0 + x, y0, seed + 17) % 4):
                    X, Y = x0 + x, y0 + yb + k
                    if 0 <= X < W and 0 <= Y < H:
                        a[Y, X, :3] = _mix(a[Y, X], pf, 0.7 - k * 0.15)
    return m.shape[1], m.shape[0]


def tiny(s, x0, y0, text, col, scale=1):
    """Drobné písmo 3×5 – jen na malé štítky (bedny, kabina); cedule a nápisy kreslí plate() / painted()."""
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



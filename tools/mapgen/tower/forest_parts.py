"""
Zarostlé patro – 4. patro Věže Řádu Váhy, patro listových Makromonů. Les, který prorostl úřadem: kořeny
zvedly dlaždice, kartotéky obrostl břečťan a houby, z kachlíkové stěny visí liány, okna jsou zelená od mechu.
Velké stromy (kořeny, kůra, koruna z chomáčů listí), jeden s dutinou, kapradí (setkání), padlý kmen,
houby, mech, listí na zemi. Ve 3/4 pohledu, světlo shora zleva, obrys selout.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
from pix import Spr, c  # noqa: E402
from figures import sphere, selout, blobs, _pick, BRASS, PAPER, INK, STEEL, RED, CABI, shade_px  # noqa: E402

LEAF = [c(0x10261A), c(0x1A3C22), c(0x28562C), c(0x3C7236), c(0x5A9040), c(0x84B254)]
LEAF_DARK = [c(0x0A1C14), c(0x12301C), c(0x1E4624), c(0x2E5E2C), c(0x467A36)]
BARK = [c(0x22160E), c(0x3A2818), c(0x543A24), c(0x6E5034), c(0x8C6A48)]
SOIL = [c(0x1E160E), c(0x2E2216), c(0x40301E), c(0x54402A)]
MOSS = [c(0x2A4420), c(0x3E5E2A), c(0x587A34), c(0x7A9A44)]
LITTER = [c(0x8A4A1E), c(0xB06A28), c(0xC89838), c(0x7A3A22), c(0x5E6A2A)]
TILE = [c(0x4E5A56), c(0x7A8882), c(0x98A8A0), c(0xB4C2BA)]
MUSH = [c(0x6A1C14), c(0xA43424), c(0xD05A3C), c(0xF0E6D2)]
FERN = [c(0x12301A), c(0x1E4A24), c(0x2E6A30), c(0x4A8A3A), c(0x6EAE4E)]


def _h(x, y, s=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (s * 83492791)) & 0xFFFF


def forest_floor(img, x0, y0, x1, y1):
    """Lesní půda: hlína ve třech tónech, mechové ostrůvky, spadané listí (2 px lístky v podzimních barvách),
    zbytky dlaždic, které nadzvedly kořeny (šedé úlomky se světlou hranou)."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            h = _h(x, y)
            col = SOIL[2] if h % 5 < 3 else SOIL[1] if h % 5 == 3 else SOIL[3]
            m = math.sin(x * 0.09 + y * 0.05) + math.sin(x * 0.04 - y * 0.11 + 2) + math.sin((x + y) * 0.07)
            if m > 1.8:
                col = MOSS[2] if h % 3 else MOSS[1]
            elif m > 1.4:
                col = MOSS[1] if h % 2 else SOIL[2]
            img[y, x] = col
    for y in range(y0 + 1, y1 - 1, 2):
        for x in range(x0 + 1, x1 - 2, 2):
            h = _h(x, y, 3)
            if h % 13 == 0:
                col = LITTER[h % 5]
                img[y, x] = col; img[y, x + 1] = col
                if h % 2:
                    img[y + 1, x + 1] = LITTER[(h // 5) % 5]
    rng = np.random.default_rng(44)
    for _ in range(14):                                                             # zbytky dlaždic
        tx, ty = int(rng.integers(x0 + 4, x1 - 14)), int(rng.integers(y0 + 6, y1 - 10))
        w, hh = int(rng.integers(6, 11)), int(rng.integers(4, 7))
        tilt = int(rng.integers(-1, 2))
        for y in range(hh):
            for x in range(w):
                X, Y = tx + x, ty + y + (x * tilt) // 6
                if (x + y) % 11 == 10:
                    continue
                img[Y, X] = TILE[3] if y == 0 else TILE[2] if x < w - 1 else TILE[1]
            img[ty + hh + (w * tilt) // 6, tx + w // 2] = SOIL[0]


def big_tree(seed=0, hollow=False, w=60, h=92):
    """Velký strom: koruna ze stínovaných chomáčů listí (světlejší vlevo nahoře, tmavé spáry), pod ní kmen
    s kůrou (svislé rýhy, světlo zleva), rozložité kořeny přes zbytky dlaždic. [hollow] = dutina v kmeni."""
    s = Spr(w, h)
    rng = np.random.default_rng(seed)
    cx = w // 2
    trunk_top, base = 46, h - 6
    for y in range(trunk_top, base + 1):                                            # kmen
        hw = 5 + (y - trunk_top) // 9
        for x in range(cx - hw, cx + hw + 1):
            nx = (x - cx) / (hw + .5)
            k = 4 if nx < -0.6 else 3 if nx < -0.1 else 2 if nx < 0.5 else 1
            if (x * 3 + (y // 4)) % 5 == 0 or (x + _h(x, y // 3, seed)) % 9 == 0:
                k = max(0, k - 1)                                                   # rýhy kůry
            s.px(x, y, BARK[k])
    for i, ang in enumerate((0.12, 0.38, 0.62, 0.88)):                            # kořeny (zužující se klíny)
        ang = math.pi * ang + rng.uniform(-0.05, 0.05)
        L = rng.uniform(9, 13)
        for t in np.linspace(0, 1, 40):
            x = cx - math.cos(ang) * (4 + L * t * 1.25)
            y = base - 4 + math.sin(ang) * (2 + L * t * 0.32) + t * 3
            r = 3.6 * (1 - t) ** 0.8 + 0.5
            for dy in range(-int(r), int(r) + 1):
                X, Y = int(x), int(y + dy * 0.6)
                if 0 <= X < w and 0 <= Y < h:
                    s.px(X, Y, BARK[4] if dy < -r * 0.4 else BARK[3] if dy <= 0 else BARK[1])
    if hollow:                                                                      # dutina
        hx, hy = cx - 1, trunk_top + 16
        for y in range(hy - 5, hy + 6):
            for x in range(hx - 4, hx + 5):
                d = ((x - hx) / 4.2) ** 2 + ((y - hy) / 5.5) ** 2
                if d < 1:
                    s.px(x, y, c(0x0A0604) if d < 0.65 else BARK[0])
                elif d < 1.5:
                    s.px(x, y, BARK[4] if x < hx else BARK[2])
    clumps = []
    for i in range(16):                                                             # koruna
        a = rng.uniform(0, 2 * math.pi); r = rng.uniform(0, 1) ** 0.6
        clumps.append((cx + math.cos(a) * r * (w * 0.36), 24 + math.sin(a) * r * 17, rng.uniform(7, 11)))
    clumps.sort(key=lambda c_: c_[1])
    blobs(s, clumps, LEAF[1:], amb=0.22)
    for _ in range(90):                                                             # lesk listů a díry v koruně
        x, y = int(rng.integers(4, w - 4)), int(rng.integers(6, 44))
        if s.opaque(x, y):
            v = s.a[y, x, 1]
            if v > 110 and rng.random() < 0.6:
                s.px(x, y, LEAF[5])
    return selout(s, 0.55, 0.85)


def fern(seed=0, w=40, h=26):
    """Kapradí (místo setkání): hustý trs – stovka lístků (krátké šikmé tahy) rozložených po půlkopuli,
    uvnitř tmavé, na okraji a nahoře světlé; ze středu trčí několik delších listů s vějířkem lístků."""
    s = Spr(w, h)
    rng = np.random.default_rng(seed)
    cx, cy = w / 2, h - 2
    for _ in range(170):
        ang = rng.uniform(math.pi * 1.02, math.pi * 1.98)
        r = rng.uniform(0.15, 1.0) ** 0.7
        x = cx + math.cos(ang) * r * (w / 2 - 2)
        y = cy + math.sin(ang) * r * (h - 4)
        lit = (-math.cos(ang) * 0.5 + 0.5) * 0.4 + r * 0.6                          # světlo zleva shora, okraj světlejší
        tone = 0 if r < 0.35 else 1 if lit < 0.45 else 2 if lit < 0.65 else 3 if lit < 0.85 else 4
        dx, dy = math.cos(ang), math.sin(ang)
        for k in range(3):
            s.px(int(x + dx * k), int(y + dy * k * 0.7), FERN[tone])
    for i in range(5):                                                              # delší listy
        ang = math.pi * (1.2 + 0.15 * i) + rng.uniform(-0.05, 0.05)
        L = rng.uniform(h * 0.7, h * 0.95)
        for k in range(int(L)):
            x = cx + math.cos(ang) * k * 0.8
            y = cy - 3 + math.sin(ang) * k * 0.8 + (k / L) ** 2 * 5
            s.px(int(x), int(y), FERN[2])
            if k % 2 == 1:
                s.px(int(x) - 1, int(y), FERN[4]); s.px(int(x) + 1, int(y), FERN[3])
    return selout(s, 0.55, 0.85)


def mushrooms(seed=0):
    """Houby v pěti podobách podle [seed] % 5: 0 muchomůrky (červené s tečkami), 1 hřiby (hnědý klobouk,
    tlustá nožka), 2 trs drobných václavek na pařízku, 3 bílé hnojníky (vysoké zvonky), 4 bledé modravé
    „svítivky“ (slabě září – Kustodiát je tu pěstuje na noční směny)."""
    v = seed % 5
    s = Spr(16, 14)
    if v == 0:
        for (x, y, r) in ((4, 6, 3.2), (10, 4, 3.8), (13, 9, 2.4)):
            for yy in range(int(y), 14):
                s.px(int(x), yy, MUSH[3]); s.px(int(x) + 1, yy, c(0xC8BEAA))
            sphere(s, x + .5, y, r, r * 0.7, MUSH[:3] + [c(0xF07A5A)], amb=0.3)
            s.px(int(x - 1), int(y - 1), MUSH[3]); s.px(int(x + 1), int(y), MUSH[3])
    elif v == 1:
        BOL = [c(0x3A220E), c(0x5E3818), c(0x8A5428), c(0xB07A44)]
        for (x, y, r, stem) in ((5, 6, 4.2, 3), (12, 8, 3.0, 2)):
            for yy in range(int(y), 14):                                            # tlustá nožka (soudek)
                bulge = 1 if yy > y + 3 else 0
                for xx in range(int(x) - stem // 2 - bulge, int(x) + stem // 2 + bulge + 1):
                    s.px(xx, yy, c(0xE8DCC0) if xx < x else c(0xC4B494))
            sphere(s, x + .5, y, r, r * 0.62, BOL, amb=0.3)
    elif v == 2:
        for x in range(2, 14):                                                      # pařízek
            for y in range(9, 14):
                s.px(x, y, BARK[3] if y == 9 else BARK[2] if x < 10 else BARK[1])
        HON = [c(0x6A4A1A), c(0xA87A30), c(0xD4A848), c(0xF0D07A)]
        for (x, y) in ((3, 6), (5, 4), (7, 6), (9, 3), (11, 5), (13, 7), (6, 8), (10, 8)):
            s.px(x, y + 1, c(0xE8DCC0)); s.px(x, y + 2, c(0xC4B494))
            sphere(s, x + .5, y, 1.8, 1.2, HON, amb=0.35)
    elif v == 3:
        for (x, y, h) in ((4, 2, 11), (9, 4, 9), (13, 6, 7)):
            for yy in range(y + 4, 14):
                s.px(x, yy, c(0xF4F2EA))
            for yy in range(y, y + 5):                                              # zvonek
                hw = 1 + (yy - y) // 2
                for xx in range(x - hw, x + hw + 1):
                    s.px(xx, yy, c(0xFAF8F2) if xx < x else c(0xD8D4CA) if yy < y + 4 else c(0x8A8680))
    else:
        GLO = [c(0x2A4A6A), c(0x5A8AB4), c(0x9ACCEE), c(0xDAF4FF)]
        for (x, y, r) in ((4, 8, 2.2), (8, 6, 2.8), (12, 9, 2.0), (9, 10, 1.6)):
            for yy in range(int(y), 14):
                s.px(int(x), yy, c(0xB8D0DA))
            sphere(s, x + .5, y, r, r * 0.7, GLO, amb=0.5)
    return selout(s, 0.55, 0.85)


def fallen_log(w=56):
    """Padlý kmen ve 3/4: válec ležící na zemi (horní plocha světlejší, boky tmavší), vlevo uříznuté čelo
    s letokruhy, vpravo zakulacený zlomený konec s třískami a pahýlem větve, mech navrch, houbička."""
    H = 16
    s = Spr(w, H)
    end = w - 8                                                                     # kde začíná zakulacený konec
    for x in range(10, w):
        t = max(0.0, (x - end) / 8)
        half = 7 * math.sqrt(max(0.0, 1 - t * t))                                   # profil se ke konci zužuje
        top, bot = 8.5 - half, 8.5 + half
        for y in range(1, H):
            if not (top <= y <= bot):
                continue
            rel = (y - top) / max(1, bot - top)
            k = 4 if rel < 0.2 else 3 if rel < 0.5 else 2 if rel < 0.8 else 1
            if (x * 2 + y * 5) % 13 == 0:
                k = max(0, k - 1)
            s.px(x, y, BARK[k])
    for (x, y) in ((w - 2, 6), (w - 1, 7), (w - 3, 5), (w - 2, 10), (w - 1, 9)):     # třísky zlomu
        s.px(x, y, c(0xC49A68))
    s.px(w - 4, 8, c(0x2A1A0E)); s.px(w - 5, 9, c(0x2A1A0E))
    for x in range(14, end):                                                        # mech navrch
        if math.sin(x * 0.4) > -0.2:
            s.px(x, 2, MOSS[3]); s.px(x, 3, MOSS[2])
    for y in range(1, H):                                                           # čelo s letokruhy
        for x in range(2, 16):
            d = math.hypot((x - 9) / 7, (y - 8.5) / 7.5)
            if d < 1:
                ring = int(d * 5) % 2
                s.px(x, y, c(0xC49A68) if ring else c(0xA47A4C))
                if d > 0.86:
                    s.px(x, y, BARK[1])
    s.px(9, 8, BARK[1])
    for i in range(5):                                                              # pahýl větve nahoru
        s.px(36 + i // 2, 1 - 0, BARK[3]); s.px(37 + i // 2, 0, BARK[2])
    for (x, y) in ((24, 1), (25, 0), (26, 1)):
        s.px(x, y, c(0xE8D4A8))
    return selout(s, 0.5, 0.82)


def overgrown_cabinet(base):
    """Kartotéka obrostlá břečťanem: přes hotový sprite [base] se vinou úponky s lístky, dole mech a houby."""
    s = base
    rng = np.random.default_rng(8)
    for _ in range(4):
        x = int(rng.integers(1, s.w - 4))
        for y in range(0, s.h):
            x += int(rng.integers(-1, 2)) if y % 3 == 0 else 0
            x = max(0, min(s.w - 2, x))
            if s.opaque(x, y):
                s.px(x, y, LEAF[2])
                if y % 3 == 0:
                    s.px(x + 1, y, LEAF[4]); s.px(x - 1, y + 1, LEAF[3])
    for x in range(s.w):
        if s.opaque(x, s.h - 1):
            s.px(x, s.h - 1, MOSS[2]); s.px(x, s.h - 2, MOSS[1] if x % 2 else MOSS[3])
    return selout(s, 0.5, 0.82)


def ivy_wall(wall, seed=2):
    """Na hotovou zadní stěnu (Spr) přidá liány shora, mech u paty a zelené skvrny na kachlích."""
    s = wall
    rng = np.random.default_rng(seed)
    for _ in range(18):
        x = int(rng.integers(0, s.w))
        L = int(rng.integers(12, s.h - 6))
        for y in range(4, 4 + L):
            x += int(rng.integers(-1, 2)) if y % 4 == 0 else 0
            x = max(0, min(s.w - 2, x))
            s.px(x, y, LEAF[2] if y % 2 else LEAF[3])
            if y % 3 == 0:
                s.px(x + 1, y, LEAF[4]); s.px(x - 1, y + 1, LEAF[3])
            if y % 5 == 0:
                s.px(x + 2, y + 1, LEAF[5])
    for x in range(s.w):                                                            # mech u paty
        hh = 4 + int(3 * math.sin(x * 0.3) + 2 * math.sin(x * 0.11))
        for y in range(s.h - hh, s.h):
            s.px(x, y, MOSS[2] if (x + y) % 3 else MOSS[1])
    for _ in range(40):
        x, y = int(rng.integers(0, s.w - 3)), int(rng.integers(6, s.h - 8))
        for dx in range(3):
            s.px(x + dx, y, MOSS[1])
    return s


def snail_sign():
    """Cedulka zapíchnutá v mechu: „POZOR, POMALÝ PROVOZ“ (ukazuje na cestičku slimáka)."""
    s = Spr(14, 16)
    for y in range(6, 16):
        s.px(6, y, BARK[3]); s.px(7, y, BARK[1])
    for y in range(0, 7):
        for x in range(0, 14):
            s.px(x, y, PAPER[3] if 0 < y < 6 and 0 < x < 13 else c(0xC8B060))
    for x in range(2, 12, 2):
        s.px(x, 2, INK); s.px(x + 1, 4, INK)
    return selout(s, 0.5, 0.82)

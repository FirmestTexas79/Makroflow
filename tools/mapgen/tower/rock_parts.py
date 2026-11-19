"""
Skalní patro – 5. patro Věže Řádu Váhy, patro zemních Makromonů. Věž tu prorazila horu: zadní stěna je holá
skála ve vrstvách, podlaha prašná kamenná plotna s prasklinami a štěrkem. Balvany, sutě (setkání), skalní
pilíř, trhlina v zemi za provazovým zábradlím, teodolit na trojnožce, vytyčovací praporky s čísly, suché
keře a napůl zasypaná lebka něčeho velkého. Ve 3/4, světlo shora zleva, obrys selout.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
from pix import Spr, c  # noqa: E402
from figures import sphere, selout, blobs, _pick, BRASS, PAPER, INK, STEEL, RED, shade_px  # noqa: E402

ROCK = [c(0x2E2620), c(0x4A3E34), c(0x6A5A4A), c(0x8C7A64), c(0xAE9A80), c(0xCCB89C)]
STRATA = [c(0x5A4636), c(0x7A604A), c(0x9A7E60), c(0xB89C78), c(0x6E5844)]
DUST = [c(0x6E5E4A), c(0x8A7860), c(0xA69276), c(0xBEAA8C)]
WOOD = [c(0x3A2414), c(0x5C3A20), c(0x825630), c(0xA87444)]
BONE = [c(0x8A8270), c(0xB8AE96), c(0xDCD4BE), c(0xF2ECDC)]
DRY = [c(0x4A3A22), c(0x6E5630), c(0x947844), c(0xB89A5C)]
ROPE = [c(0x8A6A3A), c(0xC4A060)]


def _h(x, y, s=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (s * 83492791)) & 0xFFFF


def rock_floor(img, x0, y0, x1, y1):
    """Kamenná plotna z nepravidelných desek (Voronoi): každá deska má vlastní tón, horní a levá hrana je
    osvětlená, dolní a pravá ve stínu (desky jsou mírně vystouplé), mezi nimi prach; štěrk a zrnka."""
    rng = np.random.default_rng(55)
    n = 130
    px_ = rng.uniform(x0, x1, n); py_ = rng.uniform(y0, y1, n)
    tone = rng.integers(0, 3, n)
    H, W = y1 - y0, x1 - x0
    yy, xx = np.mgrid[y0:y1, x0:x1]
    d = (xx[None] - px_[:, None, None]) ** 2 + ((yy[None] - py_[:, None, None]) * 1.35) ** 2
    own = np.argmin(d, axis=0)
    for y in range(H):
        for x in range(W):
            o = own[y, x]
            right = x + 1 < W and own[y, x + 1] != o
            down = y + 1 < H and own[y + 1, x] != o
            up = y > 0 and own[y - 1, x] != o
            left = x > 0 and own[y, x - 1] != o
            col = DUST[1 + tone[o]]
            if right or down:
                col = DUST[0] if (right and down) or down else ROCK[2]
            elif up or left:
                col = DUST[3] if tone[o] < 2 else ROCK[5]
            elif _h(x + x0, y + y0) % 47 == 0:
                col = DUST[3]
            elif _h(x + x0, y + y0, 3) % 59 == 0:
                col = ROCK[2]
            img[y + y0, x + x0] = col
    for _ in range(90):                                                             # štěrk (2px kamínky se stínem)
        x, y = int(rng.integers(x0 + 1, x1 - 2)), int(rng.integers(y0 + 1, y1 - 2))
        img[y, x] = ROCK[4]; img[y, x + 1] = ROCK[3]; img[y + 1, x + 1] = ROCK[1]


def cliff_wall(W, H):
    """Holá skála jako zadní stěna: vodorovné vrstvy (střídání okrových a šedých pruhů s vlnkou), svislé
    pukliny, římsy (světlá horní hrana, stín pod ní), nahoře zbytek kachlí věže, kde skála prorazila zeď."""
    s = Spr(W, H)
    for y in range(H):
        for x in range(W):
            wav = 3.0 * math.sin(x * 0.05 + 1) + 1.6 * math.sin(x * 0.17)
            band = int((y + wav) // 12)
            ly = (y + wav) % 12
            streak = (_h(x // 3, band, 4) % 3) - 1                                 # svislé pruhy eroze
            k = [2, 3, 2, 3, 1][band % 5] + (1 if ly < 4 else 0) + (streak if ly > 3 else 0)
            col = STRATA[max(0, min(3, k))]
            if ly < 1 and band % 2 == 0:
                col = ROCK[4]                                                       # světlá hrana tvrdší vrstvy
            elif ly > 11:
                col = ROCK[1]
            if _h(x, y, 2) % 23 == 0:
                col = ROCK[2]
            s.px(x, y, col)
    rng = np.random.default_rng(7)
    for _ in range(9):                                                              # svislé pukliny
        x = int(rng.integers(4, W - 4)); y = int(rng.integers(8, H // 2))
        for k in range(int(rng.integers(14, 40))):
            if y + k >= H:
                break
            x += int(rng.integers(-1, 2)) if k % 3 == 0 else 0
            s.px(x, y + k, ROCK[0]); s.px(x + 1, y + k, ROCK[3])
    for (lx, ly, lw) in ((6, 40, 40), (122, 34, 46), (30, 66, 30), (130, 70, 36)):   # římsy
        for x in range(lx, lx + lw):
            s.px(x, ly, ROCK[5]); s.px(x, ly + 1, ROCK[4])
            for k in range(2, 5):
                s.px(x, ly + k, ROCK[0] if k == 2 else ROCK[1])
    for y in range(0, 10):                                                          # zbytek kachlí nahoře
        for x in range(W):
            edge = 6 + int(3 * math.sin(x * 0.21) + 2 * math.sin(x * 0.5))
            if y < edge:
                lx, ly = x % 8, y % 6
                s.px(x, y, c(0x8A9A94) if (lx == 7 or ly == 5) else c(0xA8B8B0))
            elif y == edge:
                s.px(x, y, ROCK[0])
    return s


def boulder(seed=0, w=30, h=24):
    """Balvan ve 3/4: shluk plošek (každá plocha vlastní tón podle natočení ke světlu), ostré hrany,
    praskliny, lišejník; dole sedí na zemi (rovná pata se stínem)."""
    s = Spr(w, h)
    rng = np.random.default_rng(seed)
    cx, cy = w / 2, h * 0.58
    facets = [(rng.uniform(-1, 1), rng.uniform(-1, 0.6)) for _ in range(11)]
    for y in range(h):
        for x in range(w):
            dx, dy = (x + .5 - cx) / (w / 2), (y + .5 - cy) / (h * 0.55)
            r = math.hypot(dx, dy * 1.0)
            bump = 0.08 * math.sin(math.atan2(dy, dx) * 5 + seed)
            if r > 0.98 + bump or y > h - 2:
                continue
            # nejbližší ploška určuje normálu (fazetovaný kámen)
            fx, fy = min(facets, key=lambda f: (dx - f[0]) ** 2 + (dy - f[1]) ** 2)
            nx, ny = fx * 0.8, fy * 0.8
            nz = math.sqrt(max(0.05, 1 - nx * nx - ny * ny))
            v = 0.2 + 0.8 * max(0.0, -nx * 0.55 - ny * 0.72 + nz * 0.45)
            k = min(5, max(0, int(v * 5.5)))
            if dy > 0.55:
                k = min(k, 1)                                                       # spodek ve stínu
            s.px(x, y, ROCK[k])
    for _ in range(2):                                                              # prasklina
        x, y = int(rng.uniform(w * 0.3, w * 0.7)), int(rng.uniform(h * 0.2, h * 0.4))
        for k in range(int(rng.integers(4, 9))):
            x += int(rng.integers(-1, 2)); y += 1
            if s.opaque(x, y):
                s.px(x, y, ROCK[0])
    for _ in range(6):                                                              # lišejník
        x, y = int(rng.uniform(2, w - 2)), int(rng.uniform(2, h * 0.5))
        if s.opaque(x, y):
            s.px(x, y, c(0x9AA060)); s.px(x + 1, y, c(0x7A8046))
    return selout(s, 0.5, 0.82)


def scree(seed=0, w=40, h=22):
    """Suť (místo setkání): kupa kamenů a kamínků různé velikosti, větší dole, menší nahoře, každý
    kamínek stínovaný jako koule, mezi nimi tmavé mezery."""
    s = Spr(w, h)
    rng = np.random.default_rng(seed)
    stones = []
    for i in range(46):
        t = rng.uniform(0, 1) ** 0.8                                                # výška v kupě
        half = (w / 2 - 2) * (1 - t * 0.85)
        x = w / 2 + rng.uniform(-half, half)
        y = h - 3 - t * (h - 6)
        r = (4.2 - t * 2.2) * rng.uniform(0.75, 1.2)
        stones.append((x, y, max(0.9, r)))
    stones.sort(key=lambda st: st[1])
    for (x, y, r) in stones:
        ramp = [ROCK[0], ROCK[2], ROCK[3], ROCK[4], ROCK[5]] if rng.random() < 0.7 else [ROCK[0], STRATA[0], STRATA[2], STRATA[3]]
        sphere(s, x, y, r, r * 0.8, ramp, amb=0.25)
    return selout(s, 0.5, 0.82)


def pillar(seed=0, w=30, h=56):
    """Skalní věž ze tří na sobě vyvážených balvanů (dole největší); mezi nimi stín, nahoře kamínek."""
    s = Spr(w, h)
    y = h
    for i, (bw, bh) in enumerate(((30, 24), (24, 19), (17, 14))):
        b_ = boulder(seed * 7 + i, bw, bh)
        x0 = (w - bw) // 2 + (i % 2) * 2 - 1
        y -= bh - (4 if i else 0)
        for yy in range(bh):
            for xx in range(bw):
                if b_.a[yy, xx, 3] and 0 <= y + yy < h and 0 <= x0 + xx < w:
                    s.a[y + yy, x0 + xx] = b_.a[yy, xx]
        for xx in range(x0 + 3, x0 + bw - 3):                                      # stín pod horním kamenem
            if 0 <= y + bh < h and s.a[y + bh, xx, 3] if i else False:
                shade_px(s, xx, y + bh, 0.6)
    sphere(s, w / 2 + 1, y + 1, 2.5, 2, [ROCK[1], ROCK[3], ROCK[4], ROCK[5]], amb=0.3)
    return s


def theodolite():
    """Teodolit na trojnožce: tři dřevěné nohy, mosazná hlava s dalekohledem, olovnice; pod ním cedulka."""
    s = Spr(22, 36)
    for (x0, x1) in ((11, 3), (11, 19), (11, 12)):                                  # nohy
        for k in range(26):
            x = x0 + (x1 - x0) * k / 25
            s.px(int(x), 10 + k, WOOD[3] if x1 < 11 else WOOD[1] if x1 > 12 else WOOD[2])
    for x in range(8, 15):
        s.px(x, 9, BRASS[2]); s.px(x, 10, BRASS[0])
    sphere(s, 11.5, 6, 3.2, 2.6, BRASS, amb=0.35)                                   # hlava
    for x in range(4, 20):                                                          # dalekohled
        s.px(x, 4, BRASS[3] if x < 12 else BRASS[2]); s.px(x, 5, BRASS[1])
    s.px(3, 4, c(0x9CC8E8)); s.px(20, 4, c(0x3A4A5A))
    s.vline(11, 11, 20, STEEL[3]); s.px(11, 21, STEEL[1])                           # olovnice
    return selout(s, 0.5, 0.82)


def flag(n=0):
    """Vytyčovací praporek: tenká tyčka, červenobílý praporek, papírek s číslem bodu u paty."""
    s = Spr(10, 20)
    for y in range(2, 20):
        s.px(2, y, WOOD[3]); s.px(3, y, WOOD[1])
    for y in range(2, 8):
        for x in range(4, 4 + (8 - y) + 2):
            s.px(x, y, RED[3] if (x + y) % 4 < 2 else c(0xF4F0E6))
    for x in range(1, 6):
        s.px(x, 15, PAPER[3])
    s.px(2, 15, INK); s.px(4, 15, INK if n % 2 else PAPER[3])
    return selout(s, 0.5, 0.82)


def dry_bush(seed=0):
    """Suchý keř: spleť tenkých šedohnědých větviček z jednoho středu, na koncích pár uschlých lístků."""
    s = Spr(20, 14)
    rng = np.random.default_rng(seed)
    for _ in range(14):
        a = rng.uniform(math.pi * 1.05, math.pi * 1.95)
        L = rng.uniform(5, 9)
        x, y = 10.0, 12.0
        for k in range(int(L)):
            x += math.cos(a) * 1.0; y += math.sin(a) * 0.8
            a += rng.uniform(-0.3, 0.3)
            s.px(int(x), int(y), DRY[1] if k < L / 2 else DRY[2])
        s.px(int(x), int(y) - 1, DRY[3])
    for x in range(8, 13):
        s.px(x, 13, DRY[0])
    return selout(s, 0.55, 0.85)


def skull():
    """Napůl zasypaná lebka něčeho velkého: kostěná klenba s očnicí, roh, zuby; z prachu trčí jen horní půlka."""
    s = Spr(34, 18)
    for y in range(2, 16):
        for x in range(4, 30):
            d = ((x - 15) / 13) ** 2 + ((y - 12) / 10) ** 2
            if d < 1:
                v = 0.85 - (x - 4) / 40 - (y - 2) / 30
                s.px(x, y, BONE[3] if v > 0.6 else BONE[2] if v > 0.4 else BONE[1])
    for y in range(7, 12):                                                          # očnice
        for x in range(9, 14):
            if ((x - 11) / 2.4) ** 2 + ((y - 9.5) / 2.4) ** 2 < 1:
                s.px(x, y, c(0x1A140E))
    for i in range(10):                                                             # roh
        x, y = 22 + i * 0.9, 5 - i * 0.5 + (i / 9) ** 2 * 3
        for t in range(3 - i // 4):
            s.px(int(x), int(y) + t, BONE[2] if t == 0 else BONE[1])
    for x in range(26, 32, 2):                                                      # zuby
        s.px(x, 13, BONE[3]); s.px(x, 14, BONE[2])
    for x in range(0, 34):                                                          # zasypané prachem
        for y in range(14, 18):
            if (x + y) % 3 or y > 15:
                s.px(x, y, DUST[2] if y == 14 else DUST[1])
    return selout(s, 0.5, 0.82)


def rope_fence(w):
    """Provazové zábradlí: dřevěné kolíky po 14 px, mezi nimi prověšený provaz."""
    s = Spr(w, 14)
    posts = list(range(1, w - 1, 14)) + [w - 3]
    for p in posts:
        for y in range(2, 14):
            s.px(p, y, WOOD[3]); s.px(p + 1, y, WOOD[1])
        s.px(p, 1, WOOD[3])
    for a, b in zip(posts, posts[1:]):
        for x in range(a + 1, b + 1):
            t = (x - a) / max(1, b - a)
            y = 4 + round(3 * math.sin(t * math.pi))
            s.px(x, y, ROPE[1]); s.px(x, y + 1, ROPE[0])
    return selout(s, 0.55, 0.85)


def chasm(img, x0, y0, x1, y1, seed=3):
    """Trhlina v podlaze: klikatá tmavá rozsedlina, horní okraj osvětlený, spodní stěna viditelná (3/4)."""
    rng = np.random.default_rng(seed)
    y = (y0 + y1) / 2
    w = 6.0
    for x in range(x0, x1):
        y += rng.uniform(-0.7, 0.7); y = min(y1 - 4, max(y0 + 4, y))
        w = min(9.0, max(4.0, w + rng.uniform(-0.7, 0.7)))
        top, bot = int(y - w), int(y + w)
        for yy in range(top, bot + 1):
            img[yy, x] = (c(0x0C0806) if yy > top + 2 else c(0x2A2018)) if yy < bot - 1 else ROCK[2]
        img[top - 1, x] = ROCK[4]
        img[bot, x] = ROCK[3]; img[bot + 1, x] = DUST[0]

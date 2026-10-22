"""
Díl 1 – POZADÍ Popelavé pláně: dlaždice 16 × 16 px ve stylu kapesních Pokémonů.
Popelavá tráva (3 varianty), vysoká suchá tráva (místa setkání), cesta (kreslí se podle masky
buněk, se zaoblenými rohy a tmavším lemem), terénní stupeň (římsa) a skalní stěna.
"""
import numpy as np
from pix import Spr, c, outline

T = 16
# popelavá tráva: tmavá → světlá
G = [c(0x5C6448), c(0x76805E), c(0x8C9672), c(0xA2AA88), c(0xC6C8B2)]
# vysoká suchá tráva
TG = [c(0x4A4026), c(0x7A6C3E), c(0xA4925A), c(0xC8B676), c(0xE6DAA2)]
# cesta
P = dict(base=c(0xC6BA9E), shade=c(0xB2A68A), edge=c(0x8E8068), dark=c(0x72664E), hi=c(0xDAD0BA), peb=c(0x9C907A))
# římsa / skála
R = [c(0x2E2A26), c(0x4C463E), c(0x6E665C), c(0x8C8478), c(0xB0A898), c(0xCCC4B4)]


def ground(variant=0):
    s = Spr(T, T)
    s.a[:] = G[2]
    rng = np.random.default_rng(10 + variant)
    # jemné žíhání (dvoutónový vzor jako v DS hrách)
    for y in range(T):
        for x in range(T):
            if (x * 3 + y * 5 + variant) % 23 == 0:
                s.px(x, y, G[1])
            elif (x * 7 + y * 2 + variant) % 29 == 0:
                s.px(x, y, G[3])
    # trsy trávy: ˇ-tvar se světlou špičkou
    spots = [(3, 4), (11, 10)] if variant == 0 else [(10, 3), (4, 11)] if variant == 1 else [(7, 7)]
    for (x, y) in spots:
        s.px(x, y, G[0]); s.px(x - 1, y - 1, G[1]); s.px(x + 1, y - 1, G[1])
        s.px(x - 1, y - 2, G[3]); s.px(x + 1, y - 2, G[3]); s.px(x, y - 1, G[0])
    if variant == 1:     # popel
        for (x, y) in ((2, 13), (13, 6), (6, 2)):
            s.px(x, y, G[4])
    if variant == 2:     # kamínek
        s.px(12, 12, G[0]); s.px(13, 12, G[0]); s.px(12, 11, G[4]); s.px(13, 11, G[3])
        s.px(3, 3, G[4])
    return s


def tall_grass():
    """Vysoká tráva: dva řady trsů s posunem, tmavé obrysy (vzor na sebe navazuje)."""
    s = Spr(T, T)
    s.a[:] = TG[1]
    clump = [
        "....o...",
        "...o4o..",
        ".o.o4o.o",
        "o3oo3oo3",
        "o3o23o2o",
        ".o2222o.",
        "..o22o..",
        "...oo...",
    ]
    def put_clump(ox, oy):
        for y, row in enumerate(clump):
            for x, ch in enumerate(row):
                if ch == ".":
                    continue
                col = TG[0] if ch == "o" else TG[int(ch)]
                s.px((ox + x) % T, (oy + y) % T, col)
    for (ox, oy) in ((0, 0), (8, 0), (4, 8), (12, 8)):
        put_clump(ox, oy)
    return s


def rock_wall():
    """Skalní stěna (horní okraj mezi hradbami a lesem): vrstvy se světlými hranami."""
    s = Spr(T, T)
    for y in range(T):
        for x in range(T):
            band = (y + (x // 6) % 2 * 2) % 6
            col = R[2] if band > 1 else R[1]
            if band == 2:
                col = R[3]
            s.px(x, y, col)
    for x in range(0, T, 6):
        s.vline(x, 0, T - 1, R[1])
    return s


def draw_path(img, cells, seed=1):
    """
    Cesta podle masky buněk (True = cesta). Obrys buněk se zaoblí, okraj má tmavší lem
    a dovnitř jdou světlé skvrny a kamínky – jako pískové cesty v DS hrách.
    """
    H, W = cells.shape[0] * T, cells.shape[1] * T
    m = np.zeros((H, W), bool)
    for r in range(cells.shape[0]):
        for q in range(cells.shape[1]):
            if cells[r, q]:
                m[r * T:(r + 1) * T, q * T:(q + 1) * T] = True
    # zaoblení vnějších rohů (poloměr 4) – ořízne rohy, kde nesousedí cesta
    def cell(r, q):
        return 0 <= r < cells.shape[0] and 0 <= q < cells.shape[1] and cells[r, q]
    rad = 4
    for r in range(cells.shape[0]):
        for q in range(cells.shape[1]):
            if not cells[r, q]:
                continue
            for (dr, dq, cy, cx) in ((-1, -1, 0, 0), (-1, 1, 0, T - 1), (1, -1, T - 1, 0), (1, 1, T - 1, T - 1)):
                if not cell(r + dr, q) and not cell(r, q + dq):
                    for yy in range(rad):
                        for xx in range(rad):
                            if (rad - xx - .5) ** 2 + (rad - yy - .5) ** 2 > rad * rad:
                                y = r * T + (yy if dr < 0 else T - 1 - yy)
                                x = q * T + (xx if dq < 0 else T - 1 - xx)
                                m[y, x] = False
    rng = np.random.default_rng(seed)
    p = np.pad(m, 2)
    e1 = m & (~p[1:-3, 2:-2] | ~p[3:-1, 2:-2] | ~p[2:-2, 1:-3] | ~p[2:-2, 3:-1])
    e2 = m & ~e1 & (~p[:-4, 2:-2] | ~p[4:, 2:-2] | ~p[2:-2, :-4] | ~p[2:-2, 4:])
    for y in range(H):
        for x in range(W):
            if not m[y, x]:
                continue
            if e1[y, x]:
                img[y, x] = P["edge"]
            elif e2[y, x]:
                img[y, x] = P["shade"] if (x + y) % 2 == 0 else P["base"]
            else:
                img[y, x] = P["base"]
    # skvrny a kamínky
    ys, xs = np.nonzero(m & ~e1 & ~e2)
    for i in rng.choice(len(ys), size=len(ys) // 40, replace=False):
        y, x = ys[i], xs[i]
        if rng.random() < 0.6:
            img[y, x] = P["hi"]
            if x + 1 < W and m[y, x + 1]:
                img[y, x + 1] = P["hi"]
        else:
            img[y, x] = P["peb"]
            if y + 1 < H and m[y + 1, x]:
                img[y + 1, x] = P["dark"]
    return m


def draw_ledge(img, row, q0, q1):
    """
    Terénní stupeň (římsa) přes buňky q0..q1 ve spodní části řádku [row]: světlý okraj nahoře,
    skalní čelo se svislými rýhami, stín pod ním. Konce jsou zaoblené. Vrací masku překážky.
    """
    y0 = row * T + 7
    x0, x1 = q0 * T + 1, (q1 + 1) * T - 2
    block = np.zeros(img.shape[:2], bool)
    for x in range(x0, x1 + 1):
        end = min(x - x0, x1 - x)
        top = y0 + (2 - min(end, 2))
        for y in range(top, y0 + 9):
            k = y - y0
            col = R[5] if y == top else R[4] if k <= 2 else R[3] if k <= 5 else R[2]
            if k >= 3 and (x * 5) % 7 == 0:
                col = R[1]
            if y == y0 + 8 or end == 0 or k == 2:
                col = R[0]
            img[y, x] = col
            block[y, x] = True
        # stín na trávě pod římsou
        for y in (y0 + 9, y0 + 10):
            if y < img.shape[0]:
                img[y, x, :3] = (img[y, x, :3] * 0.72).astype(np.uint8)
    return block


def parts():
    return [ground(0), ground(1), ground(2), tall_grass(), rock_wall()]

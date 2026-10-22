"""Společné pixel-art nástroje pro Popelavou pláň: sprity RGBA, stínování koulí, obrysy, skládání."""
import math
import numpy as np


def c(h, a=255):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255, a], np.uint8)


class Spr:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.a = np.zeros((h, w, 4), np.uint8)

    def px(self, x, y, col):
        x, y = int(x), int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            self.a[y, x] = col

    def get(self, x, y):
        return self.a[y, x]

    def opaque(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h and self.a[y, x, 3] > 0

    def rect(self, x0, y0, x1, y1, col):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, col)

    def hline(self, x0, x1, y, col):
        self.rect(x0, y, x1, y, col)

    def vline(self, x, y0, y1, col):
        self.rect(x, y0, x, y1, col)

    def line(self, x0, y0, x1, y1, col, w=1):
        n = max(int(round(max(abs(x1 - x0), abs(y1 - y0)))), 1)
        for i in range(n + 1):
            t = i / n
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            for dx in range(w):
                for dy in range(w):
                    self.px(round(x - (w - 1) / 2 + dx), round(y - (w - 1) / 2 + dy), col)

    def ellipse(self, cx, cy, rx, ry, col):
        for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
            for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
                if ((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2 <= 1:
                    self.px(x, y, col)


def outline(s, col, inner=True):
    """Obrys: krajní neprůhledné pixely (inner) nebo pixely kolem (outer) dostanou barvu obrysu."""
    a = s.a
    op = a[:, :, 3] > 0
    nb = np.zeros_like(op)
    p = np.pad(op, 1)
    edge_any = ~p[:-2, 1:-1] | ~p[2:, 1:-1] | ~p[1:-1, :-2] | ~p[1:-1, 2:]
    if inner:
        m = op & edge_any
    else:
        grow = p[:-2, 1:-1] | p[2:, 1:-1] | p[1:-1, :-2] | p[1:-1, 2:]
        m = ~op & grow
    a[m] = col
    return s


def blobs(s, blob_list, ramp, light=(-0.55, -0.75, 0.55), seam=True, top_tint=None):
    """
    Shluk koulí (koruna stromu, keř): každá koule se stínuje sama → „šupinatý“ vzhled jako v Pokémonech.
    blob_list = [(cx, cy, r)] v pořadí kreslení (zadní první). ramp = tmavá → světlá.
    """
    L = np.array(light, float); L /= np.linalg.norm(L)
    owner = -np.ones((s.h, s.w), int)
    for i, (cx, cy, r) in enumerate(blob_list):
        for y in range(max(0, int(cy - r) - 1), min(s.h, int(cy + r) + 2)):
            for x in range(max(0, int(cx - r) - 1), min(s.w, int(cx + r) + 2)):
                dx, dy = (x + .5 - cx) / r, (y + .5 - cy) / r
                d2 = dx * dx + dy * dy
                if d2 > 1:
                    continue
                owner[y, x] = i
                n = np.array([dx, dy, math.sqrt(1 - d2)])
                l = float(n @ L)
                k = 0 if l < 0.05 else 1 if l < 0.42 else 2 if l < 0.78 else 3
                k = min(k, len(ramp) - 1)
                s.a[y, x] = ramp[k]
                if top_tint is not None and dy < -0.55 and dx < 0.3 and (x + y) % 2 == 0:
                    s.a[y, x] = top_tint
    if seam:
        # spáry mezi koulemi: pixel koule, pod kterým (dole/vpravo) je jiná, zadní koule → nejtmavší tón
        for y in range(s.h - 1):
            for x in range(s.w - 1):
                o = owner[y, x]
                if o < 0:
                    continue
                for (nx, ny) in ((x, y + 1), (x + 1, y)):
                    on = owner[ny, nx]
                    if on >= 0 and on < o:
                        s.a[ny, nx] = ramp[0]
    return owner


def paste(dst, spr, x, y):
    """Vloží sprite (binární alfa) do RGBA pole dst na (x, y) = levý horní roh."""
    h, w = spr.a.shape[:2]
    H, W = dst.shape[:2]
    for yy in range(h):
        ty = y + yy
        if not 0 <= ty < H:
            continue
        for xx in range(w):
            tx = x + xx
            if 0 <= tx < W and spr.a[yy, xx, 3] > 0:
                dst[ty, tx] = spr.a[yy, xx]


def shadow(dst, cx, cy, rx, ry, col=(30, 26, 24), k=0.35):
    """Měkký eliptický stín pod objektem (ztmaví, co tam je)."""
    H, W = dst.shape[:2]
    for y in range(int(cy - ry), int(cy + ry) + 1):
        for x in range(int(cx - rx), int(cx + rx) + 1):
            if 0 <= x < W and 0 <= y < H and ((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2 <= 1:
                dst[y, x, :3] = (dst[y, x, :3] * (1 - k) + np.array(col) * k).astype(np.uint8)


def sheet(sprites, scale=4, pad=4, bg=(40, 40, 48, 255), cols=8):
    """Náhledový arch dílů (pro kontrolu a pro uživatele)."""
    from PIL import Image
    rows = [sprites[i:i + cols] for i in range(0, len(sprites), cols)]
    W = max(sum(s.w + pad for s in r) for r in rows) + pad
    H = sum(max(s.h for s in r) + pad for r in rows) + pad
    out = np.zeros((H, W, 4), np.uint8); out[:] = bg
    y = pad
    for r in rows:
        x = pad
        for s in r:
            paste(out, s, x, y); x += s.w + pad
        y += max(s.h for s in r) + pad
    return Image.fromarray(out).resize((W * scale, H * scale), Image.NEAREST)

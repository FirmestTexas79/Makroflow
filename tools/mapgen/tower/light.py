"""
Světlo a hloubka pro interiéry (Věž Řádu Váhy, hala Arény): vržené stíny objektů, kontaktní stín (AO),
stín u paty zdi, světelné kužely z oken, odlesky v leštěné podlaze, viněta a teplé/studené tónování.
Všechno pracuje nad RGBA polem mapy (uint8) a ditheruje přechody, aby zůstal pixelový vzhled.
"""
import math
import numpy as np

BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0


def _q(v, x, y, steps=5):
    """Kvantování s ditherem: plynulá hodnota 0..1 → pixelové stupně."""
    return math.floor(v * steps + BAYER[y % 4, x % 4]) / steps


def darken(img, mask, k, col=(16, 18, 30), dither=True, steps=5):
    """Ztmaví pixely podle masky 0..1 (×k) směrem k barvě stínu (modravá – studený interiér).
    dither=False: tvrdé stupně (pixelové stíny bez zrnění)."""
    H, W = mask.shape
    c = np.array(col, float)
    for y in range(H):
        for x in range(W):
            m = mask[y, x]
            if m <= 0:
                continue
            a = (_q(min(1.0, m), x, y, steps) if dither else round(min(1.0, m) * steps) / steps) * k
            if a > 0:
                img[y, x, :3] = (img[y, x, :3] * (1 - a) + c * a).astype(np.uint8)


def lighten(img, mask, k, col):
    H, W = mask.shape
    c = np.array(col, float)
    for y in range(H):
        for x in range(W):
            m = mask[y, x]
            if m <= 0:
                continue
            a = _q(min(1.0, m), x, y) * k
            if a > 0:
                img[y, x, :3] = np.clip(img[y, x, :3] * (1 - a) + c * a, 0, 255).astype(np.uint8)


def hard_shadow(mask, spr_alpha, x0, y0, sx_per=0.55, sy_per=0.30, v=1.0):
    """Tvrdý pixelový stín: silueta „položená“ na podlahu doprava dolů (pixel ve výšce h nad patou → (+h·sx, +h·sy))."""
    a = np.asarray(spr_alpha) > 0
    h, w = a.shape
    H, W = mask.shape
    base = y0 + h - 1
    for sy in range(h):
        row = np.nonzero(a[sy])[0]
        if not len(row):
            continue
        up = h - 1 - sy
        for sx in range(row[0], row[-1] + 1):
            X = x0 + sx + int(round(up * sx_per)) + 1
            Y = base + int(round(up * sy_per))
            for yy in (Y, Y + 1):
                if 0 <= yy < H and 0 <= X < W:
                    mask[yy, X] = max(mask[yy, X], v)


def cast_shadow(mask, spr_alpha, x0, y0, foot_h, length=0.55, shear=0.75, blur=2):
    """
    Vržený stín objektu do [mask]: silueta nad patou se „položí“ na podlahu doprava dolů
    (světlo shora zleva). foot_h = kolik spodních řádků spritu stojí na zemi (stín začíná u nich).
    """
    a = np.asarray(spr_alpha) > 0
    h, w = a.shape
    H, W = mask.shape
    base = y0 + h - 1                                       # pata
    for sy in range(h):
        for sx in range(w):
            if not a[sy, sx]:
                continue
            up = (h - 1 - sy)                                   # výška pixelu nad patou
            X = x0 + sx + int(up * shear * 0.6) + 1
            Y = base + 1 + int(up * length * 0.35) - (0 if up < foot_h else 0)
            for dy in range(-blur, blur + 1):
                for dx in range(-blur, blur + 1):
                    yy, xx = Y + dy, X + dx
                    if 0 <= yy < H and 0 <= xx < W:
                        f = 1 - (abs(dx) + abs(dy)) / (2 * blur + 1)
                        mask[yy, xx] = max(mask[yy, xx], 0.55 * f)


def contact_shadow(mask, cx, cy, rx, ry, k=0.8):
    """Měkký eliptický kontaktní stín pod patou objektu."""
    H, W = mask.shape
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if 0 <= x < W and 0 <= y < H:
                d = ((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2
                if d < 1:
                    mask[y, x] = max(mask[y, x], k * (1 - d) ** 0.7)


def wall_base_shadow(mask, y_wall, depth=14, k=1.0, x0=0, x1=None):
    """Stín u paty zadní stěny: nejtmavší hned pod stěnou, mizí do [depth] px."""
    H, W = mask.shape
    x1 = W if x1 is None else x1
    for y in range(y_wall, min(H, y_wall + depth)):
        v = k * (1 - (y - y_wall) / depth) ** 1.6
        mask[y, x0:x1] = np.maximum(mask[y, x0:x1], v)


def side_wall_shadow(mask, x_left, x_right, depth=10, k=0.7, y0=0):
    """Stín podél bočních stěn (stěna vlevo stíní víc – světlo jde zleva shora jen okny)."""
    H, W = mask.shape
    for x in range(x_left, min(W, x_left + depth)):
        v = k * 0.6 * (1 - (x - x_left) / depth) ** 1.5
        mask[y0:, x] = np.maximum(mask[y0:, x], v)
    for x in range(max(0, x_right - depth), x_right):
        v = k * (1 - (x_right - 1 - x) / depth) ** 1.5
        mask[y0:, x] = np.maximum(mask[y0:, x], v)


def light_shaft(mask, top_l, top_r, angle_dx, length, fade=True):
    """Šikmý kužel světla z okna: rovnoběžník od spodku okna (top_l..top_r) dolů s posunem angle_dx na px."""
    H, W = mask.shape
    y0 = top_l[1]
    for y in range(y0, min(H, y0 + length)):
        t = (y - y0) / length
        off = (y - y0) * angle_dx
        xa, xb = top_l[0] + off, top_r[0] + off + (y - y0) * 0.12
        for x in range(int(xa), int(xb) + 1):
            if 0 <= x < W:
                edge = min(x - xa, xb - x) / max(1.0, (xb - xa) / 2)
                v = (1 - t) ** 1.2 if fade else 1.0
                mask[y, x] = max(mask[y, x], v * min(1.0, edge * 2.2))


def vignette(mask, cx, cy, rx, ry, k=0.55):
    H, W = mask.shape
    for y in range(H):
        for x in range(W):
            d = math.hypot((x - cx) / rx, (y - cy) / ry)
            if d > 0.55:
                mask[y, x] = max(mask[y, x], k * min(1.0, (d - 0.55) / 0.6) ** 1.4)


def reflect(img, src_box, y_floor, depth, k=0.22, floor_mask=None):
    """Odlesk v leštěné podlaze: řádky nad y_floor se zrcadlově a slabě promítnou pod něj (zvlněně)."""
    x0, y_top, x1 = src_box
    H, W = img.shape[:2]
    for i in range(depth):
        sy = y_floor - 1 - i
        ty = y_floor + i
        if sy < y_top or ty >= H:
            break
        a = k * (1 - i / depth) ** 1.3
        wob = int(round(math.sin(i * 0.9) * 0.6))
        for x in range(x0, x1):
            sx = min(W - 1, max(0, x + wob))
            if floor_mask is not None and not floor_mask[ty, x]:
                continue
            if BAYER[ty % 4, x % 4] > 0.85:
                continue
            img[ty, x, :3] = (img[ty, x, :3] * (1 - a) + img[sy, sx, :3] * a).astype(np.uint8)


def tone(img, top_col, bottom_col, k=0.10):
    """Celkové tónování: nahoře studenější, dole teplejší (nebo naopak) – sjednotí paletu."""
    H = img.shape[0]
    for y in range(H):
        t = y / max(1, H - 1)
        col = np.array(top_col) * (1 - t) + np.array(bottom_col) * t
        img[y, :, :3] = np.clip(img[y, :, :3] * (1 - k) + col * k, 0, 255).astype(np.uint8)

"""
Použití:  python tools/mapgen/gen_tower_lift.py
Výstup:   app/src/main/res/drawable-nodpi/tower_lift_map.png (+ tools/mapgen/tower_lift_map_debug.png)

Plánek Věže Řádu Váhy vedle tlačítek výtahu: tenká cihlová věž od věžičky s praporkem přes patra 13 → 0
(vestibul s portálem na úrovni země) až do podzemí (−1 Hunfreyho skrýš v betonu, −2 Sektor Hlubiny v čediči,
pod ním kořeny Spící). Každé patro má cihly a okno v barvě svého prostředí, válcové stínování (světlo zleva),
římsy mezi patry. Stav pater (zamčeno / tady jsi) kreslí přes obrázek TowerLiftView.
ROZMĚRY jsou ZDROJ PRAVDY i pro TowerLiftView.kt: W, TURRET, FH, GROUND.
"""
import math, os, sys
import numpy as np
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
from pix import c  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
W = 40
TURRET = 34            # výška věžičky nad patrem 13
FH = 14                # výška jednoho patra
GROUND = 4             # pás trávy a hlíny mezi vestibulem a −1
ROOTS = 12             # kořeny pod −2
X0, X1 = 8, 31         # tělo věže (včetně)
H = TURRET + 14 * FH + GROUND + 2 * FH + ROOTS
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0

# patro → (barva cihel, barva okna)
FLOORS = {
    13: (0x2A3458, 0xFFD870), 12: (0x2E4630, 0x8AF04A), 11: (0xD890B8, 0xFFE0F4), 10: (0x6A2A1E, 0xFF8A2A),
    9: (0x9AB8D0, 0xC8F4FF), 8: (0x2C2A50, 0xFFD890), 7: (0x2A6A78, 0x6AD0F0), 6: (0x4A2C6A, 0xC890FF),
    5: (0x9A7A54, 0xF0D8A0), 4: (0x3E6A34, 0xB8F07A), 3: (0x6A9A4A, 0xBCE4FF), 2: (0xB0A890, 0xFFFFF0),
    1: (0x4A3424, 0x8AE89A), 0: (0x3A4A6A, 0xFFE0A0), -1: (0x5A5E64, 0xFFB860), -2: (0x26262E, 0x9AC8FF),
}


def top_of(f):
    """Horní řádek patra f (stejně v TowerLiftView.kt)."""
    return TURRET + (13 - f) * FH if f >= 0 else TURRET + 14 * FH + GROUND + (-f - 1) * FH


def ramp(base, n=6):
    r, g, b = (base >> 16) & 255, (base >> 8) & 255, base & 255
    out = []
    for i in range(n):
        k = 0.42 + i * 0.26                                                  # tmavší → světlejší
        out.append(np.array([min(255, r * k + (18 if i == n - 1 else 0)), min(255, g * k + (18 if i == n - 1 else 0)),
                             min(255, b * k + (14 if i == n - 1 else 0)), 255], np.uint8))
    return out


def pick(rmp, v, x, y):
    f = max(0.0, min(0.999, v)) * (len(rmp) - 1)
    i = int(f)
    return rmp[i + 1] if (f - i) > BAYER[y % 4, x % 4] * 0.8 + 0.1 and i + 1 < len(rmp) else rmp[i]


def h(x, y, s=0):
    n = (x * 374761393 + y * 668265263 + s * 982451653) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return (n ^ (n >> 16)) % 1000 / 1000.0


img = np.zeros((H, W, 4), np.uint8)
cx = (X0 + X1) / 2
half = (X1 - X0 + 1) / 2


def shade(x):
    """Válcové stínování: světlo zleva."""
    nx = (x + .5 - cx) / half
    return 0.62 - 0.5 * nx - 0.12 * nx * nx


def brick_wall(y0, y1, base, kind="brick", seed=0):
    rmp = ramp(base)
    for y in range(y0, y1):
        for x in range(X0, X1 + 1):
            v = shade(x)
            ry = y - y0
            if kind == "brick":
                row = ry // 3
                off = 3 if row % 2 else 0
                mortar = (ry % 3 == 2) or ((x + off) % 6 == 5)
                bid = ((x + off) // 6, row)
                v += (h(*bid, seed) - 0.5) * 0.22                           # každá cihla trochu jiná
                if mortar:
                    v -= 0.32
                elif (ry % 3 == 0) and not mortar:
                    v += 0.08                                               # horní hrana cihly chytá světlo
            elif kind == "concrete":
                v += (h(x // 3, y // 2, seed) - 0.5) * 0.12 - (0.25 if ry % 7 == 6 else 0)
            elif kind == "basalt":
                col = (x - X0) // 4
                v += (0.12 if (x - X0) % 4 == 0 else -0.08 if (x - X0) % 4 == 3 else 0) + (h(col, ry // 5, seed) - 0.5) * 0.2
                if (ry + col * 3) % 6 == 5:
                    v -= 0.3
            if x in (X0, X1):
                v = 0.0 if x == X1 else min(v, 0.35)
            img[y, x] = pick(rmp, v, x, y)


def cornice(y, base):
    """Kamenná římsa nad patrem: přesahuje o 1 px, světlá horní hrana a stín pod ní."""
    rmp = ramp(0x8A8478)
    for x in range(X0 - 1, X1 + 2):
        img[y, x] = pick(rmp, shade(x) + 0.35, x, y)
        if y + 1 < H:
            img[y + 1, x] = pick(rmp, shade(x) - 0.25, x, y + 1)
    if y + 2 < H:
        for x in range(X0, X1 + 1):
            img[y + 2, x, :3] = (img[y + 2, x, :3] * 0.72).astype(np.uint8)  # stín pod římsou


def window(f, y0, glow, wide=False, door=False):
    """Klenuté okno (nebo portál vestibulu) se září v barvě patra, rám, příčka, odlesk."""
    # záře okna na okolních cihlách
    gcol = np.array([(glow >> 16) & 255, (glow >> 8) & 255, glow & 255], float)
    for y in range(y0 + 1, y0 + FH):
        for x in range(X0 + 1, X1):
            d = math.hypot((x + .5 - cx) / 7.5, (y + .5 - (y0 + FH / 2 + 1)) / 5.5)
            if d < 1 and BAYER[y % 4, x % 4] < (1 - d) * 1.4:
                img[y, x, :3] = np.clip(img[y, x, :3] * 0.72 + gcol * 0.28, 0, 255).astype(np.uint8)
    w = 8 if door else (6 if wide else 4)
    hgt = 10 if door else 7
    xl = int(cx - w / 2)
    yt = y0 + (FH - hgt) // 2 + (1 if not door else 2)
    gr, gg, gb = (glow >> 16) & 255, (glow >> 8) & 255, glow & 255
    frame = ramp(0x6A6258)
    for y in range(yt - 1, yt + hgt + 1):
        for x in range(xl - 1, xl + w + 1):
            arch = y - yt < w // 2 and math.hypot(x + .5 - (xl + w / 2), (y + .5 - (yt + w / 2))) > w / 2 + 0.6
            if arch and y >= yt:
                continue
            edge = x in (xl - 1, xl + w) or y in (yt - 1, yt + hgt)
            if y == yt - 1 and math.hypot(x + .5 - (xl + w / 2), (y + .5 - (yt + w / 2))) > w / 2 + 1.4:
                continue
            if edge:
                img[y, x] = pick(frame, 0.7 if x == xl - 1 else 0.3, x, y)
            else:
                k = 1.15 - (y - yt) / hgt * 0.35 - abs(x + .5 - (xl + w / 2)) / w * 0.3
                col = np.array([gr * k, gg * k, gb * k])
                if (x - xl) == 1 and y - yt < hgt // 2:
                    col = col * 0.6 + 255 * 0.4                             # odlesk na skle
                img[y, x, :3] = np.clip(col, 0, 255).astype(np.uint8); img[y, x, 3] = 255
    if not door:
        for y in range(yt + 1, yt + hgt):
            img[y, int(cx) - (0 if w % 2 else 1) + (0 if wide else 0), :3] = frame[1][:3]
        img[yt + hgt // 2 + 1, xl:xl + w, :3] = frame[1][:3]                # příčka
    for x in range(xl - 1, xl + w + 1):                                     # parapet
        img[yt + hgt + 1, x] = frame[4]
    if door:
        for x in range(xl - 2, xl + w + 2):                                 # schod
            img[yt + hgt + 1, x] = frame[5]; img[yt + hgt + 2 if yt + hgt + 2 < H else yt + hgt + 1, x] = frame[2]


# ── patra ────────────────────────────────────────────────────────────────────
for f in range(13, -3, -1):
    base, glow = FLOORS[f]
    y0 = top_of(f)
    kind = "concrete" if f == -1 else "basalt" if f == -2 else "brick"
    brick_wall(y0, y0 + FH, base, kind, seed=f + 20)
    if f >= 0:
        cornice(y0, base)
    window(f, y0, glow, wide=f in (13, 11, 3), door=(f == 0))
    if f == 4:                                                              # břečťan na Zarostlém patře
        for (x, y) in ((X0, y0 + 3), (X0 + 1, y0 + 4), (X0, y0 + 6), (X0 + 1, y0 + 8), (X1 - 1, y0 + 5), (X1, y0 + 7), (X1 - 1, y0 + 9)):
            img[y, x] = c(0x5AA040) if (x + y) % 2 else c(0x2E6A28)
    if f == 9:                                                              # rampouchy pod římsou Mrazírny
        for x in range(X0 + 1, X1, 3):
            img[y0 + 2, x] = c(0xE8F8FF); img[y0 + 3, x] = c(0xB0D8F0)
    if f == 10:                                                             # saze nad oknem Spalovny
        for x in range(int(cx) - 3, int(cx) + 3):
            img[y0 + 3, x, :3] = (img[y0 + 3, x, :3] * 0.6).astype(np.uint8)
    if f == 7:                                                              # mokrá hladina u paty patra
        for x in range(X0, X1 + 1):
            img[y0 + FH - 2, x, :3] = (img[y0 + FH - 2, x, :3] * 0.6 + np.array([40, 120, 150]) * 0.4).astype(np.uint8)

# ── věžička: užší válec s cimbuřím, kuželová střecha, praporek ────────────────
tx0, tx1 = 13, 26
tb = TURRET                                                              # spodek věžičky = vršek patra 13
tower_r = ramp(0x2A3458)
for y in range(tb - 13, tb):
    for x in range(tx0, tx1 + 1):
        nx = (x + .5 - (tx0 + tx1 + 1) / 2) / ((tx1 - tx0 + 1) / 2)
        v = 0.62 - 0.5 * nx
        ry = y - (tb - 13); off = 2 if (ry // 3) % 2 else 0
        if ry % 3 == 2 or (x + off) % 5 == 4:
            v -= 0.3
        if x in (tx0, tx1):
            v = 0.0 if x == tx1 else min(v, 0.35)
        img[y, x] = pick(tower_r, v, x, y)
for (x, y) in ((19, tb - 9), (20, tb - 9), (19, tb - 8), (20, tb - 8), (19, tb - 7), (20, tb - 7)):     # okénko věžičky
    img[y, x] = c(0xFFE8A0) if x == 19 else c(0xE0B860)
for x in range(tx0 - 2, tx1 + 3):                                         # ochoz s cimbuřím na patře 13
    img[tb - 1, x] = pick(ramp(0x8A8478), shade(x) + 0.3, x, tb - 1)
for x in range(X0 - 1, X1 + 2):
    if (x - X0) % 4 < 2:
        for y in (tb - 3, tb - 2):
            img[y, x] = pick(ramp(0x8A8478), shade(x) + 0.15, x, y)
roof = [c(0x3A1414), c(0x5A1E1A), c(0x80302A), c(0xA84838), c(0xCC6A50)]
apex_y = tb - 30
for y in range(apex_y, tb - 13):                                          # kuželová střecha s taškami
    k = (y - apex_y) / (tb - 13 - apex_y)
    hw = 1 + k * ((tx1 - tx0) / 2 + 2)
    for x in range(int((tx0 + tx1 + 1) / 2 - hw), int((tx0 + tx1 + 1) / 2 + hw) + 1):
        nx = (x + .5 - (tx0 + tx1 + 1) / 2) / (hw + 0.01)
        v = 0.6 - 0.55 * nx
        if (y - apex_y) % 3 == 2:
            v -= 0.25                                                       # řady tašek
        if abs(nx) > 0.9:
            v -= 0.3
        img[y, x] = pick(roof, v, x, y)
gold = ramp(0xC8A040)
for y in range(apex_y - 6, apex_y + 1):                                   # stožár a praporek s vahami
    img[y, 20] = gold[3]
for y in range(apex_y - 6, apex_y - 2):
    for x in range(21, 21 + (6 - (y - (apex_y - 6)) // 2)):
        img[y, x] = c(0x2A3458) if (x + y) % 5 else c(0xC8A040)
img[apex_y - 7, 20] = gold[5]

# ── země: tráva, hlína, kořeny, podzemí ──────────────────────────────────────
gy = top_of(0) + FH                                                      # úroveň země
soil = ramp(0x4A3424)
for y in range(gy, H):
    for x in range(W):
        if X0 <= x <= X1 and y < top_of(-2) + FH:
            continue
        d = (y - gy) / (H - gy)
        v = 0.55 - d * 0.45 + (h(x // 2, y // 2, 7) - 0.5) * 0.25
        col = pick(soil, v, x, y)
        if h(x, y, 9) > 0.93:
            col = c(0x6A6258) if d < 0.6 else c(0x3A3A44)                  # kamínky
        img[y, x] = col
for x in range(W):                                                       # tráva
    if not (X0 <= x <= X1):
        img[gy, x] = c(0x5AA040) if (x * 7) % 3 else c(0x7AC85A)
        img[gy + 1, x] = c(0x3E7A30)
        if h(x, 1, 3) > 0.6:
            img[gy - 1, x] = c(0x5AA040)
for x in range(X0, X1 + 1):                                               # práh pod vestibulem
    img[gy, x] = c(0x8A8478); img[gy + 1, x] = c(0x5A564E)
for y in range(gy + 2, gy + GROUND):                                       # spára mezi vestibulem a −1
    for x in range(X0, X1 + 1):
        img[y, x] = pick(soil, 0.3 + (h(x, y) - 0.5) * 0.2, x, y)
# kořeny Spící pod −2: bledé, sbíhají se dolů ke kmeni
bark = ramp(0xA29C92)
by = top_of(-2) + FH
for i, sx in enumerate((2, 9, 17, 23, 30, 37)):
    for t in np.linspace(0, 1, 30):
        x = sx + (cx - sx) * t * t
        y = by - 8 + (H - 1 - by + 8) * t if sx in (2, 37) else by + (H - 1 - by) * t
        X, Y = int(x), int(y)
        if 0 <= X < W and 0 <= Y < H and not (X0 <= X <= X1 and Y < by):
            img[Y, X] = pick(bark, 0.7 - t * 0.4 - (0.2 if i % 2 else 0), X, Y)
for y in range(by, H):                                                    # kmen
    for x in range(int(cx) - 2, int(cx) + 3):
        img[y, x] = pick(bark, 0.65 - (x - cx) * 0.15, x, y)
for (x, y) in ((6, H - 3), (33, H - 4), (14, H - 2), (26, H - 2)):         # modrá světla v hloubce
    img[y, x] = c(0x9AC8FF)

Image.fromarray(img, "RGBA").save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_lift_map.png"))
dbg = Image.new("RGBA", (W, H), (60, 52, 44, 255)); dbg.alpha_composite(Image.fromarray(img, "RGBA"))
dbg.resize((W * 4, H * 4), Image.NEAREST).save(os.path.join(HERE, "tower_lift_map_debug.png"))
print("ok", W, H, {f: top_of(f) for f in (13, 0, -1, -2)})

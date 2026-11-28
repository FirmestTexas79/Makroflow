"""
Relikvie z přetavení Výhně (docs/adr/0118) – 40 × 40, pixel art. Tři legendární artefakty, každý s vlastní barvou:
  0  Srdce výhně      – obsidiánové srdce rozpraskané žhavým magmatem, zlatá korunka mezi laloky, plamínky, žár
  1  Kovadlina předků – starobylá kovadlina z temné oceli se zlatou intarzií a tyrkysově zářícími runami předků
  2  Kladivo mistra   – válečné kladivo: ocelová hlava se zlatými objímkami a žhnoucím rubínem, kožený omotávaný topor

Kreslí se z tvarů (masky) a „výškové mapy“ – z ní se počítá světlo zleva shora a stín; barva se pak bere z ručně
volené rampy materiálu (5–6 odstínů), takže výsledek zůstane pixelový (žádné plynulé přechody). Navrch obrys,
záře kolem (poloprůhledné pixely) a jiskry.
"""
import math
import numpy as np
from PIL import Image, ImageDraw

N = 40
OUT_C = (22, 12, 16)

# ── barevné rampy (od nejtmavší po nejsvětlejší) ─────────────────────────────
OBSIDIAN = [(16, 10, 20), (30, 18, 34), (48, 28, 50), (70, 42, 70), (100, 64, 96), (140, 100, 132)]
MAGMA = [(110, 20, 8), (176, 44, 10), (232, 92, 18), (255, 150, 36), (255, 206, 90), (255, 246, 196)]
GOLD = [(84, 44, 10), (140, 86, 18), (198, 136, 30), (238, 186, 58), (255, 226, 120), (255, 250, 214)]
STEEL = [(26, 28, 40), (46, 50, 66), (72, 78, 98), (106, 114, 136), (150, 160, 182), (206, 214, 232), (244, 248, 255)]
RUNE = [(14, 70, 86), (24, 140, 160), (70, 214, 222), (170, 252, 246), (240, 255, 255)]
WOOD = [(46, 24, 14), (78, 44, 22), (114, 68, 34), (152, 98, 52)]
LEATHER = [(40, 18, 14), (70, 30, 22), (104, 48, 32), (138, 70, 46)]
RUBY = [(70, 6, 18), (140, 14, 30), (210, 34, 46), (250, 92, 84), (255, 190, 170), (255, 250, 240)]

LIGHT = np.array([-0.62, -0.66, 0.42]); LIGHT /= np.linalg.norm(LIGHT)


def canvas():
    return np.zeros((N, N, 4), np.float32)


def mask_poly(pts):
    im = Image.new("L", (N, N), 0); ImageDraw.Draw(im).polygon(pts, fill=255)
    return np.asarray(im) > 0


def dist_inside(m):
    """Vzdálenost k okraji masky uvnitř (pro výškovou mapu); malá mřížka → hrubá síla stačí."""
    ys, xs = np.nonzero(~m)
    edge = np.stack([ys, xs], 1).astype(np.float32)
    d = np.zeros(m.shape, np.float32)
    for y, x in zip(*np.nonzero(m)):
        d[y, x] = np.sqrt(((edge - (y, x)) ** 2).sum(1)).min() if len(edge) else 0
    return d


def shade(height):
    """Lambert ze sklonu výškové mapy → 0..1."""
    gy, gx = np.gradient(height)
    n = np.dstack([-gx, -gy, np.ones_like(height)])
    n /= np.linalg.norm(n, axis=2, keepdims=True)
    return np.clip((n * LIGHT).sum(2), 0, 1)


def paint(img, m, ramp, value):
    """Obarví masku z rampy: value 0..1 → index rampy (bez plynulých přechodů)."""
    idx = np.clip(np.round(value * (len(ramp) - 1)), 0, len(ramp) - 1).astype(int)
    for y, x in zip(*np.nonzero(m)):
        img[y, x, :3] = ramp[idx[y, x]]; img[y, x, 3] = 255


def px(img, x, y, c, a=255):
    if 0 <= x < N and 0 <= y < N:
        img[y, x, :3] = c; img[y, x, 3] = a


def outline(img, col=OUT_C):
    a = img[:, :, 3] >= 200
    n = np.zeros_like(a)
    n[1:] |= a[:-1]; n[:-1] |= a[1:]; n[:, 1:] |= a[:, :-1]; n[:, :-1] |= a[:, 1:]
    e = n & ~a
    img[e, :3] = col; img[e, 3] = 255


def glow(img, col, r1=2.2, r2=3.6, a1=110, a2=48):
    """Záře kolem siluety: dva prstence poloprůhledných pixelů (v aplikaci na tmavém slotu svítí)."""
    solid = img[:, :, 3] >= 200
    ys, xs = np.nonzero(solid)
    pts = np.stack([ys, xs], 1).astype(np.float32)
    for y in range(N):
        for x in range(N):
            if img[y, x, 3] > 0:
                continue
            d = np.sqrt(((pts - (y, x)) ** 2).sum(1)).min()
            if d <= r1:
                img[y, x, :3] = col; img[y, x, 3] = a1
            elif d <= r2 and (x + y) % 2 == 0:
                img[y, x, :3] = col; img[y, x, 3] = a2


def sparkle(img, x, y, c=(255, 252, 230), arm=2):
    px(img, x, y, (255, 255, 255))
    for k in range(1, arm + 1):
        a = 255 if k == 1 else 150
        for dx, dy in ((k, 0), (-k, 0), (0, k), (0, -k)):
            if img[y + dy, x + dx, 3] < 200 if 0 <= y + dy < N and 0 <= x + dx < N else False:
                px(img, x + dx, y + dy, c, a)


# ════════════════════════════════════════════════════════════════════════════
def heart():
    img = canvas()
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    # srdce: implicitní křivka (x² + y² − 1)³ − x²y³ ≤ 0, natažené na ~30 px
    X = (xx + 0.5 - 20) / 13.2
    Y = -(yy + 0.5 - 23.5) / 12.6
    m = (X * X + Y * Y - 1) ** 3 - X * X * Y ** 3 <= 0
    h = dist_inside(m)
    h = np.sqrt(np.clip(h / max(h.max(), 1), 0, 1)) * 6           # oblá „kopule“
    lit = shade(h)
    val = np.clip(0.12 + 0.95 * lit + 0.10 * (h / 6), 0, 1)
    paint(img, m, OBSIDIAN, val)
    # praskliny: Voronoi hrany uvnitř srdce, žhavější blíž ke středu
    rng = np.random.default_rng(7)
    # praskliny paprskovitě od jádra: lomené čáry s náhodným zalomením (ne mřížka)
    crack = np.zeros((N, N), bool); heat = np.zeros((N, N), np.float32)
    for y, x in zip(*np.nonzero(m)):
        heat[y, x] = max(0.0, 1 - math.hypot((x - 20) / 12.0, (y - 23) / 11.0))
    for a0 in (-150, -105, -62, -20, 28, 72, 118, 160):
        a = math.radians(a0 + rng.uniform(-10, 10)); x, y = 20.0, 23.0
        for step in range(16):
            a += rng.uniform(-0.5, 0.5)
            x += math.cos(a); y += math.sin(a)
            xi, yi = int(round(x)), int(round(y))
            if not (0 <= xi < N and 0 <= yi < N) or not m[yi, xi]: break
            crack[yi, xi] = True
            if step in (5, 9) and rng.uniform() < 0.7:                     # odbočka
                b = a + rng.choice((-1, 1)) * 0.9; bx, by = x, y
                for _ in range(int(rng.integers(2, 5))):
                    bx += math.cos(b); by += math.sin(b)
                    bxi, byi = int(round(bx)), int(round(by))
                    if 0 <= bxi < N and 0 <= byi < N and m[byi, bxi]: crack[byi, bxi] = True
    crack &= dist_inside(m) > 1.5
    for y, x in zip(*np.nonzero(crack)):
        px(img, x, y, MAGMA[int(np.clip(1 + heat[y, x] * 4.2, 1, 5))])
    # žár prosvítá kolem prasklin (sousední pixely teplejší)
    near = np.zeros_like(crack)
    near[1:] |= crack[:-1]; near[:-1] |= crack[1:]; near[:, 1:] |= crack[:, :-1]; near[:, :-1] |= crack[:, 1:]
    for y, x in zip(*np.nonzero(near & m & ~crack)):
        c = np.array(img[y, x, :3]); img[y, x, :3] = c * 0.55 + np.array(MAGMA[1]) * 0.45
    # rozžhavené jádro uprostřed – kosočtverec
    cx, cy = 20, 23
    for y in range(cy - 4, cy + 5):
        for x in range(cx - 4, cx + 5):
            d = abs(x - cx) + abs(y - cy)
            if d <= 4:
                px(img, x, y, MAGMA[5] if d <= 1 else MAGMA[4] if d <= 2 else MAGMA[3] if d <= 3 else MAGMA[2])
    # odlesk na obsidiánu vlevo nahoře
    for (x, y) in ((11, 16), (12, 15), (13, 15), (10, 17), (11, 18)):
        if m[y, x]: px(img, x, y, OBSIDIAN[5])
    px(img, 12, 16, (220, 200, 230))
    # zlatá korunka v zářezu mezi laloky + zlatá špička dole
    notch = next(y for y in range(N) if m[y, 20])                           # zářez mezi laloky
    base = notch + 1
    for i in range(16, 25):
        px(img, i, base, GOLD[3] if i < 20 else GOLD[2]); px(img, i, base + 1, GOLD[1])
    for (dx, hgt) in ((-3, 2), (-2, 4), (-1, 2), (0, 5), (1, 2), (2, 4), (3, 2)):
        for k in range(1, hgt + 1):
            px(img, 20 + dx, base - k, GOLD[4] if dx <= 0 else GOLD[3])
        px(img, 20 + dx, base - hgt, GOLD[5])
    px(img, 20, base - 6, RUBY[3]); px(img, 20, base - 7, RUBY[5]); px(img, 19, base - 6, RUBY[2]); px(img, 21, base - 6, RUBY[1])
    tip = max(y for y in range(N) if m[y, 20])
    for (x, y, c) in ((19, tip, GOLD[3]), (20, tip, GOLD[4]), (21, tip, GOLD[2]), (20, tip + 1, GOLD[3]), (20, tip + 2, GOLD[1])):
        px(img, x, y, c)
    # plamínky nad laloky
    for (bx, hgt, lean) in ((12, 7, -1), (28, 6, 1), (9, 4, -1), (31, 4, 1)):
        by = next(y for y in range(N) if m[y, bx]) - 1                      # těsně nad lalokem
        for k in range(hgt):
            w_ = max(0, (hgt - k) // 2 - (1 if k == 0 else 0))
            sx = bx + (lean if k > hgt * 0.55 else 0)
            for dx in range(-w_, w_ + 1):
                t = k / hgt
                c = MAGMA[5] if dx == 0 and t < 0.45 else MAGMA[4] if t < 0.7 else MAGMA[3]
                if abs(dx) == w_ and w_ > 0: c = MAGMA[2]
                px(img, sx + dx, by - k, c)
    outline(img)
    glow(img, (255, 120, 30))
    for (x, y) in ((6, 6), (34, 4), (35, 26), (4, 28)):
        sparkle(img, x, y, (255, 214, 120), 1)
    return img


def anvil():
    img = canvas()
    face = mask_poly([(10, 11), (36, 11), (38, 13), (10, 14)])                     # horní plocha (3/4 pohled)
    horn = mask_poly([(1, 14), (5, 12), (10, 11), (10, 17), (6, 17)])               # roh
    body = mask_poly([(10, 14), (38, 13), (38, 18), (32, 19), (28, 21), (27, 26), (32, 28), (35, 33), (5, 33), (8, 28), (13, 26),
                      (12, 21), (10, 19)])
    heel = mask_poly([(36, 11), (39, 12), (39, 17), (38, 18), (38, 13)])
    m = face | horn | body | heel
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    # těleso: svisle tmavne, zleva světlejší, v pase (zúžení) tmavší
    v = 0.62 - 0.40 * (yy - 14) / 19 + 0.18 * (1 - xx / 39)
    waist = (yy > 19) & (yy < 27)
    v = np.where(waist, v - 0.12, v)
    v = np.where((yy >= 28), 0.46 - 0.30 * (yy - 28) / 5 + 0.12 * (1 - xx / 39), v)     # patky
    paint(img, body | heel, STEEL, np.clip(v, 0, 1))
    paint(img, horn, STEEL, np.clip(0.75 - 0.30 * (yy - 12) / 5 + 0.1 * (1 - xx / 10), 0, 1))
    paint(img, face, STEEL, np.clip(0.88 + 0.12 * (1 - xx / 39), 0, 1))
    # ostré hrany: přední hrana plochy světlá, pod ní stín; odlesky
    for x in range(10, 38): px(img, x, 14, STEEL[5] if x < 26 else STEEL[4]); px(img, x, 15, STEEL[2])
    for x in range(3, 10): px(img, x, 13 if x > 4 else 14, STEEL[6] if x < 7 else STEEL[5])
    for x in range(12, 20): px(img, x, 12, STEEL[6])
    for y in range(16, 19): px(img, 37, y, STEEL[1])
    # zlatá intarzie: linka pod hranou a na patce, nýty
    for x in range(11, 37): px(img, x, 17, GOLD[3] if x % 6 else GOLD[5])
    for x in range(9, 33): px(img, x, 30, GOLD[3] if (x % 5) else GOLD[5]); px(img, x, 31, GOLD[1])
    for (x, y) in ((8, 29), (32, 29), (8, 31), (33, 31)):
        px(img, x, y, GOLD[2])
    # runy předků na pase – svítí tyrkysově
    runes = [
        [(1, 0), (1, 1), (1, 2), (1, 3), (1, 4), (0, 0), (2, 0), (0, 1), (2, 1)],          # ᛉ (obrácená) – ochrana
        [(1, 0), (1, 1), (1, 2), (1, 3), (1, 4), (0, 1), (2, 1), (0, 2), (2, 2)],          # ᛏ – síla
        [(0, 0), (0, 1), (0, 2), (0, 3), (0, 4), (2, 0), (2, 1), (2, 2), (2, 3), (2, 4), (1, 1), (1, 2)],   # ᛗ – lid předků
    ]
    for i, rn in enumerate(runes):
        ox, oy = 14 + i * 4, 21
        for (dx, dy) in rn:
            px(img, ox + dx, oy + dy, RUNE[3])
        for (dx, dy) in rn:                                                         # záře run do oceli
            for (ax, ay) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                x, y = ox + dx + ax, oy + dy + ay
                if m[y, x] and tuple(img[y, x, :3]) != RUNE[3]:
                    c = np.array(img[y, x, :3]); img[y, x, :3] = c * 0.5 + np.array(RUNE[1]) * 0.5
    px(img, 15, 21, RUNE[4]); px(img, 19, 21, RUNE[4]); px(img, 22, 21, RUNE[4])
    outline(img)
    glow(img, (60, 220, 230), 1.8, 3.4, 90, 40)
    # duchové světlo předků: stoupající tyrkysové jiskry
    for (x, y, c) in ((17, 6, RUNE[3]), (21, 3, RUNE[2]), (25, 7, RUNE[3]), (14, 3, RUNE[1]), (28, 4, RUNE[2])):
        px(img, x, y, c, 220)
    sparkle(img, 34, 5, (190, 255, 250), 2)
    sparkle(img, 4, 26, (190, 255, 250), 1)
    return img


def hammer():
    img = canvas()
    ang = math.radians(45)
    ca, sa = math.cos(ang), math.sin(ang)
    cx, cy = 18.5, 21.5
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            u = dx * ca - dy * sa             # podél toporu (+ k hlavě, vpravo nahoru)
            v = dx * sa + dy * ca             # napříč
            # hlava: kvádr se rozšířenými údernými plochami
            hw = 6.0 + (1.2 if abs(v) > 8.5 else 0.0)
            if 5.0 <= u <= 5.0 + 2 * hw * 0.62 + 0.5 and abs(v) <= 11.5:
                uu = (u - 5.0) / (2 * hw * 0.62 + 0.5)                    # 0..1 přes tloušťku hlavy
                # světlo zleva shora: levá horní strana hlavy (v < 0) a horní hrana (uu → 1) světlejší, spodek tmavý
                t = 0.46 - 0.30 * (v / 11.5) + 0.16 * (uu - 0.5)
                if uu < 0.14: t -= 0.22
                if uu > 0.86: t += 0.20
                if abs(v) > 10.4: t = 0.92 if v < 0 else 0.30               # úderné plochy
                ramp = STEEL
                if 5.6 <= abs(v) <= 7.0: ramp, t = GOLD, 0.62 + 0.3 * (1 - uu) - (0.25 if v > 0 else 0)   # zlaté objímky
                idx = int(np.clip(round(t * (len(ramp) - 1)), 0, len(ramp) - 1))
                px(img, x, y, ramp[idx]); continue
            # topor
            if -17.5 <= u < 5.0 and abs(v) <= 1.6:
                t = 0.75 - 0.35 * (v + 1.6) / 3.2
                if -11 <= u <= -1:                                         # kožená omotávka (šikmé pruhy)
                    band = int((u + v * 0.8) // 1.6) % 2
                    ramp, t = LEATHER, t - (0.25 if band else 0)
                elif u < -14.5:                                            # zlatá hlavice
                    ramp = GOLD; t = 0.85 - 0.45 * (v + 1.6) / 3.2
                else:
                    ramp = WOOD
                idx = int(np.clip(round(t * (len(ramp) - 1)), 0, len(ramp) - 1))
                px(img, x, y, ramp[idx])
            # hlavice: kulička na konci
            if -19.5 <= u < -14.5 and abs(v) <= 2.6 and (u + 17) ** 2 + v * v <= 7.5:
                t = 0.95 - 0.5 * ((v + 2.6) / 5.2) - 0.15 * ((u + 17) / 2.5)
                px(img, x, y, GOLD[int(np.clip(round(t * 5), 0, 5))])
    # rubín ve středu hlavy (u ≈ 9, v ≈ 0)
    gx, gy = cx + 9.2 * ca, cy - 9.2 * sa
    gx, gy = int(round(gx)) - 0, int(round(gy)) - 0
    gem = [(0, -2, 3), (-1, -1, 3), (0, -1, 4), (1, -1, 2), (-2, 0, 2), (-1, 0, 4), (0, 0, 5), (1, 0, 3), (2, 0, 1),
           (-1, 1, 2), (0, 1, 3), (1, 1, 1), (0, 2, 1)]
    for (dx, dy, k) in gem:
        px(img, gx + dx, gy + dy, RUBY[k])
    for (dx, dy) in ((0, -3), (-3, 0), (3, 0), (0, 3), (-2, -2), (2, -2), (-2, 2), (2, 2)):
        px(img, gx + dx, gy + dy, GOLD[4] if dy <= 0 and dx <= 0 else GOLD[2])     # zlaté lůžko kolem kamene
    outline(img)
    glow(img, (255, 200, 70), 1.8, 3.2, 95, 42)
    sparkle(img, 33, 4, (255, 246, 200), 2)
    sparkle(img, 8, 9, (255, 236, 170), 1)
    sparkle(img, 36, 24, (255, 236, 170), 1)
    return img


def sheet():
    return np.concatenate([heart(), anvil(), hammer()], axis=1)


if __name__ == "__main__":
    import sys
    s = np.clip(sheet(), 0, 255).astype(np.uint8)
    im = Image.fromarray(s, "RGBA")
    bg = Image.new("RGBA", im.size, (36, 16, 8, 255)); bg.alpha_composite(im)
    bg.resize((im.width * 8, im.height * 8), Image.NEAREST).save(sys.argv[1])

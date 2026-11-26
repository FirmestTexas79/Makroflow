"""
Použití:  python tools/cutscene/ending_bloom.py [--preview] [--from S] [--to S]
Výstup:   app/src/main/res/raw/ending_kveteni.mp4 (720 × 1560, H.264 + zvuk z build/ending_kveteni.m4a)

Cutscéna KONEC II · DEN KVETENÍ (docs/adr/0112). Kopí je venku, rána se otevřela a poslední světlo Lumiry vyteče –
nahoru, proti všemu, co platilo. Záběry (s):
  0–9    Střed kořenů: světlo naroste, klidná voda zezlátne a stoupá, na kamenech vyraší květy, záblesk.
  9–20   Kmen: z prázdné rány teče zlaté světlo po kůře a přes lávku, kde se dotkne, vyraší listy; kopí leží na lávce.
  20–28  Les pod korunou: vlna zeleně odspodu nahoru, na visících větvích listí, mlha se zvedne.
  28–36  Svah bezejmenných: bílé květy, jeden po druhém, u každého, kdo nedošel.
  36–50  Věž Řádu Váhy: voda z propasti stoupá po kolena, po pás; spisy plavou jako lekníny; Sept ve dveřích s deštníkem.
  50–62  Ozvěna: řeka proteče ulicemi od brány k náměstí; Brann vyjde před pekárnu; ráno je teplejší než včera.
  62–72  Lávka zblízka: kopí zarůstá břečťanem.
  72–81  Signální útes: obrácený strom Spící rozkvétá, zelená záře zezlátne, okvětní lístky stoupají.
  81–90  Práh: socha Lumiry na trůnu, koruna z větví rozkvete. „Rozkvetla.“ Bílé světlo.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import cine as C  # noqa: E402
from cine import W, H, Caption  # noqa: E402

B = os.path.join(HERE, "build")
rng = np.random.default_rng(426)
GOLD = np.array([255, 214, 120], np.float32)
HEART2 = C.res("root_heart").repeat(2, 0).repeat(2, 1)
TRUNK = C.res("root_trunk")
FOREST = C.res("root_forest")
SLOPE = C.res("root_nameless")
OZV = C.res("ozvena")
SIGNAL = C.res("root_signal")
THRESH = C.res("root_threshold")
IGNAR = C.shrink(C.res("makromon_01_ignar"), 26)
SEPT = C.shrink(C.res("npc_sept"), 19)


def lum(img):
    return img[:, :, 0] * 0.3 + img[:, :, 1] * 0.59 + img[:, :, 2] * 0.11


def blossom(img, x, y, f, col=(255, 236, 244), core=(255, 214, 120)):
    """Květ v px snímku: f 0 → 1 = pupen (1 px) → rozvitý (kříž 5 px se středem)."""
    if f <= 0:
        return
    C.dots(img, [x], [y], core if f > 0.5 else col, np.array([min(1.0, f * 2)]))
    if f > 0.45:
        a = np.full(4, min(1.0, (f - 0.45) * 3))
        C.dots(img, [x - 1, x + 1, x, x], [y, y, y - 1, y + 1], col, a)


def flower_field(img, pts, t0s, t, ox=0, oy=0, zoom=1, col=(255, 248, 240)):
    for (x, y), t0 in zip(pts, t0s):
        f = (t - t0) / 0.6
        if f > 0:
            blossom(img, int((x - ox) * zoom), int((y - oy) * zoom), min(1.0, f), col)


def box(img, r):
    """Rozmazání průměrem (r px) – pro hledání tmavých větví proti záři."""
    k = 2 * r + 1
    p = np.pad(img, ((r, r), (r, r)), mode="edge")
    c = p.cumsum(0).cumsum(1)
    c = np.pad(c, ((1, 0), (1, 0)))
    return (c[k:, k:] - c[:-k, k:] - c[k:, :-k] + c[:-k, :-k]) / (k * k)


# ── 1) Střed kořenů: zlatá voda stoupá ─────────────────────────────────────
STONES = [(88, 124), (66, 146), (44, 170), (64, 196), (90, 216), (118, 204), (134, 212), (108, 236), (90, 256), (62, 274),
          (38, 298), (112, 344), (124, 360), (116, 382), (88, 318)]
S1_FLOWERS = [(x * 2 + int(rng.integers(-10, 11)), y * 2 + int(rng.integers(-5, 6))) for (x, y) in STONES for _ in range(3)]
S1_T = rng.uniform(1.5, 6.0, len(S1_FLOWERS))


def s1_heart(t, _):
    cy = 506
    img = C.view(HEART2, 176, cy)
    oy = cy - H / 2
    pulse = 0.6 + 0.4 * math.sin(t * 3)
    yy, xx = np.mgrid[0:H, 0:W]
    g = np.clip(1 - np.hypot((xx - 176) / (60 + t * 30), (yy - (620 - oy)) / (50 + t * 30)), 0, 1) ** 1.6
    img = img + GOLD * g[:, :, None] * (0.25 + 0.35 * pulse) * min(1.0, t / 3)
    level = H + 40 - (H + 80) * C.ease((t - 1.0) / 7.5)                   # hladina zlaté vody v px snímku
    under = (yy > level + np.sin(xx * 0.15 + t * 3) * 2)
    shimmer = 0.5 + 0.5 * np.sin(xx * 0.3 + yy * 0.1 - t * 6)
    img = np.where(under[:, :, None], img * 0.5 + GOLD * (0.35 + 0.15 * shimmer[:, :, None]), img)
    surf = np.abs(yy - level - np.sin(xx * 0.15 + t * 3) * 2) < 1.2
    img[surf] = img[surf] * 0.3 + np.array([255, 246, 220]) * 0.7
    flower_field(img, S1_FLOWERS, S1_T, t, 0, oy)
    k = np.arange(70)
    my = (H - ((k * 37) % H + t * (60 + k % 9 * 12)) % (H + 40))
    mx = (k * 53) % W + np.sin(t + k) * 4
    C.dots(img, mx, my, (255, 240, 200), np.full(70, 0.7))
    img = C.fade(img, 1 - t / 1.2)
    if t > 7.8:
        img = C.fade(img, (t - 7.8) / 1.2, (255, 246, 214))
    return img


# ── 2) Kmen: světlo teče z rány, listy rašení ──────────────────────────────
def rivulets(n, starts, seed, length=520):
    r = np.random.default_rng(seed)
    paths = []
    for i in range(n):
        x, y = starts[i % len(starts)]
        x += r.uniform(-30, 30)
        p = []
        dx = r.uniform(-0.4, 0.4)
        for s in range(length):
            dx = np.clip(dx + r.normal(0, 0.12), -0.8, 0.8)
            x += dx; y += 1
            p.append((int(x), int(y)))
        paths.append(p)
    return paths


RIV = rivulets(26, [(140, 440), (216, 470), (290, 440), (120, 330), (310, 330)], 7)
LEAVES2 = [(p[int(k)], 0.25 + k / 520 * 7.0 + float(rng.uniform(0, 0.8))) for p in RIV for k in rng.uniform(30, 500, 6)]


def lying_spear(img, ox, oy, zoom=1, ivy=0.0, t=0.0):
    """Kopí ležící na lávce (v souřadnicích mapy Kmene): ratiště s okovy, železná pata, listová čepel; volitelně břečťan."""
    x0, y0, x1, y1 = 96, 606, 330, 588
    n = 300
    xs = np.linspace(x0, x1, n); ys = np.linspace(y0, y1, n)
    for k in range(n):
        c = (150, 104, 60) if (k // 9) % 4 else (120, 124, 130)
        for w in (-2, -1, 0, 1, 2):
            cc = (200, 150, 96) if w == -2 else (c if w <= 0 else (90, 62, 36))
            C.dots(img, [(xs[k] - ox) * zoom], [(ys[k] + w - oy) * zoom], cc, np.array([1.0]))
    for k in range(18):                                                   # listová čepel
        hw = 3.5 * math.sin(math.pi * k / 18)
        for w in np.arange(-hw, hw + 0.5):
            C.dots(img, [(x1 + k - ox) * zoom], [(y1 - k * 0.08 + w - oy) * zoom], (196, 202, 210) if w < 0 else (130, 136, 146), np.array([1.0]))
    for k in range(4):
        C.dots(img, [(x0 - k - ox) * zoom], [(y0 + k * 0.08 - oy) * zoom], (110, 112, 118), np.array([1.0]))
    if ivy > 0:                                                           # břečťan se vine od paty k čepeli
        m = int(n * min(1.0, ivy))
        for k in range(0, m):
            yy = ys[k] + math.sin(k * 0.35) * 2.5
            C.dots(img, [(xs[k] - ox) * zoom], [(yy - oy) * zoom], (60, 120, 50), np.array([1.0]))
            if k % 7 == 0:
                ly = yy + (3 if (k // 7) % 2 else -3)
                for (dx, dy, c) in ((0, 0, (90, 170, 70)), (1, 0, (70, 140, 60)), (0, 1 if ly > yy else -1, (120, 196, 90))):
                    C.dots(img, [(xs[k] + dx - ox) * zoom], [(ly + dy - oy) * zoom], c, np.array([1.0]))


def s2_trunk(t, _):
    cy = 390 + 120 * C.ease((t - 1) / 8.0)
    ox, oy = 216 - W / 2, cy - H / 2
    base = TRUNK.copy()
    lying_spear(base, 0, 0)
    img = C.view(base, 216, cy)
    yy, xx = np.mgrid[0:H, 0:W]
    g = np.clip(1 - np.hypot((xx + ox - 216) / 130, (yy + oy - 250) / 230), 0, 1) ** 1.4 * (0.7 + 0.3 * math.sin(t * 2.5))
    img = img + GOLD * g[:, :, None] * 0.55
    L = int(max(0.0, t - 0.3) * 75)
    for p in RIV:
        seg = p[:L]
        if seg:
            a = np.array(seg)
            C.dots(img, a[:, 0] - ox, a[:, 1] - oy, (255, 226, 150), np.full(len(a), 0.9))
            C.dots(img, a[:, 0] + 1 - ox, a[:, 1] - oy, (255, 200, 110), np.full(len(a), 0.35))
            hx, hy = seg[-1]
            C.dots(img, [hx - ox], [hy - oy], (255, 255, 230), np.array([1.0]))
    for ((x, y), t0) in LEAVES2:
        f = (t - t0) / 0.5
        if f > 0:
            c = (110, 190, 80) if f > 0.6 else (180, 230, 120)
            C.dots(img, [x - ox, x + 1 - ox], [y - oy, y - 1 - oy], c, np.array([1.0, min(1.0, f)]))
    img = C.fade(img, 1 - t / 0.8, (255, 246, 214)) if t < 0.8 else img
    if t > 10.4:
        img = C.fade(img, (t - 10.4) / 0.6)
    return img


# ── 3) Les pod korunou: zelená vlna, listí, mlha se zvedne ─────────────────
_FL = lum(FOREST)
_bark = np.argwhere((_FL > 70) & (_FL < 160))
LEAVES3 = _bark[rng.choice(len(_bark), 900, replace=False)]


def greenify(img, k):
    g = img * np.array([0.82, 1.18, 0.78]) + np.array([6, 22, 4])
    g = g * 1.12
    return img * (1 - k) + g * k


def s3_forest(t, _):
    cx, cy = 352, 730 - 300 * C.ease(t / 8)
    ox, oy = cx - W / 2, cy - H / 2
    img = C.view(FOREST, cx, cy)
    yy = np.arange(H)[:, None] + oy
    front = 1120 - t * 170                                                # vlna zeleně postupuje nahoru
    k = np.clip((yy - front) / 120 + 1, 0, 1)[:, :, None] * 0.9
    img = greenify(img, k)
    img = img + np.array([30, 30, 18]) * (k * 0.6)                        # mlha řídne, rozjasní se
    for (y, x) in LEAVES3:
        if y > front + 20 and oy <= y < oy + H and ox <= x < ox + W:
            f = min(1.0, (y - front - 20) / 60)
            c = (120, 206, 90) if (x + y) % 3 else (180, 230, 120)
            C.dots(img, [x - ox], [y - oy], c, np.array([f]))
    if t < 0.6:
        img = C.fade(img, 1 - t / 0.6)
    if t > 7.5:
        img = C.fade(img, (t - 7.5) / 0.5)
    return img


# ── 4) Svah bezejmenných: bílé květy ───────────────────────────────────────
def walk_cells(name):
    lines = open(os.path.join(C.ASSETS, "walk", name + ".txt")).read().splitlines()[2:]
    return [(gx * 2 + 1, gy * 2 + 1) for gy, row in enumerate(lines) for gx, ch in enumerate(row) if ch == "."]


_cells = walk_cells("root_nameless")
SLOPE_FL = [_cells[i] for i in rng.choice(len(_cells), 260, replace=False)]
SLOPE_T = [0.5 + (560 - y) / 560 * 0.3 + float(rng.uniform(0, 5.5)) for (x, y) in SLOPE_FL]


def s4_slope(t, _):
    # 2× detail svahu: dva výřezy po sobě (horní a dolní polovina), květy kvetou postupně
    half = 0 if t < 4.0 else 1
    cy = 200 if half == 0 else 380
    base = SLOPE.copy()
    flower_field(base, SLOPE_FL, SLOPE_T, t)
    img = C.view(base, 88, cy, zoom=2)
    warm = min(1.0, t / 8)
    img = img * (1 + 0.15 * warm) + np.array([12, 10, 4]) * warm
    for tc in (4.0,):
        if tc - 0.2 < t < tc + 0.2:
            img = C.fade(img, 1 - abs(t - tc) / 0.2)
    if t < 0.5:
        img = C.fade(img, 1 - t / 0.5)
    if t > 7.5:
        img = C.fade(img, (t - 7.5) / 0.5)
    return img


# ── 5) Věž: voda z propasti stoupá, spisy, Sept s deštníkem ────────────────
PAPERS = [(float(rng.uniform(110, 280)), float(rng.uniform(0, 6.28))) for _ in range(16)]


def umbrella(open_f):
    s = np.zeros((10, 17, 4), np.float32)
    if open_f <= 0:
        return s
    hw = int(1 + 7 * min(1.0, open_f))
    for y in range(5):
        w = int(hw * math.sqrt(1 - ((4 - y) / 5) ** 2))
        s[y, 8 - w:9 + w, :3] = (52, 40, 70) if y < 4 else (24, 18, 34); s[y, 8 - w:9 + w, 3] = 255
        if y < 4:
            s[y, 8 - w, :3] = (120, 100, 150)
    s[0, 8, :3] = (180, 180, 190)
    s[5:10, 8, :3] = (60, 50, 40); s[5:10, 8, 3] = 255
    s[9, 7, :3] = (60, 50, 40); s[9, 7, 3] = 255
    return s


def s5_tower(t, _):
    cx, cy = 192, 520
    base = OZV.copy()
    if t > 2.6:                                                           # Sept ve dveřích, deštník se otevře
        C.blit(base, SEPT, 192 - SEPT.shape[1] // 2, 548 - SEPT.shape[0])
        C.blit(base, umbrella((t - 3.4) / 0.5), 192 - 8, 548 - SEPT.shape[0] - 6)
    Z = 3
    img = C.view(base, cx, cy, zoom=Z)
    ox, oy = cx - W / Z / 2, cy - H / Z / 2
    level = 640 - 84 * C.ease(t / 3.0) - 16 * C.ease((t - 5.5) / 4.5)     # map y: k patě věže, pak po pás
    yy, xx = np.mgrid[0:H, 0:W]
    my = (yy // Z) + oy; mx = (xx // Z) + ox
    wave = np.sin(mx * 0.25 + t * 2.4) * 0.8
    under = my > level + wave
    depth = np.clip((my - level) / 60, 0, 1)
    sh = 0.5 + 0.5 * np.sin(mx * 0.5 + my * 0.3 - t * 3)
    water = np.array([70, 150, 160]) * (1 - depth[:, :, None] * 0.5) + GOLD * (0.12 + 0.12 * sh[:, :, None])
    img = np.where(under[:, :, None], img * 0.35 + water * 0.65, img)
    surf = np.abs(my - level - wave) < 0.6
    img[surf] = np.array([230, 246, 236])
    for (px, ph) in PAPERS:                                               # spisy plavou jako lekníny
        pxx = px + math.sin(t * 0.5 + ph) * 6
        sx, sy = int((pxx - ox) * Z), int((level - oy) * Z) + int(round(math.sin(t * 2 + ph))) * Z
        if 0 <= sx < W - 16 and 6 <= sy < H - 8:
            img[sy - 3:sy + 3, sx:sx + 15] = (236, 230, 210)
            img[sy - 3:sy - 2, sx:sx + 15] = (250, 248, 240)
            img[sy - 1:sy, sx + 3:sx + 12] = (120, 120, 130); img[sy + 1:sy + 2, sx + 3:sx + 10] = (120, 120, 130)
    if t < 0.5:
        img = C.fade(img, 1 - t / 0.5)
    if t > 13.4:
        img = C.fade(img, (t - 13.4) / 0.6)
    return img


# ── 6) Ozvěna: řeka ulicemi, Brann před pekárnou ───────────────────────────
RIVER = [(192, 60), (192, 108), (186, 182), (160, 250), (124, 336), (170, 360), (192, 368), (192, 410), (192, 470), (150, 560), (130, 640)]


def river_pts():
    pts = []
    for (a, b) in zip(RIVER, RIVER[1:]):
        n = int(math.hypot(b[0] - a[0], b[1] - a[1]))
        for k in range(n):
            pts.append((a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n))
    return np.array(pts)


RP = river_pts()


def s6_town(t, _):
    head = min(len(RP) - 1, int(t / 7.0 * len(RP)))
    hx, hy = RP[head]
    cx = 200 if t > 7.5 else hx
    cy = max(195, min(656 - 195, 262 if t > 7.5 else hy))
    base = OZV.copy()
    warm = C.ease((t - 7.0) / 4.0)
    base[:, :, :3] = base[:, :, :3] * (1 + 0.1 * warm) + np.array([18, 10, -6]) * warm
    if t > 7.5:                                                           # Brann vyjde před pekárnu a rozhlíží se
        by = 258 + 8 * C.ease((t - 7.8) / 1.2)
        spr = IGNAR if int(t * 1.5) % 2 else IGNAR[:, ::-1]
        C.blit(base, spr, 282 - IGNAR.shape[1] // 2, int(by) - IGNAR.shape[0])
    seg = RP[:head + 1]
    if len(seg) > 2:
        d = np.gradient(seg, axis=0)
        nrm = np.stack([-d[:, 1], d[:, 0]], 1); nrm /= np.maximum(1e-6, np.linalg.norm(nrm, axis=1))[:, None]
        k = np.arange(len(seg))
        for w in np.arange(-7, 7.5, 0.5):                                # pás vody kolmo na směr toku
            p = seg + nrm * w
            hi = ((k - t * 40 + abs(w) * 4) % 23) < 2
            edge = abs(w) > 6
            col = np.where(hi[:, None], np.array([226, 248, 250]), np.array([72, 150, 196]) * (1.15 - abs(w) / 14))
            col = np.where(np.full((len(k), 1), edge), np.array([200, 230, 236]), col)
            C.dots(base, p[:, 0], p[:, 1], col, np.full(len(p), 0.9))
    img = C.view(base, cx, cy, zoom=2)
    if t < 0.5:
        img = C.fade(img, 1 - t / 0.5)
    if t > 11.4:
        img = C.fade(img, (t - 11.4) / 0.6)
    return img


# ── 7) Lávka zblízka: kopí zarůstá břečťanem ───────────────────────────────
def s7_spear(t, _):
    cx, cy = 200 + 30 * C.ease(t / 9), 598
    base = TRUNK.copy()
    lying_spear(base, 0, 0, ivy=C.ease((t - 0.8) / 7.0), t=t)
    img = C.view(base, cx, cy, zoom=3) + GOLD * 0.08
    k = np.arange(50)
    C.dots(img, (k * 71) % W, (H - (k * 41 + t * (20 + k % 5 * 8) * 2) % H), (255, 236, 190), np.full(50, 0.6))
    if t < 0.5:
        img = C.fade(img, 1 - t / 0.5)
    if t > 9.4:
        img = C.fade(img, (t - 9.4) / 0.6)
    return img


# ── 8) Signální útes: Spící rozkvétá ───────────────────────────────────────
_SL = lum(SIGNAL)
_tree = np.argwhere((_SL < box(_SL, 3) - 10) & (np.arange(SIGNAL.shape[1])[None, :] < 112) & (np.arange(SIGNAL.shape[0])[:, None] > 10) &
                    (np.arange(SIGNAL.shape[0])[:, None] < 470) & (SIGNAL[:, :, 1] > SIGNAL[:, :, 0] + 8))
SIG_FL = _tree[rng.choice(len(_tree), min(600, len(_tree)), replace=False)]
SIG_T = 0.6 + np.hypot(SIG_FL[:, 1] - 55, SIG_FL[:, 0] - 30) / 420 * 6.5 + rng.uniform(0, 0.8, len(SIG_FL))


def s8_signal(t, _):
    cx, cy = 88, 250
    base = SIGNAL.copy()
    for (y, x), t0 in zip(SIG_FL, SIG_T):
        f = (t - t0) / 0.7
        if f > 0:
            blossom(base, int(x), int(y), min(1.0, f), (255, 168, 204) if (x + y) % 3 else (255, 236, 244))
    img = C.view(base, cx, cy, zoom=2)
    gold = C.ease(t / 7)
    gmask = (img[:, :, 1] > img[:, :, 0] + 20)[:, :, None]                # zelená záře zezlátne
    img = np.where(gmask, img * (1 - 0.6 * gold) + (img[:, :, 1:2] * np.array([1.2, 1.0, 0.55])) * 0.6 * gold, img)
    k = np.arange(60)                                                     # okvětní lístky stoupají
    C.dots(img, (k * 61 + np.sin(t + k) * 20) % W, H - ((k * 29 + t * (30 + k % 7 * 10)) % H), (255, 220, 236),
           np.full(60, min(1.0, t / 3)))
    if t < 0.5:
        img = C.fade(img, 1 - t / 0.5)
    if t > 8.6:
        img = C.fade(img, (t - 8.6) / 0.4)
    return img


# ── 9) Práh: Lumira na trůnu, koruna rozkvete ──────────────────────────────
_TL = lum(THRESH)
_yy, _xx = np.mgrid[0:THRESH.shape[0], 0:THRESH.shape[1]]
_throne = (_xx > 44) & (_xx < 132) & (_yy > 112)                           # socha a trůn nekvetou – jen větve kolem
_crown = np.argwhere((_TL > 120) & (_yy < 232) & (_yy > 6) & (THRESH[:, :, :3].max(2) - THRESH[:, :, :3].min(2) < 60) & ~_throne)
TH_FL = _crown[rng.choice(len(_crown), min(700, len(_crown)), replace=False)]
TH_T = 0.4 + np.hypot(TH_FL[:, 1] - 88, TH_FL[:, 0] - 140) / 160 * 4.5 + rng.uniform(0, 0.6, len(TH_FL))


def s9_lumira(t, _):
    cx, cy = 88, 175
    base = THRESH.copy()
    for (y, x), t0 in zip(TH_FL, TH_T):
        f = (t - t0) / 0.7
        if f > 0:
            blossom(base, int(x), int(y), min(1.0, f), (255, 168, 204) if (x * 7 + y) % 3 else (255, 236, 244))
    img = C.view(base, cx, cy, zoom=2)
    img = img * (1 + 0.25 * C.ease(t / 5)) + np.array([24, 18, 4]) * C.ease(t / 5)
    k = np.arange(80)
    C.dots(img, (k * 47 + np.sin(t * 0.7 + k) * 24) % W, H - ((k * 37 + t * (24 + k % 6 * 9)) % H), (255, 226, 240),
           np.full(80, min(1.0, t / 2)))
    if t < 0.4:
        img = C.fade(img, 1 - t / 0.4)
    if t > 6.5:
        img = C.fade(img, (t - 6.5) / 2.5, (255, 250, 240))
    return img


SHOTS = [(0, 9, s1_heart), (9, 20, s2_trunk), (20, 28, s3_forest), (28, 36, s4_slope), (36, 50, s5_tower), (50, 62, s6_town),
         (62, 72, s7_spear), (72, 81, s8_signal), (81, 90, s9_lumira)]
CAPS = [
    Caption(1.5, 7.6, "Světlo stoupá. Klidná voda zezlátne a začne téct – nahoru, proti všemu, co platilo."),
    Caption(10.0, 19.4, "Teče po kořenech, po kmeni, přes lávku a přes Práh. Kde se dotkne kůry, vyraší list."),
    Caption(20.6, 27.6, "Les pod korunou se zazelená a mlha se rozplyne."),
    Caption(28.6, 35.6, "Na Svahu bezejmenných vykvetou bílé květy – jeden u každého, kdo nedošel."),
    Caption(36.6, 42.6, "Pak dorazí nahoru. Věž Řádu Váhy stojí ve vodě – po kolena, po pás."),
    Caption(43.0, 49.6, "Spisy plavou po hladině jako lekníny. Sept stojí ve dveřích s deštníkem a poprvé neví, co říct."),
    Caption(50.6, 56.0, "Ozvěnou proteče řeka. Popel z ulic odplave."),
    Caption(56.4, 61.6, "Brann vyjde před pekárnu a poprvé si všimne, že dnešní ráno je jiné než včerejší."),
    Caption(63.0, 71.4, "Kopí zůstane dole na lávce, zarostlé břečťanem."),
    Caption(73.0, 80.6, "Lumira už pod světem nevisí hlavou dolů."),
    Caption(82.5, 88.6, "Rozkvetla.", cps=6, pos=0.62),
]

if __name__ == "__main__":
    a = sys.argv[1:]
    s0 = float(a[a.index("--from") + 1]) if "--from" in a else 0.0
    s1 = float(a[a.index("--to") + 1]) if "--to" in a else None
    audio = os.path.join(B, "ending_kveteni.m4a")
    out = os.path.join(B, "ending_kveteni_preview.mp4") if "--preview" in a else os.path.join(C.ROOT, "app", "src", "main", "res", "raw", "ending_kveteni.mp4")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    C.render(SHOTS, CAPS, out, audio=audio if os.path.exists(audio) and "--preview" not in a else None, start=s0, end=s1)
    print("ok", out)

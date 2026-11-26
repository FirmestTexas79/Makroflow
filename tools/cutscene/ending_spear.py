"""
Použití:  python tools/cutscene/ending_spear.py [--preview] [--from S] [--to S]
Výstup:   app/src/main/res/raw/ending_kopi.mp4 (1080 × 2340, H.264 + zvuk, když je build/ending_kopi.m4a),
          tools/cutscene/build/ending_kopi_preview.gif

Cutscéna KONEC I · KOPÍ (docs/adr/0111). Záběry (s):
  0–7    Výstup: kamera stoupá Středem kořenů od posledního světla k puklině, zlatá světélka letí s ní, záblesk.
  7–15   Kmen: od lávky nahoru k Trpitelovi, který dýchá (pás snímků z mapy); hráč jde po lávce ke kopí.
  15–23  Detail: šest očí se usměje (render gen_trpitel s póze smile 0 → 1).
  23–37  Vezmeš ratiště; Kazimír pouští jednu ruku po druhé (release 0 → 3), u každé vzlétne popel.
  37–47  Rozpad: kůže praská, světlo v prasklinách zesílí a tělo se rozpadne na popel, který STOUPÁ; zůstane kopí.
  47–53  Prázdná rána, kopí a malý hráč u ratiště.
  53–63  Sonda Řádu zblízka: „Nástupnictví potvrzeno…“ – „Jak že se jmenujete?“ (kontrolka bliká s hlasem).
  63–75  Nahoře: Brann podá chleba cizímu, Gudwin vítá nového poutníka, tabule ve Věži překlopí 213 → 214.
  75–87  Lávka: Spirra se stočí hráči u nohy a usne; kamera odjíždí; tma.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "mapgen"))
import cine as C  # noqa: E402
from cine import W, H, Caption  # noqa: E402

B = os.path.join(HERE, "build")
rng = np.random.default_rng(213)

# ── podklady ────────────────────────────────────────────────────────────────
HEART = C.res("root_heart")
HEART2 = HEART.repeat(2, 0).repeat(2, 1)
TRUNK = C.res("root_trunk")
SHEET = C.res("trpitel_sheet")
FW = SHEET.shape[1] // 12
SHEET_FR = [SHEET[:, i * FW:(i + 1) * FW] for i in range(12)]
SHEET_MS = [110 if i in (7, 8, 9) else 280 for i in range(12)]
REL_V = [0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0]


def _pose(name, fallback):
    p = os.path.join(B, name)
    return C.load(p) if os.path.exists(p) else fallback   # během renderu póz (náhled) náhradní snímek


REL = [_pose(f"trpitel_rel_{v:.1f}.png", SHEET_FR[0]) for v in REL_V]
_mp = os.path.join(B, "trpitel_rel_3.0_mat.npy")
MAT = np.load(_mp) if os.path.exists(_mp) else np.full(REL[-1].shape[:2], -1, np.int8)
FACE = [_pose(f"trpitel_face_{v:.1f}.png", np.zeros((300, 360, 4), np.float32)) for v in (0.0, 0.5, 0.7)]
LOBBY = C.res("tower_lobby")
TOWN = C.res("poketown")
OZV = C.res("ozvena")
IGNAR = C.shrink(C.res("makromon_01_ignar"), 26)
GUDWIN = C.shrink(C.res("gudwin_oliver"), 34)


def spirra_sleeping():
    """Spirra stočená ve spánku (22 × 14, ručně stavěná z tvarů – zmenšená ilustrace by se slila): velký ocas
    do spirály, tělo, hlava zastrčená k ocasu, růžové ucho, zavřené oko. Barvy z její ilustrace."""
    w, h = 22, 14
    m = np.zeros((h, w), int)                                             # 0 nic, 1 krém, 2 světlá, 3 stín, 4 růžová, 5 oko
    for y in range(h):
        for x in range(w):
            if (x - 7) ** 2 + (y - 7) ** 2 <= 36:
                m[y, x] = 1                                               # ocas
            if ((x - 13) / 5.5) ** 2 + ((y - 9.5) / 3.6) ** 2 <= 1:
                m[y, x] = 1                                               # tělo
            if (x - 17.5) ** 2 + (y - 8.5) ** 2 <= 9.5:
                m[y, x] = 2                                               # hlava
    for k in range(60):                                                   # spirála v ocase
        a = k * 0.32
        r = 0.4 + a * 0.55
        x, y = int(round(7 + math.cos(a) * r)), int(round(7 + math.sin(a) * r))
        if 0 <= x < w and 0 <= y < h and m[y, x] and r < 5.6:
            m[y, x] = 3
    for y in range(h):
        for x in range(w):
            if m[y, x] == 1 and y >= 11:
                m[y, x] = 3                                               # stín dole
    for (x, y) in ((18, 5), (19, 5), (19, 4), (17, 5)):
        m[y, x] = 4                                                       # ucho
    m[9, 18] = 5; m[9, 19] = 5                                            # zavřené oko
    pal = {1: (246, 226, 190), 2: (255, 242, 216), 3: (214, 182, 138), 4: (232, 156, 148), 5: (86, 56, 44)}
    out = np.zeros((h, w, 4), np.float32)
    for y in range(h):
        for x in range(w):
            if m[y, x]:
                out[y, x, :3] = pal[m[y, x]]; out[y, x, 3] = 255
            elif any(0 <= y + dy < h and 0 <= x + dx < w and m[y + dy, x + dx] for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0))):
                out[y, x, :3] = (96, 64, 44); out[y, x, 3] = 255             # obrys
    return out


SPIRRA = spirra_sleeping()
TX, TY = 0, 16                                                            # Trpitel v mapě Kmene (levý horní roh)
BUTT = (120, 475)                                                         # pata kopí v mapě (gen_trpitel.proj)
DECK_Y = 520


def walk_n(i):
    return C.hero("walk_n", i)


def with_sprite(base, spr, x, y, alpha=1.0):
    img = base.copy()
    a = spr[:, :, 3:4] / 255.0 * alpha
    h, w = spr.shape[:2]
    img[y:y + h, x:x + w, :3] = img[y:y + h, x:x + w, :3] * (1 - a) + spr[:, :, :3] * a
    return img


def sheet_at(t):
    """Snímek dechu Trpitele v čase t (stejné délky snímků jako v mapě)."""
    ms = int(t * 1000) % sum(SHEET_MS)
    for i, d in enumerate(SHEET_MS):
        if ms < d:
            return SHEET_FR[i]
        ms -= d
    return SHEET_FR[0]


# částice, které se v čase opakují (deterministicky)
ASH = rng.uniform(0, 1, (160, 4))
MOTES = rng.uniform(0, 1, (90, 4))


def falling_ash(img, t, ox, oy, k=1.0):
    """Popel padá z Trpitele (v souřadnicích mapy Kmene; ox/oy = levý horní roh záběru)."""
    x = 130 + ASH[:, 0] * 200 + np.sin(t * 0.7 + ASH[:, 1] * 9) * 6
    y = 80 + (ASH[:, 2] * 700 + t * (10 + ASH[:, 3] * 16)) % 700
    a = (0.35 + 0.4 * ASH[:, 3]) * k
    C.dots(img, x - ox, y - oy, (176, 170, 160), np.full(len(x), 1.0) * a)


# ── záběry ──────────────────────────────────────────────────────────────────

def s1_ascent(t, _):
    """Střed kořenů: kamera stoupá od posledního světla k puklině, světélka letí nahoru, nakonec záblesk."""
    cy = 506 - 116 * C.ease((t - 0.6) / 5.2)
    img = C.view(HEART2, 176, cy)
    pulse = 0.5 + 0.5 * math.sin(t * 2.2)
    lx, ly = 176, 620 - (cy - H / 2)
    yy, xx = np.mgrid[0:H, 0:W]
    g = np.clip(1 - np.hypot((xx - lx) / 80, (yy - ly) / 60), 0, 1) ** 2 * (0.25 + 0.2 * pulse)
    img = img + np.array([255, 220, 150]) * g[:, :, None] * 0.6
    my = (MOTES[:, 0] * 900 - t * (40 + MOTES[:, 1] * 50)) % 900 - (cy - H / 2) - 60
    mx = 40 + MOTES[:, 2] * 280 + np.sin(t + MOTES[:, 3] * 7) * 5
    C.dots(img, mx, my, (255, 238, 196), 0.5 + 0.5 * MOTES[:, 3])
    img = C.fade(img, 1 - t / 1.4)
    if t > 5.6:
        img = C.fade(img, (t - 5.6) / 1.2, (255, 248, 230))
    return img


def s2_trunk(t, _):
    """Kmen: od lávky nahoru k Trpitelovi; hráč jde po lávce ke kopí."""
    base = TRUNK.copy()
    base = with_sprite(base, sheet_at(t), TX, TY)
    hx = 214 - 90 * C.ease((t - 1) / 6.0); hy = 640 - 120 * C.ease((t - 1) / 6.0)
    fr = walk_n(int(t * 10)) if 1 < t < 7 else C.hero("idle_n", int(t * 5))
    base = with_sprite(base, fr, int(hx) - 32, int(hy) - 38)
    cy = 600 - 230 * C.ease((t - 1.5) / 6.0)
    img = C.view(base, 216, cy)
    falling_ash(img, t, 216 - W / 2, cy - H / 2)
    img = C.fade(img, 1 - t / 1.0, (255, 248, 230)) if t < 1.0 else img
    return img


def s3_face(t, _):
    """Detail hlavy: šest očí se usměje."""
    bg = C.view(TRUNK, 212, 120, zoom=3) * 0.45
    k = 0 if t < 3.5 else (1 if t < 5.0 else 2)
    f = C.ease((t - 3.5) / 0.8) if t < 5 else C.ease((t - 5.0) / 0.8)
    img = bg.copy()
    dy = int(round(math.sin(t * 1.1) * 2))                                # dech
    if k == 0 or f >= 1:
        C.blit(img, FACE[k], 0, 230 + dy)
    else:
        C.blit(img, FACE[k - 1], 0, 230 + dy)
        C.blit(img, FACE[k], 0, 230 + dy, alpha=f)
    falling_ash(img, t * 0.6, 80, -100, k=0.8)
    if t < 0.6:
        img = C.fade(img, 1 - t / 0.6)
    if t > 7.4:
        img = C.fade(img, (t - 7.4) / 0.6)
    return img


PUFFS = []


def s4_release(t, _):
    """Vezmeš ratiště; Kazimír pouští ruce jednu po druhé, u každé vzlétne obláček popela a v prasklinách blikne světlo."""
    base = TRUNK.copy()
    step = 2.6 + 1.7 * np.arange(len(REL))                                # kdy nastoupí póza i (i ≥ 1)
    i = int(np.sum(t >= step[:-1]))
    i = min(i, len(REL) - 1)
    base = with_sprite(base, REL[0 if i == 0 else i - 1] if i > 0 else REL[0], TX, TY)
    if i > 0:
        f = C.ease((t - step[i - 1]) / 0.25)
        base = with_sprite(base, REL[i], TX, TY, alpha=f)
    # hráč u paty kopí: dojde a chytí
    hx = 160 - 38 * C.ease(t / 2.2); hy = 528
    fr = C.hero("walk_w", int(t * 10)) if t < 2.2 else C.hero("idle_n", int(t * 4))
    base = with_sprite(base, fr, int(hx) - 32, int(hy) - 38)
    img = C.view(base, 216, 380)
    falling_ash(img, t, 216 - W / 2, 380 - H / 2, k=0.6)
    for j in (2, 4, 6):                                                   # popel vzlétne, když pustí ruku
        te = t - step[j - 1]
        if 0 < te < 2.5:
            n = 40
            sx = np.array([90, 70, 100][j // 2 - 1]) + rng.normal(0, 8, n) * 0 + np.linspace(-14, 14, n)
            sy = np.array([150, 230, 300][j // 2 - 1]) - te * (20 + np.arange(n) % 7 * 6)
            C.dots(img, sx - (216 - W / 2), sy - (380 - H / 2), (210, 204, 190), np.full(n, max(0.0, 1 - te / 2.5)))
    if t < 0.5:
        img = C.fade(img, 1 - t / 0.5)
    return img


# rozpad: každý pixel postavy (kromě kopí) má práh – kdy se odlomí; nejdřív končetiny, nakonec hlava a hrudník
_R3 = REL[-1]
_A = (_R3[:, :, 3] > 250) & (MAT >= 0)
_SHADOW = _R3.copy(); _SHADOW[_A] = 0                                   # průsvitný stín postavy na kůře – zmizí postupně
_SPEAR = ((MAT == 1) | ((MAT == 2) & (np.arange(_R3.shape[1])[None, :] < 216))) & _A
_BODY = _A & ~_SPEAR
_ys, _xs = np.nonzero(_BODY)
_dist = np.hypot(_xs - 212, _ys - 91)                                    # od hlavy: nejdřív konce paží a nohy, oči poslední
_noise = rng.uniform(0, 1, len(_xs))
_thr = 1 - _dist / _dist.max()
_thr = np.clip(_thr * 0.8 + _noise * 0.2, 0, 1)
_col = _R3[_ys, _xs, :3]
_vx = rng.normal(0, 4, len(_xs)); _vy = rng.uniform(14, 34, len(_xs))
_FLAKE = rng.uniform(0, 1, len(_xs)) < 0.17
DIS_T = 7.0


def s5_dissolve(t, _):
    """Kůže praská, světlo v prasklinách zesílí a tělo se rozpadne na popel, který stoupá; zůstane kopí."""
    cy = 390
    ox, oy = 216 - W / 2, cy - H / 2
    base = TRUNK.copy()
    spear = np.zeros_like(_R3); spear[_SPEAR] = _R3[_SPEAR]
    base = with_sprite(base, _SHADOW, TX, TY, alpha=max(0.0, 1 - t / 5.0))
    base = with_sprite(base, spear, TX, TY)
    base = with_sprite(base, C.hero("idle_n", int(t * 4)), 122 - 32, 528 - 38)
    img = C.view(base, 216, cy)
    p = t / DIS_T
    age = (p - _thr) * DIS_T                                              # < 0 ještě drží, > 0 letí
    hold = age < 0
    glow = np.clip(1 + age[hold] / 1.2, 0, 1)                             # těsně před odlomením rozžhavené
    colh = _col[hold] * (1 - glow[:, None] * 0.5) + np.array([150, 240, 228]) * glow[:, None] * 0.6
    C.dots(img, _xs[hold] + TX - ox, _ys[hold] + TY - oy, colh, np.ones(hold.sum()))
    spark = ~hold & (age < 0.35) & ~_FLAKE                                 # většina pixelů jen dohoří na místě
    if spark.any():
        k = (1 - age[spark] / 0.35)[:, None]
        C.dots(img, _xs[spark] + TX - ox, _ys[spark] + TY - oy, np.array([255, 236, 190]) * k + _col[spark] * (1 - k),
               (1 - age[spark] / 0.35) * 0.9)
    fly = ~hold & (age < 4.0) & _FLAKE                                    # jen každý šestý odletí jako vločka popela
    a = age[fly]
    x = _xs[fly] + TX - ox + _vx[fly] * a + np.sin(a * 2 + _noise[fly] * 9) * 3
    y = _ys[fly] + TY - oy - _vy[fly] * a - 4 * a * a
    ash = np.clip(a / 1.5, 0, 1)[:, None]
    colf = _col[fly] * (1 - ash) + np.array([214, 206, 190]) * ash
    warm = np.clip(1 - a / 0.6, 0, 1)[:, None]
    colf = colf * (1 - warm) + np.array([255, 236, 190]) * warm
    C.dots(img, x, y, colf, np.clip(1 - a / 4.0, 0, 1))
    if t > 9.4:
        img = C.fade(img, (t - 9.4) / 0.6)
    return img


def s6_hold(t, _):
    """Prázdná rána, kopí, malý hráč u ratiště; pak blíž (2×)."""
    base = TRUNK.copy()
    spear = np.zeros_like(_R3); spear[_SPEAR] = _R3[_SPEAR]
    base = with_sprite(base, spear, TX, TY)
    base = with_sprite(base, C.hero("idle_n", int(t * 4)), 122 - 32, 528 - 38)
    img = C.view(base, 216, 360) if t < 3.0 else C.view(base, 150, 420, zoom=2)
    img = img * (0.85 + 0.15 * math.cos(t * 0.8))
    if t < 0.6:
        img = C.fade(img, 1 - t / 0.6)
    if t > 5.4:
        img = C.fade(img, (t - 5.4) / 0.6)
    return img


def probe_sprite(led, noise_t):
    """Sonda Řádu zblízka (120 × 170): trojnožka, skříň s mřížkou reproduktoru, rudá kontrolka, anténa, štítek."""
    s = np.zeros((170, 120, 4), np.float32)

    def r(x0, y0, x1, y1, col):
        s[y0:y1, x0:x1, :3] = col; s[y0:y1, x0:x1, 3] = 255
    for (x0, x1) in ((30, 60), (90, 60), (60, 60)):                       # nohy trojnožky
        for y in range(110, 170):
            x = int(x0 + (x1 - x0) * (170 - y) / 60) if x0 != 60 else 60
            r(x - 1, y, x + 2, y + 1, (70, 74, 80))
    r(20, 30, 100, 112, (52, 58, 66)); r(22, 32, 98, 110, (82, 90, 100))  # skříň
    r(22, 32, 98, 36, (120, 128, 138))                                    # horní hrana ve světle
    r(30, 44, 90, 92, (24, 26, 30))                                       # mřížka
    for y in range(46, 90, 4):
        for x in range(32, 88, 4):
            r(x, y, x + 2, y + 2, (60, 64, 70))
    r(58, 2, 61, 30, (110, 116, 124)); r(56, 0, 63, 4, (150, 40, 34))     # anténa
    r(78, 96, 90, 106, (40, 10, 10))                                      # kontrolka
    c = (255, 70, 50) if led else (90, 24, 20)
    r(80, 98, 88, 104, c)
    r(30, 96, 70, 106, (210, 200, 170)); r(32, 99, 68, 101, (60, 60, 60)); r(32, 103, 60, 104, (60, 60, 60))   # štítek
    if noise_t is not None:                                               # šum v reproduktoru
        rr = np.random.default_rng(int(noise_t * 20))
        for _ in range(26):
            x, y = rr.integers(32, 88), rr.integers(46, 90)
            r(x, y, x + 2, y + 1, (150, 156, 164))
    return s


SPEECH = [(1.0, 4.6), (5.6, 9.4)]                                         # kdy Sept mluví (kontrolka bliká)


def s7_probe(t, _):
    """Sonda zblízka, hlas Septa z reproduktoru."""
    bg = C.view(TRUNK, 112, 612, zoom=3) * 0.55
    img = bg.copy()
    talking = any(a <= t < b for a, b in SPEECH)
    led = talking and (int(t * 9) % 3 != 0) or (not talking and int(t * 1.2) % 2 == 0)
    pr = probe_sprite(led, t if talking else None)
    big = pr.repeat(2, 0).repeat(2, 1)
    C.blit(img, big, (W - big.shape[1]) // 2, 210)
    if led:                                                               # rudý odlesk na mřížce a okolí
        yy, xx = np.mgrid[0:H, 0:W]
        g = np.clip(1 - np.hypot((xx - 248) / 90, (yy - 412) / 90), 0, 1) ** 2
        img = img + np.array([120, 20, 10]) * g[:, :, None] * 0.5
    falling_ash(img, t * 0.5, 40, 300, k=0.5)
    if t < 0.6:
        img = C.fade(img, 1 - t / 0.6)
    if t > 9.4:
        img = C.fade(img, (t - 9.4) / 0.6)
    return img


def tint(spr, k):
    s = spr.copy(); s[:, :, :3] = np.clip(s[:, :, :3] * np.array(k), 0, 255); return s


BREAD = np.zeros((4, 6, 4), np.float32); BREAD[:, :, :3] = (200, 140, 70); BREAD[:, :, 3] = 255; BREAD[0, :, :3] = (230, 180, 110)


def s8_montage(t, _):
    """Nahoře: Brann podá chleba cizímu, Gudwin vítá nového poutníka, tabule ve Věži překlopí 213 → 214."""
    if t < 4.0:                                                           # Ozvěna, pekárna
        base = OZV.copy()
        stranger = tint(C.hero("idle_w", int(t * 4)), (0.8, 0.95, 1.2))
        base = with_sprite(base, IGNAR, 270 - IGNAR.shape[1] // 2, 266 - IGNAR.shape[0])
        sx = 330 - 26 * C.ease((t - 0.3) / 1.6)
        base = with_sprite(base, C.hero("walk_w", int(t * 10)) if t < 1.9 else C.hero("idle_w", int(t * 4)), int(sx) - 32, 268 - 38)
        if t > 2.2:                                                       # chleba přejde z rukou do rukou
            bx = 278 + 14 * C.ease((t - 2.2) / 0.8)
            base = with_sprite(base, BREAD, int(bx), 254)
        img = C.view(base, 290, 250, zoom=2)
        k = 1 - t / 0.5 if t < 0.5 else ((t - 3.5) / 0.5 if t > 3.5 else 0)
        return C.fade(img, k)
    if t < 8.0:                                                           # Město, Gudwin
        tt = t - 4.0
        base = TOWN.copy()
        gx, gy = 46, 330
        base = with_sprite(base, GUDWIN, gx - GUDWIN.shape[1] // 2, gy - GUDWIN.shape[0])
        hx = 175 - 100 * C.ease(tt / 3.0); hy = 110 + 210 * C.ease(tt / 3.0)
        newcomer = tint(C.hero("walk_s" if tt < 3.0 else "idle_w", int(tt * 10)), (1.15, 0.9, 0.85))
        base = with_sprite(base, newcomer, int(hx) - 32, int(hy) - 38)
        img = C.view(base, 120, 260, zoom=2)
        k = 1 - tt / 0.5 if tt < 0.5 else ((tt - 3.5) / 0.5 if tt > 3.5 else 0)
        return C.fade(img, k)
    tt = t - 8.0                                                          # Věž, tabule 213 → 214
    base = LOBBY.copy()
    img = C.view(base, 26, 168, zoom=4)
    board = np.zeros((48, 88, 3), np.float32) + (14, 10, 12)
    board[0, :] = board[-1, :] = (70, 60, 50); board[:, 0] = board[:, -1] = (70, 60, 50)
    num = "213" if tt < 1.6 else "214"
    flip = 1.6 <= tt < 1.9
    draw_digits(board, num, dim=flip)
    by = H // 2 - 96
    img[by:by + 192, (W - 352) // 2:(W - 352) // 2 + 352] = board.repeat(4, 0).repeat(4, 1)
    k = 1 - tt / 0.5 if tt < 0.5 else ((tt - 3.4) / 0.6 if tt > 3.4 else 0)
    return C.fade(img, k)


SEG = {"2": "abged", "1": "bc", "3": "abgcd", "4": "fgbc"}


def draw_digits(board, s, dim=False):
    """Sedmisegmentové rudé číslice (jako tabule v čekárně), 3 × (22 × 36)."""
    on = np.array([255, 60, 40]) if not dim else np.array([120, 30, 24])
    off = np.array([46, 16, 16])
    for i, ch in enumerate(s):
        x0, y0 = 6 + i * 28, 6
        segs = {"a": (x0 + 3, y0, x0 + 19, y0 + 3), "d": (x0 + 3, y0 + 33, x0 + 19, y0 + 36), "g": (x0 + 3, y0 + 16, x0 + 19, y0 + 19),
                "f": (x0, y0 + 3, x0 + 3, y0 + 16), "b": (x0 + 19, y0 + 3, x0 + 22, y0 + 16),
                "e": (x0, y0 + 19, x0 + 3, y0 + 33), "c": (x0 + 19, y0 + 19, x0 + 22, y0 + 33)}
        for k, (a, b_, c_, d_) in segs.items():
            board[b_:d_, a:c_] = on if k in SEG[ch] else off


def s9_spirra(t, _):
    """Lávka: Spirra se stočí hráči u nohy a usne; kamera odjíždí (3× → 2× → 1×); tma."""
    base = TRUNK.copy()
    spear = np.zeros_like(_R3); spear[_SPEAR] = _R3[_SPEAR]
    base = with_sprite(base, spear, TX, TY)
    base = with_sprite(base, C.hero("idle_n", int(t * 4)), 122 - 32, 528 - 38)
    sx = 150 - 12 * C.ease((t - 0.5) / 2.0)
    base = with_sprite(base, SPIRRA, int(sx), 528 - SPIRRA.shape[0] + 1)
    zoom = 3 if t < 4 else (2 if t < 8 else 1)
    cy = {3: 500, 2: 470, 1: 380}[zoom]
    img = C.view(base, 140 if zoom > 1 else 216, cy, zoom=zoom)
    if t > 2.5:                                                           # „z“ nad spící Spirrou
        zt = (t - 2.5) % 2.0
        zx = (sx + 8 - (140 if zoom > 1 else 216) + W / zoom / 2) * zoom + zt * 6
        zy = (518 - cy + H / zoom / 2) * zoom - zt * 18
        for (dx, dy) in ((0, 0), (1, 0), (2, 0), (1, 1), (0, 2), (1, 2), (2, 2)):
            C.dots(img, [zx + dx * 2], [zy + dy * 2], (230, 230, 240), np.array([max(0.0, 1 - zt / 2)]))
    for tc in (4.0, 8.0):                                                 # střih: krátké zatmění
        if tc - 0.25 < t < tc + 0.25:
            img = C.fade(img, 1 - abs(t - tc) / 0.25)
    if t > 9.5:
        img = C.fade(img, (t - 9.5) / 2.5)
    return img


SHOTS = [(0, 7, s1_ascent), (7, 15, s2_trunk), (15, 23, s3_face), (23, 37, s4_release), (37, 47, s5_dissolve),
         (47, 53, s6_hold), (53, 63, s7_probe), (63, 75, s8_montage), (75, 87, s9_spirra)]
CAPS = [
    Caption(8.0, 14.6, "Vystoupáš puklinou zpátky ke kmeni. Kazimír na tebe čeká."),
    Caption(16.0, 22.6, "Šest očí se usměje – poprvé to není bolest."),
    Caption(24.0, 29.4, "Vezmeš ratiště. Je teplé od jeho rukou."),
    Caption(29.8, 36.6, "Kazimír pouští jednu ruku po druhé – horní, prostřední, dolní."),
    Caption(38.0, 46.4, "S každou je lehčí, až z něj zbude jen popel. Tentokrát nepadá dolů. Stoupá."),
    Caption(48.0, 52.6, "Kopí drží ránu. Ty držíš kopí."),
    Caption(54.0, 57.8, "„Nástupnictví potvrzeno. Děkujeme, Subjekte.“", cps=22, italic=True, color=(255, 190, 180)),
    Caption(58.6, 62.6, "„Subjekte…? Omlouvám se. Jak že se jmenujete?“", cps=22, italic=True, color=(255, 190, 180)),
    Caption(63.4, 66.8, "Nahoře v Ozvěně podá Brann chleba někomu, koho nezná."),
    Caption(67.4, 70.8, "Gudwin ve Městě uvítá nového poutníka."),
    Caption(71.4, 74.8, "V čekárně Věže naskočí na tabuli číslo 214."),
    Caption(76.0, 84.0, "Spirra zůstane s tebou. Stočí se ti k noze a usne. Ona jediná nezapomene."),
]

if __name__ == "__main__":
    a = sys.argv[1:]
    s0 = float(a[a.index("--from") + 1]) if "--from" in a else 0.0
    s1 = float(a[a.index("--to") + 1]) if "--to" in a else None
    audio = os.path.join(B, "ending_kopi.m4a")
    out = os.path.join(B, "ending_kopi_preview.mp4") if "--preview" in a else os.path.join(C.ROOT, "app", "src", "main", "res", "raw", "ending_kopi.mp4")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    C.render(SHOTS, CAPS, out, gif=os.path.join(B, "ending_kopi_preview.gif") if "--preview" in a else None,
             audio=audio if os.path.exists(audio) and "--preview" not in a else None, start=s0, end=s1)
    print("ok", out)

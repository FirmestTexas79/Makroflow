"""
Použití:  python tools/cutscene/ending_wake.py [--preview] [--from S] [--to S]
Výstup:   app/src/main/res/raw/ending_probuzeni.mp4 (720 × 1560, H.264 + zvuk z build/ending_probuzeni.m4a)

Cutscéna KONEC IV · PROBUZENÍ (tajný konec, docs/adr/0114). Hráč se vyspal na mechu ve Středu kořenů a vzpomene si,
že tu byl už mnohokrát. Místo kopí, potopy nebo přání jde domů spát. Záběry (s):
  0–10   Střed kořenů: noc → ráno, hráč spí na mechu, nad ním blednou čísla ze snů; vstane.
  10–24  Plechová jmenovka 213 se houpe ve tmě, čísla běží pozpátku (212, 211, 205, 198) a kolem
         chodí v kruhu průsvitní dřívější poutníci, každý se svým číslem.
  24–31  Gudwin, Mydrus (bledne – zapomíná), Elderan, Pamětní kniha.
  31–36  Hunfrey: „Vždycky se vrať. Vyspi se.“
  36–64  Cesta domů: schody ze Středu, les s Gudwinem, útes s Keshovými lucernami, Věž (Sept listuje), Ozvěna (Lio).
  64–71  Město v noci: hráč dojde k červenému domku, v okně se rozsvítí.
  71–81  Pokojík: hráč v posteli, Spirra vyskočí a stočí se mu u nohou, svíčka zhasne.
  81–96  Čekárna Věže: automat vydá lístek 214, tabule zhasne, na lístku vybledne číslo – je prázdný.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import cine as C  # noqa: E402
from cine import W, H, Caption  # noqa: E402

B = os.path.join(HERE, "build")
rng = np.random.default_rng(2140)
GOLD = np.array([255, 214, 120], np.float32)
HEART = C.res("root_heart")
FOREST = C.res("root_forest")
SIGNAL = C.res("root_signal")
LOBBY = C.res("tower_lobby")
OZV = C.res("ozvena")
TOWN = C.res("poketown")


def mon(name, size=64):
    a = C.res(name)
    step = a.shape[0] // size
    return a[step // 2::step, step // 2::step].copy()


GUDWIN_BIG = C.shrink(C.res("gudwin_oliver"), 120)
GUDWIN = C.shrink(C.res("gudwin_oliver"), 30)
MYDRUS = mon("makromon_23_mydrus").repeat(2, 0).repeat(2, 1)
ELDERAN = C.shrink(C.res("npc_elderan"), 124)
HUNFREY = C.shrink(C.res("npc_hunfrey"), 170)
KESH = mon("makromon_11_lumex", 32)
LIO = mon("makromon_39_grifben", 32)
SPIRRA_AWAKE = mon("makromon_12_spirra", 32)


def spirra_sleeping():
    """Spirra stočená ve spánku (stejná jako v konci I – ending_spear.py)."""
    w, h = 22, 14
    m = np.zeros((h, w), int)
    for y in range(h):
        for x in range(w):
            if (x - 7) ** 2 + (y - 7) ** 2 <= 36:
                m[y, x] = 1
            if ((x - 13) / 5.5) ** 2 + ((y - 9.5) / 3.6) ** 2 <= 1:
                m[y, x] = 1
            if (x - 17.5) ** 2 + (y - 8.5) ** 2 <= 9.5:
                m[y, x] = 2
    for k in range(60):
        a = k * 0.32
        r = 0.4 + a * 0.55
        x, y = int(round(7 + math.cos(a) * r)), int(round(7 + math.sin(a) * r))
        if 0 <= x < w and 0 <= y < h and m[y, x] and r < 5.6:
            m[y, x] = 3
    m[(m == 1) & (np.arange(h)[:, None] >= 11)] = 3
    for (x, y) in ((18, 5), (19, 5), (19, 4), (17, 5)):
        m[y, x] = 4
    m[9, 18] = 5; m[9, 19] = 5
    pal = {1: (246, 226, 190), 2: (255, 242, 216), 3: (214, 182, 138), 4: (232, 156, 148), 5: (86, 56, 44)}
    out = np.zeros((h, w, 4), np.float32)
    for y in range(h):
        for x in range(w):
            if m[y, x]:
                out[y, x, :3] = pal[m[y, x]]; out[y, x, 3] = 255
            elif any(0 <= y + dy < h and 0 <= x + dx < w and m[y + dy, x + dx] for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0))):
                out[y, x, :3] = (96, 64, 44); out[y, x, 3] = 255
    return out


SPIRRA = spirra_sleeping()

# ── malé číslice 3 × 5 (jmenovky, popisky) ────────────────────────────────
DIG = {"0": "111101101101111", "1": "010110010010111", "2": "111001111100111", "3": "111001111001111",
       "4": "101101111001001", "5": "111100111001111", "8": "111101111101111", "9": "111101111001111"}


def digits(img, s, x, y, col, sc=1, alpha=1.0):
    """Číslo malým písmem 3 × 5 do RGB(A) obrazu; x, y = levý horní roh."""
    for i, ch in enumerate(s):
        bits = DIG[ch]
        for k, b in enumerate(bits):
            if b == "1":
                yy, xx = y + (k // 3) * sc, x + i * 4 * sc + (k % 3) * sc
                if 0 <= yy and yy + sc <= img.shape[0] and 0 <= xx and xx + sc <= img.shape[1]:
                    img[yy:yy + sc, xx:xx + sc, :3] = img[yy:yy + sc, xx:xx + sc, :3] * (1 - alpha) + np.asarray(col) * alpha
                    if img.shape[2] == 4:
                        img[yy:yy + sc, xx:xx + sc, 3] = np.maximum(img[yy:yy + sc, xx:xx + sc, 3], 255 * alpha)


def with_sprite(base, spr, x, y, alpha=1.0):
    img = base.copy()
    C.blit(img, spr, x, y, alpha)
    return img


def hero_at(base, name, t, x, y, alpha=1.0, fps=8):
    """Postava hráče patou na (x, y) mapy."""
    C.blit(base, C.hero(name, int(t * fps)), x - 32, y - 38, alpha)


def tintmap(img, k):
    out = img.copy(); out[:, :, :3] = np.clip(out[:, :, :3] * np.asarray(k, np.float32), 0, 255); return out


def glow(img, cx, cy, rx, ry, col, k):
    yy, xx = np.mgrid[0:img.shape[0], 0:img.shape[1]]
    g = np.clip(1 - np.hypot((xx - cx) / rx, (yy - cy) / ry), 0, 1) ** 1.6
    img[:, :, :3] += np.asarray(col, np.float32) * g[:, :, None] * k
    return img


def edge_fade(img, t, dur, fin=0.5, fout=0.5):
    if t < fin:
        img = C.fade(img, 1 - t / fin)
    if t > dur - fout:
        img = C.fade(img, (t - dur + fout) / fout)
    return img


MOTES = rng.uniform(0, 1, (80, 4))


def motes(img, t, col=(255, 236, 190), k=0.6, up=True):
    y = (MOTES[:, 1] * (H + 40) - (t * (14 + MOTES[:, 2] * 20) if up else 0)) % (H + 40) - 20
    x = MOTES[:, 0] * W + np.sin(t * 0.8 + MOTES[:, 3] * 9) * 6
    C.dots(img, x, y, col, (0.3 + 0.5 * MOTES[:, 3]) * k)


# ── 1) Střed kořenů: ráno na mechu ────────────────────────────────────────
BED = (40, 300)                                                           # mech v mapě Středu (st_mech)
LYING = np.rot90(C.hero("idle_s", 0), 1).copy()                           # hlava vlevo


def s1_morning(t, _):
    base = HEART.copy()
    k = C.ease((t - 0.5) / 5.0)                                           # noc → ráno
    if t < 6.5:
        h = LYING
        C.blit(base, h, BED[0] - 20, BED[1] - 34)
    else:
        hero_at(base, "idle_s" if t < 8.4 else "idle_n", t, BED[0] + 1, BED[1] + 2)
    night = np.array([0.32, 0.38, 0.62]); day = np.array([1.08, 0.98, 0.84])
    base = tintmap(base, night * (1 - k) + day * k)
    for i, (sx, w) in enumerate(((50, 10), (82, 14), (118, 8))):         # paprsky shora
        yy, xx = np.mgrid[0:base.shape[0], 0:base.shape[1]]
        band = np.clip(1 - np.abs(xx - sx - (yy - 120) * 0.25) / w, 0, 1) * (yy > 40) * (yy < 340)
        base[:, :, :3] += GOLD * band[:, :, None] * 0.18 * k * (0.8 + 0.2 * math.sin(t * 1.3 + i))
    if t < 5.0:                                                           # čísla ze snů blednou a stoupají
        for i, s in enumerate(("213", "212", "211", "205", "198")):
            tt = t - i * 0.6
            if tt > 0:
                a = max(0.0, 1 - tt / 2.4) * 0.8
                digits(base, s, 26 + (i * 9) % 30, int(BED[1] - 30 - tt * 9), (255, 80, 64), 1, a)
    img = C.view(base, 60, 285, zoom=3)
    motes(img, t, k=0.5 * k)
    return edge_fade(img, t, 10, 1.2, 0.6)


# ── 2) Jmenovka 213 a kruh poutníků ───────────────────────────────────────
def tag_sprite(num, fade=1.0):
    """Plechová jmenovka 34 × 16: kov, dírka vlevo, vyražené číslo."""
    t = np.zeros((16, 34, 4), np.float32)
    t[1:15, 1:33, :3] = (150, 156, 160); t[1:15, 1:33, 3] = 255
    t[1, 1:33, :3] = (196, 200, 204); t[14, 1:33, :3] = (104, 108, 112)
    for (y, x) in ((1, 1), (1, 32), (14, 1), (14, 32)):
        t[y, x, 3] = 0
    t[0, 2:32, :3] = (60, 60, 64); t[0, 2:32, 3] = 255; t[15, 2:32, :3] = (60, 60, 64); t[15, 2:32, 3] = 255
    t[2:14, 0, :3] = (60, 60, 64); t[2:14, 0, 3] = 255; t[2:14, 33, :3] = (60, 60, 64); t[2:14, 33, 3] = 255
    t[6:10, 4:7, :3] = (20, 20, 24)                                       # dírka
    rr = np.random.default_rng(5)
    t[1:15, 1:33, :3] += rr.normal(0, 7, (14, 32, 1))                     # rez a škrábance
    for i, ch in enumerate(num):
        bits = DIG[ch]
        for k, b in enumerate(bits):
            if b == "1":
                yy, xx = 3 + (k // 3) * 2, 10 + i * 8 + (k % 3) * 2
                t[yy:yy + 2, xx:xx + 2, :3] = t[yy:yy + 2, xx:xx + 2, :3] * (1 - fade) + np.array([62, 64, 70]) * fade
    return t


NUMS = ["213", "212", "211", "205", "198"]
FLIP = [0.0, 4.2, 5.0, 5.8, 6.6]


def s2_circle(t, _):
    img = C.canvas((8, 10, 16))
    img = glow(img, W / 2, 380, 220, 300, (60, 70, 110), 0.5)
    motes(img, t, (180, 200, 255), 0.35)
    idx = max(i for i, f in enumerate(FLIP) if t >= f)
    # kruh poutníků (přibývají s každým číslem pozpátku)
    ring_k = C.ease((t - 8.4) / 1.6)
    cx, cy, rx, ry = W / 2, 500, 120, 60
    n = idx + 1 if t < 8.4 else 5
    figs = []
    for i in range(n):
        if i == 0 and t < 8.4:
            continue
        ang = (i - 0) / 5 * 2 * math.pi + t * 0.45 * ring_k + math.pi / 2
        x = cx + math.cos(ang) * rx; y = cy + math.sin(ang) * ry
        dx, dy = -math.sin(ang), math.cos(ang)
        name = ("walk_e" if dx > 0 else "walk_w") if abs(dx) > abs(dy) else ("walk_s" if dy > 0 else "walk_n")
        figs.append((y, x, i, name))
    for (y, x, i, name) in sorted(figs):
        spr = C.hero(name if ring_k > 0 else "idle_s", int(t * 8)).repeat(4, 0).repeat(4, 1)
        old = i                                                            # 0 = 213 (ty), 4 = 198 (nejstarší)
        a = (1.0 if old == 0 else 0.8 - old * 0.08) * min(1.0, (t - FLIP[max(1, i)]) / 0.6 if i else 1.0)
        ghost = spr.copy()
        if old:
            ghost[:, :, :3] = ghost[:, :, :3] * 0.4 + np.array([140, 170, 255]) * 0.6
        C.blit(img, ghost, int(x) - 128, int(y) - 152, max(0.0, a))
        digits(img, NUMS[i], int(x) - 11, int(y) - 100, (255, 110, 90) if old == 0 else (170, 190, 255), 2, max(0.0, a))
    # jmenovka nahoře, houpe se na provázku
    sway = math.sin(t * 1.4) * 10 * (1 - 0.6 * ring_k)
    ty = int(150 + 40 * C.ease(t / 1.5)) - int(60 * ring_k)
    tx = int(W / 2 - 85 + sway)
    tag = tag_sprite(NUMS[idx]).repeat(5, 0).repeat(5, 1)
    x0, y0 = W // 2, 0
    hx, hy = tx + 27, ty + 40                                             # dírka
    for s in np.linspace(0, 1, 300):                                      # provázek
        C.dots(img, [x0 + (hx - x0) * s], [y0 + (hy - y0) * s], (120, 100, 80), np.array([1.0]))
    jitter = 2 if any(0 < t - f < 0.15 for f in FLIP[1:]) else 0
    C.blit(img, tag, tx + jitter, ty)
    if any(0 < t - f < 0.3 for f in FLIP[1:]):
        img = glow(img, tx + 85, ty + 40, 120, 70, (200, 220, 255), 0.35)
    return edge_fade(img, t, 14, 0.6, 0.6)


# ── 3) Kdo si pamatoval ───────────────────────────────────────────────────
def book_sprite():
    b = np.zeros((48, 64, 4), np.float32)
    b[2:46, 2:62, :3] = (110, 60, 40); b[2:46, 2:62, 3] = 255             # desky
    b[4:44, 5:31, :3] = (238, 226, 196); b[4:44, 33:59, :3] = (238, 226, 196)
    b[4:44, 31:33, :3] = (150, 120, 90)
    for i in range(4):                                                    # čtyři jména z Ozvěny
        y = 10 + i * 8
        w = (17, 13, 19, 11)[i]
        b[y:y + 2, 9:9 + w, :3] = (70, 50, 40)
    for i in range(5):
        y = 9 + i * 7
        b[y:y + 1, 37:37 + (18, 14, 16, 12, 17)[i], :3] = (120, 100, 80)
    return b


BOOK = book_sprite().repeat(2, 0).repeat(2, 1)
PORTS = [(GUDWIN_BIG, 0.0), (MYDRUS, 1.5), (ELDERAN, 3.0), (BOOK, 4.5)]


def s3_remember(t, _):
    img = C.canvas((10, 10, 14))
    motes(img, t, (255, 230, 190), 0.3)
    for i, (spr, t0) in enumerate(PORTS):
        r, c = divmod(i, 2)
        cx, cy = 92 + c * 176, 214 + r * 210
        a = C.ease((t - t0) / 0.8)
        if i == 1:                                                        # Mydrus zapomíná – bledne a vrací se
            a *= 0.55 + 0.45 * math.cos((t - t0) * 2.2)
        img = glow(img, cx, cy, 90, 90, (255, 220, 170) if i != 2 else (140, 220, 255), 0.18 * a)
        C.blit(img, spr, cx - spr.shape[1] // 2, cy - spr.shape[0] // 2, a)
    return edge_fade(img, t, 7, 0.4, 0.5)


def s3_hunfrey(t, _):
    img = C.canvas((12, 10, 10))
    img = glow(img, W / 2, 330, 160, 160, (255, 190, 120), 0.3 + 0.05 * math.sin(t * 2))
    a = C.ease(t / 1.0)
    C.blit(img, HUNFREY, W // 2 - HUNFREY.shape[1] // 2, 330 - HUNFREY.shape[0] // 2 + int(math.sin(t * 1.5) * 2), a)
    motes(img, t, (255, 220, 170), 0.4)
    return edge_fade(img, t, 5, 0.3, 0.6)


# ── 4) Cesta domů ─────────────────────────────────────────────────────────
def lerp(a, b, k):
    return a + (b - a) * k


def walk_shot(base, t, dur, p0, p1, name, cam, zoom=3, extra=None, post=None):
    k = C.ease((t - 0.3) / (dur - 0.8))
    x, y = lerp(p0[0], p1[0], k), lerp(p0[1], p1[1], k)
    b = base.copy()
    if extra:
        extra(b, t, x, y)
    hero_at(b, name if 0.05 < k < 0.98 else name.replace("walk", "idle"), t, int(x), int(y))
    cx, cy = cam(x, y)
    img = C.view(b, cx, cy, zoom=zoom)
    if post:
        img = post(img, t, cx, cy)
    return edge_fade(img, t, dur, 0.3, 0.3)


HEART_DAY = tintmap(HEART, (1.08, 0.98, 0.84))


def s4a_stairs(t, _):
    return walk_shot(HEART_DAY, t, 5.5, (88, 150), (88, 24), "walk_n", lambda x, y: (88, max(130, y + 30)),
                     post=lambda img, t, cx, cy: (motes(img, t, k=0.4), img)[1])


FOREST_M = tintmap(FOREST, (1.25, 1.3, 1.2))


def forest_extra(b, t, x, y):
    C.blit(b, GUDWIN, 214 - GUDWIN.shape[1] // 2, 318 - GUDWIN.shape[0])
    if 1.4 < t < 4.6:                                                     # bublina: řekne tvoje jméno
        bx, by = 202, 270
        b[by:by + 12, bx:bx + 24, :3] = (246, 244, 236); b[by:by + 12, bx:bx + 24, 3] = 255
        b[by + 12:by + 15, bx + 6:bx + 9, :3] = (246, 244, 236); b[by + 12:by + 15, bx + 6:bx + 9, 3] = 255
        n = min(3, int((t - 1.4) * 3))
        for i in range(n):
            b[by + 5:by + 7, bx + 6 + i * 5:bx + 8 + i * 5, :3] = (80, 60, 50)


def s4b_forest(t, _):
    return walk_shot(FOREST_M, t, 5.5, (250, 400), (236, 250), "walk_n", lambda x, y: (226, y - 24), extra=forest_extra,
                     post=lambda img, t, cx, cy: (motes(img, t, (200, 255, 220), 0.5), img)[1])


_SL = SIGNAL[:, :, :3]
LAMPS = np.argwhere((_SL[:, :, 0] > 200) & (_SL[:, :, 1] > 160) & (_SL[:, :, 2] < 120) & (SIGNAL[:, :, 3] > 200))


def signal_extra(b, t, x, y):
    C.blit(b, KESH, 124 - 16, 196 - 30)
    C.dots(b, LAMPS[:, 1], LAMPS[:, 0], (255, 240, 170), np.full(len(LAMPS), 1.0))


def signal_post(img, t, cx, cy):
    z = 3
    for (ly, lx) in LAMPS[::6]:
        sx, sy = (lx - cx) * z + W / 2, (ly - cy) * z + H / 2
        if -40 < sx < W + 40 and -40 < sy < H + 40:
            img = glow(img, sx, sy, 34, 34, GOLD, 0.16 * (0.85 + 0.15 * math.sin(t * 7 + lx)))
    return img


def s4c_signal(t, _):
    return walk_shot(SIGNAL, t, 5.5, (138, 400), (136, 250), "walk_n", lambda x, y: (118, y - 40), extra=signal_extra,
                     post=signal_post)


def lobby_extra(b, t, x, y):
    if 0.6 < t < 5.0:                                                     # Sept listuje složkou
        page = int(t * 7) % 2
        px, py = 122, 176
        b[py:py + 6, px:px + 10, :3] = (236, 226, 200); b[py:py + 6, px:px + 10, 3] = 255
        b[py - 2 + page:py + 4, px + 5:px + 10, :3] = (255, 250, 236) if page else (210, 200, 176)
        b[py:py + 6, px + 5, :3] = (150, 130, 100)


def s4d_tower(t, _):
    return walk_shot(LOBBY, t, 5.5, (88, 120), (88, 262), "walk_s", lambda x, y: (88, min(max(y - 10, 130), 190)),
                     extra=lobby_extra)


LETTER = np.zeros((5, 7, 4), np.float32); LETTER[:, :, :3] = (248, 244, 232); LETTER[:, :, 3] = 255
LETTER[0, :, :3] = (200, 190, 170); LETTER[1, 1:6, :3] = (210, 200, 180); LETTER[2, 3, :3] = (200, 60, 60)


def ozv_extra(b, t, x, y):
    C.blit(b, LIO, 150 - 16, 182 - 30)
    C.blit(b, LETTER, 150 + 8, 182 - 14 + int(math.sin(t * 2) * 1))


def s4e_ozvena(t, _):
    return walk_shot(OZV, t, 5.5, (186, 290), (190, 112), "walk_n", lambda x, y: (176, y - 20), extra=ozv_extra)


# ── 5) Město v noci ───────────────────────────────────────────────────────
TOWN_N = tintmap(TOWN, (0.34, 0.4, 0.66))
DOOR = (102, 188)
WIN = (123, 182)
TOWN_PATH = [(135, 330), (135, 212), (102, 212), (102, 192)]


def path_at(pts, k):
    seg = [math.dist(pts[i], pts[i + 1]) for i in range(len(pts) - 1)]
    d = k * sum(seg)
    for i, s in enumerate(seg):
        if d <= s:
            f = d / s
            dx, dy = pts[i + 1][0] - pts[i][0], pts[i + 1][1] - pts[i][1]
            name = ("walk_e" if dx > 0 else "walk_w") if abs(dx) > abs(dy) else ("walk_s" if dy > 0 else "walk_n")
            return pts[i][0] + dx * f, pts[i][1] + dy * f, name
        d -= s
    return pts[-1][0], pts[-1][1], "walk_n"


def s5_town(t, _):
    b = TOWN_N.copy()
    k = C.ease((t - 0.3) / 4.2)
    x, y, name = path_at(TOWN_PATH, k)
    lit = t > 5.0
    if lit:                                                               # v okně se rozsvítí
        b[WIN[1] - 4:WIN[1] + 3, WIN[0] - 5:WIN[0] + 6, :3] = (255, 214, 130)
    if t < 4.6:
        hero_at(b, name, t, int(x), int(y), 1.0)
    img = C.view(b, 120, 250, zoom=2)
    if lit:
        img = glow(img, (WIN[0] - 120) * 2 + W / 2, (WIN[1] - 250) * 2 + H / 2, 70, 60, GOLD, 0.35 * C.ease((t - 5.0) / 0.6))
    img = glow(img, (x - 120) * 2 + W / 2, (y - 250) * 2 + H / 2 - 20, 40, 40, (200, 210, 255), 0.08 if t < 4.6 else 0)
    return edge_fade(img, t, 7, 0.4, 0.5)


# ── 6) Pokojík ────────────────────────────────────────────────────────────
def room():
    r = np.zeros((260, 120, 3), np.float32)
    r[:120] = (58, 60, 92)
    for x in range(0, 120, 10):
        r[:120, x] = (48, 50, 78)
    r[118:122] = (90, 66, 50)
    r[122:] = (112, 82, 58)
    for y in range(122, 260, 9):
        r[y] = (92, 66, 46)
    for y in range(122, 260, 9):
        off = (y * 7) % 30
        for x in range(off, 120, 30):
            r[y:y + 9, x] = (92, 66, 46)
    r[24:72, 38:82] = (96, 70, 48)                                        # okno
    r[27:69, 41:79] = (22, 30, 60)
    r[27:69, 59:61] = (96, 70, 48); r[47:49, 41:79] = (96, 70, 48)
    yy, xx = np.mgrid[0:260, 0:120]
    moon = (xx - 70) ** 2 + (yy - 36) ** 2 <= 20
    r[moon] = (240, 240, 220)
    for (sx, sy) in ((46, 32), (52, 56), (66, 58), (74, 52), (48, 44)):
        r[sy, sx] = (220, 220, 255)
    r[132:230, 26:94] = (118, 78, 48)                                     # postel
    r[126:140, 26:94] = (98, 62, 38); r[128:138, 28:92] = (130, 88, 56)
    r[142:156, 38:82] = (232, 232, 242); r[155, 38:82] = (196, 196, 210)  # polštář
    r[150:226, 28:92] = (186, 58, 58)                                     # deka
    for y in range(150, 226, 6):
        for x in range(28 + (y // 6 % 2) * 6, 92, 12):
            r[y:y + 3, x:x + 3] = (206, 82, 76)
    r[150:156, 28:92] = (236, 230, 220)                                   # přehnutý okraj
    r[224:228, 28:92] = (140, 40, 40)
    r[150:180, 98:114] = (100, 72, 48); r[150:152, 96:116] = (124, 90, 60)  # stolek
    return r


ROOM = room()
HEAD = C.hero("idle_s", 0)[21:31, 26:39].copy()                          # hlava z postavy


def s6_room(t, _):
    r = ROOM.copy()
    C.blit(r, HEAD, 53, 141)
    if int(t / 1.6) % 2:                                                  # dech pod dekou
        r[150, 28:92] = (250, 246, 236)
    candle = t < 7.0
    r[142:150, 104:107] = (240, 236, 220)
    if candle:
        r[139 + int(t * 9) % 2:142, 105:106] = (255, 200, 90)
    # Spirra vyskočí na postel a stočí se u nohou
    if t < 2.0:
        C.blit(r, SPIRRA_AWAKE, 104, 214)
    elif t < 3.4:
        f = (t - 2.0) / 1.4
        C.blit(r, SPIRRA_AWAKE, int(lerp(104, 54, f)), int(lerp(214, 196, f) - math.sin(f * math.pi) * 22))
    elif t < 4.4:
        C.blit(r, SPIRRA_AWAKE, 54 + (int(t * 6) % 2) * 2, 196)
    else:
        C.blit(r, SPIRRA, 52, 206)
    dark = 0.0 if candle else C.ease((t - 7.0) / 1.2) * 0.45
    r = r * (1 - dark) + np.array([10, 14, 40]) * dark
    yy, xx = np.mgrid[0:260, 0:120]
    beam = (np.abs((xx - 60) - (yy - 70) * 0.35) < 20 - (yy - 70) * 0.04) & (yy > 70) & (yy < 250)  # měsíční světlo
    r[beam] = r[beam] * 0.85 + np.array([170, 180, 230]) * 0.15
    img = r.repeat(3, 0).repeat(3, 1)
    if candle:
        img = glow(img, 315, 420, 150, 130, (255, 190, 110), 0.25 + 0.03 * math.sin(t * 9))
    if t > 5.0:                                                           # „z“ nad spícími
        for (zx0, zy0, ph) in ((180, 400, 0.0), (210, 590, 0.8)):
            zt = (t - 5.0 + ph) % 2.2
            zx, zy = zx0 + zt * 10, zy0 - zt * 26
            for (dx, dy) in ((0, 0), (1, 0), (2, 0), (1, 1), (0, 2), (1, 2), (2, 2)):
                C.dots(img, [zx + dx * 3, zx + dx * 3 + 1], [zy + dy * 3, zy + dy * 3], (230, 230, 245),
                       np.array([max(0.0, 1 - zt / 2.2)] * 2))
    return edge_fade(img, t, 10, 0.5, 0.8)


# ── 7) Čekárna: lístek 214 ────────────────────────────────────────────────
LOBBY_N = tintmap(LOBBY, (0.5, 0.52, 0.66))
SLOT = (17, 224)


def board_off(b, t):
    """Tabule 213: zhasne (nikdo další)."""
    x0, y0, x1, y1 = 14, 164, 35, 176
    if t > 5.2:
        reg = b[y0:y1, x0:x1, :3]
        red = (reg[:, :, 0] > 50) & (reg[:, :, 0] > reg[:, :, 1] * 1.6)
        k = C.ease((t - 5.2) / 0.5)
        reg[red] = reg[red] * (1 - k) + np.array([40, 26, 30]) * k


def s7_ticket(t, _):
    if t < 8.0:
        b = LOBBY_N.copy()
        board_off(b, t)
        if t > 1.2:                                                       # lístek vyjede a spadne
            f = C.ease((t - 1.2) / 1.2)
            ln = int(2 + 6 * f)
            tk = np.zeros((ln, 6, 4), np.float32); tk[:, :, :3] = (246, 242, 228); tk[:, :, 3] = 255
            if t < 3.0:
                C.blit(b, tk, SLOT[0] - 3, SLOT[1])
            else:
                g = C.ease((t - 3.0) / 0.5)
                tk = np.rot90(tk).copy() if g >= 1 else tk
                C.blit(b, tk, SLOT[0] - 3 + int(6 * g), int(SLOT[1] + 12 * g * g))
        img = C.view(b, 45, 200, zoom=4)
        if 1.0 < t < 2.6:
            img = glow(img, (SLOT[0] - 45) * 4 + W / 2, (SLOT[1] - 200) * 4 + H / 2, 40, 30, (255, 240, 200), 0.25)
        return edge_fade(img, t, 8.0, 0.6, 0.4)
    tt = t - 8.0                                                          # detail lístku: číslo vybledne
    img = C.canvas((16, 14, 18))
    tk = np.zeros((30, 22, 4), np.float32); tk[:, :, :3] = (246, 242, 228); tk[:, :, 3] = 255
    tk[0:2, :, :3] = (220, 214, 196); tk[-2:, :, :3] = (220, 214, 196)
    for x in range(0, 22, 2):
        tk[0, x, 3] = 0; tk[-1, x, 3] = 0                                 # perforace
    tk[6, 4:18, :3] = (200, 190, 176)
    digits(tk, "214", 5, 11, (40, 36, 40), 1, max(0.0, 1 - C.ease((tt - 1.6) / 3.0)))
    tk[21, 4:18, :3] = (200, 190, 176)
    big = tk.repeat(8, 0).repeat(8, 1)
    C.blit(img, big, W // 2 - big.shape[1] // 2, 300)
    img = glow(img, W / 2, 420, 200, 200, (255, 236, 200), 0.08)
    if tt > 5.6:
        img = C.fade(img, (tt - 5.6) / 1.4)
    if tt < 0.5:
        img = C.fade(img, 1 - tt / 0.5)
    return img


SHOTS = [(0, 10, s1_morning), (10, 24, s2_circle), (24, 31, s3_remember), (31, 36, s3_hunfrey),
         (36, 41.5, s4a_stairs), (41.5, 47, s4b_forest), (47, 52.5, s4c_signal), (52.5, 58, s4d_tower), (58, 63.5, s4e_ozvena),
         (63.5, 70.5, s5_town), (70.5, 80.5, s6_room), (80.5, 96, s7_ticket)]
CAPS = [
    Caption(0.8, 4.8, "Ráno. Mech je teplý a ty jsi spal celou noc."),
    Caption(5.2, 9.6, "Poprvé od chvíle, kdy jsi vešel do Makrosvěta, se ti nezdálo o číslech."),
    Caption(10.6, 13.8, "A vzpomeneš si. Na všechno."),
    Caption(14.2, 18.8, "Jmenovka 213 visela v lese dřív, než jsi přišel – protože jsi tu už byl. A předtím taky."),
    Caption(19.2, 23.8, "Kruh za kruhem. Pokaždé nádoba. Pokaždé kopí, nebo potopa, nebo přání. A pak zase od začátku."),
    Caption(24.4, 30.6, "Gudwin si to pamatoval celou dobu. Mydrus to zapomínal. Elderan to hlídal u brány. Kniha to zapsala."),
    Caption(31.2, 35.8, "A Hunfrey ti to říkal pořád dokola: „Vždycky se vrať. Vyspi se.“", pos=0.8),
    Caption(36.6, 41.2, "Tak se vrátíš. Ne ke kopí. Domů."),
    Caption(41.8, 46.6, "Nahoru lesem, kde Gudwin řekne tvoje jméno,"),
    Caption(47.2, 52.2, "kolem útesu, kde svítí Keshovy lucerny,"),
    Caption(52.8, 57.8, "Věží, kde Sept marně listuje ve tvé složce,"),
    Caption(58.4, 63.2, "náměstím, kde Lio drží dopis bez adresy."),
    Caption(64.2, 70.0, "Ve Městě si lehneš do své postele."),
    Caption(72.0, 79.6, "Spirra se ti stočí u nohou.", pos=0.92),
    Caption(81.4, 87.4, "V čekárně Věže vydá automat lístek 214."),
    Caption(88.6, 94.4, "Je prázdný. Nikdo další už není na řadě."),
]

if __name__ == "__main__":
    a = sys.argv[1:]
    s0 = float(a[a.index("--from") + 1]) if "--from" in a else 0.0
    s1 = float(a[a.index("--to") + 1]) if "--to" in a else None
    if "--frames" in a:                                                   # kontrolní snímky: --frames 3,17,…
        from PIL import Image
        ts = [float(v) for v in a[a.index("--frames") + 1].split(",")]
        ims = []
        for tg in ts:
            for (t0, t1, fn) in SHOTS:
                if t0 <= tg < t1:
                    ims.append(Image.fromarray(np.clip(fn(tg - t0, tg), 0, 255).astype(np.uint8)))
        sheet = Image.new("RGB", (W * len(ims), H))
        for i, im in enumerate(ims):
            sheet.paste(im, (i * W, 0))
        sheet.save(a[a.index("--out") + 1])
        sys.exit()
    audio = os.path.join(B, "ending_probuzeni.m4a")
    out = os.path.join(B, "ending_probuzeni_preview.mp4") if "--preview" in a else os.path.join(C.ROOT, "app", "src", "main", "res", "raw", "ending_probuzeni.mp4")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    C.render(SHOTS, CAPS, out, audio=audio if os.path.exists(audio) and "--preview" not in a else None, start=s0, end=s1)
    print("ok", out)

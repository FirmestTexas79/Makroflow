"""
Použití:  python tools/cutscene/ending_wish.py [--preview] [--from S] [--to S]
Výstup:   app/src/main/res/raw/ending_prani.mp4 (720 × 1560, H.264 + zvuk z build/ending_prani.m4a)

Cutscéna KONEC III · POSLEDNÍ PŘÁNÍ (docs/adr/0113). Jediné místo ve hře, kde se prozradí, že Makromoni byli lidé
(Lumenci). Záběry (s):
  0–14   Nad posledním světlem se píší znaky Lumenců – tři řádky přání (pod nimi překlad).
  14–21  Čtvrtý řádek se zastaví uprostřed: A AŽ SE UŽ NEBUDEME BÁT… poslední znak bliká nedopsaný.
  21–28  Hráč ho dopíše zlatými znaky: …AŤ SI VZPOMENEME. Všechny znaky vzplanou.
  28–36  Světlo se rozletí: jiskry letí vzhůru Středem kořenů a kmenem.
  36–44  Louka: jiskry prší, Makromoni se zastaví uprostřed kroku („!“).
  44–52  Signální útes: Kesh upustí lucernu. „Asko… Jmenoval jsem se Asko.“
  52–58  Ozvěna: Brann se zadívá na svoje ruce.
  58–65  Louka: malý Makromon v trávě se rozpláče.
  65–79  Chrám: na malbě se rozsvítí lidé se světlem v dlaních → řada Makromonů a za každým jeho světelná postava.
  79–95  Spirra na rameni, šepot; kamera odjíždí do noci plné stoupajících světel.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import cine as C  # noqa: E402
from cine import W, H, Caption  # noqa: E402

B = os.path.join(HERE, "build")
rng = np.random.default_rng(777)
BLUE = np.array([120, 200, 255], np.float32)
GOLDC = np.array([255, 214, 120], np.float32)
HEART = C.res("root_heart")
TRUNK = C.res("root_trunk")
SIGNAL = C.res("root_signal")
OZV = C.res("ozvena")
TEMPLE = C.res("root_temple")
MEADOW = C.res("meadow")


def mon(name, size=64):
    """Makromon z ilustrace 640 × 640 (pixel art 64 × 64 zvětšený ×10) → ostré pixely 64 nebo 32."""
    a = C.res(name)
    step = 640 // size
    return a[step // 2::step, step // 2::step].copy()


LUMEX = mon("makromon_11_lumex")
IGNAR = mon("makromon_01_ignar")
FINLET = mon("makromon_20_finlet")
FLORI64 = mon("makromon_07_flori")
AQULIN64 = mon("makromon_04_aqulin")
MYCIT64 = mon("makromon_22_mycit")
FLORI = mon("makromon_07_flori", 32)
AQULIN = mon("makromon_04_aqulin", 32)
MYCIT = mon("makromon_22_mycit", 32)
SPIRRA32 = mon("makromon_12_spirra", 32)
ROW = [mon(n) for n in ("makromon_07_flori", "makromon_04_aqulin", "makromon_01_ignar", "makromon_11_lumex", "makromon_20_finlet",
                         "makromon_22_mycit", "makromon_31_axlu", "makromon_40_lumivix", "makromon_44_gemak", "makromon_12_spirra")]


# ── znaky Lumenců: každé písmeno = 3–4 tahy na mřížce 5 × 7 (deterministicky z písmene) ─────────────────────
BASE = {"Á": "A", "Č": "C", "Ď": "D", "É": "E", "Ě": "E", "Í": "I", "Ň": "N", "Ó": "O", "Ř": "R", "Š": "S", "Ť": "T", "Ú": "U",
        "Ů": "U", "Ý": "Y", "Ž": "Z"}
STROKES = [lambda m: m.__setitem__((slice(0, 7), 0), 1), lambda m: m.__setitem__((slice(0, 7), 4), 1),
           lambda m: m.__setitem__((0, slice(0, 5)), 1), lambda m: m.__setitem__((3, slice(0, 5)), 1),
           lambda m: m.__setitem__((6, slice(0, 5)), 1), lambda m: m.__setitem__((slice(0, 7), 2), 1),
           lambda m: [m.__setitem__((k, min(4, k * 5 // 7)), 1) for k in range(7)],
           lambda m: [m.__setitem__((k, 4 - min(4, k * 5 // 7)), 1) for k in range(7)],
           lambda m: m.__setitem__((slice(1, 4), slice(1, 4)), 1)]


def rune(ch):
    m = np.zeros((7, 5), int)
    if ch in " ,.…":
        if ch in ".…":
            m[6, 2] = 1
        return m
    c = ord(BASE.get(ch, ch))
    picks = [(c * 7 + k * 13) % len(STROKES) for k in range(3 + c % 2)]
    for p in dict.fromkeys(picks):
        STROKES[p](m)
    if ch in BASE:
        m[0, 2] = 1                                                       # diakritika = tečka nahoře
    return m


LINES = ["AŤ NÁS NIKDO NENAJDE.", "AŤ ZAPOMENE KAŽDÝ, KDO NÁM UBLÍŽIL.", "AŤ UŽ SE NIKDY NEMUSÍME BÁT.", "A AŽ SE UŽ NEBUDEME BÁT…"]
END = "AŤ SI VZPOMENEME."
SC = 3                                                                    # znak = 15 × 21 px snímku


def wrap(s, n=17):
    out, cur = [], ""
    for w in s.split(" "):
        if len(cur) + len(w) + 1 > n:
            out.append(cur); cur = w
        else:
            cur = (cur + " " + w).strip()
    out.append(cur)
    return out


def draw_runes(img, text, x0, y0, shown, col, flicker_last=False, t=0.0, flare=0.0):
    """Napíše řádek znaků (shown = kolik písmen je už vidět, desetinně = právě se píše)."""
    x = x0
    for i, ch in enumerate(text):
        if i >= shown:
            break
        m = rune(ch)
        part = min(1.0, shown - i)
        a = part
        if flicker_last and i == int(shown) - 1:
            a = 0.35 + 0.65 * (int(t * 6) % 2)
        ys, xs = np.nonzero(m)
        keep = (np.arange(len(ys)) < max(1, int(len(ys) * part)))
        for (yy, xx) in zip(ys[keep], xs[keep]):
            c = col * (1 - flare) + np.array([255, 255, 255]) * flare
            img[y0 + yy * SC:y0 + (yy + 1) * SC, x + xx * SC:x + (xx + 1) * SC] = \
                img[y0 + yy * SC:y0 + (yy + 1) * SC, x + xx * SC:x + (xx + 1) * SC] * (1 - a) + c * a
        x += 6 * SC
    return x


def glow_layer(img, cx, cy, rx, ry, col, k):
    yy, xx = np.mgrid[0:H, 0:W]
    g = np.clip(1 - np.hypot((xx - cx) / rx, (yy - cy) / ry), 0, 1) ** 1.6
    return img + col * g[:, :, None] * k


def heart_bg(t):
    img = C.view(HEART, 88, 300, zoom=3) * 0.55
    return glow_layer(img, 180, 390 + 30, 90, 80, np.array([255, 230, 180]), 0.4 + 0.15 * math.sin(t * 2))


RUNE_Y = [70, 150, 260, 370]


def runes_block(img, t, upto_line, t0s, flare=0.0, line4=None, end_shown=0.0):
    for li in range(upto_line):
        rows = wrap(LINES[li])
        shown = (t - t0s[li]) * 9
        y = RUNE_Y[li]
        for r in rows:
            n = len(r)
            x0 = (W - n * 6 * SC) // 2
            draw_runes(img, r, x0, y, shown, BLUE, flicker_last=(li == 3 and line4 == "flicker" and shown > n), t=t, flare=flare)
            shown -= n + 1
            y += 26
    if end_shown > 0:
        rows = wrap(END)
        y = RUNE_Y[3] + 2 * 26
        sh = end_shown
        for r in rows:
            x0 = (W - len(r) * 6 * SC) // 2
            draw_runes(img, r, x0, y, sh, GOLDC, flare=flare)
            sh -= len(r) + 1
            y += 26


def rune_glow(img):
    """Měkká záře kolem znaků (rozmazaná kopie světlých pixelů)."""
    m = (img.max(2) > 200).astype(np.float32)
    for _ in range(2):
        m = (np.roll(m, 1, 0) + np.roll(m, -1, 0) + np.roll(m, 1, 1) + np.roll(m, -1, 1) + m) / 5 * 1.6
    m = np.clip(m, 0, 1)
    return img + BLUE * m[:, :, None] * 0.35


def s1_runes(t, _):
    img = heart_bg(t) * 0.7
    runes_block(img, t, 3, [0.6, 4.4, 8.8])
    img = rune_glow(img)
    img = glow_layer(img, W / 2, 200, 220, 160, BLUE, 0.08 * min(1.0, t / 3))
    if t < 1.0:
        img = C.fade(img, 1 - t)
    return img


def s2_unfinished(t, _):
    img = heart_bg(t + 14) * 0.7
    runes_block(img, t + 14, 4, [0.6 - 14, 4.4 - 14, 8.8 - 14, 0.5], line4="flicker")
    img = rune_glow(img)
    return img


def s3_complete(t, _):
    img = heart_bg(t + 21) * 0.7
    flare = C.ease((t - 5.0) / 1.2)
    runes_block(img, t + 21, 4, [0.6 - 21, 4.4 - 21, 8.8 - 21, 0.5 - 7], flare=flare, end_shown=max(0.0, (t - 0.8) * 5))
    img = rune_glow(img)
    if t > 5.0:
        img = C.fade(img, (t - 5.0) / 2.0, (255, 252, 244))
    return img


SPARKS = rng.uniform(0, 1, (500, 4))


def sparks_up(img, t, speed=260, cx=None, spread=1.0, col=(255, 240, 200)):
    """Jiskry letí vzhůru z bodu dole (rozevřený vějíř), po celých pixelech."""
    cx = W / 2 if cx is None else cx
    life = (t * (0.5 + SPARKS[:, 0]) + SPARKS[:, 1]) % 1.6
    x = cx + (SPARKS[:, 2] - 0.5) * 2 * spread * life * 160
    y = H + 20 - life * speed * (0.7 + SPARKS[:, 3])
    a = np.clip(1 - life / 1.6, 0, 1)
    cc = np.where((SPARKS[:, 3] > 0.6)[:, None], np.array([150, 220, 255]), np.array(col))
    C.dots(img, x, y, cc, a)
    C.dots(img, x, y + 1, cc * 0.7, a * 0.6)


def s4_burst(t, _):
    if t < 3.5:                                                           # Střed: výbuch světla
        img = C.view(HEART, 88, 253 - 50 * C.ease(t / 3.5), zoom=2)
        img = glow_layer(img, W / 2, H * 0.7, 260 * (0.3 + t / 2), 260 * (0.3 + t / 2), np.array([255, 246, 220]), max(0.0, 1.0 - t / 2.5))
        sparks_up(img, t, speed=500, spread=1.6)
    else:                                                                 # kmen: jiskry stoupají podél kůry
        tt = t - 3.5
        cy = 700 - 400 * C.ease(tt / 4.5)
        img = C.view(TRUNK, 216, cy)
        sparks_up(img, tt * 1.3, speed=900, spread=0.8)
    if t < 0.3:
        img = C.fade(img, 1 - t / 0.3, (255, 252, 244))
    if t > 7.5:
        img = C.fade(img, (t - 7.5) / 0.5)
    return img


RAIN = rng.uniform(0, 1, (220, 3))


def spark_rain(img, t, k=1.0):
    y = (RAIN[:, 0] * H + t * (80 + RAIN[:, 1] * 120)) % (H + 40) - 20
    x = RAIN[:, 2] * W + np.sin(t * 2 + RAIN[:, 0] * 9) * 4
    C.dots(img, x, y, (255, 236, 190), np.full(len(x), 0.8 * k))
    C.dots(img, x, y - 1, (255, 200, 120), np.full(len(x), 0.4 * k))


BANG = np.zeros((9, 5, 4), np.float32)
for _y in list(range(0, 5)) + [6, 7]:
    BANG[_y, 2] = (255, 255, 255, 255)
BANG[:, :, :3][BANG[:, :, 3] > 0] = (255, 250, 230)


def bubble(img, x, y):
    img[y - 2:y + 12, x - 4:x + 9] = img[y - 2:y + 12, x - 4:x + 9] * 0.2 + np.array([30, 30, 40]) * 0.8
    C.blit(img, BANG, x, y)


def s5_meadow(t, _):
    img = C.view(MEADOW, 300, 640)
    walkers = [(FLORI64, 40, 300, 1), (AQULIN64, 250, 420, -1), (MYCIT64, 120, 600, 1)]
    for i, (spr, x0, y0, d) in enumerate(walkers):
        stop = 2.0 + i * 1.2
        x = x0 + d * 18 * min(t, stop)
        hop = 0 if t > stop else abs(int(math.sin(t * 9 + i) * 3))
        C.blit(img, spr if d > 0 else spr[:, ::-1], x, y0 - hop - 64)
        if t > stop:
            bubble(img, int(x + 28), y0 - 84)
    spark_rain(img, t)
    if t < 0.5:
        img = C.fade(img, 1 - t / 0.5)
    if t > 7.5:
        img = C.fade(img, (t - 7.5) / 0.5)
    return img


LANTERN = np.zeros((10, 7, 4), np.float32)
for _y in range(10):
    for _x in range(7):
        if _y == 0 and _x == 3 or (_y == 1 and 2 <= _x <= 4) or (2 <= _y <= 8 and _x in (1, 5)) or (_y in (2, 8) and 1 <= _x <= 5):
            LANTERN[_y, _x] = (40, 34, 30, 255)
        elif 3 <= _y <= 7 and 2 <= _x <= 4:
            LANTERN[_y, _x] = (255, 214, 120, 255) if (_x, _y) != (3, 5) else (255, 250, 220, 255)


def s6_kesh(t, _):
    cx, cy = 118, 200
    img = C.view(SIGNAL, cx, cy, zoom=3) * 0.8
    kx, ky = W // 2 - 32, 330
    C.blit(img, LUMEX, kx, ky)
    if t < 2.2:
        lx, ly = kx + 48, ky + 30
        C.blit(img, LANTERN.repeat(2, 0).repeat(2, 1), lx, ly)
        img = glow_layer(img, lx + 7, ly + 10, 50, 50, GOLDC, 0.35)
    else:                                                                 # lucerna vyklouzne a dopadne na prkna
        f = min(1.0, (t - 2.2) / 0.6)
        lx, ly = kx + 48 + 10 * f, ky + 30 + 46 * f * f
        L = LANTERN.repeat(2, 0).repeat(2, 1)
        if f >= 1:
            L = np.rot90(L).copy()
        C.blit(img, L, lx, ly)
        img = glow_layer(img, lx + 7, ly + 10, 40, 30, GOLDC, 0.25)
    if 1.6 < t < 2.4:                                                     # jiskra dopadne na Keshe
        img = glow_layer(img, kx + 32, ky + 20, 40, 40, np.array([255, 250, 230]), 0.8 * (1 - abs(t - 2.0) / 0.4))
    spark_rain(img, t, 0.5)
    if t < 0.5:
        img = C.fade(img, 1 - t / 0.5)
    if t > 7.5:
        img = C.fade(img, (t - 7.5) / 0.5)
    return img


def s7_brann(t, _):
    img = C.view(OZV, 282, 250, zoom=3) * 0.85
    bx, by = W // 2 - 64, 300
    big = IGNAR.repeat(2, 0).repeat(2, 1)
    shake = int(round(math.sin(t * 20))) if 1.5 < t < 4.5 else 0
    C.blit(img, big if int(t / 1.6) % 2 == 0 else big[:, ::-1], bx + shake, by)
    img = glow_layer(img, bx + 64, by + 70, 90, 90, GOLDC, 0.12)
    spark_rain(img, t, 0.4)
    if t < 0.5:
        img = C.fade(img, 1 - t / 0.5)
    if t > 5.5:
        img = C.fade(img, (t - 5.5) / 0.5)
    return img


TEARS = rng.uniform(0, 1, (12, 2))


def s8_cry(t, _):
    img = C.view(MEADOW, 200, 760)
    fx, fy = W // 2 - 64, 380
    big = FINLET.repeat(2, 0).repeat(2, 1)
    sob = int(round(abs(math.sin(t * 5)))) if t > 2 else 0
    C.blit(img, big, fx, fy + sob)
    if t > 2:                                                             # slzy
        for (a, b) in TEARS:
            ph = (t * 1.2 + a) % 1
            sx = fx + (22 if b < 0.5 else 40)
            sy = fy + 30 + ph * 26
            C.dots(img, [sx, sx], [sy, sy + 1], (150, 210, 255), np.array([1 - ph, 1 - ph]))
    for k in range(30):                                                   # tráva kolem se kývá
        gx = (k * 37) % W; gy = fy + 50 + (k % 3) * 4
        sw = int(round(math.sin(t * 2 + k)))
        C.dots(img, [gx + sw, gx + sw, gx], [gy, gy + 1, gy + 2], (90, 170, 70), np.ones(3))
    if t < 0.5:
        img = C.fade(img, 1 - t / 0.5)
    if t > 6.5:
        img = C.fade(img, (t - 6.5) / 0.5)
    return img


def lumen_figure(h):
    """Světelná postava Lumence (jako lidé se světlem v dlaních na první malbě): hlava, krk, ramena, rovný plášť,
    paže podél těla ohnuté k hrudi, v sepjatých dlaních světlo. Průsvitná, uvnitř světlejší, s modrým lemem."""
    w = int(h * 0.42)
    s = np.zeros((h, w, 4), np.float32)
    cx = w / 2
    m = np.zeros((h, w), int)                                             # 1 tělo, 2 paže
    for y in range(h):
        for x in range(w):
            u = x + 0.5 - cx
            if (u / (h * 0.075)) ** 2 + ((y - h * 0.1) / (h * 0.085)) ** 2 < 1:
                m[y, x] = 1                                               # hlava
            elif h * 0.18 < y < h * 0.23 and abs(u) < h * 0.03:
                m[y, x] = 1                                               # krk
            elif y >= h * 0.23 and abs(u) < h * 0.1 + min(1.0, (y - h * 0.23) / (h * 0.05)) * h * 0.02 + (y - h * 0.23) * 0.03:
                m[y, x] = 1                                               # ramena, trup a plášť (jen mírně rozšířený)
    for k in range(int(h * 0.22)):                                        # paže: od ramen dolů a dovnitř k hrudi
        f = k / (h * 0.22)
        for sd in (-1, 1):
            x = int(cx + sd * (h * 0.13 - f * h * 0.09)); y = int(h * 0.25 + f * h * 0.2)
            if 0 <= x < w:
                m[y, x] = 2; m[y, min(w - 1, x + 1)] = 2
    for y in range(h):
        for x in range(w):
            if m[y, x]:
                u = abs(x + 0.5 - cx) / (w / 2)
                k = 1 - u * 0.5
                s[y, x] = (120 + 100 * k, 180 + 60 * k, 255, 120 + 80 * k) if m[y, x] == 1 else (210, 236, 255, 230)
            elif any(0 <= y + dy < h and 0 <= x + dx < w and m[y + dy, x + dx] for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0))):
                s[y, x] = (90, 160, 255, 140)                              # modrý lem
    cy = int(h * 0.45)
    s[cy - 2:cy + 2, int(cx) - 2:int(cx) + 2] = (255, 248, 210, 255)       # světlo v dlaních
    return s


def lumen_from_hero(z=4):
    """Lumenec = člověk: silueta postavy hráče (chibi) ze světla – průsvitná modrobílá, uvnitř světlejší, s lemem
    a se světlem v dlaních. Na první pohled „člověk“, a přitom stejný styl jako hra."""
    h = C.hero("idle_s", 0)[18:40, 22:42]
    a = h[:, :, 3] > 0
    s = np.zeros(h.shape, np.float32)
    lumv = (h[:, :, 0] * 0.3 + h[:, :, 1] * 0.59 + h[:, :, 2] * 0.11) / 255
    s[a, 0] = 150 + 90 * lumv[a]; s[a, 1] = 200 + 50 * lumv[a]; s[a, 2] = 255; s[a, 3] = 190
    edge = np.zeros_like(a)
    edge[1:] |= a[:-1]; edge[:-1] |= a[1:]; edge[:, 1:] |= a[:, :-1]; edge[:, :-1] |= a[:, 1:]
    edge &= ~a
    s[edge] = (90, 160, 255, 150)
    s[17:19, 9:11] = (255, 248, 210, 255)                                 # světlo v dlaních
    return s.repeat(z, 0).repeat(z, 1)


FIG = lumen_from_hero(6)


def s9_reveal(t, _):
    if t < 4.5:                                                           # malba: lidé se světlem v dlaních se rozsvítí
        img = C.view(TEMPLE, 27, 60, zoom=6)
        for k in range(4):
            if t > 0.6 + k * 0.7:
                px = (20 + k * 4 - 27 + W / 12) * 6 + 3
                img = glow_layer(img, px, (73 - 60 + H / 12) * 6, 26, 26, np.array([150, 220, 255]), 0.9)
        if t > 4.0:
            img = C.fade(img, (t - 4.0) / 0.5)
        return img
    tt = t - 4.5                                                          # řada Makromonů a za každým jeho světelná postava
    img = np.zeros((H, W, 3), np.float32) + np.array([8, 12, 26])
    img = glow_layer(img, W / 2, H / 2, 300, 300, np.array([40, 60, 110]), 0.6)
    group = 0 if tt < 5.0 else 1                                          # dvě čtveřice po sobě
    for j in range(4):
        i = group * 4 + j
        spr = ROW[i].repeat(2, 0).repeat(2, 1)
        r, c = divmod(j, 2)
        x = 26 + c * 180; y = 170 + r * 290
        lt = tt - group * 5.0 - 0.4 - j * 0.35
        a = C.ease(lt / 1.0) - 0.6 * C.ease((lt - 2.4) / 1.0)          # Makromon se rozsvítí v člověka a zase vrátí
        mon_a = 1 - C.ease(lt / 1.0) + C.ease((lt - 2.4) / 1.0)
        if a > 0:
            img = glow_layer(img, x + 64, y + 64, 90, 90, BLUE, 0.25 * a)
            C.blit(img, FIG, x + 64 - FIG.shape[1] // 2, y + 128 - FIG.shape[0], alpha=min(1.0, a))
            if 0 < lt < 1.4:                                              # jiskry při proměně
                k = np.arange(24)
                C.dots(img, x + 64 + np.cos(k + lt * 3) * (20 + lt * 40), y + 64 + np.sin(k * 1.7 + lt * 2) * (20 + lt * 40),
                       (220, 240, 255), np.full(24, max(0.0, 1 - lt / 1.4)))
        C.blit(img, spr, x, y, alpha=max(0.0, min(1.0, mon_a)))
    if 4.6 < tt < 5.4:
        img = C.fade(img, 1 - abs(tt - 5.0) / 0.4)
    sparks_up(img, tt, speed=300, spread=2.0, col=(200, 230, 255))
    if tt < 0.5:
        img = C.fade(img, 1 - tt / 0.5)
    if tt > 9.0:
        img = C.fade(img, (tt - 9.0) / 0.5)
    return img


def s10_spirra(t, _):
    img = np.zeros((H, W, 3), np.float32) + np.array([10, 16, 34])
    img = glow_layer(img, W / 2, H * 0.45, 260, 300, np.array([60, 90, 150]), 0.5)
    k = np.arange(160)                                                    # noc plná stoupajících světel
    C.dots(img, (k * 53 + np.sin(t * 0.5 + k) * 10) % W, H - ((k * 31 + t * (12 + k % 7 * 5)) % H), (170, 220, 255),
           0.4 + 0.4 * (k % 3) / 2)
    hero = C.hero("idle_s", int(t * 4))
    z = 9 if t < 9 else (5 if t < 12.5 else 3)
    big = hero.repeat(z, 0).repeat(z, 1)
    hx, hy = W // 2 - 32 * z, int(H * 0.64) - 38 * z
    C.blit(img, big, hx, hy)
    climb = C.ease((t - 0.5) / 2.5)
    zs = max(1, z // 3)
    sp = SPIRRA32.repeat(zs, 0).repeat(zs, 1)
    sx = hx + 37 * z - 4 + int(10 * z * (1 - climb))
    sy = hy + 35 * z - sp.shape[0] + int(10 * z * (1 - climb))
    C.blit(img, sp, sx, sy)
    if 4.5 < t < 8.5:                                                     # šepot: světélka od Spirry k uchu
        for j in range(6):
            ph = ((t - 4.5) * 0.8 + j / 6) % 1
            C.dots(img, [sx - ph * 3 * z], [sy + sp.shape[0] * 0.3 - math.sin(ph * 3.14) * 3 * z], (255, 240, 200), np.array([1 - ph]))
    for tc in (9.0, 12.5):
        if tc - 0.25 < t < tc + 0.25:
            img = C.fade(img, 1 - abs(t - tc) / 0.25)
    if t < 0.6:
        img = C.fade(img, 1 - t / 0.6)
    if t > 13.0:
        img = C.fade(img, (t - 13.0) / 3.0, (200, 226, 255))
    return img


SHOTS = [(0, 14, s1_runes), (14, 21, s2_unfinished), (21, 28, s3_complete), (28, 36, s4_burst), (36, 44, s5_meadow),
         (44, 52, s6_kesh), (52, 58, s7_brann), (58, 65, s8_cry), (65, 79, s9_reveal), (79, 95, s10_spirra)]
CAPS = [
    Caption(1.0, 4.2, "AŤ NÁS NIKDO NENAJDE.", cps=14),
    Caption(4.6, 8.6, "AŤ ZAPOMENE KAŽDÝ, KDO NÁM UBLÍŽIL.", cps=14),
    Caption(9.0, 13.6, "AŤ UŽ SE NIKDY NEMUSÍME BÁT.", cps=14),
    Caption(14.6, 20.6, "A pod tím ještě jeden řádek, nedopsaný. Lumira umřela uprostřed věty: A AŽ SE UŽ NEBUDEME BÁT…"),
    Caption(21.4, 27.4, "Dopovíš to za ni. Nahlas, do ticha, kde nepadá popel: „…ať si vzpomeneme.“"),
    Caption(28.6, 35.6, "Světlo se rozletí. Nahoru kořeny, kmenem, puklinami do světa – jako jiskry od ohně."),
    Caption(36.6, 43.6, "A všude, kam dopadne, se nějaký Makromon zastaví uprostřed kroku."),
    Caption(46.4, 51.6, "Kesh na útesu upustí lucernu. „Asko,“ řekne. „Jmenoval jsem se Asko.“"),
    Caption(52.6, 57.6, "V Ozvěně se Brann zadívá na svoje ruce."),
    Caption(58.6, 64.6, "Na louce se malý Makromon v trávě rozpláče, protože si vzpomněl na mámu."),
    Caption(66.0, 71.6, "Makromoni byli lidé. Lumenci – drobný světélkující národ, který si kdysi přál, aby je nikdo nenašel."),
    Caption(72.0, 78.6, "Celou dobu byli s tebou: v trávě, v jeskyních, ve tvém batohu. Teď se pamatují. A nikdo jim už neublíží."),
    Caption(80.0, 87.6, "Spirra se ti vyšplhá na rameno a dlouho se na tebe dívá."),
    Caption(88.0, 93.6, "Pak ti do ucha zašeptá tvoje jméno. Ne číslo. Jméno."),
]

if __name__ == "__main__":
    a = sys.argv[1:]
    s0 = float(a[a.index("--from") + 1]) if "--from" in a else 0.0
    s1 = float(a[a.index("--to") + 1]) if "--to" in a else None
    audio = os.path.join(B, "ending_prani.m4a")
    out = os.path.join(B, "ending_prani_preview.mp4") if "--preview" in a else os.path.join(C.ROOT, "app", "src", "main", "res", "raw", "ending_prani.mp4")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    C.render(SHOTS, CAPS, out, audio=audio if os.path.exists(audio) and "--preview" not in a else None, start=s0, end=s1)
    print("ok", out)

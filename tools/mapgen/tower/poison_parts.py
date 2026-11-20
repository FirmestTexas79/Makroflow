"""
Jedová laboratoř – 12. patro Věže Řádu Váhy (typ JED). Zarostlý skleník s laboratoří, kde Řád vaří spánek
a zapomnění: železné rámy se zamlženým sklem, za nímž tlačí liány, mechem prorostlé dlaždice s louží jedu
(setkání), velký měděný destilační přístroj s hadem a baňkou ZAPOMNĚNÍ, kádě s bublající zelenou kaší, obří
masožravka, záhony rulíku, bolehlavu a fialových hub se jmenovkami jako na hrobech, pracovní stůl s dávkami
SPÁNEK, plynové masky (jedna chybí). Ve 3/4, objemově, obrys selout.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
from pix import Spr, c  # noqa: E402
from figures import sphere, cylinder, selout, blobs, _pick, BRASS, PAPER, INK, STEEL, RED, IRON_, shade_px  # noqa: E402

SLATE = [c(0x0E1214), c(0x161C1E), c(0x1E2628), c(0x283234), c(0x343E40), c(0x465254)]
MOSSG = [c(0x14280E), c(0x1E3A14), c(0x2C5218), c(0x3E6C22)]
TOX = [c(0x0E3A12), c(0x1E6A1A), c(0x3AA024), c(0x6AD838), c(0xA8F070), c(0xE0FFB0)]
VIOLET = [c(0x1E0A2A), c(0x341448), c(0x50226A), c(0x70368E), c(0x9A58B8), c(0xC890E0)]
COPPER = [c(0x3A160A), c(0x6A2C12), c(0x9A4A1E), c(0xC8702E), c(0xE89A50), c(0xFCC890)]
WOODD = [c(0x1A100A), c(0x2C1C10), c(0x40281A), c(0x583824), c(0x704A30)]
GLASSG = [c(0x1A3A34), c(0x2E5A50), c(0x5A8A7C), c(0x9AC4B4), c(0xE0F4EC)]
BRICKM = [c(0x22140E), c(0x341E16), c(0x48281C), c(0x5C3424)]
SOILD = [c(0x140C08), c(0x22160E), c(0x302016), c(0x40301E)]
PINKF = [c(0x5A1424), c(0x8E2A40), c(0xC8506A), c(0xF08AA0)]


def _h(x, y, s=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (s * 83492791)) & 0xFFFF


def _mix(a, b, t):
    out = a.copy(); out[:3] = (a[:3] * (1 - t) + b[:3] * t).astype(np.uint8); return out


def lab_floor(img, x0, y0, x1, y1):
    """Břidlicové dlaždice 12 × 12 (každá jiný tón, okosená hrana), ve spárách mech, praskliny, zelené skvrny
    po rozlitém jedu."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx, ly = (x - x0) % 12, (y - y0) % 12
            tx, ty = (x - x0) // 12, (y - y0) // 12
            k = 2 + _h(tx, ty, 1) % 3
            col = SLATE[k]
            if lx == 0 or ly == 0:
                col = MOSSG[1] if _h(x, y, 2) % 3 else MOSSG[2]
            elif lx == 1 or ly == 1:
                col = SLATE[min(5, k + 1)]
            elif lx == 11 or ly == 11:
                col = SLATE[k - 1]
            if _h(tx, ty, 4) % 7 == 0 and abs(lx - ly) < 1 and 2 < lx < 10:
                col = SLATE[0]                                                         # prasklina
            n = math.sin(x * 0.11 + y * 0.07) + math.sin(x * 0.05 - y * 0.13 + 2)
            if n > 1.45 and col is not MOSSG[1] and (x + y) % 2:
                col = _mix(col, TOX[1], 0.45)                                           # skvrny jedu
            img[y, x] = col


def _vn(x, y, sc, seed):
    """Hladký hodnotový šum 0..1 (bilineárně interpolované náhodné hodnoty v mřížce [sc])."""
    gx, gy = x / sc, y / sc
    x0, y0 = int(math.floor(gx)), int(math.floor(gy))
    fx, fy = gx - x0, gy - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    v = lambda a, b: (_h(a, b, seed) % 1000) / 1000
    return (v(x0, y0) * (1 - fx) + v(x0 + 1, y0) * fx) * (1 - fy) + (v(x0, y0 + 1) * (1 - fx) + v(x0 + 1, y0 + 1) * fx) * fy


PUDDLE_KINDS = {
    "fresh": dict(rim=TOX[1], body=(TOX[2], TOX[3], TOX[4]), hi=TOX[5], halo=0.55),           # čerstvě rozlitý, svítí
    "old": dict(rim=c(0x6A6A1A), body=(c(0x1E4A14), TOX[1], TOX[2]), hi=TOX[4], halo=0.65),   # stará s rezavou krustou
    "film": dict(rim=TOX[1], body=(TOX[1], TOX[2], TOX[2]), hi=TOX[4], halo=0.3),            # tenký povlak, prosvítá dlažba
    "violet": dict(rim=VIOLET[2], body=(VIOLET[2], VIOLET[3], VIOLET[4]), hi=VIOLET[5], halo=0.5),
}


def toxic_puddle(img, cx, cy, rx, ry, seed=0, kind="fresh", drops=2, ang=0.0):
    """Louže jedu: zploštělá elipsa rozbitá hladkým šumem (zálivy, výběžky, ostrůvky suché dlažby), okraj
    vyhlazený, aby nebyl zubatý. Kapalina je průsvitná – prosvítá dlažba i spáry. Kolem tmavý mokrý lem (nasáklá dlažba), na okraji meniskus, uvnitř
    hlubší tmavší místo, odraz světla jako měkká skvrna a jiskřičky, plovoucí pěna, kolem satelitní kapky.
    [kind] = fresh / old / film / violet; [ang] = kterým směrem se louže rozlévá."""
    K = PUDDLE_KINDS[kind]
    rng = np.random.default_rng(seed)
    H, W = img.shape[:2]
    ca, sa = math.cos(ang), math.sin(ang)
    def field(x, y):                                                                # >0 = louže (1 + hloubka)
        dx, dy = x + .5 - cx, y + .5 - cy
        u, v = (dx * ca + dy * sa) / rx, (-dx * sa + dy * ca) / ry
        d = math.hypot(u, v)
        n = _vn(x, y, 6.5, seed) * 0.72 + _vn(x, y, 3.2, seed + 7) * 0.28
        return 1.0 + ((1.0 - d) + (n - 0.5) * 0.8) * 2.2
    R = int(max(rx, ry) * 2) + 3
    y0_, x0_ = max(0, int(cy - R)), max(0, int(cx - R))
    F = np.array([[field(x, y) for x in range(x0_, min(W, int(cx + R)))] for y in range(y0_, min(H, int(cy + R)))])
    inside = F >= 1.0
    p_ = np.pad(inside, 1)
    cnt = sum(p_[1 + dy:p_.shape[0] - 1 + dy, 1 + dx:p_.shape[1] - 1 + dx].astype(int) for dy in (-1, 0, 1) for dx in (-1, 0, 1))
    smooth = cnt >= 5                                                                # vyhlazení zubatých pixelů
    for y in range(y0_, min(H, int(cy + R))):
        for x in range(x0_, min(W, int(cx + R))):
            f = F[y - y0_, x - x0_]
            if smooth[y - y0_, x - x0_] and f < 1.0:
                f = 1.05
            elif not smooth[y - y0_, x - x0_] and f >= 1.0:
                f = 0.99
            if f >= 1.0:
                depth = min(1.0, (f - 1.0) / 2.2)
                tint = K["body"][0] if depth < 0.18 else K["body"][1] if depth < 0.55 else K["body"][2]
                alpha = (0.5 if kind == "film" else 0.68) + depth * 0.22                 # průsvitná: prosvítá dlažba a spáry
                col = _mix(img[y, x], tint, min(0.95, alpha))
                if f < 1.12:
                    col = K["rim"]
                elif f < 1.35 and (x - cx) + (y - cy) * 0.6 < -rx * 0.2:
                    col = K["hi"]                                                       # meniskus na světlé straně
                hx, hy = cx - rx * 0.28, cy - ry * 0.3                                  # měkký odraz
                q = ((x + .5 - hx) / (rx * 0.38)) ** 2 + ((y + .5 - hy) / (ry * 0.22)) ** 2
                if q < 1 and depth > 0.2:
                    col = K["hi"] if q < 0.4 else _mix(col, K["hi"], 0.5)
                img[y, x] = col
            elif f >= 1.0 - K["halo"] * 0.5:
                k = (f - (1.0 - K["halo"] * 0.5)) / (K["halo"] * 0.5)
                if k > 0.5 or (x + y) % 2:
                    img[y, x] = _mix(img[y, x], c(0x050806), 0.35 * k + 0.15)          # mokrý lem
    for _ in range(int(rng.integers(2, 5))):                                            # pěna / bublinky
        a, r = rng.uniform(0, 2 * math.pi), rng.uniform(0.15, 0.5)
        X, Y = int(cx + math.cos(a) * rx * r), int(cy + math.sin(a) * ry * r)
        if 0 <= Y < H - 1 and 0 <= X < W - 1 and field(X, Y) > 1.6:
            img[Y, X] = K["hi"]; img[Y, X + 1] = _mix(K["hi"], K["body"][1], 0.5)
            if kind == "old":
                img[Y + 1, X] = c(0x8A7A2A)
    for _ in range(drops):                                                              # satelitní kapky
        a = ang + rng.uniform(-0.9, 0.9) + (math.pi if rng.random() < 0.4 else 0)
        r = rng.uniform(1.12, 1.45)
        X, Y = int(cx + math.cos(a) * rx * r), int(cy + math.sin(a) * ry * r)
        sz = int(rng.integers(0, 2))
        for dy in range(-sz, sz + 1):
            for dx in range(-sz - 1, sz + 2):
                if 0 <= Y + dy < H and 0 <= X + dx < W and abs(dx) + abs(dy) <= sz + 1:
                    img[Y + dy, X + dx] = K["body"][1]
        if 0 <= Y < H and 0 <= X < W:
            img[Y, X] = K["hi"] if sz else K["body"][2]


def greenhouse_wall(W, H, door=(62, 115)):
    """Zadní stěna = skleník: nízká mechem prorostlá cihlová podezdívka, nad ní železné rámy s tabulemi
    zamlženého skla (nahoře oblouky), za sklem noc a tlačící se liány s listy, kapky kondenzace, jedna tabule
    prasklá. Světlé odlesky na skle."""
    s = Spr(W, H)
    knee = H - 28
    for y in range(H):
        for x in range(W):
            if y >= knee:
                ly = y - knee; row = ly // 4; off = 4 if row % 2 else 0
                lx = (x + off) % 8
                col = BRICKM[1 + _h((x + off) // 8, row, 3) % 3] if not (lx == 0 or ly % 4 == 3) else SOILD[0]
                if ly < 3:
                    col = IRON_[3] if ly == 0 else IRON_[1]
                if _h(x // 2, y // 2, 5) % 5 == 0 and ly > 6:
                    col = MOSSG[2] if (x + y) % 2 else MOSSG[1]
                s.px(x, y, col)
            else:
                t = y / knee
                col = _mix(c(0x0A1810), c(0x14301C), t)
                pane_x = x % 22
                if pane_x in (0, 1) or y % 30 in (0, 1) or y < 3:
                    col = IRON_[2] if pane_x == 0 or y % 30 == 0 or y < 2 else IRON_[0]
                else:
                    v = math.sin(x * 0.21 + y * 0.09) + math.sin(x * 0.07 - y * 0.18 + 1)          # liány za sklem
                    if v > 0.9:
                        col = _mix(col, MOSSG[2], 0.6) if (x + y) % 2 else _mix(col, MOSSG[3], 0.5)
                    if _h(x // 3, y // 3, 7) % 9 == 0 and v > 0.4:
                        col = MOSSG[3]
                    if (pane_x + y) % 23 == 0 and pane_x < 8:
                        col = _mix(col, c(0xFFFFFF), 0.35)                              # odlesk
                    if _h(x, y // 3, 9) % 41 == 0:
                        col = GLASSG[3]                                                 # kapky kondenzace
                    col = _mix(col, GLASSG[2], 0.12)                                    # zamlžení
                s.px(x, y, col)
    for (px_, py_) in ((30, 22), (150, 40)):                                            # praskliny
        for k in range(5):
            a = k * 1.3
            for t in range(8):
                s.px(px_ + math.cos(a) * t, py_ + math.sin(a) * t, GLASSG[4] if t < 3 else GLASSG[3])
    return s


def alembic():
    """Destilační přístroj (hrdina patra): cihlová pícka se zelenomodrým plamínkem, na ní velký měděný kotel
    s nýty a kopulí, z kopule labutí krk k chladicímu hadu v dřevěném sudu, z hadu kape do baňky se svítivě
    zelenou tekutinou na stojanu; na kotli teploměr a ventil."""
    W, H = 66, 70
    s = Spr(W, H)
    bx, by = 20, H - 2                                                                  # pícka
    for y in range(H - 18, H):
        for x in range(bx - 15, bx + 16):
            ly = y - (H - 18); off = 3 if (ly // 3) % 2 else 0
            col = BRICKM[2] if (x + off) % 6 and ly % 3 != 2 else BRICKM[0]
            if x > bx + 10:
                col = BRICKM[1] if col is BRICKM[2] else BRICKM[0]
            s.px(x, y, col)
    for y in range(H - 12, H - 4):                                                      # ohniště
        for x in range(bx - 6, bx + 7):
            d = abs(x - bx) / 6
            col = c(0x0A0A0A)
            if y > H - 10 + d * 3:
                col = c(0x6AF0D0) if (x + y) % 3 else c(0x2A9A8A)
            s.px(x, y, col)
    cy0, cy1 = H - 44, H - 18                                                           # kotel
    for y in range(cy0, cy1 + 1):
        t = (y - cy0) / (cy1 - cy0)
        hw = 13 + 3 * math.sin(t * math.pi)
        for x in range(int(bx - hw), int(bx + hw) + 1):
            u = (x - bx) / hw
            band = 0 if u > 0.7 else 1 if u > 0.3 else 2 if u > -0.2 else 3 if u > -0.6 else 4
            col = COPPER[[1, 2, 3, 4, 3][band]]
            if u < -0.75:
                col = COPPER[2]
            if (y - cy0) % 9 == 0 and int(x) % 3 == 0:
                col = COPPER[4]                                                         # nýty
            s.px(x, y, col)
    sphere(s, bx, cy0, 12, 7, COPPER, amb=0.3, mask=lambda x, y: y <= cy0)              # kopule
    cylinder(s, bx - 2, bx + 2, cy0 - 12, cy0 - 5, COPPER[1:], amb=0.3)
    nx, ny = bx, cy0 - 12                                                               # labutí krk
    for k in range(0, 34):
        t = k / 33
        X = nx + t * 30
        Y = ny - math.sin(t * math.pi) * 6 + t * 10
        for o in range(-1, 2):
            s.px(X, Y + o, COPPER[4] if o < 0 else COPPER[3] if o == 0 else COPPER[1])
    sx = 52                                                                             # sud s hadem
    _barrel(s, sx, H - 34, H - 11, 9.5, 2.6, WOODD, hoops=(3, 17), hoop=[IRON_[1], IRON_[2], IRON_[3]], staves=6)
    for x in range(sx - 9, sx + 10):
        u = (x + .5 - sx) / 9.5
        nz = math.sqrt(max(0, 1 - u * u))
        for y in range(int(H - 34 - 2.6 * nz), int(H - 34 + 2.6 * nz) + 1):
            s.px(x, y, c(0x1A3A34) if abs(u) < 0.8 else COPPER[3])
    for k in range(3):                                                                  # závity hadu nad sudem
        for a10 in range(0, 63):
            a = a10 / 10
            s.px(sx + math.cos(a) * 7, H - 36 - k * 3 + math.sin(a) * 2, COPPER[4] if math.sin(a) > 0 else COPPER[2])
    for y in range(H - 10, H - 4):                                                      # výpust + kapka
        s.px(sx - 10, y if y < H - 8 else H - 8, COPPER[3])
    fx_, fy = sx - 13, H - 8                                                            # baňka
    for y in range(fy - 10, fy + 1):
        for x in range(fx_ - 5, fx_ + 6):
            d = math.hypot(x + .5 - fx_, (y + .5 - (fy - 4)) * 1.1)
            if d <= 5 or (abs(x - fx_) <= 1 and y < fy - 7):
                col = GLASSG[3] if d > 4.2 else TOX[4] if y > fy - 5 else GLASSG[1]
                if x < fx_ - 2 and y < fy - 4:
                    col = GLASSG[4]
                s.px(x, y, col)
    for x in range(fx_ - 6, fx_ + 7):
        s.px(x, fy + 1, IRON_[2])
    gx, gy = bx - 9, cy0 + 10                                                           # teploměr
    for y in range(gy - 3, gy + 4):
        for x in range(gx - 3, gx + 4):
            if math.hypot(x - gx, y - gy) <= 3:
                s.px(x, y, BRASS[2] if math.hypot(x - gx, y - gy) > 2.2 else PAPER[3])
    s.px(gx, gy, RED[3]); s.px(gx + 1, gy - 1, RED[3])
    return selout(s, 0.48, 0.82)


def _barrel(s, cx, top, bot, rx, ry, ramp, hoops=(), hoop=None, staves=9):
    """Válcové těleso ve 3/4 (sud, káď, plechovka): spodek a obruče jsou oblouky elipsy (ne rovná čára), tón podle
    natočení plochy, švy dužin se sbíhají k okrajům, pod spodkem tmavá hrana. [hoops] = výšky obručí od [top]."""
    for x in range(int(cx - rx), int(cx + rx) + 1):
        u = (x + .5 - cx) / rx
        if abs(u) > 1:
            continue
        nz = math.sqrt(1 - u * u)
        y0, y1 = top + ry * nz, bot + ry * nz
        band = 0 if u < -0.65 else 1 if u < -0.2 else 2 if u < 0.35 else 3 if u < 0.75 else 4
        base = ramp[[3, 4, 3, 2, 1][band]]
        seam = int((math.asin(u) / math.pi + 0.5) * staves * 2)
        seam_prev = int((math.asin(max(-1, u - 1 / rx)) / math.pi + 0.5) * staves * 2)
        for y in range(int(y0), int(y1) + 1):
            col = base
            if seam != seam_prev:
                col = ramp[0]
            if hoop is not None:
                for hy in hoops:
                    yy = top + hy + ry * nz
                    if abs(y - yy) < 0.6:
                        col = hoop[2] if u < -0.2 else hoop[1] if u < 0.5 else hoop[0]
                    elif abs(y - yy - 1) < 0.6:
                        col = hoop[0]
            s.px(x, y, col)
        s.px(x, int(y1) + 1, ramp[0])


def vat(seed=0):
    """Káď: dřevěné dužiny se zaobleným spodkem a obloukovými železnými obručemi, nahoře hladina bublající zelené
    kaše (elipsa) s pěnou a vynořeným listem, přes okraj stékají slizké pramínky, opřené míchací pádlo."""
    W, H = 42, 48
    s = Spr(W, H)
    cx, rx, ry = 21, 17, 5.5
    top, bot = 19, H - 8
    _barrel(s, cx, top, bot, rx, ry, WOODD, hoops=(6, 18), hoop=[IRON_[1], IRON_[2], IRON_[3]], staves=8)
    for y in range(int(top - ry) - 1, int(top + ry) + 2):                               # okraj + hladina
        for x in range(cx - rx - 1, cx + rx + 2):
            d = ((x + .5 - cx) / (rx + 0.5)) ** 2 + ((y + .5 - top) / (ry + 0.5)) ** 2
            if d <= 1:
                if d > 0.72:
                    col = WOODD[4] if y < top else WOODD[2]
                    if d > 0.92:
                        col = WOODD[1] if y > top else WOODD[3]
                else:
                    n = _vn(x, y, 3, seed)
                    col = TOX[3] if n < 0.55 else TOX[4]
                    if d > 0.55 and y < top:
                        col = TOX[2]                                                    # stín pod okrajem
                s.px(x, y, col)
    for (fx_, fy) in ((cx - 7, top - 1), (cx + 5, top + 1), (cx + 1, top - 3), (cx - 2, top + 2)):   # pěna
        s.px(fx_, fy, TOX[5]); s.px(fx_ + 1, fy, TOX[5]); s.px(fx_, fy + 1, TOX[4])
    for k in range(5):                                                                  # list na hladině
        s.px(cx + 6 + k * 0.6, top - 2 + k * 0.3, MOSSG[3] if k < 3 else MOSSG[2])
    for (px_, ln) in ((cx + 12, 13), (cx - 13, 8)):                                     # stékající sliz
        nz = math.sqrt(max(0, 1 - ((px_ - cx) / rx) ** 2))
        for y in range(int(top + ry * nz), int(top + ry * nz) + ln):
            s.px(px_, y, TOX[3] if y < top + ry * nz + ln - 3 else TOX[2])
        s.px(px_, int(top + ry * nz) + ln, TOX[4])
    s.line(cx - 10, top - 1, cx - 17, top - 15, WOODD[3], 2)                           # pádlo
    for y in range(top - 19, top - 13):
        s.px(cx - 19, y, WOODD[4]); s.px(cx - 18, y, WOODD[3])
    return selout(s, 0.5, 0.85)


def _jaw(s, cx, cy, rx, ry, tilt):
    """Jedna past masožravky: dva laloky (horní a dolní) rozevřené jako mušle – zvenku zelené, uvnitř růžové
    s tečkami, po okraji světlé ostny."""
    for lobe in (-1, 1):
        oy = cy + lobe * ry * 0.55
        for y in range(int(oy - ry), int(oy + ry) + 1):
            for x in range(int(cx - rx), int(cx + rx) + 1):
                u, v = (x + .5 - cx) / rx, (y + .5 - oy) / ry
                if u * u + v * v > 1 or v * lobe < -0.05:
                    continue
                X = x + (y - cy) * tilt
                inner = u * u + v * v < 0.62
                if inner:
                    col = PINKF[2] if (x + y) % 5 else PINKF[3]
                    if lobe > 0:
                        col = PINKF[1] if (x + y) % 5 else PINKF[2]
                else:
                    col = MOSSG[3] if lobe < 0 else MOSSG[2]
                s.px(X, y, col)
        for k in range(0, int(rx * 2) + 1, 2):                                     # ostny po okraji
            x = cx - rx + k
            u = (x - cx) / rx
            y = oy + lobe * ry * math.sqrt(max(0, 1 - u * u)) + lobe
            s.px(x + (y - cy) * tilt, y, c(0xD8F0A0))


def flytrap():
    """Obří masožravka v kameninovém květináči: dva stonky, na každém otevřená past (laloky jako mušle,
    uvnitř růžové, po okraji ostny), dole drobné listy. Sklapnutí oživuje InteriorFxView (traps)."""
    W, H = 40, 44
    s = Spr(W, H)
    cx = 20
    s.line(cx - 1, H - 13, cx - 9, H - 30, MOSSG[2], 2)
    s.line(cx + 1, H - 13, cx + 10, H - 26, MOSSG[2], 2)
    _jaw(s, cx - 10, H - 33, 8, 5, -0.15)
    _jaw(s, cx + 11, H - 28, 7, 4.5, 0.15)
    POT = [c(0x2E1810), c(0x4E2E20), c(0x6E4430), c(0x8A5A3A), c(0xA87048)]          # květináč se zaobleným dnem
    _barrel(s, cx, H - 12, H - 4, 10, 2.6, POT, staves=1)
    for x in range(cx - 10, cx + 11):
        u = (x + .5 - cx) / 10
        nz = math.sqrt(max(0, 1 - u * u))
        for y in range(int(H - 12 - 2.6 * nz), int(H - 12 + 2.6 * nz) + 1):
            s.px(x, y, POT[4] if abs(u) > 0.82 or y < H - 12 - 1.6 * nz else SOILD[2])
    for (lx, ly, d) in ((cx - 9, H - 13, -1), (cx + 8, H - 13, 1), (cx - 3, H - 14, -1), (cx + 3, H - 14, 1)):
        for k in range(5):
            s.px(lx + d * k, ly - k * 0.6, MOSSG[3] if k < 3 else MOSSG[2])
    return selout(s, 0.5, 0.85)


def poison_bed(seed=0, w=50, h=40):
    """Truhlík na jedovaté houby ve 3/4: trámkový rám s okovanými rohy, vidět horní plochu vlhké hlíny
    (s mechem), čelní prkna a tmavý bok. V hlíně rostou fialové svítící zvonovité houby různé velikosti
    (trsy), bledé kloboučky, rulík s černými bobulemi vzadu a zapíchnuté jmenovky jako na hrobech."""
    s = Spr(w + 5, h)
    rng = np.random.default_rng(seed)
    soil_t, soil_b = h - 20, h - 12                                                     # horní plocha hlíny
    for y in range(soil_t, h):
        for x in range(0, w):
            if y < soil_b:
                e = min(x, w - 1 - x, y - soil_t)
                if e < 2:
                    col = WOODD[4] if y == soil_t or x < 2 else WOODD[3]
                else:
                    col = SOILD[2] if _vn(x, y, 3, seed) > 0.45 else SOILD[1]
                    if _vn(x, y, 4, seed + 3) > 0.75:
                        col = MOSSG[1] if (x + y) % 2 else MOSSG[2]
            else:                                                                      # čelo z prken
                ly = y - soil_b
                col = WOODD[3] if ly < 1 else WOODD[2] if ly < 4 else WOODD[1] if ly < 7 else WOODD[0]
                if ly == 4:
                    col = WOODD[0]
                if x in (0, 1, w - 2, w - 1) or (ly in (1, 5) and x in (2, w - 3)):
                    col = IRON_[3] if x < 2 else IRON_[1]                               # okování rohů
            s.px(x, y, col)
        for x in range(w, w + 5):                                                       # bok
            if soil_t <= y and y - soil_t >= x - w and y < h:
                s.px(x, y, WOODD[1] if y < soil_b else WOODD[0])
    for k in range(4):                                                                  # rulík vzadu
        x = 5 + k * 13 + int(rng.integers(-2, 3))
        ht = int(rng.integers(12, 17))
        s.vline(x, soil_t - ht, soil_t + 2, MOSSG[2])
        for j in range(4):
            yy = soil_t - ht + 2 + j * 3
            d = -1 if j % 2 else 1
            s.px(x + d, yy, MOSSG[3]); s.px(x + 2 * d, yy + 1, MOSSG[3]); s.px(x + d, yy + 1, MOSSG[2])
        s.px(x + 2, soil_t - ht + 3, VIOLET[4]); s.px(x + 2, soil_t - ht + 4, VIOLET[3])
        s.px(x - 2, soil_t - ht + 7, c(0x101014)); s.px(x - 1, soil_t - ht + 8, c(0x2E2E40)); s.px(x - 2, soil_t - ht + 9, c(0x101014))
    shrooms = [(int(rng.integers(5, w - 5)), int(rng.integers(soil_t + 2, soil_b - 1)), rng.uniform(1.6, 4.2)) for _ in range(9)]
    shrooms.sort(key=lambda t: t[1])
    for (x, y, r) in shrooms:                                                           # zvonovité houby
        stem_h = int(r * 1.6) + 2
        for yy in range(y - stem_h, y + 1):
            s.px(x, yy, c(0xD8C8E8) if yy < y - 1 else c(0xA898B8))
            if r > 3:
                s.px(x + 1, yy, c(0xB8A8C8))
        cy = y - stem_h
        for yy in range(int(cy - r * 0.9), cy + 2):
            t = (yy - (cy - r * 0.9)) / (r * 0.9 + 2)
            hw = r * (0.45 + 0.55 * t)
            for xx in range(int(x - hw), int(x + hw) + 1):
                u = (xx - x) / max(0.5, hw)
                col = VIOLET[5] if u < -0.35 and t < 0.7 else VIOLET[4] if u < 0.3 else VIOLET[3]
                if yy == cy + 1:
                    col = VIOLET[2]
                s.px(xx, yy, col)
        if r > 2.5:
            s.px(x - 1, cy - 1, c(0xF0E0FF))
    for k in range(3):                                                                  # jmenovky
        x = 8 + k * 17
        for y in range(soil_t - 4, soil_t + 4):
            s.px(x, y, c(0xC8C0B0) if y > soil_t - 3 else c(0xE4DCCC))
            s.px(x + 1, y, c(0xA8A090) if y > soil_t - 3 else c(0xC8C0B0))
        s.px(x, soil_t - 1, INK)
    return selout(s, 0.5, 0.85)


def workbench():
    """Pracovní stůl: tmavé dřevo, hmoždíř s fialovým práškem, stojánek s lahvičkami (zelená, fialová,
    jantarová), lékárnické váhy, otevřený zápisník, řada lahviček se štítky dávek a jedna prázdná."""
    W, H = 58, 32
    s = Spr(W + 5, H)
    top = 12
    for y in range(top, top + 7):
        for x in range(0, W):
            s.px(x, y, WOODD[4] if y == top else WOODD[3] if (x + y * 5) % 11 else WOODD[2])
    for y in range(top + 7, H):
        for x in range(0, W):
            col = WOODD[2] if y == top + 7 else WOODD[1]
            if 3 < x < W - 4 and top + 9 < y < H - 4:
                col = WOODD[2] if (x // 14) % 2 else WOODD[1]
            if y > top + 13 and x not in (1, 2, W - 3, W - 2):
                col = (0, 0, 0, 0)
            if not (len(col) == 4 and col[3] == 0 if isinstance(col, tuple) else False):
                s.px(x, y, col)
        for x in range(W, W + 5):
            if top <= y <= top + 9 and y - top >= x - W:
                s.px(x, y, WOODD[0])
    sphere(s, 8, top - 1, 4, 2.6, [c(0x6E6A66), c(0x8E8A84), c(0xB2ADA6), c(0xD4D0C8)], amb=0.4, mask=lambda x, y: y >= top - 3)
    for x in range(5, 12):
        s.px(x, top - 3, VIOLET[4] if x % 2 else VIOLET[3])
    s.line(10, top - 4, 14, top - 9, c(0xB2ADA6), 2)
    for x in range(16, 30):                                                             # stojánek s lahvičkami
        s.px(x, top - 1, WOODD[4])
    for k, col in enumerate((TOX[3], VIOLET[4], c(0xE0A030), TOX[4])):
        x = 18 + k * 3
        for y in range(top - 7, top - 1):
            s.px(x, y, col if y > top - 5 else GLASSG[3])
        s.px(x, top - 8, c(0x8A6A3A))
    for x in range(33, 41):                                                             # váhy
        s.px(x, top - 6, BRASS[3])
    s.vline(37, top - 6, top - 1, BRASS[2])
    s.px(33, top - 5, BRASS[2]); s.px(40, top - 5, BRASS[2])
    for y in range(top + 1, top + 5):                                                   # zápisník
        for x in range(42, 54):
            s.px(x, y, PAPER[3] if x != 48 else PAPER[0])
            if y % 2 == 0 and x % 3 and x != 48:
                s.px(x, y, INK)
    for k in range(6):                                                                  # dávky
        x = 3 + k * 4
        for y in range(top + 1, top + 5):
            s.px(x, y, VIOLET[3] if k < 5 else GLASSG[3])
            s.px(x + 1, y, VIOLET[2] if k < 5 else GLASSG[2])
        s.px(x, top + 3, PAPER[3]); s.px(x + 1, top + 3, PAPER[3])
    return selout(s, 0.5, 0.85)


def gas_masks(n=4, missing=2):
    """Lišta s háčky a plynovými maskami (gumová tvář, dvě kulatá skla, filtr), jeden háček prázdný s cedulkou."""
    W, H = n * 10 + 4, 18
    s = Spr(W, H)
    for x in range(W):
        s.px(x, 1, WOODD[4]); s.px(x, 2, WOODD[2])
    for i in range(n):
        x = 3 + i * 10
        s.px(x + 3, 3, IRON_[3]); s.px(x + 3, 4, IRON_[2])
        if i == missing:
            for y in range(6, 9):
                for xx in range(x + 1, x + 6):
                    s.px(xx, y, PAPER[3] if y < 8 else PAPER[1])
            s.px(x + 2, 7, INK); s.px(x + 4, 7, INK)
            continue
        for y in range(5, 15):
            for xx in range(x, x + 7):
                d = ((xx + .5 - (x + 3.5)) / 3.6) ** 2 + ((y + .5 - 10) / 5) ** 2
                if d <= 1:
                    s.px(xx, y, c(0x2A2E2A) if xx < x + 3 else c(0x1A1E1A))
        for ex in (x + 1, x + 5):
            s.px(ex, 9, GLASSG[3]); s.px(ex, 8, GLASSG[4])
        for y in range(13, 17):
            s.px(x + 3, y, IRON_[2]); s.px(x + 4, y, IRON_[1])
    return selout(s, 0.5, 0.85)


def skull_sign():
    """Smaltovaná cedule: žlutý trojúhelník s lebkou a zkříženými hnáty, pod ním pruh POZOR – JED (čárky)."""
    W, H = 22, 26
    s = Spr(W, H)
    for y in range(0, 18):
        hw = y * 0.6
        for x in range(int(11 - hw), int(11 + hw) + 1):
            e = min(x - (11 - hw), 11 + hw - x, 17 - y)
            s.px(x, y, INK if e < 1.2 else c(0xF0C830))
    for (dx, dy) in ((0, 0), (-1, 0), (1, 0), (0, 1), (-1, 1), (1, 1)):
        s.px(11 + dx, 9 + dy, INK)
    s.px(10, 10, c(0xF0C830)); s.px(12, 10, c(0xF0C830)); s.px(11, 12, INK)
    s.line(7, 13, 15, 16, INK); s.line(15, 13, 7, 16, INK)
    for y in range(19, 25):
        for x in range(1, 21):
            s.px(x, y, RED[3] if y in (19, 24) else c(0xF4EEE0))
    for x in range(3, 19, 2):
        s.px(x, 21, INK); s.px(x, 22, INK)
    return selout(s, 0.6, 0.9)


def bottle_shelf(w=42):
    """Nástěnná police se dvěma patry lahví s jedy: různé tvary (kulaté, hranaté, tenké), barvy zelená, fialová,
    jantarová, černá; štítky s lebkou (bílá tečka a čárka)."""
    H = 30
    s = Spr(w, H)
    rng = np.random.default_rng(4)
    for shelf_y in (13, 28):
        for x in range(w):
            s.px(x, shelf_y, WOODD[4]); s.px(x, shelf_y + 1, WOODD[1])
        x = 2
        while x < w - 4:
            bw = int(rng.integers(3, 6)); bh = int(rng.integers(6, 11))
            col = [TOX[3], VIOLET[3], c(0xC88A2A), c(0x1A1A22), TOX[2]][int(rng.integers(0, 5))]
            for y in range(shelf_y - bh, shelf_y):
                for xx in range(x, x + bw):
                    cc = col if y > shelf_y - bh + 2 else GLASSG[3]
                    if xx == x and y > shelf_y - bh + 2:
                        cc = _mix(col, c(0xFFFFFF), 0.4)
                    s.px(xx, y, cc)
            s.px(x + bw // 2, shelf_y - bh - 1, c(0x8A6A3A))
            s.px(x + bw // 2, shelf_y - 3, c(0xF4EEE0))
            x += bw + 1
    return selout(s, 0.55, 0.88)


def drum(seed=0):
    """Plechový sud s jedem: zelený lak se zaobleným spodkem a vylisovanými obručemi (oblouky), víko s lemem
    a zátkou, žlutý štítek s lebkou, rezavé oděrky, stékající zelená stopa."""
    W, H = 20, 28
    s = Spr(W, H)
    cx, rx, ry = 10, 8.5, 2.6
    GRN = [c(0x0A2010), c(0x16381E), c(0x22502C), c(0x34703E), c(0x5A9A62)]
    _barrel(s, cx, 6, H - 5, rx, ry, GRN, hoops=(5, 12), hoop=[GRN[1], GRN[3], GRN[4]], staves=40)
    for y in range(int(6 - ry) - 1, int(6 + ry) + 2):                                   # víko
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            d = ((x + .5 - cx) / (rx + 0.4)) ** 2 + ((y + .5 - 6) / (ry + 0.4)) ** 2
            if d <= 1:
                s.px(x, y, GRN[4] if d > 0.7 and y < 6 else GRN[2] if d > 0.7 else GRN[3])
    s.px(cx + 3, 5, IRON_[3]); s.px(cx + 4, 5, IRON_[2])
    for y in range(13, 18):                                                             # štítek
        for x in range(cx - 4, cx + 3):
            s.px(x, y, c(0xF0C830) if y not in (13, 17) else INK)
    s.px(cx - 1, 15, INK); s.px(cx - 2, 14, INK); s.px(cx, 14, INK)
    for y in range(8, 15):
        s.px(cx + 5, y, TOX[3] if y < 12 else TOX[2])
    rng = np.random.default_rng(seed)
    for _ in range(6):
        x, y = int(rng.integers(3, W - 3)), int(rng.integers(8, H - 6))
        if s.opaque(x, y):
            s.px(x, y, c(0x6A3A1E))
    return selout(s, 0.5, 0.85)


def roots(img, pts, seed=0):
    """Kořeny a šlahouny prorůstající spárami: klikaté tmavé čáry s drobnými lístky a tu a tam fialovým
    květem; [pts] = začátky (x, y, směr v rad, délka)."""
    rng = np.random.default_rng(seed)
    H, W = img.shape[:2]
    for (x, y, a, L) in pts:
        for t in range(L):
            a += rng.uniform(-0.35, 0.35)
            x += math.cos(a); y += math.sin(a) * 0.6
            X, Y = int(x), int(y)
            if not (0 <= X < W and 0 <= Y < H):
                break
            img[Y, X] = MOSSG[1] if t % 3 else MOSSG[0]
            if t % 5 == 2:
                lx, ly = X + (1 if t % 2 else -1), Y - 1
                if 0 <= lx < W and 0 <= ly < H:
                    img[ly, lx] = MOSSG[3]
                    if rng.random() < 0.15:
                        img[ly, lx] = VIOLET[4]


def litter(img, x0, y0, x1, y1, n=30, seed=0):
    """Drobnosti na podlaze: spadané uschlé lístky, střepy skla s odleskem, rozsypaný fialový prášek."""
    rng = np.random.default_rng(seed)
    for _ in range(n):
        x, y = int(rng.integers(x0, x1)), int(rng.integers(y0, y1))
        k = rng.random()
        if k < 0.55:                                                                    # lístek
            col = [c(0x4A3A1A), c(0x5E4A20), c(0x3A4A1A)][int(rng.integers(0, 3))]
            img[y, x] = col; img[y, x + 1] = _mix(col, c(0x000000), 0.3)
        elif k < 0.8:                                                                   # střep
            img[y, x] = GLASSG[4]; img[y + 1, x + 1] = GLASSG[2]
        else:                                                                           # prášek
            img[y, x] = VIOLET[3]; img[y, x + 1] = VIOLET[2]; img[y + 1, x] = VIOLET[2]


def lab_stool():
    """Laboratorní stolička: kulaté dřevěné sedátko ve 3/4 na třech kovových nohách s příčkou."""
    W, H = 14, 16
    s = Spr(W, H)
    for (fx_, fy) in ((2, H - 1), (7, H - 1), (12, H - 1)):
        s.line(7, 5, fx_, fy, IRON_[2])
    for x in range(3, 12):
        s.px(x, 10, IRON_[3])
    for y in range(1, 7):
        for x in range(0, W):
            d = ((x + .5 - 7) / 7) ** 2 + ((y + .5 - 3.5) / 2.8) ** 2
            if d <= 1:
                s.px(x, y, WOODD[4] if y < 3 else WOODD[3] if y < 5 else WOODD[2])
    return selout(s, 0.5, 0.85)


def hanging_vines(s, x0, x1, y0, seed=0):
    """Do sprite stěny: šlahouny visící z rámů skleníku přes podezdívku (různě dlouhé, s lístky)."""
    rng = np.random.default_rng(seed)
    for x in range(x0, x1, 3):
        if rng.random() < 0.45:
            continue
        L = int(rng.integers(4, 18))
        for k in range(L):
            X = x + int(math.sin(k * 0.5 + x) * 1.2)
            s.px(X, y0 + k, MOSSG[2] if k % 2 else MOSSG[1])
            if k % 3 == 1:
                s.px(X + 1, y0 + k, MOSSG[3])


def specimen_tube():
    """Preparátový válec: mosazná podstava a víko, skleněný válec se zelenou tekutinou, uvnitř se vznáší bledý
    zárodek rostliny s kořínky (a něco, co by mohlo být malé zvíře), odlesk skla, štítek."""
    W, H = 18, 44
    s = Spr(W, H)
    cx, rx, ry = 9, 7, 2.2
    LIQ = [c(0x0E3A1A), c(0x1E6A2A), c(0x3A9A3A), c(0x5AC44A), c(0x8AE070)]
    _barrel(s, cx, 6, H - 8, rx, ry, LIQ, staves=1)
    for y in range(10, H - 10):                                                         # zárodek
        k = (y - 10) / (H - 20)
        w = 2.6 * math.sin(k * math.pi) + 0.6
        for x in range(int(cx - w), int(cx + w) + 1):
            s.px(x, y, c(0xD8E8C8) if x < cx else c(0xA8C098))
    for k in range(6):
        s.px(cx - 2 + k, H - 10 + (k % 3), c(0xC8D8B8))
    s.px(cx - 1, 16, c(0x2A3A2A)); s.px(cx + 1, 16, c(0x2A3A2A))
    for y in range(8, H - 9):                                                           # odlesk
        if y % 5 != 0:
            s.px(cx - 5, y, GLASSG[4])
    for part_y, h in ((0, 5), (H - 8, 8)):                                              # víko a podstava
        for y in range(part_y, part_y + h):
            for x in range(cx - 8, cx + 9):
                u = (x - cx) / 8.5
                s.px(x, y, BRASS[3] if u < -0.3 else BRASS[2] if u < 0.4 else BRASS[1])
    for x in range(cx - 3, cx + 4):
        s.px(x, H - 5, PAPER[3])
    return selout(s, 0.5, 0.85)


def floor_pipes(img, paths):
    """Měděné trubky po podlaze mezi káděmi a přístrojem: 2 px tělo se světlou horní hranou, objímky, pod nimi
    tmavý stín. [paths] = seznamy bodů."""
    for pts in paths:
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
            for i in range(n):
                t = i / max(1, n - 1)
                X, Y = int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t))
                img[Y + 2, X] = _mix(img[Y + 2, X], c(0x000000), 0.5)
                img[Y - 1, X] = COPPER[4]; img[Y, X] = COPPER[3]; img[Y + 1, X] = COPPER[1]
                if i % 9 == 4:
                    img[Y - 1, X] = IRON_[3]; img[Y, X] = IRON_[2]; img[Y + 1, X] = IRON_[1]

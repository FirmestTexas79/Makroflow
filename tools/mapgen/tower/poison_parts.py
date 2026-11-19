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


def toxic_puddle(img, cx, cy, rx, ry, seed=0):
    """Louže jedu (místo setkání): nepravidelná svítivě zelená hladina, tmavší okraj, světlý odlesk, bublinky."""
    rng = np.random.default_rng(seed)
    lobes = [(cx + rng.uniform(-rx * 0.4, rx * 0.4), cy + rng.uniform(-ry * 0.3, ry * 0.3), rng.uniform(0.6, 0.9)) for _ in range(3)]
    for y in range(int(cy - ry) - 3, int(cy + ry) + 4):
        for x in range(int(cx - rx) - 3, int(cx + rx) + 4):
            d = min(((x + .5 - lx) / (rx * s)) ** 2 + ((y + .5 - ly) / (ry * s)) ** 2 for (lx, ly, s) in lobes + [(cx, cy, 1.0)])
            if d <= 1:
                col = TOX[2] if d > 0.75 else TOX[3] if d > 0.25 else TOX[4]
                if d > 0.88:
                    col = TOX[1]
                if (y - (cy - ry * 0.4)) ** 2 < 1 and abs(x - (cx - rx * 0.3)) < rx * 0.3:
                    col = TOX[5]
                img[y, x] = col
            elif d <= 1.35 and (x + y) % 2:
                img[y, x] = _mix(img[y, x], TOX[1], 0.5)


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
    for y in range(H - 34, H - 8):
        for x in range(sx - 9, sx + 10):
            u = (x - sx) / 9.5
            col = WOODD[3] if u < -0.4 else WOODD[2] if u < 0.5 else WOODD[1]
            if (x - sx) % 4 == 0:
                col = WOODD[0]
            if y in (H - 31, H - 14):
                col = IRON_[3] if u < 0 else IRON_[1]
            s.px(x, y, col)
    for x in range(sx - 9, sx + 10):
        s.px(x, H - 34, COPPER[3]); s.px(x, H - 35, c(0x1A3A34))
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


def vat(seed=0):
    """Káď: dřevěné dužiny se dvěma železnými obručemi, nahoře hladina bublající zelené kaše s pěnou a
    vynořeným listem, přes okraj stéká slizký pramínek, opřená míchací pádla."""
    W, H = 40, 38
    s = Spr(W, H)
    cx, top, bot = 20, 10, H - 2
    for y in range(top, bot + 1):
        for x in range(cx - 16, cx + 17):
            u = (x - cx) / 16.5
            col = WOODD[4] if u < -0.6 else WOODD[3] if u < -0.1 else WOODD[2] if u < 0.5 else WOODD[1]
            if (x - cx) % 5 == 0:
                col = WOODD[0]
            if y in (top + 6, bot - 6):
                col = IRON_[3] if u < 0 else IRON_[1]
            s.px(x, y, col)
    for y in range(top - 6, top + 6):                                                   # hladina (elipsa)
        for x in range(cx - 17, cx + 18):
            d = ((x + .5 - cx) / 17) ** 2 + ((y + .5 - top) / 6) ** 2
            if d <= 1:
                if d > 0.82:
                    col = WOODD[4] if y < top else WOODD[2]
                else:
                    col = TOX[3] if _h(x, y, seed) % 5 else TOX[4]
                    if d < 0.15:
                        col = TOX[4]
                s.px(x, y, col)
    for (fx_, fy) in ((cx - 6, top - 1), (cx + 5, top + 1), (cx + 1, top - 3)):         # pěna
        s.px(fx_, fy, TOX[5]); s.px(fx_ + 1, fy, TOX[5]); s.px(fx_, fy + 1, TOX[4])
    for y in range(top + 2, top + 14):                                                  # stékající sliz
        s.px(cx + 12, y, TOX[3] if y < top + 10 else TOX[2])
    s.px(cx + 13, top + 13, TOX[2])
    s.line(cx - 10, top - 1, cx - 18, top - 16, WOODD[3], 2)                            # pádlo
    for y in range(top - 19, top - 14):
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
    for y in range(H - 12, H):                                                          # květináč
        hw = 11 - (y - (H - 12)) * 0.25
        for x in range(int(cx - hw), int(cx + hw) + 1):
            u = (x - cx) / hw
            col = c(0x8A5A3A) if u < -0.3 else c(0x6E4430) if u < 0.5 else c(0x4E2E20)
            if y == H - 12:
                col = c(0xA87048)
            s.px(x, y, col)
    for x in range(cx - 10, cx + 11):
        s.px(x, H - 11, SOILD[2])
    for (lx, ly, d) in ((cx - 9, H - 13, -1), (cx + 8, H - 13, 1), (cx - 3, H - 14, -1), (cx + 3, H - 14, 1)):
        for k in range(5):
            s.px(lx + d * k, ly - k * 0.6, MOSSG[3] if k < 3 else MOSSG[2])
    return selout(s, 0.5, 0.85)


def poison_bed(seed=0, w=46, h=28):
    """Vyvýšený záhon: trámkový rám, tmavá hlína, rulík (fialové zvonky a černé bobule), bolehlav (bílé okolíky),
    fialové svítící houby a zapíchnuté jmenovky jako na hrobech."""
    s = Spr(w + 4, h)
    rng = np.random.default_rng(seed)
    top = h - 12
    for y in range(top, h):
        for x in range(0, w):
            e = min(x, w - 1 - x, y - top, h - 1 - y)
            col = WOODD[4] if e == 0 and y == top else WOODD[3] if e < 2 else SOILD[1 + (_h(x, y, 2) % 2)]
            if y > h - 5 and e >= 2:
                col = WOODD[2] if (x // 6) % 2 else WOODD[1]
            s.px(x, y, col)
        for x in range(w, w + 4):
            if y - top >= x - w:
                s.px(x, y, WOODD[0])
    for k in range(5):                                                                  # rulík
        x = 4 + k * 9 + int(rng.integers(-1, 2))
        ht = int(rng.integers(9, 14))
        s.vline(x, top - ht, top + 1, MOSSG[2])
        for j in range(3):
            yy = top - ht + 2 + j * 3
            s.px(x - 1, yy, MOSSG[3]); s.px(x + 1, yy + 1, MOSSG[3]); s.px(x - 2, yy, MOSSG[2])
        s.px(x + 2, top - ht + 4, VIOLET[4]); s.px(x + 2, top - ht + 5, VIOLET[3])
        s.px(x - 2, top - ht + 7, c(0x101014)); s.px(x - 1, top - ht + 8, c(0x2A2A3A))
    for k in range(2):                                                                  # bolehlav
        x = 9 + k * 22
        s.vline(x, top - 16, top + 1, MOSSG[3])
        for d in (-3, -1, 1, 3):
            s.px(x + d, top - 17 + abs(d) // 2, c(0xF0F0E8))
        s.px(x, top - 18, c(0xFFFFFF))
    for k in range(3):                                                                  # houby
        x = 14 + k * 11
        for y in range(top - 2, top + 2):
            s.px(x, y, c(0xC8B8D8))
        for xx in range(x - 2, x + 3):
            s.px(xx, top - 3, VIOLET[4] if xx < x else VIOLET[3]); s.px(xx, top - 2, VIOLET[2])
        s.px(x - 1, top - 4, VIOLET[5])
    for k in range(3):                                                                  # jmenovky
        x = 6 + k * 16
        for y in range(top - 3, top + 3):
            s.px(x, y, c(0xC8C0B0) if y > top - 2 else c(0xE0D8C8))
        s.px(x + 1, top - 3, c(0xC8C0B0)); s.px(x + 1, top - 2, c(0xA8A090))
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
    """Plechový sud s jedem: zelený lak s odřeninami, dvě obruče, žlutý štítek s lebkou, na víku zátka a stékající
    zelená stopa."""
    W, H = 18, 24
    s = Spr(W, H)
    cylinder(s, 1, W - 2, 4, H - 1, [c(0x0E2A16), c(0x1A4424), c(0x2A6034), c(0x3E7E46), c(0x62A068)], amb=0.25)
    for x in range(1, W - 1):
        u = (x + .5 - W / 2) / (W / 2 - 1)
        nz = math.sqrt(max(0, 1 - u * u))
        s.px(x, int(4 - nz * 2), c(0x5A8A60)); s.px(x, int(4 + nz * 2), c(0x2A4A30))
        for y in range(int(4 - nz * 2) + 1, int(4 + nz * 2)):
            s.px(x, y, c(0x3E6A44))
        for yy in (10, 18):
            s.px(x, int(yy + nz), IRON_[3] if u < 0.3 else IRON_[1])
    for y in range(12, 17):
        for x in range(5, 12):
            s.px(x, y, c(0xF0C830) if y not in (12, 16) else INK)
    s.px(8, 14, INK); s.px(7, 13, INK); s.px(9, 13, INK)
    s.px(12, 3, IRON_[2])
    for y in range(5, 12):
        s.px(13, y, TOX[3] if y < 10 else TOX[2])
    rng = np.random.default_rng(seed)
    for _ in range(5):
        x, y = int(rng.integers(2, W - 2)), int(rng.integers(6, H - 2))
        s.px(x, y, c(0x6A3A1E))
    return selout(s, 0.5, 0.85)

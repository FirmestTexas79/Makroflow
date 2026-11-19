"""Spalovna spisů v2 – nové díly (pec, stěna, podlaha, popel, vozík, skluz, stůl topiče). Uhlí a parní trubka
zůstávají z fire_parts (schválené)."""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
from pix import Spr, c  # noqa: E402
from figures import sphere, cylinder, selout, blobs, _pick, BRASS, PAPER, INK, STEEL, RED, IRON_, shade_px  # noqa: E402
from fire_parts import EMBER, SOOT, COAL, _h  # noqa: E402

CAST = [c(0x0E0E12), c(0x1A1A20), c(0x282830), c(0x3A3A44), c(0x55555F), c(0x7A7A84)]   # litina
HOT = [c(0x2A0E0A), c(0x4A140C), c(0x6E1E10), c(0x962C14)]                                # rozžhavená litina
FIREBRICK = [c(0x3A1408), c(0x6A2410), c(0x9A3A18), c(0xC45A22), c(0xE88A3A)]
FLAME = [c(0x9A2208), c(0xD84A10), c(0xF4821E), c(0xFCC04A), c(0xFFEC9A), c(0xFFFFF0)]


def _mix(a, b, t):
    out = a.copy(); out[:3] = (a[:3] * (1 - t) + b[:3] * t).astype(np.uint8); return out


def incinerator(flue=56):
    """Spalovací pec ve 3/4: kulatý litinový kotel na cihlovém soklu, nýtované obruče, kopulovité víko s límcem
    a kouřovod do stropu (nahoře mizí ve tmě). Vpředu klenuté ústí v masivním rámu: uvnitř šamotové cihly, žhavé
    lože a plameny; dvířka otevřená doprava (vidět vnitřní plech a panty), pod nimi dvířka popelníku. Spodek
    kotle je od žáru do ruda, na boku manometr, nad ústím mosazný štítek. Plameny oživuje InteriorFxView.FIRE."""
    W, BODY_RX = 76, 27
    cx = 34
    plinth_h, body_h, cap_h = 12, 44, 14
    H = flue + cap_h + body_h + plinth_h
    s = Spr(W, H)
    yb = H - 1                                                                     # pata soklu
    y_pl = yb - plinth_h                                                           # horní hrana soklu
    y_body = y_pl - body_h                                                         # horní hrana kotle
    y_cap = y_body - cap_h
    # sokl: cihlový ovál (horní plocha) + čelo
    for y in range(y_pl - 5, yb + 1):
        for x in range(cx - 33, cx + 34):
            dx = (x + .5 - cx) / 33
            if abs(dx) > 1:
                continue
            ytop = y_pl - 5 * math.sqrt(1 - dx * dx) + 5 - 5          # horní elipsa
            front = y_pl + 5 * math.sqrt(1 - dx * dx) - 5 + 5
            if y < y_pl - 5 * math.sqrt(1 - dx * dx):
                continue
            if y <= y_pl + 5 * math.sqrt(1 - dx * dx) - 4:
                col = FIREBRICK[2] if (x // 5 + y // 3) % 2 else FIREBRICK[1]       # horní plocha (cihly v kruhu)
                if (x + y * 3) % 7 == 0:
                    col = SOOT[3]
            else:
                ry = y - (y_pl + 5 * math.sqrt(1 - dx * dx) - 4)
                if y > yb - 0:
                    col = SOOT[1]
                else:
                    off = 3 if (int(ry) // 3) % 2 else 0
                    mort = (x + off) % 6 == 0 or int(ry) % 3 == 2
                    col = FIREBRICK[0] if mort else FIREBRICK[2] if dx < -0.3 else FIREBRICK[1]
                    if dx > 0.6 and not mort:
                        col = FIREBRICK[0]
            s.px(x, y, col)
    # kotel: válec z litiny, dole rozžhavený
    for y in range(y_body, y_pl + 1):
        t = (y - y_body) / (y_pl - y_body)
        for x in range(cx - BODY_RX, cx + BODY_RX + 1):
            nx = (x + .5 - cx) / BODY_RX
            if abs(nx) > 1:
                continue
            nz = math.sqrt(1 - nx * nx)
            v = 0.18 + 0.82 * max(0.0, -0.55 * nx + 0.45 * nz)
            col = _pick(CAST[1:], min(1, v), x, y)
            heat = max(0.0, (t - 0.45) / 0.55) * (0.35 + 0.65 * nz)
            if heat > 0.05:
                col = _mix(col, HOT[min(3, int(heat * 4))], min(0.85, heat))
            # spodní okraj kotle sedí na soklu (elipsa)
            if y > y_pl - 4 + 4 * nz:
                continue
            s.px(x, y, col)
    # obruče s nýty
    for yy in (y_body + 3, y_body + 20, y_pl - 10):
        for x in range(cx - BODY_RX, cx + BODY_RX + 1):
            nx = (x + .5 - cx) / BODY_RX
            if abs(nx) >= 1:
                continue
            nz = math.sqrt(1 - nx * nx)
            y = int(yy + 3 * nz)
            v = 0.3 + 0.7 * max(0.0, -0.55 * nx + 0.45 * nz)
            s.px(x, y - 1, _pick(CAST[2:], min(1, v), x, y))
            s.px(x, y, _pick(CAST[1:5], min(1, v), x, y))
            s.px(x, y + 1, CAST[0])
            ang = math.asin(nx)
            if abs((ang * 9) % 1 - 0.5) < 0.09 and nz > 0.2:
                s.px(x, y, CAST[5] if nx < 0.3 else CAST[4])                          # nýt
    # víko: kopule + límec, kouřovod
    for y in range(y_cap - 2, y_body + 4):
        for x in range(cx - BODY_RX, cx + BODY_RX + 1):
            dx = (x + .5 - cx) / BODY_RX
            if abs(dx) > 1:
                continue
            top = y_body - (cap_h) * math.sqrt(max(0, 1 - dx * dx)) ** 0.8 - 1
            bot = y_body + 3 * math.sqrt(1 - dx * dx)
            if top <= y <= bot:
                dy = (y - y_body) / cap_h
                nz = math.sqrt(max(0.0, 1 - dx * dx - min(1, dy * dy) * 0.6))
                v = 0.15 + 0.85 * max(0, -0.55 * dx - 0.35 * dy + 0.5 * nz)
                s.px(x, y, _pick(CAST[1:], min(1, v), x, y))
    for x in range(cx - BODY_RX, cx + BODY_RX + 1):                                  # hrana víka
        dx = (x + .5 - cx) / BODY_RX
        if abs(dx) < 1:
            y = int(y_body + 3 * math.sqrt(1 - dx * dx))
            s.px(x, y, CAST[0]); s.px(x, y - 1, CAST[4] if dx < 0.2 else CAST[3])
    fx0, fx1 = cx - 7, cx + 7
    for y in range(0, y_cap + 3):                                                   # kouřovod
        for x in range(fx0, fx1 + 1):
            nx = (x + .5 - cx) / 7.5
            v = 0.2 + 0.8 * max(0.0, -0.6 * nx + 0.4 * math.sqrt(max(0, 1 - nx * nx)))
            col = _pick(CAST[1:], min(1, v), x, y)
            fade = 1 - min(1.0, y / (flue * 0.7))                                   # nahoře mizí ve tmě
            if fade > 0:
                col = _mix(col, SOOT[0], fade * 0.85)
            s.px(x, y, col)
        if y % 14 == 8:
            for x in range(fx0 - 1, fx1 + 2):
                s.px(x, y, CAST[4] if x < cx else CAST[2]); s.px(x, y + 1, CAST[0])
    for x in range(fx0 - 3, fx1 + 4):                                               # límec kouřovodu
        for y in range(y_cap, y_cap + 4):
            s.px(x, y, CAST[4] if y == y_cap else CAST[2] if x < cx + 2 else CAST[1])
    # ústí: masivní klenutý rám
    mw, mh = 13, 30
    m_bot = y_pl - 3
    m_top = m_bot - mh
    def in_arch(x, y, w):
        if y > m_bot or y < m_top - w * 0.2:
            return False
        arch_c = m_top + w
        if y >= arch_c:
            return abs(x + .5 - cx) <= w
        return math.hypot(x + .5 - cx, (y - arch_c) * 1.0) <= w
    for y in range(m_top - 8, m_bot + 3):
        for x in range(cx - mw - 5, cx + mw + 6):
            if in_arch(x, y - 1, mw + 4) and not in_arch(x, y, mw):
                e = in_arch(x, y - 1, mw + 4) and not in_arch(x, y - 2, mw + 4)
                s.px(x, y, CAST[4] if x < cx - mw - 2 or e else CAST[2] if x < cx + mw else CAST[1])
    for k, yy in enumerate(range(m_top + 2, m_bot, 6)):                             # nýty na rámu
        s.px(cx - mw - 3, yy, CAST[5]); s.px(cx + mw + 3, yy, CAST[3])
    # uvnitř: šamot, žhavé lože, plameny (statický základ)
    for y in range(m_top - 4, m_bot + 1):
        for x in range(cx - mw, cx + mw + 1):
            if not in_arch(x, y, mw):
                continue
            t = (y - m_top) / mh
            d = abs(x + .5 - cx) / mw
            row = (y - m_top) // 3; off = 2 if row % 2 else 0
            mort = (x + off) % 5 == 0 or (y - m_top) % 3 == 2
            base = FIREBRICK[1] if mort else FIREBRICK[3 if t > 0.5 else 2]
            fl = math.sin(x * 1.1) * 0.12 + math.sin(x * 0.45 + 2) * 0.12
            f = (1 - d) * 1.1 - (1 - t) * 0.9 + fl + 0.25
            if f > 0.15:
                base = FLAME[min(5, int(f * 5))]
            if y >= m_bot - 4:
                base = FLAME[4] if (x + y) % 3 else FLAME[5] if _h(x, y) % 4 == 0 else FLAME[3]
                if y == m_bot:
                    base = FLAME[2]
            s.px(x, y, base)
    # dvířka otevřená doprava (pohled na hranu a vnitřní plech)
    dx0 = cx + mw + 5
    for y in range(m_top - 2, m_bot + 2):
        for x in range(dx0, dx0 + 9):
            k = (x - dx0) / 9
            if m_top - 2 + k * 5 <= y <= m_bot + 1 - k * 2:
                col = CAST[2] if x == dx0 + 8 else CAST[3] if x < dx0 + 2 else CAST[1]
                if (y - m_top) % 9 == 0:
                    col = CAST[4]
                s.px(x, y, col)
    for yy in (m_top + 4, m_bot - 5):                                               # panty
        s.px(dx0 - 1, yy, CAST[5]); s.px(dx0, yy, CAST[4]); s.px(dx0 - 1, yy + 1, CAST[0])
    for y in range(m_top + 12, m_top + 18):                                         # klika
        s.px(dx0 + 7, y, BRASS[3] if y < m_top + 14 else BRASS[1])
    # odlesk ohně na plechu kolem ústí
    for y in range(m_top - 2, m_bot + 3):
        for x in range(cx - mw - 9, cx + mw + 9):
            if s.opaque(x, y) and not in_arch(x, y, mw + 4):
                dd = math.hypot(x - cx, (y - (m_top + mh * 0.6)) * 0.8)
                if dd < mw + 9 and (x + y) % 2 == 0:
                    a = s.a[y, x]
                    s.a[y, x, :3] = np.clip(a[:3].astype(int) + np.array([60, 22, 4]) * (1 - dd / (mw + 9)), 0, 255).astype(np.uint8)
    # popelník pod ústím
    for y in range(m_bot + 2, y_pl + 5):
        for x in range(cx - 8, cx + 9):
            col = CAST[3] if y == m_bot + 2 else CAST[1]
            if y == y_pl + 1 and (x - cx) % 3 == 0:
                col = EMBER[3]
            s.px(x, y, col)
    # mosazný štítek nad ústím a manometr na boku
    for x in range(cx - 9, cx + 10):
        s.px(x, m_top - 10, BRASS[4]); s.px(x, m_top - 9, BRASS[2]); s.px(x, m_top - 8, BRASS[1])
    for x in range(cx - 7, cx + 8, 2):
        s.px(x, m_top - 9, INK)
    gx, gy = cx - 21, y_body + 14
    for y in range(gy - 5, gy + 6):
        for x in range(gx - 5, gx + 6):
            d = math.hypot(x + .5 - gx, y + .5 - gy)
            if d <= 5:
                s.px(x, y, BRASS[3] if d > 4 and x < gx else BRASS[1] if d > 4 else PAPER[3])
    s.line(gx, gy, gx + 3, gy - 2, RED[3])
    s.px(gx, gy, INK)
    return selout(s, 0.45, 0.8), dict(cx=cx, m_top=m_top, m_bot=m_bot, mw=mw, flue=(fx0, fx1, y_cap))


TILE = [c(0x1E100C), c(0x2E1812), c(0x3C2016), c(0x4A281C), c(0x5C3424)]          # pálené dlaždice
GLAZE = [c(0x10140E), c(0x1A2216), c(0x26321E), c(0x34442A), c(0x58704A)]         # glazované kachle (tmavě zelené)
CHECK = [c(0x1A1A20), c(0x2C2C34), c(0x40404A), c(0x5A5A66)]                       # slzičkový plech
WOOD = [c(0x24140C), c(0x3A2214), c(0x52321E), c(0x6E462A), c(0x8A5E3A)]
FOLD = [c(0x5E4220), c(0x86602E), c(0xAA8040), c(0xCCA45C), c(0xE6C47E)]


def tile_floor(img, x0, y0, x1, y1, runway=(76, 101), runway_y1=200):
    """Pálené dlaždice 12 × 12 (každá trochu jiného tónu, ohlazené rohy, vlásečnice), spáry se sazemi,
    uprostřed od výtahu k peci pás slzičkového plechu s lemem."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx, ly = (x - x0) % 12, (y - y0) % 12
            tx, ty = (x - x0) // 12, (y - y0) // 12
            k = 1 + _h(tx, ty, 11) % 3
            col = TILE[k]
            if lx == 0 or ly == 0:
                col = SOOT[1]
            elif lx == 1 or ly == 1:
                col = TILE[min(4, k + 1)]
            elif lx == 11 or ly == 11:
                col = TILE[k - 1]
            elif _h(x, y, 3) % 23 == 0:
                col = TILE[k - 1]
            if _h(tx, ty, 5) % 9 == 0 and lx + ly == 9:
                col = TILE[0]                                                        # vlásečnice
            sv = (_h(x // 6, y // 6, 13) % 100) / 100 * 0.5 + (_h(x // 2, y // 2, 14) % 100) / 100 * 0.5
            if sv > 0.78:
                col = SOOT[2] if (x + y) % 2 else col                                # saze
            img[y, x] = col
    rx0, rx1 = runway
    for y in range(y0, runway_y1):
        for x in range(rx0, rx1):
            e = min(x - rx0, rx1 - 1 - x)
            if e == 0:
                col = CHECK[0]
            elif e == 1:
                col = CHECK[3] if x < 88 else CHECK[2]
            else:
                col = CHECK[1]
                if (x * 2 + y * 3) % 8 in (0, 1) and (x + y) % 2 == 0:
                    col = CHECK[3]                                                   # slzičky
                elif (x * 2 + y * 3) % 8 == 2 and (x + y) % 2 == 0:
                    col = CHECK[0]
            img[y, x] = col


def boiler_wall(W, H, ports=()):
    """Zadní stěna kotelny: nahoře začouzené cihly, uprostřed litinová lišta s nýty, dole pás tmavě zelených
    glazovaných kachlí s lesklou hranou. [ports] = kulatá průzorová okénka (střed x, y, r), za kterými hoří."""
    s = Spr(W, H)
    tile_top = H - 30
    for y in range(H):
        for x in range(W):
            if y >= tile_top:
                ly = y - tile_top
                tx, ty = x % 8, (ly - 3) % 6
                if ly < 3:
                    col = CAST[4] if ly == 0 else CAST[2] if ly == 1 else CAST[0]
                elif tx == 0 or ty == 5:
                    col = GLAZE[0]
                else:
                    col = GLAZE[2] if _h(x // 8, (ly - 3) // 6, 2) % 3 else GLAZE[1]
                    if ty == 0 and tx < 6:
                        col = GLAZE[3]
                    if tx == 1 and ty < 2:
                        col = GLAZE[4]
            else:
                row = y // 4; off = 4 if row % 2 else 0
                lx, ly = (x + off) % 8, y % 4
                b = _h((x + off) // 8, row, 1) % 4
                col = (FIREBRICK[1] if b else FIREBRICK[0]) if not (lx == 0 or ly == 3) else SOOT[1]
                soot = 1 - y / (tile_top * 0.9) + ((_h(x // 2, y // 2, 7) % 100) / 100 - 0.5) * 0.3
                if soot > 0.45:
                    col = SOOT[2] if not (lx == 0 or ly == 3) else SOOT[0]
                    if soot < 0.55 and (x + y) % 2:
                        col = FIREBRICK[0]
            s.px(x, y, col)
    for (px_, py_, r) in ports:
        for y in range(py_ - r - 2, py_ + r + 3):
            for x in range(px_ - r - 2, px_ + r + 3):
                d = math.hypot(x + .5 - px_, y + .5 - py_)
                if d <= r - 1:
                    t = d / r
                    col = FLAME[5] if t < 0.25 else FLAME[4] if t < 0.5 else FLAME[3] if t < 0.75 else FLAME[2]
                    if y > py_ + r * 0.3:
                        col = FLAME[1]
                    s.px(x, y, col)
                elif d <= r + 1:
                    s.px(x, y, CAST[4] if (x < px_ and y < py_) else CAST[2])
                elif d <= r + 2:
                    s.px(x, y, CAST[0])
        for a in range(8):
            ang = a * math.pi / 4
            s.px(px_ + math.cos(ang) * r, py_ + math.sin(ang) * r, CAST[5])
        s.line(px_ - r + 1, py_, px_ + r - 1, py_, CAST[1])                         # mřížka průzoru
        s.line(px_, py_ - r + 1, px_, py_ + r - 1, CAST[1])
    for x in range(W):                                                              # římsa nahoře
        for y in range(0, 4):
            s.px(x, y, CAST[3] if y == 0 else CAST[1] if y < 3 else CAST[0])
    return s


def ash_bin(seed=0):
    """Popelnice (místo setkání): litinová kádě ve 3/4 s lemem a dvěma uchy, navršený popel s doutnajícími
    uhlíky a ohořelým cárem papíru, kolem paty rozsypaný popel."""
    W, H = 30, 30
    s = Spr(W, H)
    rng = np.random.default_rng(seed)
    cx = W / 2
    for y in range(H - 4, H):                                                       # rozsypaný popel
        for x in range(1, W - 1):
            if abs(x - cx) < 14 - (H - 1 - y) * 0.5 and rng.random() < 0.85:
                s.px(x, y, ASH[1] if (x + y) % 2 else ASH[2])
    cylinder(s, int(cx - 10), int(cx + 9), 12, H - 3, CAST[1:], amb=0.25)
    for x in range(int(cx - 10), int(cx + 10)):
        nx = (x + .5 - cx) / 10
        nz = math.sqrt(max(0, 1 - nx * nx))
        s.px(x, int(H - 3 + nz), CAST[0])
        for yy in (17, 23):
            s.px(x, int(yy + nz * 1.5), CAST[3] if nx < 0.3 else CAST[2])
    for x in range(int(cx - 11), int(cx + 11)):                                     # lem
        nx = (x + .5 - cx) / 11
        nz = math.sqrt(max(0, 1 - nx * nx))
        s.px(x, int(12 + nz * 2), CAST[4] if nx < 0.2 else CAST[2])
        s.px(x, int(12 - nz * 2), CAST[3])
    for side in (-1, 1):                                                            # ucha
        xx = int(cx + side * 11)
        s.px(xx, 15, CAST[3]); s.px(xx + side, 16, CAST[3]); s.px(xx, 17, CAST[3])
    blobs(s, [(cx, 11, 7.5), (cx - 4, 12, 4.5), (cx + 4, 12, 4.5), (cx - 1, 8.5, 4)], ASH, amb=0.3)
    for _ in range(4):
        x, y = int(rng.integers(int(cx - 7), int(cx + 7))), int(rng.integers(6, 13))
        if s.opaque(x, y) and s.opaque(x, y - 1):
            s.px(x, y, EMBER[3]); s.px(x + 1, y, EMBER[2])
    for y in range(4, 7):                                                           # cár papíru
        for x in range(int(cx + 1), int(cx + 6)):
            s.px(x, y, SOOT[2] if x == int(cx + 5) or y == 4 else PAPER[2])
    s.px(int(cx + 2), 5, INK); s.px(int(cx + 3), 5, INK)
    return selout(s, 0.5, 0.85)


ASH = [c(0x34302E), c(0x524C48), c(0x746C66), c(0x988F86), c(0xBCB3A8)]


def ash_spill(w=40, h=14, seed=0):
    """Vyhrabaný popel před pecí: nízká rozhrnutá vrstva s brázdami od hrabla, doutnající uhlíky, hrablo leží
    napříč."""
    s = Spr(w, h)
    rng = np.random.default_rng(seed)
    for y in range(h):
        for x in range(w):
            d = ((x + .5 - w / 2) / (w / 2)) ** 2 + ((y + .5 - h / 2) / (h / 2)) ** 2
            if d + rng.uniform(-0.12, 0.12) < 1:
                col = ASH[2] if (x // 2 + y) % 3 else ASH[1]                         # brázdy
                if d < 0.35 and (x + y) % 3 == 0:
                    col = ASH[3]
                s.px(x, y, col)
    for _ in range(9):
        x, y = int(rng.integers(4, w - 4)), int(rng.integers(2, h - 2))
        if s.opaque(x, y):
            s.px(x, y, EMBER[3] if rng.random() < 0.5 else EMBER[2])
    for k in range(w - 6):                                                          # hrablo
        s.px(3 + k, h - 4 - k * 0.12, c(0x8A6A3A) if k % 7 else c(0x6A4A26))
    for y in range(h - 9, h - 3):
        s.px(w - 4, y, CAST[3]); s.px(w - 3, y, CAST[1])
    return selout(s, 0.7, 0.92)


def folder_trolley():
    """Vozík na spisy: dřevěný rám na litinových kolech, drátěné bočnice, uvnitř nastojato složky s jezdci
    (různé barvy), několik převázaných motouzem, štítek UZAVŘENO na čele."""
    W, H = 36, 34
    s = Spr(W + 5, H)
    rng = np.random.default_rng(7)
    top, bot = 10, H - 7
    for i in range(14):                                                             # složky (vzadu → vpředu)
        x0 = 3 + i * 2
        hh = int(rng.integers(6, 12))
        colset = FOLD if i % 4 else [c(0x4A2A2A), c(0x6E3A36), c(0x8E4E46), c(0xAA6A5E), c(0xC88A7A)]
        for y in range(top - hh + 6, bot):
            for x in range(x0, x0 + 3):
                s.px(x, y, colset[3] if x == x0 else colset[2] if y < top + 1 else colset[1])
        if i % 3 == 0:
            s.px(x0 + 1, top - hh + 5, PAPER[3]); s.px(x0 + 2, top - hh + 5, PAPER[2])      # jezdec
        if i % 5 == 2:
            for y in range(top - hh + 6, bot):
                if (y - top) % 5 == 0:
                    s.px(x0 + 1, y, c(0xD8C8A0))                                       # motouz
    for y in range(top, bot + 1):                                                   # drátěná bočnice vpředu
        for x in range(1, W - 1):
            if x % 5 == 2 or y == (top + bot) // 2:
                s.px(x, y, CAST[3] if y != (top + bot) // 2 else CAST[2])
    for x in range(0, W):                                                           # rám
        s.px(x, top, WOOD[4]); s.px(x, top + 1, WOOD[2]); s.px(x, bot, WOOD[3]); s.px(x, bot + 1, WOOD[1])
    for y in range(top, bot + 2):
        s.px(0, y, WOOD[3]); s.px(1, y, WOOD[2]); s.px(W - 2, y, WOOD[2]); s.px(W - 1, y, WOOD[1])
    for y in range(top + 2, bot + 2):                                               # bok (3/4)
        for x in range(W, W + 5):
            if y - top - 2 >= x - W:
                s.px(x, y, WOOD[1] if x == W else WOOD[0])
    for x in range(W // 2 - 6, W // 2 + 7):                                         # štítek
        s.px(x, top + 6, PAPER[3]); s.px(x, top + 7, PAPER[2])
    for x in range(W // 2 - 4, W // 2 + 5, 2):
        s.px(x, top + 6, RED[3])
    for cx_ in (6, W - 6):
        sphere(s, cx_, H - 4, 3.5, 3.5, CAST[1:], amb=0.3)
        s.px(cx_, H - 4, CAST[5])
    return selout(s, 0.5, 0.82)


def chute2(h=50):
    """Skluz na spisy: ze stěny vystupuje mosazná násypka s klapkou a cedulí, z ní nýtovaný žlab šikmo dolů
    k vozíku; uvnitř žlabu tmavý stín, na hraně odřený lesk."""
    W = 26
    s = Spr(W, h)
    for y in range(12, h):                                                          # žlab
        x0 = int(4 + (y - 12) * 0.16)
        for x in range(x0, x0 + 14):
            e = x - x0
            col = STEEL[3] if e == 0 else STEEL[2] if e < 3 else STEEL[0] if e < 11 else STEEL[1]
            if e == 13:
                col = STEEL[0]
            s.px(x, y, col)
        if (y - 12) % 8 == 0:
            s.px(x0, y, STEEL[4]); s.px(x0 + 13, y, STEEL[3])
    for y in range(0, 14):                                                          # násypka
        hw = 12 - y * 0.25
        for x in range(int(13 - hw), int(13 + hw) + 1):
            col = BRASS[3] if x < 9 else BRASS[2] if x < 18 else BRASS[1]
            if y < 2:
                col = BRASS[4] if y == 0 else BRASS[3]
            s.px(x, y, col)
    for x in range(6, 21):                                                          # klapka (tmavá štěrbina)
        s.px(x, 6, BRASS[0]); s.px(x, 7, SOOT[0])
    for x in range(8, 19, 2):
        s.px(x, 3, INK)
    return selout(s, 0.5, 0.82)


def stoker_desk():
    """Pult topiče: malý dřevěný stůl ve 3/4, na něm otevřená kniha spálení (řádky jmen, poslední škrtnutá
    červeně), kalamář, petrolejka s teplým světlem, vedle stolička."""
    W, H = 40, 30
    s = Spr(W, H)
    top_y, top_h, front = 8, 9, 6
    for y in range(top_y, top_y + top_h):                                           # deska
        for x in range(2, W - 8):
            s.px(x, y, WOOD[4] if y == top_y else WOOD[3] if (x + y * 7) % 13 else WOOD[2])
    for y in range(top_y + top_h, top_y + top_h + front):                           # čelo
        for x in range(2, W - 8):
            s.px(x, y, WOOD[2] if y == top_y + top_h else WOOD[1])
    for x in (3, W - 10):                                                           # nohy
        for y in range(top_y + top_h + front, H - 1):
            s.px(x, y, WOOD[1]); s.px(x + 1, y, WOOD[0])
    for y in range(top_y + 1, top_y + 7):                                           # kniha
        for x in range(8, 24):
            col = PAPER[3] if x != 16 else PAPER[0]
            if y in (top_y + 2, top_y + 4) and x not in (15, 16, 17) and (x % 3):
                col = INK
            if y == top_y + 5 and 17 < x < 23:
                col = RED[3]
            s.px(x, y, col)
    s.px(5, top_y + 2, INK); s.px(5, top_y + 1, INK); s.px(6, top_y + 2, c(0x3A3A44))   # kalamář
    lx = W - 13                                                                     # petrolejka
    for y in range(0, top_y + 3):
        for x in range(lx - 2, lx + 3):
            if y < 2:
                s.px(x, y, BRASS[2] if abs(x - lx) < 2 else (0, 0, 0, 0))
            elif y < top_y - 1:
                s.px(x, y, FLAME[4] if abs(x - lx) < 1 and 3 < y < 6 else c(0xF4D898) if abs(x - lx) < 2 else BRASS[1])
            else:
                s.px(x, y, BRASS[3] if x < lx else BRASS[1])
    sx = W - 4                                                                      # stolička
    for y in range(H - 10, H - 7):
        for x in range(sx - 4, sx + 3):
            s.px(x, y, WOOD[4] if y == H - 10 else WOOD[2])
    for y in range(H - 7, H - 1):
        s.px(sx - 3, y, WOOD[1]); s.px(sx + 1, y, WOOD[1])
    return selout(s, 0.5, 0.85)


def folder_bundles(seed=2):
    """Převázané balíky spisů čekající na spálení: tři štosy složek různé výšky, motouz křížem, cedulka."""
    W, H = 32, 24
    s = Spr(W, H)
    rng = np.random.default_rng(seed)
    for k, (x0, n) in enumerate(((1, 5), (11, 7), (21, 4))):
        for i in range(n):
            y0 = H - 2 - i * 3
            sh = int(rng.integers(-1, 2))
            for y in range(y0 - 3, y0):
                for x in range(x0 + sh, x0 + sh + 10):
                    col = FOLD[3] if y == y0 - 3 else FOLD[2] if x < x0 + sh + 8 else FOLD[1]
                    if i % 3 == 1:
                        col = [c(0x6E3A36), c(0x8E4E46), c(0xAA6A5E)][2 if y == y0 - 3 else 1]
                    s.px(x, y, col)
            s.px(x0 + sh + 1, y0 - 2, PAPER[3])
        top = H - 2 - n * 3
        for y in range(top, H - 1):
            s.px(x0 + 5, y, c(0xD8C8A0))
        for x in range(x0, x0 + 10):
            s.px(x, top + n * 3 // 2, c(0xD8C8A0))
    return selout(s, 0.5, 0.85)

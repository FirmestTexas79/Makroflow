"""
Kancelář Řádu v2 – hlavní díly observatoře na vrcholu Věže: pódium s velkým dalekohledem, velký orloj se soukolím,
stůl Řádu s trůnem, monumentální váhy, vykládaný znak Řádu v podlaze s kolíky Foucaultova kyvadla, stěna kopule,
parketa a koberce. Ve 3/4, objemově, obrys selout.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
from pix import Spr, c  # noqa: E402
from figures import sphere, cylinder, selout, _pick, BRASS, PAPER, INK, STEEL, RED, IRON_, shade_px  # noqa: E402
from office_parts import NAVY, GOLDL, EBONY, MARBLE, CARPET, LEATHERG, SKY, _h  # noqa: E402

GOLD = [c(0x4A3010), c(0x7A5418), c(0xA8781E), c(0xD4A43A), c(0xF2CE6A), c(0xFFF0B0)]
BRONZE = [c(0x22140C), c(0x3E2614), c(0x5E3C1E), c(0x80562C), c(0xA8763E), c(0xD0A060)]
WOODR = [c(0x1A0C08), c(0x2C160E), c(0x422216), c(0x5A3020), c(0x744030), c(0x905440)]    # mahagon
VELVET = [c(0x1E0610), c(0x34091A), c(0x4E1026), c(0x6A1834), c(0x8A2846)]
PARCH = [c(0x8A7A58), c(0xB8A67C), c(0xD8C89E), c(0xF0E4C0)]
LIGHT = np.array([-0.55, -0.6, 0.58]); LIGHT = LIGHT / np.linalg.norm(LIGHT)


def _mix(a, b, t):
    out = a.copy(); out[:3] = (a[:3] * (1 - t) + b[:3] * t).astype(np.uint8); return out


def _tube(s, x0, y0, ax, ay, L0, L1, r0, r1, ramp, bands=(), band_ramp=None):
    """Válec podél osy (ax, ay) od t=L0 do L1, poloměr r0→r1, stínování podle kolmé souřadnice."""
    nx, ny = -ay, ax
    for t10 in range(int(L0 * 10), int(L1 * 10)):
        t = t10 / 10
        r = r0 + (r1 - r0) * (t - L0) / (L1 - L0)
        rr = band_ramp if band_ramp and any(abs(t - b) < 0.9 for b in bands) else ramp
        for o10 in range(int(-r * 10), int(r * 10) + 1, 3):
            o = o10 / 10
            X, Y = x0 + ax * t + nx * o, y0 + ay * t + ny * o
            u = o / r
            band = 0 if u > 0.75 else 1 if u > 0.35 else 2 if u > -0.2 else 3 if u > -0.55 else 4 if u > -0.8 else 2
            s.px(round(X), round(Y), rr[min(len(rr) - 1, band)])


def telescope_dais():
    """Pódium s velkým refraktorem: kruhové dřevěné pódium (parketa paprskovitě, mosazný lem, dva schůdky
    vpředu), vzadu mosazné zábradlí na sloupcích. Na pódiu litinová paralaktická montáž s ozubeným věncem
    a protizávažím, na ní dlouhý mosazný tubus s tmavými prstenci, sluneční clonou a hledáčkem, míří vzhůru
    doleva k oknu. Okulár dole s malou stoličkou pro pozorovatele."""
    W, H = 70, 92
    s = Spr(W, H)
    cx, cy, rx, ry = 34, 70, 31, 13                                                  # pódium
    for y in range(cy - ry - 1, cy + ry + 8):
        for x in range(cx - rx - 1, cx + rx + 2):
            dx = (x + .5 - cx) / rx
            if abs(dx) > 1:
                continue
            top = cy - ry * math.sqrt(1 - dx * dx); bot = cy + ry * math.sqrt(1 - dx * dx)
            if top <= y <= bot:
                ang = math.atan2((y - cy) / ry, dx)
                plank = int((ang + math.pi) / (2 * math.pi) * 24)
                col = WOODR[4] if plank % 2 else WOODR[3]
                q = math.hypot(dx, (y - cy) / ry)
                if q > 0.93:
                    col = GOLD[3] if y < cy else GOLD[2]
                elif q < 0.18:
                    col = WOODR[2]
                s.px(x, y, col)
            elif bot < y <= bot + 6:                                                  # čelo pódia
                k = y - bot
                col = WOODR[2] if k < 2 else WOODR[1] if dx < 0.5 else WOODR[0]
                if k == 1:
                    col = GOLD[2]
                s.px(x, y, col)
    for i in range(2):                                                               # schůdky
        for y in range(cy + ry + 2 + i * 4, cy + ry + 6 + i * 4):
            for x in range(cx - 9 + i * 0, cx + 10):
                s.px(x, y, WOODR[4] if y == cy + ry + 2 + i * 4 else WOODR[2])
    for k in range(0, 13):                                                           # zábradlí (zadní půlkruh)
        a = math.pi + k / 12 * math.pi
        px_, py_ = cx + math.cos(a) * (rx - 2), cy + math.sin(a) * (ry - 1)
        for y in range(int(py_) - 12, int(py_)):
            s.px(px_, y, GOLD[3] if math.cos(a) < 0 else GOLD[2])
        sphere(s, px_, py_ - 12, 1.2, 1.2, GOLD, amb=0.4)
    for a10 in range(0, 315):
        a = math.pi + a10 / 100
        if a > 2 * math.pi:
            break
        px_, py_ = cx + math.cos(a) * (rx - 2), cy + math.sin(a) * (ry - 1) - 12
        s.px(round(px_), round(py_), GOLD[4] if math.cos(a) < 0 else GOLD[3])
    mx, my = cx + 4, cy - 16                                                         # montáž: litinový pilíř
    for y in range(my, cy + 3):
        hw = 3.5 + max(0, y - (cy - 4)) * 0.9
        for x in range(int(mx - hw), int(mx + hw) + 1):
            u = (x - mx) / max(1, hw)
            s.px(x, y, IRON_[3] if u < -0.6 else IRON_[2] if u < -0.1 else IRON_[1] if u < 0.5 else IRON_[0])
    for x in range(mx - 6, mx + 7):
        s.px(x, cy + 3, IRON_[0])
    for a10 in range(0, 63):                                                         # ozubený věnec
        a = a10 / 10
        r = 7 + (0.8 if a10 % 2 else 0)
        s.px(mx + math.cos(a) * r, my + math.sin(a) * r * 0.45, GOLD[3] if math.sin(a) > 0 else GOLD[2])
    _tube(s, mx + 1, my - 2, 0.55, -0.35, 2, 13, 1.2, 1.2, IRON_[1:])                # osa protizávaží
    sphere(s, mx + 9, my - 7, 3.2, 3.2, IRON_, amb=0.3)                               # protizávaží
    ax, ay = -0.74, -0.67
    _tube(s, mx, my - 3, ax, ay, -12, 40, 4.0, 5.6, GOLD[1:4] + [GOLD[4]], bands=(-8, 6, 20, 33, 40),
          band_ramp=[EBONY[0], EBONY[1], EBONY[2], EBONY[3], EBONY[4]])
    _tube(s, mx + ay * 7, my - 3 - ax * 7, ax, ay, 4, 22, 1.4, 1.6, GOLD[2:])     # hledáček
    for k in range(3):                                                               # objímky hledáčku
        t = 8 + k * 7
        X, Y = mx + ax * t, my - 3 + ay * t
        s.line(X, Y, X + ay * 7, Y - ax * 7, EBONY[2])
    ex, ey = mx - ax * 14, my - 3 - ay * 14                                          # okulár
    _tube(s, ex, ey, 0.3, 0.95, 0, 5, 1.6, 1.6, [EBONY[1], EBONY[2], EBONY[3], EBONY[4]])
    for x in range(int(ex) + 4, int(ex) + 12):                                       # stolička pozorovatele
        s.px(x, cy - 2, VELVET[3]); s.px(x, cy - 1, VELVET[2])
    for y in range(cy, cy + 5):
        s.px(int(ex) + 5, y, WOODR[1]); s.px(int(ex) + 10, y, WOODR[1])
    return selout(s, 0.48, 0.82)


def great_orrery():
    """Velký orloj (model nebe): mahagonový podstavec s odkrytým soukolím za sklem (ozubená kola), mosazný sloup,
    tři naklopené prstence drah s ryskami, uprostřed zlaté slunce s paprsky. Planety obíhají v InteriorFxView."""
    W, H = 56, 66
    s = Spr(W, H)
    cx, cy = 28, 26
    dy0, dy1 = 46, H - 6                                                             # buben podstavce
    cylinder(s, cx - 13, cx + 13, dy0, dy1, WOODR[1:], amb=0.25)
    for x in range(cx - 13, cx + 14):
        u = (x - cx) / 13.5
        nz = math.sqrt(max(0, 1 - u * u))
        s.px(x, int(dy0 - nz * 2), GOLD[3]); s.px(x, int(dy0 + nz * 2), GOLD[2])     # horní lem
        s.px(x, int(dy1 + nz * 2), GOLD[1])
        for y in range(int(dy0 - nz * 2) + 1, int(dy0 + nz * 2)):
            s.px(x, y, WOODR[4] if u < 0.3 else WOODR[3])
    for y in range(dy0 + 4, dy1 - 2):                                                # okénko se soukolím
        for x in range(cx - 8, cx + 9):
            e = min(x - (cx - 8), cx + 8 - x, y - dy0 - 4, dy1 - 3 - y)
            s.px(x, y, GOLD[2] if e == 0 else BRONZE[1] if (x + y) % 2 else BRONZE[0])
    for (gx, gy, gr) in ((cx - 4, dy0 + 9, 2.6), (cx + 1, dy0 + 8, 1.8), (cx + 4, dy0 + 12, 2.4), (cx - 1, dy0 + 13, 1.6)):
        for a10 in range(0, 63, 2):
            a = a10 / 10
            r = gr + (0.8 if (a10 // 4) % 2 else 0)
            s.px(gx + math.cos(a) * r, gy + math.sin(a) * r, GOLD[3] if math.cos(a) < 0 else GOLD[1])
        s.px(gx, gy, GOLD[4])
    for fx in (cx - 11, cx, cx + 11):                                                # lví tlapy
        for y in range(dy1, H):
            for x in range(fx - 2, fx + 3):
                s.px(x, y, GOLD[3] if x < fx else GOLD[1])
        s.px(fx - 3, H - 1, GOLD[2]); s.px(fx + 3, H - 1, GOLD[1])
    cylinder(s, cx - 1, cx + 1, cy + 4, 44, GOLD[1:], amb=0.3)
    sphere(s, cx, 44, 3, 1.6, GOLD, amb=0.4)
    rings = [(21, 0.30, 0.25), (16, 0.55, -0.35), (11, 0.85, 0.0)]                   # prstence: r, zploštění, náklon
    for (r, fl, tilt) in rings:
        for a100 in range(0, 628):
            a = a100 / 100
            x = math.cos(a) * r; y = math.sin(a) * r * fl
            X = cx + x * math.cos(tilt) - y * math.sin(tilt) * 0.3
            Y = cy + y + x * math.sin(tilt) * 0.3
            front = math.sin(a) > 0
            col = GOLD[4] if front and math.cos(a) < 0 else GOLD[3] if front else GOLD[1]
            s.px(round(X), round(Y), col)
            if front:
                s.px(round(X), round(Y) + 1, GOLD[1])
            if a100 % 26 == 0:
                s.px(round(X), round(Y) + 1, GOLD[0])                                 # rysky
    for k in range(12):                                                              # paprsky slunce
        a = k / 12 * 2 * math.pi
        s.line(cx + math.cos(a) * 4, cy + math.sin(a) * 4, cx + math.cos(a) * 6.5, cy + math.sin(a) * 6.5, GOLD[3])
    sphere(s, cx, cy, 3.8, 3.8, [GOLD[1], GOLD[2], GOLD[3], GOLD[4], GOLD[5]], amb=0.55)
    return selout(s, 0.55, 0.85)


def grand_desk():
    """Stůl Řádu: masivní mahagonový stůl (86 px) se zelenou koženou vložkou a zlatým lemem, čelo se třemi
    vyřezávanými panely a zlatým znakem vah uprostřed. Za stolem trůn s vysokým čalouněným opěradlem a
    korunkou s vahami. Na stole: velký otevřený Registr nádob, svícen se třemi svíčkami, stojánek s razítky,
    přesýpací hodiny, štosy spisů převázané páskou, pečetní vosk, kalamář s brkem."""
    W, H = 92, 72
    s = Spr(W + 6, H)
    tx = 45                                                                          # trůn (za stolem)
    for y in range(0, 34):
        hw = 13 - (2 if y < 4 else 0)
        for x in range(tx - hw, tx + hw + 1):
            e = min(x - (tx - hw), tx + hw - x)
            col = WOODR[4] if e == 0 else WOODR[2] if e < 3 else VELVET[2] if (x + y) % 7 else VELVET[1]
            if e >= 3 and (x - tx) % 5 == 0 and y % 6 == 3:
                col = GOLD[3]                                                         # knoflíky čalounění
            if y < 3:
                col = WOODR[3]
            s.px(x, y, col)
    for x in range(tx - 6, tx + 7):                                                  # korunka s vahami
        s.px(x, 0, GOLD[3])
    s.vline(tx, 0, 5, GOLD[4])
    for d in (-5, 5):
        s.px(tx + d, 3, GOLD[3]); s.px(tx + d - 1, 4, GOLD[2]); s.px(tx + d + 1, 4, GOLD[2])
    top, th = 30, 20
    for y in range(top, top + th):                                                   # deska
        for x in range(0, W):
            e = min(x, W - 1 - x, y - top, top + th - 1 - y)
            if e == 0:
                col = WOODR[5] if y == top else WOODR[3]
            elif e < 3:
                col = WOODR[4] if e == 1 else GOLD[2]
            else:
                col = LEATHERG[2] if (x * 3 + y * 5) % 17 else LEATHERG[1]
            s.px(x, y, col)
    fy0, fy1 = top + th, H - 2
    for y in range(fy0, fy1):                                                        # čelo s panely
        for x in range(0, W):
            col = WOODR[2]
            if y == fy0:
                col = WOODR[4]
            for (p0, p1) in ((5, 27), (33, 59), (65, 87)):
                if p0 <= x <= p1 and fy0 + 3 <= y <= fy1 - 3:
                    e = min(x - p0, p1 - x, y - fy0 - 3, fy1 - 3 - y)
                    col = WOODR[1] if e == 0 else WOODR[3] if e == 1 and (x == p0 + 1 or y == fy0 + 4) else WOODR[2]
            s.px(x, y, col)
        for x in range(W, W + 6):
            if y - fy0 >= x - W - 6:
                s.px(x, y, WOODR[0])
    ecx, ecy = 46, (fy0 + fy1) // 2                                                  # znak vah na čele
    s.vline(ecx, ecy - 6, ecy + 5, GOLD[4])
    for x in range(ecx - 8, ecx + 9):
        s.px(x, ecy - 5, GOLD[3])
    for d in (-7, 7):
        s.line(ecx + d, ecy - 4, ecx + d - 3, ecy + 1, GOLD[2]); s.line(ecx + d, ecy - 4, ecx + d + 3, ecy + 1, GOLD[2])
        for x in range(ecx + d - 3, ecx + d + 4):
            s.px(x, ecy + 2, GOLD[4])
    for x in range(ecx - 3, ecx + 4):
        s.px(x, ecy + 6, GOLD[3])
    for x in range(W):
        s.px(x, H - 2, WOODR[1]); s.px(x, H - 1, WOODR[0])
    # Registr nádob
    bx0, bx1, by0, by1 = 30, 62, top + 4, top + 15
    for y in range(by0, by1):
        for x in range(bx0, bx1):
            mid = (bx0 + bx1) // 2
            col = PARCH[3] if x not in (mid, mid - 1) else PARCH[0]
            if (y - by0) % 2 == 1 and abs(x - mid) > 2 and x % 5 and y < by1 - 2:
                col = INK if (x + y) % 3 else c(0x3A3A48)
            s.px(x, y, col)
        s.px(bx0 - 1, y, VELVET[2]); s.px(bx1, y, VELVET[2])
    for x in range(bx0 - 1, bx1 + 1):
        s.px(x, by1, VELVET[1])
    s.line(bx1 - 4, by0 - 1, bx1 - 4, by1 + 3, RED[3])                                 # záložka
    # svícen (vlevo)
    cxs = 10
    for y in range(top - 4, top + 6):
        s.px(cxs, y, GOLD[3])
    for x in range(cxs - 5, cxs + 6):
        s.px(x, top - 4, GOLD[3])
    for d in (-5, 0, 5):
        for y in range(top - 10, top - 4):
            s.px(cxs + d, y, PAPER[3] if d < 1 else PAPER[2])
        s.px(cxs + d, top - 11, c(0x2A2A2A))
    for x in range(cxs - 3, cxs + 4):
        s.px(x, top + 6, GOLD[2])
    # stojánek s razítky (vpravo)
    for x in range(70, 86):
        s.px(x, top + 7, WOODR[4]); s.px(x, top + 8, WOODR[2])
    for k in range(5):
        x = 71 + k * 3
        for y in range(top + 2, top + 7):
            s.px(x, y, WOODR[3] if y < top + 4 else EBONY[3])
        s.px(x, top + 1, GOLD[3])
    # přesýpací hodiny
    hx = 66
    for y in range(top + 9, top + 18):
        d = abs(y - (top + 13.5))
        for x in range(hx - int(d * 0.5) - 1, hx + int(d * 0.5) + 2):
            s.px(x, y, c(0xE8D8A0) if (y > top + 14 and d < 3.5) or (y < top + 12 and d > 3) else c(0x9AB0C0))
    for x in range(hx - 3, hx + 4):
        s.px(x, top + 8, WOODR[4]); s.px(x, top + 18, WOODR[3])
    # štos spisů převázaný páskou
    for i in range(4):
        for x in range(14 - i, 26 - i):
            for y in range(top + 15 - i * 2, top + 17 - i * 2):
                s.px(x, y, PARCH[2] if y % 2 else PARCH[1])
    s.vline(19, top + 9, top + 16, RED[3])
    # kalamář + pečeť
    s.px(27, top + 5, INK); s.px(28, top + 5, INK); s.px(27, top + 4, c(0x2A2A34)); s.px(28, top + 4, c(0x2A2A34))
    s.px(64, top + 4, RED[2]); s.px(65, top + 4, RED[3]); s.px(64, top + 5, RED[1])
    return selout(s, 0.48, 0.82)


def monument_scales():
    """Monumentální váhy Řádu: stupňovitý mramorový podstavec, bronzový sloup s prstenci, nahoře zlatá koule
    a vahadlo s kladkami; řetězy ke dvěma miskám. Na levé misce hrst popela, na pravé modrý lumenský krystal.
    Popel je těžší – vahadlo je skloněné doleva."""
    W, H = 44, 76
    s = Spr(W, H)
    cx = 22
    for k, (hw, y0, y1) in enumerate(((14, H - 6, H), (11, H - 11, H - 6), (8, H - 15, H - 11))):   # stupně
        for y in range(y0, y1):
            for x in range(cx - hw, cx + hw + 1):
                col = MARBLE[5] if y == y0 else MARBLE[4] if x < cx + hw - 3 else MARBLE[3]
                if y == y1 - 1:
                    col = MARBLE[2]
                s.px(x, y, col)
    cylinder(s, cx - 2, cx + 2, 16, H - 15, BRONZE[1:], amb=0.3)
    for y in (24, 40, H - 18):
        for x in range(cx - 3, cx + 4):
            s.px(x, y, GOLD[3] if x < cx else GOLD[1])
    sphere(s, cx, 13, 3.6, 3.6, GOLD, amb=0.4)
    tilt = -0.14
    for x in range(2, W - 2):                                                        # vahadlo
        y = 15 + (x - cx) * tilt
        s.px(x, y, GOLD[4] if x < cx else GOLD[3]); s.px(x, y + 1, GOLD[1])
    sphere(s, cx, 15.5, 1.8, 1.8, GOLD, amb=0.4)
    for side, px_ in ((-1, 7), (1, W - 8)):
        top = 15 + (px_ - cx) * tilt
        bot = top + 24
        for t in range(int(top) + 2, int(bot)):                                       # řetízky
            for d in (-5, 5):
                xx = px_ + d * (t - top) / (bot - top)
                s.px(round(xx), t, GOLD[2] if t % 2 else GOLD[1])
        for x in range(px_ - 7, px_ + 8):                                            # miska
            dd = abs(x - px_) / 7
            s.px(x, bot, GOLD[4] if dd < 0.5 else GOLD[3]); s.px(x, bot + 1, GOLD[2])
            if dd < 0.85:
                s.px(x, bot + 2, GOLD[1])
            if dd < 0.6:
                s.px(x, bot + 3, GOLD[0])
        if side < 0:
            for x in range(px_ - 4, px_ + 5):                                        # popel
                s.px(x, bot - 1, c(0x8E8A84) if x % 2 else c(0x6E6A66))
            for x in range(px_ - 2, px_ + 3):
                s.px(x, bot - 2, c(0xA8A39C))
        else:
            for y in range(int(bot - 8), int(bot)):                                  # krystal
                hw = (y - (bot - 8)) * 0.45
                for x in range(int(px_ - hw), int(px_ + hw) + 1):
                    s.px(x, y, c(0xC8F4FF) if x < px_ - 0.5 else c(0x6ABCE8) if x < px_ + 1 else c(0x3A80C0))
    return selout(s, 0.5, 0.82)


def parquet_floor(img, x0, y0, x1, y1):
    """Mahagonová parketa stromeček (rybí kost): pásy po 12 px, prkénka šikmo střídavě, tmavé spáry, tóny
    podle prkénka, tu a tam lesklá hrana."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            band = (x - x0) // 12
            lx = (x - x0) % 12
            off = lx if band % 2 == 0 else 11 - lx
            k = (y + off) // 4
            seam = (y + off) % 4 == 0
            h = (band * 7919 + k * 104729) % 7
            base = [WOODR[2], WOODR[3], WOODR[2], WOODR[3], WOODR[3], WOODR[2], WOODR[4]][h]
            col = WOODR[1] if (seam or lx == 0) else base
            if not seam and lx == 1 and h == 6:
                col = WOODR[5]
            img[y, x] = col


def runner(img, x0, x1, y0, y1):
    """Úzký běhoun: vínový samet se zlatým lemem a drobným kosočtverečným vzorem."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            e = min(x - x0, x1 - 1 - x)
            col = VELVET[0] if e == 0 else GOLD[3] if e == 1 else VELVET[1] if e == 2 else VELVET[2]
            if e > 3 and (abs((x - (x0 + x1) // 2)) + y) % 8 == 0:
                col = GOLD[1]
            img[y, x] = col


def floor_emblem(img, cx, cy, r, pins=True):
    """Vykládaný znak Řádu v podlaze: mramorový kruh se zlatou obručí, 16 rysek okruhů 198–213 (213 rudá),
    hvězdicová růžice a uprostřed zlaté váhy; po obvodu mosazné kolíky Foucaultova kyvadla (některé povalené)."""
    for y in range(int(cy - r * 0.6) - 4, int(cy + r * 0.6) + 5):
        for x in range(int(cx - r) - 4, int(cx + r) + 5):
            q = math.hypot(x + .5 - cx, (y + .5 - cy) / 0.6)
            if q <= r - 3:
                m = MARBLE[3] if (x // 3 + y // 2) % 5 else MARBLE[4]
                a = math.atan2((y - cy) / 0.6, x - cx)
                star = abs(math.cos(a * 8)) ** 6 * (r - 6)
                if q < star and q > 5:
                    m = GOLD[1] if math.cos(a * 8) > 0 else MARBLE[2]
                img[y, x] = m
            elif q <= r + 1:
                ang = math.atan2((y - cy) / 0.6, x - cx)
                img[y, x] = GOLD[4] if (q > r - 1 and ang < 0) else GOLD[3] if q > r - 2 else GOLD[2]
            elif q <= r + 2:
                img[y, x] = WOODR[0]
    for i in range(16):
        a = i / 16 * 2 * math.pi - math.pi / 2
        for t in (r - 4, r - 5, r - 6):
            X, Y = int(cx + math.cos(a) * t), int(cy + math.sin(a) * t * 0.6)
            img[Y, X] = RED[3] if i == 15 else GOLD[4]
    for x in range(int(cx - 9), int(cx + 10)):                                      # váhy uprostřed
        img[int(cy - 3), x] = GOLD[4]
    for y in range(int(cy - 5), int(cy + 5)):
        img[y, int(cx)] = GOLD[4]
    for d in (-8, 8):
        for k in range(-3, 4):
            img[int(cy - 1), int(cx + d + k)] = GOLD[3]
        img[int(cy - 2), int(cx + d)] = GOLD[2]
    for x in range(int(cx - 4), int(cx + 5)):
        img[int(cy + 5), x] = GOLD[3]
    if pins:
        for i in range(24):
            a = i / 24 * 2 * math.pi
            X, Y = int(cx + math.cos(a) * (r + 5)), int(cy + math.sin(a) * (r + 5) * 0.6)
            if i in (5, 6, 17):                                                      # povalené kolíky
                img[Y, X] = GOLD[3]; img[Y, X + 1] = GOLD[2]; img[Y + 1, X] = WOODR[0]
            else:
                img[Y - 2, X] = GOLD[5]; img[Y - 1, X] = GOLD[3]; img[Y, X] = GOLD[2]; img[Y + 1, X] = WOODR[0]


def ornate_rug(img, x0, y0, x1, y1):
    """Koberec: vínové pole s medailonem, zlatý a modrý ornamentální lem, třásně na krátkých stranách."""
    w, h = x1 - x0, y1 - y0
    for y in range(y0, y1):
        for x in range(x0, x1):
            e = min(x - x0, x1 - 1 - x, y - y0, y1 - 1 - y)
            if e == 0:
                col = VELVET[0]
            elif e < 3:
                col = GOLD[2] if (x + y) % 4 else GOLD[1]
            elif e < 6:
                col = NAVY[3] if ((x + y) // 2) % 3 else GOLD[2]
            elif e == 6:
                col = GOLD[1]
            else:
                u, v = (x - (x0 + x1) / 2) / (w / 2), (y - (y0 + y1) / 2) / (h / 2)
                col = VELVET[2] if (x // 3 + y // 3) % 2 else VELVET[3]
                d = abs(u) + abs(v)
                if 0.32 < d < 0.4 or d < 0.12:
                    col = GOLD[2]
                elif d < 0.32:
                    col = NAVY[3] if (x + y) % 3 else NAVY[2]
            img[y, x] = col
    for x in range(x0 + 1, x1 - 1, 2):                                                # třásně
        img[y0 - 1, x] = PARCH[2]; img[y1, x] = PARCH[2]


def dome_wall2(W, H, windows=(), shelves=()):
    """Zadní stěna: dole mahagonový obklad s panely a zlatou lištou, výš tmavomodrá malovaná kopule se zlatými
    hvězdami a souhvězdími, mosazná žebra; římsa. Okna a regály se kreslí zvlášť."""
    s = Spr(W, H)
    pan = H - 30
    for y in range(H):
        for x in range(W):
            if y >= pan:
                ly = y - pan
                px_ = x % 18
                col = WOODR[3] if px_ in (0, 17) else WOODR[2]
                if 4 <= ly <= 24 and 3 <= px_ <= 14:
                    e = min(px_ - 3, 14 - px_, ly - 4, 24 - ly)
                    col = WOODR[1] if e == 0 else WOODR[4] if e == 1 and (px_ == 4 or ly == 5) else WOODR[2]
                if ly in (0, 1):
                    col = GOLD[3] if ly == 0 else GOLD[1]
                if ly >= 28:
                    col = WOODR[0]
            else:
                col = NAVY[2] if (_h(x // 4, y // 4, 4) % 6) else NAVY[1]
                if y > pan - 10:
                    col = NAVY[1] if (x + y) % 2 else NAVY[2]
                hh = _h(x, y, 8) % 173
                if hh == 0:
                    col = GOLD[4]
                elif hh == 1:
                    col = GOLD[2]
            s.px(x, y, col)
    consts = [((70, 14), (76, 10), (82, 13), (80, 19)), ((96, 12), (104, 8), (110, 13)), ((58, 44), (63, 38), (68, 42))]
    for pts in consts:
        for a, b in zip(pts, pts[1:]):
            for t in range(0, 11, 2):
                s.px(a[0] + (b[0] - a[0]) * t / 10, a[1] + (b[1] - a[1]) * t / 10, GOLD[1])
        for p in pts:
            s.px(p[0], p[1], GOLD[4])
    for x in range(W):                                                               # římsa
        s.px(x, 0, GOLD[4]); s.px(x, 1, GOLD[2]); s.px(x, 2, WOODR[1])
    return s


def grand_window(w=30, h=62):
    """Velké klenuté okno s mosaznými příčlemi: noční nebe, Měsíc v úplňku s krátery, hvězdy a v souhvězdí
    prázdný kroužek po chybějící hvězdě; těžké vínové závěsy po stranách se zlatou šňůrou, parapet."""
    s = Spr(w + 12, h)
    ox = 6
    rad = w / 2
    for y in range(h):
        for x in range(w):
            if y < rad and math.hypot(x + .5 - rad, y + .5 - rad) > rad:
                continue
            s.px(ox + x, y, GOLD[3] if x < 2 or y < 2 else GOLD[2])
    for y in range(2, h - 4):
        for x in range(2, w - 2):
            if y < rad and math.hypot(x + .5 - rad, y + .5 - rad) > rad - 2:
                continue
            t = y / h
            col = SKY[0] if t < 0.3 else SKY[1] if t < 0.6 else SKY[2] if t < 0.85 else SKY[3]
            hh = _h(x, y, 31) % 61
            if hh == 0:
                col = c(0xFFFFFF)
            elif hh < 2:
                col = c(0x8A9AC8)
            s.px(ox + x, y, col)
    mx, my, mr = ox + w * 0.36, h * 0.3, 6                                           # Měsíc
    for y in range(int(my - mr - 2), int(my + mr + 3)):
        for x in range(int(mx - mr - 2), int(mx + mr + 3)):
            d = math.hypot(x + .5 - mx, y + .5 - my)
            if d <= mr:
                v = 0.55 + 0.45 * max(0, -(x - mx) / mr * 0.5 - (y - my) / mr * 0.5 + 0.4)
                col = c(0xF4F2E6) if v > 0.85 else c(0xD8D6CA) if v > 0.7 else c(0xB0AEA4)
                if ((x - mx + 2) ** 2 + (y - my - 1) ** 2) < 3 or ((x - mx - 2) ** 2 + (y - my + 2) ** 2) < 2:
                    col = c(0x9A988E)
                s.px(x, y, col)
            elif d <= mr + 2 and s.opaque(x, y):
                s.px(x, y, SKY[3])
    for (sx, sy) in ((ox + 20, 14), (ox + 24, 20), (ox + 19, 25)):                    # souhvězdí + chybějící
        s.px(sx, sy, c(0xFFFFFF))
    gx, gy = ox + 23, 30
    for a in range(10):
        ang = a / 10 * 2 * math.pi
        s.px(gx + math.cos(ang) * 2.4, gy + math.sin(ang) * 2.4, c(0x4A3A5A))
    s.vline(ox + w // 2, int(rad), h - 5, GOLD[1])
    for y in (int(h * 0.5), int(h * 0.75)):
        s.hline(ox + 2, ox + w - 3, y, GOLD[1])
    for x in range(ox - 2, ox + w + 2):                                              # parapet
        s.px(x, h - 4, MARBLE[5]); s.px(x, h - 3, MARBLE[4]); s.px(x, h - 2, MARBLE[2])
    for side in (0, 1):                                                              # závěsy
        for y in range(0, h - 2):
            k = y / h
            wd = int(7 - 3 * math.sin(k * math.pi) * (1 if y > h * 0.45 else 0.3))
            for i in range(wd):
                X = i if side == 0 else w + 12 - 1 - i
                fold = (i + (y // 3) * 0) % 3
                col = VELVET[3] if fold == 0 else VELVET[2] if fold == 1 else VELVET[1]
                if i == wd - 1:
                    col = VELVET[0]
                s.px(X, y, col)
        ty = int(h * 0.55)
        for i in range(6):
            X = 2 + i if side == 0 else w + 12 - 3 - i
            s.px(X, ty, GOLD[4]); s.px(X, ty + 1, GOLD[2])
        sphere(s, 4 if side == 0 else w + 7, ty + 3, 1.5, 2, GOLD, amb=0.4)
    for x in range(w + 12):                                                          # garnýž
        s.px(x, 0, GOLD[4]); s.px(x, 1, GOLD[2])
    return selout(s, 0.5, 0.82)

"""
Grafika minihry Výheň (region 1, docs/adr/0118). Pixel art, ukládá se ×1 do drawable-nodpi a v aplikaci se zvětšuje
bez vyhlazení.
  forge_furnace.png  128 × 144  stará kulatá cihlová pec (úlová kopule): cihly v řadách po obvodu (zhušťují se k okrajům
                                 = hloubka), světlo zleva shora + žár zespodu, saze nad ústím, praskliny, mech, železná obruč,
                                 komín, kamenný sokl, polena v ústí
  forge_fire.png     4 × (40 × 30)  snímky ohně do ústí
  forge_bg.png       400 × 560   pozadí dílny ve stejném měřítku pixelů jako pec (kvádry, trám, komínový tah, dlažba, stín)
  forge_relics.png   3 × (40 × 40)  relikvie z přetavení (forge_relics.py): Srdce výhně, Kovadlina předků, Kladivo mistra
  forge_icons.png    20 × 20 v řádku, pořadí forge_icons.ICONS
"""
import math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable-nodpi")
rng = np.random.default_rng(118)


def rgba(h, w):
    return np.zeros((h, w, 4), np.float32)


def put(img, x, y, c, a=255):
    x, y = int(x), int(y)
    if 0 <= y < img.shape[0] and 0 <= x < img.shape[1]:
        img[y, x, :3] = c; img[y, x, 3] = a


def outline(img, col=(28, 16, 12)):
    a = img[:, :, 3] > 0
    n = np.zeros_like(a)
    n[1:] |= a[:-1]; n[:-1] |= a[1:]; n[:, 1:] |= a[:, :-1]; n[:, :-1] |= a[:, 1:]
    edge = n & ~a
    img[edge, :3] = col; img[edge, 3] = 255
    return img


def mix(a, b, t):
    return np.asarray(a, np.float32) * (1 - t) + np.asarray(b, np.float32) * t


# ════════════════════════════════════════════════════════════════════════════
#  PEC
# ════════════════════════════════════════════════════════════════════════════
FW, FH = 128, 144
CX, BASE, TOP, HALF = 64, 114, 26, 52          # kopule: střed, spodek, vrchol, poloviční šířka
MOUTH_W, MOUTH_H, MOUTH_Y = 40, 34, 112        # ústí (oblouk), spodní hrana


def dome_half(y):
    """Poloviční šířka kopule v řádku y – úl: spodních 16 px rovně, nahoře zakulacené."""
    if y > BASE or y < TOP:
        return -1
    h = BASE - 16 - TOP
    if y >= BASE - 16:
        return HALF + (y - (BASE - 16)) * 0.12              # lehce rozšířená pata
    t = (BASE - 16 - y) / h
    return HALF * math.sqrt(max(0.0, 1 - t ** 2.2))


def in_mouth(x, y):
    """Ústí: obdélník se segmentovým obloukem nahoře."""
    hw = MOUTH_W / 2
    if abs(x - CX) > hw or y > MOUTH_Y:
        return False
    arch_top = MOUTH_Y - MOUTH_H
    spring = MOUTH_Y - 18                                   # odkud začíná oblouk
    if y >= spring:
        return True
    r = hw
    return (x - CX) ** 2 + ((y - spring) * hw / (spring - arch_top)) ** 2 <= r * r


BRICK_PAL = [(150, 62, 40), (162, 72, 46), (140, 58, 42), (172, 84, 52), (128, 54, 38), (158, 90, 60), (118, 66, 50)]


def furnace():
    img = rgba(FH, FW)
    # ── sokl z kamenů ──
    for y in range(BASE - 2, 138):
        for x in range(6, 122):
            img[y, x, :3] = (96, 88, 80); img[y, x, 3] = 255
    stones = []
    x = 6
    row = 0
    for y0, h in ((BASE - 2, 12), (BASE + 10, 14)):
        x = 6 - (row * 9)
        while x < 122:
            w = int(rng.integers(14, 26))
            stones.append((x, y0, w, h)); x += w
        row += 1
    for (x0, y0, w, h) in stones:
        base = np.array([104, 96, 86]) * rng.uniform(0.85, 1.12) + np.array([6, 2, -2]) * rng.uniform(-1, 1)
        for y in range(y0, y0 + h):
            for x in range(max(6, x0), min(122, x0 + w)):
                if y >= 138:
                    continue
                # zaoblené rohy kamene
                edge = min(x - x0, x0 + w - 1 - x, y - y0, y0 + h - 1 - y)
                if edge < 0:
                    continue
                c = base * (0.82 if edge == 0 else 1.0)
                if y == y0 + 1:
                    c = c * 1.18                              # horní hrana na světle
                c = c * (1.06 - 0.25 * (x - 6) / 116)          # světlo zleva
                c = c + rng.normal(0, 4, 3)
                img[y, x, :3] = np.clip(c, 0, 255); img[y, x, 3] = 255
            img[y0 + h - 1 if y0 + h - 1 < 138 else 137, max(6, x0):min(122, x0 + w), :3] = (52, 46, 42)
        for y in range(y0, min(138, y0 + h)):
            if 6 <= x0 < 122:
                img[y, x0, :3] = (52, 46, 42)
    # stín kopule na soklu
    for x in range(10, 118):
        for y in range(BASE - 2, BASE + 3):
            img[y, x, :3] *= 0.55

    # ── kopule z cihel ──
    course_h = 6
    for y in range(TOP, BASE + 1):
        hw = dome_half(y)
        if hw <= 0:
            continue
        course = (BASE - y) // course_h
        yin = (BASE - y) % course_h
        for x in range(int(CX - hw), int(CX + hw) + 1):
            dx = (x - CX) / max(hw, 1)
            dx = max(-1.0, min(1.0, dx))
            ang = math.asin(dx)                               # úhel po obvodu – cihly zhuštěné k okrajům
            n_br = 22 - course // 3                           # nahoře méně cihel v řadě
            u = (ang / math.pi + 0.5) * n_br + (0.5 if course % 2 else 0)
            bi = int(math.floor(u)); fu = u - bi
            seed = (bi * 73 + course * 131) % 997
            col = np.array(BRICK_PAL[seed % len(BRICK_PAL)], np.float32)
            col += (seed % 7 - 3) * 2.5
            mortar = yin == 0 or fu < 0.06 * (1 + abs(dx) * 2.5)
            # normála kopule pro světlo
            ty = (BASE - 16 - y) / (BASE - 16 - TOP) if y < BASE - 16 else 0.0
            nx, ny = dx * math.sqrt(max(0.0, 1 - ty * ty * 0.6)), -ty
            nz = math.sqrt(max(0.05, 1 - nx * nx - ny * ny))
            L = np.array([-0.55, -0.6, 0.58]); L /= np.linalg.norm(L)
            lam = max(0.0, nx * L[0] + ny * L[1] + nz * L[2])
            shade = 0.42 + 0.72 * lam
            if mortar:
                c = np.array([86, 74, 66], np.float32) * shade
            else:
                c = col * shade
                if yin == 1:
                    c = c * 1.12                              # horní hrana cihly
                if yin == course_h - 1:
                    c = c * 0.86
            # saze nad ústím
            soot = max(0.0, 1 - abs(x - CX) / 26) * max(0.0, 1 - abs(y - (MOUTH_Y - MOUTH_H - 6)) / 30)
            c = mix(c, (34, 26, 24), min(0.85, soot * 1.1))
            # žár z ústí odspodu
            heat = max(0.0, 1 - math.hypot((x - CX) / 46, (y - MOUTH_Y) / 22))
            c = c + np.array([90, 40, 6]) * heat * 0.55
            # okrajová tma (hloubka) + mírný rim
            c = c * (0.72 + 0.28 * nz)
            img[y, x, :3] = np.clip(c + rng.normal(0, 3, 3), 0, 255); img[y, x, 3] = 255
    # vyštípnuté / tmavší cihly a praskliny
    for _ in range(26):
        y = int(rng.integers(TOP + 8, BASE - 4)); hw = dome_half(y)
        x = int(CX + rng.uniform(-hw + 4, hw - 4))
        if in_mouth(x, y) or abs(x - CX) < 24 and y > MOUTH_Y - MOUTH_H - 4:
            continue
        for dx in range(-2, 3):
            for dy in range(0, 2):
                if img[y + dy, x + dx, 3] > 0:
                    img[y + dy, x + dx, :3] *= 0.62
    for (x0, y0, steps) in ((40, 58, 16), (90, 46, 13), (78, 80, 10), (30, 90, 9)):
        x, y = x0, y0
        for _ in range(steps):
            img[y, x, :3] *= 0.4
            y += 1; x += int(rng.integers(-1, 2))
    # mech na patě vlevo a vpravo
    for _ in range(140):
        side = -1 if rng.uniform() < 0.65 else 1
        y = int(rng.integers(BASE - 22, BASE - 1))
        hw = dome_half(y)
        x = int(CX + side * (hw - rng.uniform(0, 9 if side < 0 else 5)))
        if img[y, x, 3] > 0 and not in_mouth(x, y):
            img[y, x, :3] = mix(img[y, x, :3], (74 + rng.integers(0, 30), 108 + rng.integers(0, 30), 46), 0.8)
    # železná obruč s nýty
    for x in range(FW):
        for yy in range(2):
            hw = dome_half(70 + yy)
            if abs(x - CX) <= hw:
                dx = (x - CX) / hw
                y = 70 + yy + int(round(2 * (1 - math.sqrt(max(0, 1 - dx * dx)))))  # obruč se stáčí dozadu
                sh = 0.55 + 0.55 * (1 - (dx + 1) / 2)
                img[y, x, :3] = np.array([92, 86, 84]) * sh if yy == 0 else np.array([52, 46, 46]) * sh
                img[y, x, 3] = 255
    for k in range(-4, 5):
        dx = k / 5
        x = int(CX + dx * dome_half(70)); y = 70 + int(round(2 * (1 - math.sqrt(1 - dx * dx))))
        img[y, x, :3] = (190, 180, 160)
    # rez pod obručí
    for x in range(int(CX - HALF + 6), int(CX + HALF - 6)):
        if rng.uniform() < 0.25:
            img[73 + int(rng.integers(0, 3)), x, :3] = mix(img[73, x, :3], (120, 60, 30), 0.6)

    # ── ústí: oblouk z cihel, uvnitř tma a polena ──
    arch_top = MOUTH_Y - MOUTH_H; spring = MOUTH_Y - 18
    for y in range(arch_top - 6, MOUTH_Y + 1):
        for x in range(CX - MOUTH_W // 2 - 6, CX + MOUTH_W // 2 + 7):
            if in_mouth(x, y):
                d = (MOUTH_Y - y) / MOUTH_H
                img[y, x, :3] = mix((70, 20, 8), (16, 6, 6), d ** 0.6); img[y, x, 3] = 255
    # klenáky: prstenec světlého kamene kolem oblouku, spáry míří do středu
    hw = MOUTH_W / 2
    sy = (spring - arch_top) / hw
    for y in range(arch_top - 9, spring + 1):
        for x in range(CX - int(hw) - 8, CX + int(hw) + 9):
            dx, dy = x - CX, (spring - y) / sy
            r = math.hypot(dx, dy)
            if hw < r <= hw + 6.5 and y <= spring:
                a = math.atan2(dy, dx)
                k = a / math.pi * 11
                c = np.array([188, 170, 140]) if abs(k - 5.5) < 0.5 else np.array([166, 132, 104])
                c = c * (1.08 - 0.35 * (dx + hw) / (2 * hw + 12))
                if abs(k - round(k)) < 0.09 or r > hw + 5.8:
                    c = np.array([64, 52, 46])
                put(img, x, y, np.clip(c, 0, 255))
    # boční ostění
    for y in range(spring, MOUTH_Y + 1):
        for side in (-1, 1):
            for k in range(1, 6):
                x = CX + side * (hw + k)
                c = np.array([176, 160, 132]) * (0.95 if side < 0 else 0.7) * (0.85 if (y // 4) % 2 else 1)
                if k == 5 or y % 4 == 0:
                    c = np.array([70, 58, 52])
                put(img, x, y, c)
    # polena
    for (x0, x1, y0) in ((CX - 15, CX + 10, MOUTH_Y - 4), (CX - 8, CX + 16, MOUTH_Y - 7)):
        for x in range(x0, x1):
            for dy in range(3):
                c = (96, 62, 36) if dy == 0 else (70, 44, 26) if dy == 1 else (48, 30, 20)
                put(img, x, y0 + dy, c)
        put(img, x1 - 1, y0 + 1, (176, 134, 86)); put(img, x1 - 1, y0, (150, 110, 70))
    # práh ústí
    for x in range(CX - int(hw) - 6, CX + int(hw) + 7):
        put(img, x, MOUTH_Y + 1, (150, 138, 120)); put(img, x, MOUTH_Y + 2, (96, 86, 76))

    # ── komín ──
    cx0, cx1, ctop = 72, 88, 4
    for y in range(ctop, 44):
        for x in range(cx0, cx1):
            if img[y, x, 3] > 0 and y > 34:
                continue
            row = (44 - y) // 5; yin = (44 - y) % 5
            u = (x - cx0 + (3 if row % 2 else 0)) // 7
            col = np.array(BRICK_PAL[(u * 3 + row * 5) % len(BRICK_PAL)], np.float32)
            sh = 1.15 - 0.55 * (x - cx0) / (cx1 - cx0)
            c = np.array([80, 70, 64]) if yin == 0 or (x - cx0 + (3 if row % 2 else 0)) % 7 == 0 else col
            c = mix(c * sh, (30, 24, 22), max(0.0, 1 - (y - ctop) / 12) * 0.55)   # saze u ústí komína
            img[y, x, :3] = np.clip(c, 0, 255); img[y, x, 3] = 255
    for x in range(cx0 - 2, cx1 + 2):
        for y in (ctop - 2, ctop - 1):
            img[y, x, :3] = (150, 140, 126) if y == ctop - 2 else (96, 88, 80); img[y, x, 3] = 255
    for x in range(cx0 + 2, cx1 - 2):
        img[ctop, x, :3] = (14, 10, 10)
    # větrací otvor vlevo
    for y in range(84, 90):
        for x in range(24, 30):
            put(img, x, y, (22, 12, 10) if 85 <= y <= 88 and 25 <= x <= 28 else (176, 160, 132))
    return outline(img)


def furnace_mini(W=40, H=48, fire=True):
    """Stejná pec jako v minihře, zmenšená pro interiér Staré kovárny (gen_smithy.py): úlová kopule z cihel
    (řady po 3 px, zhuštěné k okrajům), kamenný sokl, klenuté ústí s klenáky a žárem, železná obruč, komín, mech."""
    img = rgba(H, W)
    cx = W / 2 - 0.5
    base = H - 9                      # pata kopule
    top = round(H * 0.22)
    half = W / 2 - 3
    r = np.random.default_rng(4747)

    def dh(y):
        if y > base or y < top: return -1
        if y >= base - 5: return half + (y - (base - 5)) * 0.15
        t = (base - 5 - y) / (base - 5 - top)
        return half * math.sqrt(max(0.0, 1 - t ** 2.2))
    # sokl ze dvou řad kamenů
    for (y0, hh, off) in ((base - 1, 5, 0), (base + 4, 5, 3)):
        x = 1 - off
        while x < W - 1:
            w_ = int(r.integers(6, 10))
            tint = r.uniform(0.86, 1.12)
            for y in range(y0, min(H, y0 + hh)):
                for xx in range(max(1, x), min(W - 1, x + w_)):
                    edge = xx == x or y == y0 + hh - 1
                    c = np.array([104, 96, 86]) * tint * (1.18 if y == y0 else 1.0) * (1.06 - 0.25 * xx / W)
                    if edge: c = np.array([52, 46, 42])
                    img[y, xx, :3] = np.clip(c, 0, 255); img[y, xx, 3] = 255
            x += w_
    for xx in range(3, W - 3):
        for y in range(base - 1, base + 1):
            if img[y, xx, 3]: img[y, xx, :3] *= 0.6
    # kopule
    L_ = np.array([-0.55, -0.6, 0.58]); L_ /= np.linalg.norm(L_)
    mouth_w, mouth_h, mouth_y = round(W * 0.31), round(W * 0.29), base - 1
    spring = mouth_y - 6
    def in_mouth(x, y):
        if abs(x - cx) > mouth_w / 2 or y > mouth_y: return False
        if y >= spring: return True
        return (x - cx) ** 2 + ((y - spring) * (mouth_w / 2) / (spring - (mouth_y - mouth_h))) ** 2 <= (mouth_w / 2) ** 2
    for y in range(top, base + 1):
        hw = dh(y)
        if hw <= 0: continue
        course = (base - y) // 3; yin = (base - y) % 3
        for x in range(int(round(cx - hw)), int(round(cx + hw)) + 1):
            dx = max(-1.0, min(1.0, (x - cx) / max(hw, 1)))
            ang = math.asin(dx)
            u = (ang / math.pi + 0.5) * (11 - course // 4) + (0.5 if course % 2 else 0)
            bi = int(math.floor(u)); fu = u - bi
            seed = (bi * 73 + course * 131) % 997
            col = np.array(BRICK_PAL[seed % len(BRICK_PAL)], np.float32)
            mortar = yin == 0 or fu < 0.09 * (1 + abs(dx) * 2.5)
            ty = (base - 5 - y) / (base - 5 - top) if y < base - 5 else 0.0
            nx, ny = dx * math.sqrt(max(0.0, 1 - ty * ty * 0.6)), -ty
            nz = math.sqrt(max(0.05, 1 - nx * nx - ny * ny))
            lam = max(0.0, nx * L_[0] + ny * L_[1] + nz * L_[2])
            c = (np.array([86, 74, 66], np.float32) if mortar else col * (1.1 if yin == 2 else 1.0)) * (0.42 + 0.72 * lam)
            soot = max(0.0, 1 - abs(x - cx) / 9) * max(0.0, 1 - abs(y - (mouth_y - mouth_h - 2)) / 10)
            c = mix(c, (34, 26, 24), min(0.85, soot * 1.1))
            heat = max(0.0, 1 - math.hypot((x - cx) / 16, (y - mouth_y) / 8))
            c = c + np.array([90, 40, 6]) * heat * 0.55
            c = c * (0.72 + 0.28 * nz)
            img[y, x, :3] = np.clip(c, 0, 255); img[y, x, 3] = 255
    # železná obruč
    yb = int(top + (base - top) * 0.45)
    for x in range(W):
        hw = dh(yb)
        if abs(x - cx) <= hw - 0.5:
            sh = 0.55 + 0.55 * (1 - (x - cx + hw) / (2 * hw))
            img[yb, x, :3] = np.array([96, 90, 88]) * sh; img[yb + 1, x, :3] = np.array([50, 44, 44]) * sh
    for k in (-2, 0, 2):
        img[yb, int(cx + k * half / 3), :3] = (190, 180, 160)
    # ústí: tma, žár a polena, kolem klenáky
    for y in range(mouth_y - mouth_h - 3, mouth_y + 1):
        for x in range(int(cx - mouth_w / 2 - 3), int(cx + mouth_w / 2 + 4)):
            if in_mouth(x, y):
                d = (mouth_y - y) / mouth_h
                img[y, x, :3] = mix((70, 20, 8), (16, 6, 6), d ** 0.6); img[y, x, 3] = 255
    hwm = mouth_w / 2; sy = (spring - (mouth_y - mouth_h)) / hwm
    for y in range(mouth_y - mouth_h - 3, spring + 1):
        for x in range(int(cx - hwm - 3), int(cx + hwm + 4)):
            dx, dy = x - cx, (spring - y) / sy
            rr_ = math.hypot(dx, dy)
            if hwm < rr_ <= hwm + 2.4:
                a = math.atan2(dy, dx); k = a / math.pi * 7
                c = np.array([176, 150, 120]) * (1.08 - 0.35 * (dx + hwm) / (2 * hwm + 4))
                if abs(k - round(k)) < 0.12: c = np.array([64, 52, 46])
                put(img, x, y, np.clip(c, 0, 255))
    for y in range(spring, mouth_y + 1):
        for side in (-1, 1):
            x = int(round(cx + side * (hwm + 1)))
            put(img, x, y, (176, 160, 132) if side < 0 else (122, 110, 92))
            put(img, int(round(cx + side * (hwm + 2))), y, (70, 58, 52))
    if fire:
        for x in range(int(cx - hwm + 1), int(cx + hwm)):
            hgt = 3 + 6 * math.exp(-((x - cx) / 4.5) ** 2) + r.uniform(-1, 1)
            for k in range(int(hgt)):
                t = k / hgt
                put(img, x, mouth_y - 1 - k, (255, 240, 170) if t < 0.3 and abs(x - cx) < 3 else (255, 170, 40) if t < 0.65 else (210, 70, 20))
    for x in range(int(cx - hwm + 1), int(cx + hwm)):
        put(img, x, mouth_y, (96, 62, 36) if x % 3 else (150, 110, 70))
    # komín (vpravo od vrcholu, jako u velké pece)
    c0, c1 = int(cx + 3), int(cx + 9)
    for y in range(0, top + 6):
        for x in range(c0, c1):
            if img[y, x, 3] and y > top: continue
            row = (top + 6 - y) // 3; yin = (top + 6 - y) % 3
            col = np.array(BRICK_PAL[((x - c0) // 3 * 3 + row * 5) % len(BRICK_PAL)], np.float32)
            c = np.array([80, 70, 64]) if yin == 0 or (x - c0 + (1 if row % 2 else 0)) % 3 == 0 else col
            c = c * (1.15 - 0.55 * (x - c0) / (c1 - c0))
            img[y, x, :3] = np.clip(c, 0, 255); img[y, x, 3] = 255
    # mech u paty
    for _ in range(26):
        side = -1 if r.uniform() < 0.65 else 1
        y = int(r.integers(base - 8, base)); hw = dh(y)
        x = int(round(cx + side * (hw - r.uniform(0, 3))))
        if 0 <= x < W and img[y, x, 3] and not in_mouth(x, y):
            img[y, x, :3] = mix(img[y, x, :3], (80 + r.integers(0, 30), 112 + r.integers(0, 30), 48), 0.8)
    return outline(img)


def fire_frames():
    frames = []
    w, h = 40, 30
    for f in range(4):
        img = rgba(h, w)
        r = np.random.default_rng(900 + f)
        for x in range(w):
            base = 4 + 22 * math.exp(-((x - w / 2) / 11) ** 2)
            hgt = base * (0.7 + 0.3 * math.sin(x * 0.9 + f * 1.7)) + r.uniform(-2, 2)
            for y in range(h):
                d = (h - 1 - y)
                if d > hgt:
                    continue
                t = d / max(hgt, 1)
                c = mix((255, 246, 190), (255, 170, 40), min(1, t * 1.6)) if t < 0.6 else mix((255, 170, 40), (200, 50, 20), (t - 0.6) / 0.4)
                a = 255 if t < 0.85 else 190
                img[y, x, :3] = c; img[y, x, 3] = a
        for _ in range(6):
            x = int(r.integers(6, w - 6)); y = int(r.integers(0, h // 2))
            img[y, x, :3] = (255, 220, 120); img[y, x, 3] = 255
        frames.append(img)
    return np.concatenate(frames, axis=1)


# ════════════════════════════════════════════════════════════════════════════
#  DÍLNA (pozadí ve stejném měřítku pixelů jako pec)
# ════════════════════════════════════════════════════════════════════════════
OFF = 160                         # zeď navíc nad pecí, aby pozadí sahalo až k horní hraně obrazovky
BW, BH = 400, 400 + OFF           # pozadí: 1 pixel = 1 pixel pece (v aplikaci stejné celočíselné měřítko)
BFX, BFY = 136, 138 + OFF         # kam v pozadí padne levý horní roh pece (pec je uprostřed: 200 − 64)
WALL_END = 262 + OFF              # spodek zdi (patní lišta)
FLOOR_CONTACT = BFY + 138         # řádek, na kterém stojí sokl pece (276)
FIRE_X, FIRE_Y = BFX + 64, BFY + 96


def _smooth(t):
    t = np.clip(t, 0, 1)
    return t * t * (3 - 2 * t)


def _bevel_block(img, x0, y0, w, h, base, rr, lift=1.0):
    """Kámen/dlaždice s hranami: světlá horní a levá hrana, tmavá spodní a pravá, šum a skvrny."""
    for y in range(max(0, y0), min(BH, y0 + h)):
        for x in range(max(0, x0), min(BW, x0 + w)):
            ex, ey = x - x0, y - y0
            c = np.array(base, np.float32)
            if ey == 0: c = c * 1.22
            elif ex == 0: c = c * 1.12
            elif ey >= h - 2: c = c * 0.70
            elif ex >= w - 1: c = c * 0.80
            else:
                # jemné zrno a pozvolné ztmavení ke spodku kamene
                c = c * (1.04 - 0.14 * ey / max(h, 1))
            c = c + rr.normal(0, 2.6, 3)
            img[y, x, :3] = np.clip(c * lift, 0, 255); img[y, x, 3] = 255


def background():
    rr = np.random.default_rng(2118)
    img = rgba(BH, BW)
    MORT_W = np.array([30, 25, 27], np.float32)

    # ── zeď: kamenné kvádry nestejné velikosti, každý jiný odstín ──
    y = 0
    while y < WALL_END:
        h = int(rr.integers(13, 19))
        h = min(h, WALL_END - y)
        x = -int(rr.integers(0, 30))
        while x < BW:
            w = int(rr.integers(24, 46))
            tint = rr.uniform(0.86, 1.14)
            cool = rr.uniform(-6, 6)
            base = (np.array([70, 62, 62]) * tint + np.array([cool, 0, -cool]))
            _bevel_block(img, x + 1, y + 1, w - 1, h - 1, base, rr)
            for xx in range(max(0, x), min(BW, x + w + 1)):
                img[y, xx, :3] = MORT_W; img[y, xx, 3] = 255            # vodorovná spára
            for yy in range(y, min(BH, y + h)):
                if 0 <= x < BW: img[yy, x, :3] = MORT_W; img[yy, x, 3] = 255   # svislá spára
            # odštípnutý roh, drobné praskliny
            if rr.uniform() < 0.28:
                cx0 = x + (w - 4 if rr.uniform() < 0.5 else 2); cy0 = y + 2
                for i in range(3):
                    for j in range(3 - i):
                        if 0 <= cx0 + i < BW and 0 <= cy0 + j < BH: img[cy0 + j, cx0 + i, :3] *= 0.55
            if rr.uniform() < 0.22:
                px_, py_ = x + int(rr.integers(4, max(5, w - 4))), y + 3
                for k in range(int(rr.integers(4, 9))):
                    if 0 <= px_ < BW and 0 <= py_ < BH: img[py_, px_, :3] *= 0.5
                    py_ += 1; px_ += int(rr.integers(-1, 2))
            x += w
        y += h

    # svislé zasmrádlé pruhy od stropu
    for _ in range(16):
        x0 = int(rr.integers(0, BW)); wd = int(rr.integers(4, 12)); ln = int(rr.integers(50, 150))
        for yy in range(0, min(ln, WALL_END)):
            f = 1.0 - 0.32 * (1 - yy / ln)
            for xx in range(x0, min(BW, x0 + wd)):
                img[yy, xx, :3] *= f

    # ── komínový tah z cihel navazující na komín pece (pec: x 72–88, vrchol y 4) ──
    fx0, fx1 = BFX + 72, BFX + 88
    for yy in range(0, BFY + 6):
        for xx in range(fx0, fx1):
            row = (BFY + 44 - yy) // 5; yin = (BFY + 44 - yy) % 5
            u = (xx - fx0 + (3 if row % 2 else 0)) // 7
            col = np.array(BRICK_PAL[(u * 3 + row * 5) % len(BRICK_PAL)], np.float32)
            sh = 1.15 - 0.55 * (xx - fx0) / (fx1 - fx0)
            c = np.array([80, 70, 64]) if yin == 0 or (xx - fx0 + (3 if row % 2 else 0)) % 7 == 0 else col
            c = mix(c * sh, (30, 24, 22), 0.25 + 0.5 * (1 - min(1, yy / 120)) * 0.0)
            soot = max(0.0, 1 - (BFY + 6 - yy) / 150) * 0.45
            c = mix(c, (26, 20, 20), soot)
            img[yy, xx, :3] = np.clip(c + rr.normal(0, 2.5, 3), 0, 255); img[yy, xx, 3] = 255
        img[yy, fx0 - 1, :3] = (24, 16, 14); img[yy, fx1, :3] = (24, 16, 14)
    for yy in (40, 96):                                                     # železné objímky
        for xx in range(fx0 - 2, fx1 + 2):
            img[yy, xx, :3] = (96, 90, 90); img[yy + 1, xx, :3] = (50, 46, 48); img[yy - 1, xx, :3] = (140, 132, 126)
        img[yy, fx0 - 2, :3] = (190, 180, 160); img[yy, fx1 + 1, :3] = (190, 180, 160)

    # saze na zdi nad pecí
    for yy in range(0, BFY + 10):
        for xx in range(BFX - 20, BFX + 150):
            d = math.hypot((xx - (BFX + 80)) / 70.0, (yy - (BFY + 30)) / 130.0)
            k = max(0.0, 1 - d) ** 1.6 * 0.42
            if k > 0: img[yy, xx, :3] = mix(img[yy, xx, :3], (20, 16, 18), k)

    # ── strop: dubový trám, pod ním stín ──
    for yy in range(0, 24):
        for xx in range(BW):
            g = 0.5 + 0.5 * math.sin(xx * 0.13 + math.sin(yy * 0.9 + xx * 0.02) * 2.0)
            c = np.array([92, 60, 36]) * (0.78 + 0.22 * g)
            if yy < 2: c = c * 1.3
            if yy >= 20: c = c * (0.55 - 0.1 * (yy - 20))
            img[yy, xx, :3] = np.clip(c + rr.normal(0, 2.4, 3), 0, 255); img[yy, xx, 3] = 255
    for xx in range(24, BW, 52):                                            # hřeby / kování
        for dy in range(2):
            img[10 + dy, xx, :3] = (42, 36, 34); img[9, xx, :3] = (120, 110, 100)
    for yy in range(24, 44):
        k = 0.55 * (1 - (yy - 24) / 20)
        img[yy, :, :3] *= (1 - k)

    # ── nářadí: kleště, kladivo, podkovy (vlevo na zdi, vpravo nahoře) ──
    O = OFF
    def metal(x, y0, y1, c=(120, 116, 114)):
        for yy in range(y0, y1):
            img[yy, x, :3] = np.array(c) * 1.1; img[yy, x + 1, :3] = np.array(c) * 0.7
    for xx in (60, 66):
        metal(xx, O + 70, O + 118)
    for xx in range(56, 72): img[O + 118, xx, :3] = (90, 84, 82)
    for yy in range(O + 74, O + 118): img[yy, 61, :3] = (74, 70, 70)
    for yy in range(O + 66, O + 112):                                      # kladivo
        img[yy, 100, :3] = (130, 88, 54); img[yy, 101, :3] = (88, 58, 36); img[yy, 99, :3] = (160, 112, 70)
    for yy in range(O + 62, O + 70):
        for xx in range(92, 110):
            img[yy, xx, :3] = (132, 128, 126) if yy > O + 62 else (172, 168, 162)
            if yy == O + 69: img[yy, xx, :3] = (70, 66, 66)
    for cx_ in (290, 314, 338):                                            # podkovy
        for a in np.linspace(0.25, math.pi - 0.25, 26):
            for r_ in (7, 8):
                xx = int(cx_ + math.cos(a) * r_); yy = int(O + 84 - math.sin(a) * r_ * 1.15)
                img[yy, xx, :3] = (150, 144, 138) if r_ == 8 else (96, 90, 88)
        img[O + 78, cx_ - 6, :3] = (24, 18, 16)

    # ── police s lahvičkami vlevo ──
    for xx in range(76, 140):
        for yy in range(O + 150, O + 156):
            img[yy, xx, :3] = np.array([108, 74, 44]) * (1.15 if yy == O + 150 else 0.8 if yy >= O + 154 else 1.0)
    for i, bx in enumerate((82, 96, 110, 122)):
        col = ((74, 150, 96), (180, 70, 50), (90, 120, 190), (210, 176, 70))[i]
        for yy in range(O + 134, O + 150):
            for xx in range(bx, bx + 9):
                edge = xx in (bx, bx + 8) or yy == O + 149
                neck = yy < O + 138 and not (bx + 3 <= xx <= bx + 5)
                if neck: continue
                img[yy, xx, :3] = np.array(col) * (0.55 if edge else 1.0 - 0.25 * (xx - bx) / 9)
        img[O + 141, bx + 2, :3] = (240, 240, 235)
        img[O + 132, bx + 3:bx + 6, :3] = (110, 80, 50)
    for xx in range(78, 138, 8):
        img[O + 155:O + 158, xx, :3] = (40, 28, 20)                        # konzoly

    # ── podlaha: dlažba v perspektivě (řady rostou směrem dolů) ──
    FLOOR_BASE = np.array([96, 84, 74], np.float32)
    y = WALL_END; r = 0
    while y < BH:
        h = int(round(9 + r * 1.5))
        x = -int(rr.integers(0, 26)) - (r % 2) * 13
        while x < BW:
            w = int(round(rr.integers(24, 40) + r * 3.2))
            tint = rr.uniform(0.84, 1.12)
            warm = rr.uniform(-4, 8)
            base = FLOOR_BASE * tint + np.array([warm, warm * 0.4, -warm * 0.6])
            _bevel_block(img, x + 1, y + 1, w - 1, min(h - 1, BH - y - 1), base, rr)
            for xx in range(max(0, x), min(BW, x + w + 1)): img[y, xx, :3] = (38, 31, 28); img[y, xx, 3] = 255
            for yy in range(y, min(BH, y + h)):
                if 0 <= x < BW: img[yy, x, :3] = (38, 31, 28); img[yy, x, 3] = 255
            if rr.uniform() < 0.3:                                          # prasklina na dlaždici
                px_, py_ = x + int(rr.integers(3, max(4, w - 3))), y + 2
                for k in range(int(rr.integers(3, h - 2))):
                    if 0 <= px_ < BW and 0 <= py_ < BH: img[py_, px_, :3] *= 0.5
                    py_ += 1; px_ += int(rr.integers(-1, 2))
            x += w
        y += h; r += 1
    # patní lišta zdi + stín zdi na podlaze
    for xx in range(BW):
        for k in range(4):
            yy = WALL_END - 4 + k
            img[yy, xx, :3] = np.array([54, 46, 44]) * (1.25 if k == 0 else 0.8 if k == 3 else 1.0)
    for yy in range(WALL_END, WALL_END + 14):
        img[yy, :, :3] *= (0.48 + 0.52 * (yy - WALL_END) / 14)
    # drobnosti na podlaze: uhlíky, popel, sláma
    for _ in range(170):
        xx = int(rr.integers(0, BW)); yy = int(rr.integers(WALL_END + 6, BH))
        t = rr.uniform()
        if t < 0.55: img[yy, xx, :3] = (24, 20, 20); img[yy, min(BW - 1, xx + 1), :3] = (34, 28, 28)
        elif t < 0.75: img[yy, xx, :3] = (168, 156, 140)
        elif t < 0.9:
            for k in range(4): img[min(BH - 1, yy + (k > 1)), min(BW - 1, xx + k), :3] = (176, 146, 82)
        else: img[yy, xx, :3] = (230, 120, 40)

    # ── světlo: žár z ústí pece, tma v rozích ──
    yy_, xx_ = np.mgrid[0:BH, 0:BW].astype(np.float32)
    glow = np.exp(-(((xx_ - FIRE_X) / 120.0) ** 2 + ((yy_ - FIRE_Y) / 105.0) ** 2))
    vign = 0.68 + 0.32 * _smooth((yy_ + 20) / 120.0)
    side = 0.80 + 0.20 * np.exp(-((xx_ - 200) / 170.0) ** 2)
    L = (0.62 + 0.58 * glow) * vign * side
    L = np.where(yy_ > WALL_END, L * 1.05, L)
    rgb = img[:, :, :3] * L[:, :, None]
    rgb += glow[:, :, None] ** 1.4 * np.array([62, 22, 0], np.float32)
    # světelná kaluž na podlaze před pecí (ve stylu ořezaná, dvě úrovně)
    pool = np.exp(-(((xx_ - FIRE_X) / 100.0) ** 2 + ((yy_ - (FLOOR_CONTACT + 20)) / 24.0) ** 2))
    pool = np.where(yy_ > WALL_END, pool, 0)
    rgb += pool[:, :, None] * np.array([70, 30, 4], np.float32) * 0.9
    # kontaktní stín pod pecí (tvrdá elipsa se dvěma stupni)
    sh = ((xx_ - FIRE_X) / 72.0) ** 2 + ((yy_ - FLOOR_CONTACT) / 8.0) ** 2
    rgb = np.where((sh <= 1.0)[:, :, None], rgb * 0.38, np.where((sh <= 1.55)[:, :, None] & (yy_ >= FLOOR_CONTACT - 3)[:, :, None], rgb * 0.62, rgb))
    # stín pece na zdi vlevo/vpravo (okluze kolem kopule)
    d = np.hypot((xx_ - FIRE_X) / 62.0, (yy_ - (BFY + 70)) / 72.0)
    ao = np.clip((d - 1.0) / 0.35, 0, 1)
    rgb = np.where((yy_ < FLOOR_CONTACT)[:, :, None], rgb * (0.80 + 0.20 * ao)[:, :, None], rgb)
    out = np.zeros((BH, BW, 4), np.float32)
    out[:, :, :3] = (np.clip(rgb, 0, 255) // 3) * 3                           # jemná kvantizace barev (pixelartový vzhled)
    out[:, :, 3] = 255
    return out


import forge_relics  # noqa: E402  (relikvie 40 × 40, tools/mapgen/forge_relics.py)


import forge_icons  # noqa: E402  (ikony 20 × 20 ve stylu předloh)
ICONS = forge_icons.ICONS


def save(img, name):
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, name))


if __name__ == "__main__":
    save(furnace(), "forge_furnace.png")
    save(fire_frames(), "forge_fire.png")
    save(background(), "forge_bg.png")
    save(forge_relics.sheet(), "forge_relics.png")
    save(furnace_mini(24, 28), "forge_furnace_icon.png")      # ikona Staré kovárny na mapě zóny
    save(forge_icons.atlas(), "forge_icons.png")
    print("ikony:", ", ".join(f"{i}={n}" for i, (n, _) in enumerate(ICONS)))

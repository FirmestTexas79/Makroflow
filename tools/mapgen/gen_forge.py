"""
Grafika minihry Výheň (region 1, docs/adr/0118). Pixel art, ukládá se ×1 do drawable-nodpi a v aplikaci se zvětšuje
bez vyhlazení.
  forge_furnace.png  128 × 144  stará kulatá cihlová pec (úlová kopule): cihly v řadách po obvodu (zhušťují se k okrajům
                                 = hloubka), světlo zleva shora + žár zespodu, saze nad ústím, praskliny, mech, železná obruč,
                                 komín, kamenný sokl, polena v ústí
  forge_fire.png     4 × (40 × 30)  snímky ohně do ústí
  forge_bg.png       200 × 120   pozadí dílny (kamenná zeď, trámy, nářadí, podlaha)
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


def background():
    w, h = 200, 120
    img = rgba(h, w)
    # kamenná zeď
    for y in range(h):
        for x in range(w):
            row = y // 9; off = (row % 2) * 7
            bx = (x + off) // 15
            seed = (bx * 31 + row * 17) % 23
            c = np.array([58, 50, 48]) + seed * 0.9
            if y % 9 == 0 or (x + off) % 15 == 0:
                c = np.array([34, 28, 28])
            img[y, x, :3] = c; img[y, x, 3] = 255
    # trámy
    for x in range(w):
        for y in range(4, 11):
            img[y, x, :3] = (74, 48, 30) if y not in (4, 10) else (40, 26, 18)
    for x0 in (20, 178):
        for y in range(10, 96):
            for x in range(x0, x0 + 8):
                img[y, x, :3] = (82, 54, 34) if x not in (x0, x0 + 7) else (42, 28, 18)
    # nářadí na zdi: kleště, kladivo, podkova
    for y in range(22, 46):
        put(img, 40, y, (110, 106, 104)); put(img, 44, y, (110, 106, 104))
    for x in range(36, 49):
        put(img, x, 46, (90, 86, 84))
    for y in range(24, 44):
        put(img, 160, y, (120, 82, 50))
    for x in range(154, 167):
        for y in range(20, 25):
            put(img, x, y, (120, 118, 116) if y > 20 else (160, 158, 154))
    for a in np.linspace(0.2, math.pi - 0.2, 20):
        put(img, 140 + math.cos(a) * 6, 34 - math.sin(a) * 7, (150, 146, 140))
    # podlaha
    for y in range(96, h):
        for x in range(w):
            c = np.array([70, 58, 50]) * (0.8 + 0.2 * ((x // 12 + y // 6) % 2))
            if y == 96:
                c = np.array([40, 32, 28])
            img[y, x, :3] = c
    return img


import forge_icons  # noqa: E402  (ikony 20 × 20 ve stylu předloh)
ICONS = forge_icons.ICONS


def save(img, name):
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, name))


if __name__ == "__main__":
    save(furnace(), "forge_furnace.png")
    save(fire_frames(), "forge_fire.png")
    save(background(), "forge_bg.png")
    save(forge_icons.atlas(), "forge_icons.png")
    print("ikony:", ", ".join(f"{i}={n}" for i, (n, _) in enumerate(ICONS)))

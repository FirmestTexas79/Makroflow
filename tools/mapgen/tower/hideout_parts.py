"""
Díly pro patro −1 Věže Řádu Váhy – Hunfreyho skrýš (technické podlaží mezi vestibulem a Hlubinou).
Holý beton, potrubí, vlhkost. Jediné místo ve Věži, kde někdo opravdu žil: rozházené lehátko (na rozdíl od
nepoužitého v Archivu), svíčky, mapa kořenů na zdi s rudými nitěmi, čárky za probdělé noci, zapečetěný poklop.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "ashplain")); sys.path.insert(0, HERE)
from pix import Spr, c  # noqa: E402
from figures import sphere, cylinder, selout, _pick, BRASS, STEEL, RED  # noqa: E402

CONC = [c(0x1A1C1E), c(0x26292C), c(0x32363A), c(0x40454A), c(0x50565C), c(0x646B72)]
DAMP = [c(0x14181A), c(0x1C2226)]
PIPE = [c(0x1E1A16), c(0x3A2E24), c(0x5A4632), c(0x7E6444), c(0xA4865C)]   # zrezivělá měď/litina
CANVAS = [c(0x2E2A1E), c(0x4A4430), c(0x6A6246), c(0x8C8460), c(0xB0A880)]
BLANKET = [c(0x2A1414), c(0x481E1C), c(0x6A2C26), c(0x8C4234), c(0xA85A48)]
PAPER = [c(0x8A7E62), c(0xB4A882), c(0xD8CCA4), c(0xF0E6C4)]
WAX = [c(0x8A7A5A), c(0xC8B88C), c(0xEEE2BC)]
WOOD = [c(0x24160C), c(0x3A2414), c(0x54361E), c(0x6E4A2A), c(0x8A6038)]
SEAL = [c(0x1A1612), c(0x3A3026), c(0x5E4C34), c(0x8C7044), c(0xC8A050), c(0xF0D080)]


def _h(x, y, s=0):
    n = (x * 374761393 + y * 668265263 + s * 982451653) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return n ^ (n >> 16)


def _vn(x, y, s, sc):
    xi, yi = int(x // sc), int(y // sc)
    fx, fy = (x / sc) - xi, (y / sc) - yi
    def v(a, b): return (_h(a, b, s) % 1000) / 1000.0
    u, w = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    return (v(xi, yi) * (1 - u) + v(xi + 1, yi) * u) * (1 - w) + (v(xi, yi + 1) * (1 - u) + v(xi + 1, yi + 1) * u) * w


def concrete_floor(img, x0, y0, x1, y1):
    """Betonové desky 24 × 20 s dilatačními spárami, skvrny vlhkosti, vlasové praskliny, louže u stěn."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            n = _vn(x, y, 3, 9) * 0.6 + _vn(x, y, 5, 3) * 0.4
            k = 2 + (1 if n > 0.6 else 0) - (1 if n < 0.32 else 0)
            col = CONC[k].copy()
            if _vn(x, y, 7, 14) > 0.72:                                     # vlhké mapy
                col = DAMP[1].copy() if n > 0.5 else DAMP[0].copy()
            if (x - x0) % 24 == 0 or (y - y0) % 20 == 0:                     # spáry
                col = CONC[0].copy()
            elif (x - x0) % 24 == 1 or (y - y0) % 20 == 1:
                col = CONC[4].copy()
            if _h(x, y, 9) % 97 == 0:
                col = CONC[5].copy()
            img[y, x] = col
    rng = np.random.default_rng(5)
    for _ in range(9):                                                       # vlasové praskliny
        x, y = rng.integers(x0 + 4, x1 - 4), rng.integers(y0 + 4, y1 - 4)
        a = rng.uniform(0, math.tau)
        for i in range(int(rng.integers(8, 22))):
            a += rng.uniform(-0.5, 0.5)
            x += math.cos(a); y += math.sin(a) * 0.7
            if x0 <= x < x1 and y0 <= y < y1:
                img[int(y), int(x)] = CONC[0]


def puddle(img, cx, cy, rx, ry, seed=0):
    """Louže kondenzátu: tmavá voda s odleskem žárovky a mokrým lemem."""
    for y in range(int(cy - ry) - 2, int(cy + ry) + 3):
        for x in range(int(cx - rx) - 2, int(cx + rx) + 3):
            d = math.hypot((x + .5 - cx) / rx, (y + .5 - cy) / ry) + (_vn(x, y, seed, 3) - 0.5) * 0.4
            if d < 1:
                img[y, x] = c(0x0C1012) if d > 0.8 else c(0x101820)
                if d < 0.5 and (x + y + seed) % 9 == 0:
                    img[y, x] = c(0x3A4A58)
            elif d < 1.25:
                img[y, x, :3] = (img[y, x, :3] * 0.7).astype(np.uint8)
    img[int(cy - ry * 0.3), int(cx - rx * 0.3)] = c(0xB0C0D0)


def service_wall(W, H):
    """Zadní stěna technického podlaží: litý beton s otisky bednění, nahoře svazek trubek, dole soklový pruh,
    stékající rez a mokré mapy."""
    s = Spr(W, H)
    for y in range(H):
        for x in range(W):
            n = _vn(x, y, 11, 6) * 0.5 + _vn(x, y, 13, 2) * 0.5
            k = 2 + (1 if n > 0.62 else 0) - (1 if n < 0.3 else 0)
            col = CONC[k + 1].copy()
            if y % 14 == 0:
                col = CONC[2].copy()                                         # otisky bednění
            if x % 30 in (0, 1) and _h(x // 30, y // 7) % 3 == 0:
                col = CONC[1].copy()                                         # šrouby bednění
            if _vn(x, y, 17, 8) > 0.74 and y > 20:
                col[:3] = (col[:3] * 0.75).astype(np.uint8)                  # vlhkost
            s.px(x, y, col)
    for i, y0 in enumerate((4, 9, 13)):                                       # svazek trubek
        r = 2 if i < 2 else 1
        for x in range(W):
            for dy in range(-r, r + 1):
                v = 1 - abs(dy) / (r + 0.5)
                s.px(x, y0 + dy, _pick(PIPE, min(1.0, 0.25 + v * 0.75 - (0.15 if dy > 0 else 0)), x, y0 + dy))
            if x % 26 == 5:
                for dy in range(-r - 1, r + 2):
                    s.px(x, y0 + dy, PIPE[4]); s.px(x + 1, y0 + dy, PIPE[1])
    rng = np.random.default_rng(3)
    for _ in range(14):                                                      # stékající rez z trubek
        x = int(rng.integers(2, W - 2)); L = int(rng.integers(6, 28))
        for y in range(15, 15 + L):
            if 0 <= y < H:
                a = 1 - (y - 15) / L
                base = s.a[y, x, :3].astype(float)
                s.a[y, x, :3] = (base * (1 - 0.45 * a) + np.array([110, 60, 30]) * 0.45 * a).astype(np.uint8)
    for y in range(H - 8, H):                                                # sokl
        for x in range(W):
            s.px(x, y, CONC[2] if y > H - 3 else CONC[3])
    return s


def tally_marks(s, x0, y0, groups, col=None):
    """Čárky křídou po pěti (čtyři svislé a jedna šikmá) – probdělé noci."""
    col = col if col is not None else c(0xD8D4C8)
    x = x0
    for g in range(groups):
        n = 5 if g < groups - 1 else 3
        for i in range(min(n, 4)):
            for y in range(y0, y0 + 6):
                if _h(x + i * 2, y, g) % 7:
                    s.px(x + i * 2, y, col)
        if n == 5:
            for i in range(9):
                s.px(x - 1 + i, y0 + 5 - i * 0.6, col)
        x += 11
        if x > x0 + 44:
            x = x0; y0 += 9


def root_map(w, h):
    """Mapa kořenů na zdi: listy papíru přišpendlené vedle sebe, tužkou kořeny sbíhající se dolů do jednoho
    místa (zakroužkovaného), rudé nitě od připínáčků (stanice 0–7) dolů k němu, papírek „Tady končí mapa.“"""
    s = Spr(w, h)
    rng = np.random.default_rng(12)
    sheets = []
    for row in range(3):
        for col in range(4):
            x = 1 + col * (w - 2) // 4 + int(rng.integers(-1, 2)); y = 1 + row * (h - 2) // 3 + int(rng.integers(-1, 2))
            sw, sh = (w - 2) // 4 + 1, (h - 2) // 3 + 1
            sheets.append((x, y, sw, sh))
            for yy in range(y, min(h, y + sh)):
                for xx in range(x, min(w, x + sw)):
                    k = 2 if (xx + yy) % 11 else 1
                    if xx == x + sw - 1 or yy == y + sh - 1:
                        k = 1
                    s.px(xx, yy, PAPER[k + (1 if _h(xx, yy, 4) % 13 == 0 else 0)])
    tx, ty = w // 2, h - 6                                                   # cíl – dole uprostřed
    for i in range(9):                                                       # kořeny tužkou
        x = 3 + i * (w - 6) / 8; y = 3.0
        for t in range(60):
            k = t / 60
            nx = x + (tx - x) * k * k + math.sin(t * 0.5 + i) * 0.8
            ny = y + (ty - y) * k
            s.px(nx, ny, c(0x4A4238))
            if t % 9 == 4 and i % 2:
                s.px(nx + 1, ny + 1, c(0x6A6050))
    for a in range(0, 360, 12):                                              # zakroužkováno
        s.px(tx + math.cos(math.radians(a)) * 4, ty + math.sin(math.radians(a)) * 3, RED[3])
    for i, px_ in enumerate((5, 14, 24, 33, 42, 52, 61, 70)):                # připínáčky stanic + rudé nitě
        if px_ >= w - 2:
            continue
        py = 2 + (i % 3) * 3
        s.line(px_, py, tx + (i - 3.5) * 0.4, ty - 1, RED[2])
        s.px(px_, py, RED[4]); s.px(px_ + 1, py, RED[3])
    for yy in range(h - 13, h - 9):                                          # papírek z glóbu
        for xx in range(w - 15, w - 3):
            s.px(xx, yy, c(0xF4F0E0) if yy > h - 13 else c(0xC8C0A8))
    for xx in range(w - 14, w - 5, 2):
        s.px(xx, h - 11, c(0x5A5040))
    return selout(s, 0.55, 0.85)


def camp_cot(seed=0):
    """Polní lehátko ve 3/4: dřevěný rám s nohama do X, plachtová lůžková plocha, zmačkaná deka shrnutá
    k nohám, prosezený polštář s důlkem. Tady někdo opravdu ležel – a moc nespal."""
    W, H = 44, 26
    s = Spr(W, H)
    for x in range(2, W - 2):                                                # rám
        s.px(x, 7, WOOD[3]); s.px(x, 22, WOOD[1]); s.px(x, 21, WOOD[2])
    for y in range(7, 23):
        s.px(2, y, WOOD[3]); s.px(W - 3, y, WOOD[1])
    for (xa, xb) in ((4, 12), (W - 13, W - 5)):                              # nohy do X
        s.line(xa, 22, xb, 25, WOOD[1]); s.line(xb, 22, xa, 25, WOOD[0])
    for y in range(8, 21):                                                   # plachta (prověšená)
        for x in range(3, W - 3):
            sag = math.sin((x - 3) / (W - 6) * math.pi) * 1.2
            v = 0.55 + 0.25 * math.cos((y - 8 - sag) / 13 * math.pi) - (0.15 if x > W - 9 else 0)
            s.px(x, y, _pick(CANVAS, max(0.0, min(1.0, v)), x, y))
    sphere(s, 9, 13, 5.5, 4, [c(0x7E8496), c(0xA8AEBC), c(0xCCD0DA), c(0xE8EAF0)], amb=0.3)   # polštář
    for (x, y) in ((9, 13), (10, 13), (9, 14), (8, 13)):                     # důlek po hlavě
        s.px(x, y, c(0x8E94A6))
    blobs_ = [(24, 12, 5), (30, 15, 5.5), (36, 12, 5), (33, 9, 3.5), (27, 17, 4)]   # zmačkaná deka
    for (bx, by, r) in blobs_:
        sphere(s, bx, by, r, r * 0.8, BLANKET, amb=0.22)
    for x in range(18, 41, 3):                                               # lem deky
        s.px(x, 18 + (x % 2), BLANKET[4])
    return selout(s, 0.45, 0.75)


def crate_table(seed=0):
    """Bedna místo stolu: na ní pahýly svíček v louži vosku, plechový hrnek, otevřený sešit, kompas."""
    W, H = 30, 28
    s = Spr(W, H)
    for y in range(4, 12):                                                   # víko shora
        for x in range(1, W - 1):
            s.px(x, y, WOOD[4] if (x // 7) % 2 == 0 else WOOD[3])
            if (x % 7) == 0:
                s.px(x, y, WOOD[1])
    for y in range(12, H):                                                   # čelo
        for x in range(1, W - 1):
            s.px(x, y, WOOD[2] if (y - 12) % 5 else WOOD[1])
        s.px(W - 2, y, WOOD[0]); s.px(W - 3, y, WOOD[1])
    for x in range(1, W - 1):
        s.px(x, 12, WOOD[1])
    for y in range(13, H - 1):
        s.px(4, y, PIPE[2]); s.px(W - 5, y, PIPE[2])                          # kování
    for yy in range(6, 10):                                                  # sešit
        for xx in range(15, 25):
            s.px(xx, yy, PAPER[3] if xx != 20 else PAPER[1])
    for xx in range(16, 20, 2):
        s.px(xx, 7, c(0x5A5040)); s.px(xx + 5, 8, c(0x5A5040))
    sphere(s, 9, 8, 3.2, 1.8, WAX, amb=0.4)                                   # vosková louže
    for (x, hgt) in ((7, 4), (10, 2), (12, 3)):                               # pahýly svíček
        for y in range(8 - hgt, 8):
            s.px(x, y, WAX[2] if x != 12 else WAX[1])
    cylinder(s, 25, 28, 3, 7, STEEL[1:5], amb=0.3)                            # hrnek
    s.px(26, 3, STEEL[0]); s.px(27, 3, STEEL[0])
    sphere(s, 5, 10, 1.6, 1.2, BRASS, amb=0.35)                               # kompas
    s.px(5, 10, RED[3])
    return selout(s, 0.45, 0.75)


def sealed_hatch(r=11):
    """Kulatý ocelový poklop v podlaze se znakem vah, přes něj dvě řetězové pásky a plomba Řádu s voskem.
    Pod ním je cesta dolů – jen výtahem, „subjekt připraven“."""
    W, H = r * 2 + 6, int(r * 1.3) + 8
    s = Spr(W, H)
    cx, cy, ry = W / 2, H / 2, r * 0.62
    for y in range(H):
        for x in range(W):
            d = math.hypot((x + .5 - cx) / r, (y + .5 - cy) / ry)
            if d <= 1:
                ring = d > 0.84
                v = 0.55 + 0.35 * (-(x - cx) / r * 0.6 - (y - cy) / ry * 0.8) * (1 if ring else 0.4)
                s.px(x, y, _pick(STEEL[1:6] if ring else STEEL[0:5], max(0.0, min(1.0, v)), x, y))
                if not ring and int(d * 10) % 3 == 0 and d > 0.4:
                    s.px(x, y, STEEL[1])
    for i in range(10):                                                      # nýty
        a = i / 10 * math.tau
        s.px(cx + math.cos(a) * r * 0.92, cy + math.sin(a) * ry * 0.92, STEEL[5])
    s.line(cx - 6, cy - 1, cx + 6, cy - 1, BRASS[3])                          # znak vah
    s.line(cx, cy - 4, cx, cy + 2, BRASS[3])
    for sx in (-6, 6):
        s.px(cx + sx - 1, cy + 1, BRASS[2]); s.px(cx + sx, cy + 1, BRASS[3]); s.px(cx + sx + 1, cy + 1, BRASS[2])
    for k, (a, b) in enumerate((((2, 3), (W - 3, H - 4)), ((2, H - 4), (W - 3, 3)))):   # řetězy přes kříž
        (x0, y0), (x1, y1) = a, b
        n = int(math.hypot(x1 - x0, y1 - y0))
        for i in range(n):
            t = i / n
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            s.px(x, y, STEEL[4] if i % 3 else STEEL[2])
            if i % 3 == 1:
                s.px(x, y - 1, STEEL[3])
    sphere(s, cx + 1, cy + 3, 2.6, 2, [c(0x4A0A0C), c(0x7A1418), c(0xA8241E), c(0xD04A30)], amb=0.35)   # plomba
    return selout(s, 0.5, 0.8)


def mask_hook():
    """Háček ve zdi s jedinou plynovou maskou – tou, co v Jedové laboratoři chybí („VYDÁNO – SUBJEKT 212“)."""
    s = Spr(12, 18)
    s.px(5, 0, STEEL[3]); s.px(6, 0, STEEL[3]); s.px(6, 1, STEEL[2])
    sphere(s, 6, 8, 4.6, 5.2, [c(0x101410), c(0x1E241E), c(0x2E362C), c(0x46503E), c(0x5E6A52)], amb=0.25)
    for ex in (4, 8):                                                         # skla
        sphere(s, ex, 7, 1.5, 1.5, [c(0x1A2A30), c(0x3A5A64), c(0x8AB4C0)], amb=0.4)
    cylinder(s, 4, 8, 12, 16, [c(0x2A2A24), c(0x4A4A3E), c(0x6A6A58)], amb=0.3)   # filtr
    for x in range(4, 9):
        s.px(x, 13, c(0x2A2A24))
    s.px(5, 17, c(0xC8B060)); s.px(6, 17, c(0xC8B060)); s.px(7, 17, c(0xC8B060))   # štítek 212
    return selout(s, 0.5, 0.8)


def candle_on_floor():
    """Svíčka přilepená voskem přímo na beton, kolem stékající vosk (plamen kreslí InteriorFxView)."""
    s = Spr(6, 9)
    for y in range(2, 8):
        s.px(2, y, WAX[2]); s.px(3, y, WAX[1])
    s.px(2, 1, c(0x2A2420))
    for (x, y) in ((1, 8), (2, 8), (3, 8), (4, 8), (0, 8), (4, 7), (1, 7)):
        s.px(x, y, WAX[1] if x in (0, 4) else WAX[2])
    return selout(s, 0.55, 0.85)


def bucket():
    """Plechový kbelík pod kapající trubkou, voda s kruhem odlesku."""
    s = Spr(12, 13)
    cylinder(s, 1, 10, 3, 12, STEEL[1:5], amb=0.28)
    s.ellipse(5.5, 3, 5, 1.8, STEEL[4])
    s.ellipse(5.5, 3.2, 4, 1.2, c(0x1A2A34))
    s.px(4, 3, c(0x8AA8B8))
    return selout(s, 0.5, 0.8)


def bowl():
    """Plechová miska se lžící – zbytek jídla (Hunfrey jedl, i když nespal)."""
    s = Spr(9, 5)
    s.ellipse(4, 2, 4, 2, STEEL[3]); s.ellipse(4, 1.8, 3, 1.1, c(0x6A5A40))
    s.line(6, 0, 8, 2, STEEL[4])
    return selout(s, 0.5, 0.8)

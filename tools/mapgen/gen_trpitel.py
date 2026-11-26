"""
Použití:  python tools/mapgen/gen_trpitel.py [--frames N]
Výstup:   app/src/main/res/drawable-nodpi/trpitel_sheet.png (pás FRAMES snímků vedle sebe, 1 px = 1 px mapy),
          tools/mapgen/trpitel_preview.png

TRPITEL (Kazimír) – bůh ve Stromu, NPC na mapě Kmene (gen_trunk.py, RootTrunk.kt). Ne plochá kresba, ale 3D postava
(SDF: svaly jako elipsoidy a kužely s plynulým sjednocením, raymarching, stíny, okluze) natočená do 3/4: vylézá z rány
v kmeni, předkloněný k hráči, boky a nohy ještě v kmeni pod pěnou. Tělo kulturisty barvy popela, popraskaná kůže
(praskliny jsou 3D Voronoi v prostoru těla, drží na kůži), ve prasklinách doutná studené světlo, které sílí s nádechem.
Hladká hlava bez tváře, šest očí. Šest paží: levá horní se drápe za okraj rány (vytahuje se ven), pravá horní je pořád
v ocelové objímce Řádu v kůře; levá prostřední se natahuje k hráči otevřenou dlaní, pravá prostřední se opírá o kůru;
dolní pár svírá ratiště kopí, které mu prochází hrudí a přibíjí ho ke kmeni. Ruce řeší IK (rameno–loket–dlaň).
Světlo: zespodu teplé sodíkové světlomety (vrhají jeho stín nahoru na kmen), shora studená záře rány, obrys azurový.
Snímky = jeden dech: hrudník se zvedne, předkloní se, natažená ruka se přiblíží, dolní ruce zatáhnou za kopí.
"""
import math, os, sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
W0, H0 = 264, 290                                                         # rámec, ve kterém je postava modelovaná
K = 1.75                                                                  # zvětšení do mapy Kmene (432 × 960)
W, H = 432, 507                                                           # výstupní sprite (px mapy)
OFFX, OFFY = (W - W0 * K) / 2, 0.0
FRAMES = 12
BLINK = {7: 0.45, 8: 0.0, 9: 0.5}                                          # otevření očí ve snímcích mrknutí (jinak 1)
FRAME_MS = [280] * FRAMES
for _f in BLINK:
    FRAME_MS[_f] = 110                                                    # mrknutí je rychlé – kratší snímky
# kde sprite leží v mapě Kmene (levý horní roh, art px) – musí sedět s RootTrunk.kt a gen_trunk.py
MAP_X0, MAP_Y0 = 0, 16
CX = 132


def nrm(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


# ── SDF primitiva (p: N×3) ──────────────────────────────────────────────────
def sd_ell(p, c, r, R=None):
    q = p - c
    if R is not None:
        q = q @ R
    k0 = np.linalg.norm(q / r, axis=1)
    k1 = np.linalg.norm(q / (r * r), axis=1)
    return k0 * (k0 - 1.0) / np.maximum(k1, 1e-6)


def sd_cone(p, a, b, ra, rb):
    pa = p - a; ba = b - a
    h = np.clip((pa @ ba) / (ba @ ba), 0, 1)
    return np.linalg.norm(pa - np.outer(h, ba), axis=1) - (ra + (rb - ra) * h)


def ssub(d, hole, k):
    """Plynulé odečtení (vyhloubení) díry hole z tvaru d."""
    h = np.clip(0.5 - 0.5 * (d + hole) / k, 0, 1)
    return d * (1 - h) + (-hole) * h + k * h * (1 - h)


def smin(a, b, k):
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0, 1)
    return b * (1 - h) + a * h - k * h * (1 - h)


def ik(s, h, l1, l2, pole):
    """Dvoukloubová IK: rameno s, dlaň h, délky, pomocný bod pole (kam má trčet loket) → loket, skutečná dlaň."""
    d = h - s
    L = np.linalg.norm(d)
    L = min(L, l1 + l2 - 0.5)
    dn = nrm(d)
    hh = s + dn * L
    a = (l1 * l1 - l2 * l2 + L * L) / (2 * L)
    k = math.sqrt(max(0.0, l1 * l1 - a * a))
    pv = np.asarray(pole, float) - s
    pv = pv - dn * (pv @ dn)
    pv = nrm(pv) if np.linalg.norm(pv) > 1e-6 else np.array([0, 0, 1.0])
    return s + dn * a + pv * k, hh


def seg_frame(a, b):
    """Matice lokálních os pro sval podél úseku a→b: lokální y míří po úseku."""
    f = nrm(b - a)
    s = np.cross(f, np.array([0, 0, 1.0]))
    s = nrm(s) if np.linalg.norm(s) > 1e-4 else np.array([1.0, 0, 0])
    u = np.cross(s, f)
    return np.column_stack([s, f, u])


class Model:
    """Postava jako skupiny primitiv: kůže (plynulé sjednocení), kopí, kov, pěna."""
    def __init__(self):
        self.skin, self.spear, self.metal, self.foam, self.eyes, self.lids = [], [], [], [], [], []

    def sdf(self, p, mats=False):
        def grp(lst, k):
            d = None
            for f in lst:
                v = f(p)
                d = v if d is None else (smin(d, v, k) if k else np.minimum(d, v))
            return d if d is not None else np.full(len(p), 1e9)
        ds = grp(self.skin, 3.2)
        dp = grp(self.spear, 0)
        dm = grp(self.metal, 0)
        df = grp(self.foam, 2.0)
        de = grp(self.eyes, 0)
        dl = grp(self.lids, 0)
        ds = np.minimum(ds, dl)                                           # víčka jsou kůže, ale bez splývání
        d = np.minimum(np.minimum(np.minimum(ds, dp), np.minimum(dm, df)), de)
        if not mats:
            return d
        m = np.zeros(len(p), int)                                         # 0 kůže, 1 kopí, 2 kov, 3 pěna
        m[dp <= d + 1e-4] = 1; m[dm <= d + 1e-4] = 2; m[df <= d + 1e-4] = 3; m[ds <= d + 1e-4] = 0; m[de <= d + 1e-4] = 4
        return d, m


def build(fi):
    t = fi / FRAMES
    b = (1 - math.cos(2 * math.pi * t)) / 2                               # 0 výdech … 1 nádech
    M = Model()
    S = 1.9
    lean = math.radians(24 + 4 * b)
    yaw = math.radians(50)
    R = rot_x(-lean) @ rot_y(yaw)                                         # tělo: předklon k hráči, natočené do 3/4
    hl = R @ np.array([0, -83.0, 5.0])
    P0 = np.array([130.0 - S * hl[0], 64.0 - S * hl[1], -18.0])           # pánev v kmeni; hlava nahoře v ráně

    def W_(v):
        return P0 + S * (R @ np.asarray(v, float))

    def ell(c, r, Rl=None, k=None):
        cw, rr, RR = W_(c), np.asarray(r, float) * S, R if Rl is None else R @ Rl
        M.skin.append(lambda p, cw=cw, rr=rr, RR=RR: sd_ell(p, cw, rr, RR))

    def cone(a, b_, ra, rb, lst=None, world=False):
        A = np.asarray(a, float) if world else W_(a)
        B = np.asarray(b_, float) if world else W_(b_)
        (M.skin if lst is None else lst).append(lambda p, A=A, B=B: sd_cone(p, A, B, ra, rb))

    chest = 1 + 0.05 * b
    # ── trup kulturisty (lokální souřadnice: x vpravo, y dolů, z dopředu; pánev = 0) ──
    ell((0, -4, 0), (17, 11, 12))                                         # pánev
    ell((0, -24, 2), (15, 16, 11))                                        # břicho
    ell((0, -47, 0), (22 * chest, 17 * chest, 14 * chest))               # hrudní koš
    for sx in (-1, 1):
        ell((sx * 10.5, -51, 8 * chest), (13, 8, 5.2), rot_y(sx * 0.25))  # prsní svaly (ploché desky)
        ell((sx * 20, -40, -3), (8.5, 15, 9))                             # široké zádové svaly
        ell((sx * 9, -66, -4), (13, 7, 8))                                # trapézy
        ell((sx * 25, -59, -1), (8.4, 11, 8.4), rot_x(0.3))               # deltové svaly
        ell((sx * 13.5, -17, 5), (5.5, 11, 6))                            # šikmé svaly
        for k, yy in enumerate((-35, -26, -17)):
            ell((sx * 5, yy, 10.5), (5, 4.4, 3.2))                        # břišní svaly
    cone((0, -62, -1), (0, -74, 3), 9.5, 8.0)                             # krk (silný)
    head_c = (0, -83, 5)
    Rh = rot_y(math.radians(-22 + 4 * math.sin(2 * math.pi * t))) @ rot_x(math.radians(14))   # hlava se otáčí k hráči a zvedá tvář
    HC, HR = W_(head_c), R @ Rh
    EYES = [(sx * 3.0, ey, 5.4) for ey in (-2.4, 1.0, 4.4) for sx in (-1, 1)]

    def head_sdf(p, HC=HC, HR=HR):
        """Vymodelovaná hlava bez tváře: lebka, užší dolní obličej, lícní kosti, čelisti, těžký nadočnicový val,
        hřbet mezi sloupci očí, propadlé spánky a šest hlubokých očnic."""
        q = ((p - HC) @ HR) / S
        d = sd_ell(q, np.array([0, -2.5, -1.0]), np.array([8.6, 9.4, 9.2]))              # lebka
        d = smin(d, sd_ell(q, np.array([0, 3.6, 1.6]), np.array([6.6, 7.4, 7.6])), 2.0)   # dolní obličej
        for sx in (-1, 1):
            d = smin(d, sd_ell(q, np.array([sx * 4.9, 3.2, 5.0]), np.array([2.8, 2.0, 2.6])), 1.2)   # lícní kosti
            d = smin(d, sd_ell(q, np.array([sx * 5.2, 6.4, 0.5]), np.array([3.0, 3.6, 4.0])), 1.5)   # úhel čelisti
            d = ssub(d, sd_ell(q, np.array([sx * 8.2, -2.0, 3.0]), np.array([2.2, 3.4, 3.0])), 1.2)  # propadlé spánky
        for sx in (-1, 1):
            d = smin(d, sd_ell(q, np.array([sx * 3.1, -4.6, 7.0]), np.array([3.4, 1.5, 2.0])), 1.8)   # oblouky nadočnicového valu
        d = smin(d, sd_ell(q, np.array([0, 1.2, 8.0]), np.array([1.1, 5.6, 1.6])), 0.8)    # hřbet mezi sloupci očí
        for (ex, ey, ez) in EYES:
            d = ssub(d, sd_ell(q, np.array([ex, ey, 9.2]), np.array([2.25, 1.7, 4.8])), 0.45)   # hluboká očnice
        return d * S
    M.skin.append(head_sdf)
    op = BLINK.get(fi, 1.0)                                               # mrknutí: víčka sjedou shora přes oči
    for k, e_ in enumerate(EYES):                                         # oční koule hluboko v očnicích (svítí)
        if op > 0.05:
            M.eyes.append(lambda p, c=HC + HR @ (np.array(e_) * S), rr=np.array([1.05, 1.05 * max(op, 0.2), 1.05]) * S:
                          sd_ell(p, c, rr, HR))
        if op < 1.0:
            lc = HC + HR @ (np.array([e_[0], e_[1] - 1.7 * op, 6.6]) * S)
            M.lids.append(lambda p, c=lc, rr=np.array([2.2, 1.75 * (1 - op) + 0.05, 1.1]) * S: sd_ell(p, c, rr, HR))
    # ── kopí: drží ho v levých rukou vpředu, venku od těla; hrot se železnou čepelí míří nahoru ──
    H1 = np.array([64.0, 120.0, 70.0])                                    # horní úchop (levá prostřední)
    H2 = np.array([76.0, 200.0, 60.0])                                    # dolní úchop (levá dolní)
    sd = nrm(H1 - H2)                                                     # po ratišti od paty k hrotu
    butt = H2 - sd * 50
    tip = H1 + sd * 66
    M.spear.append(lambda p, A=butt, B=tip - sd * 10: sd_cone(p, A, B, 3.8, 3.1))
    M.metal.append(lambda p, c=butt: sd_cone(p, c - sd * 1, c + sd * 7, 4.8, 4.4))   # železná pata
    M.metal.append(lambda p, c=tip - sd * 10: sd_cone(p, c - sd * 2, c + sd * 6, 4.6, 3.6))   # objímka čepele
    Fb = seg_frame(tip - sd * 4, tip + sd * 26)
    M.metal.append(lambda p, c=tip + sd * 12, Fb=Fb: sd_ell(p, c, np.array([6.8, 18.0, 2.2]), Fb))   # listová čepel
    spear_in, clamp = butt, butt
    # ── paže (IK) ──
    def arm(sh_local, hand, pole, l1=40, l2=37, r=(10.0, 8.0, 7.8, 5.6)):
        s = W_(sh_local)
        e, h = ik(s, np.asarray(hand, float), l1, l2, pole)
        mid = s + (e - s) * 0.5
        cone(s, e, r[0], r[1], world=True)
        Fu = seg_frame(s, e); lu = np.linalg.norm(e - s)
        M.skin.append(lambda p, c=mid + Fu[:, 2] * 1.5, rr=np.array([r[0] * 1.05, lu * 0.36, r[0] * 1.2]), Fu=Fu: sd_ell(p, c, rr, Fu))   # biceps
        cone(e, h, r[2], r[3], world=True)
        Ff = seg_frame(e, h); lf = np.linalg.norm(h - e)
        fm = e + (h - e) * 0.3
        M.skin.append(lambda p, c=fm, rr=np.array([r[2] * 1.15, lf * 0.3, r[2] * 1.05]), Ff=Ff: sd_ell(p, c, rr, Ff))   # svaly předloktí
        return e, h

    def open_hand(h, e, spread, curl=0.0):
        f = nrm(h - e)
        side = nrm(np.cross(f, np.array([0, 0, 1.0])) + 1e-6)
        up = nrm(np.cross(side, f))
        palm = h + f * 5
        M.skin.append(lambda p, c=palm: sd_ell(p, c, np.array([7.0, 7.0, 6.0])))
        for k, a in enumerate((-1.5, -0.5, 0.5, 1.5)):
            base = palm + f * 5 + side * a * 3.2
            dirf = nrm(f + side * a * spread - up * curl)
            ln = 12 - abs(a) * 2
            mid = base + dirf * ln * 0.55
            tip = mid + nrm(dirf - up * curl * 1.5) * ln * 0.5
            cone(base, mid, 2.4, 2.1, world=True); cone(mid, tip, 2.1, 1.6, world=True)
        th = palm - side * 6 + f * 1
        cone(th, th - side * 5 + f * 6 - up * curl * 2, 2.6, 2.0, world=True)

    def fist(h, e):
        f = nrm(h - e)
        M.skin.append(lambda p, c=h + f * 4: sd_ell(p, c, np.array([7.6, 7.2, 7.2])))

    # levá horní: drápe se za okraj rány a vytahuje se ven
    e, h = arm((-27, -60, -2), (80 + b * 1.5, 30 - b * 2, 4), (50, 80, 50))
    open_hand(h, e, 0.25, curl=0.9)
    # pravá horní: pořád v objímce Řádu v kůře
    e, h = arm((27, -60, -2), (208, 50, -2), (200, 100, 30))
    open_hand(h, e, 0.18, curl=0.6)
    f = nrm(h - e)
    M.metal.append(lambda p, A=h - f * 7, B=h - f * 1: sd_cone(p, A, B, 6.0, 6.0))
    wr_r = h
    # levá prostřední a levá dolní svírají ratiště vpředu, zápěstí vytočená ven; s dechem kopí lehce zvedne
    lift = np.array([0, -2.0 * b, 0])
    for (sh, g, pole, l1, l2, r) in (((-23, -46, -4), H1, (10, 150, 50), 44, 42, (10.0, 8.0, 7.8, 5.6)),
                                     ((-19, -30, 3), H2, (30, 240, 40), 34, 32, (8.6, 7.0, 6.8, 5.0))):
        e, h = arm(sh, g + lift - nrm(g - W_(sh)) * 5, pole, l1=l1, l2=l2, r=r)
        M.skin.append(lambda p, c=g + lift: sd_ell(p, c, np.array([7.4, 7.0, 7.4])))   # pěst kolem ratiště
    # pravá prostřední se opírá dlaní o kůru, pravá dolní se zapírá o pěnu
    e, h = arm((23, -46, -4), (214, 150 + b * 2, 2), (240, 110, 40), l1=44, l2=42)
    open_hand(h, e, 0.4, curl=0.3)
    e, h = arm((19, -30, 3), (172, 206 - b * 2, 0), (200, 240, 40), l1=36, l2=34, r=(8.6, 7.0, 6.8, 5.0))
    open_hand(h, e, 0.5, curl=0.5)
    # pravé koleno se zapírá o okraj rány – leze ven
    hip = W_((10, 2, 2))
    knee = np.array([hip[0] + 34, hip[1] - 6 - b * 2, 30.0])
    foot = np.array([knee[0] + 10, knee[1] + 34, 2.0])
    cone(hip, knee, 13, 9.5, world=True)
    M.skin.append(lambda p, c=hip + (knee - hip) * 0.45: sd_ell(p, c, np.array([12.5, 12.5, 12.5])))
    cone(knee, foot, 9, 6.5, world=True)
    # pěna kolem místa, kde tělo vystupuje z kmene
    rf = np.random.default_rng(31)
    for _ in range(110):
        c = np.array([P0[0] + rf.uniform(-46, 50), P0[1] + rf.uniform(-30, 26), rf.uniform(-2, 8)])
        r = rf.uniform(3.5, 8)
        M.foam.append(lambda p, c=c, rr=np.array([r, r * 0.9, r * 0.55]): sd_ell(p, c, rr))
    info = dict(R=R, P0=P0, S=S, Rh=Rh, head_c=W_(head_c), eyes=[HC + HR @ (np.array(e_) * S) for e_ in EYES], b=b, spear_in=spear_in, spear_dir=sd, clamp=clamp, wrist_r=wr_r,
                W_=W_, head_R=R @ Rh)
    return M, info


# ── render ──────────────────────────────────────────────────────────────────
KEY = nrm((-0.45, -0.6, 0.66))                                            # studená záře shora zleva (hlavní)
FILL = nrm((0.25, 0.85, 0.45))                                            # sodík zespodu
WARM = np.array((200, 214, 214.0)) / 255
COOL = np.array((255, 170, 100.0)) / 255
CYAN = np.array((120, 240, 228.0))
ALB = {4: np.array((0, 0, 0.0)), 0: np.array((108, 100, 98.0)), 1: np.array((96, 72, 50.0)), 2: np.array((130, 136, 144.0)), 3: np.array((118, 110, 82.0))}
rs = np.random.default_rng(6)
SEEDS = rs.uniform((-60, -140, -40), (60, 20, 40), (3200, 3))             # Voronoi prasklin v prostoru těla
SEEDS_W = rs.uniform((0, 0, -20), (W0, H0, 130), (9000, 3))                  # … a pro paže ve světě
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0


def cracks(pts, seeds):
    out = np.zeros(len(pts)); idx = np.zeros(len(pts), int)
    for i in range(0, len(pts), 2000):
        d = np.linalg.norm(pts[i:i + 2000, None, :] - seeds[None, :, :], axis=2)
        part = np.partition(d, 1, axis=1)
        out[i:i + 2000] = part[:, 1] - part[:, 0]
        idx[i:i + 2000] = np.argmin(d, axis=1)
    return out, idx


def render(fi):
    M, info = build(fi)
    ys, xs = np.mgrid[0:H, 0:W]
    cam = np.array([CX, 150.0, 460.0])                                    # perspektiva: blízké (ruka k hráči) je větší
    tgt = np.column_stack([(xs.ravel() + 0.5 - OFFX) / K, (ys.ravel() + 0.5 - OFFY) / K, np.zeros(W * H)])   # pixel → rámec modelu
    rdv = tgt - cam; rdv /= np.linalg.norm(rdv, axis=1)[:, None]
    ro = np.tile(cam, (W * H, 1))
    tt = np.full(W * H, 90.0); hit = np.zeros(W * H, bool); alive = np.ones(W * H, bool)
    for _ in range(140):
        idx = np.nonzero(alive)[0]
        if len(idx) == 0:
            break
        p = ro[idx] + rdv[idx] * tt[idx, None]
        d = M.sdf(p)
        h_ = d < 0.25
        hit[idx[h_]] = True; alive[idx[h_]] = False
        tt[idx] += np.maximum(d * 0.85, 0.2)
        gone = p[:, 2] - d * 0 < -3                                # za rovinou kůry = uvnitř kmene
        alive[idx[gone]] = False
    hi = np.nonzero(hit)[0]
    P = ro[hi] + rdv[hi] * tt[hi, None]
    _, mat = M.sdf(P, mats=True)
    e = 0.5
    k1, k2, k3, k4 = np.array([1, -1, -1.]), np.array([-1, -1, 1.]), np.array([-1, 1, -1.]), np.array([1, 1, 1.])
    n = (k1 * M.sdf(P + k1 * e)[:, None] + k2 * M.sdf(P + k2 * e)[:, None] + k3 * M.sdf(P + k3 * e)[:, None] + k4 * M.sdf(P + k4 * e)[:, None])
    n /= np.linalg.norm(n, axis=1)[:, None]
    ao = np.ones(len(P)); w = 1.0
    for i in range(1, 7):
        hh = 1.2 * i
        ao -= np.maximum(0, hh - M.sdf(P + n * hh)) / hh * 0.2 * w; w *= 0.8
    ao = np.clip(ao, 0.15, 1)
    sh = np.ones(len(P)); ts = np.full(len(P), 1.5)
    for _ in range(40):                                                   # měkký stín od sodíku
        q = P + n * 0.6 + np.outer(ts, KEY)
        d = M.sdf(q)
        sh = np.minimum(sh, np.clip(6 * d / ts, 0, 1))
        ts += np.clip(d, 0.6, 6)
    dk = np.clip(n @ KEY, 0, 1); df = np.clip(n @ FILL, 0, 1)
    fres = (1 - np.clip(n[:, 2], 0, 1)) ** 2.6
    alb = np.array([ALB[m] for m in mat])
    b = info["b"]
    light = 0.14 + WARM * (dk * sh)[:, None] * 0.95 + COOL * df[:, None] * 0.38
    col = alb * light * ao[:, None] + CYAN * (fres * 0.5 * ao ** 3 * (mat != 3))[:, None]
    # praskliny: v těle drží na kůži (lokální souřadnice trupu), na pažích ve světě
    R, P0, S = info["R"], info["P0"], info["S"]
    loc = ((P - P0) @ R) / S
    on_torso = (loc[:, 1] > -100) & (loc[:, 1] < 4) & (np.abs(loc[:, 0]) < 30)
    cw, cid = cracks(np.where(on_torso[:, None], loc * 1.0, P / S), SEEDS)
    cw2, cid2 = cracks(P, SEEDS_W)
    crack_w = np.where(on_torso, cw * S, cw2)
    cell = np.where(on_torso, cid, cid2 + 5000)
    skin = mat == 0
    head = np.linalg.norm((P - info["head_c"]) / np.array([10.5, 13, 11]) / S, axis=1) < 1.1
    isc = skin & ~head & (crack_w < 0.6) & ((cell * 2654435761 % 100) < 75)
    glow = isc & ((cell * 40503 % 100) < 22)
    col[isc] = col[isc] * 0.28 + np.array((26, 10, 12.0)) * 0.7
    col[glow] += CYAN * (0.22 + 0.4 * b)
    # oči: svítí zevnitř očnic – jádro, záře po stěnách jamky; okolí očnic v tvrdé tmě (okluze + stín valu)
    pulse = 0.75 + 0.25 * b
    for k, E in enumerate(info["eyes"]):
        op = BLINK.get(fi, 1.0)
        dd = np.linalg.norm(P - E, axis=1) / S
        if op < 0.3:
            continue
        sock = (dd < 3.6) & (mat != 4)
        col[sock] *= np.clip((dd[sock] - 0.6) / 3.0, 0.03, 1)[:, None] ** 2.2   # tvrdá tma kolem očí
        g = np.exp(-((dd - 1.0) / 0.7) ** 2) * 0.9 * pulse * op * (mat != 4)   # záře na stěnách očnice
        col += CYAN[None, :] * g[:, None]
    ey_ = mat == 4                                                        # oči: jádro bílé, okraj azurový
    facing = np.clip(-(n[ey_] @ np.array([0, 0, -1.0])), 0, 1)
    col[ey_] = CYAN * (1.0 + 0.3 * pulse) * (0.7 + 0.3 * facing[:, None]) + (facing[:, None] > 0.75) * np.array((150, 60, 70.0))
    col[head] *= np.clip(ao[head] * 1.25, 0.08, 1)[:, None] ** 1.6      # hloubky hlavy tmavší než trup
    # kopí: okovy a omotávky podle vzdálenosti po ratišti, objímka kovová
    along = (P - info["spear_in"]) @ info["spear_dir"]
    sp = mat == 1
    band = sp & ((along.astype(int) % 15) < 2)
    rope = sp & ((along.astype(int) % 7) == 4)
    col[band] = np.array((120, 124, 130.0)) * light[band] * 1.2
    col[rope] = np.array((150, 128, 88.0)) * light[rope]
    # pěna: hrbolatá
    fm = mat == 3
    img = np.zeros((H * W, 3)); img[hi] = col
    eyem = np.zeros(H * W, bool); eyem[hi[mat == 4]] = True; eyem = eyem.reshape(H, W)
    depth = np.full(H * W, -1e9); depth[hi] = P[:, 2]
    img = img.reshape(H, W, 3); depth = depth.reshape(H, W); a = hit.reshape(H, W)
    # posterizace s ditheringem (pixel art), obrysy na skocích hloubky a kolem siluety
    q = 14.0
    th = BAYER[np.arange(H)[:, None] % 4, np.arange(W)[None, :] % 4][:, :, None]
    img = np.floor(img / q + th) * q
    out = np.zeros((H, W, 4), np.uint8)
    # stín postavy na kůře a pěně za ní (světlo shora zleva) – průhledná tma, mapa pod ní zůstane vidět
    miss = np.nonzero(~hit)[0]
    Q = np.column_stack([tgt[miss, 0], tgt[miss, 1], np.full(len(miss), 0.5)])
    shq = np.ones(len(miss)); ts = np.full(len(miss), 1.0)
    for _ in range(36):
        d = M.sdf(Q + np.outer(ts, KEY))
        shq = np.minimum(shq, np.clip(4 * d / ts, 0, 1))
        ts += np.clip(d, 1.0, 10)
    sa = np.zeros(H * W); sa[miss] = (1 - shq) * 0.62
    sa = sa.reshape(H, W)
    sm = (sa > 0.04) & ~a
    out[sm, :3] = (6, 6, 10)
    out[sm, 3] = np.clip(np.floor(sa[sm] * 4 + BAYER[np.nonzero(sm)[0] % 4, np.nonzero(sm)[1] % 4]) / 4 * 255, 0, 200).astype(np.uint8)
    out[a, :3] = np.clip(img[a], 0, 255); out[a, 3] = 255
    for y in range(1, H - 1):
        for x in range(1, W - 1):
            if a[y, x]:
                for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    if a[y + dy, x + dx] and depth[y + dy, x + dx] - depth[y, x] > 7:
                        out[y, x, :3] = (out[y, x, :3] * 0.35).astype(np.uint8)
                        break
    ey, ex = np.nonzero(eyem)                                             # záře očí přesahuje přes okraj očnic
    for y0, x0 in zip(ey, ex):
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                y1, x1 = y0 + dy, x0 + dx
                if 0 <= y1 < H and 0 <= x1 < W and a[y1, x1] and not eyem[y1, x1]:
                    k = 0.22 / (1 + dx * dx + dy * dy)
                    out[y1, x1, :3] = np.clip(out[y1, x1, :3] + CYAN * k, 0, 255).astype(np.uint8)
    edge = np.zeros_like(a)
    edge[1:, :] |= a[:-1, :]; edge[:-1, :] |= a[1:, :]; edge[:, 1:] |= a[:, :-1]; edge[:, :-1] |= a[:, 1:]
    edge &= ~a
    out[edge] = (12, 9, 11, 255)
    info["alpha"] = a
    return out, info


def chain_points(info):
    """Body pro řetězy v mapě (sprite px): objímka na ratišti a objímka pravého horního zápěstí."""
    return dict(clamp=info["clamp"][:2], wrist=info["wrist_r"][:2], spear_in=info["spear_in"][:2])


def proj(wp):
    """Bod ze světa do px spritu (stejná perspektiva jako render)."""
    k = 460.0 / (460.0 - wp[2])
    return (OFFX + K * (CX + (wp[0] - CX) * k), OFFY + K * (150 + (wp[1] - 150) * k))


_I0 = build(0)[1]
SPEAR_IN = proj(_I0["spear_in"])
CLAMP = proj(_I0["clamp"])
WRIST_R = proj(_I0["wrist_r"])

def _frame(i):
    return render(i)[0]


if __name__ == "__main__":
    n = FRAMES
    if "--frames" in sys.argv:
        n = int(sys.argv[sys.argv.index("--frames") + 1])
    from multiprocessing import Pool
    with Pool(min(n, os.cpu_count() or 2)) as pool:
        frames = pool.map(_frame, range(n))
    if n == FRAMES:
        Image.fromarray(np.concatenate(frames, axis=1), "RGBA").save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "trpitel_sheet.png"))
    prev = Image.new("RGBA", (W * 3, H * 3), (20, 22, 26, 255))
    prev.alpha_composite(Image.fromarray(frames[0], "RGBA").resize((W * 3, H * 3), Image.NEAREST))
    prev.save(os.path.join(HERE, "trpitel_preview.png"))
    print("trpitel", n, "snímků", W, "x", H, "clamp", tuple(round(v) for v in CLAMP), "wrist", tuple(round(v) for v in WRIST_R))

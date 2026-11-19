"""
Díly vestibulu Věže Řádu Váhy (Akt II, docs/adr/0103): studený mramor, tmavá modř Kustodiátu a mosaz.
Všechno je úřad – měří, eviduje, čeká. Nic tu není ošklivé, jen až příliš uklizené.

  * podlaha z bílých a šedých mramorových desek s žilkováním a modrými kosočtverci v rozích,
  * modrý koberec s mosazným lemem a vzorem vah, mozaika vah v podlaze,
  * zadní stěna: římsa se zubořezem, kvádry, kanelované pilastry, vysoká okna s popelem venku,
    portréty „kalibrovaných“ s prázdnou tváří, mosazné potrubí potrubní pošty,
  * výtah s mosaznou mřížovou klecí a nad ním ukazatel pater 0–7 (pod nulou dvě zaslepené pozice),
  * pult archiváře Septa (psací stroj, razítko, spisy, zvonek, bankéřská lampa, jmenovka, potrubní pošta) a Sept,
  * čekárna: lavice, automat na pořadová čísla, tabule „SUBJEKT 213“ s hodinami (stojí na 6:12),
  * kartotéky, sloupky s modrým provazem (fronta), uschlé rostliny v kamenných květináčích.
Vše po prvcích s vlastním obrysem a stínem (světlo shora zleva).
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "ashplain")); sys.path.insert(0, os.path.join(HERE, "..", "orchard"))
from pix import Spr, c, outline  # noqa: E402
from pixart import K, box_outline  # noqa: E402

MARB = [c(0x4E5464), c(0x7A8292), c(0xA6ADBB), c(0xCCD1DB), c(0xE6E9EF), c(0xF7F8FA)]
NAVY = [c(0x0E1424), c(0x1A2440), c(0x2A3A62), c(0x40568A), c(0x6A82B4)]
BRASS = [c(0x5A3E12), c(0x8A6424), c(0xBC9238), c(0xE0BC5C), c(0xFFE9A0)]
IRON = [c(0x1C1E24), c(0x2E323C), c(0x464C58), c(0x6A7280), c(0x9AA2B0)]
WALN = [c(0x24140C), c(0x3C2416), c(0x5A3822), c(0x7A5034), c(0x9C6C48)]
PAPER = [c(0x9E9682), c(0xC8C0A8), c(0xE8E2CE), c(0xFAF6EA)]
ASH = [c(0x6E6A66), c(0x8E8A84), c(0xB2ADA6), c(0xD4D0C8)]
INK = c(0x1A1A22)
RED = [c(0x5A1414), c(0x8E2222), c(0xC03A30)]
GREEN = [c(0x123A22), c(0x1E5A36), c(0x3A8A58), c(0x8AD8A0)]
FL = [c(0x6C7282), c(0x9AA0AE), c(0xB6BBC6), c(0xC8CCD5), c(0xDDE0E6)]   # podlaha (tlumený mramor)
SKIN = [c(0x9A8C84), c(0xC4B8AE), c(0xDED4CA)]               # bledá úřednická pleť


# ── podlaha ─────────────────────────────────────────────────────────────────

def floor(img, x0, y0, x1, y1, seed=7):
    """Mramorové desky 16 × 16 ve dvou tónech, žilkování, světlá hrana, spára, modrý kosočtverec v křížení spár."""
    r = np.random.default_rng(seed)
    veins = {}
    for y in range(y0, y1):
        for x in range(x0, x1):
            tx, ty = (x - x0) // 16, (y - y0) // 16
            lx, ly = (x - x0) % 16, (y - y0) % 16
            light = (tx + ty) % 2 == 0
            base = FL[3] if light else FL[2]
            col = base
            if lx == 15 or ly == 15:
                col = FL[0]
            elif lx == 0 or ly == 0:
                col = FL[4] if light else FL[3]
            else:
                key = (tx, ty)
                if key not in veins:                                      # žilka: šikmá vlnovka přes desku
                    veins[key] = (r.uniform(-0.9, 0.9), r.uniform(2, 13), r.uniform(0, 6.28))
                k, off, ph = veins[key]
                v = ly - (off + k * lx + 1.6 * math.sin(lx * 0.7 + ph))
                if abs(v) < 0.5 and (lx + ly) % 3 != 0:
                    col = FL[2] if light else FL[1]
            # kosočtverec v křížení spár
            dx, dy = (lx + 1) % 16, (ly + 1) % 16
            ddx, ddy = min(dx, 16 - dx), min(dy, 16 - dy)
            if ddx + ddy <= 2:
                col = NAVY[2] if ddx + ddy <= 1 else NAVY[3]
            img[y, x] = col


def carpet(img, x0, x1, y0, y1):
    """Modrý koberec: tmavý okraj, mosazný lem, uvnitř jemný vzor vah po 16 px."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            e = min(x - x0, x1 - 1 - x)
            if e == 0:
                col = NAVY[0]
            elif e == 1:
                col = BRASS[3]
            elif e == 2:
                col = BRASS[1]
            else:
                col = NAVY[2] if ((y // 2) + (x // 2)) % 2 else NAVY[1]
                cx = (x0 + x1) // 2
                ly = (y - y0) % 16
                # malé váhy: sloupek a ramena
                if x == cx and 4 <= ly <= 10:
                    col = BRASS[2]
                elif ly == 5 and abs(x - cx) <= 4:
                    col = BRASS[2]
                elif ly in (8, 9) and abs(abs(x - cx) - 4) <= 1:
                    col = BRASS[1] if ly == 9 else BRASS[3]
            img[y, x] = col


def floor_emblem(r=18):
    """Mozaika v podlaze: mosazný kruh, modré pole, váhy v rovnováze, kolem dvanáct teček (měsíce měření)."""
    d = r * 2 + 1
    s = Spr(d, d)
    for y in range(d):
        for x in range(d):
            q = math.hypot(x - r, y - r)
            if q <= r:
                if q > r - 1.2:
                    col = BRASS[1]
                elif q > r - 2.4:
                    col = BRASS[3]
                elif q > r - 3.4:
                    col = NAVY[0]
                else:
                    col = NAVY[2] if ((x + y) // 2) % 2 else NAVY[1]
                s.px(x, y, col)
    for i in range(12):
        a = i / 12 * 6.283
        s.px(round(r + math.cos(a) * (r - 5)), round(r + math.sin(a) * (r - 5)), BRASS[3])
    # váhy
    s.vline(r, r - 9, r + 7, BRASS[3]); s.vline(r + 1, r - 9, r + 7, BRASS[1])
    s.rect(r - 4, r + 7, r + 5, r + 8, BRASS[2]); s.hline(r - 4, r + 5, r + 7, BRASS[4])
    s.ellipse(r + 0.5, r - 10, 1.6, 1.6, BRASS[4])
    s.hline(r - 10, r + 11, r - 7, BRASS[3]); s.hline(r - 10, r + 11, r - 6, BRASS[1])
    for sx in (r - 9, r + 10):
        s.line(sx, r - 6, sx - 3, r + 1, BRASS[2]); s.line(sx, r - 6, sx + 3, r + 1, BRASS[2])
        s.hline(sx - 4, sx + 4, r + 2, BRASS[3]); s.hline(sx - 3, sx + 3, r + 3, BRASS[1])
    return s


# ── zadní stěna ─────────────────────────────────────────────────────────────

def back_wall(W, H):
    """Římsa se zubořezem, mramorové kvádry, kanelované pilastry, sokl. Okna, výtah a obrazy se vkládají zvlášť."""
    s = Spr(W, H)
    for y in range(H):
        for x in range(W):
            if y < 3:
                col = MARB[1] if y == 2 else MARB[3]
            elif y < 7:
                col = MARB[4] if (x % 4) < 2 and y < 6 else MARB[1]           # zubořez
            elif y < 9:
                col = MARB[5] if y == 7 else MARB[2]
            elif y >= H - 6:
                col = NAVY[1] if y >= H - 2 else NAVY[3] if y == H - 6 else NAVY[2]   # modrý sokl
            else:
                row = (y - 9) // 8
                bx = (x + (row % 2) * 10) % 20
                by = (y - 9) % 8
                col = MARB[3]
                if by == 7 or bx == 19:
                    col = MARB[1]
                elif by == 0 or bx == 0:
                    col = MARB[5]
                elif (bx * 3 + by * 5 + row) % 23 == 0:
                    col = MARB[2]
            s.px(x, y, col)
    return s


def pilaster(h):
    """Kanelovaný pilastr 10 × h s hlavicí a patkou."""
    s = Spr(10, h)
    for y in range(h):
        for x in range(10):
            if y < 4 or y >= h - 4:
                col = MARB[5] if y in (0, h - 4) else MARB[3] if y in (1, 2, h - 3) else MARB[1]
            else:
                f = x % 3
                col = MARB[5] if f == 0 else MARB[3] if f == 1 else MARB[2]
                if x in (0, 9):
                    col = MARB[1]
            s.px(x, y, col)
    return outline(s, K)


def window(w=20, h=50, seed=3):
    """Vysoké okno s obloukem: mosazný rám, příčky, venku šedé nebe a padající popel."""
    s = Spr(w, h)
    r = np.random.default_rng(seed)
    rad = w / 2
    for y in range(h):
        for x in range(w):
            if y < rad and math.hypot(x + .5 - rad, y + .5 - rad) > rad:
                continue
            s.px(x, y, BRASS[2])
    for y in range(2, h - 2):
        for x in range(2, w - 2):
            if y < rad and math.hypot(x + .5 - rad, y + .5 - rad) > rad - 2:
                continue
            t = y / h
            col = ASH[3] if t < 0.3 else ASH[2] if t < 0.7 else ASH[1]
            s.px(x, y, col)
    for _ in range(14):                                                   # vločky popela
        x, y = int(r.integers(3, w - 3)), int(r.integers(4, h - 4))
        if s.opaque(x, y):
            s.px(x, y, ASH[0] if r.random() < 0.5 else c(0xEEEAE2))
    s.vline(w // 2, 3, h - 3, BRASS[1])                                    # příčky
    for y in (int(h * 0.42), int(h * 0.72)):
        s.hline(2, w - 3, y, BRASS[1])
    s.rect(0, h - 3, w - 1, h - 1, MARB[4]); s.hline(0, w - 1, h - 1, MARB[1])   # parapet
    return outline(s, K)


def portrait(seed=0):
    """Portrét „kalibrovaného“ 16 × 20: mosazný rám, modré pozadí, postava v kabátě – místo tváře hladká bledá plocha."""
    s = Spr(16, 22)
    s.rect(0, 0, 15, 19, BRASS[2]); s.hline(0, 15, 0, BRASS[4]); s.vline(0, 0, 19, BRASS[3])
    s.hline(0, 15, 19, BRASS[0]); s.vline(15, 0, 19, BRASS[1])
    s.rect(2, 2, 13, 17, NAVY[2])
    for y in range(2, 18):
        for x in range(2, 14):
            if (x + y) % 5 == 0:
                s.px(x, y, NAVY[1])
    coat = [NAVY[0], IRON[1], WALN[1]][seed % 3]
    s.ellipse(8, 17, 5.2, 4.5, coat)                                       # ramena
    s.rect(6, 12, 9, 13, SKIN[1])                                          # krk
    s.ellipse(7.5, 8.5, 3.4, 3.9, SKIN[2])                                 # hlava bez tváře
    s.hline(5, 10, 5, [IRON[1], WALN[2], ASH[1]][seed % 3])                # vlasy
    s.px(9, 8, SKIN[1]); s.px(9, 9, SKIN[1])                               # jen stín, žádné oči
    s.rect(5, 20, 10, 21, BRASS[3]); s.hline(5, 10, 21, BRASS[0])          # štítek s číslem
    return outline(s, K)


def tube_pipe(h):
    """Mosazná trubka potrubní pošty 4 × h se spoji."""
    s = Spr(4, h)
    for y in range(h):
        s.px(0, y, BRASS[1]); s.px(1, y, BRASS[4]); s.px(2, y, BRASS[2]); s.px(3, y, BRASS[0])
        if y % 12 == 0:
            s.hline(0, 3, y, BRASS[3])
    return s


# ── výtah a ukazatel pater ──────────────────────────────────────────────────

def elevator(w=40, h=58):
    """Výtah: mramorové ostění se svorníkem, mosazná mřížová klec (kosočtverce), uvnitř tma, práh."""
    s = Spr(w, h)
    s.rect(0, 0, w - 1, h - 1, MARB[3])
    s.rect(0, 0, w - 1, 4, MARB[4]); s.hline(0, w - 1, 0, MARB[5]); s.hline(0, w - 1, 4, MARB[1])
    s.rect(w // 2 - 3, 0, w // 2 + 2, 6, MARB[5]); box_outline(s, w // 2 - 3, 0, w // 2 + 2, 6, MARB[1])   # svorník
    s.vline(0, 0, h - 1, MARB[5]); s.vline(w - 1, 0, h - 1, MARB[1])
    ix0, iy0, ix1, iy1 = 5, 8, w - 6, h - 4
    s.rect(ix0, iy0, ix1, iy1, IRON[0])
    for y in range(iy0 + 1, iy1):                                          # tlumené světlo kabiny
        for x in range(ix0 + 1, ix1):
            if (y - iy0) < 6:
                s.px(x, y, IRON[1])
    for y in range(iy0, iy1 + 1):                                          # mřížová klec
        for x in range(ix0, ix1 + 1):
            u, v = (x - ix0 + y - iy0) % 6, (x - ix0 - (y - iy0)) % 6
            if u == 0 or v == 0:
                s.px(x, y, BRASS[3] if u == 0 else BRASS[2])
    s.vline(w // 2, iy0, iy1, BRASS[1]); s.vline(w // 2 - 1, iy0, iy1, BRASS[4])   # střed dveří
    s.rect(ix0, iy0, ix1, iy0 + 1, BRASS[2]); s.rect(ix0, iy1 - 1, ix1, iy1, BRASS[1])
    s.rect(2, h - 3, w - 3, h - 1, BRASS[2]); s.hline(2, w - 3, h - 3, BRASS[4])          # práh
    s.rect(w - 4, 24, w - 2, 32, BRASS[2]); s.px(w - 3, 26, RED[2]); s.px(w - 3, 29, BRASS[4])   # tlačítka
    return outline(s, K)


def floor_dial(r=15):
    """Ukazatel pater: mosazný půlkruh, čísla 0–7 jako rysky, ručička na 0; pod nulou dvě zaslepené pozice."""
    w, h = r * 2 + 3, r + 7
    s = Spr(w, h)
    cx, cy = r + 1, r + 1
    for y in range(h):
        for x in range(w):
            q = math.hypot(x - cx, y - cy)
            if y <= cy and q <= r:
                s.px(x, y, BRASS[1] if q > r - 1 else BRASS[3] if q > r - 2.5 else PAPER[3] if q > 3 else BRASS[2])
    s.rect(cx - r, cy, cx + r, cy + 1, BRASS[1])
    for i in range(8):                                                     # 0 vlevo … 7 vpravo
        a = math.pi - i / 7 * math.pi
        x0, y0 = cx + math.cos(a) * (r - 3), cy - math.sin(a) * (r - 3)
        x1, y1 = cx + math.cos(a) * (r - 6), cy - math.sin(a) * (r - 6)
        s.line(x0, y0, x1, y1, INK if i else RED[2])
    s.line(cx, cy, cx - r + 5, cy - 1, RED[1])                              # ručička na 0
    s.ellipse(cx, cy, 1.6, 1.6, BRASS[4])
    for k, x in enumerate((cx - 8, cx + 4)):                                # dvě zaslepené pozice pod nulou
        s.rect(x, cy + 3, x + 4, cy + 5, IRON[2]); s.hline(x, x + 4, cy + 3, IRON[3])
        s.px(x + 1 + k, cy + 4, IRON[0]); s.px(x + 3 - k, cy + 4, IRON[0])   # škrábance
    return outline(s, K)


# ── Septův pult ─────────────────────────────────────────────────────────────

def desk(w=66, h=30):
    """Úřední pult: mramorová deska s mosaznou hranou, ořechové čelo s panely a vahami, na desce věci."""
    s = Spr(w, h)
    top = 9
    s.rect(0, top, w - 1, top + 4, MARB[4]); s.hline(0, w - 1, top, MARB[5]); s.hline(0, w - 1, top + 4, MARB[1])
    s.hline(0, w - 1, top + 5, BRASS[3])
    for y in range(top + 6, h):
        for x in range(w):
            p = x % 16
            col = WALN[2]
            if y == h - 1 or y == h - 2:
                col = NAVY[1]
            elif p in (0, 15):
                col = WALN[1]
            elif p in (2, 13) or y in (top + 8, h - 5):
                col = WALN[3]
            s.px(x, y, col)
    mx = w // 2                                                             # váhy na čele
    s.rect(mx - 6, top + 9, mx + 6, top + 18, NAVY[2]); box_outline(s, mx - 6, top + 9, mx + 6, top + 18, BRASS[2])
    s.vline(mx, top + 11, top + 17, BRASS[3]); s.hline(mx - 4, mx + 4, top + 12, BRASS[3])
    s.px(mx - 4, top + 14, BRASS[3]); s.px(mx + 4, top + 14, BRASS[3])
    # věci na desce (nad horní hranou, sahají do 0..top)
    # psací stroj
    s.rect(6, top - 5, 19, top, IRON[1]); s.rect(7, top - 4, 18, top - 3, IRON[2])
    for x in range(8, 18, 2):
        s.px(x, top - 2, PAPER[3]); s.px(x + 1, top - 1, PAPER[3])
    s.rect(9, top - 8, 16, top - 5, PAPER[3]); s.hline(10, 15, top - 7, IRON[3])
    # stohy spisů
    for i, (x, hh) in enumerate(((22, 6), (27, 4))):
        for k in range(hh):
            s.rect(x, top - 1 - k, x + 4, top - 1 - k, PAPER[2] if k % 2 else c(0xD8C8A0))
        s.hline(x, x + 4, top - hh, NAVY[3])
    # razítko a podušky
    s.rect(36, top - 2, 40, top, NAVY[2]); s.rect(37, top - 6, 39, top - 3, WALN[3]); s.rect(36, top - 7, 40, top - 6, WALN[4])
    # zvonek
    s.ellipse(45.5, top - 1.5, 2.5, 2.0, BRASS[3]); s.px(45, top - 4, BRASS[4])
    # bankéřská lampa
    s.rect(52, top - 1, 58, top, BRASS[1]); s.vline(55, top - 6, top - 1, BRASS[2])
    s.rect(51, top - 9, 59, top - 6, GREEN[2]); s.hline(51, 59, top - 9, GREEN[3]); s.hline(51, 59, top - 6, GREEN[0])
    # jmenovka
    s.rect(24, top + 1, 34, top + 3, BRASS[3]); s.hline(25, 33, top + 2, INK)
    return outline(s, K)


def sept():
    """Archivář Sept 20 × 28 (horní polovina, za pultem): uhlazené šedé vlasy s pěšinkou, kulaté brýle, ve kterých se
    odráží jen světlo, vysoký límec, tmavomodrý kabát s mosaznými knoflíky a páskou na rukávu se znakem vah."""
    s = Spr(20, 28)
    s.ellipse(10, 21, 8.5, 7.5, NAVY[1])                                   # ramena a trup
    s.rect(2, 21, 17, 27, NAVY[1])
    for y in range(16, 28):
        s.px(5, y, NAVY[2]); s.px(14, y, NAVY[0])
    s.vline(10, 17, 27, NAVY[0])
    for y in (19, 23, 27):
        s.px(9, y, BRASS[3]); s.px(11, y, BRASS[3])
    s.rect(1, 22, 4, 24, PAPER[2]); s.px(2, 23, BRASS[2])                    # páska na rukávu
    s.rect(7, 13, 13, 16, PAPER[3]); s.vline(10, 14, 16, PAPER[1])           # vysoký límec
    s.ellipse(10, 8, 5.2, 6.0, SKIN[2])                                     # hlava
    s.rect(5, 2, 15, 4, ASH[2]); s.ellipse(10, 4, 5.4, 3.0, ASH[2])           # vlasy
    s.hline(5, 9, 3, ASH[3]); s.vline(8, 2, 4, ASH[0])                       # pěšinka
    s.px(5, 6, ASH[1]); s.px(15, 6, ASH[1])
    for gx in (7, 13):                                                      # brýle: jen odlesk
        s.ellipse(gx, 8.5, 1.9, 1.7, PAPER[3]); s.px(gx - 1, 8, c(0xFFFFFF))
    s.hline(8, 12, 8, IRON[2])
    s.hline(8, 12, 12, SKIN[0])                                             # rovná ústa
    s.px(14, 10, SKIN[1]); s.px(15, 9, SKIN[1])
    s.rect(15, 18, 18, 20, SKIN[1])                                         # ruka s perem
    s.line(18, 17, 19, 14, INK)
    return outline(s, K)


# ── čekárna a kartotéka ─────────────────────────────────────────────────────

def bench(w=36):
    """Čekárenská lavice z ořechu s mosaznými nohami a opěradlem."""
    s = Spr(w, 12)
    s.rect(0, 0, w - 1, 3, WALN[2]); s.hline(0, w - 1, 0, WALN[4]); s.hline(0, w - 1, 3, WALN[0])
    s.rect(0, 5, w - 1, 8, WALN[3]); s.hline(0, w - 1, 5, WALN[4]); s.hline(0, w - 1, 8, WALN[1])
    for x in (2, w // 2, w - 3):
        s.vline(x, 4, 4, BRASS[2]); s.vline(x, 9, 11, BRASS[1])
    return outline(s, K)


def ticket_machine():
    """Automat na pořadová čísla 12 × 24: mosazná skříňka, okénko s číslem, výdejní štěrbina, lístek."""
    s = Spr(12, 24)
    s.rect(1, 0, 10, 20, BRASS[2]); s.vline(1, 0, 20, BRASS[4]); s.vline(10, 0, 20, BRASS[0]); s.hline(1, 10, 0, BRASS[4])
    s.rect(3, 3, 8, 7, INK); s.px(4, 5, RED[2]); s.px(5, 5, RED[2]); s.px(6, 4, RED[2]); s.px(7, 5, RED[2])
    s.rect(3, 10, 8, 11, IRON[1]); s.rect(4, 12, 7, 15, PAPER[3]); s.hline(4, 7, 14, INK)
    s.ellipse(5.5, 17.5, 1.5, 1.5, RED[2])
    s.rect(4, 20, 7, 23, IRON[1])
    return outline(s, K)


def queue_board():
    """Stojan s tabulí „PROSÍM ČEKEJTE · SUBJEKT 213“ a hodinami nahoře (stojí na 6:12)."""
    s = Spr(30, 40)
    s.ellipse(15, 6, 6.2, 6.2, BRASS[2]); s.ellipse(15, 6, 5, 5, PAPER[3])
    s.line(15, 6, 15, 10, INK)                                              # 6
    s.line(15, 6, 17, 4, INK)                                               # :12 (krátká ručička)
    s.px(15, 2, INK); s.px(19, 6, INK); s.px(11, 6, INK)
    s.rect(1, 12, 28, 30, NAVY[1]); box_outline(s, 1, 12, 28, 30, BRASS[2])
    for x in range(4, 26, 2):                                               # řádek „PROSÍM ČEKEJTE“
        s.px(x, 15, PAPER[2])
    digits = {"2": ["111", "001", "111", "100", "111"], "1": ["010", "110", "010", "010", "111"], "3": ["111", "001", "111", "001", "111"]}
    for k, ch in enumerate("213"):                                          # velké číslo 213
        for yy, row in enumerate(digits[ch]):
            for xx, b in enumerate(row):
                if b == "1":
                    s.rect(7 + k * 6 + xx, 19 + yy * 2, 7 + k * 6 + xx, 20 + yy * 2, RED[2])
    s.vline(14, 31, 37, BRASS[1]); s.vline(15, 31, 37, BRASS[3])
    s.rect(9, 37, 20, 39, BRASS[1]); s.hline(9, 20, 37, BRASS[3])
    return outline(s, K)


def cabinet(seed=0):
    """Kartotéka 22 × 34: šedá ocel, čtyři řady šuplíků s mosaznými štítky a úchyty, jeden pootevřený."""
    s = Spr(22, 34)
    s.rect(0, 0, 21, 33, IRON[3]); s.hline(0, 21, 0, IRON[4]); s.vline(0, 0, 33, IRON[4]); s.vline(21, 0, 33, IRON[1])
    for row in range(4):
        y = 2 + row * 8
        for col in range(2):
            x = 2 + col * 10
            s.rect(x, y, x + 8, y + 6, IRON[2]); s.hline(x, x + 8, y, IRON[4])
            s.rect(x + 2, y + 1, x + 6, y + 2, PAPER[3]); s.hline(x + 3, x + 5, y + 4, BRASS[3])
    open_r = 1 + seed % 3
    y = 2 + open_r * 8
    s.rect(12, y + 5, 20, y + 7, IRON[1])
    for x in range(13, 20):
        s.px(x, y + 5, PAPER[2] if x % 2 else PAPER[3])
    return outline(s, K)


def stanchion_line(w):
    """Sloupky s modrým provazem (fronta k pultu)."""
    s = Spr(w, 13)
    posts = list(range(1, w - 1, max(6, (w - 3) // 3)))
    if posts[-1] < w - 3:
        posts.append(w - 3)
    for p in posts:
        s.rect(p, 3, p + 1, 11, BRASS[2]); s.vline(p, 3, 11, BRASS[4]); s.ellipse(p + 0.5, 2, 1.5, 1.5, BRASS[3])
        s.hline(p - 1, p + 2, 12, BRASS[1])
    for a, b in zip(posts, posts[1:]):
        for x in range(a + 2, b):
            y = 4 + int(2.5 * math.sin(math.pi * (x - a) / max(1, b - a)))
            s.px(x, y, NAVY[3]); s.px(x, y + 1, NAVY[1])
    return outline(s, K)


def dead_plant():
    """Uschlá rostlina v kamenném květináči: šedé suché listy, popel v hlíně."""
    s = Spr(14, 22)
    for i, (dx, dy) in enumerate(((-4, -9), (3, -11), (-1, -13), (5, -6), (-5, -4))):
        s.line(7, 13, 7 + dx, 13 + dy, ASH[1] if i % 2 else ASH[0])
        s.px(7 + dx, 13 + dy, ASH[2])
    s.rect(2, 13, 11, 20, MARB[3]); s.hline(2, 11, 13, MARB[5]); s.vline(11, 13, 20, MARB[1]); s.hline(2, 11, 20, MARB[1])
    s.hline(3, 10, 14, ASH[0])
    return outline(s, K)


def side_column(h=40, seed=0):
    """Sloup u boční stěny (pohled shora): mramorový dřík s kanelurami, hlavice a patka."""
    s = Spr(14, h)
    for y in range(h):
        for x in range(14):
            if y < 5 or y >= h - 4:
                col = MARB[5] if y in (0, h - 4) else MARB[3]
            else:
                col = [MARB[4], MARB[3], MARB[2]][x % 3]
            s.px(x, y, col)
    return outline(s, K)


# ── cutscéna: průčelí Věže s obřími dveřmi ──────────────────────────────────

BRONZE = [c(0x2A1A10), c(0x4A3018), c(0x6E4A24), c(0x946634), c(0xB88A4A), c(0xE0B870)]


def tower_facade(W=192, H=288, seed=5):
    """Průčelí Věže pro cutscénu: popelavé nebe, věž s patry (římsy, řady oken, nahoře tmavší), pilastry
    s vrženým stínem, hluboký portál s mosazným lemem a erbem vah, široké schodiště, lucerny.
    Uvnitř portálu je studené bílé světlo chodby; dveřní křídla kreslí aplikace (otevírají se)."""
    rng = np.random.default_rng(seed)
    img = np.zeros((H, W, 4), np.uint8); img[:, :, 3] = 255
    for y in range(H):
        t = y / H
        col = np.array([58, 56, 60]) * (1 - t) + np.array([120, 116, 112]) * t
        img[y, :, :3] = col.astype(np.uint8)
    # věž: lehce se zužuje nahoru (patra)
    base_y = H - 34
    for y in range(0, base_y):
        t = y / base_y
        half = int(W * 0.47 - (1 - t) * 10)
        x0, x1 = W // 2 - half, W // 2 + half
        floor_i = (base_y - y) // 38
        ly = (base_y - y) % 38
        for x in range(x0, x1):
            row = (y // 8); bx = (x + (row % 2) * 10) % 20; by = y % 8
            col = MARB[3]
            if by == 7 or bx == 19:
                col = MARB[1]
            elif by == 0 or bx == 0:
                col = MARB[4]
            if ly in (36, 37):                                            # římsa každého patra
                col = MARB[5] if ly == 37 else MARB[1]
            img[y, x] = col
            if x in (x0, x1 - 1):
                img[y, x] = K
        # okna pater (nad portálem a po stranách)
        if floor_i >= 1 and ly in range(10, 28):
            for k in range(-3, 4):
                wx = W // 2 + k * 22
                if abs(k) <= 1 and floor_i < 4:
                    continue
                if x0 + 6 < wx < x1 - 10:
                    lit_win = ((floor_i * 7 + k * 3) % 5 == 0)
                    for x in range(wx - 3, wx + 4):
                        img[y, x, :3] = (255, 236, 170) if lit_win and ly > 12 else (24, 28, 44) if ly > 11 else (40, 46, 70)
                    img[y, wx - 4, :3] = MARB[5][:3]; img[y, wx + 4, :3] = MARB[0][:3]
        # nahoře tmavne (výška, popel)
        k = round((0.55 * (1 - t) ** 1.4) * 6) / 6
        if k:
            img[y, x0:x1, :3] = (img[y, x0:x1, :3] * (1 - k) + np.array([20, 22, 34]) * k).astype(np.uint8)
    # pilastry s vrženým stínem
    for px in (22, 52, 130, 160):
        p = pilaster(base_y - 26)
        for yy in range(p.h):
            for xx in range(p.w):
                if p.a[yy, xx, 3]:
                    img[22 + yy, px + xx] = p.a[yy, xx]
            for xx in range(p.w, p.w + 4):
                X = px + xx
                if X < W:
                    img[22 + yy, X, :3] = (img[22 + yy, X, :3] * 0.72).astype(np.uint8)
    # portál: hluboký výklenek
    pw, ph = 76, 150
    x0, y0 = W // 2 - pw // 2, base_y - ph
    rr = pw / 2
    for y in range(y0 - 6, base_y):
        for x in range(x0 - 6, x0 + pw + 6):
            q = math.hypot(x + .5 - (x0 + rr), y + .5 - (y0 + rr))
            if y < y0 + rr and q > rr + 6:
                continue
            outer = y < y0 + rr and q > rr or x < x0 or x >= x0 + pw
            img[y, x] = BRASS[2] if outer and (q > rr + 4 or x < x0 - 4 or x >= x0 + pw + 4) else MARB[5] if outer else MARB[0]
    ix0, iy0, iw, ih = x0 + 8, y0 + 8, pw - 16, ph - 8
    for y in range(y0, base_y):
        for x in range(x0, x0 + pw):
            if y < y0 + rr and math.hypot(x + .5 - (x0 + rr), y + .5 - (y0 + rr)) > rr:
                continue
            inner = (ix0 <= x < ix0 + iw) and (y >= iy0) and not (y < iy0 + iw / 2 and math.hypot(x + .5 - (ix0 + iw / 2), y + .5 - (iy0 + iw / 2)) > iw / 2)
            if inner:
                q = abs(x - (ix0 + iw / 2)) / (iw / 2)                     # chodba za dveřmi: bílé světlo do hloubky
                d = (y - iy0) / ih
                v = 255 - q * 60 - d * 30
                img[y, x, :3] = np.clip([v - 18, v - 8, v], 0, 255).astype(np.uint8)
            else:
                d = min(x - x0, x0 + pw - 1 - x) / 8                          # ostění výklenku: hloubka
                img[y, x, :3] = (np.array(MARB[2][:3]) * (0.55 + 0.45 * min(1, d))).astype(np.uint8)
    em = floor_emblem(16)
    _paste(img, em, W // 2 - em.w // 2, y0 - 44)
    # schodiště
    for i in range(7):
        y = base_y + i * 5
        for yy in range(y, min(H, y + 5)):
            for x in range(x0 - 18 - i * 7, x0 + pw + 18 + i * 7):
                if 0 <= x < W:
                    img[yy, x] = MARB[5] if yy == y else MARB[3] if yy < y + 3 else MARB[1]
    # lucerny s teplou září
    for lx in (x0 - 16, x0 + pw + 10):
        for y in range(y0 + 20, y0 + 70):
            for x in range(lx - 20, lx + 26):
                d = math.hypot((x - lx - 3) / 1.2, y - (y0 + 44)) / 22
                if 0 <= x < W and d < 1:
                    a = (1 - d) ** 2 * 0.35
                    img[y, x, :3] = np.clip(img[y, x, :3] * (1 - a) + np.array([255, 210, 140]) * a, 0, 255).astype(np.uint8)
        l = Spr(7, 16); l.rect(1, 2, 5, 10, BRASS[2]); l.rect(2, 3, 4, 9, c(0xFFF2C0)); l.vline(3, 11, 15, IRON[1]); l.hline(0, 6, 1, BRASS[3]); outline(l, K)
        _paste(img, l, lx, y0 + 38)
    # padající popel
    for _ in range(160):
        x, y = int(rng.integers(0, W)), int(rng.integers(0, H))
        img[y, x, :3] = (205, 200, 192) if rng.random() < 0.7 else (150, 146, 140)
    return img, (W // 2, iy0, iw, ih)


def door_leaf(iw=60, ih=142):
    """Levé křídlo bronzových dveří (pravé = zrcadlo): tvar poloviny oblouku portálu, reliéfní panely,
    nýty, polovina erbu vah u středu, těžká závora. Vrací RGBA pole (iw/2 × ih)."""
    w, h = iw // 2, ih
    a = np.zeros((h, w, 4), np.uint8)
    rr = iw / 2
    for y in range(h):
        for x in range(w):
            if y < rr and math.hypot(x + .5 - rr, y + .5 - rr) > rr:
                continue
            col = BRONZE[3]
            if x == w - 1:
                col = BRONZE[0]                                              # spára mezi křídly
            elif x < 2:
                col = BRONZE[4] if x == 0 else BRONZE[2]
            a[y, x] = col
    # reliéfní panely
    panels = [(4, 30, w - 4, 62), (4, 68, w - 4, 102), (4, 108, w - 4, h - 6)]
    for (px0, py0, px1, py1) in panels:
        for y in range(py0, py1):
            for x in range(px0, px1):
                if a[y, x, 3]:
                    e = min(x - px0, px1 - 1 - x, y - py0, py1 - 1 - y)
                    a[y, x] = BRONZE[5] if (e == 0 and (x == px0 or y == py0)) else BRONZE[1] if e == 0 else BRONZE[2] if e == 1 else BRONZE[3]
        for (rx, ry) in ((px0 + 2, py0 + 2), (px1 - 3, py0 + 2), (px0 + 2, py1 - 3), (px1 - 3, py1 - 3)):
            a[ry, rx] = BRONZE[5]; a[ry + 1, rx + 1] = BRONZE[0]
    # nýty po obvodu
    for y in range(int(rr) + 4, h - 2, 6):
        a[y, 3] = BRONZE[5]; a[y + 1, 3] = BRONZE[1]
    # polovina erbu vah u středu (kruh na spáře)
    cy = 85
    for y in range(cy - 13, cy + 14):
        for x in range(w - 13, w):
            q = math.hypot(x + .5 - w, y + .5 - cy)
            if q <= 13:
                a[y, x] = BRASS[1] if q > 12 else BRASS[3] if q > 10.5 else NAVY[1] if q > 9.5 else NAVY[2]
    for x in range(w - 9, w):
        a[cy - 5, x] = BRASS[4]
    a[cy - 4:cy + 6, w - 1] = BRASS[3]
    for y in range(cy - 4, cy + 2):
        a[y, w - 8 + (y - cy + 4) // 2] = BRASS[3]
    a[cy + 2, w - 11:w - 4] = BRASS[3]
    # závora (vodorovná tyč přes střed)
    a[cy + 20:cy + 24, 2:w] = IRON[2]; a[cy + 20, 2:w] = IRON[4]; a[cy + 23, 2:w] = IRON[0]
    return a


def _paste(dst, spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < dst.shape[0] and 0 <= x + xx < dst.shape[1]:
                dst[y + yy, x + xx] = spr.a[yy, xx]


def calibration_scale():
    """Velké kalibrační váhy 34 × 46 na mramorovém podstavci: mosazný sloup, rameno v dokonalé rovnováze,
    dvě misky – na jedné závaží, na druhé… nic. Přesto stojí rovně."""
    s = Spr(34, 46)
    s.rect(5, 36, 28, 45, MARB[3]); s.hline(5, 28, 36, MARB[5]); s.rect(5, 37, 28, 38, MARB[4])
    s.vline(28, 36, 45, MARB[1]); s.hline(5, 28, 45, MARB[1]); s.rect(7, 40, 26, 43, MARB[2])
    s.rect(9, 41, 24, 42, BRASS[2])                                       # štítek
    s.rect(15, 33, 18, 35, BRASS[1]); s.hline(15, 18, 33, BRASS[3])
    s.vline(16, 8, 33, BRASS[3]); s.vline(17, 8, 33, BRASS[1])            # sloup
    s.ellipse(16.5, 6, 2.6, 2.6, BRASS[3]); s.px(16, 5, BRASS[4])
    s.hline(3, 30, 9, BRASS[4]); s.hline(3, 30, 10, BRASS[1])              # rameno
    for sx in (4, 29):
        s.line(sx, 11, sx - 3, 20, BRASS[2]); s.line(sx, 11, sx + 3, 20, BRASS[2])
        s.ellipse(sx, 21, 4.5, 1.8, BRASS[3]); s.hline(sx - 4, sx + 4, 22, BRASS[0])
    s.rect(1, 17, 6, 20, IRON[2]); s.hline(1, 6, 17, IRON[4])              # závaží vlevo
    return outline(s, K)

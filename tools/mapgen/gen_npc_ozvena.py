"""Obyvatelé Ozvěny (Akt II, docs/adr/0093) – postavy ze stejné Sunnyside předlohy jako hráč (docs/adr/0051).

Základ: tools/sprites/sunnyside_human.aseprite, animace idle-s (4 snímky), vybraný účes.
Každá postava = přebarvené vlasy, košile a kalhoty + vlastní doplněk (čepice, zástěra, kniha, košík…),
který se kreslí na každý snímek podle polohy hlavy a trupu, takže se hýbe s dýcháním.
Na mapě 1 px spritu = 2 dp, stejně jako hráč (MakromonMapActivity.placeTownsfolk).

Výstup: app/src/main/res/drawable-nodpi/npc_<id>_map.png (pás 4 snímků, pata na posledním řádku)
"""
import os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "sprites"))
import ase_extract as A  # noqa: E402

RES = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable-nodpi")
SRC = os.path.join(HERE, "..", "sprites", "sunnyside_human.aseprite")
CROP = (34, 8, 62, 39)            # 28 × 31, postava má střed na x = 14, pata na posledním řádku
CX = 48 - CROP[0]

K = (24, 20, 37)
RED = [(233, 50, 69), (166, 31, 52)]          # košile (světlá, tmavá)
BLUE = [(55, 68, 100), (36, 43, 66)]          # kalhoty / laclové kalhoty
SLEEVE = (63, 39, 49)


def near(p, q, t=6): return sum(abs(a - b) for a, b in zip(p[:3], q)) <= t


def lum(p): return 0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2]


def render(ase, f, hair):
    ase["hair"] = hair
    return A.render_frame(ase, f).crop(CROP).convert("RGBA")


def recolor(im, bare, hair_ramp, shirt, pants, sleeve=None):
    """hair_ramp: [světlá, střední, tmavá] ; shirt/pants: [světlá, tmavá]."""
    out = im.copy(); px = out.load(); b = bare.load()
    W, H = im.size
    hair = [(x, y) for y in range(H) for x in range(W) if px[x, y][3] and px[x, y] != b[x, y] and not near(px[x, y], K, 4)]
    if hair:
        ls = sorted({round(lum(px[x, y])) for x, y in hair})
        lo, hi = ls[0], ls[-1]
        for x, y in hair:
            t = 0 if hi == lo else (lum(px[x, y]) - lo) / (hi - lo)
            px[x, y] = hair_ramp[0 if t > 0.66 else 1 if t > 0.25 else 2] + (255,)
    for y in range(H):
        for x in range(W):
            p = px[x, y]
            if not p[3]: continue
            for src, dst in ((RED[0], shirt[0]), (RED[1], shirt[1]), (BLUE[0], pants[0]), (BLUE[1], pants[1])):
                if near(p, src): px[x, y] = dst + (255,); break
            else:
                if sleeve and near(p, SLEEVE, 3) and (x, y) not in hair: px[x, y] = sleeve + (255,)
    return out, hair


def put(px, x, y, c, W, H):
    if 0 <= x < W and 0 <= y < H: px[x, y] = (c + (255,)) if len(c) == 3 else c


def stamp(img, ox, oy, rows, pal):
    """ASCII obrázek do snímku, '.' = průhledné."""
    px = img.load(); W, H = img.size
    for dy, r in enumerate(rows):
        for dx, ch in enumerate(r):
            if ch != ".": put(px, ox + dx, oy + dy, pal[ch], W, H)


def eye_row(im, top):
    """Řádek očí: první řádek pod vlasy, kde je tmavý pixel s kůží po obou stranách."""
    px = im.load()
    skin = lambda p: p[3] and p[0] > 190 and 140 < p[1] < 190
    for y in range(top + 2, top + 14):
        if any(near(px[x, y], K, 4) and skin(px[x - 1, y]) and skin(px[x + 1, y]) for x in range(CX - 5, CX + 5)): return y
    return top + 7


def anchors(im, hair):
    px = im.load(); W, H = im.size
    top = min(y for _, y in hair) if hair else 13
    shirt_rows = [y for y in range(H) for x in range(W) if px[x, y][3] and (near(px[x, y], RED[0]) or near(px[x, y], RED[1]))]
    torso = min(shirt_rows) if shirt_rows else 24
    return top, torso, eye_row(im, top)


P = {
    "K": K, "W": (250, 248, 240), "w": (206, 204, 214), "A": (234, 222, 194), "a": (190, 172, 138),
    "B": (222, 160, 78), "b": (164, 102, 44), "c": (250, 214, 140), "M": (92, 56, 40),
    "U": (92, 132, 196), "u": (58, 86, 140), "T": (150, 98, 56), "t": (104, 64, 36),
    "Y": (236, 200, 96), "y": (182, 136, 52), "L": (226, 58, 58), "l": (150, 28, 40), "G": (120, 196, 96),
    "N": (238, 234, 222), "n": (150, 146, 152), "F": (255, 255, 255), "Q": (60, 52, 70), "R": (196, 64, 64),
    "X": (210, 232, 246),
}

NPCS = {
    # Pekař Bohoušek: kuchařská čepice, bílá košile, zástěra, knír, bochník v ruce
    "bohousek": dict(hair="short hair", hair_ramp=[(132, 88, 62), (96, 60, 44), (64, 40, 34)],
                     shirt=[(246, 244, 236), (196, 196, 210)], pants=[(228, 214, 182), (184, 166, 130)], sleeve=(196, 196, 210)),
    # Písařka Věnka: kaštanový drdol s brkem, zelené šaty, otevřená kniha
    "venka": dict(hair="long hair", hair_ramp=[(178, 96, 66), (130, 62, 48), (86, 40, 38)],
                  shirt=[(84, 160, 120), (48, 106, 84)], pants=[(84, 160, 120), (48, 106, 84)], sleeve=(48, 106, 84)),
    # Babička Lída: šedivý drdol, fialový svetr, vínová sukně, košík jablek
    "lida": dict(hair="long hair", hair_ramp=[(234, 234, 240), (190, 190, 204), (138, 136, 156)],
                 shirt=[(156, 116, 186), (106, 74, 136)], pants=[(176, 70, 84), (120, 42, 58)], sleeve=(106, 74, 136)),
    # Metař Jarda: modrá čepice, modré montérky, koště
    "jarda": dict(hair="spikey hair", hair_ramp=[(150, 104, 70), (110, 72, 50), (74, 46, 38)],
                  shirt=[(214, 210, 196), (164, 160, 150)], pants=[(92, 132, 196), (58, 86, 140)], sleeve=(58, 86, 140)),
    # Pan Kořínek: krátké šedivé vlasy a knír, hořčicová vesta, noviny
    "korinek": dict(hair="short hair", hair_ramp=[(214, 214, 220), (164, 162, 174), (112, 110, 126)],
                    shirt=[(214, 170, 74), (156, 116, 44)], pants=[(92, 84, 76), (60, 54, 50)], sleeve=(156, 116, 44)),
}


def accessories(name, im, top, torso, eye, k):
    px = im.load(); W, H = im.size
    if name == "bohousek":
        stamp(im, CX - 6, top - 6, [
            "...KKKKKKK...",
            ".KKWWWWWWwKK.",
            "KWWWWWWWWWWwK",
            "KWWWWWWWWWWwK",
            ".KWWWWWWWWwK.",
            "..KwWwWwWwK..",
            "..KKKKKKKKK..",
        ], P)
        # knír pod očima
        for dx in (-2, -1, 0, 1): put(px, CX + dx, eye + 2, P["M"], W, H)
        put(px, CX - 3, eye + 2, P["K"], W, H); put(px, CX + 2, eye + 2, P["K"], W, H)
        # bochník v pravé ruce postavy (vlevo na obrázku)
        stamp(im, CX - 9, torso + 1, ["KKKK.", "KcBBK", "KBbbK", ".KKK."], P)
        if k == 2: put(px, CX - 8, torso - 1, (255, 255, 255, 170), W, H); put(px, CX - 7, torso - 2, (255, 255, 255, 120), W, H)
    elif name == "venka":
        # brk zastrčený v drdolu
        stamp(im, CX + 2, top - 3, ["..F", ".FK", "FK.", "K.."] if k < 2 else ["...F", "..FK", ".FK.", "K..."], P)
        # otevřená kniha před tělem
        stamp(im, CX - 3, torso + 1, ["KKKKKKK", "KNNKNNK", "KnNKNnK", ".KKKKK."], P)
    elif name == "lida":
        # košík s jablky na předloktí (vlevo na obrázku)
        stamp(im, CX - 10, torso, ["..KKK..", ".K...K.", "KLGLlLK", "KTtTtTK", ".KKKKK."], P)
    elif name == "jarda":
        # čepice se štítkem přes vlasy
        stamp(im, CX - 5, top - 1, [
            "..KKKKKKK..",
            ".KUUUUUUuK.",
            "KUUUUUUUUuK",
            "KKKKKKKKKKK",
        ], P)
        # koště opřené vedle (vpravo na obrázku), metla se v snímcích 1 a 3 posune
        sx = CX + 7
        for y in range(torso - 6, torso + 4): put(px, sx, y, P["T"], W, H); put(px, sx + 1, y, P["K"], W, H)
        put(px, sx - 1, torso - 7, P["K"], W, H); put(px, sx, torso - 7, P["K"], W, H)
        off = 1 if k in (1, 3) else 0
        stamp(im, sx - 2 + off, torso + 4, [".KKK.", "KYYyK", "KYyYK", "KyYyK"], P)
    elif name == "korinek":
        # noviny drží před hrudí; ve snímku 3 otočí roh
        rows = ["KKKKKKKKK", "KNNNNNNNK", "KNnnNnnNK", "KNNNNNNNK", "KNnnnNnNK", "KKKKKKKKK"]
        if k == 3: rows[0] = "KKKKKKKK."; rows[1] = "KNNNNNNKK"
        stamp(im, CX - 4, torso + 1, rows, P)
        # šedivý knír
        for dx in (-2, -1, 0, 1): put(px, CX + dx, eye + 2, (176, 174, 186), W, H)
        put(px, CX - 3, eye + 2, P["K"], W, H); put(px, CX + 2, eye + 2, P["K"], W, H)


def build(ase, name):
    cfg = NPCS[name]
    frames = []
    for k in range(4):
        bare = render(ase, k, "none")
        im = render(ase, k, cfg["hair"])
        im, hair = recolor(im, bare, cfg["hair_ramp"], cfg["shirt"], cfg["pants"], cfg.get("sleeve"))
        top, torso, eye = anchors(render(ase, k, cfg["hair"]), hair)
        accessories(name, im, top, torso, eye, k)
        frames.append(im)
    w, h = frames[0].size
    strip = Image.new("RGBA", (w * 4, h))
    for k, f in enumerate(frames): strip.paste(f, (k * w, 0))
    return strip


if __name__ == "__main__":
    ase = A.parse(SRC)
    os.makedirs(RES, exist_ok=True)
    for n in NPCS:
        build(ase, n).save(os.path.join(RES, f"npc_{n}_map.png"))
    print("ok", list(NPCS))

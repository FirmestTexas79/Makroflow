"""
Mapa „Zóna 1“ do deníku (docs/adr/0052).

Použití:  python tools/mapgen/gen_zone.py [--preview out.png]
Výstup:   app/src/main/assets/zone/<BIOME>.png  – výřez lokace ve tvaru chozeného území (maska chůze
                                                  rozšířená o okolí), inkoustový obrys, lehce do sépie
          app/src/main/assets/zone/head_<BIOME>.png – hlava NPC lokace (kulatý odznak kreslí aplikace)
          app/src/main/assets/zone/zone1.json   – rozvržení v jednotkách plátna W × H: obdélníky lokací,
                                                  převod pozice v lokaci na plátno, spoje východ → vchod

Tvar lokace = maska chůze (assets/walk/*.txt), takže vchody a východy leží na mapě tam, kde v lokaci.
Venkovní mapy (město, louka, hory) se ve hře kreslí centerCrop na telefon 1280 × 2856 – výřez je tedy
jen viditelná část a souřadnice uzlů jsou podíly obrazovky (stejně jako v BiomeRegistry).
Souřadnice uzlů jsou opsané z Kotlinu – ZoneOneTest je ověřuje proti BiomeRegistry / CaveMap.
"""
import json, math, os, sys
from PIL import Image, ImageFilter, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..")
RES = os.path.join(ROOT, "app", "src", "main", "res")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
OUT = os.path.join(ASSETS, "zone")
PHONE = (1280, 2856)

W, H = 940, 1260                   # plátno mapy (jednotky)
UNIT_PX = 2.0                       # 1 px výřezu = 2 jednotky plátna
INK = (74, 53, 32, 255)

# lokace: obrázek, venkovní (centerCrop na telefon), levý horní roh a výška na plátně
LOC = {
    "SKY_PASS":     dict(img="sky_pass",     outdoor=False, x=541, y=40,   h=290),
    "HIDDEN_GROVE": dict(img="hidden_grove", outdoor=False, x=0,   y=170,  h=190),
    "FOREST":       dict(img="forest",       outdoor=False, x=112, y=40,   h=350),
    "MEADOW":       dict(img="meadow",       outdoor=True,  x=42,  y=430,  h=470),
    "MOUNTAINS":    dict(img="mountains",    outdoor=True,  x=520, y=360,  h=500),
    "CAVE_OPEN":    dict(img="cave_open",    outdoor=False, x=800, y=440,  h=270),
    "TOWN":         dict(img="poketown",     outdoor=True,  x=60,  y=970,  h=260),
    "MINES":        dict(img="mines",        outdoor=False, x=398, y=40,   h=300),
    "CAVE_MAZE":    dict(img="cave_maze",    outdoor=False, x=402, y=400,  h=260),
}

# uzly: venkovní = podíl obrazovky (BiomeRegistry), jeskyně = art px (CaveMap)
NODES = {
    ("TOWN", "les"): (0.455, 0.150), ("TOWN", "gudwin"): (0.120, 0.520),
    ("MEADOW", "vstup_z_town"): (0.340, 0.640), ("MEADOW", "les_sever"): (0.470, 0.090),
    ("MEADOW", "hory"): (0.765, 0.432), ("MEADOW", "meadow_npc"): (0.630, 0.410),
    ("MOUNTAINS", "vstup_z_meadow"): (0.500, 0.960), ("MOUNTAINS", "mine"): (0.130, 0.470),
    ("MOUNTAINS", "cave"): (0.790, 0.345), ("MOUNTAINS", "peak"): (0.500, 0.150),
    ("MOUNTAINS", "kral_mlsak"): (0.500, 0.625),
    ("FOREST", "vstup_z_louky"): (150, 588), ("FOREST", "skryta_stezka"): (96, 312), ("FOREST", "mytina"): (150, 54),
    ("HIDDEN_GROVE", "vstup_z_hvozdu"): (80, 290), ("HIDDEN_GROVE", "oltar"): (80, 100),
    ("CAVE_MAZE", "vychod_dolu"): (26, 466), ("CAVE_MAZE", "vstup_doly"): (26, 269),
    ("CAVE_OPEN", "vychod_jeskyne"): (75, 428),
    ("MINES", "zpet_do_stoly"): (75, 530), ("MINES", "vendelin"): (54, 458),
    ("SKY_PASS", "vstup_ze_svatyne"): (80, 424),
}

# spoje: (lokace, uzel, směr ven) ↔ (lokace, uzel, směr ven); směr určuje tvar křivky
LINKS = [
    (("TOWN", "les", "n"), ("MEADOW", "vstup_z_town", "s"), False),
    (("MEADOW", "les_sever", "n"), ("FOREST", "vstup_z_louky", "s"), False),
    (("MEADOW", "hory", "e"), ("MOUNTAINS", "vstup_z_meadow", "s"), False),
    (("MOUNTAINS", "peak", "n"), ("SKY_PASS", "vstup_ze_svatyne", "s"), False),
    (("MOUNTAINS", "cave", "e"), ("CAVE_OPEN", "vychod_jeskyne", "s"), False),
    (("MOUNTAINS", "mine", "w"), ("CAVE_MAZE", "vychod_dolu", "s"), False),
    (("CAVE_MAZE", "vstup_doly", "w"), ("MINES", "zpet_do_stoly", "s"), False),
    (("FOREST", "skryta_stezka", "w"), ("HIDDEN_GROVE", "vstup_z_hvozdu", "s"), True),     # tajný
]

# jméno lokace: střed a účaří textu (jednotky plátna) – mimo spoje a tvary
LABEL = {
    "TOWN": (163, 962), "MEADOW": (300, 452), "FOREST": (270, 34), "HIDDEN_GROVE": (96, 400),
    "MOUNTAINS": (700, 352), "CAVE_MAZE": (460, 702), "MINES": (453, 32), "CAVE_OPEN": (832, 432),
    "SKY_PASS": (822, 110),
}
LABEL_SIZE = 30

NPC = {"TOWN": "gudwin", "MEADOW": "meadow_npc", "MOUNTAINS": "kral_mlsak", "FOREST": "mytina",
       "MINES": "vendelin", "HIDDEN_GROVE": "oltar"}
# hlava NPC: obrázek a čtvercový výřez hlavy (px zdroje)
HEADS = {
    "TOWN": ("gudwin_oliver", (40, 0, 260, 220)),
    "MEADOW": ("npc_bush", (40, 40, 460, 460)),
    "MOUNTAINS": ("kral_mlsak", (0, 60, 640, 700)),
    "FOREST": ("makromon_23_mydrus", (100, 10, 460, 370)),
    "MINES": ("npc_vendelin", (100, 0, 430, 330)),
    "HIDDEN_GROVE": ("npc_elderan", (100, 20, 420, 340)),
}


def find_res(name):
    for d in sorted(os.listdir(RES)):
        if not d.startswith("drawable"): continue
        for ext in (".png", ".webp", ".gif", ".jpg"):
            p = os.path.join(RES, d, name + ext)
            if os.path.exists(p): return p
    raise FileNotFoundError(name)


def walk_grid(biome):
    lines = [l for l in open(os.path.join(ASSETS, "walk", biome.lower() + ".txt"), encoding="utf-8").read().splitlines()
             if l and not l.startswith("# ")]
    iw, ih, cell = map(int, lines[0].split())
    rows = lines[1:]
    return iw, ih, cell, [[ch != "#" for ch in r] for r in rows]


def visible_box(iw, ih, outdoor):
    """Viditelná část obrázku v px (centerCrop na telefon u venkovních map)."""
    if not outdoor: return (0.0, 0.0, float(iw), float(ih))
    vw, vh = PHONE
    s = max(vw / iw, vh / ih)
    dx = (vw - iw * s) / 2; dy = (vh - ih * s) / 2
    return (-dx / s, -dy / s, (vw - dx) / s, (vh - dy) / s)


def node_img(biome, node, iw, ih, outdoor):
    """Uzel → px obrázku."""
    x, y = NODES[(biome, node)]
    if not outdoor: return float(x), float(y)
    vx0, vy0, vx1, vy1 = visible_box(iw, ih, True)
    return vx0 + x * (vx1 - vx0), vy0 + y * (vy1 - vy0)


def build(biome, spec):
    src = Image.open(find_res(spec["img"])).convert("RGBA")
    iw, ih, cell, grid = walk_grid(biome)
    assert src.size == (iw, ih), (biome, src.size, iw, ih)
    vx0, vy0, vx1, vy1 = visible_box(iw, ih, spec["outdoor"])
    rows, cols = len(grid), len(grid[0])
    # rozšíření chozeného území o okolí (tvar lokace), v buňkách
    r = max(1, round(0.07 * (vx1 - vx0) / cell))
    dil = [[False] * cols for _ in range(rows)]
    for y in range(rows):
        for x in range(cols):
            if not grid[y][x]: continue
            for yy in range(max(0, y - r), min(rows, y + r + 1)):
                for xx in range(max(0, x - r), min(cols, x + r + 1)):
                    if (xx - x) ** 2 + (yy - y) ** 2 <= r * r: dil[yy][xx] = True
    mask = Image.new("L", (cols, rows), 0)
    mask.putdata([255 if v else 0 for row in dil for v in row])
    mask = mask.resize((cols * cell, rows * cell), Image.NEAREST).crop((0, 0, iw, ih))
    # jen viditelná část
    vis = Image.new("L", (iw, ih), 0)
    ImageDraw.Draw(vis).rectangle([max(0, vx0), max(0, vy0), min(iw, vx1) - 1, min(ih, vy1) - 1], fill=255)
    mask = Image.composite(mask, Image.new("L", (iw, ih), 0), vis)
    bx0, by0, bx1, by1 = mask.getbbox()
    crop = (bx0, by0, bx1, by1)
    th = max(8, round(spec["h"] / UNIT_PX))
    tw = max(8, round(th * (bx1 - bx0) / (by1 - by0)))
    img = src.crop(crop).resize((tw, th), Image.LANCZOS)
    m = mask.crop(crop).resize((tw, th), Image.BOX).point(lambda v: 255 if v >= 128 else 0)
    m = m.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.MinFilter(3))     # zahladit zoubky
    # lehká sépie – mapa v deníku, ne fotka lokace
    px = img.load()
    for yy in range(th):
        for xx in range(tw):
            rr, gg, bb, aa = px[xx, yy]
            l = (rr * 30 + gg * 59 + bb * 11) // 100
            sr, sg, sb = min(255, l + 38), min(255, l + 22), max(0, l - 6)
            k = 0.28
            px[xx, yy] = (round(rr * (1 - k) + sr * k), round(gg * (1 - k) + sg * k), round(bb * (1 - k) + sb * k), 255)
    pad = 2
    out = Image.new("RGBA", (tw + 2 * pad, th + 2 * pad), (0, 0, 0, 0))
    mm = Image.new("L", out.size, 0); mm.paste(m, (pad, pad))
    ring = mm.filter(ImageFilter.MaxFilter(3))
    out.paste(Image.new("RGBA", out.size, INK), (0, 0), ring)
    body = Image.new("RGBA", out.size, (0, 0, 0, 0)); body.paste(img, (pad, pad))
    out.paste(body, (0, 0), mm)
    os.makedirs(OUT, exist_ok=True)
    out.save(os.path.join(OUT, biome + ".png"), optimize=True)

    # převod px obrázku → jednotky plátna
    sx = tw / (bx1 - bx0) * UNIT_PX; sy = th / (by1 - by0) * UNIT_PX
    ox = spec["x"] + pad * UNIT_PX - bx0 * sx; oy = spec["y"] + pad * UNIT_PX - by0 * sy
    # pozice ve hře (podíl světa / obrazovky) → plátno: v = a + f * b
    fw, fh = (vx1 - vx0), (vy1 - vy0)
    tr = [ox + vx0 * sx, fw * sx, oy + vy0 * sy, fh * sy]
    rect = [spec["x"], spec["y"], out.width * UNIT_PX, out.height * UNIT_PX]
    return dict(rect=rect, map=[round(v, 2) for v in tr], img=(iw, ih), outdoor=spec["outdoor"]), (ox, oy, sx, sy)


def to_canvas(t, biome, node, info):
    iw, ih = info["img"]
    x, y = node_img(biome, node, iw, ih, info["outdoor"])
    ox, oy, sx, sy = t
    return [round(ox + x * sx, 1), round(oy + y * sy, 1)]


def frac(biome, node, info):
    x, y = NODES[(biome, node)]
    if info["outdoor"]: return [x, y]
    iw, ih = info["img"]
    return [round(x / iw, 4), round(y / ih, 4)]


def head(biome):
    name, box = HEADS[biome]
    im = Image.open(find_res(name))
    im.seek(0)
    im = im.convert("RGBA").crop(box).resize((64, 64), Image.LANCZOS)
    im.save(os.path.join(OUT, "head_" + biome + ".png"), optimize=True)


def main(preview=None):
    locs, tf = {}, {}
    for b, spec in LOC.items():
        info, t = build(b, spec)
        locs[b] = info; tf[b] = t
    links = []
    for (a, an, ad), (c, cn, cd), secret in LINKS:
        links.append(dict(a=a, aNode=an, aDir=ad, aPos=to_canvas(tf[a], a, an, locs[a]), aFrac=frac(a, an, locs[a]),
                          b=c, bNode=cn, bDir=cd, bPos=to_canvas(tf[c], c, cn, locs[c]), bFrac=frac(c, cn, locs[c]),
                          secret=secret))
    npcs = {b: dict(node=n, pos=to_canvas(tf[b], b, n, locs[b]), frac=frac(b, n, locs[b])) for b, n in NPC.items()}
    for b in HEADS: head(b)
    data = dict(w=W, h=H, labelSize=LABEL_SIZE,
                locations={b: dict(rect=[round(v, 1) for v in i["rect"]], map=i["map"], label=list(LABEL[b])) for b, i in locs.items()},
                links=links, npcs=npcs)
    json.dump(data, open(os.path.join(OUT, "zone1.json"), "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    print("zóna 1:", ", ".join(f"{b} {int(i['rect'][2])}×{int(i['rect'][3])}" for b, i in locs.items()))
    if preview: render_preview(data, preview)


def render_preview(d, path):
    """Náhled rozvržení (jen kontrola – aplikace kreslí sama)."""
    s = 0.6
    im = Image.new("RGBA", (int(d["w"] * s), int(d["h"] * s)), (236, 220, 184, 255))
    dr = ImageDraw.Draw(im)
    def bez(l):
        p0 = l["aPos"]; p3 = l["bPos"]
        k = max(60, 0.45 * math.dist(p0, p3))
        dv = dict(n=(0, -1), s=(0, 1), e=(1, 0), w=(-1, 0))
        p1 = (p0[0] + dv[l["aDir"]][0] * k, p0[1] + dv[l["aDir"]][1] * k)
        p2 = (p3[0] + dv[l["bDir"]][0] * k, p3[1] + dv[l["bDir"]][1] * k)
        pts = []
        for i in range(41):
            t = i / 40
            x = (1-t)**3*p0[0] + 3*(1-t)**2*t*p1[0] + 3*(1-t)*t*t*p2[0] + t**3*p3[0]
            y = (1-t)**3*p0[1] + 3*(1-t)**2*t*p1[1] + 3*(1-t)*t*t*p2[1] + t**3*p3[1]
            pts.append((x * s, y * s))
        return pts
    for l in d["links"]:
        pts = bez(l)
        for i in range(0, len(pts) - 1, 2): dr.line([pts[i], pts[i + 1]], fill=(120, 40, 30, 255), width=3)
    for b, loc in d["locations"].items():
        t = Image.open(os.path.join(OUT, b + ".png"))
        x, y, w, h = loc["rect"]
        t = t.resize((max(1, int(w * s)), max(1, int(h * s))), Image.NEAREST)
        im.alpha_composite(t, (int(x * s), int(y * s)))
    for l in d["links"]:
        for p in (l["aPos"], l["bPos"]):
            dr.ellipse([p[0] * s - 5, p[1] * s - 5, p[0] * s + 5, p[1] * s + 5], fill=(250, 240, 200, 255), outline=(120, 40, 30, 255), width=2)
    for b, n in d["npcs"].items():
        hx, hy = n["pos"]
        hd = Image.open(os.path.join(OUT, "head_" + b + ".png")).resize((34, 34))
        dr.ellipse([hx * s - 19, hy * s - 19, hx * s + 19, hy * s + 19], fill=(250, 240, 200, 255), outline=INK, width=2)
        im.alpha_composite(hd, (int(hx * s - 17), int(hy * s - 17)))
    from PIL import ImageFont
    font = ImageFont.truetype(os.path.join(ROOT, "app", "src", "main", "res", "font", "jersey_15.ttf"), int(d["labelSize"] * s))
    names = {"TOWN": "Město", "MEADOW": "Louka", "FOREST": "Hvozd", "HIDDEN_GROVE": "Zapomenutý háj", "MOUNTAINS": "Hory",
             "CAVE_MAZE": "Starý důl", "MINES": "Doly", "CAVE_OPEN": "Mechová jeskyně", "SKY_PASS": "Nebeský průsmyk"}
    for b, loc in d["locations"].items():
        x, y = loc["label"]
        dr.text((x * s, y * s), names[b], font=font, fill=INK, anchor="ms", stroke_width=2, stroke_fill=(236, 220, 184, 255))
    im.save(path)


if __name__ == "__main__":
    a = sys.argv[1:]
    main(a[a.index("--preview") + 1] if "--preview" in a else None)

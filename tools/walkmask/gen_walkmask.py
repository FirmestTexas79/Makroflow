"""
Mapy chůze (docs/adr/0033): kam na mapě smí postava šlápnout.

Pro každou lokaci se z obrázku mapy odhadne podle barev, co je tráva / cesta / podlaha
(průchozí) a co domy, stromy, voda, skály (neprůchozí). Výsledek se sjednotí s „koridory“
kolem hran původního grafu bodů (spojení, která už fungovala, zůstanou průchozí – mosty,
schody, vchody) a doladí ručními obdélníky. Výstup: app/src/main/assets/walk/<mapa>.txt
('.' = průchozí, '#' = zeď) a náhled tools/walkmask/out/<mapa>_overlay.png.

Souřadnice uzlů celoobrazovkových map jsou ve zlomcích OBRAZOVKY (centerCrop) naladěné na
telefonu 1280 × 2856 – převádí se na pixely obrázku stejně jako v aplikaci (MapGeometry).
"""
import math, os, sys
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
RES = os.path.join(ROOT, "app", "src", "main", "res")
OUT_ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "walk")
OUT_PREVIEW = os.path.join(os.path.dirname(__file__), "out")
PHONE = (1280, 2856)


def crop_to_image(rx, ry, iw, ih, vw=PHONE[0], vh=PHONE[1]):
    s = max(vw / iw, vh / ih)
    dx = (vw - iw * s) / 2; dy = (vh - ih * s) / 2
    return ((rx * vw - dx) / s, (ry * vh - dy) / s)


# ── Klasifikace pixelů ─────────────────────────────────────────────────────

def lush(c):          # louka a město: světlá tráva a pískové cesty
    r, g, b = c
    if b > r + 50 and b > 140: return False                 # voda
    grass = g >= 185 and r >= 125 and g > b + 40 and r < g
    path = r >= 185 and g >= 160 and b >= 110 and r - b >= 35 and r >= g
    return grass or path

def mountain(c):      # písečné dno údolí (sytý písek) a světlé stezky; vršky skal jsou bledší
    r, g, b = c
    sand = r >= 195 and r - b >= 95 and g >= 150
    trail = r >= 230 and g >= 205
    return sand or trail

def cave(c):          # modrošedá podlaha vs tmavá skála
    r, g, b = c
    lum = 0.3 * r + 0.59 * g + 0.11 * b
    if b > r + 55 and b > 150 and g > 120: return False      # jezírko
    return lum >= 25 and b >= 40

def forest(c):        # světle zelená mýtina
    r, g, b = c
    if b > r + 50: return False
    return g >= 175 and r >= 120 and g > b + 60 and r < g


# ── Grafy (kopie z BiomeRegistry / CaveMaps / ForestMap) ───────────────────

def graph_from_kotlin(path, name):
    """Načte uzly a hrany z Kotlin zdrojáku (Waypoint("id", PointF(x, y), listOf(...)))."""
    import re
    src = open(path, encoding="utf-8").read()
    start = src.index(f"val {name} = listOf(")
    end = src.index("\n    )", start)
    block = src[start:end]
    nodes, edges = {}, []
    for m in re.finditer(r'Waypoint\("([^"]+)",\s*PointF\(([\d.]+)f,\s*([\d.]+)f\),\s*listOf\(([^)]*)\)\)', block):
        nid, x, y, nb = m.group(1), float(m.group(2)), float(m.group(3)), m.group(4)
        nodes[nid] = (x, y)
        for n in re.findall(r'"([^"]+)"', nb): edges.append((nid, n))
    return nodes, edges

def cave_graph(path, var):
    import re
    src = open(path, encoding="utf-8").read()
    start = src.index(f"val {var}")
    body = src[start:start + 6000]
    consts = dict((k, int(v)) for k, v in re.findall(r'\b(?:private )?(?:const )?val ([A-Z]\w*)\s*=\s*(\d+)', src))
    nodes = {}
    for m in re.finditer(r'CaveNode\("([^"]+)",\s*(\w+),\s*(\w+)\)', body):
        def val(t): return int(t) if t.isdigit() else consts.get(t, 0)
        nodes[m.group(1)] = (val(m.group(2)), val(m.group(3)))
    edges = re.findall(r'"([^"]+)"\s+to\s+"([^"]+)"', body)
    return nodes, [e for e in edges if e[0] in nodes and e[1] in nodes]


# ── Mřížka ─────────────────────────────────────────────────────────────────

def build(name, img_path, classify, cell, nodes_img, edges, corridor_r, extra_walk=(), extra_block=(), thresh=0.55):
    im = Image.open(img_path).convert("RGB")
    W, H = im.size
    gw, gh = math.ceil(W / cell), math.ceil(H / cell)
    px = im.load()
    grid = [[False] * gw for _ in range(gh)]
    for gy in range(gh):
        for gx in range(gw):
            ok = tot = 0
            for y in range(gy * cell, min(H, (gy + 1) * cell)):
                for x in range(gx * cell, min(W, (gx + 1) * cell)):
                    tot += 1; ok += classify(px[x, y])
            grid[gy][gx] = tot > 0 and ok / tot >= thresh

    def seg_d(p, a, b):
        ax, ay = a; bx, by = b; x, y = p
        dx, dy = bx - ax, by - ay
        L = dx * dx + dy * dy
        t = 0 if L == 0 else max(0, min(1, ((x - ax) * dx + (y - ay) * dy) / L))
        return math.hypot(x - (ax + t * dx), y - (ay + t * dy))

    # koridory kolem hran grafu
    for a, b in edges:
        if a not in nodes_img or b not in nodes_img: continue
        pa, pb = nodes_img[a], nodes_img[b]
        for gy in range(gh):
            for gx in range(gw):
                c = ((gx + 0.5) * cell, (gy + 0.5) * cell)
                if seg_d(c, pa, pb) <= corridor_r: grid[gy][gx] = True
    for (x0, y0, x1, y1) in extra_walk:
        for gy in range(int(y0 / cell), int(math.ceil(y1 / cell))):
            for gx in range(int(x0 / cell), int(math.ceil(x1 / cell))):
                if 0 <= gx < gw and 0 <= gy < gh: grid[gy][gx] = True
    # uzly samotné vždy průchozí
    for (x, y) in nodes_img.values():
        gx, gy = int(x / cell), int(y / cell)
        if 0 <= gx < gw and 0 <= gy < gh: grid[gy][gx] = True
    # ruční zdi až po uzlech – uzel může stát na neprůchozím místě (záhon, stůl)
    for (x0, y0, x1, y1) in extra_block:
        for gy in range(int(y0 / cell), int(math.ceil(y1 / cell))):
            for gx in range(int(x0 / cell), int(math.ceil(x1 / cell))):
                if 0 <= gx < gw and 0 <= gy < gh: grid[gy][gx] = False

    # jen oblast spojená s uzly (ostrůvky trávy za stromy by lákaly k nedosažitelným klepnutím)
    from collections import deque
    seen = [[False] * gw for _ in range(gh)]
    q = deque()
    for (x, y) in nodes_img.values():
        gx, gy = min(gw - 1, max(0, int(x / cell))), min(gh - 1, max(0, int(y / cell)))
        if grid[gy][gx] and not seen[gy][gx]: seen[gy][gx] = True; q.append((gx, gy))
    while q:
        x, y = q.popleft()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < gw and 0 <= ny < gh and grid[ny][nx] and not seen[ny][nx]:
                seen[ny][nx] = True; q.append((nx, ny))
    grid = seen

    os.makedirs(OUT_ASSETS, exist_ok=True); os.makedirs(OUT_PREVIEW, exist_ok=True)
    with open(os.path.join(OUT_ASSETS, f"{name}.txt"), "w") as f:
        f.write(f"# mapa chůze {name}: obrázek {W}x{H}, buňka {cell} px (tools/walkmask/gen_walkmask.py)\n")
        f.write(f"{W} {H} {cell}\n")
        for row in grid: f.write("".join("." if v else "#" for v in row) + "\n")

    ov = im.copy()
    d = ImageDraw.Draw(ov, "RGBA")
    for gy in range(gh):
        for gx in range(gw):
            if not grid[gy][gx]:
                d.rectangle([gx * cell, gy * cell, (gx + 1) * cell - 1, (gy + 1) * cell - 1], fill=(200, 0, 0, 90))
    for nid, (x, y) in nodes_img.items():
        d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=(255, 255, 0, 255))
    ov.save(os.path.join(OUT_PREVIEW, f"{name}_overlay.png"))
    walk = sum(v for row in grid for v in row)
    print(f"{name}: {gw}x{gh} buněk, průchozích {walk} ({100 * walk // (gw * gh)} %)")


def main():
    reg = os.path.join(ROOT, "app/src/main/java/cz/uhk/macroflow/pokemon/BiomeRegistry.kt")
    caves = os.path.join(ROOT, "app/src/main/java/cz/uhk/macroflow/pokemon/cave/CaveMaps.kt")
    forest = os.path.join(ROOT, "app/src/main/java/cz/uhk/macroflow/pokemon/cave/ForestMap.kt")

    def screen_graph(var, iw, ih):
        n, e = graph_from_kotlin(reg, var)
        return {k: crop_to_image(x, y, iw, ih) for k, (x, y) in n.items()}, e

    n, e = screen_graph("TOWN_GRAPH", 384, 624)
    build("town", os.path.join(RES, "drawable/poketown.webp"), lush, 6, n, e, corridor_r=7, **TOWN_FIX)
    n, e = screen_graph("MEADOW_GRAPH", 688, 1536)
    build("meadow", os.path.join(RES, "drawable/meadow.png"), lush, 12, n, e, corridor_r=16, **MEADOW_FIX)
    n, e = screen_graph("MOUNTAINS_GRAPH", 688, 1536)
    build("mountains", os.path.join(RES, "drawable/mountains.png"), mountain, 12, n, e, corridor_r=16, **MOUNTAIN_FIX)
    for var, img, fname in (("OPEN", "cave_open", "cave_open"), ("MAZE", "cave_maze", "cave_maze")):
        n, e = cave_graph(caves, var)
        build(fname, os.path.join(RES, f"drawable-nodpi/{img}.png"), cave, 3, {k: (x, y) for k, (x, y) in n.items()}, e, corridor_r=4)
    n, e = cave_graph(forest, "MAP")
    build("forest", os.path.join(RES, "drawable-nodpi/forest.png"), forest_c, 3, {k: (x, y) for k, (x, y) in n.items()}, e, corridor_r=4)


forest_c = forest
# Ruční doladění (obdélníky v pixelech obrázku): extra_walk / extra_block
TOWN_FIX = dict(extra_walk=[], extra_block=[])
# Louka (docs/adr/0034): záhony – 4 v rozích, cesta plus mezi nimi – a pracovní stůl; sedí s MeadowLayout.kt
MEADOW_FIX = dict(extra_walk=[(345, 824, 520, 846), (426, 745, 448, 925)],
                  extra_block=[(360, 769, 426, 824), (448, 769, 514, 824), (360, 846, 426, 901), (448, 846, 514, 901),
                               (93, 790, 161, 830)])
MOUNTAIN_FIX = dict(extra_walk=[], extra_block=[(304, 700, 384, 812)])   # socha krále na podstavci

if __name__ == "__main__":
    main()

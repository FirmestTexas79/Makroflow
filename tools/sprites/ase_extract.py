"""
Rozbalí .aseprite soubor na jednotlivé animace (docs/adr/0051).

Použití:  python tools/sprites/ase_extract.py <soubor.aseprite> <výstupní složka> [--hair "short hair"]
          (pozadí a vodítka – vrstvy „bg“, „guide“ – se vynechají; --hair zapne jen zvolený účes)
Výstup:   <složka>/<tag>.png   – vodorovný pás snímků animace (šířka = počet snímků × šířka plátna)
          <složka>/<tag>.gif   – náhled s časováním snímků z Aseprite
          <složka>/index.json  – tagy, rozsahy snímků, délky snímků (ms), rozměr plátna, vrstvy
          <složka>/contact.png – přehled všech animací pod sebou

Čte formát Aseprite (https://github.com/aseprite/aseprite/blob/main/docs/ase-file-specs.md):
vrstvy (viditelnost, krytí, skupiny), cely typu raw / linked / zlib, tagy a délky snímků.
Skládá viditelné vrstvy zdola nahoru (normální prolnutí). Barevná hloubka RGBA i indexovaná.
"""
import json, os, struct, sys, zlib
from PIL import Image


def read_string(b, o):
    n = struct.unpack_from("<H", b, o)[0]
    return b[o + 2:o + 2 + n].decode("utf-8", "replace"), o + 2 + n


def parse(path):
    d = open(path, "rb").read()
    fsize, magic, nframes, W, H, depth, flags, speed = struct.unpack_from("<IHHHHHIH", d, 0)
    assert magic == 0xA5E0, "není to .aseprite"
    transparent_index = d[28]
    o = 128
    layers, frames, tags, palette = [], [], [], {}
    for fi in range(nframes):
        fstart = o
        flen, fmagic, oldchunks, dur = struct.unpack_from("<IHHH", d, o)
        nchunks = struct.unpack_from("<I", d, o + 12)[0] or oldchunks
        o += 16
        cels = []
        for _ in range(nchunks):
            clen, ctype = struct.unpack_from("<IH", d, o)
            c = d[o + 6:o + clen]
            if ctype == 0x2004:                                   # vrstva
                lflags, ltype, child, _, _, blend, opacity = struct.unpack_from("<HHHHHHB", c, 0)
                name, _ = read_string(c, 16)
                layers.append(dict(name=name, visible=bool(lflags & 1), type=ltype, level=child,
                                   blend=blend, opacity=opacity))
            elif ctype == 0x2005:                                 # cel
                li, x, y, op, ctyp = struct.unpack_from("<HhhBH", c, 0)
                body = c[16:]
                cel = dict(layer=li, x=x, y=y, opacity=op, kind=ctyp)
                if ctyp == 1:
                    cel["link"] = struct.unpack_from("<H", body, 0)[0]
                elif ctyp in (0, 2):
                    w, h = struct.unpack_from("<HH", body, 0)
                    raw = body[4:] if ctyp == 0 else zlib.decompress(body[4:])
                    cel.update(w=w, h=h, raw=raw)
                cels.append(cel)
            elif ctype == 0x2018:                                 # tagy
                n = struct.unpack_from("<H", c, 0)[0]
                p = 10
                for _ in range(n):
                    a, b, direction, rep = struct.unpack_from("<HHBH", c, p)
                    p += 17
                    name, p = read_string(c, p)
                    tags.append(dict(name=name, start=a, end=b, direction=direction))
            elif ctype == 0x2019:                                 # paleta
                psize, first, last = struct.unpack_from("<III", c, 0)
                p = 20
                for i in range(first, last + 1):
                    eflags, r, g, bb, a = struct.unpack_from("<HBBBB", c, p)
                    p += 6
                    if eflags & 1:
                        _, p = read_string(c, p)
                    palette[i] = (r, g, bb, a)
            o += clen
        frames.append(dict(duration=dur, cels=cels))
        o = fstart + flen
    return dict(W=W, H=H, depth=depth, layers=layers, frames=frames, tags=tags,
                palette=palette, transparent=transparent_index)


def cel_image(ase, cel, fi):
    if cel["kind"] == 1:                                          # odkaz na cel jiného snímku
        src = next(c for c in ase["frames"][cel["link"]]["cels"] if c["layer"] == cel["layer"])
        return cel_image(ase, src, cel["link"])
    w, h, raw = cel["w"], cel["h"], cel["raw"]
    if ase["depth"] == 32:
        return Image.frombytes("RGBA", (w, h), raw)
    if ase["depth"] == 16:
        g = Image.frombytes("LA", (w, h), raw)
        return g.convert("RGBA")
    pal = ase["palette"]
    im = Image.new("RGBA", (w, h))
    im.putdata([(0, 0, 0, 0) if i == ase["transparent"] else pal.get(i, (0, 0, 0, 0)) for i in raw])
    return im


SKIP = {"bg", "guide"}


def layer_visible(ase, li):
    """Vrstva je vidět jen když je vidět ona i všechny nadřazené skupiny (pozadí a vodítka nikdy)."""
    layers = ase["layers"]
    name = layers[li]["name"].strip().lower()
    if name in SKIP:
        return False
    hair = ase.get("hair")
    if hair and name.endswith(" hair"):
        return name == hair
    if not layers[li]["visible"]:
        return False
    level = layers[li]["level"]
    for j in range(li - 1, -1, -1):
        if layers[j]["level"] < level:
            if not layers[j]["visible"]:
                return False
            level = layers[j]["level"]
    return True


def render_frame(ase, fi):
    out = Image.new("RGBA", (ase["W"], ase["H"]), (0, 0, 0, 0))
    cels = sorted(ase["frames"][fi]["cels"], key=lambda c: c["layer"])
    for cel in cels:
        L = ase["layers"][cel["layer"]]
        if L["type"] != 0 or not layer_visible(ase, cel["layer"]):
            continue
        img = cel_image(ase, cel, fi)
        a = cel["opacity"] * L["opacity"] / 255.0
        if a < 1:
            r, g, b, al = img.split()
            img = Image.merge("RGBA", (r, g, b, al.point(lambda v: int(v * a))))
        out.alpha_composite(img, (cel["x"], cel["y"])) if cel["x"] >= 0 and cel["y"] >= 0 else out.paste(img, (cel["x"], cel["y"]), img)
    return out


def main(src, dst, hair=None):
    ase = parse(src)
    ase["hair"] = hair.lower() if hair else None
    os.makedirs(dst, exist_ok=True)
    frames = [render_frame(ase, i) for i in range(len(ase["frames"]))]
    W, H = ase["W"], ase["H"]
    index = dict(canvas=[W, H], frames=len(frames), layers=[l["name"] for l in ase["layers"]], animations=[])
    tags = ase["tags"] or [dict(name="all", start=0, end=len(frames) - 1, direction=0)]
    strips = []
    used = {}
    for t in tags:
        idx = list(range(t["start"], t["end"] + 1))
        strip = Image.new("RGBA", (W * len(idx), H))
        for k, i in enumerate(idx):
            strip.paste(frames[i], (k * W, 0))
        safe = "".join(ch if ch.isalnum() or ch in "-_" else "_" for ch in t["name"].strip().lower())
        used[safe] = used.get(safe, 0) + 1
        if used[safe] > 1: safe = f"{safe}_{used[safe]}"      # dva tagy „axe“ (boční a nahoru)
        strip.save(os.path.join(dst, safe + ".png"))
        durs = [ase["frames"][i]["duration"] for i in idx]
        frames[idx[0]].resize((W * 3, H * 3), Image.NEAREST).save(
            os.path.join(dst, safe + ".gif"), save_all=True,
            append_images=[frames[i].resize((W * 3, H * 3), Image.NEAREST) for i in idx[1:]],
            duration=durs, loop=0, disposal=2, transparency=0)
        index["animations"].append(dict(name=t["name"], file=safe + ".png", start=t["start"], end=t["end"],
                                        frames=len(idx), durations_ms=durs))
        strips.append((t["name"], strip))
    json.dump(index, open(os.path.join(dst, "index.json"), "w"), ensure_ascii=False, indent=1)
    # přehled
    maxw = max(s.width for _, s in strips)
    sheet = Image.new("RGBA", (maxw, H * len(strips)), (40, 44, 60, 255))
    for k, (_, s) in enumerate(strips):
        sheet.alpha_composite(s, (0, k * H))
    sheet.save(os.path.join(dst, "contact.png"))
    print(f"{len(frames)} snímků {W}x{H}, {len(strips)} animací → {dst}")
    for a in index["animations"]:
        print(f"  {a['name']:>12}: {a['frames']:>3} snímků, {sum(a['durations_ms'])} ms")


if __name__ == "__main__":
    args = sys.argv[1:]
    hair = None
    if "--hair" in args:
        k = args.index("--hair"); hair = args[k + 1]; del args[k:k + 2]
    main(args[0], args[1], hair)

"""
Malý „střihový stůl“ pro cutscény konců (docs/adr/0111): záběry jsou funkce času → pixelový obraz 360 × 780 (1 px =
1 px mapy), obraz se zvětší ×2 bez vyhlazení na 720 × 1560 (telefon na výšku) a přes něj se vysází titulky
s psacím strojem (DejaVu Serif – česká diakritika). Výstup přes ffmpeg do MP4 (H.264, -preset slow, CRF 32 – videa jsou v APK, docs/adr/0117), náhled GIF.
"""
import math, os, subprocess
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
W, H = 360, 780
SCALE = 2
FPS = 20
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf"
FONT_I = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Italic.ttf"


def load(path, rgba=True):
    im = Image.open(path)
    return np.array(im.convert("RGBA" if rgba else "RGB")).astype(np.float32)


def res(name):
    for d in ("drawable-nodpi", "drawable"):
        for ext in (".png", ".webp", ".gif"):
            p = os.path.join(RES, d, name + ext)
            if os.path.exists(p):
                return load(p)
    raise FileNotFoundError(name)


def canvas(color=(0, 0, 0)):
    c = np.zeros((H, W, 3), np.float32)
    c[:] = color
    return c


def blit(dst, spr, x, y, alpha=1.0):
    """RGBA sprite do RGB plátna na celé pixely (x, y = levý horní roh), s průhledností."""
    x, y = int(round(x)), int(round(y))
    h, w = spr.shape[:2]
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(dst.shape[1], x + w), min(dst.shape[0], y + h)
    if x1 <= x0 or y1 <= y0:
        return
    s = spr[y0 - y:y1 - y, x0 - x:x1 - x]
    a = (s[:, :, 3:4] / 255.0) * alpha if s.shape[2] == 4 else np.full(s.shape[:2] + (1,), alpha)
    dst[y0:y1, x0:x1, :3] = dst[y0:y1, x0:x1, :3] * (1 - a) + s[:, :, :3] * a


def view(img, cx, cy, zoom=1):
    """Výřez obrazu kolem (cx, cy) zvětšený celým násobkem – kamera na celé pixely, mimo obraz tma."""
    sw, sh = W / zoom, H / zoom
    x0, y0 = int(round(cx - sw / 2)), int(round(cy - sh / 2))
    out = np.zeros((int(math.ceil(sh)), int(math.ceil(sw)), img.shape[2]), np.float32)
    ix0, iy0 = max(0, x0), max(0, y0)
    ix1, iy1 = min(img.shape[1], x0 + out.shape[1]), min(img.shape[0], y0 + out.shape[0])
    if ix1 > ix0 and iy1 > iy0:
        out[iy0 - y0:iy1 - y0, ix0 - x0:ix1 - x0] = img[iy0:iy1, ix0:ix1]
    if zoom != 1:
        out = out.repeat(zoom, 0).repeat(zoom, 1)
    o = out[:H, :W]
    if o.shape[2] == 4:
        o = o[:, :, :3] * (o[:, :, 3:4] / 255.0)
    return o


def ease(t):
    t = min(1.0, max(0.0, t))
    return t * t * (3 - 2 * t)


def fade(img, k, color=(0, 0, 0)):
    """k = 0 nic, 1 celé do barvy; schodovitě (pixel art), ne plynule."""
    k = math.floor(min(1.0, max(0.0, k)) * 12) / 12
    return img * (1 - k) + np.array(color, np.float32) * k


def dots(img, xs, ys, col, alpha=1.0):
    xs = np.asarray(xs).astype(int); ys = np.asarray(ys).astype(int)
    ok = (xs >= 0) & (xs < img.shape[1]) & (ys >= 0) & (ys < img.shape[0])
    xs, ys = xs[ok], ys[ok]
    a = alpha[ok][:, None] if np.ndim(alpha) else alpha
    c = np.asarray(col, np.float32)
    if c.ndim == 2:
        c = c[ok]
    img[ys, xs, :3] = img[ys, xs, :3] * (1 - a) + c * a


def hero(name, frame=0):
    """Snímek postavy hráče z assets/hero (64 × 40, pata na y = 38)."""
    strip = load(os.path.join(ASSETS, "hero", name + ".png"))
    n = strip.shape[1] // 64
    return strip[:, (frame % n) * 64:(frame % n + 1) * 64]


def shrink(spr, h):
    """Velký obrázek (ilustrace Makromona) na pixelovou výšku h: zmenšení + ostré okraje průhlednosti."""
    im = Image.fromarray(spr.astype(np.uint8), "RGBA")
    bb = im.getbbox()
    im = im.crop(bb)
    w = max(1, round(im.width * h / im.height))
    im = im.resize((w, h), Image.LANCZOS)
    a = np.array(im).astype(np.float32)
    a[:, :, 3] = np.where(a[:, :, 3] > 110, 255, 0)
    return a


class Caption:
    """Titulek: od t0 do t1, psací stroj (cps znaků/s), jemné zatmavení pod textem, volitelně kurzíva (hlas z rádia)."""
    def __init__(self, t0, t1, text, cps=28, italic=False, pos=0.78, color=(244, 236, 216)):
        self.t0, self.t1, self.text, self.cps, self.italic, self.pos, self.color = t0, t1, text, cps, italic, pos, color


_fonts = {}


def _font(italic, size):
    k = (italic, size)
    if k not in _fonts:
        _fonts[k] = ImageFont.truetype(FONT_I if italic else FONT, size)
    return _fonts[k]


def _wrap(draw, text, font, width):
    lines = []
    for para in text.split("\n"):
        cur = ""
        for word in para.split(" "):
            t = (cur + " " + word).strip()
            if draw.textlength(t, font=font) <= width:
                cur = t
            else:
                lines.append(cur); cur = word
        lines.append(cur)
    return lines


def draw_captions(big, caps, t):
    """Titulky do zvětšeného snímku (PIL Image 720 × 1560)."""
    d = ImageDraw.Draw(big, "RGBA")
    for c in caps:
        if not (c.t0 <= t < c.t1):
            continue
        n = int((t - c.t0) * c.cps)
        out = min(1.0, (c.t1 - t) / 0.6)                                  # na konci se rozplyne
        font = _font(c.italic, 17 * SCALE)
        lines = _wrap(d, c.text, font, W * SCALE - 50 * SCALE)
        lh = 24 * SCALE
        total = len(lines) * lh
        y0 = int(H * SCALE * c.pos - total / 2)
        d.rectangle([0, y0 - 17 * SCALE, W * SCALE, y0 + total + 13 * SCALE], fill=(0, 0, 0, int(120 * out)))
        shown = 0
        for i, line in enumerate(lines):
            vis = line[:max(0, n - shown)]
            shown += len(line) + 1
            if not vis:
                continue
            x = (W * SCALE - d.textlength(line, font=font)) / 2
            a = int(255 * out)
            d.text((x + SCALE, y0 + i * lh + SCALE), vis, font=font, fill=(0, 0, 0, a))
            d.text((x, y0 + i * lh), vis, font=font, fill=c.color + (a,))


def render(shots, caps, out_mp4, gif=None, gif_every=3, audio=None, start=0.0, end=None):
    """shots = [(t0, t1, fn(t_local, t_global) → obraz 360 × 780)]; zapíše MP4 (a náhled GIF)."""
    total = end if end is not None else max(t1 for _, t1, _ in shots)
    n = int((total - start) * FPS)
    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W * SCALE}x{H * SCALE}",
           "-r", str(FPS), "-i", "-"]
    if audio:
        cmd += ["-i", audio, "-c:a", "aac", "-b:a", "128k", "-shortest"]
    cmd += ["-c:v", "libx264", "-preset", "slow", "-crf", "32", "-tune", "animation", "-pix_fmt", "yuv420p", "-movflags", "+faststart", out_mp4]
    ff = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    gif_frames = []
    for i in range(n):
        t = start + i / FPS
        img = canvas()
        for (t0, t1, fn) in shots:
            if t0 <= t < t1:
                img = fn(t - t0, t)
                break
        small = np.clip(img, 0, 255).astype(np.uint8)
        big = Image.fromarray(small.repeat(SCALE, 0).repeat(SCALE, 1))
        draw_captions(big, caps, t)
        ff.stdin.write(big.tobytes())
        if gif and i % gif_every == 0:
            gif_frames.append(big.resize((W, H), Image.NEAREST))
    ff.stdin.close(); ff.wait()
    if gif and gif_frames:
        gif_frames[0].save(gif, save_all=True, append_images=gif_frames[1:], duration=int(1000 * gif_every / FPS), loop=0)

"""
Použití:  python tools/audiogen/gen_audio.py      (numpy, scipy, ffmpeg s libvorbis)
Výstup:   app/src/main/res/raw/music_*.ogg, sfx_*.ogg

Vlastní hudba a zvuky Makrosvěta (docs/adr/0017) – celé syntetizované v kódu, žádné cizí
nahrávky ani samply (licenčně čisté). Každá skladba je napsaná jako noty/akordy a
vyrenderovaná malým syntezátorem:
  * loutna/kytara – Karplus-Strong (drnkaná struna jako IIR filtr, scipy.lfilter),
  * flétna/píšťala – sinus + vyšší harmonické + dech (filtrovaný šum) + vibrato,
  * pad – rozladěné pily přes dolní propust, pomalý náběh,
  * bicí – rámový buben, tom, chrastítko, kapky vody (sinus s rychlým sklouznutím),
  * dozvuk – konvoluce se syntetickou impulzní odezvou (exponenciálně doznívající šum).
Smyčky jsou bez švu: dozvuk přesahující konec se přičte zpět na začátek.
"""
import os, subprocess, tempfile
import numpy as np
from scipy.signal import lfilter, butter, sosfilt, fftconvolve
from scipy.io import wavfile

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "raw")
rng = np.random.default_rng(2026)

NOTE = {n: i for i, n in enumerate(["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"])}
NOTE.update({"Db": 1, "Eb": 3, "Gb": 6, "Ab": 8, "Bb": 10})


def hz(name):
    """'A4' → 440 Hz, 'F#3', 'Bb2' …"""
    n, o = name[:-1], int(name[-1])
    return 440.0 * 2 ** ((NOTE[n] + 12 * (o + 1) - 69) / 12)


def lp(x, f, order=2):
    return sosfilt(butter(order, f, "low", fs=SR, output="sos"), x)


def hp(x, f, order=2):
    return sosfilt(butter(order, f, "high", fs=SR, output="sos"), x)


def bp(x, lo, hi, order=2):
    return sosfilt(butter(order, [lo, hi], "band", fs=SR, output="sos"), x)


def env_adsr(n, a=0.01, d=0.1, s=0.7, r=0.2):
    t = np.arange(n) / SR
    dur = n / SR
    e = np.ones(n) * s
    e[t < a] = t[t < a] / a
    m = (t >= a) & (t < a + d)
    e[m] = 1 - (1 - s) * (t[m] - a) / d
    m = t > dur - r
    e[m] *= np.clip((dur - t[m]) / r, 0, 1)
    return e


# ── nástroje ────────────────────────────────────────────────────────────────────
def pluck(f, dur, bright=0.5, decay=0.996, body=True):
    """Drnkaná struna (Karplus-Strong). bright 0..1 = tvrdost trsátka."""
    n = int(dur * SR)
    N = max(2, int(round(SR / f)))
    exc = rng.uniform(-1, 1, N)
    exc = lp(exc, 800 + 7000 * bright, 1)                     # měkčí trsátko = méně výšek
    x = np.zeros(n); x[:N] = exc
    a = np.zeros(N + 2); a[0] = 1; a[N] = -0.5 * decay; a[N + 1] = -0.5 * decay
    y = lfilter([1.0], a, x)
    if body:  # rezonance těla nástroje
        y = y + 0.35 * bp(y, 180, 420) + 0.2 * bp(y, 900, 1600)
    y *= env_adsr(n, 0.002, 0.05, 1.0, min(0.25, dur * 0.3))
    return y / (np.max(np.abs(y)) + 1e-9)


def flute(f, dur, breath=0.12, vib=5.2, depth=0.006, bright=0.25):
    n = int(dur * SR); t = np.arange(n) / SR
    v = 1 + depth * np.sin(2 * np.pi * vib * t) * np.clip(t / 0.35, 0, 1)   # vibrato až po nasazení
    ph = 2 * np.pi * np.cumsum(f * v) / SR
    y = np.sin(ph) + bright * np.sin(2 * ph) + 0.08 * np.sin(3 * ph)
    noise = bp(rng.normal(0, 1, n), f * 0.8, min(f * 4, SR / 2 - 100)) * breath
    y = y + noise
    y *= env_adsr(n, 0.07, 0.1, 0.85, min(0.3, dur * 0.4))
    return y / (np.max(np.abs(y)) + 1e-9)


def reed(f, dur):
    """Nosová píšťala / duduk do hor: víc lichých harmonických, pomalé vibrato."""
    n = int(dur * SR); t = np.arange(n) / SR
    v = 1 + 0.008 * np.sin(2 * np.pi * 4.6 * t) * np.clip(t / 0.5, 0, 1)
    ph = 2 * np.pi * np.cumsum(f * v) / SR
    y = sum(np.sin(k * ph) / k ** 1.3 for k in (1, 2, 3, 5, 7))
    y = lp(y, 2600) + bp(rng.normal(0, 1, n), f, f * 3) * 0.06
    y *= env_adsr(n, 0.12, 0.2, 0.8, min(0.4, dur * 0.4))
    return y / (np.max(np.abs(y)) + 1e-9)


def pad(freqs, dur, cutoff=1400, attack=1.2):
    n = int(dur * SR); t = np.arange(n) / SR
    y = np.zeros(n)
    for f in freqs:
        for det in (-0.004, 0.0, 0.0045):
            ph = (f * (1 + det) * t + rng.random()) % 1.0
            y += 2 * ph - 1
    y = lp(y, cutoff, 2)
    y *= env_adsr(n, attack, 0.5, 0.9, min(1.5, dur * 0.4))
    return y / (np.max(np.abs(y)) + 1e-9)


def sine_bass(f, dur):
    n = int(dur * SR); t = np.arange(n) / SR
    y = np.sin(2 * np.pi * f * t) + 0.25 * np.sin(4 * np.pi * f * t)
    y *= env_adsr(n, 0.02, 0.2, 0.6, 0.2)
    return y / (np.max(np.abs(y)) + 1e-9)


def frame_drum(dur=0.6, pitch=90, slap=0.3):
    n = int(dur * SR); t = np.arange(n) / SR
    f = pitch * (1 + 0.6 * np.exp(-t * 30))
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 7)
    skin = bp(rng.normal(0, 1, n), 700, 3500) * np.exp(-t * 45) * slap
    y = body + skin
    return y / (np.max(np.abs(y)) + 1e-9)


def tom(pitch=140, dur=0.45):
    return frame_drum(dur, pitch, 0.15)


def shaker(dur=0.09):
    n = int(dur * SR); t = np.arange(n) / SR
    y = hp(rng.normal(0, 1, n), 5000) * np.exp(-t * 55) * np.clip(t / 0.01, 0, 1)
    return y / (np.max(np.abs(y)) + 1e-9)


def drop(f, dur=0.35):
    """Kapka vody: sinus s rychlým sklouznutím nahoru + krátký dozvuk."""
    n = int(dur * SR); t = np.arange(n) / SR
    fr = f * (1 + 1.2 * (1 - np.exp(-t * 60)))
    y = np.sin(2 * np.pi * np.cumsum(fr) / SR) * np.exp(-t * 22)
    return y / (np.max(np.abs(y)) + 1e-9)


def bird(dur=0.25, f0=3200):
    n = int(dur * SR); t = np.arange(n) / SR
    fr = f0 * (1 + 0.25 * np.sin(2 * np.pi * 18 * t)) * (1 + 0.3 * t / dur)
    y = np.sin(2 * np.pi * np.cumsum(fr) / SR) * np.sin(np.pi * t / dur) ** 2
    return y


def wind(dur, lo=250, hi=1400, rate=0.07):
    n = int(dur * SR); t = np.arange(n) / SR
    y = bp(rng.normal(0, 1, n), lo, hi, 1)
    swell = 0.55 + 0.45 * np.sin(2 * np.pi * rate * t + 1.3) * np.sin(2 * np.pi * rate * 0.37 * t)
    return y / (np.max(np.abs(y)) + 1e-9) * swell


def brass(f, dur):
    n = int(dur * SR); t = np.arange(n) / SR
    ph = (f * t) % 1.0
    y = (2 * ph - 1) + 0.5 * (2 * ((f * 1.003 * t) % 1.0) - 1)
    cut = 600 + 3000 * np.clip(t / 0.08, 0, 1) * np.exp(-t * 2)
    # časově proměnná propust: po blocích
    out = np.zeros(n); blk = 512
    for i in range(0, n, blk):
        out[i:i + blk] = lp(y[i:i + blk + 256], float(np.clip(cut[i], 200, 8000)), 1)[:len(out[i:i + blk])]
    out *= env_adsr(n, 0.02, 0.1, 0.8, min(0.25, dur * 0.3))
    return out / (np.max(np.abs(out)) + 1e-9)


# ── mix ─────────────────────────────────────────────────────────────────────────
class Track:
    def __init__(self, length_s):
        self.n = int(length_s * SR)
        self.buf = np.zeros(self.n + SR * 8)   # rezerva na doznívání

    def add(self, sig, at_s, gain=1.0):
        i = int(at_s * SR)
        end = min(len(self.buf), i + len(sig))
        self.buf[i:end] += sig[:end - i] * gain


def reverb_ir(seconds, damp=3000, predelay=0.02):
    n = int(seconds * SR); t = np.arange(n) / SR
    ir = rng.normal(0, 1, n) * np.exp(-t * 6.9 / seconds)
    ir = lp(ir, damp, 1)
    ir = np.concatenate([np.zeros(int(predelay * SR)), ir])
    ir[0] = 0
    return ir / np.sqrt(np.sum(ir ** 2))


def render_loop(track, wet, rev_s, damp=3000, echo=None):
    """Dozvuk + zacyklení: vše, co přesáhne délku smyčky, se přičte na začátek."""
    dry = track.buf
    rev = fftconvolve(dry, reverb_ir(rev_s, damp))[:len(dry) + int(rev_s * SR)]
    out = np.zeros(len(rev)); out[:len(dry)] += dry
    out = out * (1 - wet) + rev * wet * 1.6
    if echo:  # (zpoždění s, zpětná vazba, podíl)
        d, fb, mix = echo; k = int(d * SR)
        e = np.zeros(len(out) + 6 * k); e[:len(out)] = out
        for i in range(1, 6):
            e[i * k:i * k + len(out)] += out * (fb ** i) * mix
        out = e
    loop = out[:track.n].copy()
    tail = out[track.n:]
    for i in range(0, len(tail), track.n):                    # zabalit přesah
        seg = tail[i:i + track.n]; loop[:len(seg)] += seg
    return loop


def master(x, peak_db=-3.0, rms_db=-19.0, tone=None):
    x = x - np.mean(x)
    if tone:  # zateplení – odříznutí ostrých výšek (šum trsátek, chrastítko)
        x = lp(np.concatenate([x[-SR:], x]), tone, 2)[SR:]    # kruhově, ať smyčka nemá lupnutí
    rms = np.sqrt(np.mean(x ** 2)) + 1e-12
    x = x * (10 ** (rms_db / 20) / rms)
    # jemná saturace místo ostrého ořezu
    x = np.tanh(x * 1.2) / np.tanh(1.2)
    pk = np.max(np.abs(x))
    return x * min(1.0, 10 ** (peak_db / 20) / pk)


def export(name, x, quality=4):
    os.makedirs(RAW, exist_ok=True)
    with tempfile.TemporaryDirectory() as d:
        wav = os.path.join(d, "a.wav")
        wavfile.write(wav, SR, (np.clip(x, -1, 1) * 32767).astype(np.int16))
        out = os.path.join(RAW, name + ".ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", str(quality), out], check=True)
    print(f"{name}: {len(x) / SR:.1f} s, {os.path.getsize(out) // 1024} kB")


def chord_notes(root, kind, octave):
    iv = {"maj": (0, 4, 7), "min": (0, 3, 7), "sus4": (0, 5, 7), "sus2": (0, 2, 7)}[kind]
    base = NOTE[root] + 12 * (octave + 1)
    return [440.0 * 2 ** ((base + i - 69) / 12) for i in iv]


# ═══════════════════════════════════════════════════════════════════════════════
# MĚSTO – klidné probrnkávání loutny, vítr hladí (D dur, 3/4, 72 bpm)
# ═══════════════════════════════════════════════════════════════════════════════
def town():
    bpm = 72; beat = 60 / bpm; bar = 3 * beat
    prog = [("D", "maj"), ("B", "min"), ("G", "maj"), ("A", "sus4"),
            ("D", "maj"), ("F#", "min"), ("G", "maj"), ("A", "maj"),
            ("B", "min"), ("G", "maj"), ("D", "maj"), ("A", "maj"),
            ("G", "maj"), ("D", "maj"), ("E", "min"), ("A", "sus4")]
    bars = len(prog) * 2
    tr = Track(bars * bar)
    # melodie (píšťala, jen v druhé polovině – první průchod čistě loutna)
    # (C#5 by nad Asus4 tvořilo malou sekundu s D → D5; nad F#m místo G raději F#)
    mel = [("F#5", 1.5), ("E5", 0.5), ("D5", 1), ("", 3), ("B4", 1), ("D5", 1), ("E5", 1), ("D5", 3),
           ("D5", 1.5), ("E5", 0.5), ("F#5", 1), ("A5", 2), ("F#5", 1), ("G5", 3), ("E5", 2), ("", 1),
           ("D5", 1), ("F#5", 1), ("E5", 1), ("D5", 2), ("B4", 1), ("A4", 3), ("", 3),
           ("B4", 1), ("D5", 1), ("G5", 1), ("F#5", 1.5), ("E5", 0.5), ("D5", 1), ("E5", 3), ("", 3)]
    for b in range(bars):
        root, kind = prog[b % len(prog)]
        t0 = b * bar
        lo = chord_notes(root, kind, 3)
        hi = chord_notes(root, kind, 4)
        # arpeggio osminami: basa, kvinta, oktáva, tercie, kvinta, oktáva
        pat = [lo[0], lo[2], hi[0], hi[1], hi[2], hi[0]]
        for i, f in enumerate(pat):
            swing = 0.02 * (i % 2)
            tr.add(pluck(f, 2.4, bright=0.35, decay=0.9975), t0 + i * beat / 2 + swing,
                   gain=(0.55 if i == 0 else 0.33) * (0.9 + 0.2 * rng.random()))
        tr.add(sine_bass(lo[0] / 2, bar * 0.95), t0, 0.22)
        if b % 2 == 0:
            tr.add(pad(hi, bar * 2, cutoff=900, attack=1.5), t0, 0.07)
    t = len(prog) * bar
    for note, beats in mel:
        if note: tr.add(flute(hz(note), beats * beat * 0.98, breath=0.1, bright=0.2), t, 0.2)
        t += beats * beat
    tr.add(wind(bars * bar + 2, 200, 1100, 0.05), 0, 0.05)
    x = render_loop(tr, wet=0.3, rev_s=2.2, damp=2800)
    export("music_town", master(x, rms_db=-21, tone=3800))


# ═══════════════════════════════════════════════════════════════════════════════
# LOUKA / HVOZD – světlejší pastorála, loutna + flétna + ptáci (G dur, 6/8)
# ═══════════════════════════════════════════════════════════════════════════════
def meadow():
    bpm = 88; beat = 60 / bpm; bar = 3 * beat
    prog = [("G", "maj"), ("D", "maj"), ("E", "min"), ("C", "maj"),
            ("G", "maj"), ("C", "maj"), ("A", "min"), ("D", "sus4")]
    bars = len(prog) * 3
    tr = Track(bars * bar)
    mel = [("B4", 1), ("D5", 1), ("G5", 1), ("F#5", 2), ("D5", 1), ("E5", 1.5), ("D5", 0.5), ("B4", 1), ("C5", 3),
           ("B4", 1), ("A4", 1), ("G4", 1), ("A4", 2), ("B4", 1), ("C5", 1), ("B4", 1), ("A4", 1), ("A4", 3),
           ("D5", 1), ("G5", 1), ("A5", 1), ("B5", 2), ("G5", 1), ("A5", 1.5), ("G5", 0.5), ("E5", 1), ("G5", 3),
           ("E5", 1), ("D5", 1), ("B4", 1), ("C5", 2), ("A4", 1), ("B4", 3), ("A4", 3)]
    for b in range(bars):
        root, kind = prog[b % len(prog)]
        t0 = b * bar
        lo = chord_notes(root, kind, 3); hi = chord_notes(root, kind, 4)
        pat = [lo[0], hi[0], hi[1], lo[2], hi[1], hi[2]]
        for i, f in enumerate(pat):
            tr.add(pluck(f, 1.8, bright=0.45, decay=0.997), t0 + i * beat / 2, 0.5 if i == 0 else 0.3)
        tr.add(sine_bass(lo[0] / 2, bar * 0.9), t0, 0.2)
    t = len(prog) * bar
    for note, beats in mel:
        if note: tr.add(flute(hz(note), beats * beat * 0.97, breath=0.13, bright=0.3), t, 0.24)
        t += beats * beat
    for _ in range(14):
        tr.add(bird(0.18 + 0.1 * rng.random(), 2800 + 1400 * rng.random()), rng.uniform(0, bars * bar - 1), 0.05)
    tr.add(wind(bars * bar + 2, 300, 1500, 0.06), 0, 0.035)
    x = render_loop(tr, wet=0.25, rev_s=1.8, damp=3500)
    export("music_meadow", master(x, rms_db=-21, tone=5500))


# ═══════════════════════════════════════════════════════════════════════════════
# HORY – suchá, bubnová (A frygická / dorská, 4/4, 96 bpm, skoro bez dozvuku)
# ═══════════════════════════════════════════════════════════════════════════════
def mountains():
    bpm = 96; beat = 60 / bpm; bar = 4 * beat
    bars = 24
    tr = Track(bars * bar)
    scale = ["A3", "Bb3", "C4", "D4", "E4", "F4", "G4", "A4"]
    # bordun A + E (suchý, nosový pad)
    tr.add(pad([hz("A2"), hz("E3")], bars * bar + 1, cutoff=700, attack=2.0), 0, 0.12)
    for b in range(bars):
        t0 = b * bar
        # rámový buben: DUM . tek DUM | tek . DUM tek  (šestnáctinový groove)
        hits = [(0, "D"), (1.5, "t"), (2, "D"), (3, "t"), (3.5, "t")]
        if b % 4 == 3: hits += [(2.5, "D"), (3.75, "t")]            # přechod
        for pos, k in hits:
            if k == "D": tr.add(frame_drum(0.7, 80, 0.25), t0 + pos * beat, 0.55)
            else: tr.add(frame_drum(0.25, 220, 0.9), t0 + pos * beat, 0.22)
        if b >= 4:
            for s in range(8): tr.add(shaker(), t0 + s * beat / 2 + 0.01, 0.07 if s % 2 else 0.11)
        if b % 2 == 1: tr.add(tom(125), t0 + 3.5 * beat, 0.3)
        # drnkací ostinato (oud): krátké, tvrdé, suché
        riff = [0, 1, 0, 2, 0, 1, 4, 3] if b % 8 < 4 else [0, 1, 0, 2, 3, 2, 1, 0]
        for i, deg in enumerate(riff):
            tr.add(pluck(hz(scale[deg]), 0.5, bright=0.8, decay=0.985, body=True), t0 + i * beat / 2, 0.28)
    # nosová píšťala – frygická fráze ve 2. polovině
    t = 12 * bar
    for note, beats in [("E5", 2), ("F5", 1), ("E5", 1), ("D5", 2), ("C5", 1), ("Bb4", 1), ("A4", 4),
                        ("C5", 1), ("D5", 1), ("E5", 2), ("F5", 1.5), ("G5", 0.5), ("F5", 1), ("E5", 3), ("", 4),
                        ("A5", 2), ("G5", 1), ("F5", 1), ("E5", 2), ("D5", 1), ("C5", 1), ("Bb4", 2), ("A4", 6)]:
        if note: tr.add(reed(hz(note), beats * beat * 0.95), t, 0.2)
        t += beats * beat
    tr.add(wind(bars * bar + 2, 150, 700, 0.04), 0, 0.05)
    x = render_loop(tr, wet=0.08, rev_s=0.8, damp=2500)       # suchý prostor
    export("music_mountains", master(x, rms_db=-19, tone=6000))


# ═══════════════════════════════════════════════════════════════════════════════
# JESKYNĚ – kapání a lehké píšťaly (E moll/aiolská, volně, dlouhá ozvěna)
# ═══════════════════════════════════════════════════════════════════════════════
def cave():
    length = 64.0
    tr = Track(length)
    tr.add(pad([hz("E2"), hz("B2")], length + 2, cutoff=380, attack=3.0), 0, 0.2)
    tr.add(pad([hz("G3"), hz("B3"), hz("E4")], length + 2, cutoff=600, attack=6.0), 0, 0.05)
    penta = ["E5", "G5", "A5", "B5", "D6", "E6"]
    t = 0.3
    while t < length - 0.5:                                  # kapky v nepravidelném rytmu
        f = hz(penta[int(rng.integers(0, len(penta)))])
        tr.add(drop(f * 0.5, 0.3), t, 0.28 * (0.6 + 0.4 * rng.random()))
        t += rng.choice([0.9, 1.3, 1.8, 2.4, 3.1])
    # píšťala: krátké tiché fráze s dlouhými pauzami
    phrases = [(6, [("B4", 1.2), ("E5", 0.8), ("D5", 2.5)]),
               (18, [("G4", 1.0), ("A4", 1.0), ("B4", 3.0)]),
               (31, [("E5", 0.8), ("G5", 0.8), ("F#5", 1.2), ("E5", 3.0)]),
               (45, [("D5", 1.0), ("B4", 1.0), ("A4", 1.2), ("B4", 3.5)]),
               (57, [("E4", 1.5), ("B4", 3.0)])]
    for start, notes in phrases:
        t = start
        for note, d in notes:
            tr.add(flute(hz(note), d, breath=0.22, vib=4.5, depth=0.005, bright=0.12), t, 0.16)
            t += d * 0.95
    for t in (12.0, 38.0):                                   # vzdálené dunění
        n = int(4 * SR); tt = np.arange(n) / SR
        rumble = lp(rng.normal(0, 1, n), 90, 2) * np.sin(np.pi * tt / 4)
        tr.add(rumble / np.max(np.abs(rumble)), t, 0.25)
    x = render_loop(tr, wet=0.55, rev_s=4.5, damp=2200, echo=(0.42, 0.45, 0.35))
    export("music_cave", master(x, rms_db=-22))


# ═══════════════════════════════════════════════════════════════════════════════
# ZVUKY
# ═══════════════════════════════════════════════════════════════════════════════
def one_shot(sig, rev=0.8, wet=0.2, name="", peak=-1.0):
    ir = reverb_ir(rev, 4000)
    y = sig * (1 - wet) + fftconvolve(sig, ir)[:len(sig)] * wet * 1.5
    y = y / np.max(np.abs(y)) * 10 ** (peak / 20)
    n = len(y); fade = int(0.05 * SR)
    y[-fade:] *= np.linspace(1, 0, fade)
    export(name, y, quality=5)


def sfx_battle_start():
    dur = 1.4; n = int(dur * SR); t = np.arange(n) / SR
    y = np.zeros(n)
    # sklouznutí šumu vzhůru (nádech) → úder
    sweep = bp(rng.normal(0, 1, n), 300, 6000) * np.clip(t / 0.5, 0, 1) * (t < 0.55)
    y += sweep * 0.5
    riser = np.sin(2 * np.pi * np.cumsum(200 * 2 ** (t / 0.55 * 2)) / SR) * (t < 0.55) * np.clip(t / 0.55, 0, 1)
    y += riser * 0.3
    hit_at = int(0.55 * SR)
    drum = frame_drum(0.8, 60, 0.6)
    y[hit_at:hit_at + len(drum)] += drum[:n - hit_at] * 1.0
    for f, g in ((hz("A3"), 0.5), (hz("E4"), 0.4), (hz("A4"), 0.35), (hz("C5"), 0.3)):
        s = brass(f, 0.8); y[hit_at:hit_at + len(s)] += s[:n - hit_at] * g
    one_shot(y, 0.9, 0.25, "sfx_battle_start")


def sfx_catch():
    dur = 1.6; n = int(dur * SR); y = np.zeros(n)
    for i, note in enumerate(["C5", "E5", "G5", "C6"]):
        at = int(i * 0.11 * SR)
        s = pluck(hz(note), 1.2, bright=0.9, decay=0.998, body=False)
        bell = np.sin(2 * np.pi * hz(note) * 2 * np.arange(len(s)) / SR) * np.exp(-np.arange(len(s)) / SR * 4)
        seg = s * 0.7 + bell * 0.3
        y[at:at + len(seg)] += seg[:n - at] * (0.7 if i < 3 else 1.0)
    sp = int(0.45 * SR)                                        # třpyt
    for k in range(6):
        s = drop(hz("E6") * (1 + 0.12 * k), 0.25); a = sp + int(k * 0.05 * SR)
        y[a:a + len(s)] += s[:n - a] * 0.15
    one_shot(y, 1.2, 0.3, "sfx_catch")


def sfx_victory():
    beat = 0.14; dur = 2.0; n = int(dur * SR); y = np.zeros(n)
    notes = [("G4", 1), ("G4", 1), ("G4", 1), ("C5", 4)]
    t = 0
    for note, b in notes:
        s = brass(hz(note), b * beat * 0.9 + (0.6 if b == 4 else 0))
        a = int(t * SR); y[a:a + len(s)] += s[:n - a] * 0.6
        t += b * beat
    a = int(3 * beat * SR)
    for f in (hz("C4"), hz("E4"), hz("G4")):
        s = brass(f, 1.0); y[a:a + len(s)] += s[:n - a] * 0.3
    d = frame_drum(0.6, 70, 0.4); y[a:a + len(d)] += d[:n - a] * 0.6
    one_shot(y, 1.0, 0.22, "sfx_victory")


if __name__ == "__main__":
    town(); meadow(); mountains(); cave()
    sfx_battle_start(); sfx_catch(); sfx_victory()

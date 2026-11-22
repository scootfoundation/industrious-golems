"""MINECRAFT-style title lettering, studied from the game's own logo: fat stone letters seen from above in mild perspective, counters solid black,
a short dark front band under the letters standing on the baseline, thick black outline. Only the stone word is drawn here (see make_word)."""
import math
import random
from logo_slab import hull, pstr, inside, par
from logo_slab import bar as _bar


def bar(x0, y0, x1, y1, c=0.2):
    return _bar(x0, y0, x1, y1, c)

LETTERS = {
    "I": (5.2, [bar(0, 0, 5.2, 2.5), bar(1.0, 0, 4.2, 10), bar(0, 7.5, 5.2, 10)]),
    "T": (8.6, [bar(0, 0, 8.6, 2.6), bar(2.7, 0, 5.9, 10)]),
    "E": (8.2, [bar(0, 0, 3.2, 10), bar(0, 0, 8.2, 2.5), bar(0, 3.8, 6.6, 6.2), bar(0, 7.5, 8.2, 10)]),
    "F": (8.2, [bar(0, 0, 3.2, 10), bar(0, 0, 8.2, 2.5), bar(0, 3.8, 6.6, 6.2)]),
    "C": (8.6, [bar(0, 0.6, 3.2, 9.4), bar(0.6, 0, 8.6, 2.7), bar(0.6, 7.3, 8.6, 10), bar(6.0, 2.0, 8.6, 4.2), bar(6.0, 5.8, 8.6, 8.0)]),
    "A": (8.6, [bar(0, 0.8, 3.1, 10), bar(5.5, 0.8, 8.6, 10), bar(0.9, 0, 7.7, 2.7), bar(0, 5.2, 8.6, 7.4)]),
    "R": (8.6, [bar(0, 0, 3.2, 10), bar(0, 0, 7.9, 2.7), bar(5.4, 0.6, 8.5, 5.8), bar(0, 4.3, 7.7, 6.8), par((3.3, 6.2), (6.3, 6.2), (8.6, 10), (5.5, 10))]),
    "N": (10.0, [bar(0, 0, 2.7, 10), bar(7.3, 0, 10.0, 10), par((1.8, 0), (4.2, 0), (8.2, 10), (5.8, 10))]),
    "M": (11.6, [bar(0, 0, 2.9, 10), bar(8.7, 0, 11.6, 10), par((2.0, 0), (4.6, 0), (6.7, 6.2), (4.7, 6.2)), par((9.6, 0), (7.0, 0), (4.9, 6.2), (6.9, 6.2)), bar(4.9, 5.0, 6.7, 8.2)]),
}

GAP = 1.0
PITCH = 58.0
CAM_X = 1.6       # camera along the text, fraction of the width: letters left of it lean right
DIST = 3.2
SLANT = 0.05     # extra lean of the letter shapes
XS = 1.0
SY = 1.5          # letters are taller than the 10-unit design
DEPTH = 9.0
DRIFT = 0.45       # the front band drifts left as it goes down


class Cam:
    def __init__(self, width, zc):
        e = math.radians(PITCH)
        self.se, self.ce = math.sin(e), math.cos(e)
        self.cx, self.cz, self.d = CAM_X * width, zc, DIST * width

    def __call__(self, x, y, z):
        x, z = x - self.cx, z - self.cz
        depth = self.d - self.se * y - self.ce * z
        k = self.d / depth
        return (x * k, -(y * self.ce - z * self.se) * k)


def letters(word):
    out, x = [], 0.0
    for ch in word:
        w, strokes = LETTERS[ch]
        out.append((ch, x, w, strokes))
        x += w + GAP
    return out, x - GAP


def make_word(word="MINECRAFT", rng=None):
    rng = rng or random.Random(5)
    lets, W = letters(word)
    cam = Cam(W, 5.0 * SY)

    def P(x, y, py):         # x,py in letter units
        return cam((x * XS + SLANT * (10 - py)), y, py * SY)
    unit = abs(P(1, 0, 5)[0] - P(0, 0, 5)[0])
    bases, bands, tops, allp, seps = [], [], [], [], []
    for ch, x0, w, strokes in lets:
        letter_pts = []
        for st in strokes:
            t = [P(x0 + px, 0, py) for px, py in st]
            b = [(P(x0 + px, -DEPTH, py)[0] - DRIFT * DEPTH * unit * 0.0, P(x0 + px, -DEPTH, py)[1]) for px, py in st]
            b = [cam((x0 + px) * XS + SLANT * (10 - py) - DRIFT * DEPTH, -DEPTH, py * SY) for px, py in st]
            tops.append(t)
            letter_pts += t + b
            if max(py for _, py in st) >= 9.3:
                bands.append(hull(t + b))
                for k in (min(range(len(t)), key=lambda q: t[q][0]), max(range(len(t)), key=lambda q: t[q][0])):
                    seps.append(t[k] + b[k])
            allp += t + b
        bases.append(hull(letter_pts))
    xs, ys = [p[0] for p in allp], [p[1] for p in allp]
    sw = 1.7 * unit
    pad = sw
    vb = (min(xs) - pad, min(ys) - pad, max(xs) - min(xs) + 2 * pad, max(ys) - min(ys) + 2 * pad)
    ty = [p[1] for t in tops for p in t]
    by = [p[1] for t in bands for p in t]
    y0, y1 = min(ty), max(ty)
    defs = ('<linearGradient id="face" gradientUnits="userSpaceOnUse" x1="0" y1="%.2f" x2="0" y2="%.2f"><stop offset="0" stop-color="#d6d2cc"/><stop offset="1" stop-color="#bcb8b1"/></linearGradient>'
            '<linearGradient id="band" gradientUnits="userSpaceOnUse" x1="0" y1="%.2f" x2="0" y2="%.2f"><stop offset="0" stop-color="#454545"/><stop offset="1" stop-color="#2b2b2b"/></linearGradient>') % (y0, y1, y1, max(by))
    body = ['<g fill="#000" stroke="#000" stroke-width="%.2f" stroke-linejoin="round">%s</g>' % (sw, "".join('<polygon points="%s"/>' % pstr(p) for p in bases))]
    body.append('<g fill="url(#band)">%s</g>' % "".join('<polygon points="%s"/>' % pstr(p) for p in bands))
    body.append('<g stroke="#000" stroke-width="%.2f" stroke-opacity="0.8">%s</g>' % (0.3 * unit, "".join('<path d="M%.2f %.2f L%.2f %.2f"/>' % q for q in seps)))
    body.append('<g fill="url(#face)" stroke="url(#face)" stroke-width="0.4">%s</g>' % "".join('<polygon points="%s"/>' % pstr(p) for p in tops))
    # cracks: bent at right angles, thin, with a light fleck
    cr, pits = [], []
    flat = [(x0, st) for ch, x0, w, strokes in lets for st in strokes]
    for _ in range(34):
        x0, st = rng.choice(flat)
        xsb, ysb = [p[0] for p in st], [p[1] for p in st]
        for _t in range(20):
            x, y = rng.uniform(min(xsb), max(xsb)), rng.uniform(min(ysb), max(ysb))
            if inside(st, (x, y)):
                break
        else:
            continue
        pts = [(x, y)]
        horiz = rng.random() < 0.5
        for _ in range(rng.randrange(2, 5)):
            L = rng.uniform(0.8, 2.2)
            if horiz:
                nx, ny = x + L * rng.choice((-1, 1)), y + rng.uniform(-0.15, 0.15)
            else:
                nx, ny = x + rng.uniform(-0.15, 0.15), y + L * rng.choice((-1, 1))
            if not inside(st, (nx, ny)):
                break
            x, y = nx, ny
            pts.append((x, y))
            horiz = not horiz
        if len(pts) > 1:
            d = "M" + " L".join("%.2f %.2f" % P(x0 + a, 0, b) for a, b in pts)
            cr.append('<path d="%s" stroke="#7c7872" stroke-width="%.2f"/>' % (d, 0.1 * unit))
            fx, fy = P(x0 + pts[0][0], 0, pts[0][1])
            pits.append('<ellipse cx="%.2f" cy="%.2f" rx="%.2f" ry="%.2f"/>' % (fx, fy, 0.45 * unit, 0.1 * unit))
    body.append('<g fill="none" stroke-linecap="round" stroke-linejoin="round">%s</g>' % "".join(cr))
    body.append('<g fill="#f6f4f0">%s</g>' % "".join(pits))
    # creeper face on the A
    ax = sum(w + GAP for ch, x0, w, s in lets[:6])
    px_ = []
    for (a, b, w_, h_) in [(1.7, 0.5, 1.4, 1.2), (5.0, 0.5, 1.4, 1.2), (3.4, 1.4, 1.8, 1.5)]:
        q = [P(ax + a + dx, 0, b + dy) for dx, dy in ((0, 0), (w_, 0), (w_, h_), (0, h_))]
        px_.append('<polygon points="%s"/>' % pstr(q))
    body.append('<g fill="#101010">%s</g>' % "".join(px_))
    return vb, defs, "".join(body)


def make(path):
    vb, defs, body = make_word()
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="%.1f %.1f %.1f %.1f" role="img" aria-label="Minecraft">'
           '<defs>%s</defs>%s</svg>') % (vb + (defs, body))
    open(path, "w", encoding="utf-8").write(svg)


if __name__ == "__main__":
    make("mc.svg")

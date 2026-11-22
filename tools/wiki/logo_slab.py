"""Wiki logo, hand-built: every letter is a few fat convex strokes (chamfered bars, slanted legs) in a 10-unit-tall letter space,
extruded as a slab and seen in mild perspective from above: lit stone top, dark front, thick black outline, rough chipped edges.
Pure SVG. make(path) writes it; tweak PITCH/CAM_X/DIST below, letter shapes in LETTERS."""
import math
import random

PITCH = 58.0       # camera elevation, degrees
CAM_X = 0.12       # camera position along the text (fraction of the width); letters lean away from it
DIST = 2.4         # camera distance in text widths
SLANT = 0.28      # letters lean right: x shift per unit of height
XS = 1.0         # letters are squeezed a little in x
GAP = 0.9          # between letters
DEPTH = 12.0        # slab thickness in letter units


def bar(x0, y0, x1, y1, c=0.38):
    c = min(c, (x1 - x0) / 2.2, (y1 - y0) / 2.2)
    return [(x0 + c, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1 - c), (x1 - c, y1), (x0 + c, y1), (x0, y1 - c), (x0, y0 + c)]


def par(*p):
    return list(p)


# letter: (width, [convex strokes])
LETTERS = {
    "I": (5.0, [bar(0, 0, 5, 3.4), bar(1.0, 0, 4.0, 10), bar(0, 7, 5, 10)]),
    "T": (8.5, [bar(0, 0, 8.5, 3.4), bar(2.6, 0, 5.9, 10)]),
    "E": (8.0, [bar(0, 0, 3.4, 10), bar(0, 0, 8, 3.4), bar(0, 3.3, 6.4, 6.6), bar(0, 7, 8, 10)]),
    "F": (8.0, [bar(0, 0, 3.4, 10), bar(0, 0, 8, 3.4), bar(0, 3.3, 6.4, 6.6)]),
    "L": (8.0, [bar(0, 0, 3.4, 10), bar(0, 7, 8, 10)]),
    "C": (8.5, [bar(0, 0.6, 3.4, 9.4), bar(0.6, 0, 8.5, 3.4), bar(0.6, 7, 8.5, 10), bar(5.8, 2, 8.5, 4.4), bar(5.8, 5.6, 8.5, 8)]),
    "A": (8.5, [bar(0, 0.8, 3.3, 10), bar(5.4, 0.8, 8.5, 10), bar(0.9, 0, 7.6, 3.5), bar(0, 5, 8.5, 7.2)]),
    "R": (8.5, [bar(0, 0, 3.4, 10), bar(0, 0, 7.8, 3.4), bar(5.4, 0.6, 8.4, 5.8), bar(0, 4, 7.6, 6.8), par((3.3, 6.2), (6.3, 6.2), (8.5, 10), (5.4, 10))]),
    "N": (9.0, [bar(0, 0, 3.3, 10), bar(5.9, 0, 9, 10), par((2.0, 0), (5.2, 0), (7.0, 10), (3.8, 10))]),
    "M": (11.5, [bar(0, 0, 3.3, 10), bar(8.4, 0, 11.5, 10), par((2.0, 0), (5.0, 0), (6.9, 6.4), (4.5, 6.4)), par((9.5, 0), (6.5, 0), (4.6, 6.4), (7.0, 6.4)), bar(4.4, 5.2, 7.1, 8.8)]),
    "D": (8.5, [bar(0, 0, 3.4, 10), bar(0, 0, 6.6, 3.4), bar(0, 7, 6.6, 10), bar(5.4, 1.4, 8.5, 8.6)]),
    "U": (8.5, [bar(0, 0, 3.4, 9.4), bar(5.3, 0, 8.5, 9.4), bar(0.6, 7, 7.9, 10)]),
    "S": (8.5, [bar(0.6, 0, 8.5, 3.4), bar(0, 0.6, 3.3, 5.8), bar(0.4, 3.8, 8.1, 6.4), bar(5.4, 4.2, 8.5, 9.4), bar(0, 7, 7.9, 10)]),
    "O": (8.5, [bar(0, 0.6, 3.4, 9.4), bar(5.3, 0.6, 8.5, 9.4), bar(0.6, 0, 7.9, 3.4), bar(0.6, 7, 7.9, 10)]),
    "G": (8.5, [bar(0, 0.6, 3.4, 9.4), bar(0.6, 0, 8.5, 3.4), bar(0.6, 7, 7.9, 10), bar(5.3, 4.6, 8.5, 9.4), bar(4.2, 4.6, 8.5, 6.8)]),
}


def word_strokes(word, scale=1.0):
    """[(stroke points)], width in letter units (unscaled)."""
    out, x = [], 0.0
    for ch in word:
        w, strokes = LETTERS[ch]
        for s in strokes:
            out.append([(x + px, py) for px, py in s])
        x += w + GAP
    return out, x - GAP


def hull(points):
    pts = sorted(set((round(a, 3), round(b, 3)) for a, b in points))

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lo, up = [], []
    for p in pts:
        while len(lo) >= 2 and cross(lo[-2], lo[-1], p) <= 0:
            lo.pop()
        lo.append(p)
    for p in reversed(pts):
        while len(up) >= 2 and cross(up[-2], up[-1], p) <= 0:
            up.pop()
        up.append(p)
    return lo[:-1] + up[:-1]


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


def pstr(pts):
    return " ".join("%.2f,%.2f" % p for p in pts)


def inside(poly, p):
    s = None
    for i in range(len(poly)):
        a, b = poly[i], poly[(i + 1) % len(poly)]
        v = (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])
        if v != 0:
            if s is None:
                s = v > 0
            elif s != (v > 0):
                return False
    return True


def lerpc(a, b, t):
    return "#%02x%02x%02x" % tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def make(path, only_top=False):
    rng = random.Random(7)
    top = "MINECRAFT"
    s1, W = word_strokes(top)
    words = [("INDUSTRIOUS", (214, 112, 30), (255, 226, 112), (86, 48, 22)),
             ("GOLEMS", (96, 160, 60), (214, 240, 140), (52, 86, 30))]
    w1 = word_strokes(words[0][0])[1]
    w2 = word_strokes(words[1][0])[1]
    sc2 = W / (w1 + 5.0 + w2)
    cam = Cam(W, 6.0)
    lines = []
    lines.append(dict(strokes=s1, z0=0.0, s=1.0, dx=0.0, kind="stone", sy=1.25))
    z2 = 12.5 + 8.0
    if not only_top:
        lines.append(dict(strokes=word_strokes(words[0][0])[0], z0=z2, s=sc2, dx=0.0, kind=0, sy=1.5))
        lines.append(dict(strokes=word_strokes(words[1][0])[0], z0=z2, s=sc2, dx=(w1 + 5.0) * sc2, kind=1, sy=1.5))
    order = (lines[1:] + lines[:1])          # far-to-near drawing is reversed: the first line is drawn last, on top
    allpts, built = [], []
    for ln in order:
        s, z0, dx = ln["s"], ln["z0"], ln["dx"]
        tops, sides, edges = [], [], []
        for st in ln["strokes"]:
            t3 = [cam(dx + (px * XS + SLANT * (10 - py)) * s, 0.0, z0 + py * s * ln.get("sy", 1.0)) for px, py in st]
            b3 = [cam(dx + (px * XS + SLANT * (10 - py) - 0.22 * DEPTH) * s, -DEPTH * s * (1.0 if ln["kind"] == "stone" else 1.1), z0 + py * s * ln.get("sy", 1.0)) for px, py in st]
            tops.append(t3)
            for k in (min(range(len(t3)), key=lambda q: t3[q][0]), max(range(len(t3)), key=lambda q: t3[q][0])):
                edges.append(t3[k] + b3[k])
            sides.append(hull(t3 + b3))
            allpts += t3 + b3
        ln["tops"], ln["sides"] = tops, sides
        ln["edges"] = edges
    xs, ys = [p[0] for p in allpts], [p[1] for p in allpts]
    unit = (max(xs) - min(xs)) / W
    pad = 1.4 * unit
    vb = (min(xs) - pad, min(ys) - pad, max(xs) - min(xs) + 2 * pad, max(ys) - min(ys) + 2 * pad)
    defs, body = [], []
    defs.append('<filter id="rough" x="-3%%" y="-6%%" width="106%%" height="112%%"><feTurbulence type="fractalNoise" baseFrequency="%.3f" numOctaves="2" seed="4" result="n"/>'
                '<feDisplacementMap in="SourceGraphic" in2="n" scale="%.2f" xChannelSelector="R" yChannelSelector="G"/></filter>' % (0.9 / unit / 10, 0.07 * unit))
    for i, ln in enumerate(order):
        ty = [p[1] for t in ln["tops"] for p in t]
        sy = [p[1] for t in ln["sides"] for p in t]
        y0, ylit, y1 = min(ty), max(ty), max(sy)
        f = (ylit - y0) / (y1 - y0)
        if ln["kind"] == "stone":
            topf = ("t%d" % i, [(0, "#d4d0c9"), (1, "#c2bdb6")])
            lit, dark = "#5c5c5c", "#060606"
        else:
            a, b, c = words[ln["kind"]][1:]
            topf = ("t%d" % i, [(0, lerpc(a, a, 0)), (0.4, lerpc(a, a, 0)), (1, lerpc(b, b, 0))])
            lit, dark = lerpc(c, c, 0), "#0a0806"
        defs.append('<linearGradient id="t%d" gradientUnits="userSpaceOnUse" x1="0" y1="%.2f" x2="0" y2="%.2f">%s</linearGradient>' % (
            i, y0, ylit, "".join('<stop offset="%s" stop-color="%s"/>' % s for s in topf[1])))
        defs.append('<linearGradient id="s%d" gradientUnits="userSpaceOnUse" x1="0" y1="%.2f" x2="0" y2="%.2f">'
                    '<stop offset="0" stop-color="%s"/><stop offset="%.3f" stop-color="%s"/><stop offset="%.3f" stop-color="%s"/><stop offset="%.3f" stop-color="%s"/></linearGradient>' % (
                        i, y0, y1, dark, max(0, f - 0.01), dark, f, lit, 0.97, dark))
        sw = 2.2 * unit
        g = ['<g filter="url(#rough)">']
        g.append('<g fill="#000" stroke="#000" stroke-width="%.2f" stroke-linejoin="round">%s</g>' % (sw, "".join('<polygon points="%s"/>' % pstr(p) for p in ln["sides"])))
        g.append('<g fill="url(#s%d)" stroke="none">%s</g>' % (i, "".join('<polygon points="%s"/>' % pstr(p) for p in ln["sides"])))
        rim, lip = ("#f4f1ec", "#86827d") if ln["kind"] == "stone" else ("#fff6d0", "#e0903a" if ln["kind"] == 0 else "#8cc860")
        g.append('<g fill="%s" transform="translate(0 %.2f)">%s</g>' % (rim, -0.5 * unit, "".join('<polygon points="%s"/>' % pstr(p) for p in ln["tops"])))
        g.append('<g fill="%s" transform="translate(0 %.2f)">%s</g>' % (lip, 0.45 * unit, "".join('<polygon points="%s"/>' % pstr(p) for p in ln["tops"])))
        sep = ['<path d="M%.2f %.2f L%.2f %.2f"/>' % e3 for e3 in ln["edges"]]
        g.append('<g stroke="#000" stroke-opacity="0.7" stroke-width="%.2f">%s</g>' % (0.25 * unit, "".join(sep)))
        g.append('<g fill="url(#t%d)" stroke="url(#t%d)" stroke-width="0.3">%s</g>' % (i, i, "".join('<polygon points="%s"/>' % pstr(p) for p in ln["tops"])))
        g.append("</g>")
        body.append("".join(g))
        if ln["kind"] == "stone":
            cr = []
            strokes = ln["strokes"]
            for _ in range(26):
                st = rng.choice(strokes)
                xsb = [p[0] for p in st]
                ysb = [p[1] for p in st]
                for _try in range(20):
                    x, y = rng.uniform(min(xsb), max(xsb)), rng.uniform(min(ysb), max(ysb))
                    if inside(st, (x, y)):
                        break
                else:
                    continue
                pts = [(x, y)]
                ang = rng.random() * math.tau
                for _ in range(rng.randrange(3, 7)):
                    ang += rng.uniform(-1.2, 1.2)
                    L = rng.uniform(1.0, 2.4)
                    nx, ny = x + math.cos(ang) * L, y + math.sin(ang) * L
                    if not inside(st, (nx, ny)):
                        break
                    x, y = nx, ny
                    pts.append((x, y))
                if len(pts) > 1:
                    d = "M" + " L".join("%.2f %.2f" % cam(px * XS + SLANT * (10 - py), 0, py * ln.get("sy", 1.0)) for px, py in pts)
                    cr.append('<path d="%s" stroke="#3b3733" stroke-width="%.2f"/>' % (d, 0.2 * unit * 1.6))
                    cr.append('<path d="%s" transform="translate(%.2f %.2f)" stroke="#f4f1ec" stroke-width="%.2f"/>' % (d, 0.12 * unit, 0.18 * unit, 0.12 * unit * 1.6))
            body.append('<g fill="none" stroke-linecap="round" stroke-linejoin="round">%s</g>' % "".join(cr))
            ax = sum(LETTERS[ch][0] + GAP for ch in "MINECR")
            sy1 = ln.get("sy", 1.0)
            px_ = []
            for (x0, y0_, w_, h_) in [(1.9, 0.55, 1.3, 1.15), (4.9, 0.55, 1.3, 1.15), (3.4, 1.55, 1.7, 1.2)]:
                q = [cam((ax + x0 + dx_) * XS + SLANT * (10 - (y0_ + dy_)), 0, (y0_ + dy_) * sy1) for dx_, dy_ in ((0, 0), (w_, 0), (w_, h_), (0, h_))]
                px_.append('<polygon points="%s"/>' % pstr(q))
            body.append('<g fill="#26231f">%s</g>' % "".join(px_))
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="%.1f %.1f %.1f %.1f" role="img" aria-label="Industrious Golems">'
           '<title>Industrious Golems</title><defs>%s</defs>%s</svg>') % (vb + ("".join(defs), "".join(body)))
    with open(path, "w", encoding="utf-8") as f:
        f.write(svg)


if __name__ == "__main__":
    make("logo.svg")

"""Draws the wiki logo (site/assets/img/logo.svg): chunky slanted block lettering with a deep extrusion and a black outline, in the style
of the game's title screen: a cracked-stone "MINECRAFT" over "INDUSTRIOUS GOLEMS", the second line tucked under the first's extrusion.
Pure SVG, generated so the lettering can be tweaked in one place."""
import math
import random

# 8 rows tall; 6 columns wide unless noted (M is 8). '#' is a block.
GLYPHS = {
    "I": ["####", "####", ".##.", ".##.", ".##.", ".##.", "####", "####"],
    "T": ["#####", "#####", ".###.", ".###.", ".###.", ".###.", ".###.", ".###."],
    "E": ["#####", "#####", "##...", "####.", "####.", "##...", "#####", "#####"],
    "C": [".####", "#####", "##...", "##...", "##...", "##...", "#####", ".####"],
    "N": ["##.##", "###.#", "#####", "#####", "#.###", "##.##", "##.##", "##.##"],
    "M": ["##...##", "###.###", "#######", "#######", "##.#.##", "##...##", "##...##", "##...##"],
    "R": ["####.", "#####", "##.##", "#####", "####.", "####.", "##.##", "##.##"],
    "A": [".###.", "#####", "##.##", "##.##", "#####", "#####", "##.##", "##.##"],
    "F": ["#####", "#####", "##...", "###..", "###..", "##...", "##...", "##..."],
    "D": ["####.", "#####", "##.##", "##.##", "##.##", "##.##", "#####", "####."],
    "U": ["##.##", "##.##", "##.##", "##.##", "##.##", "##.##", "#####", ".###."],
    "S": [".####", "#####", "##...", ".###.", "..###", "...##", "#####", "####."],
    "O": [".###.", "#####", "##.##", "##.##", "##.##", "##.##", "#####", ".###."],
    "G": [".####", "#####", "##...", "##.##", "##.##", "##.##", "#####", ".####"],
    "L": ["##...", "##...", "##...", "##...", "##...", "##...", "#####", "#####"],
}
GAP = 1          # blank columns between letters: the lettering is tight, as in the game's logo
SPACE = 4        # columns for a word space
SLANT = 11.0      # degrees the whole lettering leans
ASPECT = 1.1    # block width / height


def layout(text):
    """[(word, {(col, row)}, start_col)] and the line's width in columns."""
    words, x = [], 0
    for w in text.split(" "):
        blocks, start = set(), x
        for ch in w:
            g = GLYPHS[ch]
            for r, row in enumerate(g):
                for c, v in enumerate(row):
                    if v == "#":
                        blocks.add((x + c, r))
            x += len(g[0]) + GAP
        x -= GAP
        words.append((w, blocks, start))
        x += SPACE
    return words, x - SPACE


def runs(blocks):
    """Merge blocks into horizontal runs: [(col, row, length)]."""
    out = []
    for r in sorted({b[1] for b in blocks}):
        cols = sorted(c for c, rr in blocks if rr == r)
        start = prev = cols[0]
        for c in cols[1:]:
            if c != prev + 1:
                out.append((start, r, prev - start + 1))
                start = c
            prev = c
        out.append((start, r, prev - start + 1))
    return out


def hull(points):
    pts = sorted(set(points))

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


def poly(points):
    return '<polygon points="%s"/>' % " ".join("%.1f,%.1f" % p for p in points)


class Line:
    """One line of lettering at a position: blocks of height bh (width bh * ASPECT), extruded by `depth` block heights."""

    def __init__(self, text, ox, oy, bh, depth, style, aspect=ASPECT):
        self.words, self.cols = layout(text)
        self.ox, self.oy, self.bh, self.bw = ox, oy, bh, bh * aspect
        self.dx, self.dy = depth[0] * bh, depth[1] * bh
        self.style = style
        self.all = set().union(*[w[1] for w in self.words])

    def rect(self, c, r, n=1):
        return (self.ox + c * self.bw, self.oy + r * self.bh, n * self.bw, self.bh)

    def sweep(self, c, r, n, grow=0.0):
        x, y, w, h = self.rect(c, r, n)
        x, y, w, h = x - grow, y - grow, w + 2 * grow, h + 2 * grow
        pts = [(x, y), (x + w, y), (x, y + h), (x + w, y + h),
               (x + self.dx, y + self.dy), (x + w + self.dx, y + self.dy), (x + self.dx, y + h + self.dy), (x + w + self.dx, y + h + self.dy)]
        return hull(pts)

    def outline(self):
        o = 0.28 * self.bh
        shapes = "".join(poly(self.sweep(c, r, n, o)) for (c, r, n) in runs(self.all))
        return '<g fill="#000">%s</g>' % shapes

    def side(self):
        out = []
        for name, blocks, start in self.words:
            out.append('<g fill="url(#%s)">%s</g>' % (self.style[name]["side"], "".join(poly(self.sweep(c, r, n)) for (c, r, n) in runs(blocks))))
        return "".join(out)

    def face(self):
        out = []
        for name, blocks, start in self.words:
            rects = "".join('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f"/>' % (x, y, w + 0.8, h + 0.8)
                            for (x, y, w, h) in (self.rect(c, r, n) for (c, r, n) in runs(blocks)))
            out.append('<g fill="url(#%s)">%s</g>' % (self.style[name]["face"], rects))
        hi, lo = [], []
        t = self.bh * 0.2
        for (c, r) in sorted(self.all):
            x, y, w, h = self.rect(c, r)
            if (c, r - 1) not in self.all:
                hi.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f"/>' % (x, y, w + 0.8, t))
            if (c, r + 1) not in self.all:
                lo.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f"/>' % (x, y + h - t, w + 0.8, t))
        out.append('<g fill="#ffffff70">%s</g><g fill="#00000050">%s</g>' % ("".join(hi), "".join(lo)))
        return "".join(out)

    def cracks(self, rng, count):
        """Thin cracks that wander inside the lettering (stone only)."""
        out = []
        cells = sorted(self.all)
        for _ in range(count):
            c, r = rng.choice(cells)
            x = self.ox + (c + rng.random()) * self.bw
            y = self.oy + (r + rng.random()) * self.bh
            pts = [(x, y)]
            ang = rng.random() * math.tau
            for _ in range(rng.randrange(2, 5)):
                ang += rng.uniform(-1.1, 1.1)
                length = rng.uniform(0.5, 1.1) * self.bh
                nx, ny = x + math.cos(ang) * length, y + math.sin(ang) * length
                cc, rr = int((nx - self.ox) // self.bw), int((ny - self.oy) // self.bh)
                if (cc, rr) not in self.all:
                    break
                x, y = nx, ny
                pts.append((x, y))
            if len(pts) > 1:
                d = "M" + " L".join("%.1f %.1f" % p for p in pts)
                out.append('<path d="%s" stroke="#00000070" stroke-width="%.1f"/>' % (d, 0.09 * self.bh))
                out.append('<path d="%s" transform="translate(%.1f %.1f)" stroke="#ffffff70" stroke-width="%.1f"/>' % (d, 0.07 * self.bh, 0.09 * self.bh, 0.06 * self.bh))
        return '<g fill="none" stroke-linecap="round" stroke-linejoin="round">%s</g>' % "".join(out)


def make(path, only_top=False):
    rng = random.Random(11)
    u = 12.0                                    # block height of the top line
    top, bottom = "MINECRAFT", "INDUSTRIOUS GOLEMS"
    _, c1 = layout(top)
    _, c2 = layout(bottom)
    w1 = c1 * u * ASPECT
    asp2 = 0.78                                 # the second line has narrower, taller blocks so it stays readable
    u2 = w1 / (c2 * asp2)                       # and is as wide as the first
    depth1, depth2 = (-1.0, 3.4), (-0.9, 2.8)
    pad = 20
    shear = math.tan(math.radians(SLANT))
    y_top = pad
    line1 = Line(top, pad + 1.0 * u, y_top, u, depth1, {"MINECRAFT": {"face": "stone", "side": "stoneSide"}})
    y2 = y_top + 8 * u + 3.1 * u                # the second line starts inside the first's extrusion
    line2 = Line(bottom, pad + 1.0 * u, y2, u2, depth2, {"INDUSTRIOUS": {"face": "amber", "side": "amberSide"}, "GOLEMS": {"face": "grass", "side": "grassSide"}}, aspect=asp2)
    H = (y_top + 8 * u + line1.dy + pad) if only_top else y2 + 8 * u2 + line2.dy + pad
    W = w1 + 2 * pad + 2 * u + shear * H
    face1 = (y_top, y_top + 8 * u)
    ext1 = (y_top + 8 * u, y_top + 8 * u + line1.dy)
    face2 = (y2, y2 + 8 * u2)
    ext2 = (y2 + 8 * u2, y2 + 8 * u2 + line2.dy)

    def grad(i, y0, y1, stops):
        return '<linearGradient id="%s" gradientUnits="userSpaceOnUse" x1="0" y1="%.1f" x2="0" y2="%.1f">%s</linearGradient>' % (
            i, y0, y1, "".join('<stop offset="%s" stop-color="%s"/>' % s for s in stops))
    defs = "".join([
        grad("stone", *face1, [(0, "#d9d5ce"), (0.55, "#c2bdb6"), (1, "#a8a39c")]),
        grad("stoneSide", face1[0], ext1[1], [(0, "#0e0e0e"), ("%.3f" % ((face1[1] - face1[0]) / (ext1[1] - face1[0]) - 0.001), "#0e0e0e"), ("%.3f" % ((face1[1] - face1[0]) / (ext1[1] - face1[0])), "#707070"), (1, "#0a0a0a")]),
        grad("amber", *face2, [(0, "#d8883a"), (0.45, "#f0b04a"), (1, "#ffe27a")]),
        grad("amberSide", face2[1] - u2, ext2[1] + u2, [(0, "#3a1d12"), (1, "#6a4020")]),
        grad("grass", *face2, [(0, "#9fd36a"), (0.5, "#c7ec86"), (1, "#e6f7a8")]),
        grad("grassSide", face2[1] - u2, ext2[1] + u2, [(0, "#233a14"), (1, "#4c6a28")]),
    ])
    # the second line is drawn first so the first line's extrusion lies over its top, as in the game's logo
    body = ('' if only_top else line2.outline() + line2.side() + line2.face()) + (line1.outline() + line1.side() + line1.face() + line1.cracks(rng, 34))
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %.0f %.0f" role="img" aria-label="Industrious Golems">'
           '<title>Industrious Golems</title><defs>%s</defs>'
           '<g transform="translate(%.1f 0) skewX(%.1f)">%s</g></svg>') % (W, H, defs, shear * H, -SLANT, body)
    with open(path, "w", encoding="utf-8") as f:
        f.write(svg)


if __name__ == "__main__":
    make("logo.svg")
    make("minecraft.svg", True)

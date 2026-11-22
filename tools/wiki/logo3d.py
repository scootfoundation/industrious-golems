"""Wiki logo as a real 3D scene: the lettering lies on a slab seen in perspective (a top face in lit stone, dark front sides below it,
black outline), like the game's title screen. Pure SVG output. See make()."""
import math
import random
from logo import GLYPHS, layout, runs

PITCH = 52.0      # camera elevation above the slab, degrees
YAW = 0.0
CAM_X = 0.78      # camera position along the text, as a fraction of its width
DIST = 1.15       # camera distance, in text widths


class Cam:
    def __init__(self, width, centre):
        e = math.radians(PITCH)
        self.se, self.ce, self.yaw = math.sin(e), math.cos(e), math.radians(YAW)
        self.cx, self.cz = CAM_X * width, centre[1]
        self.d = DIST * width
        self.F = self.d

    def __call__(self, x, y, z):
        x, z = x - self.cx, z - self.cz
        x, z = x * math.cos(self.yaw) + z * math.sin(self.yaw), -x * math.sin(self.yaw) + z * math.cos(self.yaw)
        depth = self.d - self.se * y - self.ce * z
        sx = x
        sy = y * self.ce - z * self.se
        k = self.F / depth
        return (sx * k, -sy * k)


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def hexc(c):
    return "#%02x%02x%02x" % tuple(int(max(0, min(255, round(v)))) for v in c)


def build(lines, cam, rng):
    """lines: [dict(text, bw, bh, z0, y0, depth, top=(c0,c1), side=(c0,c1) or per-word colours)]. Returns svg body + point list."""
    outline, fills, extras, pts = [], [], [], []
    ox = 0.0
    for ln in lines:
        words, cols = layout(ln["text"])
        ln["cols"] = cols
        ln["words"] = words
    for ln in lines:
        bw, bh, z0, y0, D = ln["bw"], ln["bh"], ln["z0"], ln["y0"], ln["depth"]
        allb = set().union(*[w[1] for w in ln["words"]])
        faces = []

        def P(c, y, r):
            return cam(c * bw, y, z0 + r * bh)

        for wi, (name, blocks, start) in enumerate(ln["words"]):
            colours = ln["colours"][name]
            for (c, r, n) in runs(blocks):
                t = 0.15 + 0.5 * (r + 0.5) / 8.0
                topc = hexc(lerp(colours[0], colours[1], t))
                quad = [P(c, y0, r), P(c + n, y0, r), P(c + n, y0, r + 1), P(c, y0, r + 1)]
                key = (r, -abs(c + n / 2 - cam.cx / bw), 1)
                # sides, per cell so the painter's order is right
                for cc in range(c, c + n):
                    kc = (r, -abs(cc + 0.5 - cam.cx / bw), 0)
                    if (cc, r + 1) not in blocks:
                        q = [P(cc, y0, r + 1), P(cc + 1, y0, r + 1), P(cc + 1, y0 - D, r + 1), P(cc, y0 - D, r + 1)]
                        faces.append((kc, q, ("g", colours[2], colours[3]), 0))
                    if cc == c and (cc - 1, r) not in blocks and (cc) * bw > cam.cx:
                        q = [P(cc, y0, r), P(cc, y0, r + 1), P(cc, y0 - D, r + 1), P(cc, y0 - D, r)]
                        faces.append((kc, q, ("f", hexc(lerp(colours[2], colours[3], 0.55))), 0))
                    if cc == c + n - 1 and (cc + 1, r) not in blocks and (cc + 1) * bw < cam.cx:
                        q = [P(cc + 1, y0, r), P(cc + 1, y0, r + 1), P(cc + 1, y0 - D, r + 1), P(cc + 1, y0 - D, r)]
                        faces.append((kc, q, ("f", hexc(lerp(colours[2], colours[3], 0.55))), 0))
                faces.append((key, quad, ("f", topc), (c, r, n)))
        faces.sort(key=lambda f: f[0])
        ln["faces"] = faces
        ln["allb"] = allb
        for f in faces:
            pts.extend(f[1])
    return pts


def poly_pts(q):
    return " ".join("%.1f,%.1f" % p for p in q)


def make(path, only_top=False):
    rng = random.Random(11)
    top_t, bot_t = "MINECRAFT", "INDUSTRIOUS GOLEMS"
    _, c1 = layout(top_t)
    _, c2 = layout(bot_t)
    bw1, bh1 = 1.0, 1.5
    W = c1 * bw1
    bw2 = W / c2
    bh2 = bh1 * 0.62
    D1, D2 = 3.4 * bh1, 1.5 * bh1
    stone = [(214, 210, 203), (186, 181, 174), (96, 96, 96), (8, 8, 8)]
    amber = [(236, 160, 70), (255, 232, 130), (80, 46, 20), (22, 12, 6)]
    grass = [(150, 205, 100), (230, 248, 170), (50, 82, 28), (12, 24, 6)]
    cam = Cam(W, (0, 4 * bh1))
    lines = []
    if not only_top:
        lines.append(dict(text=bot_t, bw=bw2, bh=bh2, z0=8 * bh1 + 1.9 * bh1, y0=0.0, depth=D2,
                          colours={"INDUSTRIOUS": amber, "GOLEMS": grass}))
    lines.append(dict(text=top_t, bw=bw1, bh=bh1, z0=0, y0=0, depth=D1, colours={"MINECRAFT": stone}))
    pts = build(lines, cam, rng)
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    pad = 0.04 * W
    minx, maxx, miny, maxy = min(xs) - pad, max(xs) + pad, min(ys) - pad, max(ys) + pad
    sw = 0.05 * bh1 * cam.F / cam.d * 1.4 * 8
    body, defs, n = [], [], 0
    for ln in lines:
        outl = "".join('<polygon points="%s"/>' % poly_pts(f[1]) for f in ln["faces"])
        body.append('<g fill="#000" stroke="#000" stroke-width="%.1f" stroke-linejoin="round">%s</g>' % (sw * 2, outl))
        fl = []
        for key, q, fill, meta in ln["faces"]:
            if fill[0] == "g":
                n += 1
                gid = "g%d" % n
                defs.append('<linearGradient id="%s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="%s"/><stop offset="1" stop-color="%s"/></linearGradient>' % (gid, hexc(fill[1]), hexc(fill[2])))
                fl.append('<polygon points="%s" fill="url(#%s)" stroke="url(#%s)" stroke-width="0.6"/>' % (poly_pts(q), gid, gid))
            else:
                fl.append('<polygon points="%s" fill="%s" stroke="%s" stroke-width="0.6"/>' % (poly_pts(q), fill[1], fill[1]))
        body.append("".join(fl))
    # cracks + edge shading on the top line's top face
    ln = lines[-1]
    bw, bh = ln["bw"], ln["bh"]
    cells = sorted(ln["allb"])
    cr = []
    for _ in range(34):
        c, r = rng.choice(cells)
        x, y = c + rng.random(), r + rng.random()
        ptsl = [(x, y)]
        ang = rng.random() * math.tau
        for _ in range(rng.randrange(2, 5)):
            ang += rng.uniform(-1.1, 1.1)
            L = rng.uniform(0.4, 0.9)
            nx, ny = x + math.cos(ang) * L / 1.0, y + math.sin(ang) * L / 1.15
            if (int(nx), int(ny)) not in ln["allb"]:
                break
            x, y = nx, ny
            ptsl.append((x, y))
        if len(ptsl) > 1:
            d = "M" + " L".join("%.1f %.1f" % cam(px * bw, 0, py * bh) for px, py in ptsl)
            cr.append('<path d="%s" stroke="#00000080" stroke-width="%.1f"/>' % (d, sw * 0.22))
            cr.append('<path d="%s" transform="translate(%.1f %.1f)" stroke="#ffffff90" stroke-width="%.1f"/>' % (d, sw * 0.15, sw * 0.2, sw * 0.14))
    near = []
    for (c, r) in cells:
        if (c, r + 1) not in ln["allb"]:
            a, b = cam(c * bw, 0, (r + 1) * bh), cam((c + 1) * bw, 0, (r + 1) * bh)
            near.append('<path d="M%.1f %.1f L%.1f %.1f"/>' % (a + b))
    body.append('<g fill="none" stroke-linecap="round" stroke-linejoin="round">%s</g>' % "".join(cr))
    body.append('<g fill="none" stroke="#00000055" stroke-width="%.1f">%s</g>' % (sw * 0.4, "".join(near)))
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="%.0f %.0f %.0f %.0f" role="img" aria-label="Industrious Golems">'
           '<title>Industrious Golems</title><defs>%s</defs>%s</svg>') % (minx, miny, maxx - minx, maxy - miny, "".join(defs), "".join(body))
    with open(path, "w", encoding="utf-8") as f:
        f.write(svg)


if __name__ == "__main__":
    make("logo.svg")

"""Software renderer for the wiki: golems, chests and inventory-style item icons.

Pure PIL + numpy, orthographic, nearest-neighbour texturing so the pixel art stays crisp.
  - golems are built from the box lists in client/GolemModels.java (parsed, so they cannot drift from the game)
  - chests use the vanilla chest boxes with the mod's chest textures
  - items are read from the Minecraft client jar's item definitions and models (a flat sprite, or an isometric block)
Usage: see build.py, which calls these.
"""
import json
import math
import re
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
MOD_ASSETS = ROOT / "src/main/resources/assets/industriousgolems"
GOLEM_MODELS = ROOT / "src/main/java/com/scottcaldwell/industriousgolems/client/GolemModels.java"

# face shading, like the game's: top brightest, sides darker
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


# ---------------------------------------------------------------- the rasteriser

class Scene:
    """Faces (origin, u edge, v edge, texture, rect, shade) in a y-up world; rendered through a yaw/pitch camera."""

    def __init__(self):
        self.faces = []

    def quad(self, o, a, b, tex, rect, shade, flip_u=False, flip_v=False):
        self.faces.append((np.array(o, float), np.array(a, float), np.array(b, float), tex, rect, shade, flip_u, flip_v))

    def cube(self, x0, y0, z0, x1, y1, z1, faces):
        """faces: {name: (texture image, (u0, v0, u1, v1) in texture pixels)}; world is y-up, front is -z.

        u/v run the way the game's do: on the north face u rises with x, on east with z, on south against x, on west against z.
        """
        w, h, d = x1 - x0, y1 - y0, z1 - z0
        spec = {
            "north": ((x0, y1, z0), (w, 0, 0), (0, -h, 0)),
            "south": ((x1, y1, z1), (-w, 0, 0), (0, -h, 0)),
            "east": ((x1, y1, z0), (0, 0, d), (0, -h, 0)),
            "west": ((x0, y1, z1), (0, 0, -d), (0, -h, 0)),
            "up": ((x0, y1, z0), (w, 0, 0), (0, 0, d)),
            "down": ((x0, y0, z1), (w, 0, 0), (0, 0, -d)),
        }
        for name, (tex, rect) in faces.items():
            o, a, b = spec[name]
            self.quad(o, a, b, tex, rect, SHADE[name])

    def render(self, size, yaw=35.0, pitch=25.0, scale=None, pad=0.04, fixed=None):
        """Returns an RGBA image. `fixed` = (scale, cx, cy) to share a frame between renders."""
        cy_, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
        cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

        def proj(p):
            x, y, z = p
            x2, z2 = x * cy_ + z * sy_, -x * sy_ + z * cy_
            return np.array([x2, y * cp + z2 * sp, -y * sp + z2 * cp])  # screen x, screen y (up), depth

        P = []
        for o, a, b, tex, rect, shade, fu, fv in self.faces:
            P.append((proj(o), proj(o + a) - proj(o), proj(o + b) - proj(o), tex, rect, shade, fu, fv))
        pts = np.array([q for f in P for q in (f[0], f[0] + f[1], f[0] + f[2], f[0] + f[1] + f[2])])
        if fixed:
            scale, cx, cy = fixed
        else:
            lo, hi = pts[:, :2].min(0), pts[:, :2].max(0)
            scale = scale or (size * (1 - 2 * pad)) / max(hi[0] - lo[0], hi[1] - lo[1])
            cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
        img = np.zeros((size, size, 4), np.float32)
        zbuf = np.full((size, size), np.inf)
        for o, a, b, tex, rect, shade, fu, fv in P:
            sc = lambda v: np.array([(v[0] - cx) * scale + size / 2, size / 2 - (v[1] - cy) * scale])
            so, sa, sb = sc(o), sc(o + a) - sc(o), sc(o + b) - sc(o)
            det = sa[0] * sb[1] - sa[1] * sb[0]
            if abs(det) < 1e-6:
                continue
            corners = np.array([so, so + sa, so + sb, so + sa + sb])
            x0, y0 = np.maximum(np.floor(corners.min(0)), 0).astype(int)
            x1, y1 = np.minimum(np.ceil(corners.max(0)), size - 1).astype(int)
            if x1 < x0 or y1 < y0:
                continue
            xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            dx, dy = xs - so[0], ys - so[1]
            s = (dx * sb[1] - dy * sb[0]) / det
            t = (sa[0] * dy - sa[1] * dx) / det
            inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
            if not inside.any():
                continue
            u0, v0, u1, v1 = rect
            if fu:
                s = 1 - s
            if fv:
                t = 1 - t
            tx = np.clip((u0 + s * (u1 - u0)).astype(int), 0, tex.width - 1)
            ty = np.clip((v0 + t * (v1 - v0)).astype(int), 0, tex.height - 1)
            arr = np.asarray(tex)
            px = arr[ty, tx].astype(np.float32)
            depth = o[2] + s * a[2] + t * b[2]
            ok = inside & (px[..., 3] > 8) & (depth < zbuf[y0:y1 + 1, x0:x1 + 1] - 1e-4)
            if not ok.any():
                continue
            px[..., :3] *= shade
            sub = img[y0:y1 + 1, x0:x1 + 1]
            sub[ok] = px[ok]
            zbuf[y0:y1 + 1, x0:x1 + 1][ok] = depth[ok]
        return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA"), (scale, cx, cy)


def box_faces(tex, u, v, w, h, d):
    """The game's cube-net layout: faces of a w x h x d box with its net at (u, v)."""
    return {
        "up": (tex, (u + d, v, u + d + w, v + d)),
        "down": (tex, (u + d + w, v, u + d + 2 * w, v + d)),
        "west": (tex, (u, v + d, u + d, v + d + h)),       # the model's -x side
        "north": (tex, (u + d, v + d, u + d + w, v + d + h)),
        "east": (tex, (u + d + w, v + d, u + 2 * d + w, v + d + h)),
        "south": (tex, (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)),
    }


# ---------------------------------------------------------------- golems

def parse_golem(name):
    """The parts of GolemModels.<name>(): [(path, [(u, v, x, y, z, w, h, d, grow)], (px, py, pz))] with y down."""
    src = GOLEM_MODELS.read_text()
    body = re.search(r"static LayerDefinition %s\(\) \{(.*?)return LayerDefinition" % name, src, re.S).group(1)
    parts = {}
    for m in re.finditer(r'PartDefinition (\w+) = (\w+)\.addOrReplaceChild\("(\w+)", CubeListBuilder\.create\(\)(.*?)PartPose\.offset\(([^)]*)\)\);', body, re.S):
        var, parent, pname, cubes, pose = m.groups()
        boxes = []
        for c in re.finditer(r"texOffs\((\S+?), (\S+?)\)\.addBox\(([^,]+), ([^,]+), ([^,]+), ([^,]+), ([^,]+), ([^,]+), new CubeDeformation\(([^)]*)\)\)", cubes):
            n = [float(g.rstrip("F")) for g in c.groups()]
            boxes.append(tuple(n))
        parts[var] = (parent, boxes, tuple(float(x.strip().rstrip("F")) for x in pose.split(",")))
    return parts


def golem_scene(name, texture, offset=(0, 0, 0)):
    tex = Image.open(texture).convert("RGBA")
    parts = parse_golem(name)
    origin = {"root": np.array([0.0, 24.0, 0.0])}
    for var, (parent, boxes, pose) in parts.items():
        parent_name = parent if parent == "root" else parent
        base = origin.get(parent_name, origin["root"])
        pos = base + np.array(pose)
        origin[var] = pos
    scene = Scene()
    for var, (parent, boxes, pose) in parts.items():
        pos = origin[var]
        for (u, v, x, y, z, w, h, d, grow) in boxes:
            # model space is y-down with feet at y = 24 -> y-up world
            wx0, wx1 = pos[0] + x - grow, pos[0] + x + w + grow
            wy0, wy1 = 24 - (pos[1] + y + h) - grow, 24 - (pos[1] + y) + grow
            wz0, wz1 = pos[2] + z - grow, pos[2] + z + d + grow
            scene.cube(wx0 + offset[0], wy0 + offset[1], wz0 + offset[2], wx1 + offset[0], wy1 + offset[1], wz1 + offset[2],
                       box_faces(tex, u, v, w, h, d))
    return scene


def chest_scene(texture, lid_open=0.0):
    return chest_scene_from(Image.open(texture).convert("RGBA"))


def chest_scene_from(tex):
    s = Scene()
    # vanilla chest boxes (see GolemChestRenderer): base 14x10x14, lid 14x5x14 on top, 2x4x1 latch on the front
    s.cube(1, 0, 1, 15, 10, 15, box_faces(tex, 0, 19, 14, 10, 14))
    s.cube(1, 10, 1, 15, 15, 15, box_faces(tex, 0, 0, 14, 5, 14))
    s.cube(7, 8, 0, 9, 12, 1, box_faces(tex, 0, 0, 2, 4, 1))
    return s


# ---------------------------------------------------------------- vanilla items

class Vanilla:
    def __init__(self, jar):
        self.zip = zipfile.ZipFile(jar)
        self.names = set(self.zip.namelist())
        self._tex = {}

    def json(self, path):
        return json.loads(self.zip.read(path))

    def texture(self, ref):
        ref = ref.replace("minecraft:", "")
        if ref not in self._tex:
            p = "assets/minecraft/textures/%s.png" % ref
            if p not in self.names:
                raise KeyError(p)
            img = Image.open(self.zip.open(p)).convert("RGBA")
            if img.height > img.width:  # an animation strip: first frame
                img = img.crop((0, 0, img.width, img.width))
            self._tex[ref] = img
        return self._tex[ref]

    def item_model(self, item):
        node = self.json("assets/minecraft/items/%s.json" % item)["model"]
        while node["type"].endswith("model") is False:
            if "on_false" in node:
                node = node["on_false"]
            elif "fallback" in node:
                node = node["fallback"]
            elif "models" in node:
                node = node["models"][0]
            elif "cases" in node:
                node = node["cases"][0]["model"]
            else:
                node = node["model"]
        return node["model"].replace("minecraft:", "")

    def resolve(self, model):
        """The merged model: (elements, textures dict, root builtin) following the parent chain."""
        chain, textures, elements, builtin = [], {}, None, None
        while model:
            data = self.json("assets/minecraft/models/%s.json" % model)
            chain.append(data)
            parent = data.get("parent")
            if parent and parent.startswith("builtin/"):
                builtin = parent
                break
            model = parent.replace("minecraft:", "") if parent else None
        for data in reversed(chain):
            textures.update(data.get("textures", {}))
            if "elements" in data:
                elements = data["elements"]
        return elements, textures, builtin

    def tex_ref(self, textures, ref):
        while ref.startswith("#"):
            ref = textures[ref[1:]]
        return ref


def item_scene(vanilla, item, tints=None):
    """Either ('sprite', [layers]) for a flat item or ('block', Scene) for a model with elements."""
    if item == "chest":  # a special model: the vanilla chest boxes with its entity texture
        return "block", chest_scene_from(vanilla.texture("entity/chest/normal"))
    model = vanilla.item_model(item)
    elements, textures, builtin = vanilla.resolve(model)
    if elements is None:
        layers = []
        for i in range(4):
            key = "layer%d" % i
            if key in textures:
                layers.append(vanilla.texture(vanilla.tex_ref(textures, textures[key])))
        return "sprite", layers
    scene = Scene()
    for el in elements:
        f, t = el["from"], el["to"]
        faces = {}
        for name, face in el["faces"].items():
            tex = vanilla.texture(vanilla.tex_ref(textures, face["texture"]))
            u0, v0, u1, v1 = [c * tex.width / 16 for c in face.get("uv", [0, 0, 16, 16])]
            faces[name] = (tex, (u0, v0, u1, v1))
        rot = el.get("rotation")
        if rot and rot.get("angle", 0) % 90 != 0 and False:
            pass
        scene.cube(f[0], f[1], f[2], t[0], t[1], t[2], faces)
    return "block", scene


# ---------------------------------------------------------------- inventory slots

SCALE = 3  # screen pixels per game pixel
_CACHE = {}


def slot_icon(vanilla, item, tint=None):
    """An item as the inventory shows it: its sprite, or the block's isometric render, on a Minecraft slot (18x18 game px at SCALE)."""
    kind, data = item_scene(vanilla, item)
    side = 18 * SCALE
    slot = Image.new("RGBA", (18, 18), (139, 139, 139, 255))
    px = slot.load()
    for i in range(18):
        for j in range(18):
            if i == 0 or j == 0:
                px[i, j] = (55, 55, 55, 255)       # the inset's dark top-left edge
            if i == 17 or j == 17:
                px[i, j] = (255, 255, 255, 255)    # and its light bottom-right edge
    slot = slot.resize((side, side), Image.NEAREST)
    if kind == "sprite":
        icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for layer in data:
            icon.alpha_composite(layer if layer.size == (16, 16) else layer.resize((16, 16), Image.NEAREST))
        if tint:
            icon = tinted(icon, tint)
        icon = icon.resize((16 * SCALE, 16 * SCALE), Image.NEAREST)
    else:
        icon, _ = data.render(16 * SCALE, yaw=-45, pitch=30, pad=0.0)
    slot.alpha_composite(icon, (SCALE, SCALE))
    return slot


def tinted(img, rgb):
    a = np.asarray(img).astype(np.float32)
    a[..., :3] *= np.array(rgb) / 255.0
    return Image.fromarray(a.astype(np.uint8), "RGBA")

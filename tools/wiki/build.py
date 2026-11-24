"""Builds the wiki in site/ (the GitHub Pages site): renders, item icons and the HTML pages.

    python tools/wiki/build.py [--jar path/to/minecraft client.jar]

The pages are generated from the data in this file (golem facts) plus the mod's own files (textures, GolemModels.java), so the
renders cannot drift from the game; the facts below are written by hand and must be kept in step with the code (CLAUDE.md).
The jar is the vanilla Minecraft client the dev environment already downloaded (found in the Gradle cache); its item sprites
are only used to draw the small item icons.
"""
import argparse
import html
import json
import sys
from pathlib import Path

import render as r
from PIL import Image

SITE = r.ROOT / "site"
REPO = "https://github.com/scootfoundation/industrious-golems"
MCWIKI = "https://minecraft.wiki/w/"
LANG_NAMES = {}


def find_jar():
    base = Path.home() / ".gradle/caches/ng_execute"
    jars = sorted((j for j in base.glob("*/client.jar")), key=lambda j: j.stat().st_size, reverse=True)
    for j in jars:
        import zipfile
        names = zipfile.ZipFile(j).namelist()
        if "assets/minecraft/items/slime_ball.json" in names and "assets/minecraft/items/cactus_flower.json" in names:
            return j
    sys.exit("no Minecraft client jar found: pass --jar")


# ------------------------------------------------------------------ item catalogue

# id -> (display name, minecraft.wiki page). Only what differs from the jar's name needs a page; the name comes from the jar's lang file.
PAGE = {
    "oak_log": "Log", "oak_sapling": "Sapling", "white_wool": "Wool", "snort_pottery_sherd": "Pottery_Sherd",
    "red_mushroom_block": "Mushroom_Block", "terracotta": "Terracotta", "disc_fragment_5": "Disc_Fragment_5",
    "carved_pumpkin": "Carved_Pumpkin", "jack_o_lantern": "Jack_o'Lantern", "hay_block": "Hay_Bale",
    "cobblestone": "Cobblestone", "granite": "Granite", "pumpkin": "Pumpkin", "powder_snow_bucket": "Bucket",
    "milk_bucket": "Milk_Bucket", "goat_horn": "Goat_Horn", "wheat_seeds": "Wheat_Seeds",
    "cobbled_deepslate": "Cobbled_Deepslate",
}
LABEL = {"oak_log": "Any log", "oak_sapling": "Sapling", "white_wool": "Wool", "snort_pottery_sherd": "Any pottery sherd",
         "red_mushroom_block": "Red Mushroom Block", "wheat_seeds": "Seeds", "terracotta": "Terracotta (any colour)"}


def name(item):
    if item in LABEL:
        return LABEL[item]
    return LANG_NAMES.get("item.minecraft." + item) or LANG_NAMES.get("block.minecraft." + item) or item.replace("_", " ").title()


def page(item):
    return PAGE.get(item) or name(item).replace(" ", "_")


def slot(item, label=False, hint=None):
    """An inventory slot linking to the Minecraft wiki, with the game's tooltip; optionally with the name (and a hint) beside it."""
    n = html.escape(name(item))
    tip = '<span class="tip"><b>%s</b></span>' % n   # a tooltip is just the name; a hint is shown beside the slot instead
    s = ('<a class="invslot" href="%s%s"><img src="{root}assets/items/%s.png" alt="%s">%s</a>' % (MCWIKI, page(item), item, n, tip))
    if not label:
        return s
    extra = '<span class="lore">%s</span>' % html.escape(hint) if hint else ""
    return ('<span class="entry">%s<span class="entry-text"><a href="%s%s">%s</a>%s</span></span>'
            % (s, MCWIKI, page(item), n, extra))


def panel(title, inner, cls=""):
    """An inventory screen: the grey bevelled panel with its title, like the crafting table, around a row or grid of slots."""
    return '<div class="mcpanel %s"><div class="craft-title">%s</div>%s</div>' % (cls, title, inner)


def wiki(text, item=None, page_name=None):
    return '<a href="%s%s">%s</a>' % (MCWIKI, page_name or page(item), text)


# ------------------------------------------------------------------ the golems

GOLEMS = [
    dict(
        id="hay", slug="hay-golem", title="Hay Golem", role="Farmer", tagline="A slow, patient scarecrow that tends your crops.",
        texture="hay_golem", chest="hay", job="Hay Chest", body="hay_block", build="a <b>carved pumpkin</b> on a <b>fence</b> (any kind) on a <b>hay bale</b>", job_vanilla="composter",
        recipe=["chest", "composter", "hay_block"],
        consumes=[("wheat_seeds", "Seeds, carrots, potatoes and the other plantable crops")],
        produces=["wheat", "carrot", "potato", "beetroot", "melon_slice", "pumpkin", "sweet_berries"],
        offerings=[("slime_ball", "slimes"), ("glow_ink_sac", "glow squid"), ("pufferfish", "warm oceans"), ("cobweb", "mineshafts"),
                   ("rabbit_foot", "rabbits"), ("cactus_flower", "cacti"), ("pointed_dripstone", "dripstone caves"),
                   ("glow_berries", "lush caves")],
        summary="The Hay Golem is the farmer. It harvests ripe crops from soil you have tilled and sows the next generation, "
                "carrying the harvest to its Hay Chest one stack at a time.",
        behaviour=[
            "It works the tilled farmland within <strong>10 blocks</strong> of its chest (and two blocks up and down). It never tills soil itself: "
            "you prepare the plots and plant the first generation.",
            "It walks onto a ripe crop and breaks it, then onto the bare farmland to sow whatever seeds it is holding. It will not sow things that are not seeds.",
            "It is deliberately slow: it plods about at well under the pace of the other golems.",
            "Seeds come from the chest. When it runs out it waits, so keep some seeds in the Hay Chest.",
        ],
        notes=["Hoppers and water streams work fine around it: it only ever picks up the crops it works with.",
               "The vanilla composter still counts as its job site if you have no Hay Chest."],
        tips=[],
    ),
    dict(
        id="mushroom", slug="mushroom-golem", title="Mushroom Golem", role="Lumberjack", tagline="Fells trees and replants the grove as it goes.",
        texture="mushroom_golem", chest="mushroom", job="Mushroom Chest", body="red_mushroom_block", build="a <b>carved pumpkin</b> on a <b>red mushroom block</b>", job_vanilla="fletching_table",
        recipe=["chest", "fletching_table", "red_mushroom_block"],
        consumes=[("oak_sapling", "Saplings, a few at a time")],
        produces=["oak_log", "oak_sapling", "stick", "apple"],
        offerings=[("amethyst_shard", "geodes"), ("resin_clump", "pale gardens"), ("spore_blossom", "lush caves"), ("pink_petals", "cherry groves"),
                   ("goat_horn", "goats"), ("armadillo_scute", "armadillos"), ("honey_bottle", "bee nests"), ("open_eyeblossom", "pale gardens")],
        summary="The Mushroom Golem is the lumberjack. It fells the natural trees around its chest, log by log, and plants saplings "
                "so the grove keeps growing.",
        behaviour=[
            "It looks for trees within <strong>16 blocks</strong> of its chest and fells them log by log, including branches and extra trunks. "
            "Logs, sticks, apples and saplings are carried to the chest.",
            "It fells a tree from the top: it <strong>sneezes</strong> up onto the crown, then breaks the tree's logs within <strong>3 blocks</strong> of it before the block under its feet, so a big, thick trunk comes down in one go. "
            "It cannot be hurt by the drop, since a Mushroom Golem <strong>takes no fall damage</strong>.",
            "If it ever gets stuck (say after the leaves it cleared leave it boxed in, so it can neither reach a tree nor get back to its chest), it uses the same sneeze to hop a few blocks toward where it is headed, or toward its chest.",
            "It <strong>ignores logs you have built with</strong> (and stripped logs), so your house is safe.",
            "It plants saplings wherever the ground near the chest suits a tree best (it does not go back to where a tree stood), within <strong>14 blocks</strong> of the chest, keeping saplings at least <strong>5 blocks apart</strong> so trees have room. It plants before it fells: it keeps planting until there is no good ground left, then goes back to cutting. It "
            "leaves a <strong>6-block clearing</strong> around the chest so the chest never gets walled in.",
            "It only plants saplings it has been given or has collected: it never conjures them. A lone dark oak sapling is left alone, because it cannot grow without room for four.",
            "If the chest is full it sits down with the stack it is holding, and gets up again once there is room.",
        ],
        notes=["To start a grove, put a handful of saplings in the chest. The golem tops up its supply from there."],
        tips=[],
    ),
    dict(
        id="terracotta", slug="terracotta-golem", title="Terracotta Golem", role="Herder", tagline="Shears, milks and keeps your pens at a sensible size.",
        texture="terracotta_golem", chest="terracotta", job="Terracotta Chest", body="terracotta", build="a <b>carved pumpkin</b> on two <b>terracotta</b> (any kind, plain or coloured)", job_vanilla="loom",
        recipe=["chest", "loom", "terracotta"],
        consumes=[("bucket", "Buckets (each milking turns one into milk)"), ("wheat", "Animal feed")],
        produces=["white_wool", "milk_bucket", "beef", "porkchop", "chicken", "mutton", "leather", "feather", "egg"],
        offerings=[("turtle_scute", "baby turtles"), ("sea_pickle", "warm oceans"), ("cocoa_beans", "jungles"), ("mangrove_propagule", "mangrove swamps"),
                   ("powder_snow_bucket", "powder snow"), ("snort_pottery_sherd", "archaeology: any sherd works"), ("shroomlight", "the Nether"),
                   ("big_dripleaf", "lush caves")],
        summary="The Terracotta Golem is the herder. It shears sheep, milks cows and looks after the animals in the pens near its chest.",
        behaviour=[
            "It works the <strong>enclosed pens</strong> (up to 64 floor blocks) with animals in them within <strong>16 blocks</strong> of its chest. "
            "A pen with a gap in the fence is not a pen.",
            "It shears sheep and milks cows (a cow can be milked about every five minutes), turning each bucket from the chest into a bucket of milk.",
            "It keeps herds to a sensible size: it <strong>breeds</strong> a pen that has more than four floor blocks per animal, using feed from the chest, "
            "and <strong>culls</strong> one that is crowded below 3.5. It never culls a species below two adults.",
            "Wool, milk, meat, leather, feathers and eggs go to the chest.",
        ],
        notes=["Give it plenty of feed and buckets: without feed it cannot breed, without buckets it cannot milk."],
        tips=[],
    ),
    dict(
        id="stone", slug="stone-golem", title="Stone Golem", role="Miner", tagline="Digs a mineshaft, one block at a time.",
        texture="stone_golem", chest="stone", job="Stone Chest", body="smooth_stone", build="a <b>carved pumpkin</b> on a <b>T of smooth stone</b>, like an iron golem", job_vanilla="stonecutter",
        recipe=["chest", "stonecutter", "smooth_stone"],
        consumes=[("oak_fence", "Fences and planks (any wood) for braces"), ("torch", "Torches, one on each brace"), ("cobblestone", "Cobblestone, to floor over gaps and plug liquid")],
        produces=["cobblestone", "granite", "diorite", "andesite", "tuff", "cobbled_deepslate", "coal", "raw_iron", "raw_copper", "raw_gold", "redstone", "lapis_lazuli", "diamond", "emerald", "quartz", "amethyst_shard", "clay_ball", "calcite"],
        offerings=[("nautilus_shell", "drowned, fishing"), ("prismarine_crystals", "ocean monuments"), ("echo_shard", "ancient cities"), ("disc_fragment_5", "ancient cities"),
                   ("chorus_fruit", "the End"), ("phantom_membrane", "phantoms"), ("ghast_tear", "ghasts"), ("breeze_rod", "breezes")],
        summary="The Stone Golem is the miner. Put its chest on the floor where you want a mine and it will dig a hub room and four straight trunk tunnels out of it, propped and lit like a mineshaft.",
        behaviour=[
            "It digs a <strong>5 &times; 5 hub room</strong> around its chest, then a <strong>3 &times; 3 trunk</strong> out of the middle of each wall in all four directions, up to <strong>64 blocks</strong> each. Everything is cut from the chest's own level up; it never digs the floor.",
            "The first block of a trunk, and every fifth after it, is a <strong>mineshaft brace</strong>: a fence post each side, a plank beam across and a <strong>torch</strong> hung on the middle plank facing the chest, so the hub is lit by the first brace of each trunk. It digs up to a brace, builds it, and only then goes on.",
            "It has an <strong>inventory</strong>, shown as a sack on its back that fills up as it digs: <strong>right-click</strong> the golem to open it. Three slots are for <strong>fence posts, plank beams and torches</strong> (an empty one shows what belongs in it), and five hold what it digs, cobblestone included. Fill any of them by hand if you like; it also tops them up from its chest and puts its diggings away there, keeping 16 cobblestone for building.",
            "Out of fence posts, planks or torches, or with a full inventory and a full chest, it goes back to its chest and <strong>stamps and shakes its head</strong>: that means look in its inventory.",
            "Each time it breaks a block it picks up the drops within five blocks before it carries on.",
            "Short gaps in the floor (up to five blocks) are bridged with cobblestone. Water or lava that would flood the tunnel is <strong>plugged with cobblestone</strong>, and a plug in the way ends that trunk. A long drop or a block it does not dig ends a trunk too, and it leaves caves it breaks into open for you to explore.",
            "Sand and gravel overhead are dug out a block at a time as they fall into the tunnel.",
            "It never fights. A <strong>hostile mob</strong> within ten blocks (a creeper: sixteen) sends it running back to its chest to cower, arms over its head, shaking, until the mob has gone.",
            "It works <strong>ore showing in the walls</strong> before plain rock, and what each block opens up shows the next, so it follows a vein as far as it can reach it. It never digs the floor, and it leaves <strong>ancient debris</strong> and obsidian for you (bare hands cannot get them).",
            "Besides stone and the usual ores it digs <strong>nether quartz and nether gold, amethyst and calcite, clay, dirt, deepslate, basalt, blackstone and dripstone</strong>. It digs bare-handed and drops what an <strong>iron pickaxe</strong> would give.",
        ],
        notes=["Place the chest on the floor of where you want the mine, with room around it. Stock the chest with fences, planks and torches, or hand them to the golem in its inventory (right-click it)."],
        tips=[],
    ),
]

BUILD_HOWTO = ("Each golem is built from its own pattern of blocks with a <strong>carved pumpkin</strong> or <strong>jack o'lantern</strong> placed on top "
               "(see each golem's page). The pumpkin and all but the lowest block are consumed, the lowest block becomes the golem's chest, and the golem stands on it. "
               "You do not need a chest first: the pattern makes one. The golem is bound to that chest and starts <em>dormant</em>.")


# ------------------------------------------------------------------ rendering

def crop(im, pad=8):
    """Trim transparent margins (the scale is untouched, so golems keep their relative sizes)."""
    l, t, rr, b = im.getbbox()
    return im.crop((max(l - pad, 0), max(t - pad, 0), min(rr + pad, im.width), min(b + pad, im.height)))


def build_images(vanilla, items):
    img = SITE / "assets/img"
    img.mkdir(parents=True, exist_ok=True)
    (SITE / "assets/items").mkdir(parents=True, exist_ok=True)
    s = 7.5  # one scale for every golem so they stand at their true relative sizes
    for g in GOLEMS:
        sc = r.golem_scene(g["texture"].replace("_golem", ""), r.MOD_ASSETS / ("textures/entity/%s.png" % g["texture"]))
        crop(sc.render(400, yaw=-30, pitch=18, scale=s)[0]).save(img / ("%s.png" % g["slug"]))
        ch = r.chest_scene(r.MOD_ASSETS / ("textures/entity/chest/%s.png" % g["chest"]))
        crop(ch.render(320, yaw=-30, pitch=25, scale=11)[0]).save(img / ("%s-chest.png" % g["slug"]))
    for item in sorted(items):
        r.slot_icon(vanilla, item).save(SITE / "assets/items" / ("%s.png" % item))




def build_extras(vanilla):
    """Images that are not items: the crafting-grid pieces, each chest as an inventory item, the page background."""
    img = SITE / "assets/img"
    r.slot_image().save(SITE / "assets/items/_empty.png")
    for g in GOLEMS:
        ch = r.chest_scene(r.MOD_ASSETS / ("textures/entity/chest/%s.png" % g["chest"]))
        r.slot_image(r.scene_icon(ch)).save(SITE / "assets/items" / ("%s_chest.png" % g["id"]))
    # the crafting arrow, as a 22x15 pixel image
    a = Image.new("RGBA", (22, 15), (0, 0, 0, 0))
    px = a.load()
    for x in range(0, 14):
        for y in range(5, 10):
            px[x, y] = (139, 139, 139, 255)
    for k in range(8):
        for y in range(k, 15 - k):
            px[13 + k, y] = (139, 139, 139, 255)
    a.resize((22 * 3, 15 * 3), Image.NEAREST).save(img / "arrow.png")
    # the page background: the dirt block texture, darkened and tiled like the game's option screens
    dirt = vanilla.texture("block/dirt").resize((64, 64), Image.NEAREST)
    dark = Image.new("RGBA", dirt.size, (0, 0, 0, 150))
    dirt.alpha_composite(dark)
    dirt.save(img / "bg.png")
    build_landscape(vanilla, img)


def build_landscape(vanilla, img):
    """The page background, painted pixel by pixel in game pixels (the CSS shows it at 3x): a sky that pales to a hazy
    horizon with clouds and far hills, uneven grass with flowers, dirt that dithers into stone, and stone with the odd cave and ores that
    get more common and richer with depth. It tiles sideways; below it the CSS repeats a tall stone tile that matches its bottom edge, which lines up because
    the strip is a whole number of 16-pixel texture rows tall."""
    import math
    import random
    import numpy as np
    rnd = random.Random(11)
    W, H = 320, 720
    GROUND = 118
    DIRT_END = GROUND + 64
    a = np.zeros((H, W, 4), np.uint8)
    a[..., 3] = 255

    def wave(x, seed, terms=4):
        """a smooth function of x that repeats every W pixels, about -1..1"""
        g = random.Random(seed)
        v = 0.0
        for k in range(1, terms + 1):
            v += math.sin(2 * math.pi * (g.randint(1, 5) * k) * x / W + g.random() * 6.28) / k
        return v / 1.8

    def mix(c1, c2, t):
        return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))

    # ---- sky: deep blue at the top, pale at the horizon, with a 4x4 ordered dither between the bands
    bayer = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]
    top, hor = (84, 138, 232), (186, 220, 248)
    for y in range(GROUND + 6):
        t = min(1.0, (y / (GROUND - 10)) ** 1.5)
        for x in range(W):
            tt = min(1.0, max(0.0, t + (bayer[y % 4][x % 4] / 16 - 0.5) * 0.09))
            a[y, x, :3] = mix(top, hor, tt)
    # ---- clouds: soft white puffs with a pale blue underside
    def cloud(cx, cy, w, h):
        for dx in range(-w, w + 1):
            hh = int(h * math.sqrt(max(0.0, 1 - (dx / (w + 0.5)) ** 2)) + rnd.random() * 1.5)
            for dy in range(-hh, 2):
                x, y = (cx + dx) % W, cy + dy
                if 0 <= y < GROUND:
                    a[y, x, :3] = (232, 242, 252) if dy >= 0 else (255, 255, 255)
    for cx, cy, w, h in ((30, 40, 22, 6), (70, 30, 12, 4), (120, 58, 26, 7), (175, 26, 18, 5), (215, 50, 30, 8), (270, 36, 14, 4), (300, 64, 16, 5)):
        cloud(cx, cy, w, h)
        cloud(cx - w // 2, cy - 3, w // 2, h - 1)
        cloud(cx + w // 2, cy - 2, w // 2, h - 2)
    # ---- far hills and trees, faded into the sky haze
    for layer, (base, amp, col, seed) in enumerate(((GROUND - 20, 16, (150, 196, 200), 3), (GROUND - 9, 11, (112, 168, 118), 5))):
        for x in range(W):
            top_y = int(base - amp * (0.55 + 0.45 * wave(x, seed)))
            for y in range(top_y, GROUND + 6):
                a[y, x, :3] = col
        g = random.Random(seed * 7)
        for _ in range(26):
            x = g.randrange(W)
            top_y = int(base - amp * (0.55 + 0.45 * wave(x, seed)))
            h = g.randint(5, 9)
            tcol = mix(col, (60, 120, 60), 0.35)
            for dy in range(h):
                for dx in range(-2, 3):
                    if abs(dx) + dy // 2 <= 3 and dy >= 2:
                        a[top_y - h + dy + 2, (x + dx) % W, :3] = tcol
            a[top_y - 2:top_y + 1, x, :3] = mix(col, (70, 50, 30), 0.5)
    # ---- the ground surface: a few pixels of rise and fall
    surf = [GROUND + int(round(2.4 * wave(x, 9, 3))) for x in range(W)]
    dirt_t = np.array(vanilla.texture("block/dirt"))
    stone_t = np.array(vanilla.texture("block/stone"))
    ore_tex = {n: np.array(vanilla.texture("block/%s_ore" % n)) for n in ("coal", "iron", "copper", "lapis", "gold", "redstone", "diamond")}

    def rock(h, depth0, depth1, y_off, caves, margin=0, bottom=0):
        """Stone rows y_off..y_off+h-1 (texture phase kept so rows line up with the tiled CSS), with ores that get more common
        and richer as depth goes from depth0 to depth1 (0..1), and a few caves."""
        g = random.Random(h + int(y_off))
        area = np.zeros((h, W, 4), np.uint8)
        for y in range(h):
            for x in range(W):
                area[y, x] = stone_t[(y + y_off) % 16, x % 16]
        plain = area.copy()
        for by in range(margin // 16, (h - bottom) // 16):
            f = depth0 + (depth1 - depth0) * (by * 16 / max(1, h))
            for bx in range(W // 16):
                if g.random() > 0.012 + 0.09 * f * f:      # rare near the surface, common far down
                    continue
                weights = {"coal": 1.0 - 0.6 * f, "iron": 0.7, "copper": 0.6 * (1 - f) + 0.2, "lapis": 0.7 * f, "gold": 0.6 * f,
                           "redstone": 0.9 * f, "diamond": 0.5 * f * f}
                r_ = g.random() * sum(weights.values())
                for n, wgt in weights.items():
                    r_ -= wgt
                    if r_ <= 0:
                        break
                area[by * 16:by * 16 + 16, bx * 16:bx * 16 + 16] = ore_tex[n]
        # caves: clusters of whole blocks (grown by a random walk on the block grid), dark inside, outlined, and lit by torches
        cols = W // 16
        mask = np.zeros((h, W), bool)
        torches = []
        for _ in range(caves):
            cell = (g.randrange(cols), g.randint((margin + 16) // 16, (h - bottom - 32) // 16))
            blocks = {cell}
            for _s in range(g.randint(18, 34)):
                bx, by = g.choice(sorted(blocks))
                dx, dy = g.choice(((1, 0), (-1, 0), (1, 0), (-1, 0), (0, 1), (0, -1)))
                nb = ((bx + dx) % cols, by + dy)
                if (margin + 16) // 16 <= nb[1] < (h - bottom) // 16:
                    blocks.add(nb)
            for bx, by in blocks:
                mask[by * 16:by * 16 + 16, bx * 16:bx * 16 + 16] = True
            floors = sorted(b for b in blocks if (b[0], b[1] + 1) not in blocks)
            for bx, by in g.sample(floors, min(len(floors), 2 + len(blocks) // 9)):
                torches.append((bx, by))
        yy, xx = np.mgrid[0:h, 0:W]
        light = np.zeros((h, W), np.float32)
        for bx, by in torches:
            tx, ty = bx * 16 + 8, by * 16 + 5
            dx = np.minimum(np.abs(xx - tx), W - np.abs(xx - tx))
            d = np.sqrt(dx ** 2 + (yy - ty) ** 2)
            light = np.maximum(light, np.clip(1 - d / 46.0, 0, 1) ** 1.4)
        cool, warm = np.array((0.75, 0.8, 1.0), np.float32), np.array((1.1, 0.78, 0.45), np.float32)
        lt = light[..., None]
        wall = plain[..., :3].astype(np.float32) * (0.16 + 0.95 * lt) * (cool * (1 - lt) + warm * lt)
        def grow(m):  # a pixel next to the set, 4-neighbourhood (wrapping sideways)
            o = m.copy()
            o |= np.roll(m, 1, 1) | np.roll(m, -1, 1)
            o[1:] |= m[:-1]
            o[:-1] |= m[1:]
            return o
        area[..., :3][mask] = np.clip(wall[mask], 0, 255).astype(np.uint8)
        # the edge is ragged, like the grass lip over dirt: inside, a lit lip of stone that breaks up over a few pixels;
        # outside, the stone darkens towards the cave the same way
        inner, cur = [], mask
        for _k in range(4):
            edge = cur & grow(~cur)
            inner.append(edge)
            cur = cur & ~edge
        outer, cur = [], mask
        for _k in range(3):
            nxt = grow(cur)
            outer.append(nxt & ~cur)
            cur = nxt
        for k, edge in enumerate(outer):
            pick = edge & (np.random.RandomState(k + 3).rand(h, W) < 1.0 - 0.28 * k)
            area[..., :3][pick] = (plain[..., :3][pick].astype(np.float32) * (0.30 + 0.22 * k)).astype(np.uint8)
        for k, edge in enumerate(inner):
            pick = edge & (np.random.RandomState(k + 9).rand(h, W) < 1.0 - 0.27 * k)
            lip = plain[..., :3][pick].astype(np.float32) * (0.55 + 0.9 * light[pick][:, None]) * (1.0, 0.92, 0.8) + 14 + 50 * light[pick][:, None]
            area[..., :3][pick] = np.clip(lip, 0, 255).astype(np.uint8)
        out_im = Image.fromarray(area, "RGBA")
        torch_im = vanilla.texture("block/torch")
        for bx, by in torches:
            out_im.alpha_composite(torch_im, (bx * 16, by * 16))
        area = np.array(out_im)
        return area

    stone = rock(H, 0.0, 1.0, 0, 6, margin=DIRT_END + 60)
    # ---- dirt that dithers into stone over a ragged band
    grass_dark, grass_mid, grass_lit = (74, 128, 40), (104, 166, 56), (150, 206, 84)
    for x in range(W):
        thick = 4 + int(2.5 * (1 + wave(x, 21, 3))) + rnd.randint(0, 1)  # the grass lip hangs lower in places
        for y in range(surf[x], H):
            d = y - surf[x]
            if d < thick:
                c = grass_lit if d == 0 else grass_mid if d < thick - 1 else grass_dark
                n = rnd.randint(-8, 8)
                a[y, x, :3] = tuple(max(0, min(255, v + n)) for v in c)
                continue
            band_start = DIRT_END + int(9 * wave(x, 33, 3))
            t = (y - band_start) / 26.0
            use_stone = t > 0 and rnd.random() < min(1.0, t * 1.15)
            a[y, x, :3] = (stone[y, x] if use_stone else dirt_t[y % 16, x % 16])[:3]
    # ---- the grass top: blades and tiny pixel flowers standing on the surface
    flowers = [((255, 215, 40), (255, 245, 150)), ((220, 50, 40), (255, 120, 90)), ((250, 250, 250), (255, 235, 90)),
               ((120, 150, 255), (200, 215, 255)), ((236, 120, 190), (255, 190, 225)), ((250, 150, 40), (255, 210, 120))]
    x = 0
    while x < W:
        s = surf[x]
        r_ = rnd.random()
        if r_ < 0.55:
            h = rnd.randint(1, 3)
            for dy in range(1, h + 1):
                a[s - dy, x, :3] = grass_lit if dy == h else grass_mid
        x += 1
    for x in range(3, W - 3, 1):
        if rnd.random() < 0.075:
            s = surf[x]
            head, mid = rnd.choice(flowers)
            h = rnd.randint(3, 6)
            for dy in range(1, h + 1):
                a[s - dy, x, :3] = grass_dark
            a[s - h - 1, x, :3] = mid
            for dx, dy in ((-1, -h - 1), (1, -h - 1), (0, -h - 2), (0, -h)):
                if dy == -h:
                    continue
                a[s + dy, (x + dx) % W, :3] = head
    out = Image.fromarray(a, "RGBA")
    out.resize((W * 3, H * 3), Image.NEAREST).save(img / "landscape.png")
    # the endless stone below the strip: one tall tile, as rich in ores as the strip's bottom, repeated by the CSS
    TILE = 480
    Image.fromarray(rock(TILE, 1.0, 1.0, 0, 2, margin=40, bottom=40), "RGBA").resize((W * 3, TILE * 3), Image.NEAREST).save(img / "underground.png")
    if (img / "stone.png").exists():
        (img / "stone.png").unlink()


# ------------------------------------------------------------------ pages

NAV_FONT = "https://fonts.googleapis.com/css2?family=Pixelify+Sans:wght@400;600;700&family=Source+Sans+3:wght@400;600;700&display=swap"


def css_version():
    """A short hash of the stylesheet, so a changed stylesheet is never served from a browser's cache against new pages."""
    import hashlib
    return hashlib.md5((SITE / "assets/style.css").read_bytes()).hexdigest()[:8]


def layout(title, body, root, active, description, splash=None):
    return """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>%(title)s</title>
<meta name="description" content="%(description)s">
<link rel="icon" href="%(root)sassets/img/favicon.png">
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="%(font)s">
<link rel="stylesheet" href="%(root)sassets/style.css?v=%(ver)s">
</head>
<body>
<header class="title">
  <a class="logo" href="%(root)sindex.html">Industrious Golems</a>
</header>
<main class="gui">
%(body)s
</main>
<footer class="foot">Industrious Golems is a fan-made <a href="https://neoforged.net/">NeoForge</a> mod and is not affiliated with Mojang or Microsoft.
Item icons are drawn from Minecraft's own textures; item links go to the <a href="https://minecraft.wiki/">Minecraft Wiki</a>.
This wiki is generated by <code>tools/wiki</code> in the <a href="%(repo)s">repository</a>.</footer>
</body>
</html>
""" % dict(title=html.escape(title), description=html.escape(description), root=root, body=body.replace("{root}", root), repo=REPO,
           font=NAV_FONT, ver=css_version())


def crafting(g):
    """The chest recipe as a crafting table shows it (shapeless, so the ingredients fill the first cells)."""
    cells = [slot(i) for i in g["recipe"]] + ['<span class="invslot blank"><img src="{root}assets/items/_empty.png" alt=""></span>'] * (9 - len(g["recipe"]))
    result = ('<span class="invslot"><img src="{root}assets/items/%s_chest.png" alt="%s">'
              '<span class="tip"><b>%s</b></span></span>' % (g["id"], g["job"], g["job"]))
    return ('<div class="crafting"><div class="craft-title">Crafting</div><div class="craft-body">'
            '<div class="grid3">%s</div><img class="arrow" src="{root}assets/img/arrow.png" alt="makes">%s</div></div>' % ("".join(cells), result))


def golem_page(g):
    offer = panel("Offerings", '<div class="entries">%s</div>' % "".join(slot(i, label=True, hint="Found: " + h) for i, h in g["offerings"]), "wide")
    made = panel("Produces", '<div class="invgrid">%s</div>' % "".join(slot(i) for i in g["produces"]))
    uses = panel("Supplies", '<div class="entries">%s</div>' % "".join(slot(i, label=True, hint=t) for i, t in g["consumes"]), "wide")
    uses_text = "; ".join(t for _, t in g["consumes"]) or "Nothing: it digs bare-handed."
    behaviour = "".join("<li>%s</li>" % t for t in g["behaviour"])
    notes = "".join("<li>%s</li>" % t for t in g["notes"])
    others = "".join('<a class="card small" href="%s.html"><span class="view"><img src="{root}assets/img/%s.png" alt=""></span><span class="cardname">%s</span></a>'
                     % (o["slug"], o["slug"], o["title"]) for o in GOLEMS if o is not g)
    summary = g["summary"].replace("The " + g["title"], "The <strong>%s</strong>" % g["title"], 1)
    body = """
<article>
<h1>%(title)s</h1>
<p class="role">%(role)s</p>
<aside class="infobox">
  <div class="ib-title">%(title)s</div>
  <div class="view"><img src="{root}assets/img/%(slug)s.png" alt="Render of the %(title)s"></div>
  <div class="view chest"><img src="{root}assets/img/%(slug)s-chest.png" alt="Render of the %(job)s"></div>
  <div class="ib-cap">%(job)s</div>
  <table>
    <tr><th>Role</th><td>%(role)s</td></tr>
    <tr><th>Job site</th><td>%(job)s (or a vanilla %(jobv)s)</td></tr>
    <tr><th>Built from</th><td>%(build)s</td></tr>
    <tr><th>Uses</th><td>%(uses_text)s</td></tr>
    <tr><th>Charge</th><td>45 minutes per offering</td></tr>
  </table>
</aside>
<p class="lead">%(summary)s</p>

<h2 id="building">Building a %(title)s</h2>
<p>%(howto)s</p>
<p>The chest is also craftable, so you can move or replace it. It has 27 slots; place two of the same kind side by side and they join into a 54-slot double chest.</p>
%(crafting)s

<h2 id="offerings">Waking it up</h2>
<p>A golem starts <em>dormant</em>: head hanging, standing still, holding out the thing it wants in its off hand. Right-click it with that item
to give it an <strong>offering</strong>. Each offering is worth <strong>45 minutes</strong> of work and it stores at most two (90 minutes).
It then asks for something different, so no single item will do. The clock runs only while the golem is loaded and has a job site.</p>
<p>These are the eight items it can ask for, and where each is found. They are deliberately unrelated to its job, so keeping a large operation going means going out exploring.</p>
%(offer)s

<h2 id="behaviour">What it does</h2>
<ul>%(behaviour)s</ul>
<p>Like every golem it <strong>carries one stack at a time</strong>: it has no inventory, picks things up in its hand, takes them to its chest and goes back for the next. With a stack already in hand it first gathers more of the same item, up to a full stack, so it makes fewer trips.
Supplies come out of the chest the same way.</p>

<h3>It produces</h3>
%(made)s
%(usesblock)s
<h2 id="notes">Notes</h2>
<ul>%(notes)s<li>A golem prefers its own chest to any other chest nearby.</li></ul>

<h2>Other golems</h2>
<div class="cards">%(others)s</div>
</article>
""" % dict(
        title=g["title"], role=g["role"], slug=g["slug"], job=g["job"], jobv=wiki(name(g["job_vanilla"]).lower(), g["job_vanilla"]),
        build=g["build"],
        uses_text=uses_text, summary=summary, howto=BUILD_HOWTO, crafting=crafting(g),
        offer=offer, behaviour=behaviour, made=made,
        usesblock=("<h3>It uses</h3>%s" % uses) if uses else "", notes=notes, others=others)
    return layout("%s - Industrious Golems Wiki" % g["title"], body, "../", g["slug"], g["tagline"] + " " + g["summary"])


def home_page():
    cards = "".join('<a class="card" href="golems/%s.html"><span class="view"><img src="assets/img/%s.png" alt="%s"></span><span class="cardname">%s</span><span class="lore">%s &middot; %s</span></a>'
                    % (g["slug"], g["slug"], g["title"], g["title"], g["role"], g["tagline"]) for g in GOLEMS)
    rows = "".join('<div class="row"><a class="rowname" href="golems/%s.html">%s<span class="lore">%s</span></a>'
                   '<div class="cell"><span class="craft-title">Body</span><div class="slots">%s</div></div>'
                   '<div class="cell"><span class="craft-title">Uses</span><div class="slots">%s</div></div>'
                   '<div class="cell"><span class="craft-title">Produces</span><div class="slots">%s</div></div></div>' % (
                       g["slug"], g["title"], g["role"], slot(g["body"]),
                       "".join(slot(i) for i, _ in g["consumes"]) or '<span class="lore">nothing</span>',
                       "".join(slot(i) for i in g["produces"][:7])) for g in GOLEMS)
    body = """
<article>
<p class="lead"><strong>Industrious Golems</strong> is a NeoForge mod that adds <strong>work golems</strong>: golems that hold a job and produce resources directly,
while you build the infrastructure around them (chests, hoppers, furnaces, crafters, minecarts) from ordinary vanilla blocks.
Villagers, trading and iron golems are left exactly as they are.</p>
<div class="cards hero">%(cards)s</div>

<h2 id="how">How golems work</h2>
<h3>Building one</h3>
<p>%(howto)s</p>
<h3>The chest</h3>
<p>Each golem has its own <strong>27-slot chest</strong>, which is its job site. It deposits what it produces there and restocks its supplies from it. The chest is craftable
(a %(chest)s, the golem's old job block and its body block) so you can move one. Two of the same kind side by side join into a <strong>54-slot double chest</strong>, which the golem uses just the same.</p>
<p><strong>Copper chests.</strong> If there is a vanilla <strong>copper chest</strong> within 3 blocks of the golem's chest, the golem puts what it <em>produces</em> there instead,
so copper golems can carry it on to wherever it belongs. What the golem also works with itself (seeds for the hay golem, saplings for the mushroom golem) still goes in its own chest, and with
no copper chest nearby, or none with room, everything goes in its own chest as before.</p>
<h3>Charge and offerings</h3>
<p>Golems work bare-handed, day and night, but only while <strong>charged</strong>. A new golem is dormant until you give it something it craves: right-click it with the item.
Each kind has <strong>eight offerings</strong>, uncommon finds from different biomes, structures and mobs, and it craves one at a time. One offering is worth <strong>45 minutes</strong>
of work; a golem stores two. The more golems you run, the more offerings you need, which means going out exploring.</p>
<h3>One stack at a time</h3>
<p>A golem has no inventory. It breaks a block, shears or milks, and the result drops in the world as it would for a player. It picks up <strong>one stack in its hand</strong>,
carries it to its chest and goes back for the next. If more of the same item lies about it collects that first, up to a full stack, before heading to the chest. Supplies (seeds, saplings, buckets, feed) come out of the chest the same way.</p>

<h2 id="golems">The golems</h2>
<div class="rows">%(rows)s</div>

<h2 id="about">About this wiki</h2>
<p>The source and issue tracker are on <a href="%(repo)s">GitHub</a>. Item links go to the <a href="https://minecraft.wiki/">Minecraft Wiki</a>.</p>
</article>
""" % dict(cards=cards, howto=BUILD_HOWTO, chest=wiki("chest", "chest"), repo=REPO, rows=rows)
    return layout("Industrious Golems Wiki", body, "", "home",
                  "Work golems for Minecraft (NeoForge): a farmer, a lumberjack, a herder and a miner.")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar")
    args = ap.parse_args()
    jar = Path(args.jar) if args.jar else find_jar()
    vanilla = r.Vanilla(jar)
    LANG_NAMES.update(json.loads(vanilla.zip.read("assets/minecraft/lang/en_us.json")))
    items = {"chest", "carved_pumpkin"}
    for g in GOLEMS:
        items.update([g["body"], g["job_vanilla"]] + g["recipe"] + g["produces"] + [i for i, _ in g["consumes"]] + [i for i, _ in g["offerings"]])
    build_images(vanilla, items)
    build_extras(vanilla)
    fav = Image.open(r.MOD_ASSETS / "textures/item/hay_golem_spawn_egg.png").convert("RGBA")
    fav.save(SITE / "assets/img/favicon.png")
    (SITE / "golems").mkdir(exist_ok=True)
    (SITE / "index.html").write_text(home_page(), encoding="utf-8")
    for g in GOLEMS:
        (SITE / "golems" / ("%s.html" % g["slug"])).write_text(golem_page(g), encoding="utf-8")
    (SITE / ".nojekyll").write_text("")
    print("built", len(GOLEMS), "golem pages and", len(items), "icons into", SITE)


if __name__ == "__main__":
    main()

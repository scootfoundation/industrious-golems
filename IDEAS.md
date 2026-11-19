# Mod ideas

Ideas for mods that are not part of this one. They are written down here so they are not lost.
Nothing in this file is committed to; it is a design notebook.

Three ideas, one shared theme: **make distance and time matter.** They overlap heavily (see
"How they fit together" at the end).

---

## 1. Intentful transport

Goal: make it hard to travel so fast that you don't experience the world, while still allowing fast
travel when it is genuinely needed.

- Horses get a speed boost on compacted (trodden) grass.
- Horses running on grass have a chance to compact it.
- Rockets no longer accelerate you while flying an elytra.
- A world map that teleports you to named waypoints, at a cost (e.g. XP).
- Nether-to-overworld ratio changed from 8:1 to 1:1.

### Notes

- **Easy:** rocket disable (one hook on firework use); horse speed on compacted grass (check the
  block under the hooves).
- **1:1 nether is mostly a datapack change.** The nether's coordinate scale is a property of its
  dimension type JSON, so little or no code may be needed. It also removes the main built-in
  shortcut in vanilla, so the nether becomes a parallel world rather than a highway.
- **Waypoint map is the hardest part and the one that most cuts against the goal.** Open questions:
  - Discovery: unlock waypoints by visiting them. Keeps travel meaningful.
  - Cost: a flat cost is weak. Scale it with distance (per hundred metres) so short hops are cheap
    and long ones deliberate.
  - Where the map lives (item, block or screen), and whether waypoints are per player or per world.
- **Positive feedback loop to cap:** the speed bonus rewards worn paths, which draws more traffic,
  which wears them more. Cap the wear level and flatten the bonus at the top, or every hub becomes
  a motorway.

---

## 2. A world that wears

Goal: in long-lived worlds, the parts that are not being lived in turn to ruins, and the parts that
are heavily used show it.

### Wear from entities

Any ground-based entity wears the ground it walks on. The chance scales with **footprint and
speed**: bigger and faster entities are more likely to wear a block, so a sprinting horse wears
the most (roughly 1.4 m wide at up to ~10 m/s) and a walking player the least of the common cases
(0.6 m wide at ~4 m/s).

- Evaluate on **entering a new block**, not every tick. Cheap.
- Ground only. Flying, swimming and item entities contribute nothing.
- Heavy mobs (iron golems) could get a mass multiplier.
- Watch out for mob farms wearing their spawn floors constantly. Exempt or cap.

### Decay spreads (stateless)

No per-block memory. Each random tick there is a small chance that decay spreads to a neighbour,
so decayed areas beget more decay:

- Wild grass spreads to nearby grass blocks.
- Stone near cracked stone cracks too.
- Wild grass near stone bricks makes them mossy.
- Stone brick -> cracked -> cobble -> gravel -> dirt (not air; see below).

Using the engine's own random ticks keeps this cheap, and matches how grass and moss already
spread in vanilla.

**Design rules agreed so far**

1. **Chunk catch-up.** Random ticks only run in loaded chunks near a player, so a far-away base
   would never decay. Store one timestamp per chunk (last loaded). On load, run catch-up passes
   proportional to elapsed time. Still no per-block state, just one number per chunk.
2. **Seeds and stopping conditions.**
   - Decay needs seeds: worldgen already provides some (cracked bricks, moss in ruins), plus a
     very low spontaneous rate on exposed blocks.
   - Runaway: below a critical spread rate decay stays in patches, above it it takes over. Tune
     near the edge. Scale the spread chance with the number of decayed neighbours (out of 26) so
     isolated blocks rarely seed decay.
   - Cure: players need a way to push back. Replace a cracked block, scrape off moss, cut wild
     grass at the source. Upkeep becomes a small firefighting loop.
3. **Environment instead of memory.** Local conditions do the work neglect would have done:
   - Moss spreads where it is dark or wet (water, shade, caves).
   - Cracks spread where blocks are exposed to sky and rain, so a roof protects what is under it.
   - Wild grass spreads in sunlight, so shaded stone stays bare.
4. **No decay into air.** Gravel -> dirt rather than gravel -> nothing. Air-creating decay causes
   cave-ins and physics cascades.
5. **Protect player builds.** Player-placed blocks age into "ruin" variants (mossy, cracked) but
   never disappear, so a returning player does not find a wrecked base.

### Cobwebs in the dark

Dark places (low light level) have a chance of spawning a cobweb in a ceiling corner. Cobwebs
**spread**, and **negatively affect the "liveliness" of blocks around them** (higher decay chance
nearby, no grass spread, etc.). A lit, tended room never grows them; a long-dark one does. Fits
neatly with torches in unvisited areas going out.

### Desire paths (stateless)

Each step has a small chance to trample grass; each random tick has a small chance for a trampled
block to revert. The fraction of trampled blocks settles where the two rates balance: busy routes
stay worn, quiet ones fade, with no counters.

No stages needed, but a **half-step block** could smooth well-trodden paths:

- States: grass -> slightly depressed (half-step) -> trampled path.
- When an entity jumps or lands on the edge of a path, there is a chance the edge block depresses
  one step, so the path edges blend into the ground instead of ending in a sharp ledge.
- Entities auto-step up low heights, so a half-step needs no jump, and well-travelled paths become
  visibly smoother. (Vanilla dirt paths are 15/16 of a block high; the half-step would be lower.)

### More ideas (unordered)

- **Rain amplifies wear** (mud). Wet weather raises wear chances.
- **Surface matters.** Snow and sand show tracks that fade quickly; grass compacts; wood floors scuff.
- **Chokepoints wear faster** (doorways, bridges); stairs and thresholds chip into a worn variant.
- **Rails rust with neglect.** Unpowered rails lose power or slow carts.
- **Light failure.** Torches and lanterns in unvisited areas go out, so mobs spawn in abandoned bases.
- **Nature succeeds.** Cleared land grows shrubs, then saplings, then trees; vines and moss
  spread over stone.
- **Wood rots** near water; stone weathers slower.
- **Villages decline** when neglected: beds lost, doors broken, villagers leave. Iron golems could
  be the village repair crew (this repo is about golems).
- **Farms revert** to weeds and plain dirt.
- **Attention** (any placement, break, door or crop interaction) could reset a neglect timer in a
  radius, if the stateless approach turns out not to be enough.

### Tuning notes

A random tick hits each block about once per 68 s in a default world (4096 blocks per 16x16x16
section, 3 random ticks per section per tick). A per-tick spread chance of 1 in 20 means a decayed
block seeds a neighbour roughly once every 20 minutes. Decide whether to respect the
`randomTickSpeed` gamerule or use scheduled ticks of our own.

---

## 3. Illustrious allays

A mob that does the opposite of the vanilla allay: instead of collecting items into your inventory,
it **takes items out and puts them in the world**, building from blueprints. A kind of automated
builder.

### UX sketch

- A special banner is placed in the world and selects a blueprint.
- Default blueprints include structures that exist in Minecraft: villager buildings, camps, etc.
  (Vanilla structure NBT files can be used.)
- Custom designs: build it in the world and **scan** it by selecting two corners with a wand. This
  is far more tractable than describing a design in words, which would need an external AI
  service.

### Hard parts

- **Materials:** taking items from the player's inventory is intrusive. Prefer drawing from nearby
  linked chests; inventory only if the player hands items over.
- **Building:** flying, placing blocks at a sensible pace, missing materials, lava and mobs, loaded
  chunks only.
- **Permissions:** on servers, claims and ownership must apply to what an allay builds.

### The link to decay

If the world wears, allays are the repair crew that rebuilds ruins. Decay creates a need; allays
answer it. Vanilla villager buildings and camps are a natural first set of blueprints. Paths and
early wear heal themselves (traffic or time), so allays only need to handle ruin; they do not
fight the path system.

---

## How they fit together

A shared **wear** idea ties all three together:

1. **Lived-in:** normal blocks.
2. **Trodden:** desire paths from traffic. Reverts to grass if traffic stops.
3. **Worn:** stable roads or cobble in heavy use.
4. **Ruin:** neglect has gone on long enough that only repair can undo it.

- Transport uses the trodden state for the horse speed bonus.
- Decay produces ruins and spreads by neighbour rules and environment.
- Allays repair ruin, completing the loop.

Which to build first is to be decided when the work starts. Rough difficulty, easiest to hardest:
rocket disable and horse speed; trodden states and the 1:1 nether (datapack); decay with chunk
catch-up; waypoint map; allays with scanned blueprints.

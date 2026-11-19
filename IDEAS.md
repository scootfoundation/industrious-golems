# Mod ideas

Ideas for mods that are not part of this one. They are written down here so they are not lost.
Nothing in this file is committed to; it is a design notebook.

Three ideas, one shared theme: **make distance and time matter.** They overlap heavily (see
"How they fit together" at the end).

---

## 1. Intentful transport

Goal: make it hard to travel so fast that you don't experience the world, while still allowing fast
travel when it is genuinely needed.

- Horses get a speed boost on compacted (trodden) grass. (Compaction itself, i.e. horses
  wearing the ground, belongs to mod 2; this mod only reads the trodden state.)
- Rockets no longer accelerate you while flying an elytra.
- Fast travel between named waypoints, at a cost (see below).
- Nether-to-overworld ratio changed from 8:1 to 1:1.

### Waypoint design

- **Waypoints are lodestones** that get activated and named (how: TBD, e.g. a name tag).
- **The map UI** opens by right-clicking a vanilla map placed in an item frame.
- The UI shows all discovered waypoints; **hovering shows the travel cost**: a fixed cost plus a
  cost proportional to distance.

### Notes

- **Easy:** rocket disable (one hook on firework use); horse speed on compacted grass (check the
  block under the hooves).
- **1:1 nether is mostly a datapack change.** The nether's coordinate scale is a property of its
  dimension type JSON, so little or no code may be needed. It also removes the main built-in
  shortcut in vanilla, so the nether becomes a parallel world rather than a highway.
- **Waypoint map is the hardest part and the one that most cuts against the goal.** Open questions:
  - Discovery: unlock waypoints by visiting them. Keeps travel meaningful.
  - Cost: fixed + per-distance (see above); decide the currency (XP is the working assumption).
  - Whether waypoints are per player or per world, and how cross-dimension travel is priced.
  - The client UI needs a custom screen drawn over the map; unexplored areas stay blank.
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

## 3. Wrights

A mob that does the opposite of the vanilla allay: instead of collecting items into your inventory,
a **wright** takes items out of a chest and **puts them in the world**, building from blueprints.
An automated builder.

### Surveying

Any structure can be captured as a blueprint, any time. Nothing is inferred; it is a plain copy.

1. **Draw the footprint.** Place survey **stakes** around the structure and join them with
   **string** to form a closed 2D outline at ground level. The outline can be any shape (an L, a
   courtyard), not just a rectangle.
2. **A wright scans it.** It flies the outline and then scans **layer by layer upward**
   inside it, until it reaches a layer that is entirely air. The height emerges from the scan, so
   there is no third axis to set. The scan takes time proportional to height and area.
3. **Name the result.** It becomes a **blueprint item**.
4. **Limits:** cap the number of stakes and the footprint area.
5. **Captures** block types and block states (orientation, door halves, stair shapes). **Leaves
   out** container contents and most entities.
6. Blueprints are stored server-side as ordinary structure data; the item only points to it, which
   avoids item size limits.
7. **Vanilla blueprints** (villager houses, camps, etc.) ship in the same format, so there is only
   one system. Vanilla structure NBT files can be converted; their generated bounding boxes are
   known, so no outline is needed for them.

Surveying is a record, not a duplication tool: wrights can only place what is actually in the
chest. The bill of materials is a real planning step.

**Known issues with the outline-and-scan approach**

- **Slopes.** An outline on a hillside includes terrain above the stakes, which is solid, so the
  scan would not terminate until above the hill. Mitigation: ignore natural terrain blocks (dirt,
  stone, grass, sand, gravel) above the stake layer; flat or cleared sites work without it.
- **Basements.** The scan starts at the stake layer, so anything below is missed. Place the
  stakes at the lowest level you want captured.
- **Disconnected pieces.** Anything above an all-air layer is not captured. Arguably correct.
- **Chicken and egg.** Making a wright needs a blueprint, and surveying needs a wright. Resolve it
  by having **villagers sell blueprints**: buy one, give it to an allay to apprentice it, and you
  have a wright who can then do all the scanning. Surveying stays entirely the wright's job.

### Using blueprints

- **Material substitution.** Before building, the lodge's bill of materials lets you swap one
  block type for another (oak for spruce, stone brick for deepslate brick), preserving block
  states. One survey then gives many variants. Works with any survey method.
- **Repeat.** Survey one segment (a wall bay, a field row, a house) and tell the wrights to repeat
  it N times along a direction. Makes row houses, fences and farms trivial.
- **Duplication.** Copy a blueprint at a cartography table, the way maps are copied, so players
  can share them. Villagers also sell blueprints, which is how you get your first wright.

### Wright acquisition

**Apprenticed allays.** Give an allay a blueprint and it becomes a wright. Allays are already
obtainable in vanilla (caged in pillager outposts, duplicated with amethyst), so there is no new
spawn logic, and amethyst gives a natural brake on supply.

### Building

1. Place a **wright's lodge** (a block like a beehive). It holds a few wrights (capacity limits how
   many you have), has a blueprint slot and a bill-of-materials screen, and draws materials from an
   **adjacent chest**.
2. Put a blueprint in the lodge. A ghost outline previews the placement; sneak-click rotates or
   mirrors it. Confirm.
3. The lodge shows a **bill of materials**: what is needed against what the chest holds.
4. Wrights fly to the chest, take **one block at a time**, fly to the build site and place it.
   Each wright flies at a decent pace; more wrights build faster.

### Rules

- **Only fill gaps.** Wrights place blocks only where there is space for them; they never clear or
  replace what is already there.
- **No foundations, no site preparation.** If a house ends up floating, so be it.
- **Placement order (dependency rule):** a block is not placed until the block **beneath it in the
  blueprint** has been placed. If there is no blueprint block beneath it, it waits for a block on
  **any other side** (a horizontal neighbour, or the block above) to be placed first. Partial
  builds then always look like plausible construction sites rather than scattered blocks.
  - Each connected group of blocks needs a starting block; use its lowest block as the root.
  - Gravity blocks (sand, gravel) need no special case: whatever is beneath them is placed first.
- **Missing materials:** skip that block and mark it, rather than stopping.
- **No permissions, claims or ownership.** Just build the functionality.

### Open questions

- How a wright decides a block "has space" for gravity blocks, fluids and waterlogged states.
- Pathing around obstacles, and what happens in unloaded chunks (probably pause).
- Whether a wright returns to being an allay (e.g. when released from the lodge).

### Link to decay

Survey your base while it is in good shape. If it later decays, a wright can rebuild it from that
record, so repair comes from something done earlier, not from guesswork. Because wrights only fill
gaps, rebuilding a decayed build restores what has gone missing and leaves intact blocks alone.
Paths and early wear heal themselves (traffic or time), so wrights only matter for rebuilding
what has been lost; they do not fight the path system.

---

## How they fit together

A shared **wear** idea ties all three together:

1. **Lived-in:** normal blocks.
2. **Trodden:** desire paths from traffic. Reverts to grass if traffic stops.
3. **Worn:** stable roads or cobble in heavy use.
4. **Ruin:** neglect has gone on long enough that only repair can undo it.

- Transport uses the trodden state for the horse speed bonus.
- Decay produces ruins and spreads by neighbour rules and environment.
- Wrights rebuild from surveys taken earlier, completing the loop.

Which to build first is to be decided when the work starts. Rough difficulty, easiest to hardest:
rocket disable and horse speed; trodden states and the 1:1 nether (datapack); decay with chunk
catch-up; waypoint map; wrights with surveyed blueprints.

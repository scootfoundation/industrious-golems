# Industrious Golems — Technical Assessment
n> **Historical:** written for the original villager-based design. The mod now uses work golems (see prd.md); the analysis of the farming, logging and mining work still applies.

**Scope:** High-level technical viability review of the *Industrious Golems* PRD (Draft v1).
**Assumed constraints:** Personal mod. New worlds only — no save migration concerns. No cross-mod compatibility hedging required. NeoForge target loader (Minecraft / NeoForge version still TBD).

## Verdict

Buildable. No fundamental blockers. The Farmer and loot redistribution are light lifts, the Lumberjack is moderate, and the Miner is the one feature that warrants a standalone prototype before committing to a design.

## Straightforward

- **Loot table redistribution.** Mending → stronghold libraries / ancient cities (and other trade-locked items into appropriate loot tables) via Global Loot Modifiers. Audit findings can be patched during personal playtesting rather than as a ship-blocker.
- **Wandering trader.** Untouched. No work required.
- **Vanilla mechanics preserved automatically** (breeding, raids, zombification, iron golems). These live on the `Villager` entity class and the gossip/panic system, not on profession. Reusing `Villager` and replacing only the WORK activity behaviours brings these along for free.
- **Job site rebinding.** Register custom `VillagerProfession`s pointing at the Composter / Fletching Table / Stonecutter `PoiType`s. With new-worlds-only, vanilla Fletcher/Mason can simply be unregistered or stripped.
- **Right-click does nothing.** Cancel `PlayerInteractEvent.EntityInteractSpecific` for the three professions. Trivial.

## Needs care

**Villager AI replacement.** Modern villagers use the Brain/Schedule system — memory modules, activities, behaviour packages. Replacing WORK-time behaviour means writing new `Behavior<Villager>` subclasses and registering them under the WORK activity for each profession. Doable but fiddly; expect time getting daily-schedule transitions right so panic / sleep / raid still trigger correctly.

**Block-breaking by non-player entities.** Vanilla villagers only break crops, and that's hardcoded in `HarvestFarmlandTask`. For Lumberjack and Miner, implement: pathfind to target → reach check → server-side break → drop capture into output chest. Skipping `BlockEvent.BreakEvent` is acceptable here (personal mod, no claim-mod compatibility requirement).

**Chest I/O.** Don't route through the villager's 8-slot inventory. Search adjacent blocks for `IItemHandler` capability and transfer directly. Standard NeoForge pattern.

**Tool consumption.** PRD says "tool wear matches vanilla," but vanilla durability semantics imply real tool-aware drop calculations (Silk Touch, Fortune, Efficiency speed). Recommend simplifying to consume-on-timer for v1 unless enchanted-tool behaviour is specifically desired — meaningful complexity reduction.

**Personal-mod implementation freedom.** With no public-API constraints, Mixin-based replacement of vanilla classes (e.g. wholesale-replacing `HarvestFarmlandTask`, mutating `VillagerProfession.FLETCHER` fields) is faster and cleaner than going through registry-and-event APIs. Likely 30–40% less code than the "correct" approach.

## The Miner — where the risk lives

The 3×3 trunk-and-branch behaviour is the most ambitious feature. Trunks of ~64m cross ~4 chunks; if the miner wanders outside the player's simulation distance, ticking stops. Continuous block-breaking is also tick-expensive if naive. Three options:

### Option A — Pure entity simulation

Villager physically pathfinds and breaks every block. Consistent with how Lumberjack will work.

- **Pros:** Architecturally uniform across roles. Most "vanilla-feeling" behaviour.
- **Cons:** Requires force-loading the active branch (perceptible overworld impact). Per-tick block-break cost per miner. Long-tunnel pathfinding cost.

### Option B — Block-entity state machine with villager as visual proxy *(recommended for v1)*

A BlockEntity on the Stonecutter owns the mining state — deterministic tunnel layout, throttled progress, drops emitted directly to the output chest. The villager animates at the current frontier when the player is nearby; otherwise stands at the job site.

- **Pros:** No force-loading needed. Cheap and predictable performance. Edge cases (lava, caves, bedrock) become straightforward state-machine checks. Support / torch / rail placement at fixed intervals (4m / 6m / continuous) is trivial in this model.
- **Cons:** Loses some of the "villager doing the work" feel — though most of a 64m trunk happens out of view anyway.

### Option C — Hybrid

Villager-driven inside simulation distance, BE-driven outside, reconciled on transition.

- **Pros:** Best feel-to-cost ratio.
- **Cons:** Two implementations of the same behaviour plus state reconciliation. Reconciliation logic is the part that bites.

**Recommendation:** Ship Option B for v1. Reconsider C only if the visual-proxy approach feels notably wrong in practice.

## The other two roles

**Lumberjack.** A 16×16 grove sits in 1–2 chunks, well inside loaded radius. Tree felling is a contained BFS over connected logs. Option A (pure entity simulation) is fine.

**Farmer.** Essentially "vanilla farmer that doesn't require crops planted by other villagers." Vanilla `HarvestFarmlandTask` handles ~95% of the desired behaviour. Lightest lift of the three.

## Recommendations

1. **Pin the MC / NeoForge target version before scoping further.** Brain/Behavior signatures, capability APIs, and registry handling have shifted across recent versions. Marked out of scope in the PRD, but materially affects implementation.
2. **Prototype the Miner first.** It's the only feature with real architectural risk. Build Option B end-to-end on a single straight trunk before committing to the full trunk-and-branch layout and the other two roles.
3. **Simplify tool durability to consume-on-timer for v1.** Defer enchantment-aware drop calculation unless it's specifically wanted.
4. **Use Mixin freely.** With no public-API or save-compat obligations, replacing vanilla AI classes wholesale is the pragmatic path.

## Build order suggestion

1. Vanilla profession neutering + Fletcher/Mason replacement scaffolding.
2. Farmer (lightest, validates the Brain/Behavior approach).
3. Lumberjack (validates entity-driven block-breaking).
4. Miner Option B prototype on a single trunk.
5. Miner full layout (branches, supports, torches, rails, edge cases).
6. Loot redistribution + iterate during playtesting.
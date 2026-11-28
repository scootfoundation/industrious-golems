package com.scottcaldwell.industriousgolems.mine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The shape of the stone golem's mine, worked out from the world as it stands (nothing is remembered, so a mine survives a reload and
 * the player's edits).
 *
 * <p>The chest stands on the floor, and the floor is the floor of the whole mine: everything is cut from the chest's own level up. Round it the golem
 * clears a hub room, 5 x 5 and four high (it stands on its chest). From the middle
 * of each wall a trunk runs out in each cardinal direction, 3 wide and 3 high, up to {@link #ARM_MAX} slices long. The first slice of an arm and every
 * {@link #SUPPORT_EVERY}th after it is a mineshaft brace (a fence post each side, a plank beam across the top, a torch on the middle plank facing the
 * chest). The golem digs up to the next brace's slice, builds the brace, and only then digs on, so it is never more than a brace's spacing from
 * support. Missing floor is bridged with cobblestone when the gap is short ({@link #MAX_GAP}); anything else it cannot deal with (liquid, a
 * long drop, something that falls, a block it does not dig) ends that arm. {@link #next} says what to do about the mine next.
 */
public final class Trunk {

    /** How far the hub room reaches from the chest on each side: the hub is 2 * HUB + 1 blocks across. */
    public static final int HUB = 2;
    /** How high the hub is: the golem stands up on its chest, and needs the room. */
    public static final int HUB_HEIGHT = 4;
    /** How high a trunk is (and wide: 3 x 3). */
    public static final int HEIGHT = 3;
    /** The most slices in one arm. 64 is four chunks: a whole mine (two arms) stays inside the area a player nearby keeps loaded. */
    public static final int ARM_MAX = 64;
    /** Slices between one brace and the next; slice 1 (the first block of the arm) has one. */
    public static final int SUPPORT_EVERY = 5;
    /** The longest run of missing floor it will bridge. */
    public static final int MAX_GAP = 5;
    /** How far from the golem it looks for ore, and how far from the mine's axes an ore may be and still be worked. */
    private static final int VEIN_SCAN = 6;
    public static final int VEIN_BAND = 5;

    private static final Direction[] ARMS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private Trunk() { }

    /** A block to place; a torch also says which way it faces (it hangs on the plank behind it). */
    public record Placement(BlockPos pos, Kit part, Direction facing) {
        public Placement(BlockPos pos, Kit part) {
            this(pos, part, null);
        }
    }

    /**
     * What to do next: dig {@code dig}, or (when it is null) place the {@code builds}; {@code plug} says they seal liquid, which comes before
     * everything else. {@code wanted} is all the digging the same part of the mine still asks for, {@code dig} among it: the golem takes
     * those in one column with the same swing.
     */
    public record Step(BlockPos dig, List<Placement> builds, boolean plug, Direction arm, int slice, List<BlockPos> wanted) {
        public boolean digs() {
            return dig != null;
        }
    }

    private enum Role { AIR, POST, BEAM, TORCH, FLOOR }

    private record Cell(BlockPos pos, Role role, Direction facing) {
        Cell(BlockPos pos, Role role) {
            this(pos, role, null);
        }
    }

    private record Result(boolean blocked, List<BlockPos> dig, List<Placement> builds, List<Placement> plugs) {
        Result(boolean blocked, List<BlockPos> dig, List<Placement> builds) {
            this(blocked, dig, builds, List.of());
        }

        boolean done() {
            return !blocked && dig.isEmpty() && builds.isEmpty() && plugs.isEmpty();
        }
    }

    private record Frontier(Direction arm, int slice, Result result) { }

    // ---- the plan -------------------------------------------------------------------------------------------------

    /**
     * The next thing to do, or null when there is nothing: the mine is finished (every arm at its length, or ended), or something stops it.
     * The hub comes first, then the arms, taken a section at a time, the least advanced first, except that a golem already inside an arm carries on down it.
     *
     * @param from    where the golem is, to take the nearest of equal choices
     * @param ignored blocks it has given up on for now
     */
    public static Step next(ServerLevel level, BlockPos chest, BlockPos from, Predicate<BlockPos> ignored) {
        return next(level, chest, from, ignored, pos -> false);
    }

    /** @param taken blocks another golem is digging or placing: left to it, and an arm with nothing else to do is passed over */
    public static Step next(ServerLevel level, BlockPos chest, BlockPos from, Predicate<BlockPos> ignored, Predicate<BlockPos> taken) {
        BlockPos vein = vein(level, chest, from, ignored.or(taken));
        if (vein != null) { // with whatever ore is stacked above or below it
            List<BlockPos> stacked = new ArrayList<>();
            for (int dy = -2; dy <= 2; dy++) {
                BlockPos at = vein.above(dy);
                if (at.getY() >= chest.getY() && level.getBlockState(at).is(com.scottcaldwell.industriousgolems.ModTags.MINER_ORES) && !ignored.or(taken).test(at)) stacked.add(at);
            }
            return new Step(vein, List.of(), false, null, -1, stacked);
        }
        Result hub = assess(level, hubCells(chest), () -> true, ignored);
        if (!hub.done()) return step(level, hub, null, 0, from, taken);

        // no changing arm without a reason: while the golem is inside an arm it carries on down that one. Back at the hub (after a trip to the
        // chest) the arms are taken a section at a time, the least advanced first, the nearest of equals, so the mine grows as a plus.
        Frontier best = null;
        int bestSection = Integer.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (Direction arm : ARMS) {
            Frontier front = frontier(level, chest, arm, ignored);
            if (front == null || busy(front.result(), taken)) continue;
            if (insideArm(chest, arm, from)) { best = front; break; }
            int section = (front.slice() - 1) / SUPPORT_EVERY;
            double distance = sliceCentre(chest, arm, front.slice()).distSqr(from);
            if (section < bestSection || (section == bestSection && distance < bestDistance)) {
                best = front;
                bestSection = section;
                bestDistance = distance;
            }
        }
        return best == null ? null : step(level, best.result(), best.arm(), best.slice(), from, taken);
    }

    /**
     * Ore showing in a wall, ceiling or cave the golem is working near: dug before the plain rock, and what it opens up shows the next block of the
     * vein, so a vein is followed as far as the golem can reach it from where it can stand. Never the floor, which it walks on. Only ore within
     * {@link #VEIN_BAND} of one of the mine's axes, so it does not wander off after every ore in sight.
     */
    private static BlockPos vein(ServerLevel level, BlockPos chest, BlockPos from, Predicate<BlockPos> ignored) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(from.offset(-VEIN_SCAN, -3, -VEIN_SCAN), from.offset(VEIN_SCAN, 4, VEIN_SCAN))) {
            int dx = pos.getX() - chest.getX(), dz = pos.getZ() - chest.getZ(), dy = pos.getY() - chest.getY();
            if (dy < 0 || dy > HEIGHT + 1 || Math.min(Math.abs(dx), Math.abs(dz)) > VEIN_BAND || Math.max(Math.abs(dx), Math.abs(dz)) > HUB + ARM_MAX + VEIN_BAND) continue;
            if (!level.isLoaded(pos) || !level.getBlockState(pos).is(com.scottcaldwell.industriousgolems.ModTags.MINER_ORES) || ignored.test(pos)) continue;
            if (!MineFinder.isMineable(level, pos) || MineFinder.fistSpot(level, pos, from) == null) continue;
            double distance = pos.distSqr(from);
            if (distance < bestDistance) {
                best = pos.immutable();
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Whether any arm still has work (or the hub does): false once the mine is as big as it will get. */
    public static boolean hasWork(ServerLevel level, BlockPos chest) {
        return next(level, chest, chest, pos -> false) != null;
    }

    /** Whether all there is to do here is already another golem's. */
    private static boolean busy(Result result, Predicate<BlockPos> taken) {
        if (!result.plugs().isEmpty()) return taken.test(result.plugs().get(0).pos());
        if (!result.dig().isEmpty()) return result.dig().stream().allMatch(taken);
        return !result.builds().isEmpty() && taken.test(result.builds().get(0).pos());
    }

    private static Step step(ServerLevel level, Result result, Direction arm, int slice, BlockPos from, Predicate<BlockPos> taken) {
        if (busy(result, taken)) return null;
        // liquid in or beside the slice is sealed with cobblestone before anything else is done, even where it ends the arm
        if (!result.plugs().isEmpty()) return new Step(null, result.plugs(), true, arm, slice, List.of());
        if (result.blocked()) return null;
        if (result.dig().isEmpty()) return new Step(null, result.builds(), false, arm, slice, List.of());
        // some of the rock may not be dug yet from where it lies (it has to be open to the air on a side): take the ones that can be
        BlockPos dig = result.dig().stream().filter(p -> !taken.test(p) && MineFinder.isMineable(level, p, true) && MineFinder.fistSpot(level, p, from) != null).min(Comparator.comparingDouble(p -> p.distSqr(from))).orElse(null);
        return dig == null ? null : new Step(dig, List.of(), false, arm, slice, result.dig().stream().filter(p -> !taken.test(p)).toList());
    }

    /** Whether {@code pos} is in the arm's trunk, past the hub (so the golem is out in it rather than around the chest). */
    private static boolean insideArm(BlockPos chest, Direction arm, BlockPos pos) {
        int along = (pos.getX() - chest.getX()) * arm.getStepX() + (pos.getZ() - chest.getZ()) * arm.getStepZ();
        int across = Math.abs((pos.getX() - chest.getX()) * arm.getStepZ() - (pos.getZ() - chest.getZ()) * arm.getStepX());
        return along > HUB && across <= 1 && pos.getY() >= chest.getY() - 1 && pos.getY() <= chest.getY() + HEIGHT;
    }

    /** The first slice of the arm that is not finished, or null if the arm is complete or has ended. */
    private static Frontier frontier(ServerLevel level, BlockPos chest, Direction arm, Predicate<BlockPos> ignored) {
        for (int k = 1; k <= ARM_MAX; k++) {
            final int slice = k;
            Result r = assess(level, sliceCells(chest, arm, k), () -> bridgeable(level, chest, arm, slice), ignored);
            if (r.blocked() && r.plugs().isEmpty()) return null;
            if (!r.done()) return new Frontier(arm, k, r);
        }
        return null;
    }

    // ---- geometry -------------------------------------------------------------------------------------------------

    /** Whether slice {@code slice} (counted from 1) carries a brace: the first, then every {@link #SUPPORT_EVERY}th. */
    public static boolean isBrace(int slice) {
        return slice % SUPPORT_EVERY == 1;
    }

    public static BlockPos sliceCentre(BlockPos chest, Direction arm, int slice) {
        return chest.relative(arm, HUB + slice);
    }

    private static List<Cell> hubCells(BlockPos chest) {
        List<Cell> cells = new ArrayList<>();
        for (int dx = -HUB; dx <= HUB; dx++) {
            for (int dz = -HUB; dz <= HUB; dz++) {
                cells.add(new Cell(chest.offset(dx, -1, dz), Role.FLOOR));
                for (int h = 0; h < HUB_HEIGHT; h++) {
                    if (dx == 0 && dz == 0 && h == 0) continue; // the chest itself
                    cells.add(new Cell(chest.offset(dx, h, dz), Role.AIR));
                }
            }
        }
        return cells;
    }

    private static List<Cell> sliceCells(BlockPos chest, Direction arm, int slice) {
        BlockPos middle = sliceCentre(chest, arm, slice);
        Direction side = arm.getClockWise();
        boolean support = isBrace(slice);
        List<Cell> cells = new ArrayList<>(13);
        for (int l = -1; l <= 1; l++) {
            BlockPos column = middle.relative(side, l);
            cells.add(new Cell(column.below(), Role.FLOOR));
            for (int h = 0; h < HEIGHT; h++) {
                Role role = Role.AIR;
                if (support) role = h == HEIGHT - 1 ? Role.BEAM : l != 0 ? Role.POST : Role.AIR;
                cells.add(new Cell(column.above(h), role));
            }
        }
        // the torch hangs on the middle plank, on the side facing the chest
        if (support) cells.add(new Cell(middle.relative(arm.getOpposite()).above(HEIGHT - 1), Role.TORCH, arm.getOpposite()));
        return cells;
    }

    /** Whether the missing floor of this slice is a short gap: solid floor turns up within {@link #MAX_GAP} slices. */
    private static boolean bridgeable(ServerLevel level, BlockPos chest, Direction arm, int slice) {
        for (int k = slice; k <= slice + MAX_GAP; k++) {
            BlockPos floor = sliceCentre(chest, arm, k).below();
            if (!level.isLoaded(floor)) return false;
            BlockState state = level.getBlockState(floor);
            if (!state.getCollisionShape(level, floor).isEmpty() && state.getFluidState().isEmpty()) return true;
        }
        return false;
    }

    // ---- assessing a slice ----------------------------------------------------------------------------------------

    private static boolean open(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private static Result assess(ServerLevel level, List<Cell> cells, java.util.function.BooleanSupplier bridgeable, Predicate<BlockPos> ignored) {
        List<BlockPos> dig = new ArrayList<>();
        List<Placement> floors = new ArrayList<>(), posts = new ArrayList<>(), beams = new ArrayList<>(), torches = new ArrayList<>();
        List<Placement> plugs = new ArrayList<>();
        java.util.Set<BlockPos> inside = new java.util.HashSet<>();
        for (Cell cell : cells) inside.add(cell.pos());
        boolean blocked = false;
        for (Cell cell : cells) {
            BlockPos pos = cell.pos();
            if (!level.isLoaded(pos)) return new Result(true, List.of(), List.of());
            BlockState state = level.getBlockState(pos);
            switch (cell.role()) {
                case FLOOR -> {
                    if (!state.getFluidState().isEmpty()) { // a pool under the corridor: fill it, and it is floor
                        if (ignored.test(pos)) blocked = true;
                        else plugs.add(new Placement(pos, Kit.COBBLE));
                    } else if (state.getCollisionShape(level, pos).isEmpty()) {
                        if (!bridgeable.getAsBoolean() || ignored.test(pos)) blocked = true;
                        else {
                            floors.add(new Placement(pos, Kit.COBBLE));
                        }
                    }
                }
                case AIR -> {
                    if (open(level, pos, state)) continue;
                    if (!state.getFluidState().isEmpty() && !ignored.test(pos)) { plugs.add(new Placement(pos, Kit.COBBLE)); blocked = true; }
                    else if (MineFinder.isRock(state) && !ignored.test(pos)) dig.add(pos);
                    else blocked = true;
                }
                case POST, BEAM, TORCH -> {
                    Role role = cell.role();
                    Kit part = role == Role.POST ? Kit.FENCE : role == Role.BEAM ? Kit.PLANKS : Kit.TORCH;
                    boolean there = switch (role) {
                        case POST -> state.is(BlockTags.FENCES);
                        case BEAM -> state.is(BlockTags.PLANKS);
                        default -> state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH);
                    };
                    if (there) continue;
                    if (open(level, pos, state)) {
                        if (role == Role.TORCH && !state.isAir()) continue; // something else is there: no torch, no fuss
                        if (ignored.test(pos)) { blocked = true; continue; }
                        (role == Role.POST ? posts : role == Role.BEAM ? beams : torches).add(new Placement(pos, part, cell.facing()));
                    } else if (!state.getFluidState().isEmpty() && !ignored.test(pos)) { plugs.add(new Placement(pos, Kit.COBBLE)); blocked = true; }
                    else if (MineFinder.isRock(state) && !ignored.test(pos)) dig.add(pos);
                    else blocked = true;
                }
            }
        }
        // liquid just outside the slice, which would flow in once the rock between is dug
        for (Cell cell : cells) {
            if (cell.role() == Role.FLOOR) continue;
            for (Direction d : Direction.values()) {
                BlockPos n = cell.pos().relative(d);
                if (inside.contains(n) || !level.isLoaded(n) || level.getBlockState(n).getFluidState().isEmpty()) continue;
                Placement plug = new Placement(n, Kit.COBBLE);
                if (ignored.test(n)) blocked = true;
                else if (!plugs.contains(plug)) plugs.add(plug);
            }
        }
        List<Placement> builds = new ArrayList<>(floors);
        builds.addAll(posts);
        builds.addAll(beams);
        builds.addAll(torches);
        return new Result(blocked, dig, builds, plugs);
    }
}

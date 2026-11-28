package com.scottcaldwell.industriousgolems.herd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction.Axis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An enclosed area animals are kept in: the floor cells an animal could get to from where it stands, found by flood fill. It can
 * step or jump up one block and drop down three, so what holds it is a fence, wall or shut gate, anything two or more high, or a
 * long drop. Anything larger than {@link #MAX_CELLS} leaks and is not a pen.
 */
public final class HerdPen {

    public static final int MAX_CELLS = 64;
    /** How far from the job site the herder looks for animals. */
    public static final int SEARCH_RADIUS = 16;

    public final Set<BlockPos> cells;
    public final List<Animal> animals = new ArrayList<>();

    private HerdPen(Set<BlockPos> cells) {
        this.cells = cells;
    }

    /** The animals a herder manages. */
    public static boolean managed(Entity entity) {
        return entity instanceof Cow || entity instanceof Sheep || entity instanceof Pig || entity instanceof Chicken;
    }

    /** All enclosed pens with at least one managed animal within reach of {@code center}. */
    public static List<HerdPen> find(Level level, BlockPos center) {
        AABB box = new AABB(center).inflate(SEARCH_RADIUS, 4, SEARCH_RADIUS);
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, box, HerdPen::managed);
        List<HerdPen> pens = new ArrayList<>();
        for (Animal animal : animals) {
            BlockPos pos = animal.blockPosition();
            HerdPen existing = null;
            for (HerdPen pen : pens) {
                if (pen.cells.contains(pos)) { existing = pen; break; }
            }
            if (existing != null) {
                existing.animals.add(animal);
                continue;
            }
            Set<BlockPos> cells = cellsAround(level, pos);
            if (cells == null) continue; // not enclosed
            HerdPen pen = new HerdPen(cells);
            pen.animals.add(animal);
            pens.add(pen);
        }
        return pens;
    }

    /** Flood fills the floor cells reachable from {@code start}; null when it is not standable or the area leaks. */
    public static Set<BlockPos> cellsAround(Level level, BlockPos start) {
        if (!standable(level, start)) return null;
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(dir);
                BlockPos reached = null;
                if (standable(level, next)) {
                    reached = next;
                } else if (isRise(level, next) && standable(level, next.above()) && clear(level, current.above(2))) {
                    reached = next.above(); // up a step, or a jump onto a block
                } else {
                    reached = drop(level, next);
                }
                if (reached == null || !seen.add(reached)) continue;
                if (seen.size() > MAX_CELLS) return null;
                queue.add(reached);
            }
        }
        return seen;
    }

    /** Feet and head are free and there is something to stand on. */
    private static boolean standable(Level level, BlockPos feet) {
        if (!clear(level, feet) || !clear(level, feet.above())) return false;
        VoxelShape floor = shape(level, feet.below());
        return !floor.isEmpty() && floor.max(Axis.Y) <= 1.0; // (not the top of a fence or wall: that is what holds them in)
    }

    private static boolean clear(Level level, BlockPos pos) {
        return shape(level, pos).isEmpty();
    }

    /** Something an animal can step or jump onto (a slab, a block), as opposed to a fence or wall. */
    private static boolean isRise(Level level, BlockPos pos) {
        VoxelShape shape = shape(level, pos);
        return !shape.isEmpty() && shape.max(Axis.Y) <= 1.0;
    }

    /** Where an animal walking off into this cell would land, up to three blocks down; null when it would not go. */
    private static BlockPos drop(Level level, BlockPos pos) {
        if (!clear(level, pos) || !clear(level, pos.above())) return null;
        for (int down = 1; down <= 3; down++) {
            BlockPos at = pos.below(down);
            if (!clear(level, at)) return null;
            if (standable(level, at)) return at;
        }
        return null;
    }

    /** Whether a fence gate is part of what holds these cells in. */
    public static boolean hasGate(Level level, Set<BlockPos> cells) {
        for (BlockPos cell : cells) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(cell.relative(dir)).getBlock() instanceof FenceGateBlock) return true;
            }
        }
        return false;
    }

    private static VoxelShape shape(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos);
    }
}
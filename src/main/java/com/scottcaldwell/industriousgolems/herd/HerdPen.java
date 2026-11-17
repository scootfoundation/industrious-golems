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
 * An enclosed area animals are kept in: the walkable floor cells reachable from an animal without passing a fence,
 * wall, closed gate or drop, found by flood fill. Anything larger than {@link #MAX_CELLS} leaks and is not a pen.
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
                } else if (isStep(level, next) && standable(level, next.above()) && clear(level, current.above(2))) {
                    reached = next.above(); // up a slab-high step
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
        return clear(level, feet) && clear(level, feet.above()) && !shape(level, feet.below()).isEmpty();
    }

    private static boolean clear(Level level, BlockPos pos) {
        return shape(level, pos).isEmpty();
    }

    /** A low block (slab, carpet) an animal can step onto, as opposed to a fence or wall. */
    private static boolean isStep(Level level, BlockPos pos) {
        VoxelShape shape = shape(level, pos);
        return !shape.isEmpty() && shape.max(Axis.Y) <= 0.5;
    }

    private static VoxelShape shape(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos);
    }
}
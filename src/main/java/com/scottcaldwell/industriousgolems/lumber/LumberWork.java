package com.scottcaldwell.industriousgolems.lumber;

import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.behavior.ContainerInteraction;
import com.scottcaldwell.industriousgolems.behavior.TaskState;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import com.scottcaldwell.industriousgolems.needs.Job;
import com.scottcaldwell.industriousgolems.needs.Jobs;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;

/**
 * What the lumberjack does, in order: put away whatever it carries, clear up what lies about, fetch a sapling when there is
 * somewhere to plant one (all three are the dispatcher's, see {@code StartTask}), then {@link #plan plant} the sapling in hand,
 * and only after all of that {@link #fell fell} a tree. Planting and felling are separate tasks ({@link PlantSapling},
 * {@link FellTree}); a sapling is only ever planted from the hand, which is filled from the chest or from what the leaves dropped.
 */
public final class LumberWork {
    private LumberWork() { }

    /** How long a planting-place search is reused: it scans a few thousand blocks and several callers ask in the same tick. */
    private static final long PLACE_CACHE_TICKS = 10;

    /** A sapling the golem can grow on its own: dark oak and pale oak only grow from a 2x2 planting, so they are only put away. */
    public static boolean plantable(ItemStack stack) {
        return stack.is(ItemTags.SAPLINGS) && !stack.is(Items.DARK_OAK_SAPLING) && !stack.is(Items.PALE_OAK_SAPLING);
    }

    /**
     * In order: plant the sapling in hand if there is somewhere for it (else it goes back in the chest); with a free hand, fetch a sapling when
     * there is somewhere to plant one; and last, with a free hand, fell a tree.
     */
    public static Stream<Job> jobs(ServerLevel level, WorkGolem golem) {
        BlockPos site = golem.jobSite(level);
        if (site == null || !EventHooks.canEntityGrief(level, golem)) return Stream.empty();
        TaskState state = golem.getData(ModAttachments.TASK);
        if (golem.isCarrying()) {
            if (!plantable(golem.carried())) return Stream.empty();
            return Jobs.of(() -> {
                BlockPos at = plantingPlace(level, golem, site, state, level.getGameTime(), golem.carried());
                return at == null ? null : Job.now(new PlantSapling.Plan(at));
            });
        }
        return Jobs.of(
                () -> plantingPlace(level, golem, site, state, level.getGameTime(), null) != null ? Job.needing("saplings") : null,
                () -> {
                    long now = level.getGameTime();
                    TreeFinder.Tree tree = TreeFinder.find(level, site, golem.blockPosition(), pos -> state.ignoresBlock(pos, now));
                    return tree == null ? null : Job.whenTidy(new FellTree.FellPlan(tree.logs(), tree.base()));
                });
    }

    /** The best open ground near the chest (see {@link Planting}); the golem does not remember where trees stood. {@code sapling} null means "any sapling would do". */
    private static BlockPos plantingPlace(ServerLevel level, WorkGolem golem, BlockPos site, TaskState state, long now, ItemStack sapling) {
        if (state.plantSpotFresh(now, PLACE_CACHE_TICKS)) {
            BlockPos cached = state.plantSpot();
            if (cached == null) return null; // looked a moment ago and found nothing
            if (!state.ignoresBlock(cached, now) && canPlantAt(level, cached, sapling)) return cached;
        }
        BlockPos found = Planting.find(level, golem, site, pos -> state.ignoresBlock(pos, now));
        state.cachePlantSpot(found, now);
        return found;
    }

    /** Whether a sapling can go here: the place is free (air, or a plant that gives way) and the sapling would survive on it. */
    static boolean canPlantAt(ServerLevel level, BlockPos pos, ItemStack sapling) {
        if (!isFree(level.getBlockState(pos))) return false;
        if (sapling == null) return Blocks.OAK_SAPLING.defaultBlockState().canSurvive(level, pos);
        return sapling.getItem() instanceof BlockItem item && item.getBlock().defaultBlockState().canSurvive(level, pos);
    }

    /** Air, or grass, ferns and flowers that a sapling simply replaces (never water). */
    static boolean isFree(BlockState state) {
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }
}

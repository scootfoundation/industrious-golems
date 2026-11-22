package com.scottcaldwell.industriousgolems.lumber;

import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.behavior.PickUp;
import com.scottcaldwell.industriousgolems.behavior.ContainerInteraction;
import com.scottcaldwell.industriousgolems.behavior.TaskState;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.EventHooks;

/**
 * What the lumberjack does next, in order: put away whatever it carries (the dispatcher does that before anything else it can't
 * use), clear up what lies about, plant a sapling where one can go, and only then fell a tree. Planting and felling are separate
 * tasks ({@link PlantSapling}, {@link FellTree}); a sapling is only ever planted from the hand, which is filled from the chest or
 * from what the leaves dropped.
 */
public final class LumberWork {
    private LumberWork() { }

    /** A {@link PlantSapling.Plan} or a {@link FellTree.FellPlan}, or null when the dispatcher should deposit, pick up or fetch instead. */
    public static Object plan(ServerLevel level, WorkGolem golem) {
        BlockPos site = golem.jobSite(level);
        if (site == null || !EventHooks.canEntityGrief(level, golem)) return null;
        TaskState state = golem.getData(ModAttachments.TASK);
        long now = level.getGameTime();
        if (golem.isCarrying()) {
            // a sapling goes in the ground if there is a place for it; anything else (or no place) is taken back to the chest
            if (!golem.carried().is(ItemTags.SAPLINGS)) return null;
            BlockPos at = plantingPlace(level, golem, site, state, now, golem.carried());
            return at == null ? null : new PlantSapling.Plan(at);
        }
        if (PickUp.plan(level, golem) != null) return null; // what the last job dropped, first
        // somewhere to plant and saplings in the chest to do it with: fetch one before felling anything
        if (plantingPlace(level, golem, site, state, now, null) != null && ContainerInteraction.planSupply(level, golem) != null) return null;
        TreeFinder.Tree tree = TreeFinder.find(level, site, golem.blockPosition(), pos -> state.ignoresBlock(pos, now));
        return tree == null ? null : new FellTree.FellPlan(tree.logs(), tree.base());
    }

    /** A sapling from the chest is wanted when there is somewhere to plant it. */
    public static String wants(ServerLevel level, WorkGolem golem) {
        if (golem.isCarrying()) return null;
        BlockPos site = golem.jobSite(level);
        if (site == null) return null;
        TaskState state = golem.getData(ModAttachments.TASK);
        return plantingPlace(level, golem, site, state, level.getGameTime(), null) != null ? "saplings" : null;
    }

    /** The last tree's stump first, then open ground near the chest. {@code sapling} null means "any sapling would do". */
    private static BlockPos plantingPlace(ServerLevel level, WorkGolem golem, BlockPos site, TaskState state, long now, ItemStack sapling) {
        BlockPos stump = golem.replantPos();
        if (stump != null && !state.ignoresBlock(stump, now) && canPlantAt(level, stump, sapling)) return stump;
        return Planting.find(level, golem, site, pos -> state.ignoresBlock(pos, now));
    }

    static boolean canPlantAt(ServerLevel level, BlockPos pos, ItemStack sapling) {
        if (!level.getBlockState(pos).isAir()) return false;
        if (sapling == null) return net.minecraft.world.level.block.Blocks.OAK_SAPLING.defaultBlockState().canSurvive(level, pos);
        return sapling.getItem() instanceof BlockItem item && item.getBlock().defaultBlockState().canSurvive(level, pos);
    }
}

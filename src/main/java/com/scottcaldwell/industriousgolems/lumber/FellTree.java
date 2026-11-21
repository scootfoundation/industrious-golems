package com.scottcaldwell.industriousgolems.lumber;

import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.behavior.PickUp;
import com.scottcaldwell.industriousgolems.behavior.TaskRunner;
import com.scottcaldwell.industriousgolems.behavior.TaskState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import com.scottcaldwell.industriousgolems.golem.Anim;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * Fells a natural tree from the top down. The lumberjack walks to the foot of the tree, sneezes and vanishes in a puff
 * of spores, and reappears standing on top of the crown. From there it mines straight down through the trunk's column,
 * bare-handed: the block under its feet breaks (leaves and logs alike, dropped as for a player and carried off a stack
 * at a time, see PickUp), it drops one block, and again, until it lands on the ground. Then it steps back and replants a
 * sapling where the trunk stood, if it is holding one. Runs in the exclusive task activity.
 */
public class FellTree extends TaskRunner<FellTree.FellPlan> {

    /** How near the golem gets to the foot of the tree before it sneezes. */
    private static final double SNEEZE_DIST = 3.5;
    /** Ticks spent trying to get to the foot of the tree or the planting spot before giving up. */
    private static final int STALL_TICKS = 600;
    /** How far from the sapling's centre the golem stands to plant it (9 pixels). */
    private static final double PLANT_DIST = 9.0 / 16.0;

    private enum Phase { WALK, SNEEZE, APPEAR, BREAK, STEP_BACK, PLANT, DONE }

    /** The tree chosen by {@link #plan}: chop {@code logs}, then replant at {@code replant}. */
    public record FellPlan(List<BlockPos> logs, BlockPos replant) { }

    private final List<BlockPos> remaining = new ArrayList<>();
    private Phase phase = Phase.DONE;
    private int timer;
    private int stalled;
    /** The spot on top of the crown the golem pops out at (its feet), or null when there is none. */
    private BlockPos top;
    private Vec3 plantSpot;

    public FellTree() {
        super(FellPlan.class, 1800);
    }

    // ---- choosing work ----------------------------------------------------------------------------------------

    /**
     * With a free hand or a sapling in it: a tree to fell (the sapling then goes in where it stood). With a sapling in
     * hand and a stump to replant: just the planting. Null when there is nothing to do, or mob griefing is off.
     */
    public static Object plan(ServerLevel level, WorkGolem golem) {
        BlockPos site = golem.jobSite(level);
        if (site == null || !EventHooks.canEntityGrief(level, golem)) return null;
        boolean sapling = golem.carried().is(ItemTags.SAPLINGS);
        if (golem.isCarrying() && !sapling) return null;
        BlockPos stump = golem.replantPos();
        if (sapling && stump != null && canPlantAt(level, stump, golem.carried())) return new FellPlan(List.of(), stump);
        if (leftToCollect(level, golem)) return null; // the last tree's drops first
        TaskState state = golem.getData(ModAttachments.TASK);
        long now = level.getGameTime();
        TreeFinder.Tree tree = TreeFinder.find(level, site, golem.blockPosition(), pos -> state.ignoresBlock(pos, now));
        if (tree != null) return new FellPlan(tree.logs(), tree.base());
        if (!sapling) return null;
        BlockPos spot = Planting.find(level, golem, site, pos -> state.ignoresBlock(pos, now)); // no tree to fell: grow one
        return spot == null ? null : new FellPlan(List.of(), spot);
    }

    /** Whether something the work dropped is still lying about, to be carried to the chest before more work is started. */
    private static boolean leftToCollect(ServerLevel level, WorkGolem golem) {
        return !golem.isCarrying() && PickUp.plan(level, golem) != null;
    }

    /** A sapling from the chest would replant the last tree's stump; a tree still to fell wants one too. */
    public static String wants(ServerLevel level, WorkGolem golem) {
        BlockPos stump = golem.replantPos();
        if (golem.isCarrying()) return null;
        if (stump != null && level.getBlockState(stump).isAir()) return "saplings";
        BlockPos site = golem.jobSite(level);
        if (site == null) return null;
        TaskState state = golem.getData(ModAttachments.TASK);
        long now = level.getGameTime();
        if (TreeFinder.find(level, site, golem.blockPosition(), pos -> state.ignoresBlock(pos, now)) != null) return "saplings";
        return Planting.find(level, golem, site, pos -> state.ignoresBlock(pos, now)) != null ? "saplings" : null;
    }

    private static boolean canPlantAt(ServerLevel level, BlockPos pos, ItemStack sapling) {
        return sapling.getItem() instanceof BlockItem item && level.getBlockState(pos).isAir()
                && item.getBlock().defaultBlockState().canSurvive(level, pos);
    }

    // ---- the job ------------------------------------------------------------------------------------------------

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        remaining.clear();
        remaining.addAll(plan.logs());
        golem.setReplantPos(plan.replant());
        top = remaining.isEmpty() ? null : crownTop(level, plan.logs(), plan.replant());
        plantSpot = null;
        stalled = 0;
        phase = remaining.isEmpty() ? Phase.STEP_BACK : Phase.WALK;
        timer = 0;
        golem.setPickupSuspended(true); // the drops are left lying until the felling is done, then picked up in turn
    }

    @Override
    protected boolean allowedNow(ServerLevel level, WorkGolem golem) {
        return phase != Phase.DONE && super.allowedNow(level, golem);
    }

    /**
     * Where to stand on top of the tree: the trunk's highest log, then up through whatever leaves and logs sit directly over
     * it. Null when there is no room to stand there.
     */
    static BlockPos crownTop(ServerLevel level, List<BlockPos> logs, BlockPos base) {
        BlockPos high = logs.get(0);
        for (BlockPos log : logs) {
            if (log.getY() > high.getY() || (log.getY() == high.getY() && log.distSqr(base) < high.distSqr(base))) high = log;
        }
        BlockPos column = high;
        while (isTreeBlock(level, column.above())) column = column.above();
        BlockPos feet = column.above();
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) return null;
        return feet;
    }

    private static boolean isTreeBlock(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
    }

    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        switch (phase) {
            case WALK -> {
                BlockPos base = plan.replant();
                lookAt(golem, new BlockPosTracker(Vec3.atCenterOf(base)));
                double dx = golem.getX() - (base.getX() + 0.5), dz = golem.getZ() - (base.getZ() + 0.5);
                if (Math.sqrt(dx * dx + dz * dz) > SNEEZE_DIST) {
                    if (++stalled > STALL_TICKS) { giveUp(golem, time); return; }
                    walkTo(golem, new BlockPosTracker(base), 2);
                    return;
                }
                if (top == null) { phase = Phase.STEP_BACK; return; } // no room on top: fall back to just replanting
                holdStill(golem);
                golem.playClip(Anim.M_SNEEZE_VANISH);
                phase = Phase.SNEEZE;
                timer = 0;
            }
            case SNEEZE -> {
                holdStill(golem);
                if (timer == Math.round(Anim.M_VANISH_AT * 20) - 2) puff(level, golem.position().add(0, 0.6, 0));
                if (++timer < Math.round(Anim.M_VANISH_AT * 20)) return;
                // gone in a puff of spores, and out again on top of the crown
                golem.teleportTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
                golem.getNavigation().stop();
                golem.setDeltaMovement(Vec3.ZERO);
                puff(level, golem.position().add(0, 0.6, 0));
                golem.playClip(Anim.M_SNEEZE_APPEAR);
                phase = Phase.APPEAR;
                timer = 0;
            }
            case APPEAR -> {
                holdStill(golem);
                if (++timer < Math.round(Anim.M_APPEAR_LEN * 20)) return;
                startBreak(golem);
            }
            case BREAK -> {
                holdStill(golem);
                timer++;
                if (timer == Math.round(Anim.M_BREAK_AT * 20)) {
                    golem.swing(InteractionHand.MAIN_HAND);
                    BlockPos below = golem.blockPosition().below();
                    if (isTreeBlock(level, below)) level.destroyBlock(below, true, golem);
                }
                if (timer < Math.round(Anim.M_BREAK_LEN * 20) || !golem.onGround()) return;
                if (isTreeBlock(level, golem.blockPosition().below())) startBreak(golem);
                else { golem.stopClip(); phase = Phase.STEP_BACK; stalled = 0; }
            }
            case STEP_BACK -> {
                // down on the ground: step back from the stump and plant, if holding a sapling
                if (!golem.carried().is(ItemTags.SAPLINGS) || !canPlantAt(level, plan.replant(), golem.carried())) { finish(); return; }
                if (plantSpot == null) plantSpot = chooseSpot(level, golem, plan.replant());
                if (plantSpot == null) { finish(); return; }
                lookAt(golem, new BlockPosTracker(Vec3.atCenterOf(plan.replant())));
                if (!standAt(golem, plantSpot, 0.9)) {
                    if (++stalled > STALL_TICKS) giveUp(golem, time);
                    return;
                }
                holdStill(golem);
                golem.playClip(Anim.M_PLANT);
                phase = Phase.PLANT;
                timer = 0;
            }
            case PLANT -> {
                holdStill(golem);
                lookAt(golem, new BlockPosTracker(Vec3.atCenterOf(plan.replant())));
                timer++;
                if (timer == Math.round(Anim.M_PLANT_AT * 20)) plant(level, golem, plan.replant());
                if (timer >= Math.round(Anim.M_PLANT_LEN * 20)) finish();
            }
            case DONE -> { }
        }
    }

    /** It cannot get there: leave this tree or spot alone for a while rather than trying it again at once. */
    private void giveUp(WorkGolem golem, long time) {
        golem.getData(ModAttachments.TASK).ignoreBlock(plan.replant(), time + 3000);
        finish();
    }

    private void startBreak(WorkGolem golem) {
        golem.playClip(Anim.M_BREAK_BELOW);
        phase = Phase.BREAK;
        timer = 0;
    }

    /** A free spot a plant-distance from the sapling's place, on the side the golem is already on if it can be. */
    private static Vec3 chooseSpot(ServerLevel level, WorkGolem golem, BlockPos at) {
        Vec3 centre = Vec3.atCenterOf(at);
        Vec3 away = golem.position().subtract(centre).multiply(1, 0, 1);
        double best = -1;
        Vec3 pick = null;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            Vec3 spot = new Vec3(centre.x + d.getStepX() * PLANT_DIST, at.getY(), centre.z + d.getStepZ() * PLANT_DIST);
            BlockPos feet = BlockPos.containing(spot);
            if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) continue;
            double score = away.x * d.getStepX() + away.z * d.getStepZ();
            if (pick == null || score > best) { best = score; pick = spot; }
        }
        return pick;
    }

    private static void puff(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y, at.z, 24, 0.35, 0.3, 0.35, 0.05);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0.03);
    }

    private void plant(ServerLevel level, WorkGolem golem, BlockPos pos) {
        ItemStack stack = golem.carried();
        if (!stack.is(ItemTags.SAPLINGS) || !canPlantAt(level, pos, stack)) return;
        golem.swing(InteractionHand.MAIN_HAND);
        level.setBlockAndUpdate(pos, ((BlockItem) stack.getItem()).getBlock().defaultBlockState());
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        stack.shrink(1);
        if (stack.isEmpty()) golem.setCarried(ItemStack.EMPTY);
        golem.setReplantPos(null);
    }

    @Override
    protected void end(ServerLevel level, WorkGolem golem, long time) {
        // interrupted up in the tree: poof back to the ground rather than be stuck on a leaf
        BlockPos base = plan == null ? null : plan.replant();
        if (base != null && golem.getY() > base.getY() + 1.5) {
            golem.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        }
        golem.setPickupSuspended(false);
        remaining.clear();
        top = null;
        plantSpot = null;
        phase = Phase.DONE;
    }
}

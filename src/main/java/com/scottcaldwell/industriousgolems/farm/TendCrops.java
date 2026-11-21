package com.scottcaldwell.industriousgolems.farm;

import com.scottcaldwell.industriousgolems.behavior.Machine;
import com.scottcaldwell.industriousgolems.behavior.TaskRunner;
import com.scottcaldwell.industriousgolems.golem.Anim;
import com.scottcaldwell.industriousgolems.golem.Face;
import com.scottcaldwell.industriousgolems.golem.Stand;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import com.scottcaldwell.industriousgolems.needs.ProfessionNeeds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import java.util.Comparator;
import java.util.Optional;

/**
 * Works the pre-tilled soil around the job site one plant at a time. The golem steps up to the plot it is working: it
 * breaks a ripe crop, which drops its yield as it would for a player, or sows the seeds it carries on bare farmland. It carries a single stack, so it reaps with a free hand (the drops are picked up, stack by
 * stack, and taken to the chest elsewhere) and sows whatever seeds it is holding. Runs in the exclusive task activity.
 * It stands a little way off the plant, facing it, as the hay golem's animations are written for.
 */
public class TendCrops extends TaskRunner<TendCrops.CropPlan> {

    private static final int RADIUS = 10;
    private static final int HEIGHT = 2;
    private static final int STALL_TICKS = 400;

    private enum State { APPROACH, WORKING, FOLLOW_THROUGH, DONE }

    public static final Machine.Graph<State> GRAPH = new Machine.Graph<>("tend-crops", "Farmer: reap a crop / sow seeds",
            "Hay golem. Stands a set distance from the plot, facing it, and plays the reap or sow clip; the crop is cut or the seed "
                    + "planted at a moment in the clip. Reaped crops are left to be picked up afterwards.", State.class)
            .start(State.APPROACH)
            .state(State.APPROACH, "Walking to the spot a set distance from the plot.")
            .state(State.WORKING, "On the spot, the clip playing; the reap or sow has not happened yet.")
            .state(State.FOLLOW_THROUGH, "The crop is cut (or the seed planted); the clip is finishing.")
            .state(State.DONE, "(end) Task over.")
            .edge(State.APPROACH, State.WORKING, "settled on the spot, facing the plot")
            .edge(State.APPROACH, State.DONE, "the plot is no longer ripe / bare or the hands changed, or no path for 20 s")
            .edge(State.WORKING, State.FOLLOW_THROUGH, "the clip reaches the harvest / plant moment")
            .edge(State.WORKING, State.DONE, "the clip ends without the act")
            .edge(State.FOLLOW_THROUGH, State.DONE, "the clip ends");

    public enum Kind { REAP, SOW }

    /** One plot to tend: reap the ripe crop at {@code pos}, or sow the carried seeds on the bare farmland there. */
    public record CropPlan(Kind kind, BlockPos pos) { }

    private final Machine.Run<State> machine = GRAPH.run();
    private int timer;
    private int stalled;
    private Vec3 spot;

    public TendCrops() {
        super(CropPlan.class, 1200);
    }

    // ---- choosing work ----------------------------------------------------------------------------------------

    /** Carrying seeds: sow the nearest bare farmland. Hands free: reap the nearest ripe crop. Otherwise nothing. */
    public static Object plan(ServerLevel level, WorkGolem golem) {
        BlockPos site = golem.jobSite(level);
        if (site == null || !EventHooks.canEntityGrief(level, golem)) return null;
        if (golem.isCarrying()) {
            if (!ProfessionNeeds.of(golem).isSupply("seeds", golem.carried())) return null;
            return nearest(level, site, golem, false).map(p -> new CropPlan(Kind.SOW, p)).orElse(null);
        }
        return nearest(level, site, golem, true).map(p -> new CropPlan(Kind.REAP, p)).orElse(null);
    }

    /** Seeds would let the golem sow some bare farmland. */
    public static String wants(ServerLevel level, WorkGolem golem) {
        BlockPos site = golem.jobSite(level);
        if (site == null || golem.isCarrying()) return null;
        return nearest(level, site, golem, false).isPresent() ? "seeds" : null;
    }

    private static Optional<BlockPos> nearest(ServerLevel level, BlockPos center, WorkGolem golem, boolean ripe) {
        BlockPos from = golem.blockPosition();
        return BlockPos.betweenClosedStream(center.offset(-RADIUS, -HEIGHT, -RADIUS), center.offset(RADIUS, HEIGHT, RADIUS))
                .filter(p -> ripe ? isRipe(level.getBlockState(p)) : isBareFarmland(level, p))
                .map(BlockPos::immutable)
                .min(Comparator.comparingDouble(p -> p.distSqr(from)));
    }

    private static boolean isRipe(BlockState state) {
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }

    private static boolean isBareFarmland(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).is(Blocks.FARMLAND);
    }

    // ---- the job ------------------------------------------------------------------------------------------------

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        machine.reset();
        stalled = 0;
        spot = null;
        golem.setPickupSuspended(true); // what a crop drops is picked up afterwards, as its own job
    }

    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        BlockPos pos = plan.pos();
        if (!machine.is(State.APPROACH)) {
            holdStill(golem);
            Face.toward(golem, Vec3.atCenterOf(pos));
            timer++;
            float at = plan.kind() == Kind.REAP ? Anim.H_HARVEST_AT : Anim.H_REPLANT_AT;
            float len = plan.kind() == Kind.REAP ? Anim.H_HARVEST_LEN : Anim.H_REPLANT_LEN;
            if (machine.is(State.WORKING) && timer >= Math.round(at * 20)) {
                machine.to(State.FOLLOW_THROUGH);
                golem.swing(InteractionHand.MAIN_HAND);
                if (plan.kind() == Kind.REAP) level.destroyBlock(pos, true, golem); // drops like a player's break
                else sow(level, golem, pos);
            }
            if (timer >= Math.round(len * 20)) done();
            return;
        }
        if (!stillWorthIt(level, golem)) { done(); return; }

        lookAt(golem, new BlockPosTracker(Vec3.atCenterOf(pos)));
        // stand where the clip expects the plant to be: 12 pixels ahead
        if (spot == null) {
            spot = Stand.cardinal(level, pos, golem, Anim.H_CROP_DIST);
            if (spot == null) spot = Vec3.atCenterOf(pos).subtract(0, 0.5, 0); // nowhere to stand back: work from the plot itself
        }
        if (!standAt(golem, spot, 0.9)) {
            if (++stalled > STALL_TICKS) done();
            return;
        }
        Face.toward(golem, Vec3.atCenterOf(pos));
        holdStill(golem);
        golem.playClip(plan.kind() == Kind.REAP ? Anim.H_HARVEST : Anim.H_REPLANT);
        machine.to(State.WORKING);
        timer = 0;
    }

    private void done() {
        machine.to(State.DONE);
        finish();
    }

    private boolean stillWorthIt(ServerLevel level, WorkGolem golem) {
        return plan.kind() == Kind.REAP
                ? isRipe(level.getBlockState(plan.pos())) && !golem.isCarrying()
                : isBareFarmland(level, plan.pos()) && golem.isCarrying();
    }

    private void sow(ServerLevel level, WorkGolem golem, BlockPos pos) {
        ItemStack stack = golem.carried();
        if (!(stack.getItem() instanceof BlockItem item)) return;
        Block block = item.getBlock();
        if (!(block instanceof CropBlock)) return;
        BlockState crop = block.defaultBlockState();
        if (!crop.canSurvive(level, pos)) return;
        level.setBlockAndUpdate(pos, crop);
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        stack.shrink(1);
        if (stack.isEmpty()) golem.setCarried(ItemStack.EMPTY);
    }

    @Override
    protected void end(ServerLevel level, WorkGolem golem, long time) {
        golem.setPickupSuspended(false);
    }
}

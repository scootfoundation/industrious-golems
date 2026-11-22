package com.scottcaldwell.industriousgolems.lumber;

import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.behavior.Machine;
import com.scottcaldwell.industriousgolems.behavior.TaskRunner;
import com.scottcaldwell.industriousgolems.golem.Anim;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/** Plants the sapling the golem is holding: walks to the spot beside the place, bends down and puts it in. Runs in the exclusive task activity. */
public class PlantSapling extends TaskRunner<PlantSapling.Plan> {

    /** Ticks spent trying to get to the planting spot before giving up. */
    private static final int STALL_TICKS = 600;
    /** How far from the sapling's centre the golem stands to plant it (9 pixels). */
    private static final double PLANT_DIST = 9.0 / 16.0;

    private enum State { STEP_UP, PLANT, DONE }

    public static final Machine.Graph<State> GRAPH = new Machine.Graph<>("plant-sapling", "Lumberjack: plant a sapling",
            "Mushroom golem, holding a sapling. Walks to a spot beside the place to plant (the open ground near the "
                    + "chest best suited to a tree) and plants it. The sapling comes from the hand: fetched from the chest or picked up, never conjured.", State.class)
            .start(State.STEP_UP)
            .state(State.STEP_UP, "Walking to the spot a plant-distance from the place, facing it.")
            .state(State.PLANT, "Plant clip playing; the sapling leaves the hand and goes in at the planting moment.")
            .state(State.DONE, "(end) Task over.")
            .edge(State.STEP_UP, State.PLANT, "standing on the spot with a sapling in hand")
            .edge(State.STEP_UP, State.DONE, "no sapling in hand, the place is taken, no spot to stand, or no path for 30 s (the place is then ignored for 2.5 min)")
            .edge(State.PLANT, State.DONE, "plant clip ends");

    /** @param at where the sapling goes */
    public record Plan(BlockPos at) { }

    private final Machine.Run<State> machine = GRAPH.run();
    private int timer;
    private int stalled;
    private Vec3 spot;

    public PlantSapling() {
        super(Plan.class, 1200);
    }

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        machine.reset();
        timer = 0;
        stalled = 0;
        spot = null;
    }

    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        BlockPos at = plan.at();
        switch (machine.state()) {
            case STEP_UP -> {
                if (!golem.carried().is(ItemTags.SAPLINGS) || !LumberWork.canPlantAt(level, at, golem.carried())) { done(); return; }
                if (spot == null) spot = chooseSpot(level, golem, at);
                if (spot == null) { giveUp(golem, time); return; }
                lookAt(golem, new BlockPosTracker(Vec3.atCenterOf(at)));
                if (!standAt(golem, spot, 0.9)) {
                    if (++stalled > STALL_TICKS) giveUp(golem, time);
                    return;
                }
                holdStill(golem);
                golem.playClip(Anim.M_PLANT);
                machine.to(State.PLANT);
                timer = 0;
            }
            case PLANT -> {
                holdStill(golem);
                lookAt(golem, new BlockPosTracker(Vec3.atCenterOf(at)));
                timer++;
                if (timer == Math.round(Anim.M_PLANT_AT * 20)) plant(level, golem, at);
                if (timer >= Math.round(Anim.M_PLANT_LEN * 20)) done();
            }
            case DONE -> { }
        }
    }

    private void done() {
        machine.to(State.DONE);
        finish();
    }

    /** It cannot get there: leave this place alone for a while rather than trying it again at once. */
    private void giveUp(WorkGolem golem, long time) {
        golem.getData(ModAttachments.TASK).ignoreBlock(plan.at(), time + 3000);
        done();
    }

    /** A free spot a plant-distance from the sapling's place, on the side the golem is already on if it can be. */
    private static Vec3 chooseSpot(ServerLevel level, WorkGolem golem, BlockPos at) {
        Vec3 centre = Vec3.atCenterOf(at);
        Vec3 away = golem.position().subtract(centre).multiply(1, 0, 1);
        double best = -1;
        Vec3 pick = null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Vec3 spot = new Vec3(centre.x + d.getStepX() * PLANT_DIST, at.getY(), centre.z + d.getStepZ() * PLANT_DIST);
            BlockPos feet = BlockPos.containing(spot);
            if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) continue;
            double score = away.x * d.getStepX() + away.z * d.getStepZ();
            if (pick == null || score > best) { best = score; pick = spot; }
        }
        return pick;
    }

    private void plant(ServerLevel level, WorkGolem golem, BlockPos pos) {
        ItemStack stack = golem.carried();
        if (!stack.is(ItemTags.SAPLINGS) || !LumberWork.canPlantAt(level, pos, stack)) return;
        golem.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        level.setBlockAndUpdate(pos, ((BlockItem) stack.getItem()).getBlock().defaultBlockState());
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        stack.shrink(1);
        if (stack.isEmpty()) golem.setCarried(ItemStack.EMPTY);
    }

    @Override
    protected void end(ServerLevel level, WorkGolem golem, long time) {
        spot = null;
    }
}

package com.scottcaldwell.industriousgolems.golem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A golem's way through fence gates. Its paths run through shut gates (see {@link GolemPathing}); as it comes up to one on its path it
 * opens it, and once it is through it shuts it again. It only shuts gates it opened itself, so one a player left open stays open.
 *
 * <p>Letting no animal out comes first: while a gate it opened is open, animals near it are held back; the golem starts no work and
 * arrives nowhere until the gate is shut again ({@link #busy}); and what it has open is saved with it and shut if it dies. A golem
 * that {@link Shoo shoos} moves animals away before it opens at all.
 */
public class Gates {

    /** How near (squared) a gate on the path has to be before the golem opens it. */
    private static final double OPEN_DIST_SQR = 4.0;
    /** While a gate is open, animals nearer to its middle than this are moved back. */
    private static final double GUARD = 1.6;
    private static final double GUARD_SPEED = 0.15;
    /** Ticks standing about in a gateway with nowhere to go before it steps out of it, and how fast it does. */
    private static final int LINGER_TICKS = 5;
    private static final double STEP_OUT = 0.08;

    private final WorkGolem golem;
    private final Set<BlockPos> opened = new HashSet<>();
    private int lingering;

    public Gates(WorkGolem golem) {
        this.golem = golem;
    }

    /** Whether a gate it opened is still open: it has yet to get through and shut it. */
    public boolean busy() {
        return !opened.isEmpty();
    }

    public void tick(ServerLevel level) {
        Set<BlockPos> ahead = new HashSet<>();
        Path path = golem.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            int next = path.getNextNodeIndex();
            for (int i = next; i < Math.min(path.getNodeCount(), next + 2); i++) {
                BlockPos pos = path.getNodePos(i);
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof FenceGateBlock) || golem.position().distanceToSqr(Vec3.atBottomCenterOf(pos)) > OPEN_DIST_SQR) continue;
                ahead.add(pos.immutable());
                if (!state.getValue(FenceGateBlock.OPEN) && golem.shoo().mayOpen(level, pos)) {
                    set(level, pos, state, true);
                    opened.add(pos.immutable());
                }
            }
        }
        boolean inGateway = false;
        for (Iterator<BlockPos> it = opened.iterator(); it.hasNext(); ) {
            BlockPos pos = it.next();
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof FenceGateBlock) || !state.getValue(FenceGateBlock.OPEN)) {
                it.remove();
                continue;
            }
            boolean mine = golem.getBoundingBox().intersects(new AABB(pos));
            // not on top of itself or another golem coming through
            if (ahead.contains(pos) || !level.getEntitiesOfClass(WorkGolem.class, new AABB(pos)).isEmpty()) {
                guard(level, pos, state);
                if (mine && golem.getNavigation().isDone()) {
                    inGateway = true;
                    if (lingering >= LINGER_TICKS) stepOut(pos, state);
                }
                continue;
            }
            set(level, pos, state, false);
            it.remove();
        }
        lingering = inGateway ? lingering + 1 : 0;
    }

    /** Shuts everything it opened, whatever is going on: for a golem that is about to be gone. */
    public void closeAll(ServerLevel level) {
        for (BlockPos pos : opened) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN)) set(level, pos, state, false);
        }
        opened.clear();
    }

    void save(ValueOutput output) {
        if (!opened.isEmpty()) output.store("OpenedGates", BlockPos.CODEC.listOf(), List.copyOf(opened));
    }

    void load(ValueInput input) {
        opened.clear();
        input.read("OpenedGates", BlockPos.CODEC.listOf()).ifPresent(opened::addAll);
    }

    private void guard(ServerLevel level, BlockPos gate, BlockState state) {
        for (Animal animal : animalsNear(level, gate, GUARD)) {
            Vec3 away = away(animal.position(), gate, state);
            animal.getNavigation().stop();
            animal.setDeltaMovement(away.x * GUARD_SPEED, animal.getDeltaMovement().y, away.z * GUARD_SPEED);
        }
    }

    /** Animals (but not ones being led) within {@code range} of the middle of the gate, measured across the ground. */
    static List<Animal> animalsNear(ServerLevel level, BlockPos gate, double range) {
        Vec3 middle = Vec3.atBottomCenterOf(gate);
        return level.getEntitiesOfClass(Animal.class, new AABB(gate).inflate(range, 1.0, range),
                a -> a.isAlive() && !a.isLeashed() && !a.isPassenger() && a.position().subtract(middle).horizontalDistanceSqr() <= range * range);
    }

    /**
     * The way to move something so that it gets further from the gate without changing sides: mostly square away from the line of
     * the fence, a little along it. Something right in the gap goes to the side the golem is not on.
     */
    Vec3 away(Vec3 from, BlockPos gate, BlockState state) {
        boolean alongX = state.getValue(FenceGateBlock.FACING).getAxis() == Direction.Axis.X;
        Vec3 offset = from.subtract(Vec3.atBottomCenterOf(gate));
        double through = alongX ? offset.x : offset.z, along = alongX ? offset.z : offset.x;
        if (Math.abs(through) < 0.1) {
            Vec3 mine = golem.position().subtract(Vec3.atBottomCenterOf(gate));
            through = (alongX ? mine.x : mine.z) > 0 ? -1 : 1;
        }
        double square = Math.signum(through), sideways = Math.max(-0.5, Math.min(0.5, along));
        return new Vec3(alongX ? square : sideways, 0, alongX ? sideways : square).normalize();
    }

    /** Standing in the gap with nowhere to go: on to whichever side it is nearer, so the gate can shut. */
    private void stepOut(BlockPos gate, BlockState state) {
        boolean alongX = state.getValue(FenceGateBlock.FACING).getAxis() == Direction.Axis.X;
        Vec3 offset = golem.position().subtract(Vec3.atBottomCenterOf(gate));
        double through = alongX ? offset.x : offset.z;
        if (Math.abs(through) < 0.05) through = alongX ? golem.getLookAngle().x : golem.getLookAngle().z;
        double way = through < 0 ? -STEP_OUT : STEP_OUT;
        golem.move(MoverType.SELF, new Vec3(alongX ? way : 0, 0, alongX ? 0 : way));
    }

    /** As a hand would: an opening gate swings away from the golem. */
    private void set(ServerLevel level, BlockPos pos, BlockState state, boolean open) {
        Direction heading = golem.getDirection();
        if (open && state.getValue(FenceGateBlock.FACING) == heading.getOpposite()) state = state.setValue(FenceGateBlock.FACING, heading);
        level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, open), 10);
        level.playSound(null, pos, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(golem, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }
}

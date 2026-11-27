package com.scottcaldwell.industriousgolems.behavior;

import com.scottcaldwell.industriousgolems.golem.Pack;
import com.scottcaldwell.industriousgolems.golem.Slots;
import com.scottcaldwell.industriousgolems.block.GolemChestBlock;
import com.scottcaldwell.industriousgolems.golem.Anim;
import com.scottcaldwell.industriousgolems.golem.Face;
import com.scottcaldwell.industriousgolems.golem.Stand;
import com.scottcaldwell.industriousgolems.mine.Kit;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import com.scottcaldwell.industriousgolems.needs.Need;
import com.scottcaldwell.industriousgolems.needs.ProfessionNeeds;
import com.scottcaldwell.industriousgolems.needs.Job;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A visit to a chest, carrying one stack at a time. A golem carrying something takes it to the chest and puts it in;
 * an empty-handed golem whose work needs a supply takes one stack of it out. It behaves like a player: walks over,
 * looks at the chest, pauses, opens it (lid animation and sound), moves the stack, closes it, looks away and leaves.
 *
 * <p>Supplies come from the golem's own chest (see {@link GolemChestBlock}) first, then regular chests next to the job site.
 * Deposits go to a copper chest next to the job site when there is one, so that copper golems can carry the produce on to wherever
 * it belongs; the exception is something the golem also works with (seeds for the hay golem, saplings for the mushroom golem), which
 * goes to its own chest where it will be fetched from again. With no copper chest nearby (or none with room) the own chest takes it.
 */
public class ContainerInteraction extends TaskRunner<ContainerInteraction.Plan> {

    /** Containers within this many blocks (horizontally; +-2 vertically) of the job site count as "adjacent". */
    public static final int SEARCH_RADIUS = 3;
    private static final int DURATION = 1200;
    /** With nowhere proper to stand, how close the golem has to be to use the chest anyway: with nothing between, and over a fence. */
    private static final double BESIDE = 1.8, OVER_FENCE = 5.0;
    /** How near its spot a golem gets before it waits for another to finish with the chest. */
    private static final double QUEUE_DIST = 2.0;
    /**
     * How far below and above the spot in front of the chest still counts as being there. A full block below does not; above
     * allows for a golem perched on the rim of a neighbouring chest or composter, which the last shuffle slides it off.
     */
    private static final double LEVEL_BELOW = 0.9, LEVEL_ABOVE = 1.1;


    private BlockPos chestPos;
    private Plan visit;
    private ClipRun clip;
    private final Approach approach = new Approach(400);
    private Container opened;
    private Anim.Chest spec;
    private Vec3 spot;

    public ContainerInteraction() {
        super(Plan.class, DURATION);
    }

    // ---- choosing a container ---------------------------------------------------------------------------------

    /** Which container to visit; {@code supply} is the need to take from it, or null to put the carried stack in. */
    public record Plan(BlockPos pos, String supply, java.util.function.Predicate<ItemStack> accepts) implements Task {
        public Plan(BlockPos pos, String supply) {
            this(pos, supply, s -> true);
        }
    }

    /** Puts what the golem carries away: always the first thing to do with a full hand. */
    public static Plan planDeposit(ServerLevel level, WorkGolem golem) {
        ItemStack carried = golem.carried();
        if (carried.isEmpty()) return null;
        BlockPos pos = best(level, golem, chest -> Slots.fits(chest, carried), true, isInput(golem, carried));
        return pos == null ? null : new Plan(pos, null);
    }

    /**
     * Fetches a stack of a supply for the best job that needs one and that a container nearby can supply. A job whose supply nothing nearby
     * holds (or holds only in a form the job cannot use) is passed over, so it cannot hold up the jobs after it.
     */
    public static Plan planSupply(ServerLevel level, WorkGolem golem, JobList jobs) {
        if (golem.isCarrying()) return null;
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        for (Job job : jobs) {
            if (!job.needsSupply()) continue;
            Need need = needs.need(job.supply());
            if (need == null) continue;
            BlockPos pos = best(level, golem, chest -> need.isKit() ? packVisitUseful(need, golem, chest) : slotWith(need, job.accepts(), chest) >= 0, false);
            if (pos != null) return new Plan(pos, need.id(), job.accepts());
        }
        return null;
    }

    /** Whether the golem works with this itself (a supply or part of a kit), so that it belongs in its own chest rather than being sent on. */
    private static boolean isInput(WorkGolem golem, ItemStack stack) {
        for (Need need : ProfessionNeeds.of(golem).needs()) {
            if (need.match().test(stack)) return true;
            for (Need.Part part : need.parts()) if (part.match().test(stack)) return true;
        }
        return false;
    }

    private static BlockPos best(ServerLevel level, WorkGolem golem, java.util.function.Predicate<Container> usable,
                                 boolean deposit) {
        return best(level, golem, usable, deposit, true);
    }

    /** @param ownFirst for a deposit: whether the golem's own chest beats a copper chest (the stack is one it works with) */
    private static BlockPos best(ServerLevel level, WorkGolem golem, java.util.function.Predicate<Container> usable,
                                 boolean deposit, boolean ownFirst) {
        BlockPos site = golem.jobSite(level);
        if (site == null) return null;
        BlockPos best = null;
        int bestPref = 0;
        double bestDist = 0;
        for (BlockPos p : BlockPos.betweenClosed(site.offset(-SEARCH_RADIUS, -2, -SEARCH_RADIUS),
                site.offset(SEARCH_RADIUS, 2, SEARCH_RADIUS))) {
            if (level.getBlockEntity(p) == null) continue;
            Container c = HopperBlockEntity.getContainerAt(level, p);
            if (c == null || !usable.test(c)) continue;
            BlockState state = level.getBlockState(p);
            boolean own = p.equals(site) && state.getBlock() instanceof GolemChestBlock;
            boolean copper = state.is(BlockTags.COPPER_CHESTS);
            int pref = own ? (deposit && !ownFirst ? 2 : 3) : copper == deposit ? (deposit && !ownFirst ? 3 : 2) : 1;
            double dist = p.distSqr(golem.blockPosition());
            if (pref > bestPref || (pref == bestPref && dist < bestDist)) {
                best = p.immutable();
                bestPref = pref;
                bestDist = dist;
            }
        }
        return best;
    }

    /** A slot of the chest holding something of the kit that the golem's pack is still short of, or -1. */
    private static int kitSlot(Need need, WorkGolem golem, Container chest) {
        for (Need.Part part : need.parts()) {
            if (golem.pack().count(part.match()) >= part.target()) continue;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack s = chest.getItem(i);
                if (!s.isEmpty() && part.match().test(s)) return i;
            }
        }
        return -1;
    }

    private static int slotWith(Need need, Predicate<ItemStack> accepts, Container chest) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (!s.isEmpty() && need.match().test(s) && accepts.test(s)) return i;
        }
        return -1;
    }

    // ---- the visit (runs in the exclusive task activity) --------------------------------------------------------

    /** The steps of a visit. The put and the fetch share them; the transfer step is what differs. */
    private enum State { APPROACH, REACH, OPEN, LET_GO, TRANSFERRED, THROWN, CLOSED, DONE }

    public static final Machine.Graph<State> GRAPH = new Machine.Graph<>("chest-visit", "Visit the chest (put away / fetch a supply)",
            "Any golem. Walks to the spot in front of the chest, faces it, then plays the profession's clip; the lid, the hand-over and "
                    + "the close are each tied to a moment in that clip.", State.class)
            .start(State.APPROACH)
            .state(State.APPROACH, "Walking to the spot in front of the chest (the side it faces), waiting near it while another golem is at the "
                    + "chest. Never starts the clip anywhere else, and not while up on top of the chest.")
            .state(State.REACH, "Standing in front, clip playing, lid still shut.")
            .state(State.OPEN, "The lid is up (the chest is opened for the golem: lid animation and sound). Waiting for the hand to let go or take.")
            .state(State.LET_GO, "Put only: the hand has let go of the stack and it is on its way in.")
            .state(State.TRANSFERRED, "The stack is in the chest (put) or in the hand (fetch).")
            .state(State.THROWN, "Fetch, for golems that fling the stack out onto the grass to be picked up in the usual way.")
            .state(State.CLOSED, "Lid down; the clip is finishing.")
            .state(State.DONE, "(end) Task over. Interruptions close the lid and give back any stack the chest never took.")
            .edge(State.APPROACH, State.REACH, "settled on the spot, at ground level, facing the chest")
            .edge(State.APPROACH, State.DONE, "no standing room in front, no nearer for 20 s, or the chest is gone")
            .edge(State.REACH, State.OPEN, "clip reaches the lid-open moment")
            .edge(State.OPEN, State.LET_GO, "put: clip reaches the release moment")
            .edge(State.OPEN, State.TRANSFERRED, "fetch: clip reaches the take moment, a stack moves chest -> hand")
            .edge(State.LET_GO, State.TRANSFERRED, "put: clip reaches the drop moment, the stack lands in the chest")
            .edge(State.TRANSFERRED, State.THROWN, "fetch, thrower kinds: the clip reaches the throw moment")
            .edge(State.TRANSFERRED, State.CLOSED, "clip reaches the lid-close moment")
            .edge(State.THROWN, State.CLOSED, "clip reaches the lid-close moment")
            .anyTo(State.DONE, "the clip ends, or the visit is interrupted or the chest vanishes (the lid is shut and any stack handed back)", State.APPROACH, State.DONE);

    private final Machine.Run<State> machine = GRAPH.run();

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        chestPos = plan.pos();
        visit = plan;
        machine.reset();
        spec = Anim.chest(golem.profession().id());
        spot = null;
        approach.reset();
        golem.setPickupSuspended(true); // a free hand while the stack is in the air must not grab something else
        lookAtChest(golem);
    }

    private void lookAtChest(WorkGolem golem) {
        golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(Vec3.atCenterOf(chestPos)));
    }

    private void done() {
        machine.to(State.DONE);
        finish();
    }

    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        if (chestPos == null) return;
        if (machine.is(State.APPROACH)) approach(level, golem);
        else play(golem);
    }

    private void approach(ServerLevel level, WorkGolem golem) {
        lookAtChest(golem);
        if (spot == null) {
            spot = Stand.front(level, chestPos, golem, spec.dist());
            // no spot in front of or beside it: only where the golem already stands on the ground, never up on the chest, and only if
            // that is right by the chest, or a short reach over a fence from it (a herder in a pen with no gate)
            if (spot == null && golem.onGround() && Math.abs(golem.getY() - chestPos.getY()) < 0.5) {
                Vec3 chest = Vec3.atBottomCenterOf(chestPos);
                double far = golem.position().distanceToSqr(chest);
                if (far < BESIDE * BESIDE || (far < OVER_FENCE * OVER_FENCE && Approach.fenceBetween(golem, chest))) spot = golem.position();
            }
            if (spot == null) { done(); return; }
        }
        // up on top of the chest (its lid is under a block high, so the golem's block position is the chest's own, not the one above):
        // climb down first, the clip needs level ground
        if (golem.getBlockX() == chestPos.getX() && golem.getBlockZ() == chestPos.getZ() && golem.getY() > chestPos.getY() + 0.3) {
            golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            if (approach.walk(golem, new BlockPosTracker(BlockPos.containing(spot)), 0, () -> false) == Approach.Outcome.UNREACHABLE) done();
            return;
        }
        // the spot is at the chest's height and standAt only measures across the ground: under a chest up on a ledge the golem would
        // otherwise count as there. Walk to the spot itself, up whatever way leads to it.
        if (spot.y - golem.getY() > LEVEL_BELOW || golem.getY() - spot.y > LEVEL_ABOVE) {
            if (approach.walk(golem, new BlockPosTracker(BlockPos.containing(spot)), 0, () -> false) == Approach.Outcome.UNREACHABLE) done();
            return;
        }
        // another golem sharing the chest is at it: wait beside it rather than shove for the same spot
        if (golem.position().distanceToSqr(spot) < QUEUE_DIST * QUEUE_DIST && taken(level, golem)) {
            holdStill(golem);
            return;
        }
        Approach.Outcome outcome = approach.stand(golem, spot, 1.0);
        if (outcome != Approach.Outcome.ARRIVED) {
            if (outcome == Approach.Outcome.UNREACHABLE) done();
            return;
        }
        Face.toward(golem, Vec3.atCenterOf(chestPos));
        golem.setUsingChest(chestPos);
        machine.to(State.REACH);
        startClip(level, golem);
    }

    private boolean taken(ServerLevel level, WorkGolem golem) {
        return !level.getEntitiesOfClass(WorkGolem.class, new AABB(chestPos).inflate(2.5),
                other -> other != golem && chestPos.equals(other.usingChest())).isEmpty();
    }

    /** Starts the visit's clip: the lid, the hand-over and the close each happen at their moment in it. */
    private void startClip(ServerLevel level, WorkGolem golem) {
        boolean put = visit.supply() == null;
        clip = ClipRun.play(golem, put ? spec.put() : spec.fetch(), put ? spec.putLen() : spec.fetchLen())
                .at(put ? spec.putOpen() : spec.fetchOpen(), () -> open(level, golem));
        if (put) {
            clip.at(spec.putRelease(), () -> letGo(golem)).at(spec.putDrop(), () -> drop(level, golem));
        } else {
            clip.at(spec.fetchTake(), () -> takeOut(level, golem)).at(spec.fetchThrow(), () -> {
                if (!machine.is(State.TRANSFERRED)) return;
                throwOut(level, golem);
                machine.to(State.THROWN);
            });
        }
        boolean throwing = !put && spec.fetchThrow() >= 0;
        clip.at(put ? spec.putClose() : spec.fetchClose(), () -> {
            if (!machine.is(throwing ? State.THROWN : State.TRANSFERRED)) return;
            closeChest(golem);
            machine.to(State.CLOSED);
        });
    }

    private void play(WorkGolem golem) {
        holdStill(golem);
        Face.toward(golem, Vec3.atCenterOf(chestPos));
        if (!clip.tick()) return;
        closeChest(golem);
        done();
    }

    private void open(ServerLevel level, WorkGolem golem) {
        if (!machine.is(State.REACH)) return;
        Container chest = HopperBlockEntity.getContainerAt(level, chestPos);
        if (chest == null) { done(); return; }
        opened = chest;
        chest.startOpen((ContainerUser) golem);
        golem.setOpenedChestPos(chestPos);
        machine.to(State.OPEN);
    }

    /**
     * The chest as it is now, or null if it has been broken. Looked up each time: a double chest is a new combined container on every
     * lookup, so the one opened earlier cannot be compared with it.
     */
    private Container chestNow(ServerLevel level) {
        return level.getBlockEntity(chestPos) == null ? null : HopperBlockEntity.getContainerAt(level, chestPos);
    }

    /** The hand lets go of the stack. */
    private void letGo(WorkGolem golem) {
        if (!machine.is(State.OPEN)) return;
        golem.stow(); // (the golem looks after it until it lands: saved with it, dropped if it dies)
        golem.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        machine.to(State.LET_GO);
    }

    /** And it lands in the chest. */
    private void drop(ServerLevel level, WorkGolem golem) {
        if (!machine.is(State.LET_GO)) return;
        Container chest = chestNow(level);
        ItemStack stack = golem.takeStowed();
        ItemStack rest = chest == null ? stack : Slots.insert(chest, stack); // broken meanwhile: back in the hand
        if (chest != null) chest.setChanged();
        if (!rest.isEmpty()) golem.setCarried(rest);
        machine.to(State.TRANSFERRED);
    }

    private void takeOut(ServerLevel level, WorkGolem golem) {
        if (!machine.is(State.OPEN)) return;
        Container chest = chestNow(level);
        if (chest != null) {
            take(ProfessionNeeds.of(golem).need(visit.supply()), visit.accepts(), golem, chest);
            chest.setChanged();
        }
        machine.to(State.TRANSFERRED);
    }

    /** The stack is flung out onto the grass beside the golem, where it will be picked up in the ordinary way. */
    private void throwOut(ServerLevel level, WorkGolem golem) {
        ItemStack stack = golem.carried();
        if (stack.isEmpty()) return;
        golem.setCarried(ItemStack.EMPTY);
        golem.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        Vec3 from = Face.local(golem.position().add(0, 1.0, 0), golem.getYRot(), 0.3, 0.8);
        Vec3 land = Face.local(golem.position(), golem.getYRot(), spec.landX(), spec.landZ());
        ItemEntity drop = new ItemEntity(level, from.x, from.y, from.z, stack);
        double flight = 11.0;
        drop.setDeltaMovement((land.x - from.x) / flight * 0.95, 0.3, (land.z - from.z) / flight * 0.95);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
    }

    @Override
    protected void end(ServerLevel level, WorkGolem golem, long time) {
        golem.setPickupSuspended(false);
        golem.setUsingChest(null);
        closeChest(golem); // also covers interruptions (panic, damage, timeout)
        golem.unstow(); // let go of a stack the chest never received
        chestPos = null;
    }

    private void closeChest(WorkGolem golem) {
        if (opened == null) return;
        opened.stopOpen((ContainerUser) golem);
        golem.setOpenedChestPos(null);
        opened = null;
    }

    // ---- moving the stack -------------------------------------------------------------------------------------

    /** Takes one stack of the supply (at most what the need allows) from the chest into the hand. */
    private static void take(Need need, Predicate<ItemStack> accepts, WorkGolem golem, Container chest) {
        if (need == null) return;
        if (need.isKit()) { depositLoot(golem, chest); fillPack(need, golem, chest); return; }
        if (golem.isCarrying()) return;
        int slot = slotWith(need, accepts, chest);
        if (slot < 0) return;
        ItemStack in = chest.getItem(slot);
        int n = Math.min(need.carry(), in.getCount());
        golem.setCarried(in.copyWithCount(n));
        in.shrink(n);
        if (in.isEmpty()) chest.setItem(slot, ItemStack.EMPTY);
    }

    /** Tops the pack up from the chest, each kind of the kit to its target. */
    private static void fillPack(Need need, WorkGolem golem, Container chest) {
        for (Need.Part part : need.parts()) {
            int want = part.target() - golem.pack().count(part.match());
            for (int i = 0; i < chest.getContainerSize() && want > 0; i++) {
                ItemStack in = chest.getItem(i);
                if (in.isEmpty() || !part.match().test(in)) continue;
                int n = Math.min(want, in.getCount());
                int took = n - golem.pack().add(in.copyWithCount(n)).getCount(); // as much as the pack takes
                if (took <= 0) continue;
                in.shrink(took);
                if (in.isEmpty()) chest.setItem(i, ItemStack.EMPTY);
                want -= took;
            }
        }
    }

    /** Puts what the golem has dug in the chest, everything in its general slots but the cobblestone it keeps for building; what the chest cannot take stays. */
    private static void depositLoot(WorkGolem golem, Container chest) {
        int keep = Kit.RESERVE;
        for (int i = Kit.SPECIAL; i < Pack.SIZE; i++) {
            ItemStack held = golem.pack().getItem(i);
            if (held.isEmpty()) continue;
            int kept = 0;
            if (Kit.COBBLE.match().test(held)) {
                kept = Math.min(keep, held.getCount());
                keep -= kept;
            }
            if (kept >= held.getCount()) continue;
            ItemStack rest = Slots.insert(chest, held.copyWithCount(held.getCount() - kept));
            int left = kept + rest.getCount();
            golem.pack().setItem(i, left <= 0 ? ItemStack.EMPTY : held.copyWithCount(left));
        }
    }

    /** Whether a visit to this chest would do the golem any good: its diggings would go in, or it has something of the kit that the pack is short of. */
    private static boolean packVisitUseful(Need need, WorkGolem golem, Container chest) {
        if (kitSlot(need, golem, chest) >= 0) return true;
        for (int i = Kit.SPECIAL; i < Pack.SIZE; i++) {
            ItemStack held = golem.pack().getItem(i);
            if (held.isEmpty()) continue;
            boolean cobble = Kit.COBBLE.match().test(held);
            if ((!cobble || golem.pack().count(Kit.COBBLE.match()) > Kit.RESERVE) && Slots.fits(chest, held.copyWithCount(1))) return true;
        }
        return false;
    }
}

package com.scottcaldwell.industriousgolems.offering;

import com.scottcaldwell.industriousgolems.golem.Anim;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * What keeps a golem going. A golem does nothing until it is given what it currently craves; each offering then runs
 * it for {@link #PER_OFFERING} ticks (of its own time: the clock only runs while the golem is loaded and has a job
 * site). It stores at most {@link #MAX} ticks, so a player cannot stock it up for hours, and after each offering it
 * craves something different from what it just had, so no single item is enough.
 */
public final class Offerings {

    /** 45 minutes of work per offering. */
    public static final int PER_OFFERING = 45 * 60 * 20;
    /** The most charge a golem stores: two offerings' worth. */
    public static final int MAX = 2 * PER_OFFERING;
    /** An offering is accepted only while this much or less is stored (seconds, as synced to clients). */
    public static final int ACCEPTS_BELOW_SECONDS = (MAX - PER_OFFERING) / 20;

    private Offerings() { }

    /** Whether the golem will take this stack right now: it is what it craves and there is room for it. */
    public static boolean wants(WorkGolem golem, ItemStack stack) {
        return acceptsMore(golem) && craved(golem, stack);
    }

    /** Whether the golem has room for another offering. */
    public static boolean acceptsMore(WorkGolem golem) {
        return golem.chargeSeconds() <= ACCEPTS_BELOW_SECONDS;
    }

    private static boolean craved(WorkGolem golem, ItemStack stack) {
        List<Offering> list = golem.profession().offerings();
        int craving = golem.cravingIndex();
        return !stack.isEmpty() && craving >= 0 && craving < list.size() && list.get(craving).match().test(stack);
    }

    /**
     * Takes one item from the stack if the golem wants it, charging the golem and choosing its next craving.
     * Server side only.
     *
     * @return whether the offering was accepted
     */
    public static boolean offer(WorkGolem golem, ItemStack stack) {
        if (!wants(golem, stack)) return false;
        boolean wasDormant = !golem.isCharged();
        ItemStack given = stack.copyWithCount(1);
        stack.shrink(1);
        golem.setLastOffering(golem.cravingIndex());
        golem.setChargeTicks(golem.chargeTicks() + PER_OFFERING);
        golem.setCravingIndex(next(golem));
        if (golem.level() instanceof ServerLevel level) {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(given)),
                    golem.getX(), golem.getEyeY() - 0.3, golem.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
            if (wasDormant) {
                Anim.Receive receive = Anim.receive(golem.profession().id());
                if (receive != null) golem.playClipAndWait(receive.clip(), receive.len());
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, golem.getX(), golem.getY() + 1.0, golem.getZ(), 8, 0.3, 0.5, 0.3, 0.0);
                golem.playSound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0F, 1.2F);
            } else {
                golem.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.0F);
            }
        }
        return true;
    }

    /** Runs the golem's clock one tick. Server side. */
    public static void tick(WorkGolem golem) {
        List<Offering> list = golem.profession().offerings();
        if (golem.cravingIndex() < 0 || golem.cravingIndex() >= list.size()) golem.setCravingIndex(next(golem));
        if (golem.chargeTicks() <= 0 || !golem.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE)) return;
        golem.setChargeTicks(golem.chargeTicks() - 1);
        if (golem.chargeTicks() == 0) runDown(golem);
    }

    /** The golem stops where it stands and goes dormant. */
    private static void runDown(WorkGolem golem) {
        golem.getNavigation().stop();
        golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        golem.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        golem.playSound(SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.0F, 1.2F);
    }

    /** Charges the golem to the limit without needing offerings (tests, commands). */
    public static void fill(WorkGolem golem) {
        golem.setChargeTicks(MAX);
    }

    /** A random craving other than the one just satisfied. */
    private static int next(WorkGolem golem) {
        int size = golem.profession().offerings().size();
        if (size <= 1) return 0;
        int last = golem.lastOffering();
        int pick = golem.getRandom().nextInt(last >= 0 && last < size ? size - 1 : size);
        return last >= 0 && last < size && pick >= last ? pick + 1 : pick;
    }
}

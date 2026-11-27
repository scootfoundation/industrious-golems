package com.scottcaldwell.industriousgolems.golem;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** How a kind of golem carries things, and so how it forages for what lies about. */
public enum Carry {

    /** One stack, in its hand. Collects what lies within reach of its chest. */
    HAND(24, 4, false, false, 0) {
        @Override
        public boolean canTake(WorkGolem golem, ItemStack stack) {
            ItemStack held = golem.carried();
            return held.isEmpty() || (ItemStack.isSameItemSameComponents(held, stack) && held.getCount() < held.getMaxStackSize());
        }

        @Override
        int take(WorkGolem golem, ItemStack lying) {
            ItemStack held = golem.carried();
            if (held.isEmpty()) {
                golem.setCarried(lying.copy());
                return lying.getCount();
            }
            int n = Math.min(lying.getCount(), held.getMaxStackSize() - held.getCount());
            ItemStack more = held.copy();
            more.grow(n);
            golem.setCarried(more);
            return n;
        }
    },

    /**
     * An inventory on its back (see {@link Pack}). Its work takes it far from its chest, so it collects what lies about <em>itself</em>,
     * as it goes, scooping up what is beside the stack it bends for.
     */
    PACK(5, 3, true, true, 1.5) {
        @Override
        public boolean canTake(WorkGolem golem, ItemStack stack) {
            return golem.pack().fitsAll(List.of(stack.copyWithCount(1)));
        }

        @Override
        int take(WorkGolem golem, ItemStack lying) {
            return lying.getCount() - golem.pack().add(lying).getCount();
        }
    };

    private final int reach, reachHeight;
    private final boolean aroundSelf, dryOnly;
    private final double scoop;

    Carry(int reach, int reachHeight, boolean aroundSelf, boolean dryOnly, double scoop) {
        this.reach = reach;
        this.reachHeight = reachHeight;
        this.aroundSelf = aroundSelf;
        this.dryOnly = dryOnly;
        this.scoop = scoop;
    }

    /** Whether there is room for at least some of this stack. */
    public abstract boolean canTake(WorkGolem golem, ItemStack stack);

    /** Takes as much of the lying stack as there is room for and says how many that was; does not change {@code lying}. */
    abstract int take(WorkGolem golem, ItemStack lying);

    /** How far sideways, in blocks, it looks for lying stacks. */
    public int reach() {
        return reach;
    }

    /** And how far up and down. */
    public int reachHeight() {
        return reachHeight;
    }

    /** Whether it looks around itself (otherwise around its chest). */
    public boolean aroundSelf() {
        return aroundSelf;
    }

    /** Whether it leaves what lies in liquid. */
    public boolean dryOnly() {
        return dryOnly;
    }

    /** How far from the stack it picks up others in the same movement; 0 for none. */
    public double scoop() {
        return scoop;
    }
}

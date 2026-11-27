package com.scottcaldwell.industriousgolems.golem;

import com.scottcaldwell.industriousgolems.mine.Kit;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;

/**
 * The inventory of a golem that carries one (the stone golem, see {@link Carry#PACK}): a slot each for fence posts, plank beams and
 * torches (see {@link Kit}), then general slots for whatever it digs and the cobblestone it builds with.
 */
public class Pack extends SimpleContainer {

    public static final int SIZE = 8;

    private final Runnable changed;

    /** @param changed told whenever the contents change */
    public Pack(Runnable changed) {
        super(SIZE);
        this.changed = changed;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (changed != null) changed.run();
    }

    /** How many items in the pack match. */
    public int count(Predicate<ItemStack> match) {
        int n = 0;
        for (int i = 0; i < SIZE; i++) if (match.test(getItem(i))) n += getItem(i).getCount();
        return n;
    }

    /** Puts the stack in: in its special slot if it has one, otherwise into the general slots. Returns what did not fit. */
    public ItemStack add(ItemStack stack) {
        ItemStack rest = put(this, stack.copy());
        setChanged();
        return rest;
    }

    private static ItemStack put(SimpleContainer into, ItemStack stack) {
        int special = Kit.specialSlot(stack);
        return special >= 0 ? Slots.insert(into, special, special + 1, stack) : Slots.insert(into, Kit.SPECIAL, SIZE, stack);
    }

    /** Whether all of these would fit in together. */
    public boolean fitsAll(List<ItemStack> stacks) {
        SimpleContainer trial = new SimpleContainer(SIZE);
        for (int i = 0; i < SIZE; i++) trial.setItem(i, getItem(i).copy());
        for (ItemStack stack : stacks) {
            if (!put(trial, stack.copy()).isEmpty()) return false;
        }
        return true;
    }

    /** Takes one matching item out, or returns an empty stack. */
    public ItemStack takeOne(Predicate<ItemStack> match) {
        for (int i = 0; i < SIZE; i++) {
            ItemStack held = getItem(i);
            if (held.isEmpty() || !match.test(held)) continue;
            ItemStack one = held.copyWithCount(1);
            removeItem(i, 1);
            return one;
        }
        return ItemStack.EMPTY;
    }

    /** Whether the general slots hold anything worth putting in the chest: the diggings, and cobblestone beyond what is kept for building. */
    public boolean hasLoot() {
        int cobble = 0;
        for (int i = Kit.SPECIAL; i < SIZE; i++) {
            ItemStack held = getItem(i);
            if (held.isEmpty()) continue;
            if (Kit.COBBLE.match().test(held)) cobble += held.getCount();
            else return true;
        }
        return cobble > Kit.RESERVE;
    }

    /** How many of the general slots are taken, out of {@code SIZE - Kit.SPECIAL}: how full the sack on its back looks. */
    public int fill() {
        int n = 0;
        for (int i = Kit.SPECIAL; i < SIZE; i++) if (!getItem(i).isEmpty()) n++;
        return n;
    }
}

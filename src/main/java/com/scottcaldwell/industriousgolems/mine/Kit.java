package com.scottcaldwell.industriousgolems.mine;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * What the stone golem builds its mine with. A support needs fences, planks and a torch together, so the miner has an inventory (see
 * {@link com.scottcaldwell.industriousgolems.golem.WorkGolem#pack()}): the first {@link #SPECIAL} slots each take one of these, the rest are
 * general (what it digs, cobblestone among it). It tops each of these up from the chest to {@link #target} in one visit.
 */
public enum Kit {
    /** Support posts. */
    FENCE(s -> s.is(ItemTags.WOODEN_FENCES), 16, Items.OAK_FENCE),
    /** Support beams. */
    PLANKS(s -> s.is(ItemTags.PLANKS), 12, Items.OAK_PLANKS),
    /** Light, so the corridor does not breed mobs: one on each support. */
    TORCH(s -> s.is(Items.TORCH), 8, Items.TORCH),
    /** Floor over small gaps, and plugs for liquid. Not a slot of its own: it is dug in quantity and kept with the rest. */
    COBBLE(s -> s.is(Items.COBBLESTONE), 16, Items.COBBLESTONE);

    /** How many of the kit have a slot of their own (the first ones); the pack's other slots take anything. */
    public static final int SPECIAL = 3;
    /** Cobblestone the golem keeps when it puts its diggings in the chest: what it needs for floor and plugs. */
    public static final int RESERVE = 16;

    private final Predicate<ItemStack> match;
    private final int target;
    private final Item icon;

    Kit(Predicate<ItemStack> match, int target, Item icon) {
        this.match = match;
        this.target = target;
        this.icon = icon;
    }

    public Predicate<ItemStack> match() {
        return match;
    }

    /** How many the golem tops its pack up to. */
    public int target() {
        return target;
    }

    public Item icon() {
        return icon;
    }

    /** Whether this has a slot of its own in the pack. */
    public boolean special() {
        return ordinal() < SPECIAL;
    }

    /** The kit's special slot this stack goes in, or -1. */
    public static int specialSlot(ItemStack stack) {
        for (Kit part : values()) if (part.special() && part.match.test(stack)) return part.ordinal();
        return -1;
    }

    /** The block that placing this item makes (a torch is a wall torch; see {@link Trunk.Placement#facing}). */
    public BlockState state(ItemStack stack) {
        return switch (this) {
            case TORCH -> Blocks.WALL_TORCH.defaultBlockState();
            case COBBLE -> Blocks.COBBLESTONE.defaultBlockState();
            default -> Block.byItem(stack.getItem()).defaultBlockState();
        };
    }
}

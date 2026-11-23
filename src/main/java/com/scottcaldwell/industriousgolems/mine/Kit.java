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
 * What the stone golem builds its mine with. A golem holds one stack in its hand, but a support needs fences and planks together, so the
 * miner also carries a pack of these (see {@link com.scottcaldwell.industriousgolems.golem.WorkGolem#pack()}), topped up from the chest to
 * {@link #target} of each in one visit.
 */
public enum Kit {
    /** Support posts. */
    FENCE(s -> s.is(ItemTags.WOODEN_FENCES), 16, Items.OAK_FENCE),
    /** Support beams. */
    PLANKS(s -> s.is(ItemTags.PLANKS), 12, Items.OAK_PLANKS),
    /** Light, so the corridor does not breed mobs. */
    TORCH(s -> s.is(Items.TORCH), 8, Items.TORCH),
    /** Floor over small gaps, and plugs for liquid. */
    COBBLE(s -> s.is(Items.COBBLESTONE), 16, Items.COBBLESTONE);

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

    /** The block that placing this item makes. */
    public BlockState state(ItemStack stack) {
        return switch (this) {
            case TORCH -> Blocks.TORCH.defaultBlockState();
            case COBBLE -> Blocks.COBBLESTONE.defaultBlockState();
            default -> Block.byItem(stack.getItem()).defaultBlockState();
        };
    }
}

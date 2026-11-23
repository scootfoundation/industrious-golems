package com.scottcaldwell.industriousgolems.mine;

import com.scottcaldwell.industriousgolems.ModMenus;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The stone golem's inventory: a slot each for fence posts, plank beams and torches (each takes only its kind, so an empty one shows plainly what the
 * golem is out of), then five general slots for what it digs, over the player's own.
 */
public class GolemPackMenu extends AbstractContainerMenu {

    public static final int ROW_Y = 20, PLAYER_Y = 48, HEIGHT = 132;
    private static final int KIT_SLOTS = Kit.SPECIAL, SLOTS = WorkGolem.PACK_SIZE;

    private final Container pack;
    private final WorkGolem golem;

    /** The client's copy: a container of its own, kept in step with the golem's by the menu. */
    public GolemPackMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(SLOTS), null);
    }

    public GolemPackMenu(int id, Inventory inventory, WorkGolem golem) {
        this(id, inventory, golem.pack(), golem);
    }

    private GolemPackMenu(int id, Inventory inventory, Container pack, WorkGolem golem) {
        super(ModMenus.GOLEM_PACK.get(), id);
        this.pack = pack;
        this.golem = golem;
        for (Kit part : Kit.values()) {
            if (!part.special()) continue;
            addSlot(new Slot(pack, part.ordinal(), x(part.ordinal()), ROW_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return part.match().test(stack);
                }
            });
        }
        for (int i = KIT_SLOTS; i < SLOTS; i++) addSlot(new Slot(pack, i, x(i), ROW_Y));
        addStandardInventorySlots(inventory, 8, PLAYER_Y);
    }

    /** Left edge of slot {@code i} of the one row: the three of its own, a small gap, then the five general ones, centred across the panel. */
    public static int x(int i) {
        return 12 + i * 18 + (i >= KIT_SLOTS ? 8 : 0);
    }

    @Override
    public boolean stillValid(Player player) {
        return golem == null || (golem.isAlive() && player.distanceToSqr(golem) <= 64.0);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        boolean moved = index < SLOTS ? moveItemStackTo(stack, SLOTS, slots.size(), true) : (Kit.specialSlot(stack) >= 0 && moveItemStackTo(stack, Kit.specialSlot(stack), Kit.specialSlot(stack) + 1, false)) || moveItemStackTo(stack, KIT_SLOTS, SLOTS, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return before;
    }
}

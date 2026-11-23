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
 * The stone golem's inventory: one slot for each of {@link Kit} (fence posts, plank beams, torches, cobblestone) over the player's own. A slot takes
 * only its kind, so an empty one shows plainly what the golem is out of.
 */
public class GolemPackMenu extends AbstractContainerMenu {

    public static final int KIT_X = 44 - 18 / 2 + 9, KIT_Y = 20, PLAYER_Y = 62;
    private static final int KIT_SLOTS = Kit.values().length;

    private final Container pack;
    private final WorkGolem golem;

    /** The client's copy: a container of its own, kept in step with the golem's by the menu. */
    public GolemPackMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(KIT_SLOTS), null);
    }

    public GolemPackMenu(int id, Inventory inventory, WorkGolem golem) {
        this(id, inventory, golem.pack(), golem);
    }

    private GolemPackMenu(int id, Inventory inventory, Container pack, WorkGolem golem) {
        super(ModMenus.GOLEM_PACK.get(), id);
        this.pack = pack;
        this.golem = golem;
        for (Kit part : Kit.values()) {
            addSlot(new Slot(pack, part.ordinal(), x(part), KIT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return part.match().test(stack);
                }
            });
        }
        addStandardInventorySlots(inventory, 8, PLAYER_Y);
    }

    /** Left edge of a kit slot: the four centred across the panel. */
    public static int x(Kit part) {
        return 8 + (176 - 16 - 4 * 18) / 2 + part.ordinal() * 18 + 0;
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
        boolean moved = index < KIT_SLOTS ? moveItemStackTo(stack, KIT_SLOTS, slots.size(), true) : moveItemStackTo(stack, 0, KIT_SLOTS, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return before;
    }
}

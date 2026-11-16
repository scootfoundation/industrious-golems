package com.scottcaldwell.industriousvillagers;

import com.scottcaldwell.industriousvillagers.inventory.VillagerMenu;
import com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * One menu type per profession, so the client already knows the layout (tool slot, slot allowance) and the open
 * packet needs no extra data.
 */
public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, IndustriousVillagers.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<VillagerMenu>> FARMER = MENUS.register("farmer",
            () -> new MenuType<VillagerMenu>(
                    (id, inventory) -> VillagerMenu.client(ModMenus.FARMER.get(), id, inventory, ProfessionNeeds.byKey(ModProfessions.FARMER_KEY)),
                    FeatureFlags.DEFAULT_FLAGS));

    /** The menu type for a profession villager's inventory screen, or null if it has none. */
    public static MenuType<VillagerMenu> typeFor(Villager villager) {
        return villager.getVillagerData().profession().is(ModProfessions.FARMER_KEY) ? FARMER.get() : null;
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}

package com.scottcaldwell.industriousvillagers;

import com.scottcaldwell.industriousvillagers.golem.WorkGolem;
import com.scottcaldwell.industriousvillagers.inventory.GolemMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One menu type per profession, so the client already knows the layout (tool slots, slot allowance) and the open
 * packet needs no extra data.
 */
public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, IndustriousVillagers.MODID);

    private static final Map<String, DeferredHolder<MenuType<?>, MenuType<GolemMenu>>> BY_PROFESSION = new LinkedHashMap<>();

    static {
        for (ModProfessions.Profession profession : ModProfessions.all()) {
            BY_PROFESSION.put(profession.id(), MENUS.register(profession.id(),
                    () -> new MenuType<GolemMenu>(
                            (id, inventory) -> GolemMenu.client(BY_PROFESSION.get(profession.id()).get(), id, inventory, profession.needs()),
                            FeatureFlags.DEFAULT_FLAGS)));
        }
    }

    public static MenuType<GolemMenu> typeFor(WorkGolem golem) {
        return BY_PROFESSION.get(golem.profession().id()).get();
    }

    public static Iterable<DeferredHolder<MenuType<?>, MenuType<GolemMenu>>> all() {
        return BY_PROFESSION.values();
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}

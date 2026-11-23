package com.scottcaldwell.industriousgolems;

import com.scottcaldwell.industriousgolems.mine.GolemPackMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The golem's inventory screen. */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, IndustriousGolems.MODID);

    /** The stone golem's pack of building supplies. */
    public static final DeferredHolder<MenuType<?>, MenuType<GolemPackMenu>> GOLEM_PACK =
            MENUS.register("golem_pack", () -> IMenuTypeExtension.create((id, inventory, buffer) -> new GolemPackMenu(id, inventory)));

    private ModMenus() { }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}

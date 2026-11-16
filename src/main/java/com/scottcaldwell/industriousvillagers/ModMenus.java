package com.scottcaldwell.industriousvillagers;

import com.scottcaldwell.industriousvillagers.inventory.VillagerMenu;
import com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;

/**
 * One menu type per profession with needs (derived from {@link ProfessionNeeds#professions()}), so the client already
 * knows the layout (tool slot, slot allowance) and the open packet needs no extra data.
 */
public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, IndustriousVillagers.MODID);

    private static final Map<ResourceKey<VillagerProfession>, DeferredHolder<MenuType<?>, MenuType<VillagerMenu>>> BY_PROFESSION = new HashMap<>();

    /** The menu for villagers without a profession of ours (unemployed, nitwit, vanilla professions). */
    private static final DeferredHolder<MenuType<?>, MenuType<VillagerMenu>> BASIC = MENUS.register("villager",
            () -> new MenuType<VillagerMenu>(
                    (id, inventory) -> VillagerMenu.client(ModMenus.BASIC.get(), id, inventory, ProfessionNeeds.BASIC_NEEDS),
                    FeatureFlags.DEFAULT_FLAGS));

    static {
        for (ResourceKey<VillagerProfession> key : ProfessionNeeds.professions()) {
            BY_PROFESSION.put(key, MENUS.register(key.identifier().getPath(),
                    () -> new MenuType<VillagerMenu>(
                            (id, inventory) -> VillagerMenu.client(BY_PROFESSION.get(key).get(), id, inventory, ProfessionNeeds.byKey(key)),
                            FeatureFlags.DEFAULT_FLAGS)));
        }
    }

    /** The menu type for a profession villager's inventory screen, or null if it has none. */
    public static MenuType<VillagerMenu> typeFor(Villager villager) {
        return villager.getVillagerData().profession().unwrapKey()
                .map(BY_PROFESSION::get).orElse(BASIC).get();
    }

    public static Iterable<DeferredHolder<MenuType<?>, MenuType<VillagerMenu>>> all() {
        java.util.List<DeferredHolder<MenuType<?>, MenuType<VillagerMenu>>> all = new java.util.ArrayList<>(BY_PROFESSION.values());
        all.add(BASIC);
        return all;
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
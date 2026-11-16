package com.scottcaldwell.industriousvillagers.client;

import com.scottcaldwell.industriousvillagers.IndustriousVillagers;
import com.scottcaldwell.industriousvillagers.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = IndustriousVillagers.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() { }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        for (var menu : ModMenus.all()) event.register(menu.get(), VillagerScreen::new);
    }
}

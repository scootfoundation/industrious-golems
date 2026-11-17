package com.scottcaldwell.industriousvillagers;

import com.mojang.logging.LogUtils;
import com.scottcaldwell.industriousvillagers.golem.WorkGolem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(IndustriousVillagers.MODID)
public class IndustriousVillagers {

    public static final String MODID = "industriousvillagers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IndustriousVillagers(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModGameTests.register(modEventBus);
        ModAttachments.register(modEventBus);
        ModActivities.register(modEventBus);
        ModMenus.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingDeathEvent.class, event -> {
            if (event.getEntity() instanceof WorkGolem golem) {
                com.scottcaldwell.industriousvillagers.needs.NeedsIndicator.clearOnDeath(golem);
            }
        });
        LOGGER.info("Industrious Golems initialising");
    }
}

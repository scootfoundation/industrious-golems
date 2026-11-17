package com.scottcaldwell.industriousgolems;

import com.mojang.logging.LogUtils;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(IndustriousGolems.MODID)
public class IndustriousGolems {

    public static final String MODID = "industriousgolems";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IndustriousGolems(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModGameTests.register(modEventBus);
        ModAttachments.register(modEventBus);
        ModActivities.register(modEventBus);
        ModMenus.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingDeathEvent.class, event -> {
            if (event.getEntity() instanceof WorkGolem golem) {
                com.scottcaldwell.industriousgolems.needs.NeedsIndicator.clearOnDeath(golem);
            }
        });
        LOGGER.info("Industrious Golems initialising");
    }
}

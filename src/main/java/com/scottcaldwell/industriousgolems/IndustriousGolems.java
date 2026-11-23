package com.scottcaldwell.industriousgolems;

import com.mojang.logging.LogUtils;
import com.scottcaldwell.industriousgolems.golem.ChestSpawn;
import com.scottcaldwell.industriousgolems.golem.Timings;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import com.scottcaldwell.industriousgolems.needs.NeedsIndicator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.slf4j.Logger;

@Mod(IndustriousGolems.MODID)
public class IndustriousGolems {

    public static final String MODID = "industriousgolems";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IndustriousGolems(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModGameTests.register(modEventBus);
        ModAttachments.register(modEventBus);
        ModActivities.register(modEventBus);
        ModMenus.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, event -> {
            if (event.getEntity() instanceof WorkGolem golem) {
                NeedsIndicator.clearOnDeath(golem);
            }
        });
        // chest and pick-up timings are data, so /reload picks up an edit
        NeoForge.EVENT_BUS.addListener(AddServerReloadListenersEvent.class,
                event -> event.addListener(Identifier.fromNamespaceAndPath(MODID, "golem_timings"), new Timings()));
        NeoForge.EVENT_BUS.register(ChestSpawn.class);
        LOGGER.info("Industrious Golems initialising");
    }
}

package com.scottcaldwell.industriousvillagers;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(IndustriousVillagers.MODID)
public class IndustriousVillagers {

    public static final String MODID = "industriousvillagers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IndustriousVillagers(IEventBus modEventBus, ModContainer modContainer) {
        ModProfessions.register(modEventBus);
        LOGGER.info("Industrious Villagers initialising");
    }
}

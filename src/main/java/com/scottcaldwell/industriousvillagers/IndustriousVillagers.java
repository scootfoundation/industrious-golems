package com.scottcaldwell.industriousvillagers;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;

@Mod(IndustriousVillagers.MODID)
public class IndustriousVillagers {

    public static final String MODID = "industriousvillagers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IndustriousVillagers(IEventBus modEventBus, ModContainer modContainer) {
        ModProfessions.register(modEventBus);
        ModGameTests.register(modEventBus);
        // Villagers loaded from saved data or /summon come back with CanPickUpLoot=false, so they
        // never collect crop drops (and so never get seeds to replant). Force it on.
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, event -> {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof Villager villager) {
                villager.setCanPickUpLoot(true);
            }
        });
        LOGGER.info("Industrious Villagers initialising");
    }
}

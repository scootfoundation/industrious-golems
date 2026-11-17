package com.scottcaldwell.industriousgolems.client;

import com.scottcaldwell.industriousgolems.IndustriousGolems;
import com.scottcaldwell.industriousgolems.ModEntities;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = IndustriousGolems.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    private static final ModelLayerLocation HAY = layer("hay_golem");
    private static final ModelLayerLocation MUSHROOM = layer("mushroom_golem");
    private static final ModelLayerLocation TERRACOTTA = layer("terracotta_golem");
    private static final ModelLayerLocation STONE = layer("stone_golem");

    private ClientEvents() { }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(IndustriousGolems.MODID, name), "main");
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(IndustriousGolems.MODID, "textures/entity/" + name + ".png");
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HAY, GolemModels::hay);
        event.registerLayerDefinition(MUSHROOM, GolemModels::mushroom);
        event.registerLayerDefinition(TERRACOTTA, GolemModels::terracotta);
        event.registerLayerDefinition(STONE, GolemModels::stone);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // stride: how far the legs swing; armTilt: how far the arms hang out from the body
        event.registerEntityRenderer(ModEntities.FARMER_GOLEM.get(), context -> new GolemRenderer(context, HAY,
                texture("hay_golem"), part -> new GolemModel(part, GolemModels.HAY_HAND, 0.5F, 0.0F), 0.4F));
        event.registerEntityRenderer(ModEntities.LUMBERJACK_GOLEM.get(), context -> new GolemRenderer(context, MUSHROOM,
                texture("mushroom_golem"), part -> new GolemModel(part, GolemModels.MUSHROOM_HAND, 0.7F, 0.0F), 0.5F));
        event.registerEntityRenderer(ModEntities.HERDER_GOLEM.get(), context -> new GolemRenderer(context, TERRACOTTA,
                texture("terracotta_golem"), part -> new GolemModel(part, GolemModels.TERRACOTTA_HAND, 0.6F, 0.36F), 0.6F));
        event.registerEntityRenderer(ModEntities.MINER_GOLEM.get(), context -> new GolemRenderer(context, STONE,
                texture("stone_golem"), part -> new GolemModel(part, GolemModels.STONE_HAND, 0.4F, 0.1F), 0.6F));
    }
}

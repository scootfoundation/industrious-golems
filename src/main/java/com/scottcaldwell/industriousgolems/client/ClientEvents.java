package com.scottcaldwell.industriousgolems.client;

import com.scottcaldwell.industriousgolems.IndustriousGolems;
import com.scottcaldwell.industriousgolems.ModEntities;
import com.scottcaldwell.industriousgolems.client.anim.Animator;
import com.scottcaldwell.industriousgolems.client.anim.BasicAnims;
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
        // front: where an item hugged to the chest sits, in body pixels
        event.registerEntityRenderer(ModEntities.FARMER_GOLEM.get(), context -> new GolemRenderer(context, HAY,
                texture("hay_golem"), Animator.HAY, part -> new GolemModel(part, GolemModels.HAY_HAND, new float[]{0F, -3.15F, -5.5F}), 0.4F));
        event.registerEntityRenderer(ModEntities.LUMBERJACK_GOLEM.get(), context -> new GolemRenderer(context, MUSHROOM,
                texture("mushroom_golem"), Animator.MUSHROOM, part -> new GolemModel(part, GolemModels.MUSHROOM_HAND, new float[]{0F, -2F, -6F}), 0.5F));
        event.registerEntityRenderer(ModEntities.HERDER_GOLEM.get(), context -> new GolemRenderer(context, TERRACOTTA,
                texture("terracotta_golem"), Animator.TERRACOTTA, part -> new GolemModel(part, GolemModels.TERRACOTTA_HAND, new float[]{0F, -8.1F, -6F}), 0.6F));
        event.registerEntityRenderer(ModEntities.MINER_GOLEM.get(), context -> new GolemRenderer(context, STONE,
                texture("stone_golem"), BasicAnims.kit(true, 0.0), part -> new GolemModel(part, GolemModels.STONE_HAND, new float[]{0F, -5.85F, -9.5F}), 0.6F));
    }
}

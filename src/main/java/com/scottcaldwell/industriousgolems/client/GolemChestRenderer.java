package com.scottcaldwell.industriousgolems.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.scottcaldwell.industriousgolems.IndustriousGolems;
import com.scottcaldwell.industriousgolems.block.GolemChestEntity;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a golem chest the way vanilla draws a chest: the standard chest model with the chest's own texture (which has
 * the interior painted on it), and the lid swung up by the block entity's openness.
 */
public class GolemChestRenderer implements BlockEntityRenderer<GolemChestEntity, GolemChestRenderer.State> {

    public static class State extends ChestRenderState {
        public SpriteId golemSprite;
    }

    private final SpriteGetter sprites;
    private final ChestModel model;

    public GolemChestRenderer(BlockEntityRendererProvider.Context context) {
        this.sprites = context.sprites();
        this.model = new ChestModel(layer().bakeRoot());
    }

    /**
     * The vanilla chest's boxes, except that the lid is a hair smaller, so that the pixel row where it overlaps the base sits
     * inside the base's walls instead of sharing their plane (which flickers where the lid meets the body).
     */
    private static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation shrink = new CubeDeformation(-0.02F);
        root.addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(0, 19).addBox(1.0F, 0.0F, 1.0F, 14.0F, 10.0F, 14.0F), PartPose.ZERO);
        root.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 0).addBox(1.0F, 0.0F, 0.0F, 14.0F, 5.0F, 14.0F, shrink), PartPose.offset(0.0F, 9.0F, 1.0F));
        root.addOrReplaceChild("lock", CubeListBuilder.create().texOffs(0, 0).addBox(7.0F, -2.0F, 14.0F, 2.0F, 4.0F, 1.0F), PartPose.offset(0.0F, 9.0F, 1.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GolemChestEntity chest, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(chest, state, partialTick, cameraPos, crumbling);
        state.facing = chest.getBlockState().getValue(ChestBlock.FACING);
        state.open = chest.getOpenNess(partialTick);
        // hay_chest -> textures/entity/chest/hay.png
        String id = BuiltInRegistries.BLOCK.getKey(chest.getBlockState().getBlock()).getPath().replace("_chest", "");
        state.golemSprite = Sheets.CHEST_MAPPER.apply(Identifier.fromNamespaceAndPath(IndustriousGolems.MODID, id));
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.mulPose(ChestRenderer.modelTransformation(state.facing));
        float open = 1.0F - state.open;
        open = 1.0F - open * open * open; // the lid eases out, as vanilla's does
        collector.submitModel(model, open, pose, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, state.golemSprite, sprites, 0);
        if (state.breakProgress != null) {
            collector.order(1).submitCrumblingOverlay(model, open, pose, state.golemSprite.renderType(model.renderType()), state.lightCoords,
                    OverlayTexture.NO_OVERLAY, -1, state.breakProgress);
        }
        pose.popPose();
    }
}

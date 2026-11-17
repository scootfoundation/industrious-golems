package com.scottcaldwell.industriousgolems.client;

import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.PartPose;

/**
 * Generated from the golem design page (see the golem workshop artifact): the box geometry of each golem. All use a
 * 128x128 texture. The head and arms hang off the body, the hips (terracotta golem only) and legs off the root.
 * Do not edit by hand.
 */
final class GolemModels {
    private GolemModels() { }

    static LayerDefinition hay() {
        MeshDefinition mesh = new MeshDefinition().transformed(p -> p.translated(0.0F, 24.0F, 0.0F));
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(36, 0).addBox(-4F, -6F, -2F, 8F, 6F, 4F, new CubeDeformation(0F)),
                PartPose.offset(0F, -12F, 0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4F, -5F, -3F, 8F, 5F, 6F, new CubeDeformation(0F))
                .texOffs(28, 0).addBox(-1F, -2F, -5F, 2F, 3F, 2F, new CubeDeformation(0F))
                .texOffs(0, 24).addBox(-4.5F, -8.5F, -3.5F, 9F, 4F, 7F, new CubeDeformation(0F))
                .texOffs(0, 40).addBox(-8F, -4.5F, -7F, 16F, 1F, 14F, new CubeDeformation(0F)),
                PartPose.offset(0F, -6F, 0F));
        PartDefinition p_rarm = p_body.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(60, 0).addBox(-2F, -1F, -1F, 2F, 11F, 2F, new CubeDeformation(0F)),
                PartPose.offset(-4F, -6F, 0F));
        PartDefinition p_larm = p_body.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(60, 0).addBox(0F, -1F, -1F, 2F, 11F, 2F, new CubeDeformation(0F)),
                PartPose.offset(4F, -6F, 0F));
        PartDefinition p_rleg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(68, 0).addBox(-1F, 0F, -1F, 2F, 12F, 2F, new CubeDeformation(0F)),
                PartPose.offset(-2F, -12F, 0F));
        PartDefinition p_lleg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(68, 0).addBox(-1F, 0F, -1F, 2F, 12F, 2F, new CubeDeformation(0F)),
                PartPose.offset(2F, -12F, 0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Where the held item sits, in the right arm's own pixels (mirrored for the left arm). */
    static final float[] HAY_HAND = {-1F, 10F, 0F};

    static LayerDefinition mushroom() {
        MeshDefinition mesh = new MeshDefinition().transformed(p -> p.translated(0.0F, 24.0F, 0.0F));
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 15).addBox(-4F, -6F, -3F, 8F, 6F, 6F, new CubeDeformation(0F)),
                PartPose.offset(0F, -5F, 0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4F, -5F, -5F, 8F, 5F, 10F, new CubeDeformation(0.015F))
                .texOffs(56, 0).addBox(-1F, -2F, -6F, 2F, 3F, 2F, new CubeDeformation(0F))
                .texOffs(0, 40).addBox(-11F, -7F, -9F, 22F, 3F, 18F, new CubeDeformation(0F))
                .texOffs(0, 64).addBox(-6F, -9F, -4F, 12F, 2F, 8F, new CubeDeformation(0F)),
                PartPose.offset(0F, -6F, 0F));
        PartDefinition p_rarm = p_body.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(36, 16).addBox(-3F, -1F, -2F, 3F, 10F, 4F, new CubeDeformation(0F)),
                PartPose.offset(-4F, -6F, 0F));
        PartDefinition p_larm = p_body.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(50, 16).addBox(0F, -1F, -2F, 3F, 10F, 4F, new CubeDeformation(0F)),
                PartPose.offset(4F, -6F, 0F));
        PartDefinition p_rleg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 27).addBox(-4F, 0F, -2F, 4F, 5F, 4F, new CubeDeformation(0F)),
                PartPose.offset(0F, -5F, 0F));
        PartDefinition p_lleg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(16, 27).addBox(0F, 0F, -2F, 4F, 5F, 4F, new CubeDeformation(0F)),
                PartPose.offset(0F, -5F, 0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Where the held item sits, in the right arm's own pixels (mirrored for the left arm). */
    static final float[] MUSHROOM_HAND = {-1.5F, 9F, 0F};

    static LayerDefinition terracotta() {
        MeshDefinition mesh = new MeshDefinition().transformed(p -> p.translated(0.0F, 24.0F, 0.0F));
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(24, 0).addBox(-4F, -18F, -2.5F, 8F, 7F, 5F, new CubeDeformation(0F)),
                PartPose.offset(0F, -3F, 0F));
        PartDefinition p_hips = root.addOrReplaceChild("hips", CubeListBuilder.create()
                .texOffs(50, 0).addBox(-7F, -11F, -4F, 14F, 11F, 8F, new CubeDeformation(0F)),
                PartPose.offset(0F, -3F, 0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3F, -6F, -3F, 6F, 6F, 6F, new CubeDeformation(0F))
                .texOffs(20, 22).addBox(-3.5F, -7.5F, -3.5F, 7F, 2F, 7F, new CubeDeformation(0F)),
                PartPose.offset(0F, -18F, 0F));
        PartDefinition p_rarm = p_body.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(94, 0).addBox(-4F, -1F, -2F, 4F, 18F, 4F, new CubeDeformation(0F)),
                PartPose.offset(-4F, -18F, 0F));
        PartDefinition p_larm = p_body.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(94, 0).addBox(0F, -1F, -2F, 4F, 18F, 4F, new CubeDeformation(0F)),
                PartPose.offset(4F, -18F, 0F));
        PartDefinition p_rleg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 22).addBox(-2.5F, 0F, -2.5F, 5F, 3F, 5F, new CubeDeformation(0F)),
                PartPose.offset(-3.5F, -3F, 0F));
        PartDefinition p_lleg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(0, 22).addBox(-2.5F, 0F, -2.5F, 5F, 3F, 5F, new CubeDeformation(0F)),
                PartPose.offset(3.5F, -3F, 0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Where the held item sits, in the right arm's own pixels (mirrored for the left arm). */
    static final float[] TERRACOTTA_HAND = {-2F, 17F, 0F};

}

package com.scottcaldwell.industriousgolems.client;

import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.PartPose;

/**
 * Generated from the golem design page (see the golem workshop artifact): the box geometry of each golem. All use a
 * 128x128 texture. The head and arms hang off the body (the mushroom cap off the head), the hips (terracotta golem
 * only) and legs off the root. Do not edit by hand.
 */
final class GolemModels {
    private GolemModels() { }

    /** The hay golem: a thin stilt-legged scarecrow in a wide straw hat (the hat is painted into the main texture). */
    static LayerDefinition hay() {
        MeshDefinition mesh = new MeshDefinition().transformed(p -> p.translated(0.0F, 24.0F, 0.0F));
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(8, 14).addBox(-4F, -7F, -2F, 8F, 7F, 4F, new CubeDeformation(0F)),
                PartPose.offset(0F, -18F, 0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4F, -5F, -3F, 8F, 5F, 6F, new CubeDeformation(0F))
                .texOffs(28, 0).addBox(-1F, -2F, -5F, 2F, 3F, 2F, new CubeDeformation(0F))
                .texOffs(0, 40).addBox(-4.5F, -8.5F, -3.5F, 9F, 4F, 7F, new CubeDeformation(0F))
                .texOffs(0, 56).addBox(-8F, -4.5F, -7F, 16F, 1F, 14F, new CubeDeformation(0F)),
                PartPose.offset(0F, -7F, 0F));
        PartDefinition p_rarm = p_body.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(32, 14).addBox(-2F, -1F, -1F, 2F, 16F, 2F, new CubeDeformation(0F)),
                PartPose.offset(-4F, -7F, 0F));
        PartDefinition p_larm = p_body.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(32, 14).addBox(0F, -1F, -1F, 2F, 16F, 2F, new CubeDeformation(0F)),
                PartPose.offset(4F, -7F, 0F));
        PartDefinition p_rleg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 14).addBox(-1F, 0F, -1F, 2F, 18F, 2F, new CubeDeformation(0F)),
                PartPose.offset(-2F, -18F, 0F));
        PartDefinition p_lleg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(0, 14).addBox(-1F, 0F, -1F, 2F, 18F, 2F, new CubeDeformation(0F)),
                PartPose.offset(2F, -18F, 0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Where the held item sits, in the arm's own pixels, measured from where the item layer already puts it 10 pixels down the arm (mirrored for the left arm). */
    static final float[] HAY_HAND = {-1F, 5F, 0F};

    /** The mushroom golem: a squat stem body under a wide spotted cap that sits on its head. */
    static LayerDefinition mushroom() {
        MeshDefinition mesh = new MeshDefinition().transformed(p -> p.translated(0.0F, 24.0F, 0.0F));
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(14, 0).addBox(-4F, -4F, -3F, 8F, 4F, 6F, new CubeDeformation(0F)),
                PartPose.offset(0F, -2F, 0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4F, -5F, -5F, 8F, 5F, 10F, new CubeDeformation(0.015F))
                .texOffs(42, 0).addBox(-1F, -2F, -6F, 2F, 3F, 2F, new CubeDeformation(0F)),
                PartPose.offset(0F, -4F, 0F));
        PartDefinition p_cap = p_head.addOrReplaceChild("cap", CubeListBuilder.create()
                .texOffs(0, 10).addBox(-11F, -4F, -9F, 22F, 4F, 18F, new CubeDeformation(0F))
                .texOffs(80, 10).addBox(-6F, -7F, -4F, 12F, 3F, 8F, new CubeDeformation(0F)),
                PartPose.offset(0F, -5F, 0F));
        PartDefinition p_rarm = p_body.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(50, 0).addBox(-3F, -1F, -2F, 3F, 6F, 4F, new CubeDeformation(0F))
                .texOffs(64, 0).addBox(-3.5F, 3F, -2.5F, 4F, 2F, 5F, new CubeDeformation(0F)),
                PartPose.offset(-4F, -3F, 0F));
        PartDefinition p_larm = p_body.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(50, 0).addBox(0F, -1F, -2F, 3F, 6F, 4F, new CubeDeformation(0F))
                .texOffs(64, 0).addBox(-0.5F, 3F, -2.5F, 4F, 2F, 5F, new CubeDeformation(0F)),
                PartPose.offset(4F, -3F, 0F));
        PartDefinition p_rleg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4F, 0F, -2F, 3F, 2F, 4F, new CubeDeformation(0F)),
                PartPose.offset(0F, -2F, 0F));
        PartDefinition p_lleg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(0, 0).addBox(1F, 0F, -2F, 3F, 2F, 4F, new CubeDeformation(0F)),
                PartPose.offset(0F, -2F, 0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Where the held item sits, in the arm's own pixels, measured from where the item layer already puts it 10 pixels down the arm (mirrored for the left arm). */
    static final float[] MUSHROOM_HAND = {-1.5F, -7F, 0F};

    /** The terracotta golem: a wide pot-like hip, a narrow chest and a small head, with long arms. */
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

    /** Where the held item sits, in the arm's own pixels, measured from where the item layer already puts it 10 pixels down the arm (mirrored for the left arm). */
    static final float[] TERRACOTTA_HAND = {-2F, 7F, 0F};

    /** The stone golem: a boulder chest with a small head and fists like rocks. */
    static LayerDefinition stone() {
        MeshDefinition mesh = new MeshDefinition().transformed(p -> p.translated(0.0F, 24.0F, 0.0F));
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 22).addBox(-6F, -3F, -4F, 12F, 3F, 8F, new CubeDeformation(0F))
                .texOffs(56, 0).addBox(-12F, -13F, -6F, 24F, 10F, 12F, new CubeDeformation(0F)),
                PartPose.offset(0F, -5F, 0F));
        PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6F, -6F, -5F, 12F, 6F, 10F, new CubeDeformation(0F))
                .texOffs(44, 0).addBox(-1.5F, -2F, -8F, 3F, 3F, 3F, new CubeDeformation(0F))
                .texOffs(24, 43).addBox(-3F, -9F, -2F, 5F, 3F, 4F, new CubeDeformation(0F))
                .texOffs(42, 43).addBox(2F, -8F, 1F, 3F, 2F, 3F, new CubeDeformation(0F)),
                PartPose.offset(0F, -13F, 0F));
        PartDefinition p_rarm = p_body.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(0, 54).addBox(-7F, -1F, -3.5F, 7F, 10F, 7F, new CubeDeformation(0F))
                .texOffs(28, 54).addBox(-9F, 8F, -5.5F, 11F, 8F, 11F, new CubeDeformation(0F)),
                PartPose.offset(-12F, -13F, 0F));
        PartDefinition p_larm = p_body.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(0, 54).addBox(0F, -1F, -3.5F, 7F, 10F, 7F, new CubeDeformation(0F))
                .texOffs(28, 54).addBox(-2F, 8F, -5.5F, 11F, 8F, 11F, new CubeDeformation(0F)),
                PartPose.offset(12F, -13F, 0F));
        PartDefinition p_rleg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 43).addBox(-3F, 0F, -3F, 6F, 5F, 6F, new CubeDeformation(0F)),
                PartPose.offset(-3F, -5F, 0F));
        PartDefinition p_lleg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(0, 43).addBox(-3F, 0F, -3F, 6F, 5F, 6F, new CubeDeformation(0F)),
                PartPose.offset(3F, -5F, 0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Where the held item sits, in the arm's own pixels, measured from where the item layer already puts it 10 pixels down the arm (mirrored for the left arm). */
    static final float[] STONE_HAND = {-3.5F, 3F, 0F};
}

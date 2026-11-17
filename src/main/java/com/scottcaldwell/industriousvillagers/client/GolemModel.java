package com.scottcaldwell.industriousvillagers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The model shared by the three work golems (they differ in proportions only, built by {@link GolemModels}). Poses:
 * walking swings the legs and arms; holding a tool brings the right arm forward; a swing lifts the arm and strikes it
 * down; a golem that cannot work shakes its head.
 */
public class GolemModel extends EntityModel<GolemRenderState> implements ArmedModel<GolemRenderState>, HeadedModel {

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final float[] hand;
    /** How far the legs swing, and how much the whole body bobs, per kind. */
    private final float stride;
    /** Arms hang slightly out from the body (the terracotta golem's wide hips would otherwise hide them). */
    private final float armTilt;

    public GolemModel(ModelPart root, float[] hand, float stride, float armTilt) {
        super(root);
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.hand = hand;
        this.stride = stride;
        this.armTilt = armTilt;
    }

    @Override
    public void setupAnim(GolemRenderState state) {
        super.setupAnim(state);
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        if (state.unhappy > 0) { // shaking its head: it cannot work
            head.zRot = 0.3F * Mth.sin(0.45F * state.ageInTicks);
            head.xRot = 0.4F;
        }

        float swing = state.walkAnimationPos * 0.6662F;
        float amount = Math.min(1.0F, state.walkAnimationSpeed * 2.0F);
        rightLeg.xRot = Mth.cos(swing) * stride * amount;
        leftLeg.xRot = Mth.cos(swing + Mth.PI) * stride * amount;

        boolean holding = !state.rightHandItemState.isEmpty();
        rightArm.xRot = Mth.cos(swing + Mth.PI) * 0.5F * amount;
        leftArm.xRot = Mth.cos(swing) * 0.5F * amount;
        rightArm.zRot = armTilt;
        leftArm.zRot = -armTilt;
        rightArm.yRot = 0.0F;
        leftArm.yRot = 0.0F;
        if (holding) rightArm.xRot = Math.min(rightArm.xRot, -0.9F);

        if (state.attackTime > 0.0F) { // working: lift the arm and strike down (both arms when empty-handed)
            float strike = Mth.sin(state.attackTime * Mth.PI);
            rightArm.xRot = -0.75F - 1.3F * strike;
            rightArm.yRot = 0.3F * Mth.sin(state.attackTime * Mth.PI * 2.0F);
            if (!holding) {
                leftArm.xRot = rightArm.xRot;
                leftArm.yRot = -rightArm.yRot;
            }
        }
    }

    @Override
    public void translateToHand(GolemRenderState state, HumanoidArm arm, PoseStack poseStack) {
        root.translateAndRotate(poseStack);
        body.translateAndRotate(poseStack);
        boolean right = arm == HumanoidArm.RIGHT;
        (right ? rightArm : leftArm).translateAndRotate(poseStack);
        poseStack.translate((right ? hand[0] : -hand[0]) / 16.0F, hand[1] / 16.0F, hand[2] / 16.0F);
    }

    @Override
    public ModelPart getHead() {
        return head;
    }

    @Override
    public void translateToHead(PoseStack poseStack) {
        body.translateAndRotate(poseStack);
        head.translateAndRotate(poseStack);
    }
}

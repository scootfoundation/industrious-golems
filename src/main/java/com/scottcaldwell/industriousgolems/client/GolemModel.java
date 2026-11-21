package com.scottcaldwell.industriousgolems.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.scottcaldwell.industriousgolems.client.anim.Pose;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;

/**
 * The model shared by the work golems (they differ in proportions and parts only, built by {@link GolemModels}). It does
 * no animating of its own: the pose comes from the golem's clip, see {@code client.anim}.
 */
public class GolemModel extends EntityModel<GolemRenderState> implements ArmedModel<GolemRenderState>, HeadedModel {

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart hips;
    private final ModelPart cap;
    private final ModelPart rightFore;
    private final ModelPart leftFore;
    private final float[] hand;
    /** Where an item held in front of the chest sits, in body pixels. */
    private final float[] front;

    /** Whether a carried item sits between the two hands, rather than in the right one. */
    private final boolean between;

    public GolemModel(ModelPart root, float[] hand, float[] front) {
        this(root, hand, front, false);
    }

    public GolemModel(ModelPart root, float[] hand, float[] front, boolean between) {
        super(root);
        this.between = between;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.hips = root.hasChild("hips") ? root.getChild("hips") : null;
        this.cap = head.hasChild("cap") ? head.getChild("cap") : null;
        this.rightFore = rightArm.hasChild("right_forearm") ? rightArm.getChild("right_forearm") : null;
        this.leftFore = leftArm.hasChild("left_forearm") ? leftArm.getChild("left_forearm") : null;
        this.hand = hand;
        this.front = front;
    }

    private static void apply(ModelPart m, Pose.Part p) {
        if (m == null) return;
        m.resetPose();
        m.xRot += p.rx;
        m.yRot += p.ry;
        m.zRot += p.rz;
        m.x += p.x;
        m.y += p.y;
        m.z += p.z;
        m.xScale = 1.0F + p.sx;
        m.yScale = 1.0F + p.sy;
        m.zScale = 1.0F + p.sz;
    }

    @Override
    public void setupAnim(GolemRenderState state) {
        super.setupAnim(state);
        Pose p = state.pose;
        apply(body, p.body);
        apply(head, p.head);
        apply(rightArm, p.rarm);
        apply(leftArm, p.larm);
        apply(rightLeg, p.rleg);
        apply(leftLeg, p.lleg);
        apply(hips, p.hips);
        apply(cap, p.cap);
        apply(rightFore, p.rfore);
        apply(leftFore, p.lfore);
    }

    @Override
    public void translateToHand(GolemRenderState state, HumanoidArm arm, PoseStack poseStack) {
        root.translateAndRotate(poseStack);
        body.translateAndRotate(poseStack);
        boolean right = arm == HumanoidArm.RIGHT;
        String hold = right ? state.hold : null;
        if ("tuck".equals(hold) && rightFore != null) { // curled in the crook of the right arm
            rightArm.translateAndRotate(poseStack);
            rightFore.translateAndRotate(poseStack);
            poseStack.translate(7.0F / 16.0F, -1.0F / 16.0F, -6.0F / 16.0F);
            return;
        }
        if ("front".equals(hold) || "both".equals(hold) || "tuck".equals(hold)) {
            poseStack.translate(front[0] / 16.0F, front[1] / 16.0F, front[2] / 16.0F);
            return;
        }
        if (between && hold == null) { // cradled between both hands: halfway between where each would hold it
            Vector3f r = handPoint(rightArm, rightFore, hand[0]), l = handPoint(leftArm, leftFore, -hand[0]);
            poseStack.translate((r.x + l.x) / 2, (r.y + l.y) / 2, (r.z + l.z) / 2);
            return;
        }
        ModelPart a = right ? rightArm : leftArm;
        a.translateAndRotate(poseStack);
        ModelPart fore = right ? rightFore : leftFore;
        if (fore != null) fore.translateAndRotate(poseStack);
        poseStack.translate((right ? hand[0] : -hand[0]) / 16.0F, hand[1] / 16.0F, hand[2] / 16.0F);
    }

    /** Where a hand is, in the body's space, with the item's offset down the arm. */
    private Vector3f handPoint(ModelPart arm, ModelPart fore, float sideways) {
        PoseStack ps = new PoseStack();
        arm.translateAndRotate(ps);
        if (fore != null) fore.translateAndRotate(ps);
        ps.translate(sideways / 16.0F, hand[1] / 16.0F, hand[2] / 16.0F);
        return ps.last().pose().transformPosition(new Vector3f());
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

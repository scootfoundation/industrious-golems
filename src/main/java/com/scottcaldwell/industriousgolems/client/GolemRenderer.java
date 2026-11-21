package com.scottcaldwell.industriousgolems.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.scottcaldwell.industriousgolems.offering.Offering;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.HumanoidArm;
import java.util.List;
import com.scottcaldwell.industriousgolems.client.anim.AnimState;
import com.scottcaldwell.industriousgolems.client.anim.Animator;
import com.scottcaldwell.industriousgolems.client.anim.Kit;
import com.scottcaldwell.industriousgolems.client.anim.Pose;
import com.scottcaldwell.industriousgolems.golem.ItemKind;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/** Draws a work golem with its kind's model and texture, posed by the animation clip its task asked for, and the item it holds. */
public class GolemRenderer extends MobRenderer<WorkGolem, GolemRenderState, GolemModel> {

    private final Identifier texture;
    private final Kit kit;
    private final Map<WorkGolem, AnimState> memory = new WeakHashMap<>();

    public GolemRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, Identifier texture, Kit kit,
                         Function<ModelPart, GolemModel> model, float shadowRadius) {
        super(context, model.apply(context.bakeLayer(layer)), shadowRadius);
        this.texture = texture;
        this.kit = kit;
        addLayer(new ItemInHandLayer<>(this));
    }

    @Override
    public Identifier getTextureLocation(GolemRenderState state) {
        return texture;
    }

    @Override
    public GolemRenderState createRenderState() {
        return new GolemRenderState();
    }

    @Override
    public void extractRenderState(WorkGolem golem, GolemRenderState state, float partialTick) {
        super.extractRenderState(golem, state, partialTick);
        ArmedEntityRenderState.extractArmedEntityRenderState(golem, state, itemModelResolver, partialTick);
        state.unhappy = golem.getUnhappyCounter();
        state.dormant = !golem.isCharged();
        String item = ItemKind.of(golem.carried());
        // the craving is drawn floating above the head, not in the off hand where the server keeps a copy of it
        (state.mainArm == HumanoidArm.RIGHT ? state.leftHandItemState : state.rightHandItemState).clear();
        extractNeed(golem, state);
        String clip = golem.clip();
        double clipTime = (golem.level().getGameTime() + partialTick - golem.clipStart()) / 20.0;
        double walkDist = state.walkAnimationPos * 4.0;
        boolean moving = state.walkAnimationSpeed > 0.04F;
        AnimState memory = this.memory.computeIfAbsent(golem, g -> new AnimState());
        state.hold = Animator.evaluate(kit, state.pose, clip.isEmpty() ? null : clip, clipTime, moving, walkDist, state.ageInTicks / 20.0,
                state.dormant && golem.cravingIndex() >= 0, item, memory);
        state.headYaw = state.yRot;
    }

    /** What a dormant golem is asking for. */
    private void extractNeed(WorkGolem golem, GolemRenderState state) {
        List<Offering> offerings = golem.profession().offerings();
        int craving = golem.cravingIndex();
        state.asking = !golem.isCharged() && craving >= 0 && craving < offerings.size();
        state.needSprite = null;
        state.needName = null;
        if (!state.asking) {
            state.need.clear();
            return;
        }
        Offering offering = offerings.get(craving);
        ItemStack icon = offering.icon();
        itemModelResolver.updateForNonLiving(state.need, icon, ItemDisplayContext.GROUND, golem);
        state.needSprite = state.need.pickParticleIcon(RandomSource.create(1L));
        if (entityRenderDispatcher.crosshairPickEntity == golem) {
            state.needName = offering.displayName();
            Vec3 at = state.nameTagAttachment;
            state.nameTag = state.needName;
            state.nameTagAttachment = (at == null ? new Vec3(0, state.boundingBoxHeight + 0.5, 0) : at).add(0, 0.8, 0);
        }
    }

    /** After the golem itself: the item it is asking for, a see-through icon that turns to face the camera and bobs above its head. */
    @Override
    public void submit(GolemRenderState state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        super.submit(state, pose, out, camera);
        if (!state.asking || state.need.isEmpty()) return;
        float bob = Mth.sin(state.ageInTicks / 12.0F) * 0.06F;
        pose.pushPose();
        pose.translate(0.0F, state.boundingBoxHeight + 0.5F + bob, 0.0F);
        TextureAtlasSprite sprite = state.needSprite;
        if (sprite == null) { // no flat picture of it: the real model, turning
            pose.mulPose(Axis.YP.rotationDegrees(state.ageInTicks * 3.0F));
            pose.scale(0.6F, 0.6F, 0.6F);
            state.need.submit(pose, out, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
        } else {
            pose.mulPose(camera.orientation);
            float h = 0.3F;
            float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
            out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(sprite.atlasLocation()), (p, vc) -> {
                float[][] corner = {{-h, -h, u0, v1}, {h, -h, u1, v1}, {h, h, u1, v0}, {-h, h, u0, v0}};
                for (int side = 0; side < 2; side++) { // both ways round, so no face culling can hide it (the far side a hair behind, so the two never fight)
                    for (int k = 0; k < 4; k++) {
                        float[] c = corner[side == 0 ? k : 3 - k];
                        vc.addVertex(p, c[0], c[1], side == 0 ? 0.0F : -0.004F).setColor(1.0F, 1.0F, 1.0F, 0.6F).setUv(c[2], c[3])
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                                .setNormal(p, 0.0F, 0.0F, side == 0 ? 1.0F : -1.0F);
                    }
                }
            });
        }
        pose.popPose();
    }

    @Override
    protected boolean shouldShowName(WorkGolem golem, double distance) {
        return super.shouldShowName(golem, distance)
                || (!golem.isCharged() && golem.cravingIndex() >= 0 && entityRenderDispatcher.crosshairPickEntity == golem);
    }

    /** Moves the whole golem as its clip asks (climbing a chest, hopping, hiding), in the clip's frame: y up, z negative forward. */
    @Override
    protected void scale(GolemRenderState state, PoseStack poseStack) {
        Pose p = state.pose;
        if (p.hide) {
            poseStack.scale(0.0001F, 0.0001F, 0.0001F);
            return;
        }
        poseStack.translate(-p.offX / 16.0F, -p.offY / 16.0F, p.offZ / 16.0F);
        if (p.offRy != 0.0F) poseStack.mulPose(Axis.YP.rotation(-p.offRy));
        if (p.scale != 1.0F) {
            poseStack.translate(0.0F, 1.501F, 0.0F);
            poseStack.scale(p.scale, p.scale, p.scale);
            poseStack.translate(0.0F, -1.501F, 0.0F);
        }
    }
}

package com.scottcaldwell.industriousgolems.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
        if (item == null) item = ItemKind.of(golem.getItemBySlot(EquipmentSlot.OFFHAND));
        String clip = golem.clip();
        double clipTime = (golem.level().getGameTime() + partialTick - golem.clipStart()) / 20.0;
        double walkDist = state.walkAnimationPos * 4.0;
        boolean moving = state.walkAnimationSpeed > 0.04F;
        AnimState memory = this.memory.computeIfAbsent(golem, g -> new AnimState());
        state.hold = Animator.evaluate(kit, state.pose, clip.isEmpty() ? null : clip, clipTime, moving, walkDist, state.ageInTicks / 20.0,
                state.dormant && golem.cravingIndex() >= 0, item, memory);
        state.headYaw = state.yRot;
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

package com.scottcaldwell.industriousgolems.client;

import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

/** Draws a work golem with its kind's model and texture, and the tool it is using in its hand. */
public class GolemRenderer extends MobRenderer<WorkGolem, GolemRenderState, GolemModel> {

    private final Identifier texture;

    public GolemRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, Identifier texture,
                         Function<ModelPart, GolemModel> model, float shadowRadius) {
        super(context, model.apply(context.bakeLayer(layer)), shadowRadius);
        this.texture = texture;
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
    }
}

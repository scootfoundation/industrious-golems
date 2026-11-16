package com.scottcaldwell.industriousvillagers.mixin.client;

import com.scottcaldwell.industriousvillagers.IndustriousVillagers;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Map;

/**
 * Our professions reuse vanilla villager skins (PRD: "minimise new assets"). The renderer resolves
 * textures under the profession's own namespace, so without this our professions render as the
 * missing-texture checkerboard. Redirect them to the vanilla profession textures (and hat metadata,
 * which is resolved through the same method).
 */
@Mixin(VillagerProfessionLayer.class)
public class VillagerProfessionLayerMixin {

    private static final Map<String, String> VANILLA_SKIN = Map.of(
            "farmer", "farmer",
            "lumberjack", "fletcher",
            "miner", "mason"
    );

    @ModifyVariable(
            method = "getIdentifier(Ljava/lang/String;Lnet/minecraft/resources/Identifier;)Lnet/minecraft/resources/Identifier;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Identifier industriousvillagers$useVanillaSkin(Identifier id) {
        if (IndustriousVillagers.MODID.equals(id.getNamespace())) {
            String vanilla = VANILLA_SKIN.get(id.getPath());
            if (vanilla != null) {
                return Identifier.withDefaultNamespace(vanilla);
            }
        }
        return id;
    }
}

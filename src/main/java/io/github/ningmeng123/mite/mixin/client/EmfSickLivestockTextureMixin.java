package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.SickLivestockVisuals;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Explicit CEM base-atlas bindings follow the resolved gray-white body; independent atlases remain unchanged. @author NingMeng123 */
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPart", remap = false)
public abstract class EmfSickLivestockTextureMixin {
    // The factory has already resolved ETF variants here, so aliases match actual textures rather than unvaried names.
    @ModifyVariable(method = "renderTextureOverrideWithoutReset(Ltraben/entity_texture_features/utils/URenderTypeToVertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack;III)V",
            at = @At("STORE"), ordinal = 0, require = 1, remap = false)
    private RenderType mite$keepGrayBaseTexture(RenderType layer) {
        return SickLivestockVisuals.baseTextureLayer(layer);
    }
}

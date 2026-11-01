package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.SickLivestockVisuals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Independent CEM atlases, including Fresh Animations wool, retain their original colors. @author NingMeng123 */
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPart", remap = false)
public abstract class EmfSickLivestockPartColorMixin {
    // EMF calls this only when switching to a part's texture override; the already bound base atlas bypasses it.
    @ModifyVariable(method = "renderTextureOverrideWithoutReset(Ltraben/entity_texture_features/utils/URenderTypeToVertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack;III)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 1, remap = false)
    private int mite$keepIndependentTextureColor(int color) {
        return SickLivestockVisuals.independentTextureColor(color);
    }
}

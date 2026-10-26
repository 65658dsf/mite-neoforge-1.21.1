package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ningmeng123.mite.client.EmfPartTextureCompatibility;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Enter before Accelerated Rendering's cancellable HEAD hook, including nested and failed renders. @author NingMeng123 */
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPart", remap = false)
public abstract class EmfPartTextureCompatibilityMixin {
    @WrapMethod(method = "renderLikeVanilla(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", remap = false)
    private void mite$preserveChildTextures(PoseStack pose, VertexConsumer consumer, int light, int overlay,
            int color, Operation<Void> original) {
        int previousDepth = EmfPartTextureCompatibility.begin((ModelPart)(Object)this);
        try {
            original.call(pose, consumer, light, overlay, color);
        } finally {
            EmfPartTextureCompatibility.end(previousDepth);
        }
    }
}

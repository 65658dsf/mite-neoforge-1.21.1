package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ningmeng123.mite.client.SickLivestockVisuals;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Tint only customized sick livestock bodies while retaining their resource-pack texture layout. @author NingMeng123 */
@Mixin(LivingEntityRenderer.class)
public abstract class SickLivestockBodyColorMixin<T extends LivingEntity> {
    @WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void mite$sickCustomizedBody(EntityModel<T> model, PoseStack pose, VertexConsumer vertices, int light,
            int overlay, int color, Operation<Void> original, @Local(argsOnly = true) T entity) {
        int tintedColor = SickLivestockVisuals.bodyColor(entity, model, color);
        var scope = SickLivestockVisuals.beginBodyColor(color, tintedColor);
        try {
            original.call(model, pose, vertices, light, overlay, tintedColor);
        } finally {
            SickLivestockVisuals.endBodyColor(scope);
        }
    }
}

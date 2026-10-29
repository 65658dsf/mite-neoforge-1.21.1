package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ningmeng123.mite.client.mob.InvisibleMonsterVisuals;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Render invisible hostile bodies through the normal lit translucent pass. @author NingMeng123 */
@Mixin(LivingEntityRenderer.class)
public abstract class InvisibleMonsterRendererMixin<T extends LivingEntity> {
    @WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType(Lnet/minecraft/world/entity/LivingEntity;ZZZ)Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType mite$showInvisibleHostile(LivingEntityRenderer<T, ?> renderer, T entity, boolean body, boolean translucent,
            boolean outline, Operation<RenderType> original) {
        if (InvisibleMonsterVisuals.silhouette(entity) && !outline) return RenderType.entityTranslucent(renderer.getTextureLocation(entity));
        return original.call(renderer, entity, body, translucent, outline);
    }

    @WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void mite$faintHostileBody(EntityModel<T> model, PoseStack pose, VertexConsumer vertices, int light, int overlay,
            int color, Operation<Void> original, @Local(argsOnly = true) T entity) {
        if (InvisibleMonsterVisuals.silhouette(entity)) color = color & 0x00FFFFFF | InvisibleMonsterVisuals.COLOR & 0xFF000000;
        original.call(model, pose, vertices, light, overlay, color);
    }
}

package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ningmeng123.mite.client.EnderDragonModelCompatibility;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draw independent CEM neck/tail pieces at the matching vanilla animated spine pose. @author NingMeng123 */
@Mixin(EnderDragonRenderer.DragonModel.class)
public abstract class EnderDragonSegmentsMixin {
    @Unique private int mite$neckSegment;
    @Unique private int mite$tailSegment;

    @Inject(method = "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("HEAD"))
    private void mite$resetSegments(PoseStack pose, VertexConsumer consumer, int light, int overlay, int color, CallbackInfo ci) {
        mite$neckSegment = 0;
        mite$tailSegment = 5;
    }

    @WrapOperation(method = "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", ordinal = 0), require = 1)
    private void mite$drawNeckSegment(ModelPart part, PoseStack pose, VertexConsumer consumer, int light, int overlay, int color, Operation<Void> original) {
        EnderDragonModelCompatibility.renderSegment(part, mite$neckSegment++, () -> original.call(part, pose, consumer, light, overlay, color));
    }

    @WrapOperation(method = "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", ordinal = 3), require = 1)
    private void mite$drawTailSegment(ModelPart part, PoseStack pose, VertexConsumer consumer, int light, int overlay, int color, Operation<Void> original) {
        EnderDragonModelCompatibility.renderSegment(part, mite$tailSegment++, () -> original.call(part, pose, consumer, light, overlay, color));
    }
}

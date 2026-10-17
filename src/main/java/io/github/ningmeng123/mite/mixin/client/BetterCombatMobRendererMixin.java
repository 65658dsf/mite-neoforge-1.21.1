package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.mite.client.BetterCombatMobAnimations;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Apply after native subtype poses, before armor and held-item layers copy the model. @author NingMeng123 */
@Mixin(LivingEntityRenderer.class)
public abstract class BetterCombatMobRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Shadow protected M model;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", shift = At.Shift.AFTER))
    private void mite$animateMobRoot(T entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
            int light, CallbackInfo ci) {
        if (entity instanceof Mob mob) BetterCombatMobAnimations.applyBody(mob, partial, pose);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
    private void mite$animateArmedMob(T entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
            int light, CallbackInfo ci) {
        if (entity instanceof Mob mob) BetterCombatMobAnimations.apply(model, mob, partial, pose);
    }

    @WrapMethod(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void mite$restoreSharedMobModel(T entity, float yaw, float partial, PoseStack pose,
            MultiBufferSource buffers, int light, Operation<Void> original) {
        try {
            original.call(entity, yaw, partial, pose, buffers, light);
        } finally {
            if (entity instanceof Mob) BetterCombatMobAnimations.finish(model);
        }
    }
}

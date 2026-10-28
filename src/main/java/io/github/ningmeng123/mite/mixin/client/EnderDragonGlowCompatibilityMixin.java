package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.mite.client.EnderDragonGlowCompatibility;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Preserve CEM geometry, textures and EDP stage effects while excluding incompatible UV overlays. @author NingMeng123 */
@Mixin(value = EnderDragonRenderer.class, priority = 900)
public abstract class EnderDragonGlowCompatibilityMixin {
    @Shadow @Final private EnderDragonRenderer.DragonModel model;

    @WrapMethod(method = "render(Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void mite$compatibleGlow(EnderDragon dragon, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, Operation<Void> original) {
        original.call(dragon, yaw, partialTick, pose, EnderDragonGlowCompatibility.compatibleBuffers(model, buffers), light);
    }
}

package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.mite.client.FirstPersonArmorRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Restore the shared armor tree after ISMAH's recursive arm-only visibility changes. @author NingMeng123 */
@Pseudo
@Mixin(targets = "com.github.razorplay01.ismah.util.FirstPersonArmorRenderer", remap = false)
public abstract class IsmahArmorRenderStateMixin {
    @WrapMethod(method = "renderChestArm(Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V",
            remap = false)
    private static void mite$preserveSharedArmor(HumanoidArmorLayer<?, ?, ?> armorLayer, PoseStack pose,
            MultiBufferSource buffers, int light, AbstractClientPlayer player, Operation<Void> original) {
        try (FirstPersonArmorRenderState saved = FirstPersonArmorRenderState.open()) {
            original.call(armorLayer, pose, buffers, light, player);
        }
    }

    @WrapOperation(method = "renderChestArm(Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V",
            at = @At(value = "INVOKE", target = "Lcom/github/razorplay01/ismah/util/FirstPersonArmorRenderer;applyArmOnlyPose(Lnet/minecraft/client/model/HumanoidModel;Lnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/client/model/geom/PartPose;)V",
                    remap = false), remap = false)
    private static void mite$captureArmorBeforeHiding(HumanoidModel<?> model, HumanoidArm arm,
            PartPose armPose, Operation<Void> original) {
        FirstPersonArmorRenderState.capture(model);
        original.call(model, arm, armPose);
    }
}

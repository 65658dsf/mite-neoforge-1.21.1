package io.github.ningmeng123.mite.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.mite.client.BetterCombatMobAnimations;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Use the weapon's animated grip as well as the mob's animated arms. @author NingMeng123 */
@Mixin(ItemInHandLayer.class)
public abstract class BetterCombatMobHeldItemMixin {
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void mite$animateMobWeapon(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
            HumanoidArm arm, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (entity instanceof Mob mob) BetterCombatMobAnimations.applyHeldItem(mob, arm, pose);
    }
}

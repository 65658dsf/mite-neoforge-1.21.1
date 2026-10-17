package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.MonsterShieldPoses;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native humanoid mobs show their active shield use. @author NingMeng123 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidShieldPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void mite$showShieldBlock(LivingEntity entity, float swing, float speed, float age, float yaw, float pitch, CallbackInfo ci) {
        MonsterShieldPoses.apply((HumanoidModel<?>)(Object)this, entity);
    }
}

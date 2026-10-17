package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.MonsterShieldPoses;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shield stance takes precedence over the native two-arm skeleton melee pose. @author NingMeng123 */
@Mixin(value = SkeletonModel.class, priority = 1100)
public abstract class SkeletonShieldPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/Mob;FFFFF)V", at = @At("TAIL"))
    private void mite$showSkeletonShieldBlock(Mob entity, float swing, float speed, float age, float yaw, float pitch, CallbackInfo ci) {
        MonsterShieldPoses.apply((HumanoidModel<?>)(Object)this, entity);
    }
}

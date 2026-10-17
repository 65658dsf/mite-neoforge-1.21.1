package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.MonsterShieldPoses;
import net.minecraft.client.model.AbstractZombieModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zombie attack animation must not lower the arm that is actively blocking. @author NingMeng123 */
@Mixin(AbstractZombieModel.class)
public abstract class ZombieShieldPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/monster/Monster;FFFFF)V", at = @At("TAIL"))
    private void mite$showZombieShieldBlock(Monster entity, float swing, float speed, float age, float yaw, float pitch, CallbackInfo ci) {
        MonsterShieldPoses.apply((HumanoidModel<?>)(Object)this, entity);
    }
}

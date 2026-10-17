package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.MonsterShieldPoses;
import net.minecraft.client.model.PiglinModel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Includes zombified piglin arm animation and the matching sleeve overlay. @author NingMeng123 */
@Mixin(PiglinModel.class)
public abstract class PiglinShieldPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/Mob;FFFFF)V", at = @At("TAIL"))
    private void mite$showPiglinShieldBlock(Mob entity, float swing, float speed, float age, float yaw, float pitch, CallbackInfo ci) {
        if (!MonsterShieldPoses.active(entity)) return;
        PiglinModel<?> model = (PiglinModel<?>)(Object)this;
        MonsterShieldPoses.apply(model, entity);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
    }
}

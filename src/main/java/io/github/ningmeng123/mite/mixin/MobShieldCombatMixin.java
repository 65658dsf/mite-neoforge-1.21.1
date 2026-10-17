package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.MobShields;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Guards cannot attack through their own raised shield. @author NingMeng123 */
@Mixin(Mob.class)
public abstract class MobShieldCombatMixin {
    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void mite$lowerShieldBeforeAttacking(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (MobShields.defending((Mob)(Object)this)) cir.setReturnValue(false);
    }
}

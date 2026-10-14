package io.github.ningmeng123.mite.mixin;
import io.github.ningmeng123.mite.entity.MobCombat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.Witch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** tv supplies ten indirect-magic defense; the native subclass's 85 percent multiplier would apply a second reduction. @author NingMeng123 */
@Mixin(Witch.class)
public abstract class SourceWitchDefenseMixin {
    @Inject(method = "getDamageAfterMagicAbsorb", at = @At("HEAD"), cancellable = true)
    private void mite$alreadyAppliedSourceDefense(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (MobCombat.defending((Witch)(Object)this)) cir.setReturnValue(amount);
    }
}

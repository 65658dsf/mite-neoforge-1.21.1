package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.curse.PlayerCurses;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Run after the cancellable death hook, so a rescued witch keeps her curses. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class WitchCurseDeathMixin {
    @Inject(method = "die", at = @At("TAIL"))
    private void mite$liftWitchCurses(DamageSource source, CallbackInfo ci) {
        if ((Object)this instanceof Witch witch && !witch.level().isClientSide) PlayerCurses.removeForWitch(witch);
    }
}

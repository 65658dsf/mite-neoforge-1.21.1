package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native goals and brain melee requests share the weapon reach and delayed hit. @author NingMeng123 */
@Mixin(Mob.class)
public abstract class BetterCombatMobMixin {
    @Inject(method = "isWithinMeleeAttackRange", at = @At("HEAD"), cancellable = true)
    private void mite$weaponRange(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        Mob mob = (Mob)(Object)this;
        if (BetterMobCombat.supported(mob)) cir.setReturnValue(BetterMobCombat.canStrike(mob, target));
    }

    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void mite$weaponWindup(Entity target, CallbackInfoReturnable<Boolean> cir) {
        Mob mob = (Mob)(Object)this;
        if (!BetterMobCombat.executing(mob) && target instanceof LivingEntity living
                && (BetterMobCombat.attacking(mob) || BetterMobCombat.supported(mob))) {
            if (BetterMobCombat.supported(mob)) BetterMobCombat.request(mob, living);
            cir.setReturnValue(false);
        }
    }
}

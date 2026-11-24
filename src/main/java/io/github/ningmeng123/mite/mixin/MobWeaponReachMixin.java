package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.combat.MobWeaponReach;
import io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extend native melee checks and independently enforce the ceiling on direct attacks. @author NingMeng123 */
@Mixin(value = Mob.class, priority = 1001)
public abstract class MobWeaponReachMixin {
    @Inject(method = "isWithinMeleeAttackRange", at = @At("RETURN"), cancellable = true)
    private void mite$limitWeaponRange(LivingEntity target, CallbackInfoReturnable<Boolean> callback) {
        Mob mob = (Mob)(Object)this;
        if (!MobWeaponReach.applies(mob) || BetterMobCombat.supported(mob)) return;
        boolean reachable = (callback.getReturnValueZ() || MobWeaponReach.nativeRangeAllows(mob, target, mob.position()))
                && MobWeaponReach.canReachFrom(mob, target, mob.position());
        if (reachable != callback.getReturnValueZ()) callback.setReturnValue(reachable);
    }

    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void mite$validateWeaponHit(Entity target, CallbackInfoReturnable<Boolean> callback) {
        Mob mob = (Mob)(Object)this;
        if (!MobWeaponReach.canReachFrom(mob, target, mob.position())) callback.setReturnValue(false);
    }
}

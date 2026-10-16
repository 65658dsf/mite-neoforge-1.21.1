package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.survival.DeathExperienceOrbs;
import io.github.ningmeng123.mite.world.ExperienceOrbPhysics;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Source pickup/physical movement with owner-safe death lifetime and native mending retained. @author NingMeng123 */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin {
    @Shadow private Player followingPlayer;

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void mite$restrictDeathOrbPickup(Player player, CallbackInfo ci) {
        if (!DeathExperienceOrbs.canPickup((ExperienceOrb) (Object) this, player)) {
            ci.cancel();
            return;
        }
        if (!ExperienceOrbPhysics.canReach((ExperienceOrb)(Object)this,player)) {
            // Reject before PickupXp, pickup cooldown, mending and count changes.
            ci.cancel();
        }
    }

    @Inject(method="hurt",at=@At("HEAD"),cancellable=true)
    private void mite$orbsResistFire(DamageSource source,float amount,CallbackInfoReturnable<Boolean> cir) {
        if (source.is(DamageTypeTags.IS_FIRE)) cir.setReturnValue(false);
    }

    @ModifyExpressionValue(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/ExperienceOrb;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"),require=1,allow=1)
    private boolean mite$sourceUnderwaterGravity(boolean original) { return false; }

    @ModifyExpressionValue(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"),require=1,allow=1)
    private boolean mite$noNativeLavaBounce(boolean original) { return false; }

    @Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/phys/AABB;)Z"),require=1,allow=1)
    private void mite$fullLavaDamping(CallbackInfo ci) { ExperienceOrbPhysics.lavaMovement((ExperienceOrb)(Object)this); }

    @ModifyExpressionValue(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;getEyeHeight()F"),require=1,allow=1)
    private float mite$attractToSourceEyeHeight(float original) { return original*2F; }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mite$refreshDeathOrbTarget(CallbackInfo ci) {
        ExperienceOrb orb = (ExperienceOrb) (Object) this;
        if (DeathExperienceOrbs.hasOwner(orb)) {
            followingPlayer = DeathExperienceOrbs.findFollowingOwner(orb);
        }
    }

    @ModifyExpressionValue(method = "scanForEntities", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/entity/XpOrbTargetingEvent;getFollowingPlayer()Lnet/minecraft/world/entity/player/Player;"),
            require = 1, allow = 1)
    private @Nullable Player mite$restrictDeathOrbTarget(@Nullable Player original) {
        ExperienceOrb orb = (ExperienceOrb) (Object) this;
        return DeathExperienceOrbs.hasOwner(orb) ? DeathExperienceOrbs.findFollowingOwner(orb) : original;
    }

    @ModifyConstant(method = "tick", constant = @Constant(intValue = 6000), require = 1, allow = 1)
    private int mite$extendDeathOrbLifetime(int vanillaLifetime) {
        ExperienceOrb orb = (ExperienceOrb) (Object) this;
        if (!DeathExperienceOrbs.hasOwner(orb)) {
            return vanillaLifetime;
        }
        // Only the server counts the loaded lifetime. A client can keep ticking
        // a tracked orb outside server simulation range, so wait for its removal
        // packet instead of expiring it against an independent client clock.
        return orb.level().isClientSide ? Integer.MAX_VALUE : DeathExperienceOrbs.LIFETIME_TICKS;
    }

    @Inject(method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;)Z", at = @At("HEAD"), cancellable = true)
    private void mite$preventOwnedOrbMerges(ExperienceOrb other, CallbackInfoReturnable<Boolean> cir) {
        if (DeathExperienceOrbs.hasOwner((ExperienceOrb) (Object) this) || DeathExperienceOrbs.hasOwner(other)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;II)Z", at = @At("HEAD"), cancellable = true)
    private static void mite$preventPublicAwardsMergingIntoOwnedOrbs(
            ExperienceOrb candidate, int group, int value, CallbackInfoReturnable<Boolean> cir) {
        if (DeathExperienceOrbs.hasOwner(candidate)) {
            cir.setReturnValue(false);
        }
    }
}

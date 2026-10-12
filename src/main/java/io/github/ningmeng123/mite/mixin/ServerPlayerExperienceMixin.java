package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.mite.survival.PlayerDeathExperienceEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps command setters consistent and marks their vanilla XP packet dirty. @author NingMeng123 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerExperienceMixin {
    @Shadow private int lastSentExp;

    @Inject(method = "setExperiencePoints", at = @At("HEAD"), cancellable = true)
    private void mite$setExperiencePoints(int points, CallbackInfo ci) {
        PlayerExperience.setPoints((ServerPlayer) (Object) this, points);
        lastSentExp = Integer.MIN_VALUE;
        ci.cancel();
    }

    @Inject(method = "setExperienceLevels", at = @At("HEAD"), cancellable = true)
    private void mite$setExperienceLevels(int level, CallbackInfo ci) {
        PlayerExperience.setLevels((ServerPlayer) (Object) this, level);
        lastSentExp = Integer.MIN_VALUE;
        ci.cancel();
    }

    @ModifyConstant(method = {"giveExperiencePoints", "giveExperienceLevels", "onEnchantmentPerformed"},
            constant = @Constant(intValue = -1), require = 3)
    private int mite$useSignedExperienceDirtyValue(int original) {
        // -1 is a valid debt total, so it cannot serve as the dirty sentinel.
        return Integer.MIN_VALUE;
    }

    @ModifyConstant(method = {"changeDimension", "restoreFrom"},
            constant = @Constant(intValue = -1, ordinal = 0), require = 2)
    private int mite$useSignedExperienceDirtyValueOnTransition(int original) {
        // In 1.21.1 the first integer -1 writes lastSentExp; a later one writes
        // lastSentFood and must retain its vanilla meaning.
        return Integer.MIN_VALUE;
    }

    @Inject(method = "die", at = @At("TAIL"))
    private void mite$captureRespawnExperience(DamageSource source, CallbackInfo ci) {
        // TAIL is after successful death processing, not its cancellation return.
        PlayerDeathExperienceEvents.captureDeath((ServerPlayer) (Object) this);
    }
}

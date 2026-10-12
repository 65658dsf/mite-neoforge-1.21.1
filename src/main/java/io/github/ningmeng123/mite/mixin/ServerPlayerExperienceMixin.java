package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.PlayerExperience;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps command setters consistent and marks their vanilla XP packet dirty. @author NingMeng123 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerExperienceMixin {
    @Shadow private int lastSentExp;

    @Inject(method = "setExperiencePoints", at = @At("HEAD"), cancellable = true)
    private void mite$setExperiencePoints(int points, CallbackInfo ci) {
        PlayerExperience.setPoints((ServerPlayer) (Object) this, points);
        lastSentExp = -1;
        ci.cancel();
    }

    @Inject(method = "setExperienceLevels", at = @At("HEAD"), cancellable = true)
    private void mite$setExperienceLevels(int level, CallbackInfo ci) {
        PlayerExperience.setLevels((ServerPlayer) (Object) this, level);
        lastSentExp = -1;
        ci.cancel();
    }
}

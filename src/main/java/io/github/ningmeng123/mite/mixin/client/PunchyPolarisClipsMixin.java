package io.github.ningmeng123.mite.mixin.client;

import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.client.PolarisWeaponAnimations;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep Polaris' named short-weapon clips isolated from default animations. @author NingMeng123 */
@Pseudo
@Mixin(targets = "punchy.client.animation.io.PunchyAnimationLoader", remap = false)
public abstract class PunchyPolarisClipsMixin {
    @Inject(method = "readClips", at = @At("HEAD"), require = 1)
    private static void mite$scopePolarisClips(JsonObject json, String source, CallbackInfoReturnable<Map<?, ?>> callback) {
        PolarisWeaponAnimations.scopeClips(json, source);
    }
}

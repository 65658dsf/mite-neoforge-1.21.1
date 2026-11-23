package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import net.bettercombat.client.BetterCombatClientMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets Punchy retry its config lookup after Better Combat has initialized. @author NingMeng123 */
@Pseudo
@Mixin(targets = "punchy.compat.BetterCombatCompat", remap = false)
public abstract class PunchyBetterCombatCompatMixin {
    @Inject(method = "resolveClientConfig", at = @At("HEAD"), cancellable = true, require = 0)
    private static void mite$deferBetterCombatLookup(CallbackInfoReturnable<Object> callback) {
        // Punchy initializes during mod construction and would cache a null config for the whole session.
        if (BetterCombatBridge.enabled() && BetterCombatClientMod.config == null) {
            callback.setReturnValue(null);
        }
    }
}

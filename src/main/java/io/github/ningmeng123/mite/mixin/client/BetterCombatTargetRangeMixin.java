package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatPlayerReach;
import net.bettercombat.client.collision.TargetFinder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Apply configured range, then enforce the same per-target MITE cap as the server. @author NingMeng123 */
@Pseudo
@Mixin(targets = "net.bettercombat.client.collision.TargetFinder", remap = false)
public abstract class BetterCombatTargetRangeMixin {
    @Inject(method = "applyAttackRangeModifiers", at = @At("RETURN"), cancellable = true, require = 1)
    private static void mite$configuredRange(Player player, double range, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(BetterCombatPlayerReach.configure(player, cir.getReturnValue()));
    }

    @ModifyArg(method = "findAttackTargetResult", at = @At(value = "INVOKE",
            target = "Lnet/bettercombat/client/collision/TargetFinder;getInitialTargets(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;D)Ljava/util/List;"),
            index = 2, require = 1)
    private static double mite$includeConfiguredTargets(double range, @Local(argsOnly = true) Player player) {
        return BetterCombatPlayerReach.searchRange(player, range);
    }

    @Inject(method = "findAttackTargetResult", at = @At("RETURN"), require = 1)
    private static void mite$sourceReachCap(Player player, Entity cursorTarget,
            net.bettercombat.api.WeaponAttributes.Attack attack, double range,
            CallbackInfoReturnable<TargetFinder.TargetResult> cir) {
        var result = cir.getReturnValue();
        result.entities = result.entities.stream()
                .filter(target -> !Boolean.FALSE.equals(BetterCombatPlayerReach.canMelee(player, target)))
                .collect(java.util.stream.Collectors.toList());
    }
}

package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatPlayerMode;
import net.bettercombat.api.AttackHand;
import net.bettercombat.logic.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Apply vanilla mode to Better Combat's passive player effects. @author NingMeng123 */
@Pseudo
@Mixin(targets = "net.bettercombat.logic.PlayerAttackHelper", remap = false)
public abstract class BetterCombatPlayerHandlingMixin {
    @Inject(method = "getCurrentAttack", at = @At("HEAD"), cancellable = true)
    private static void mite$vanillaAttack(Player player, int combo, CallbackInfoReturnable<AttackHand> cir) {
        if (BetterCombatPlayerMode.isVanilla(player)) cir.setReturnValue(null);
    }

    @Inject(method = "isDualWielding(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("HEAD"), cancellable = true)
    private static void mite$vanillaAttackSpeed(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (BetterCombatPlayerMode.isVanilla(player)) cir.setReturnValue(false);
    }

    @Inject(method = "getRangeWithWeapon", at = @At("HEAD"), cancellable = true)
    private static void mite$vanillaInteractionRange(Player player, double range, CallbackInfoReturnable<Double> cir) {
        if (BetterCombatPlayerMode.isVanilla(player)) cir.setReturnValue(range);
    }

    @Inject(method = "poseForPlayer", at = @At("HEAD"), cancellable = true)
    private static void mite$vanillaPose(Player player, CallbackInfoReturnable<Pose> cir) {
        if (BetterCombatPlayerMode.isVanilla(player)) cir.setReturnValue(new Pose("", ""));
    }
}

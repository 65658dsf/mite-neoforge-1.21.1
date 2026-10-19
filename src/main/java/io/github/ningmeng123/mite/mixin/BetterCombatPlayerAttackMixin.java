package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatPlayerReach;
import net.bettercombat.api.AttackHand;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Carry the actual selected move into the queued vanilla attack transaction. @author NingMeng123 */
@Pseudo
@Mixin(targets = "net.bettercombat.network.ServerNetwork", remap = false)
public abstract class BetterCombatPlayerAttackMixin {
    @WrapOperation(method = "handleAttackRequest", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;executeIfPossible(Ljava/lang/Runnable;)V"), require = 1)
    private static void mite$weaponRange(MinecraftServer server, Runnable action, Operation<Void> original,
            @Local(argsOnly = true) ServerPlayer player, @Local AttackHand hand) {
        original.call(server, (Runnable)() -> BetterCombatPlayerReach.duringAttack(player,
                hand.itemStack(), hand.attack().rangeMultiplier(), action));
    }

    /** The optional non-vanilla/dragon-part gate must also admit configured longer moves. */
    @ModifyExpressionValue(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/bettercombat/logic/PlayerAttackHelper;getRangeForItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)D"), require = 1)
    private static double mite$validationRange(double original, @Local(argsOnly = true) ServerPlayer player) {
        Double range = BetterCombatPlayerReach.reach(player);
        return range == null ? original : Math.max(original, range);
    }
}

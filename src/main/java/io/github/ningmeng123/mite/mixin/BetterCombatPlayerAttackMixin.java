package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatPlayerReach;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatPlayerMode;
import net.bettercombat.api.AttackHand;
import net.bettercombat.network.Packets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carry the actual selected move into the queued vanilla attack transaction. @author NingMeng123 */
@Pseudo
@Mixin(targets = "net.bettercombat.network.ServerNetwork", remap = false)
public abstract class BetterCombatPlayerAttackMixin {
    @WrapOperation(method = "handleAttackRequest", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;executeIfPossible(Ljava/lang/Runnable;)V"), require = 1)
    private static void mite$weaponRange(MinecraftServer server, Runnable action, Operation<Void> original,
            @Local(argsOnly = true) ServerPlayer player, @Local AttackHand hand) {
        original.call(server, (Runnable)() -> {
            if (!BetterCombatPlayerMode.isVanilla(player))
                BetterCombatPlayerReach.duringAttack(player, hand.itemStack(), hand.attack().rangeMultiplier(), action);
        });
    }

    @Inject(method = "handleAttackAnimation", at = @At("HEAD"), cancellable = true)
    private static void mite$allowAnimationStop(Packets.AttackAnimation packet, MinecraftServer server,
            ServerPlayer player, CallbackInfo ci) {
        if (BetterCombatPlayerMode.isVanilla(player) && !Packets.AttackAnimation.StopSymbol.equals(packet.animationName()))
            ci.cancel();
    }

    /** The optional non-vanilla/dragon-part gate must also admit configured longer moves. */
    @ModifyExpressionValue(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/bettercombat/logic/PlayerAttackHelper;getRangeForItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)D"), require = 1)
    private static double mite$validationRange(double original, @Local(argsOnly = true) ServerPlayer player) {
        Double range = BetterCombatPlayerReach.reach(player);
        return range == null ? original : Math.max(original, range);
    }
}

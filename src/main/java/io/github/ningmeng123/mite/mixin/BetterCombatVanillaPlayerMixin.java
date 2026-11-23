package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatPlayerMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep native player actions consistent with the selected combat mode. @author NingMeng123 */
@Mixin(Player.class)
public abstract class BetterCombatVanillaPlayerMixin {
    @ModifyExpressionValue(method = "getAttackStrengthScale", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getCurrentItemAttackStrengthDelay()F"))
    private float mite$attackRecovery(float ticks) {
        return BetterCombatPlayerMode.attackInterval((Player)(Object)this, ticks);
    }

    // Insert the vanilla-mode return before Better Combat can cancel the offhand lookup.
    @Inject(method = "getItemBySlot", at = @At("HEAD"), cancellable = true, order = 900)
    private void mite$vanillaOffhand(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        Player player = (Player)(Object)this;
        if (slot == EquipmentSlot.OFFHAND && BetterCombatPlayerMode.isVanilla(player))
            cir.setReturnValue(player.getInventory().offhand.getFirst());
    }

    @Inject(method = "attack", at = @At("HEAD"))
    private void mite$clearPreviousAttack(Entity target, CallbackInfo ci) {
        Player player = (Player)(Object)this;
        if (BetterCombatPlayerMode.isVanilla(player))
            ((BetterCombatLastAttackAccess)player).mite$setLastAttack(null);
    }
}

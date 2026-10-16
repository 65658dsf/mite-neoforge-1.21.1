package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.curse.CursePlayerEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native right-click and dispenser paths retain their item instead of losing forbidden armor. @author NingMeng123 */
@Mixin(Player.class)
public abstract class CursePlayerEquipmentMixin {
    @Inject(method = "canUseSlot", at = @At("HEAD"), cancellable = true)
    private void mite$cannotUseArmorSlot(EquipmentSlot slot, CallbackInfoReturnable<Boolean> cir) {
        if (CursePlayerEffects.cannotEquip((Player)(Object)this, slot)) cir.setReturnValue(false);
    }
    @Inject(method = "canTakeItem", at = @At("HEAD"), cancellable = true)
    private void mite$cannotReceiveDispenserArmor(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player)(Object)this;
        if (!stack.isEmpty() && CursePlayerEffects.cannotEquip(player, CursePlayerEffects.rawEquipmentSlot(stack)))
            cir.setReturnValue(false);
    }
}

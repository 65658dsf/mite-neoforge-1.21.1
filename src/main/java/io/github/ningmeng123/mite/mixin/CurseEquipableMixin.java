package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.curse.CursePlayerEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reject by the raw intended slot before native fallback can move offhand armor to mainhand. @author NingMeng123 */
@Mixin(Equipable.class)
public interface CurseEquipableMixin {
    @Inject(method = "swapWithEquipmentSlot", at = @At("HEAD"), cancellable = true)
    private void mite$cannotSwapArmor(Item item, Level level, Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.isEmpty() && CursePlayerEffects.cannotEquip(player, CursePlayerEffects.rawEquipmentSlot(stack)))
            cir.setReturnValue(InteractionResultHolder.fail(stack));
    }
}

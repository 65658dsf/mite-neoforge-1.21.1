package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.MobEquipmentUpgrades;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Quality precedes piglin gold preference when replacing comparable equipment. @author NingMeng123 */
@Mixin(Piglin.class)
public abstract class PiglinEquipmentUpgradeMixin {
    @Inject(method = "canReplaceCurrentItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true)
    private void mite$preferEquipmentQuality(ItemStack incoming, ItemStack current, CallbackInfoReturnable<Boolean> cir) {
        Boolean decision = MobEquipmentUpgrades.replacementDecision((Piglin)(Object)this, incoming, current);
        if (decision != null) cir.setReturnValue(decision);
    }
}

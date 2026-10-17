package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import io.github.ningmeng123.mite.entity.MobEquipmentUpgrades;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep collision pickup consistent with the equipment pursuit policy. @author NingMeng123 */
@Mixin(Mob.class)
public abstract class MobEquipmentUpgradeMixin {
    @Inject(method = "canReplaceCurrentItem", at = @At("HEAD"), cancellable = true)
    private void mite$preferEquipmentQuality(ItemStack incoming, ItemStack current, CallbackInfoReturnable<Boolean> cir) {
        Boolean decision = MobEquipmentUpgrades.replacementDecision((Mob)(Object)this, incoming, current);
        if (decision != null) cir.setReturnValue(decision);
    }

    @Inject(method = "equipItemIfPossible", at = @At("HEAD"), cancellable = true)
    private void mite$rejectUnavailableEquipment(ItemStack incoming, CallbackInfoReturnable<ItemStack> cir) {
        Mob mob = (Mob)(Object)this;
        if (MobEquipmentUpgrades.handles(mob) && (SourceEnchantmentCombat.disarmed(mob)
                || RemovedDiamondEquipment.isRemoved(incoming)))
            cir.setReturnValue(ItemStack.EMPTY);
    }

    @WrapOperation(method = "equipItemIfPossible", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EquipmentSlot;isArmor()Z"))
    private boolean mite$doNotCarryRejectedArmor(EquipmentSlot slot, Operation<Boolean> original,
            @Local(argsOnly = true) ItemStack incoming) {
        return (!MobEquipmentUpgrades.handles((Mob)(Object)this) || !MobEquipmentUpgrades.equipment(incoming)) && original.call(slot);
    }
}

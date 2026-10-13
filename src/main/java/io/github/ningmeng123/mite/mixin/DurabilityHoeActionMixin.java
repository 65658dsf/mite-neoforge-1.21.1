package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** R196 yb.tryTillSoil costs fifty points, not one mining action. @author NingMeng123 */
@Mixin(HoeItem.class)
public abstract class DurabilityHoeActionMixin {
    @WrapOperation(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private void mite$tillingWear(ItemStack stack, int amount, LivingEntity owner,
            EquipmentSlot slot, Operation<Void> original) {
        if (EquipmentDurability.toolProfile(stack) != null) EquipmentDurability.hurt(stack, 50, owner, slot);
        else original.call(stack, amount, owner, slot);
    }
}

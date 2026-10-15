package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.entity.BloodMoonEquipment;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve explicit armor before the source ordinary-skeleton initializer reaches the shared armor roll. @author NingMeng123 */
@Mixin(R196Skeleton.class)
public abstract class BloodMoonSkeletonEquipmentMixin {
    @WrapOperation(method = "initializeOrdinaryTroop", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/monster/AbstractSkeleton;setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"))
    private static void mite$preserveExplicitArmor(AbstractSkeleton skeleton, EquipmentSlot slot, ItemStack stack, Operation<Void> original) {
        if (stack.isEmpty() && slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                && !skeleton.getItemBySlot(slot).isEmpty() && BloodMoonEquipment.progressionActive(skeleton)) return;
        original.call(skeleton, slot, stack);
    }
}

package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep the real registry holder while carrying both levels through native effect dispatch. @author NingMeng123 */
@Mixin(EnchantmentHelper.class)
public abstract class GearEnchantmentIterationMixin {
    @WrapOperation(method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentVisitor;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentVisitor;accept(Lnet/minecraft/core/Holder;I)V"))
    private static void mite$visit(EnchantmentHelper.EnchantmentVisitor visitor, Holder<Enchantment> enchantment,
            int level, Operation<Void> original, @Local(argsOnly = true) ItemStack stack) {
        try (var ignored = EnchantmentSources.visiting(stack, enchantment)) { original.call(visitor, enchantment, level); }
    }

    @WrapOperation(method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentInSlotVisitor;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentInSlotVisitor;accept(Lnet/minecraft/core/Holder;ILnet/minecraft/world/item/enchantment/EnchantedItemInUse;)V"))
    private static void mite$visitSlot(EnchantmentHelper.EnchantmentInSlotVisitor visitor, Holder<Enchantment> enchantment,
            int level, EnchantedItemInUse item, Operation<Void> original) {
        try (var ignored = EnchantmentSources.visiting(item.itemStack(), enchantment)) { original.call(visitor, enchantment, level, item); }
    }
}

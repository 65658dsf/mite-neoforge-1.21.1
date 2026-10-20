package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.enchantment.NaturalEnchantmentLimits;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.providers.EnchantmentsByCost;
import net.minecraft.world.item.enchantment.providers.EnchantmentsByCostWithDifficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native cost-based reward providers retain natural level caps. @author NingMeng123 */
@Mixin({EnchantmentsByCost.class, EnchantmentsByCostWithDifficulty.class})
public abstract class NaturalCostEnchantmentMixin {
    @WrapOperation(method = "enchant", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/ItemEnchantments$Mutable;upgrade(Lnet/minecraft/core/Holder;I)V"))
    private void mite$naturalRewardLevel(ItemEnchantments.Mutable enchantments, Holder<Enchantment> enchantment,
            int level, Operation<Void> original) {
        original.call(enchantments, enchantment, Math.min(level, NaturalEnchantmentLimits.maximumLevel(enchantment)));
    }
}
